package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Message;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息响应
 */
@Slf4j
@Data
public class MessageResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String messageId;
    private String conversationId;
    private String role;
    private String content;
    private List<ReferenceInfo> references;
    private Map<String, Object> metadata;
    private Integer sequence;
    private LocalDateTime createdAt;

    /**
     * 引用信息
     */
    @Data
    public static class ReferenceInfo {
        private String chunkId;
        private String docId;
        private String docName;
        private String kbId;
        private String kbName;
        private String content;
        private Float score;
        private Float similarityScore;
        private Float finalScore;
    }

    public static MessageResponse fromEntity(Message entity) {
        MessageResponse response = new MessageResponse();
        response.setMessageId(entity.getMessageId());
        response.setConversationId(entity.getConversationId());
        response.setRole(entity.getRole());
        response.setContent(entity.getContent());
        response.setSequence(entity.getSequence());
        response.setCreatedAt(entity.getCreatedAt());

        if (entity.getReferences() != null && !entity.getReferences().isEmpty()) {
            try {
                List<ReferenceInfo> refs = MAPPER.readValue(entity.getReferences(),
                        new TypeReference<List<ReferenceInfo>>() {});
                for (ReferenceInfo ref : refs) {
                    if (ref.getScore() == null) {
                        ref.setScore(ref.getFinalScore() != null ? ref.getFinalScore() : ref.getSimilarityScore());
                    }
                }
                response.setReferences(refs);
            } catch (Exception e) {
                log.warn("解析消息引用信息失败: messageId={}", entity.getMessageId());
            }
        }
        return response;
    }
}

