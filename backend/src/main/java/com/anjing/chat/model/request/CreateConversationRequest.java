package com.anjing.chat.model.request;

import lombok.Data;

import java.util.List;

/**
 * 创建会话请求
 */
@Data
public class CreateConversationRequest {

    /**
     * 会话标题（可选，不提供则自动生成）
     */
    private String title;

    /**
     * 会话描述
     */
    private String description;

    /**
     * 关联的知识库ID列表
     */
    private List<String> kbIds;

    /**
     * 会话配置
     */
    private ConversationConfig config;

    @Data
    public static class ConversationConfig {
        /**
         * 使用的模型ID
         */
        private String modelId;

        /**
         * 系统提示词
         */
        private String systemPrompt;

        /**
         * 是否启用知识检索
         */
        private Boolean enableRetrieval = true;

        /**
         * 检索结果数量
         */
        private Integer topK = 5;

        /**
         * 相似度阈值
         */
        private Float similarityThreshold = 0.5f;

        /**
         * 是否启用Rerank
         */
        private Boolean enableRerank = false;

        /**
         * 温度参数
         */
        private Float temperature = 0.7f;

        /**
         * 最大输出tokens
         */
        private Integer maxTokens = 2048;
    }
}

