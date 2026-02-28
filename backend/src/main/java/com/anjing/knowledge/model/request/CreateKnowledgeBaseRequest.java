package com.anjing.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class CreateKnowledgeBaseRequest {

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
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
     * 分块大小（字符数），默认500
     */
    private Integer chunkSize = 500;

    /**
     * 分块重叠大小（字符数），默认50
     */
    private Integer chunkOverlap = 50;

    /**
     * 知识库类型
     */
    private String kbType;

    /**
     * 是否启用RAPTOR
     */
    private Boolean raptorEnabled = false;

    /**
     * RAPTOR配置（JSON字符串）
     */
    private String raptorConfig;
}

