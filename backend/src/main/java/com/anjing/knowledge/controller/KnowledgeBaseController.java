package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.request.CreateKnowledgeBaseRequest;
import com.anjing.knowledge.model.request.UpdateKnowledgeBaseRequest;
import com.anjing.knowledge.model.response.KnowledgeBaseResponse;
import com.anjing.knowledge.service.KnowledgeBaseService;
import com.anjing.model.response.APIResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库
     */
    @PostMapping
    public APIResponse<KnowledgeBaseResponse> createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        log.info("创建知识库: name={}", request.getName());
        KnowledgeBaseResponse response = knowledgeBaseService.createKnowledgeBase(request);
        return APIResponse.success(response);
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{kbId}")
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
    @DeleteMapping("/{kbId}")
    public APIResponse<Void> deleteKnowledgeBase(@PathVariable String kbId) {
        log.info("删除知识库: kbId={}", kbId);
        knowledgeBaseService.deleteKnowledgeBase(kbId);
        return APIResponse.success(null);
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{kbId}")
    public APIResponse<KnowledgeBaseResponse> getKnowledgeBase(@PathVariable String kbId) {
        KnowledgeBaseResponse response = knowledgeBaseService.getKnowledgeBase(kbId);
        return APIResponse.success(response);
    }

    /**
     * 分页查询知识库列表
     */
    @GetMapping("/list")
    public APIResponse<Map<String, Object>> listKnowledgeBases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<KnowledgeBaseResponse> pageResult = knowledgeBaseService.listKnowledgeBases(page, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("records", pageResult.getContent());
        data.put("total", pageResult.getTotalElements());
        data.put("currentPage", pageResult.getNumber() + 1);
        data.put("pageSize", pageResult.getSize());
        data.put("totalPage", pageResult.getTotalPages());
        return APIResponse.success(data);
    }

    /**
     * 获取所有知识库列表（不分页，用于下拉选择）
     */
    @GetMapping("/all")
    public APIResponse<List<KnowledgeBaseResponse>> listAllKnowledgeBases() {
        List<KnowledgeBaseResponse> list = knowledgeBaseService.listAllKnowledgeBases();
        return APIResponse.success(list);
    }
}

