package com.anjing.chat.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 💬 消息实体
 * 
 * @author Chat Service Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_msg_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_msg_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * 消息ID
     */
    @Id
    @Column(name = "message_id", length = 64, nullable = false)
    private String messageId;

    /**
     * 会话ID
     */
    @Column(name = "conversation_id", length = 64, nullable = false)
    private String conversationId;

    /**
     * 消息角色：user, assistant, system
     */
    @Column(name = "role", length = 32, nullable = false)
    private String role;

    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    /**
     * 引用的知识来源（JSON数组）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "`references`", columnDefinition = "JSON")
    private String references;

    /**
     * 消息元数据（JSON格式）
     * 包含：token数量、模型信息、耗时等
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    /**
     * 父消息ID（用于消息链）
     */
    @Column(name = "parent_message_id", length = 64)
    private String parentMessageId;

    /**
     * 消息序号
     */
    @Column(name = "sequence", nullable = false)
    private Integer sequence = 0;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

