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
}
