package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Conversation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 会话响应
 */
@Slf4j
@Data
public class ConversationResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String conversationId;
    private String title;
    private String description;
    private List<String> kbIds;
    private Map<String, Object> config;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ConversationResponse fromEntity(Conversation entity) {
        ConversationResponse response = new ConversationResponse();
        response.setConversationId(entity.getConversationId());
        response.setTitle(entity.getTitle());
        response.setDescription(entity.getDescription());
        response.setMessageCount(entity.getMessageCount());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setKbIds(parseKbIds(entity));
        response.setConfig(parseConfig(entity));
        return response;
    }

    private static List<String> parseKbIds(Conversation entity) {
        if (entity.getKbIds() == null || entity.getKbIds().isBlank()) {
            return Collections.emptyList();
        }

        try {
            return MAPPER.readValue(entity.getKbIds(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析会话知识库列表失败: conversationId={}", entity.getConversationId());
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> parseConfig(Conversation entity) {
        if (entity.getConfig() == null || entity.getConfig().isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return MAPPER.readValue(entity.getConfig(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析会话配置失败: conversationId={}", entity.getConversationId());
            return Collections.emptyMap();
        }
    }
}
