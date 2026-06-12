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
 * 📋 解析策略实体
 * 
 * 定义文档解析的策略配置
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "parsing_strategy", indexes = {
        @Index(name = "idx_ps_is_default", columnList = "is_default"),
        @Index(name = "idx_ps_is_enabled", columnList = "is_enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsingStrategy {

    /**
     * 策略ID
     */
    @Id
    @Column(name = "strategy_id", length = 64, nullable = false)
    private String strategyId;

    /**
     * 策略名称
     */
    @Column(name = "name", length = 128, nullable = false)
    private String name;

    /**
     * 策略描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 解析器类型：general, ocr, vision等
     */
    @Column(name = "parser_type", length = 64, nullable = false)
    private String parserType;

    /**
     * 解析配置（JSON格式）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parser_config", columnDefinition = "JSON")
    private String parserConfig;

    /**
     * 是否为默认策略
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

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
