package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines vector recall and keyword recall with RRF for teaching hybrid retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalHybridSearchService {

    private static final int RRF_K = 60;
    private static final String SOURCE_VECTOR = "vector";
    private static final String SOURCE_KEYWORD = "keyword";
    private static final String SOURCE_HYBRID = "hybrid";

    private final KeywordSearchProvider keywordSearchProvider;
    private final RetrievalResultEnrichmentService resultEnrichmentService;

    public List<SearchResult> merge(String query, List<String> kbIds, List<SearchResult> vectorResults, int candidateCount) {
        List<SearchResult> keywordResults = keywordSearchProvider.search(query, kbIds, candidateCount).stream()
                .map(this::toSearchResult)
                .toList();
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

    private SearchResult toSearchResult(KeywordSearchProvider.KeywordSearchHit hit) {
        SearchResult result = resultEnrichmentService.enrich(new VectorStoreService.VectorSearchResult(
                hit.chunkId(),
                hit.kbId(),
                hit.content(),
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
}
