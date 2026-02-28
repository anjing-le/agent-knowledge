package com.anjing.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 知识检索请求
 */
@Data
public class SearchRequest {

    /**
     * 搜索查询文本
     */
    @NotBlank(message = "查询文本不能为空")
    private String query;

    /**
     * 知识库ID列表
     */
    @NotEmpty(message = "知识库ID列表不能为空")
    private List<String> kbIds;

    /**
     * 返回结果数量
     */
    private Integer topK = 5;

    /**
     * 相似度阈值（0-1之间）
     */
    private Float similarityThreshold = 0.3f;

    /**
     * 是否启用Rerank
     */
    private Boolean rerank = false;

    /**
     * Rerank候选数量
     */
    private Integer candidateCount = 20;

    /**
     * Rerank使用的LLM ID
     */
    private String rerankLlmId;

    /**
     * 排除的Chunk ID列表
     */
    private List<String> excludeChunkIds;

    /**
     * 排除的文档ID列表
     */
    private List<String> excludeDocIds;
}

