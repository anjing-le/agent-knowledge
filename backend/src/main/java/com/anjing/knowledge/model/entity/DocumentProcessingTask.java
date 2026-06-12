package com.anjing.knowledge.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Processing task for one document ingestion attempt.
 */
@Entity
@Table(name = "document_processing_task", indexes = {
        @Index(name = "idx_doc_task_doc_id", columnList = "doc_id"),
        @Index(name = "idx_doc_task_kb_id", columnList = "kb_id"),
        @Index(name = "idx_doc_task_status", columnList = "status"),
        @Index(name = "idx_doc_task_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingTask {

    @Id
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "doc_id", length = 64, nullable = false)
    private String docId;

    @Column(name = "kb_id", length = 64, nullable = false)
    private String kbId;

    @Column(name = "task_type", length = 32, nullable = false)
    private String taskType = "INGESTION";

    @Column(name = "phase", length = 32, nullable = false)
    private String phase = "PENDING";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "progress", nullable = false)
    private Float progress = 0.0f;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "parser_task_id", length = 128)
    private String parserTaskId;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
