package com.anjing.knowledge.model.response;

import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Document processing task view for ingestion progress.
 */
@Data
public class DocumentProcessingTaskResponse {

    private String taskId;
    private String docId;
    private String kbId;
    private String taskType;
    private String phase;
    private String status;
    private Float progress;
    private String message;
    private String errorMessage;
    private String parserTaskId;
    private Integer retryCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DocumentProcessingTaskResponse fromEntity(DocumentProcessingTask entity) {
        DocumentProcessingTaskResponse response = new DocumentProcessingTaskResponse();
        response.setTaskId(entity.getTaskId());
        response.setDocId(entity.getDocId());
        response.setKbId(entity.getKbId());
        response.setTaskType(entity.getTaskType());
        response.setPhase(entity.getPhase());
        response.setStatus(entity.getStatus());
        response.setProgress(entity.getProgress());
        response.setMessage(entity.getMessage());
        response.setErrorMessage(entity.getErrorMessage());
        response.setParserTaskId(entity.getParserTaskId());
        response.setRetryCount(entity.getRetryCount());
        response.setStartedAt(entity.getStartedAt());
        response.setCompletedAt(entity.getCompletedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
