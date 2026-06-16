package com.anjing.demo.model.response;

import com.anjing.knowledge.model.response.RetrievalAdapterStatusResponse;
import lombok.Data;

import java.util.List;

/**
 * Backend-built teaching evidence report for the scaffold-grown RAG demo.
 */
@Data
public class RagEvidenceReportResponse {

    private String status;
    private String summary;
    private String markdown;
    private RagDemoSeedResponse demo;
    private RagRetrievalEvaluationResponse evaluation;
    private RetrievalAdapterStatusResponse adapterStatus;
    private List<ReportStat> stats;
    private List<String> scaffoldStack;
    private IngestionBoundary ingestionBoundary;
    private CitationEvidence citationEvidence;
    private List<String> evidenceCommands;

    @Data
    public static class ReportStat {
        private String label;
        private String value;
        private String hint;
    }

    @Data
    public static class IngestionBoundary {
        private String uploadApi;
        private String javaBoundary;
        private String pythonBoundary;
        private String parserContract;
        private String probeCommand;
    }

    @Data
    public static class CitationEvidence {
        private String chatQuestion;
        private String answerPreview;
        private String chatRoute;
        private String assemblyStrategy;
        private String contextWindowPolicy;
        private Integer referenceCount;
        private Integer includedChunkCount;
        private Integer promptCharCount;
        private Integer contextCharCount;
        private List<String> promptSections;
        private List<CitationChunk> includedChunks;
        private List<CitationReference> references;
    }

    @Data
    public static class CitationChunk {
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

    @Data
    public static class CitationReference {
        private Integer rank;
        private String chunkId;
        private String docId;
        private String docName;
        private String kbId;
        private String kbName;
        private String retrievalSource;
        private Float similarityScore;
        private Float finalScore;
        private Float keywordScore;
        private Float hybridScore;
        private Float rerankScore;
        private String rerankProvider;
        private String scoreExplanation;
    }
}
