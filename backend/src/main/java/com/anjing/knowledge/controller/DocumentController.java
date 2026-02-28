package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.response.DocumentResponse;
import com.anjing.knowledge.service.DocumentService;
import com.anjing.model.response.APIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档（路径：POST /api/documents/{kbId}/upload）
     */
    @PostMapping("/{kbId}/upload")
    public APIResponse<DocumentResponse> uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String parserStrategyId,
            @RequestParam(required = false) String chunkStrategyId) throws IOException {
        log.info("上传文档: kbId={}, fileName={}, size={}", kbId, file.getOriginalFilename(), file.getSize());
        DocumentResponse response = documentService.uploadDocument(kbId, file, parserStrategyId, chunkStrategyId);
        return APIResponse.success(response);
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/{kbId}/upload/batch")
    public APIResponse<List<DocumentResponse>> batchUploadDocuments(
            @PathVariable String kbId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) String parserStrategyId,
            @RequestParam(required = false) String chunkStrategyId) throws IOException {
        log.info("批量上传文档: kbId={}, fileCount={}", kbId, files.length);
        
        List<DocumentResponse> responses = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            try {
                DocumentResponse response = documentService.uploadDocument(kbId, file, parserStrategyId, chunkStrategyId);
                responses.add(response);
            } catch (Exception e) {
                log.error("上传文档失败: fileName={}, error={}", file.getOriginalFilename(), e.getMessage());
            }
        }
        
        return APIResponse.success(responses);
    }

    /**
     * 获取文档详情（路径：GET /api/documents/{docId}）
     */
    @GetMapping("/{docId}")
    public APIResponse<DocumentResponse> getDocument(@PathVariable String docId) {
        DocumentResponse response = documentService.getDocument(docId);
        return APIResponse.success(response);
    }

    /**
     * 分页查询知识库下的文档（路径：GET /api/documents/{kbId}/list）
     */
    @GetMapping("/{kbId}/list")
    public APIResponse<Map<String, Object>> listDocuments(
            @PathVariable String kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<DocumentResponse> pageResult = documentService.listDocuments(kbId, page, size, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("records", pageResult.getContent());
        data.put("total", pageResult.getTotalElements());
        data.put("currentPage", pageResult.getNumber() + 1);
        data.put("pageSize", pageResult.getSize());
        data.put("totalPage", pageResult.getTotalPages());
        return APIResponse.success(data);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public APIResponse<Void> deleteDocument(@PathVariable String docId) {
        log.info("删除文档: docId={}", docId);
        documentService.deleteDocument(docId);
        return APIResponse.success(null);
    }

    /**
     * 批量删除文档
     */
    @DeleteMapping("/batch")
    public APIResponse<Void> batchDeleteDocuments(@RequestBody List<String> docIds) {
        log.info("批量删除文档: count={}", docIds.size());
        documentService.batchDeleteDocuments(docIds);
        return APIResponse.success(null);
    }

    /**
     * 更新文档状态（路径：PUT /api/documents/{docId}/status）
     */
    @PutMapping("/{docId}/status")
    public APIResponse<Void> setDocumentEnabled(
            @PathVariable String docId,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("isEnabled", true);
        log.info("设置文档启用状态: docId={}, enabled={}", docId, enabled);
        documentService.setDocumentEnabled(docId, enabled);
        return APIResponse.success(null);
    }

    /**
     * 重新处理文档（路径：POST /api/documents/{docId}/reprocess）
     */
    @PostMapping("/{docId}/reprocess")
    public APIResponse<Void> reprocessDocument(@PathVariable String docId) {
        log.info("重新处理文档: docId={}", docId);
        documentService.reprocessDocument(docId);
        return APIResponse.success(null);
    }
}

