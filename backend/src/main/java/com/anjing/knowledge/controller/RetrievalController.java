package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.RetrievalService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识检索Controller
 * 
 * 提供知识检索API，供Chat服务调用
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.Retrieval.BASE)
@RequiredArgsConstructor
@Tag(name = "Retrieval", description = "RAG 检索接口")
public class RetrievalController {

    private final RetrievalService retrievalService;

    /**
     * 知识检索
     * 
     * @param request 检索请求
     * @return 检索结果列表
     */
    @PostMapping(ApiConstants.Retrieval.SEARCH)
    @Operation(summary = "知识检索")
    public APIResponse<List<SearchResult>> search(@Valid @RequestBody SearchRequest request) {
        log.info("知识检索请求: query={}, kbIds={}, topK={}", 
                request.getQuery(), request.getKbIds(), request.getTopK());
        
        List<SearchResult> results = retrievalService.search(request);
        
        log.info("知识检索完成: resultCount={}", results.size());
        return APIResponse.success(results);
    }

    /**
     * 简单检索（GET方式，用于快速测试）
     */
    @GetMapping(ApiConstants.Retrieval.SIMPLE)
    @Operation(summary = "简单检索")
    public APIResponse<List<SearchResult>> simpleSearch(
            @RequestParam String query,
            @RequestParam String kbId,
            @RequestParam(defaultValue = "5") Integer topK) {
        
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setKbIds(List.of(kbId));
        request.setTopK(topK);
        request.setSimilarityThreshold(0.0f);
        
        List<SearchResult> results = retrievalService.search(request);
        return APIResponse.success(results);
    }
}
