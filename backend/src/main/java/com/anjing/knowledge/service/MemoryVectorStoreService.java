package com.anjing.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store for local demos and teaching.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(VectorStoreService.class)
public class MemoryVectorStoreService implements VectorStoreService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, VectorEntry>> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(String kbId, String chunkId, List<Float> vector, String content) {
        store.computeIfAbsent(kbId, key -> new ConcurrentHashMap<>())
                .put(chunkId, new VectorEntry(chunkId, vector, content));
        log.debug("向量存储成功: kbId={}, chunkId={}, dimensions={}", kbId, chunkId, vector.size());
    }

    @Override
    public void upsertBatch(String kbId, List<String> chunkIds, List<List<Float>> vectors, List<String> contents) {
        ConcurrentHashMap<String, VectorEntry> kbStore =
                store.computeIfAbsent(kbId, key -> new ConcurrentHashMap<>());
        for (int i = 0; i < chunkIds.size(); i++) {
            kbStore.put(chunkIds.get(i), new VectorEntry(chunkIds.get(i), vectors.get(i), contents.get(i)));
        }
        log.info("批量向量存储成功: kbId={}, count={}", kbId, chunkIds.size());
    }

    @Override
    public List<VectorSearchResult> search(List<String> kbIds, List<Float> queryVector, int topK) {
        List<VectorSearchResult> allResults = new ArrayList<>();

        for (String kbId : kbIds) {
            ConcurrentHashMap<String, VectorEntry> kbStore = store.get(kbId);
            if (kbStore == null || kbStore.isEmpty()) {
                continue;
            }

            for (VectorEntry entry : kbStore.values()) {
                float similarity = cosineSimilarity(queryVector, entry.getVector());
                allResults.add(new VectorSearchResult(entry.getChunkId(), kbId, entry.getContent(), similarity));
            }
        }

        allResults.sort((left, right) -> Float.compare(right.getScore(), left.getScore()));

        List<VectorSearchResult> topResults = allResults.stream().limit(topK).collect(Collectors.toList());
        log.info("向量检索完成: kbIds={}, candidateCount={}, topK={}, resultCount={}",
                kbIds, allResults.size(), topK, topResults.size());
        return topResults;
    }

    @Override
    public void deleteByDocChunks(String kbId, List<String> chunkIds) {
        ConcurrentHashMap<String, VectorEntry> kbStore = store.get(kbId);
        if (kbStore != null) {
            chunkIds.forEach(kbStore::remove);
            log.info("删除向量成功: kbId={}, count={}", kbId, chunkIds.size());
        }
    }

    @Override
    public void deleteByKbId(String kbId) {
        store.remove(kbId);
        log.info("删除知识库向量成功: kbId={}", kbId);
    }

    @Override
    public int getVectorCount(String kbId) {
        ConcurrentHashMap<String, VectorEntry> kbStore = store.get(kbId);
        return kbStore == null ? 0 : kbStore.size();
    }

    private float cosineSimilarity(List<Float> left, List<Float> right) {
        if (left.size() != right.size()) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float normLeft = 0.0f;
        float normRight = 0.0f;

        for (int i = 0; i < left.size(); i++) {
            dotProduct += left.get(i) * right.get(i);
            normLeft += left.get(i) * left.get(i);
            normRight += right.get(i) * right.get(i);
        }

        if (normLeft == 0.0f || normRight == 0.0f) {
            return 0.0f;
        }

        return dotProduct / (float) (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }
}
