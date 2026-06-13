package com.anjing.demo.controller;

import com.anjing.annotation.ScaffoldSample;
import com.anjing.demo.model.response.RagDemoSeedResponse;
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

    @PostMapping(ApiConstants.Test.RAG_DEMO_SEED)
    @Operation(summary = "Seed local RAG teaching demo")
    public APIResponse<RagDemoSeedResponse> seedRagDemo() {
        return APIResponse.success(ragDemoSeedService.seedTeachingDemo(), "RAG demo data seeded");
    }
}
