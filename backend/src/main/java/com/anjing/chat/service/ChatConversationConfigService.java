package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles conversation knowledge base selection and JSON-backed config fields.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationConfigService {

    private final ObjectMapper objectMapper;

    public void applyCreateRequest(Conversation conversation, CreateConversationRequest request) {
        conversation.setKbIds(serializeKnowledgeBaseIds(request.getKbIds()));
        conversation.setConfig(serializeConfig(request.getConfig()));
    }

    public List<String> resolveKnowledgeBaseIds(SendMessageRequest request, Conversation conversation) {
        if (request.getKbIds() != null) {
            return request.getKbIds();
        }
        if (StringUtils.isBlank(conversation.getKbIds())) {
            return new ArrayList<>();
        }
        return deserializeKnowledgeBaseIds(conversation.getKbIds());
    }

    public void syncKnowledgeBaseIds(Conversation conversation, List<String> kbIds) {
        conversation.setKbIds(serializeKnowledgeBaseIds(kbIds));
    }

    private String serializeKnowledgeBaseIds(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return null;
        }
        return toJson(kbIds);
    }

    private String serializeConfig(CreateConversationRequest.ConversationConfig config) {
        if (config == null) {
            return null;
        }
        return toJson(config);
    }

    private List<String> deserializeKnowledgeBaseIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
