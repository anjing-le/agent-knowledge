package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Message;
import com.anjing.knowledge.model.response.RagContextTrace;
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
    private RagContextTrace contextTrace;
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
        private Float keywordScore;
        private Float hybridScore;
        private Float rerankScore;
        private String rerankProvider;
        private String retrievalSource;
        private Integer rank;
        private String scoreExplanation;
        private Map<String, Object> metadata;
    }

    public static MessageResponse fromEntity(Message entity) {
        MessageResponse response = new MessageResponse();
        response.setMessageId(entity.getMessageId());
        response.setConversationId(entity.getConversationId());
        response.setRole(entity.getRole());
        response.setContent(entity.getContent());
        response.setSequence(entity.getSequence());
        response.setCreatedAt(entity.getCreatedAt());
        response.setMetadata(parseMetadata(entity.getMetadata()));
        response.setContextTrace(parseContextTrace(response.getMetadata()));

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

    private static Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(metadata, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析消息元数据失败");
            return Map.of("raw", metadata);
        }
    }

    private static RagContextTrace parseContextTrace(Map<String, Object> metadata) {
        if (metadata == null || !metadata.containsKey("contextTrace")) {
            return null;
        }
        try {
            return MAPPER.convertValue(metadata.get("contextTrace"), RagContextTrace.class);
        } catch (Exception e) {
            log.warn("解析 RAG 上下文组装 trace 失败");
            return null;
        }
    }
}
