package com.anjing.chat.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 发送消息请求
 */
@Data
public class SendMessageRequest {

    /**
     * 会话ID（由 Controller 从 URL 路径注入，请求体无需传）
     */
    private String conversationId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 关联的知识库ID列表（可覆盖会话级配置）
     */
    private List<String> kbIds;

    /**
     * 附加的图片URL列表（用于多模态）
     */
    private List<String> imageUrls;

    /**
     * 附加的文件ID列表
     */
    private List<String> fileIds;

    /**
     * 是否流式响应
     */
    private Boolean stream = true;

    /**
     * 临时覆盖的配置
     */
    private OverrideConfig overrideConfig;

    @Data
    public static class OverrideConfig {
        private String modelId;
        private Float temperature;
        private Integer maxTokens;
        private Boolean enableRetrieval;
        private Integer topK;
    }
}

