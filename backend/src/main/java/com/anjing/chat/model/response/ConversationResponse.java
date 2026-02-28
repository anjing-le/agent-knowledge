package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Conversation;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话响应
 */
@Data
public class ConversationResponse {

    private String conversationId;
    private String title;
    private String description;
    private List<String> kbIds;
    private Object config;
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
        // TODO: 解析JSON字段
        return response;
    }
}

