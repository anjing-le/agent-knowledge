package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.request.BatchDeleteDocumentsRequest;
import com.anjing.knowledge.model.request.UpdateEnabledRequest;
import com.anjing.knowledge.model.response.DocumentProcessingTaskResponse;
import com.anjing.knowledge.model.response.DocumentResponse;
import com.anjing.knowledge.service.DocumentIngestionService;
import com.anjing.knowledge.service.DocumentService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import com.anjing.model.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档管理Controller
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.Knowledge.BASE)
@RequiredArgsConstructor
@Tag(name = "Knowledge Documents", description = "知识库文档管理接口")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIngestionService ingestionService;

    /**
     * 上传文档（路径：POST /api/knowledge/bases/{kbId}/documents）
     */
    @PostMapping(
            value = ApiConstants.Knowledge.BASE_DOCUMENTS,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            params = "file"
    )
    @Operation(summary = "上传文档")
    public APIResponse<DocumentResponse> uploadDocument(
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String parserStrategyId,
            @RequestParam(required = false) String chunkStrategyId) throws IOException {
        log.info("上传文档: kbId={}, fileName={}, size={}", kbId, file.getOriginalFilename(), file.getSize());
        DocumentResponse response = ingestionService.uploadDocument(kbId, file, parserStrategyId, chunkStrategyId);
        return APIResponse.success(response);
    }

    /**
     * 批量上传文档
     */
    @PostMapping(
            value = ApiConstants.Knowledge.BASE_DOCUMENTS,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            params = "files"
    )
    @Operation(summary = "批量上传文档")
    public APIResponse<List<DocumentResponse>> batchUploadDocuments(
            @PathVariable String kbId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false) String parserStrategyId,
            @RequestParam(required = false) String chunkStrategyId) {
        log.info("批量上传文档: kbId={}, fileCount={}", kbId, files.length);
        return APIResponse.success(ingestionService.batchUploadDocuments(kbId, files, parserStrategyId, chunkStrategyId));
    }

    /**
     * 获取文档详情（路径：GET /api/knowledge/documents/{docId}）
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_DETAIL)
    @Operation(summary = "获取文档详情")
    public APIResponse<DocumentResponse> getDocument(@PathVariable String docId) {
        DocumentResponse response = documentService.getDocument(docId);
        return APIResponse.success(response);
    }

    /**
     * 分页查询知识库下的文档（路径：GET /api/knowledge/bases/{kbId}/documents）
     */
    @GetMapping(ApiConstants.Knowledge.BASE_DOCUMENTS)
    @Operation(summary = "分页查询知识库文档")
    public APIResponse<PageResult<DocumentResponse>> listDocuments(
            @PathVariable String kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return APIResponse.success(documentService.listDocuments(kbId, page, size, keyword));
    }

    /**
     * 删除文档
     */
    @DeleteMapping(ApiConstants.Knowledge.DOCUMENT_DETAIL)
    @Operation(summary = "删除文档")
    public APIResponse<Void> deleteDocument(@PathVariable String docId) {
        log.info("删除文档: docId={}", docId);
        documentService.deleteDocument(docId);
        return APIResponse.success();
    }

    /**
     * 批量删除文档
     */
    @PostMapping(ApiConstants.Knowledge.DOCUMENT_BATCH_DELETE)
    @Operation(summary = "批量删除文档")
    public APIResponse<Void> batchDeleteDocuments(@Valid @RequestBody BatchDeleteDocumentsRequest request) {
        log.info("批量删除文档: count={}", request.getDocIds().size());
        documentService.batchDeleteDocuments(request.getDocIds());
        return APIResponse.success();
    }

    /**
     * 更新文档状态（路径：PUT /api/knowledge/documents/{docId}/enabled）
     */
    @PutMapping(ApiConstants.Knowledge.DOCUMENT_ENABLED)
    @Operation(summary = "更新文档启用状态")
    public APIResponse<Void> setDocumentEnabled(
            @PathVariable String docId,
            @Valid @RequestBody UpdateEnabledRequest request) {
        log.info("设置文档启用状态: docId={}, enabled={}", docId, request.getIsEnabled());
        documentService.setDocumentEnabled(docId, request.enabledValue());
        return APIResponse.success();
    }

    /**
     * 重新处理文档（路径：POST /api/knowledge/documents/{docId}/reprocess）
     */
    @PostMapping(ApiConstants.Knowledge.DOCUMENT_REPROCESS)
    @Operation(summary = "重新处理文档")
    public APIResponse<Void> reprocessDocument(@PathVariable String docId) {
        log.info("重新处理文档: docId={}", docId);
        ingestionService.reprocessDocument(docId);
        return APIResponse.success();
    }

    /**
     * 查询文档处理任务（路径：GET /api/knowledge/documents/{docId}/tasks）
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_TASKS)
    @Operation(summary = "查询文档处理任务")
    public APIResponse<List<DocumentProcessingTaskResponse>> listDocumentTasks(@PathVariable String docId) {
        return APIResponse.success(ingestionService.listDocumentTasks(docId));
    }
}
