package com.anjing.knowledge.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新知识库请求
 */
@Data
public class UpdateKnowledgeBaseRequest {

    /**
     * 知识库名称
     */
    @Size(max = 128, message = "知识库名称长度不能超过128")
    private String name;

    /**
     * 描述信息
     */
    @Size(max = 2000, message = "描述信息长度不能超过2000")
    private String description;

    /**
     * 头像base64
     */
    private String avatar;

    /**
     * 向量化模型名称
     */
    private String embeddingModel;

    /**
     * 分块大小（字符数）
     */
    private Integer chunkSize;

    /**
     * 分块重叠大小（字符数）
     */
    private Integer chunkOverlap;

    /**
     * 是否启用RAPTOR
     */
    private Boolean raptorEnabled;

    /**
     * RAPTOR配置（JSON字符串）
     */
    private String raptorConfig;

    /**
     * 是否启用
     */
    private Boolean isEnabled;
}

