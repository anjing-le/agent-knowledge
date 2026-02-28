package com.anjing.knowledge.model.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 📚 知识库实体
 * 
 * 对应数据库表：knowledge_base
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "knowledge_base", uniqueConstraints = {
        @UniqueConstraint(name = "uk_name", columnNames = "name")
}, indexes = {
        @Index(name = "idx_kb_name", columnList = "name"),
        @Index(name = "idx_kb_is_deleted", columnList = "is_deleted"),
        @Index(name = "idx_kb_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    /**
     * 知识库ID - 主键
     * 格式：kb_yyyyMMdd_序号
     */
    @Id
    @Column(name = "kb_id", length = 64)
    private String kbId;

    /**
     * 知识库名称
     * 长度限制：1-128字符
     */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /**
     * 描述信息
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 头像base64字符串
     */
    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    /**
     * 向量化模型名称
     */
    @Column(name = "embedding_model", length = 128)
    private String embeddingModel;

    /**
     * 知识库类型
     * 空字符串-普通知识库，chatfile-会话文件知识库
     */
    @Column(name = "kb_type", length = 32)
    private String kbType;

    /**
     * 分块大小（字符数）
     */
    @Column(name = "chunk_size", nullable = false)
    private Integer chunkSize = 500;

    /**
     * 分块重叠大小（字符数）
     */
    @Column(name = "chunk_overlap", nullable = false)
    private Integer chunkOverlap = 50;

    /**
     * 是否启用RAPTOR策略
     */
    @Column(name = "raptor_enabled", nullable = false)
    private Boolean raptorEnabled = false;

    /**
     * RAPTOR配置 - JSON格式
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raptor_config", columnDefinition = "JSON")
    private String raptorConfig;

    /**
     * 是否启用
     */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    /**
     * 是否删除
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // JPA生命周期回调
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }
}

