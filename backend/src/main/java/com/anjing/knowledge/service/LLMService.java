package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${app.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${app.llm.max-tokens:2048}")
    private int maxTokens;

    @Value("${app.llm.temperature:0.7}")
    private float temperature;

    private final RestTemplate restTemplate;

    public LLMService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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
        String systemPrompt = buildRAGSystemPrompt(searchResults);
        return chat(systemPrompt, userMessage, historyMessages);
    }

    /**
     * 通用对话（支持历史消息）
     */
    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userMessage, List<Map<String, String>> historyMessages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

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

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("LLM 响应生成成功: model={}, responseLength={}", model, content.length());
                    return content;
                }
            }

            log.error("LLM API 调用失败: status={}", response.getStatusCode());
            return "抱歉，AI 生成回答失败，请稍后重试。";

        } catch (Exception e) {
            log.error("LLM 调用异常: {}", e.getMessage(), e);
            return "抱歉，AI 服务暂时不可用：" + e.getMessage();
        }
    }

    /**
     * 构建 RAG 系统提示词
     */
    private String buildRAGSystemPrompt(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "你是一个智能知识库助手。当前没有从知识库中检索到相关内容。请在回答开头加一行：⚠️ 知识库中未找到相关内容，以下为AI自身知识。然后用 **「💡 AI补充」** 标注你的回答内容。";
        }

        StringBuilder context = new StringBuilder();
        context.append("你是一个严谨的知识库问答助手。你必须严格遵守以下规则，违反任何一条都是不可接受的。\n\n");

        context.append("## 核心原则（最高优先级）\n");
        context.append("1. **绝对禁止幻觉**：你只能使用下方【知识库参考内容】中明确存在的信息来回答，不得编造、推测、脑补任何知识库中没有的内容\n");
        context.append("2. **忠于原文**：回答必须忠实于知识库原文的含义，可以重新组织语言，但不得改变原意、不得添加原文没有的细节\n");
        context.append("3. **宁缺毋滥**：如果知识库内容无法回答用户的问题，直接说【知识库中没有找到相关信息】，绝不要用自己的知识去补充\n");
        context.append("4. **相关性判断**：先判断下方参考内容是否与用户的问题真正相关。如果参考内容的主题和用户问题完全无关，即使有参考内容也要回答：❌ 知识库中的内容与您的问题不相关，未找到有效信息。不要强行用不相关的内容去回答\n\n");

        // 收集实际出现的文档名用于 prompt 示例
        Set<String> docNames = new LinkedHashSet<>();
        for (SearchResult r : searchResults) {
            if (r.getDocName() != null) {
                docNames.add(r.getDocName());
            }
        }
        String docNameExample = docNames.isEmpty() ? "未知文档" : docNames.iterator().next();

        context.append("## 回答格式\n");
        context.append("1. 回答开头加一行：✅ 以下回答基于知识库检索结果\n");
        context.append("2. 每段回答末尾必须用括号标注实际的来源文档名，例如：（来源：").append(docNameExample).append("）\n");
        context.append("3. 注意：来源必须是下方参考内容中【来源】字段的真实文档名，禁止写占位符\n");
        context.append("4. 回答要清晰、有条理，适当使用列表和分段\n\n");

        context.append("## 知识库参考内容\n\n");

        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            String docName = result.getDocName() != null ? result.getDocName() : "未知文档";
            context.append(String.format("【参考 %d】来源：%s | 相似度：%.2f\n",
                    i + 1, docName, result.getFinalScore()));
            context.append("内容：");
            context.append(result.getContent());
            context.append("\n\n");
        }

        context.append("---\n");
        context.append("再次提醒：只使用上面的参考内容回答。来源必须写参考内容中的真实文档名（如：").append(docNameExample).append("），不要写占位符。\n");

        return context.toString();
    }
}
