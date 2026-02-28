package com.anjing.knowledge.model.response;

import com.anjing.knowledge.model.entity.Document;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档响应
 */
@Data
public class DocumentResponse {

    private String docId;
    private String kbId;
    private String fileId;
    private String docName;
    private String docType;
    private Long docSize;
    private String status;
    private Float progress;
    private String progressMsg;
    private String parserStrategyId;
    private String chunkStrategyId;
    private Integer chunkNum;
    private Integer tokenNum;
    private Integer imageNum;
    private String thumbnail;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    public static DocumentResponse fromEntity(Document entity) {
        DocumentResponse response = new DocumentResponse();
        response.setDocId(entity.getDocId());
        response.setKbId(entity.getKbId());
        response.setFileId(entity.getFileId());
        response.setDocName(entity.getDocName());
        response.setDocType(entity.getDocType());
        response.setDocSize(entity.getDocSize());
        response.setStatus(entity.getStatus());
        response.setProgress(entity.getProgress());
        response.setProgressMsg(entity.getProgressMsg());
        response.setParserStrategyId(entity.getParserStrategyId());
        response.setChunkStrategyId(entity.getChunkStrategyId());
        response.setChunkNum(entity.getChunkNum());
        response.setTokenNum(entity.getTokenNum());
        response.setImageNum(entity.getImageNum());
        response.setThumbnail(entity.getThumbnail());
        response.setIsEnabled(entity.getIsEnabled());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setCompletedAt(entity.getCompletedAt());
        return response;
    }
}

