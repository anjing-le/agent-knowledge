package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.request.CreateKnowledgeBaseRequest;
import com.anjing.knowledge.model.request.UpdateKnowledgeBaseRequest;
import com.anjing.knowledge.model.response.KnowledgeBaseResponse;
import com.anjing.knowledge.service.KnowledgeBaseService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import com.anjing.model.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理Controller
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.Knowledge.BASE)
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "知识库管理接口")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库
     */
    @PostMapping(ApiConstants.Knowledge.BASES)
    @Operation(summary = "创建知识库")
    public APIResponse<KnowledgeBaseResponse> createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        log.info("创建知识库: name={}", request.getName());
        KnowledgeBaseResponse response = knowledgeBaseService.createKnowledgeBase(request);
        return APIResponse.success(response);
    }

    /**
     * 更新知识库
     */
    @PutMapping(ApiConstants.Knowledge.BASE_DETAIL)
    @Operation(summary = "更新知识库")
    public APIResponse<KnowledgeBaseResponse> updateKnowledgeBase(
            @PathVariable String kbId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        log.info("更新知识库: kbId={}", kbId);
        KnowledgeBaseResponse response = knowledgeBaseService.updateKnowledgeBase(kbId, request);
        return APIResponse.success(response);
    }

    /**
     * 删除知识库
     */
    @DeleteMapping(ApiConstants.Knowledge.BASE_DETAIL)
    @Operation(summary = "删除知识库")
    public APIResponse<Void> deleteKnowledgeBase(@PathVariable String kbId) {
        log.info("删除知识库: kbId={}", kbId);
        knowledgeBaseService.deleteKnowledgeBase(kbId);
        return APIResponse.success();
    }

    /**
     * 获取知识库详情
     */
    @GetMapping(ApiConstants.Knowledge.BASE_DETAIL)
    @Operation(summary = "获取知识库详情")
    public APIResponse<KnowledgeBaseResponse> getKnowledgeBase(@PathVariable String kbId) {
        KnowledgeBaseResponse response = knowledgeBaseService.getKnowledgeBase(kbId);
        return APIResponse.success(response);
    }

    /**
     * 分页查询知识库列表
     */
    @GetMapping(ApiConstants.Knowledge.BASES)
    @Operation(summary = "分页查询知识库")
    public APIResponse<PageResult<KnowledgeBaseResponse>> listKnowledgeBases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<KnowledgeBaseResponse> pageResult = knowledgeBaseService.listKnowledgeBases(page, size, keyword);
        return APIResponse.success(PageResult.of(
                pageResult.getContent(),
                pageResult.getTotalElements(),
                pageResult.getNumber() + 1,
                pageResult.getSize()
        ));
    }

    /**
     * 获取所有知识库列表（不分页，用于下拉选择）
     */
    @GetMapping(ApiConstants.Knowledge.BASES_ALL)
    @Operation(summary = "查询全部知识库")
    public APIResponse<List<KnowledgeBaseResponse>> listAllKnowledgeBases() {
        List<KnowledgeBaseResponse> list = knowledgeBaseService.listAllKnowledgeBases();
        return APIResponse.success(list);
    }
}
