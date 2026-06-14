package com.anjing.knowledge.model.response;

import lombok.Data;

import java.util.List;

/**
 * Teaching trace for how retrieval results are assembled into the RAG prompt context.
 */
@Data
public class RagContextTrace {

    private String assemblyStrategy;
    private String contextWindowPolicy;
    private Integer referenceCount;
    private Integer includedChunkCount;
    private Integer historyMessageCount;
    private Integer promptCharCount;
    private Integer contextCharCount;
    private List<String> promptSections;
    private List<IncludedChunk> includedChunks;

    @Data
    public static class IncludedChunk {
        private Integer rank;
        private String chunkId;
        private String docId;
        private String docName;
        private String kbId;
        private String kbName;
        private String retrievalSource;
        private Float finalScore;
        private Integer contentChars;
        private String scoreExplanation;
    }
}
