package com.anjing.knowledge.model.enums;

/**
 * 文档处理状态枚举
 */
public enum DocumentStatus {
    
    /**
     * 待处理
     */
    PENDING("PENDING", "待处理"),
    
    /**
     * 解析中
     */
    PARSING("PARSING", "解析中"),
    
    /**
     * 分块中
     */
    CHUNKING("CHUNKING", "分块中"),
    
    /**
     * 向量化中
     */
    EMBEDDING("EMBEDDING", "向量化中"),
    
    /**
     * RAPTOR处理中
     */
    RAPTORING("RAPTORING", "RAPTOR处理中"),
    
    /**
     * 处理完成
     */
    COMPLETED("COMPLETED", "处理完成"),
    
    /**
     * 解析失败
     */
    PARSE_FAILED("PARSE_FAILED", "解析失败"),
    
    /**
     * 分块失败
     */
    CHUNK_FAILED("CHUNK_FAILED", "分块失败"),
    
    /**
     * 向量化失败
     */
    EMBEDDING_FAILED("EMBEDDING_FAILED", "向量化失败"),
    
    /**
     * RAPTOR失败
     */
    RAPTOR_FAILED("RAPTOR_FAILED", "RAPTOR失败");

    private final String code;
    private final String description;

    DocumentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static DocumentStatus fromCode(String code) {
        for (DocumentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为终态
     */
    public boolean isFinalState() {
        return this == COMPLETED || this.name().endsWith("FAILED");
    }

    /**
     * 判断是否为失败状态
     */
    public boolean isFailedState() {
        return this.name().endsWith("FAILED");
    }
}

