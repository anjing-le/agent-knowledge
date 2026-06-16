package com.anjing.demo.controller;

import com.anjing.annotation.ScaffoldSample;
import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.demo.model.response.RagEvidenceReportResponse;
import com.anjing.demo.model.response.RagRetrievalEvaluationResponse;
import com.anjing.demo.service.RagEvidenceReportService;
import com.anjing.demo.service.RagRetrievalEvaluationService;
import com.anjing.demo.service.RagDemoSeedService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only RAG teaching demo endpoints.
 */
@ScaffoldSample("本地教学 Demo：为 dev/test 环境生成 RAG 最小演示数据")
@Profile({"dev", "test"})
@RestController
@RequestMapping(ApiConstants.Test.BASE)
@RequiredArgsConstructor
@Tag(name = "RAG Demo Seed", description = "Local dev/test RAG teaching demo data")
public class RagDemoController {

    private final RagDemoSeedService ragDemoSeedService;
    private final RagRetrievalEvaluationService ragRetrievalEvaluationService;
    private final RagEvidenceReportService ragEvidenceReportService;

    @PostMapping(ApiConstants.Test.RAG_DEMO_SEED)
    @Operation(summary = "Seed local RAG teaching demo")
    public APIResponse<RagDemoSeedResponse> seedRagDemo() {
        return APIResponse.success(ragDemoSeedService.seedTeachingDemo(), "RAG demo data seeded");
    }

    @PostMapping(ApiConstants.Test.RAG_DEMO_RETRIEVAL_EVALUATION)
    @Operation(summary = "Evaluate local RAG retrieval demo")
    public APIResponse<RagRetrievalEvaluationResponse> evaluateRetrieval() {
        return APIResponse.success(ragRetrievalEvaluationService.evaluateDemoRetrieval(), "RAG retrieval evaluated");
    }

    @PostMapping(ApiConstants.Test.RAG_DEMO_EVIDENCE_REPORT)
    @Operation(summary = "Build local RAG teaching evidence report")
    public APIResponse<RagEvidenceReportResponse> evidenceReport() {
        return APIResponse.success(ragEvidenceReportService.buildTeachingEvidenceReport(),
                "RAG demo evidence report built");
    }
}
