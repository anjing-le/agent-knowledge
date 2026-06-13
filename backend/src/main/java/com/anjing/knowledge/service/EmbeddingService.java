package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Embedding 向量化服务
 *
 * 调用 OpenAI 兼容接口（OneRouter）生成文本向量
 */
@Slf4j
@Service
public class EmbeddingService {

    @Value("${app.embedding.api-url:https://llm.onerouter.pro/v1/embeddings}")
    private String apiUrl;

    @Value("${app.embedding.provider:remote}")
    private String provider;

    @Value("${app.embedding.api-key:}")
    private String apiKey;

    @Value("${app.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${app.embedding.dimensions:1536}")
    private int dimensions;

    private final RemoteHttpClient remoteHttpClient;

    public EmbeddingService(RemoteHttpClient remoteHttpClient) {
        this.remoteHttpClient = remoteHttpClient;
    }

    /**
     * 对单个文本生成向量（使用全局默认模型）
     */
    public List<Float> embed(String text) {
        return embed(text, null);
    }

    /**
     * 对单个文本生成向量（指定模型）
     */
    public List<Float> embed(String text, String embeddingModel) {
        List<List<Float>> results = embedBatch(List.of(text), embeddingModel);
        return results.isEmpty() ? Collections.emptyList() : results.get(0);
    }

    /**
     * 批量生成向量（使用全局默认模型）
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        return embedBatch(texts, null);
    }

    /**
     * 批量生成向量（指定模型，null 则用全局默认）
     */
    @SuppressWarnings("unchecked")
    public List<List<Float>> embedBatch(List<String> texts, String embeddingModel) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        if (isLocalDemoProvider()) {
            List<List<Float>> embeddings = texts.stream().map(this::localDemoEmbedding).toList();
            log.info("本地演示 Embedding 生成成功: count={}, dimensions={}", texts.size(), dimensions);
            return embeddings;
        }

        String actualModel = (embeddingModel != null && !embeddingModel.isBlank()) ? embeddingModel : this.model;

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", actualModel);
            body.put("input", texts);
            body.put("dimensions", dimensions);

            Map response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .url(apiUrl)
                            .targetService("embedding-provider")
                            .headers(jsonHeaders())
                            .body(body)
                            .checkResponse(false)
                            .build(),
                    Map.class
            );

            if (response != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                List<List<Float>> embeddings = new ArrayList<>();

                for (Map<String, Object> item : data) {
                    List<Number> embedding = (List<Number>) item.get("embedding");
                    List<Float> floatEmbedding = new ArrayList<>();
                    for (Number num : embedding) {
                        floatEmbedding.add(num.floatValue());
                    }
                    embeddings.add(floatEmbedding);
                }

                log.info("Embedding 生成成功: count={}, dimensions={}", texts.size(), 
                        embeddings.isEmpty() ? 0 : embeddings.get(0).size());
                return embeddings;
            } else {
                log.error("Embedding API 调用失败: 响应为空");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Embedding 调用异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取向量维度
     */
    public int getDimensions() {
        return dimensions;
    }

    private Map<String, String> jsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return headers;
    }

    private boolean isLocalDemoProvider() {
        return "local-demo".equalsIgnoreCase(provider);
    }

    private List<Float> localDemoEmbedding(String text) {
        int size = Math.max(1, dimensions);
        int bucketCount = Math.max(1, Math.min(size, 64));
        float[] vector = new float[size];
        vector[0] = 3.0f;

        int[] codePoints = Optional.ofNullable(text).orElse("").toLowerCase(Locale.ROOT).codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int codePoint = codePoints[i];
            if (Character.isWhitespace(codePoint)) {
                continue;
            }
            vector[Math.floorMod(codePoint, bucketCount)] += 1.0f;
            if (i + 1 < codePoints.length && !Character.isWhitespace(codePoints[i + 1])) {
                int bigramHash = Objects.hash(codePoint, codePoints[i + 1]);
                vector[Math.floorMod(bigramHash, bucketCount)] += 0.6f;
            }
        }

        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0.0f) {
            return Collections.nCopies(size, 0.0f);
        }

        float scale = (float) Math.sqrt(norm);
        List<Float> embedding = new ArrayList<>(size);
        for (float value : vector) {
            embedding.add(value / scale);
        }
        return embedding;
    }
}
