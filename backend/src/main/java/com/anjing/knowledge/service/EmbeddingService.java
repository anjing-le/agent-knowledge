package com.anjing.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @Value("${app.embedding.api-key:}")
    private String apiKey;

    @Value("${app.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${app.embedding.dimensions:1536}")
    private int dimensions;

    private final RestTemplate restTemplate;

    public EmbeddingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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

        String actualModel = (embeddingModel != null && !embeddingModel.isBlank()) ? embeddingModel : this.model;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", actualModel);
            body.put("input", texts);
            body.put("dimensions", dimensions);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
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
                log.error("Embedding API 调用失败: status={}", response.getStatusCode());
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
}
