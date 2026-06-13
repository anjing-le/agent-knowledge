package com.anjing.knowledge.model.response;

import com.anjing.knowledge.model.entity.Chunk;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Chunk response payload.
 */
@Data
public class ChunkResponse {

    private String chunkId;
    private String docId;
    private String kbId;
    private String content;
    private Integer chunkIndex;
    private Integer chunkLength;
    private Integer tokenCount;
    private String metadata;
    private String vectorId;
    private Integer embeddingStatus;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChunkResponse fromEntity(Chunk entity) {
        ChunkResponse response = new ChunkResponse();
        response.setChunkId(entity.getChunkId());
        response.setDocId(entity.getDocId());
        response.setKbId(entity.getKbId());
        response.setContent(entity.getContent());
        response.setChunkIndex(entity.getChunkIndex());
        response.setChunkLength(entity.getChunkLength());
        response.setTokenCount(entity.getTokenCount());
        response.setMetadata(entity.getMetadata());
        response.setVectorId(entity.getVectorId());
        response.setEmbeddingStatus(entity.getEmbeddingStatus());
        response.setIsEnabled(entity.getIsEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
