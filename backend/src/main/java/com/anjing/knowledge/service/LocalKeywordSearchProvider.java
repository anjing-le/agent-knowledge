package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic local keyword recall for demos before a BM25/Elasticsearch adapter is introduced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalKeywordSearchProvider implements KeywordSearchProvider {

    private static final Pattern ASCII_TERM_PATTERN = Pattern.compile("[a-z0-9]+");

    private final ChunkRepository chunkRepository;

    @Override
    public List<KeywordSearchHit> search(String query, List<String> kbIds, int candidateCount) {
        Set<String> queryTerms = extractTerms(query);
        if (queryTerms.isEmpty() || kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }

        List<KeywordSearchHit> hits = new ArrayList<>();
        for (String kbId : kbIds) {
            for (Chunk chunk : chunkRepository.findByKbIdAndIsEnabledTrue(kbId)) {
                float score = calculateKeywordScore(queryTerms, chunk.getContent());
                if (score > 0.0f) {
                    hits.add(new KeywordSearchHit(chunk.getChunkId(), chunk.getKbId(), chunk.getContent(), score));
                }
            }
        }

        List<KeywordSearchHit> results = hits.stream()
                .sorted(Comparator.comparing(KeywordSearchHit::score).reversed())
                .limit(Math.max(1, candidateCount))
                .toList();
        log.info("本地关键词召回完成: kbCount={}, hitCount={}, resultCount={}",
                kbIds.size(), hits.size(), results.size());
        return results;
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
}
