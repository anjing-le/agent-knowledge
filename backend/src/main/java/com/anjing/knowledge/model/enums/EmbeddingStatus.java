package com.anjing.knowledge.model.enums;

/**
 * 向量化状态枚举
 */
public enum EmbeddingStatus {
    
    /**
     * 未向量化
     */
    NOT_EMBEDDED(0, "未向量化"),
    
    /**
     * 向量化中
     */
    EMBEDDING(1, "向量化中"),
    
    /**
     * 已向量化
     */
    EMBEDDED(2, "已向量化"),
    
    /**
     * 向量化失败
     */
    FAILED(3, "向量化失败");

    private final Integer code;
    private final String description;

    EmbeddingStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static EmbeddingStatus fromCode(Integer code) {
        if (code == null) {
            return NOT_EMBEDDED;
        }
        for (EmbeddingStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return NOT_EMBEDDED;
    }
}

