package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import com.anjing.config.properties.KeywordSearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch keyword recall adapter.
 *
 * <p>It keeps external search engine calls behind the scaffold RemoteHttpClient,
 * while RetrievalHybridSearchService still owns RRF merge and score explanation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.keyword-search", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchKeywordSearchProvider implements KeywordSearchProvider {

    private static final String TARGET_SERVICE = "keyword-search-provider";

    private final RemoteHttpClient remoteHttpClient;
    private final KeywordSearchProperties keywordSearchProperties;

    @Override
    public List<KeywordSearchHit> search(String query, List<String> kbIds, int candidateCount) {
        if (query == null || query.isBlank() || kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }

        List<KeywordSearchHit> hits = new ArrayList<>();
        int size = Math.max(1, candidateCount);
        for (String kbId : kbIds) {
            hits.addAll(searchKnowledgeBase(query, kbId, size));
        }

        List<KeywordSearchHit> results = hits.stream()
                .sorted(Comparator.comparing(KeywordSearchHit::score).reversed())
                .limit(size)
                .toList();
        log.info("Elasticsearch 关键词召回完成: kbCount={}, hitCount={}, resultCount={}",
                kbIds.size(), hits.size(), results.size());
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<KeywordSearchHit> searchKnowledgeBase(String query, String kbId, int candidateCount) {
        try {
            Map<String, Object> response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .url(searchUrl(kbId))
                            .targetService(TARGET_SERVICE)
                            .headers(jsonHeaders())
                            .body(searchBody(query, candidateCount))
                            .checkResponse(false)
                            .build(),
                    Map.class
            );
            return extractHits(response, kbId);
        } catch (Exception error) {
            log.error("Elasticsearch 关键词召回失败: kbId={}, message={}", kbId, error.getMessage(), error);
            return List.of();
        }
    }

    private Map<String, Object> searchBody(String query, int candidateCount) {
        KeywordSearchProperties.Elasticsearch properties = keywordSearchProperties.getElasticsearch();
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", query);
        multiMatch.put("fields", properties.getFields());
        multiMatch.put("type", "best_fields");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", candidateCount);
        body.put("_source", properties.getSourceFields());
        body.put("query", Map.of("multi_match", multiMatch));
        return body;
    }

    private List<KeywordSearchHit> extractHits(Map<String, Object> response, String fallbackKbId) {
        if (response == null || response.isEmpty()) {
            return List.of();
        }

        Object hitsValue = response.get("hits");
        if (!(hitsValue instanceof Map<?, ?> hitsMap)) {
            return List.of();
        }

        Object itemsValue = hitsMap.get("hits");
        if (!(itemsValue instanceof List<?> items)) {
            return List.of();
        }

        List<KeywordSearchHit> results = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> hit)) {
                continue;
            }
            Map<?, ?> source = source(hit.get("_source"));
            String chunkId = firstText(source, "chunkId", "chunk_id");
            if (chunkId == null || chunkId.isBlank()) {
                chunkId = text(hit.get("_id"));
            }
            if (chunkId == null || chunkId.isBlank()) {
                continue;
            }
            String kbId = firstText(source, "kbId", "kb_id");
            String content = firstText(source, "content", "text");
            Float score = asFloat(hit.get("_score"));
            results.add(new KeywordSearchHit(
                    chunkId,
                    kbId == null || kbId.isBlank() ? fallbackKbId : kbId,
                    content == null ? "" : content,
                    score == null ? 0.0f : score
            ));
        }
        return results;
    }

    private Map<?, ?> source(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private String firstText(Map<?, ?> map, String firstKey, String secondKey) {
        String first = text(map.get(firstKey));
        return first == null || first.isBlank() ? text(map.get(secondKey)) : first;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Float asFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Float.parseFloat(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String searchUrl(String kbId) {
        KeywordSearchProperties.Elasticsearch properties = keywordSearchProperties.getElasticsearch();
        return stripTrailingSlash(properties.getBaseUrl())
                + "/"
                + properties.getIndexPrefix()
                + kbId
                + "/_search";
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:9200";
        }
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        String apiKey = keywordSearchProperties.getElasticsearch().getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey);
        }
        return headers;
    }
}
