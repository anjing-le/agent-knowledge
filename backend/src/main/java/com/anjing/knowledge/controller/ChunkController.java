package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.request.UpdateEnabledRequest;
import com.anjing.knowledge.model.response.ChunkResponse;
import com.anjing.knowledge.service.ChunkService;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import com.anjing.model.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 分片管理 Controller
 *
 * 提供 Chunk 查询和状态管理 API
 */
@Slf4j
@RestController
@RequestMapping(ApiConstants.Knowledge.BASE)
@RequiredArgsConstructor
@Tag(name = "Knowledge Chunks", description = "知识库切片查询与状态管理接口")
public class ChunkController {

    private final ChunkService chunkService;

    /**
     * 获取文档的分片列表（分页）
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_CHUNKS)
    @Operation(summary = "分页查询文档切片")
    public APIResponse<PageResult<ChunkResponse>> listChunks(
            @PathVariable String docId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("查询分片列表: docId={}, page={}, size={}", docId, page, size);
        return APIResponse.success(chunkService.listChunks(docId, page, size));
    }

    /**
     * 获取分片详情
     */
    @GetMapping(ApiConstants.Knowledge.CHUNK_DETAIL)
    @Operation(summary = "获取切片详情")
    public APIResponse<ChunkResponse> getChunk(@PathVariable String chunkId) {
        Optional<ChunkResponse> chunk = chunkService.getChunk(chunkId);
        if (chunk.isEmpty()) {
            return APIResponse.error("分片不存在");
        }
        return APIResponse.success(chunk.get());
    }

    /**
     * 更新分片启用状态
     */
    @PutMapping(ApiConstants.Knowledge.CHUNK_ENABLED)
    @Operation(summary = "更新切片启用状态")
    public APIResponse<Void> updateChunkStatus(
            @PathVariable String chunkId,
            @Valid @RequestBody UpdateEnabledRequest request) {
        log.info("更新分片状态: chunkId={}, isEnabled={}", chunkId, request.getIsEnabled());
        if (!chunkService.updateChunkEnabled(chunkId, request.enabledValue())) {
            return APIResponse.error("分片不存在");
        }
        return APIResponse.success();
    }

    /**
     * 获取文档的分片数量
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_CHUNK_COUNT)
    @Operation(summary = "统计文档切片数量")
    public APIResponse<Long> getChunkCount(@PathVariable String docId) {
        return APIResponse.success(chunkService.countByDocument(docId));
    }
}
