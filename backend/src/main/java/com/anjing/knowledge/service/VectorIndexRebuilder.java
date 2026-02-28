package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用启动时自动重建内存向量索引
 *
 * 因为 VectorStoreService 使用内存存储，重启后向量数据会丢失。
 * 此组件在启动后自动从数据库读取已向量化的 Chunk，重新 Embedding 并加载到内存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorIndexRebuilder {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildVectorIndex() {
        log.info("[启动] 开始重建内存向量索引...");

        List<KnowledgeBase> kbList = knowledgeBaseRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
        if (kbList.isEmpty()) {
            log.info("[启动] 没有知识库，跳过向量索引重建");
            return;
        }

        int totalChunks = 0;
        int totalKbs = 0;

        for (KnowledgeBase kb : kbList) {
            List<Chunk> chunks = chunkRepository.findByKbIdAndEmbeddingStatus(kb.getKbId(), EmbeddingStatus.EMBEDDED.getCode());
            if (chunks.isEmpty()) {
                continue;
            }

            log.info("[启动] 重建知识库向量: kbId={}, name={}, chunkCount={}", kb.getKbId(), kb.getName(), chunks.size());

            int batchSize = 20;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<Chunk> batch = chunks.subList(i, end);

                List<String> contents = new ArrayList<>();
                List<String> chunkIds = new ArrayList<>();
                for (Chunk chunk : batch) {
                    contents.add(chunk.getContent());
                    chunkIds.add(chunk.getChunkId());
                }

                try {
                    String embeddingModel = kb.getEmbeddingModel();
                    List<List<Float>> vectors = embeddingService.embedBatch(contents, embeddingModel);

                    if (vectors.size() == contents.size()) {
                        vectorStoreService.upsertBatch(kb.getKbId(), chunkIds, vectors, contents);
                    } else {
                        log.warn("[启动] Embedding 返回数量不匹配: expected={}, actual={}", contents.size(), vectors.size());
                    }
                } catch (Exception e) {
                    log.error("[启动] 向量重建批次失败: kbId={}, batch={}, error={}", kb.getKbId(), (i / batchSize) + 1, e.getMessage());
                }
            }

            totalChunks += chunks.size();
            totalKbs++;
        }

        log.info("[启动] 向量索引重建完成: 知识库={}个, 向量={}条", totalKbs, totalChunks);
    }
}
