package com.anjing.knowledge.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Parser-neutral document parse result consumed by the RAG ingestion pipeline.
 */
@Data
public class DocumentParseResult {

    private boolean success;
    private String content;
    private List<ChunkData> chunks;
    private Map<String, Object> metadata;
    private String errorMessage;

    public static DocumentParseResult error(String message) {
        DocumentParseResult result = new DocumentParseResult();
        result.setSuccess(false);
        result.setErrorMessage(message);
        return result;
    }

    @Data
    public static class ChunkData {
        private String content;
        private int index;
        private int length;
        private int tokenCount;
        private Map<String, Object> metadata;
    }
}
