package com.anjing.demo.service;

import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.demo.model.response.RagRetrievalEvaluationResponse;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Runs a deterministic retrieval evaluation suite against the local RAG demo dataset.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalEvaluationService {

    private static final int TOP_K = 3;
    private static final int CANDIDATE_COUNT = 6;
    private static final String SUITE_NAME = "rag-demo-retrieval-v1";

    private final RagDemoSeedService ragDemoSeedService;
    private final RetrievalService retrievalService;

    public RagRetrievalEvaluationResponse evaluateDemoRetrieval() {
        RagDemoSeedResponse demo = ragDemoSeedService.seedTeachingDemo();
        List<EvaluationCase> cases = demoCases();
        List<RagRetrievalEvaluationResponse.CaseResult> results = cases.stream()
                .map(evaluationCase -> evaluateCase(demo.getKbId(), evaluationCase))
                .toList();

        int passedCount = (int) results.stream().filter(RagRetrievalEvaluationResponse.CaseResult::getPassed).count();
        float recallAtK = cases.isEmpty() ? 0.0f : (float) passedCount / cases.size();

        RagRetrievalEvaluationResponse response = new RagRetrievalEvaluationResponse();
        response.setSuiteName(SUITE_NAME);
        response.setKbId(demo.getKbId());
        response.setTopK(TOP_K);
        response.setTotalCases(cases.size());
        response.setPassedCases(passedCount);
        response.setRecallAtK(recallAtK);
        response.setPassed(passedCount == cases.size());
        response.setCases(results);
        response.setEvidenceCommands(List.of(
                "./scripts/evaluate-rag-retrieval.sh",
                "./scripts/smoke-rag-demo.sh",
                "./scripts/check-contracts.sh"
        ));

        log.info("RAG retrieval evaluation completed: suite={}, passed={}/{}, recallAtK={}",
                SUITE_NAME, passedCount, cases.size(), recallAtK);
        return response;
    }

    private RagRetrievalEvaluationResponse.CaseResult evaluateCase(String kbId, EvaluationCase evaluationCase) {
        List<SearchResult> results = retrievalService.search(searchRequest(kbId, evaluationCase.query()));
        List<String> hitChunkIds = results.stream()
                .map(SearchResult::getChunkId)
                .filter(Objects::nonNull)
                .toList();
        Integer expectedRank = expectedRank(hitChunkIds, evaluationCase.expectedChunkIds());

        RagRetrievalEvaluationResponse.CaseResult result = new RagRetrievalEvaluationResponse.CaseResult();
        result.setQuery(evaluationCase.query());
        result.setExpectedChunkIds(evaluationCase.expectedChunkIds());
        result.setHitChunkIds(hitChunkIds);
        result.setTopChunkId(hitChunkIds.isEmpty() ? null : hitChunkIds.get(0));
        result.setExpectedRank(expectedRank);
        result.setPassed(expectedRank != null && expectedRank <= TOP_K);
        if (!results.isEmpty()) {
            result.setTopScoreExplanation(results.get(0).getScoreExplanation());
        }
        return result;
    }

    private SearchRequest searchRequest(String kbId, String query) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setKbIds(List.of(kbId));
        request.setTopK(TOP_K);
        request.setCandidateCount(CANDIDATE_COUNT);
        request.setSimilarityThreshold(0.0f);
        request.setHybrid(true);
        request.setRerank(true);
        request.setRerankLlmId("local-lexical");
        return request;
    }

    private Integer expectedRank(List<String> hitChunkIds, List<String> expectedChunkIds) {
        for (int index = 0; index < hitChunkIds.size(); index++) {
            if (expectedChunkIds.contains(hitChunkIds.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private List<EvaluationCase> demoCases() {
        return List.of(
                new EvaluationCase(
                        "脚手架提供哪些工程最佳实践？",
                        List.of("chunk_rag_demo_teaching_001")
                ),
                new EvaluationCase(
                        "RAG 链路包含哪些阶段？",
                        List.of("chunk_rag_demo_teaching_002")
                ),
                new EvaluationCase(
                        "Java 后端和 agent-doc-parser 的边界是什么？",
                        List.of("chunk_rag_demo_teaching_003")
                )
        );
    }

    private record EvaluationCase(String query, List<String> expectedChunkIds) {
    }
}
