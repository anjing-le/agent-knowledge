package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.model.response.APIResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 分片管理 Controller
 *
 * 提供 Chunk 查询和状态管理 API
 */
@Slf4j
@RestController
@RequestMapping("/api/chunks")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkRepository chunkRepository;

    /**
     * 获取文档的分片列表（分页）
     */
    @GetMapping("/{docId}/list")
    public APIResponse<Map<String, Object>> listChunks(
            @PathVariable String docId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("查询分片列表: docId={}, page={}, size={}", docId, page, size);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Chunk> chunkPage = chunkRepository.findByDocIdOrderByChunkIndexAsc(docId, pageable);
        Map<String, Object> data = new HashMap<>();
        data.put("records", chunkPage.getContent());
        data.put("total", chunkPage.getTotalElements());
        data.put("currentPage", chunkPage.getNumber() + 1);
        data.put("pageSize", chunkPage.getSize());
        data.put("totalPage", chunkPage.getTotalPages());
        return APIResponse.success(data);
    }

    /**
     * 获取分片详情
     */
    @GetMapping("/detail/{chunkId}")
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
    @PutMapping("/{chunkId}/status")
    public APIResponse<Void> updateChunkStatus(
            @PathVariable String chunkId,
            @RequestBody StatusRequest request) {
        log.info("更新分片状态: chunkId={}, isEnabled={}", chunkId, request.isEnabled);
        Chunk chunk = chunkRepository.findById(chunkId).orElse(null);
        if (chunk == null) {
            return APIResponse.error("分片不存在");
        }
        chunk.setIsEnabled(request.isEnabled);
        chunkRepository.save(chunk);
        return APIResponse.success(null);
    }

    /**
     * 获取文档的分片数量
     */
    @GetMapping("/{docId}/count")
    public APIResponse<Long> getChunkCount(@PathVariable String docId) {
        long count = chunkRepository.countByDocId(docId);
        return APIResponse.success(count);
    }

    /**
     * 状态请求
     */
    @lombok.Data
    public static class StatusRequest {
        private boolean isEnabled;
    }
}
