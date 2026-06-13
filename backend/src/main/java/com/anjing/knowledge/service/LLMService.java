package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import com.anjing.knowledge.model.response.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM 大模型服务
 *
 * 调用 OpenAI 兼容接口生成 RAG 回答
 */
@Slf4j
@Service
public class LLMService {

    @Value("${app.llm.api-url:https://llm.onerouter.pro/v1/chat/completions}")
    private String apiUrl;

    @Value("${app.llm.provider:remote}")
    private String provider;

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${app.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${app.llm.max-tokens:2048}")
    private int maxTokens;

    @Value("${app.llm.temperature:0.7}")
    private float temperature;

    private final RemoteHttpClient remoteHttpClient;
    private final RagPromptBuilderService promptBuilderService;

    public LLMService(RemoteHttpClient remoteHttpClient, RagPromptBuilderService promptBuilderService) {
        this.remoteHttpClient = remoteHttpClient;
        this.promptBuilderService = promptBuilderService;
    }

    /**
     * 基于检索结果生成 RAG 回答（无历史）
     */
    public String generateRAGResponse(String userMessage, List<SearchResult> searchResults) {
        return generateRAGResponse(userMessage, searchResults, null);
    }

    /**
     * 基于检索结果 + 对话历史生成 RAG 回答
     *
     * @param userMessage    用户问题
     * @param searchResults  检索到的文档片段
     * @param historyMessages 历史对话，格式 [{role, content}, ...]，可为 null
     */
    public String generateRAGResponse(String userMessage, List<SearchResult> searchResults,
                                       List<Map<String, String>> historyMessages) {
        if (isLocalDemoProvider()) {
            return localDemoRagResponse(userMessage, searchResults);
        }
        String systemPrompt = promptBuilderService.buildRagSystemPrompt(searchResults);
        return chat(systemPrompt, userMessage, historyMessages);
    }

    /**
     * 通用对话（支持历史消息）
     */
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userMessage, List<Map<String, String>> historyMessages) {
        if (isLocalDemoProvider()) {
            return "本地演示回答：我已收到问题「" + safeTrim(userMessage, 80)
                    + "」。当前使用 local-demo provider，生产环境请配置真实 LLM provider。";
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            if (historyMessages != null) {
                messages.addAll(historyMessages);
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);

            Map response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .url(apiUrl)
                            .targetService("llm-provider")
                            .headers(jsonHeaders())
                            .body(body)
                            .checkResponse(false)
                            .build(),
                    Map.class
            );

            if (response != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("LLM 响应生成成功: model={}, responseLength={}", model, content.length());
                    return content;
                }
            }

            log.error("LLM API 调用失败: 响应为空或 choices 为空");
            return "抱歉，AI 生成回答失败，请稍后重试。";

        } catch (Exception e) {
            log.error("LLM 调用异常: {}", e.getMessage(), e);
            return "抱歉，AI 服务暂时不可用：" + e.getMessage();
        }
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

    private String localDemoRagResponse(String userMessage, List<SearchResult> searchResults) {
        StringBuilder answer = new StringBuilder();
        answer.append("本地演示回答：围绕「")
                .append(safeTrim(userMessage, 80))
                .append("」，可以先根据当前检索结果这样理解。");

        if (searchResults == null || searchResults.isEmpty()) {
            answer.append("\n\n当前没有检索到可引用片段，请先上传文档、完成切片和向量化，或检查知识库选择。");
            return answer.toString();
        }

        int count = Math.min(3, searchResults.size());
        for (int i = 0; i < count; i++) {
            SearchResult result = searchResults.get(i);
            answer.append("\n\n")
                    .append(i + 1)
                    .append(". ")
                    .append(safeTrim(result.getContent(), 160));
            if (result.getDocName() != null && !result.getDocName().isBlank()) {
                answer.append("（来源：").append(result.getDocName()).append("）");
            }
        }
        answer.append("\n\n这是 local-demo provider 生成的教学回答；引用仍来自真实检索结果。");
        return answer.toString();
    }

    private String safeTrim(String value, int maxLength) {
        String text = Optional.ofNullable(value).orElse("").trim().replaceAll("\\s+", " ");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
