package com.anjing.knowledge.model.entity;

import com.anjing.util.DateUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 🧩 Chunk实体（文档分块）
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "chunk", indexes = {
        @Index(name = "idx_chunk_doc_id", columnList = "doc_id"),
        @Index(name = "idx_chunk_kb_id", columnList = "kb_id"),
        @Index(name = "idx_chunk_embedding_status", columnList = "embedding_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chunk {
    
    /**
     * Chunk ID,主键
     */
    @Id
    @Column(name = "chunk_id", length = 64, nullable = false)
    private String chunkId;
    
    /**
     * 文档ID
     */
    @Column(name = "doc_id", length = 64, nullable = false)
    private String docId;
    
    /**
     * 知识库ID
     */
    @Column(name = "kb_id", length = 64, nullable = false)
    private String kbId;
    
    /**
     * 任务ID(chunking_task)
     */
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;
    
    /**
     * Chunk内容
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;
    
    /**
     * Chunk序号（从0开始）
     */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;
    
    /**
     * Chunk长度（字符数）
     */
    @Column(name = "chunk_length", nullable = false)
    private Integer chunkLength;
    
    /**
     * Token数量
     */
    @Column(name = "token_count")
    private Integer tokenCount;
    
    /**
     * 元数据（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;
    
    /**
     * 向量数据库中的ID（Milvus的ID）
     */
    @Column(name = "vector_id", length = 128)
    private String vectorId;
    
    /**
     * 向量化状态：0-未向量化，1-向量化中，2-已向量化，3-向量化失败
     */
    @Column(name = "embedding_status")
    private Integer embeddingStatus = 0;
    
    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateUtils.nowLocalDateTime();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateUtils.nowLocalDateTime();
    }
}
