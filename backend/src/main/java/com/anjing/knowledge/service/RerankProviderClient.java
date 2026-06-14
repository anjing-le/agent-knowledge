package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote rerank provider adapter using the scaffold RemoteHttpClient.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankProviderClient {

    @Value("${app.rerank.api-url:https://api.cohere.com/v2/rerank}")
    private String apiUrl = "https://api.cohere.com/v2/rerank";

    @Value("${app.rerank.api-key:}")
    private String apiKey = "";

    @Value("${app.rerank.model:rerank-v3.5}")
    private String model = "rerank-v3.5";

    private final RemoteHttpClient remoteHttpClient;

    @SuppressWarnings("unchecked")
    public List<Float> rerank(String query, List<String> documents, String rerankModel) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        String actualModel = rerankModel == null || rerankModel.isBlank() ? model : rerankModel;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", actualModel);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", documents.size());

            Map<String, Object> response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .url(apiUrl)
                            .targetService("rerank-provider")
                            .headers(jsonHeaders())
                            .body(body)
                            .checkResponse(false)
                            .build(),
                    Map.class
            );

            List<Float> scores = extractScores(response, documents.size());
            log.info("远程 Rerank 响应完成: documentCount={}, scoreCount={}", documents.size(), scores.size());
            return scores;
        } catch (Exception error) {
            log.error("远程 Rerank 调用失败: {}", error.getMessage(), error);
            return Collections.emptyList();
        }
    }

    private List<Float> extractScores(Map<String, Object> response, int expectedSize) {
        if (response == null || response.isEmpty() || expectedSize <= 0) {
            return Collections.emptyList();
        }

        List<Float> directScores = extractDirectScores(response.get("scores"), expectedSize);
        if (!directScores.isEmpty()) {
            return directScores;
        }

        List<Float> rankedScores = extractRankedScores(response.get("results"), expectedSize);
        if (!rankedScores.isEmpty()) {
            return rankedScores;
        }
        return extractRankedScores(response.get("data"), expectedSize);
    }

    private List<Float> extractDirectScores(Object value, int expectedSize) {
        if (!(value instanceof List<?> items) || items.size() != expectedSize) {
            return Collections.emptyList();
        }

        List<Float> scores = new ArrayList<>(expectedSize);
        for (Object item : items) {
            Float score = asFloat(item);
            if (score == null) {
                return Collections.emptyList();
            }
            scores.add(score);
        }
        return scores;
    }

    private List<Float> extractRankedScores(Object value, int expectedSize) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<Float> scores = new ArrayList<>(Collections.nCopies(expectedSize, 0.0f));
        boolean matched = false;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> result)) {
                continue;
            }
            Integer index = asInteger(firstPresent(result, "index", "document_index"));
            Float score = asFloat(firstPresent(result, "relevance_score", "score"));
            if (index == null || score == null || index < 0 || index >= expectedSize) {
                continue;
            }
            scores.set(index, score);
            matched = true;
        }
        return matched ? scores : Collections.emptyList();
    }

    private Object firstPresent(Map<?, ?> map, String firstKey, String secondKey) {
        Object first = map.get(firstKey);
        return first == null ? map.get(secondKey) : first;
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

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return headers;
    }
}
