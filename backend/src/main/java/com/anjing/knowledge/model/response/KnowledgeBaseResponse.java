package com.anjing.knowledge.model.response;

import com.anjing.knowledge.model.entity.KnowledgeBase;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库响应
 */
@Data
public class KnowledgeBaseResponse {

    private String kbId;
    private String name;
    private String description;
    private String avatar;
    private String embeddingModel;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String kbType;
    private Boolean raptorEnabled;
    private String raptorConfig;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 统计信息
    private Long documentCount;
    private Long chunkCount;
    private Long tokenCount;

    public static KnowledgeBaseResponse fromEntity(KnowledgeBase entity) {
        KnowledgeBaseResponse response = new KnowledgeBaseResponse();
        response.setKbId(entity.getKbId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setAvatar(entity.getAvatar());
        response.setEmbeddingModel(entity.getEmbeddingModel());
        response.setChunkSize(entity.getChunkSize());
        response.setChunkOverlap(entity.getChunkOverlap());
        response.setKbType(entity.getKbType());
        response.setRaptorEnabled(entity.getRaptorEnabled());
        response.setRaptorConfig(entity.getRaptorConfig());
        response.setIsEnabled(entity.getIsEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}

