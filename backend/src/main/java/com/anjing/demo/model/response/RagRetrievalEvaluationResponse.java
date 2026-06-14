package com.anjing.demo.model.response;

import lombok.Data;

import java.util.List;

/**
 * Local retrieval evaluation result for the RAG teaching demo.
 */
@Data
public class RagRetrievalEvaluationResponse {

    private String suiteName;
    private String kbId;
    private Integer topK;
    private Integer totalCases;
    private Integer passedCases;
    private Float recallAtK;
    private Boolean passed;
    private List<CaseResult> cases;
    private List<String> evidenceCommands;

    @Data
    public static class CaseResult {
        private String query;
        private List<String> expectedChunkIds;
        private List<String> hitChunkIds;
        private String topChunkId;
        private Integer expectedRank;
        private Boolean passed;
        private String topScoreExplanation;
    }
}
