package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a deterministic local rerank score before a remote rerank provider is introduced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalRerankService {

    private static final Pattern ASCII_TERM_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final String DEFAULT_RERANK_PROVIDER = "local-lexical";
    private static final String LOCAL_DEMO_PROVIDER = "local-demo";
    private static final float SIMILARITY_WEIGHT = 0.7f;
    private static final float RERANK_WEIGHT = 0.3f;

    @Value("${app.rerank.provider:local-demo}")
    private String provider = LOCAL_DEMO_PROVIDER;

    private final RerankProviderClient rerankProviderClient;

    public List<SearchResult> rerank(String query, List<SearchResult> results, String rerankLlmId) {
        if (shouldUseRemoteProvider()) {
            List<Float> providerScores = rerankProviderClient.rerank(
                    query,
                    results.stream().map(SearchResult::getContent).toList(),
                    rerankLlmId
            );
            if (providerScores.size() == results.size()) {
                return applyScores(results, providerScores, remoteProviderLabel(rerankLlmId));
            }
            log.warn("远程 Rerank 分数不可用，回退本地 lexical rerank: expected={}, actual={}",
                    results.size(), providerScores.size());
        }

        Set<String> queryTerms = extractTerms(query);
        log.info("执行本地Rerank: candidateCount={}, queryTerms={}, rerankLlmId={}",
                results.size(), queryTerms.size(), DEFAULT_RERANK_PROVIDER);

        List<Float> localScores = results.stream()
                .map(result -> calculateRerankScore(queryTerms, result.getContent()))
                .toList();
        return applyScores(results, localScores, DEFAULT_RERANK_PROVIDER);
    }

    private List<SearchResult> applyScores(List<SearchResult> results, List<Float> rerankScores, String providerLabel) {
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            float rerankScore = rerankScores.get(index);
            float retrievalScore = scoreOrZero(result.getFinalScore());
            result.setRerankScore(rerankScore);
            result.setRerankProvider(providerLabel);
            result.setFinalScore((retrievalScore * SIMILARITY_WEIGHT) + (rerankScore * RERANK_WEIGHT));
        }
        return results;
    }

    private boolean shouldUseRemoteProvider() {
        return provider != null
                && !provider.isBlank()
                && !LOCAL_DEMO_PROVIDER.equalsIgnoreCase(provider)
                && !DEFAULT_RERANK_PROVIDER.equalsIgnoreCase(provider);
    }

    private String remoteProviderLabel(String rerankLlmId) {
        if (rerankLlmId != null && !rerankLlmId.isBlank()) {
            return rerankLlmId;
        }
        return provider == null || provider.isBlank() ? "remote" : provider;
    }

    private float calculateRerankScore(Set<String> queryTerms, String content) {
        if (queryTerms.isEmpty()) {
            return 0.0f;
        }

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

        float coverage = (float) overlap / queryTerms.size();
        float density = (float) overlap / Math.max(contentTerms.size(), 1);
        return Math.min(1.0f, (coverage * 0.8f) + (density * 0.2f));
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

    private float scoreOrZero(Float score) {
        return score == null ? 0.0f : score;
    }
}
