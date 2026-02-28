package com.anjing.chat.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 💬 会话实体
 * 
 * @author Chat Service Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "conversation", indexes = {
        @Index(name = "idx_conv_created_at", columnList = "created_at"),
        @Index(name = "idx_conv_is_deleted", columnList = "is_deleted")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    /**
     * 会话ID
     */
    @Id
    @Column(name = "conversation_id", length = 64, nullable = false)
    private String conversationId;

    /**
     * 会话标题
     */
    @Column(name = "title", length = 255)
    private String title;

    /**
     * 会话描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 关联的知识库ID列表（JSON数组）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_ids", columnDefinition = "JSON")
    private String kbIds;

    /**
     * 会话配置（JSON格式）
     * 包含：模型配置、检索配置等
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "JSON")
    private String config;

    /**
     * 消息数量
     */
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    /**
     * 是否删除
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
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

