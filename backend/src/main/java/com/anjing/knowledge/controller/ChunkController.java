package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.request.UpdateEnabledRequest;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.model.constants.ApiConstants;
import com.anjing.model.response.APIResponse;
import com.anjing.model.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

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

    private final ChunkRepository chunkRepository;

    /**
     * 获取文档的分片列表（分页）
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_CHUNKS)
    @Operation(summary = "分页查询文档切片")
    public APIResponse<PageResult<Chunk>> listChunks(
            @PathVariable String docId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("查询分片列表: docId={}, page={}, size={}", docId, page, size);
        int current = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(current - 1, pageSize);
        Page<Chunk> chunkPage = chunkRepository.findByDocIdOrderByChunkIndexAsc(docId, pageable);
        return APIResponse.success(PageResult.of(
                chunkPage.getContent(),
                chunkPage.getTotalElements(),
                chunkPage.getNumber() + 1,
                chunkPage.getSize()
        ));
    }

    /**
     * 获取分片详情
     */
    @GetMapping(ApiConstants.Knowledge.CHUNK_DETAIL)
    @Operation(summary = "获取切片详情")
    public APIResponse<Chunk> getChunk(@PathVariable String chunkId) {
        Chunk chunk = chunkRepository.findById(chunkId).orElse(null);
        if (chunk == null) {
            return APIResponse.error("分片不存在");
        }
        return APIResponse.success(chunk);
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
        Chunk chunk = chunkRepository.findById(chunkId).orElse(null);
        if (chunk == null) {
            return APIResponse.error("分片不存在");
        }
        chunk.setIsEnabled(request.enabledValue());
        chunkRepository.save(chunk);
        return APIResponse.success();
    }

    /**
     * 获取文档的分片数量
     */
    @GetMapping(ApiConstants.Knowledge.DOCUMENT_CHUNK_COUNT)
    @Operation(summary = "统计文档切片数量")
    public APIResponse<Long> getChunkCount(@PathVariable String docId) {
        long count = chunkRepository.countByDocId(docId);
        return APIResponse.success(count);
    }
}
