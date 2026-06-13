package com.anjing.knowledge.model.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 知识检索结果
 */
@Data
public class SearchResult {

    /**
     * Chunk ID
     */
    private String chunkId;

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 知识库ID
     */
    private String kbId;

    /**
     * Chunk内容
     */
    private String content;

    /**
     * 相似度分数（向量搜索）
     */
    private Float similarityScore;

    /**
     * Rerank分数（如果启用了Rerank）
     */
    private Float rerankScore;

    /**
     * 最终分数
     */
    private Float finalScore;

    /**
     * 检索排序名次
     */
    private Integer rank;

    /**
     * 分数解释，用于检索调试和教学演示
     */
    private String scoreExplanation;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 知识库名称
     */
    private String kbName;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 高亮内容（如果启用了高亮）
     */
    private String highlightContent;
}
