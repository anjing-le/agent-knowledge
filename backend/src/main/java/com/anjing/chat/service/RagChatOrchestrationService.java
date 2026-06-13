package com.anjing.chat.service;

import com.anjing.chat.model.entity.Message;
import com.anjing.chat.repository.MessageRepository;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.LLMService;
import com.anjing.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates RAG retrieval, chat history assembly and answer generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatOrchestrationService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final MessageRepository messageRepository;
    private final RetrievalService retrievalService;
    private final LLMService llmService;

    public RagChatAnswer generateAnswer(String conversationId, String userMessage, List<String> kbIds) {
        List<SearchResult> searchResults = retrieveKnowledge(userMessage, kbIds);
        List<Map<String, String>> historyMessages = buildHistoryMessages(conversationId);
        String content = llmService.generateRAGResponse(userMessage, searchResults, historyMessages);

        return new RagChatAnswer(content, searchResults);
    }

    private List<SearchResult> retrieveKnowledge(String query, List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setQuery(query);
            searchRequest.setKbIds(kbIds);
            searchRequest.setTopK(DEFAULT_TOP_K);

            return retrievalService.search(searchRequest);
        } catch (Exception e) {
            log.error("知识检索失败: query={}, error={}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, String>> buildHistoryMessages(String conversationId) {
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        int start = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            history.add(Map.of("role", message.getRole(), "content", message.getContent()));
        }
        log.info("加载对话历史: conversationId={}, historyCount={}", conversationId, history.size());
        return history;
    }

    public record RagChatAnswer(String content, List<SearchResult> references) {
    }
}
