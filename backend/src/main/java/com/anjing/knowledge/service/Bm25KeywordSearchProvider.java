package com.anjing.knowledge.service;

import com.anjing.config.properties.KeywordSearchProperties;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local BM25 keyword recall adapter.
 *
 * <p>This is a lightweight bridge between the deterministic local provider and
 * an external Elasticsearch adapter. It keeps BM25 scoring behind
 * KeywordSearchProvider, while RetrievalHybridSearchService still owns RRF merge.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "bm25")
public class Bm25KeywordSearchProvider implements KeywordSearchProvider {

    private static final Pattern ASCII_TERM_PATTERN = Pattern.compile("[a-z0-9]+");

    private final ChunkRepository chunkRepository;
    private final KeywordSearchProperties keywordSearchProperties;

    @Override
    public List<KeywordSearchHit> search(String query, List<String> kbIds, int candidateCount) {
        List<String> queryTerms = extractTerms(query);
        if (queryTerms.isEmpty() || kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }

        int size = Math.max(1, candidateCount);
        List<KeywordSearchHit> hits = new ArrayList<>();
        for (String kbId : kbIds) {
            hits.addAll(searchKnowledgeBase(queryTerms, kbId));
        }

        List<KeywordSearchHit> results = hits.stream()
                .sorted(Comparator.comparing(KeywordSearchHit::score).reversed())
                .limit(size)
                .toList();
        log.info("BM25 关键词召回完成: kbCount={}, hitCount={}, resultCount={}",
                kbIds.size(), hits.size(), results.size());
        return results;
    }

    private List<KeywordSearchHit> searchKnowledgeBase(List<String> queryTerms, String kbId) {
        List<ChunkDocument> documents = chunkRepository.findByKbIdAndIsEnabledTrue(kbId).stream()
                .map(this::toDocument)
                .filter(document -> document.length > 0)
                .toList();
        if (documents.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> documentFrequency = documentFrequency(documents, queryTerms);
        float averageLength = averageLength(documents);
        float minimumScore = keywordSearchProperties.getBm25().getMinimumScore();

        return documents.stream()
                .map(document -> new KeywordSearchHit(
                        document.chunk.getChunkId(),
                        document.chunk.getKbId(),
                        document.chunk.getContent(),
                        bm25Score(document, queryTerms, documentFrequency, documents.size(), averageLength)
                ))
                .filter(hit -> hit.score() > minimumScore)
                .toList();
    }

    private ChunkDocument toDocument(Chunk chunk) {
        List<String> terms = extractTerms(chunk.getContent());
        return new ChunkDocument(chunk, termFrequency(terms), terms.size());
    }

    private Map<String, Integer> termFrequency(List<String> terms) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String term : terms) {
            result.merge(term, 1, Integer::sum);
        }
        return result;
    }

    private Map<String, Integer> documentFrequency(List<ChunkDocument> documents, List<String> queryTerms) {
        Set<String> uniqueQueryTerms = new HashSet<>(queryTerms);
        Map<String, Integer> result = new HashMap<>();
        for (String queryTerm : uniqueQueryTerms) {
            int count = 0;
            for (ChunkDocument document : documents) {
                if (document.termFrequency.containsKey(queryTerm)) {
                    count++;
                }
            }
            result.put(queryTerm, count);
        }
        return result;
    }

    private float bm25Score(
            ChunkDocument document,
            List<String> queryTerms,
            Map<String, Integer> documentFrequency,
            int documentCount,
            float averageLength
    ) {
        KeywordSearchProperties.Bm25 properties = keywordSearchProperties.getBm25();
        float k1 = Math.max(0.01f, properties.getK1());
        float b = Math.min(1.0f, Math.max(0.0f, properties.getB()));
        float score = 0.0f;

        for (String queryTerm : new HashSet<>(queryTerms)) {
            int termFrequency = document.termFrequency.getOrDefault(queryTerm, 0);
            if (termFrequency <= 0) {
                continue;
            }

            int frequency = documentFrequency.getOrDefault(queryTerm, 0);
            float idf = (float) Math.log(1.0d + ((documentCount - frequency + 0.5d) / (frequency + 0.5d)));
            float lengthRatio = averageLength <= 0.0f ? 1.0f : document.length / averageLength;
            float denominator = termFrequency + (k1 * (1.0f - b + (b * lengthRatio)));
            score += idf * ((termFrequency * (k1 + 1.0f)) / denominator);
        }
        return score;
    }

    private float averageLength(List<ChunkDocument> documents) {
        int totalLength = documents.stream().mapToInt(document -> document.length).sum();
        return (float) totalLength / Math.max(1, documents.size());
    }

    private List<String> extractTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        Matcher matcher = ASCII_TERM_PATTERN.matcher(normalized);
        while (matcher.find()) {
            terms.add(matcher.group());
        }

        normalized.codePoints()
                .filter(this::isCjk)
                .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                .forEach(terms::add);
        return terms;
    }

    private boolean isCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF);
    }

    private record ChunkDocument(Chunk chunk, Map<String, Integer> termFrequency, int length) {
    }
}
