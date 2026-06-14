package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combines vector recall with deterministic local keyword recall for teaching hybrid retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalHybridSearchService {

    private static final Pattern ASCII_TERM_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final int RRF_K = 60;
    private static final String SOURCE_VECTOR = "vector";
    private static final String SOURCE_KEYWORD = "keyword";
    private static final String SOURCE_HYBRID = "hybrid";

    private final ChunkRepository chunkRepository;
    private final RetrievalResultEnrichmentService resultEnrichmentService;

    public List<SearchResult> merge(String query, List<String> kbIds, List<SearchResult> vectorResults, int candidateCount) {
        List<SearchResult> keywordResults = keywordSearch(query, kbIds, candidateCount);
        if (keywordResults.isEmpty()) {
            markVectorOnlyResults(vectorResults);
            return vectorResults;
        }

        Map<String, HybridCandidate> merged = new LinkedHashMap<>();
        for (int index = 0; index < vectorResults.size(); index++) {
            SearchResult result = vectorResults.get(index);
            HybridCandidate candidate = merged.computeIfAbsent(result.getChunkId(), key -> new HybridCandidate(result));
            candidate.vectorRank = index + 1;
        }
        for (int index = 0; index < keywordResults.size(); index++) {
            SearchResult result = keywordResults.get(index);
            HybridCandidate candidate = merged.computeIfAbsent(result.getChunkId(), key -> new HybridCandidate(result));
            candidate.keywordRank = index + 1;
            candidate.result.setKeywordScore(result.getKeywordScore());
        }

        List<SearchResult> results = merged.values().stream()
                .map(this::applyHybridScore)
                .sorted(Comparator.comparing(this::scoreOrZero).reversed())
                .limit(Math.max(1, candidateCount))
                .toList();

        log.info("混合检索完成: vectorCount={}, keywordCount={}, mergedCount={}",
                vectorResults.size(), keywordResults.size(), results.size());
        return results;
    }

    private List<SearchResult> keywordSearch(String query, List<String> kbIds, int candidateCount) {
        Set<String> queryTerms = extractTerms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        List<KeywordHit> hits = new ArrayList<>();
        for (String kbId : kbIds) {
            for (Chunk chunk : chunkRepository.findByKbIdAndIsEnabledTrue(kbId)) {
                float score = calculateKeywordScore(queryTerms, chunk.getContent());
                if (score > 0.0f) {
                    hits.add(new KeywordHit(chunk, score));
                }
            }
        }

        return hits.stream()
                .sorted(Comparator.comparing(KeywordHit::score).reversed())
                .limit(Math.max(1, candidateCount))
                .map(this::toSearchResult)
                .toList();
    }

    private SearchResult toSearchResult(KeywordHit hit) {
        Chunk chunk = hit.chunk();
        SearchResult result = resultEnrichmentService.enrich(new VectorStoreService.VectorSearchResult(
                chunk.getChunkId(),
                chunk.getKbId(),
                chunk.getContent(),
                hit.score()
        ));
        result.setSimilarityScore(null);
        result.setKeywordScore(hit.score());
        result.setFinalScore(hit.score());
        result.setRetrievalSource(SOURCE_KEYWORD);
        return result;
    }

    private SearchResult applyHybridScore(HybridCandidate candidate) {
        SearchResult result = candidate.result;
        float hybridScore = normalizedRrfScore(candidate.vectorRank, candidate.keywordRank);
        result.setHybridScore(hybridScore);
        result.setFinalScore(hybridScore);
        result.setRetrievalSource(source(candidate));
        return result;
    }

    private float normalizedRrfScore(Integer vectorRank, Integer keywordRank) {
        float score = 0.0f;
        if (vectorRank != null) {
            score += 1.0f / (RRF_K + vectorRank);
        }
        if (keywordRank != null) {
            score += 1.0f / (RRF_K + keywordRank);
        }
        float maxScore = 2.0f / (RRF_K + 1.0f);
        return Math.min(1.0f, score / maxScore);
    }

    private String source(HybridCandidate candidate) {
        if (candidate.vectorRank != null && candidate.keywordRank != null) {
            return SOURCE_HYBRID;
        }
        if (candidate.keywordRank != null) {
            return SOURCE_KEYWORD;
        }
        return SOURCE_VECTOR;
    }

    private void markVectorOnlyResults(List<SearchResult> results) {
        for (SearchResult result : results) {
            result.setRetrievalSource(SOURCE_VECTOR);
        }
    }

    private float calculateKeywordScore(Set<String> queryTerms, String content) {
        Set<String> contentTerms = extractTerms(content);
        if (contentTerms.isEmpty()) {
            return 0.0f;
        }

        int overlap = 0;
        for (String term : queryTerms) {
            if (contentTerms.contains(term)) {
                overlap += 1;
            }
        }
        if (overlap == 0) {
            return 0.0f;
        }

        float coverage = (float) overlap / queryTerms.size();
        float density = (float) overlap / Math.max(contentTerms.size(), 1);
        return Math.min(1.0f, (coverage * 0.75f) + (density * 0.25f));
    }

    private Set<String> extractTerms(String text) {
        Set<String> terms = new HashSet<>();
        if (text == null || text.isBlank()) {
            return terms;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = ASCII_TERM_PATTERN.matcher(normalized);
        while (matcher.find()) {
            terms.add(matcher.group());
        }

        normalized.codePoints()
                .filter(this::isCjk)
                .forEach(codePoint -> terms.add(new String(Character.toChars(codePoint))));
        return terms;
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF);
    }

    private float scoreOrZero(SearchResult result) {
        return result.getFinalScore() == null ? 0.0f : result.getFinalScore();
    }

    private static class HybridCandidate {
        private final SearchResult result;
        private Integer vectorRank;
        private Integer keywordRank;

        private HybridCandidate(SearchResult result) {
            this.result = result;
        }
    }

    private record KeywordHit(Chunk chunk, float score) {
    }
}
