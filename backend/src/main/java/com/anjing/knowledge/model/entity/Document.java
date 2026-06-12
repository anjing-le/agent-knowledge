package com.anjing.knowledge.model.entity;

import com.anjing.util.DateUtils;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 📄 文档元信息实体
 * 
 * 对应数据库表：document
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "document", indexes = {
        @Index(name = "idx_doc_kb_id", columnList = "kb_id"),
        @Index(name = "idx_doc_status", columnList = "status"),
        @Index(name = "idx_doc_is_deleted", columnList = "is_deleted"),
        @Index(name = "idx_doc_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    /**
     * 文档ID，主键
     */
    @Id
    @Column(name = "doc_id", length = 64, nullable = false)
    private String docId;

    /**
     * 知识库ID
     */
    @Column(name = "kb_id", length = 64, nullable = false)
    private String kbId;

    /**
     * 关联的文件ID
     */
    @Column(name = "file_id", length = 64, nullable = false)
    private String fileId;

    /**
     * 预览文件ID（用于文档预览）
     */
    @Column(name = "preview_file_id", length = 64)
    private String previewFileId;

    /**
     * 文档名称
     */
    @Column(name = "doc_name", length = 255, nullable = false)
    private String docName;

    /**
     * 文档类型
     */
    @Column(name = "doc_type", length = 32, nullable = false)
    private String docType;

    /**
     * 文档大小（字节）
     */
    @Column(name = "doc_size", nullable = false)
    private Long docSize;

    /**
     * 解析状态：PENDING, PARSING, CHUNKING, EMBEDDING, RAPTORING, COMPLETED, FAILED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    /**
     * 解析进度：0.0-1.0
     */
    @Column(name = "progress", nullable = false)
    private Float progress = 0.0f;

    /**
     * 进度消息/错误信息
     */
    @Column(name = "progress_msg", columnDefinition = "TEXT")
    private String progressMsg;

    /**
     * 使用的解析策略ID
     */
    @Column(name = "parser_strategy_id", length = 64)
    private String parserStrategyId;

    /**
     * 使用的分块策略ID
     */
    @Column(name = "chunk_strategy_id", length = 64)
    private String chunkStrategyId;

    /**
     * 第三方解析服务的任务ID
     */
    @Column(name = "parser_task_id", length = 128)
    private String parserTaskId;

    /**
     * chunk数量
     */
    @Column(name = "chunk_num", nullable = false)
    private Integer chunkNum = 0;

    /**
     * token数量
     */
    @Column(name = "token_num", nullable = false)
    private Integer tokenNum = 0;

    /**
     * 图片数量
     */
    @Column(name = "image_num", nullable = false)
    private Integer imageNum = 0;

    /**
     * 文档元数据（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "doc_meta", columnDefinition = "JSON")
    private String docMeta;

    /**
     * 缩略图
     */
    @Column(name = "thumbnail", columnDefinition = "TEXT")
    private String thumbnail;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 解析完成时间
     */
    @Column(name = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 是否已删除
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateUtils.nowLocalDateTime();
        }
        updatedAt = DateUtils.nowLocalDateTime();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateUtils.nowLocalDateTime();
    }
}
