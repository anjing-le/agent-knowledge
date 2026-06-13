package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embeds document chunks and writes vectors through the vector-store boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private static final int BATCH_SIZE = 20;

    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public boolean embedChunks(String kbId, List<Chunk> chunks, String embeddingModel) {
        for (int index = 0; index < chunks.size(); index += BATCH_SIZE) {
            int end = Math.min(index + BATCH_SIZE, chunks.size());
            List<Chunk> batch = chunks.subList(index, end);

            List<String> contents = new ArrayList<>();
            List<String> chunkIds = new ArrayList<>();
            for (Chunk chunk : batch) {
                contents.add(chunk.getContent());
                chunkIds.add(chunk.getChunkId());
            }

            chunkRepository.batchUpdateEmbeddingStatus(chunkIds, EmbeddingStatus.EMBEDDING.getCode());
            try {
                List<List<Float>> vectors = embeddingService.embedBatch(contents, embeddingModel);
                if (vectors.size() != contents.size()) {
                    log.error("Embedding 返回数量不匹配: expected={}, actual={}", contents.size(), vectors.size());
                    chunkRepository.batchUpdateEmbeddingStatus(chunkIds, EmbeddingStatus.FAILED.getCode());
                    return false;
                }

                vectorStoreService.upsertBatch(kbId, chunkIds, vectors, contents);
                markEmbedded(batch);
                chunkRepository.saveAll(batch);

                log.info("[RAG] 向量化批次完成: batch={}/{}, kbId={}",
                        (index / BATCH_SIZE) + 1,
                        (chunks.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                        kbId);
            } catch (Exception e) {
                log.error("向量化处理异常: kbId={}, error={}", kbId, e.getMessage(), e);
                chunkRepository.batchUpdateEmbeddingStatus(chunkIds, EmbeddingStatus.FAILED.getCode());
                return false;
            }
        }
        return true;
    }

    private void markEmbedded(List<Chunk> batch) {
        for (Chunk chunk : batch) {
            chunk.setEmbeddingStatus(EmbeddingStatus.EMBEDDED.getCode());
            chunk.setVectorId(chunk.getChunkId());
        }
    }
}
