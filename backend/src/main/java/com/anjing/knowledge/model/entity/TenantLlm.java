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
 * 🤖 LLM模型配置实体
 * 
 * 用于配置Embedding模型、Rerank模型、Chat模型等
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "tenant_llm", indexes = {
        @Index(name = "idx_llm_model_type", columnList = "model_type"),
        @Index(name = "idx_llm_is_default", columnList = "is_default")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantLlm {

    /**
     * 模型配置ID
     */
    @Id
    @Column(name = "llm_id", length = 64, nullable = false)
    private String llmId;

    /**
     * 模型名称（显示名称）
     */
    @Column(name = "llm_name", length = 128, nullable = false)
    private String llmName;

    /**
     * 模型提供商：openai, azure, doubao, zhipu, etc.
     */
    @Column(name = "model_factory", length = 64, nullable = false)
    private String modelFactory;

    /**
     * 模型类型：embedding, rerank, chat, etc.
     */
    @Column(name = "model_type", length = 32, nullable = false)
    private String modelType;

    /**
     * 实际模型名称（API调用时使用）
     */
    @Column(name = "model_name", length = 128, nullable = false)
    private String modelName;

    /**
     * API密钥（加密存储）
     */
    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    /**
     * API基础URL
     */
    @Column(name = "api_base", length = 512)
    private String apiBase;

    /**
     * 是否为默认模型
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /**
     * 模型配置（JSON格式）
     * 包含max_tokens, temperature, dimensions等
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_config", columnDefinition = "JSON")
    private String modelConfig;

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
