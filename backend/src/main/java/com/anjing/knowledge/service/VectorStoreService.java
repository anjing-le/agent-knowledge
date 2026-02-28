package com.anjing.knowledge.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 向量存储服务（内存实现）
 *
 * 使用 ConcurrentHashMap + 余弦相似度实现向量检索
 * 适用于教学演示和小规模数据，生产环境可替换为 Milvus
 */
@Slf4j
@Service
public class VectorStoreService {

    /**
     * 向量存储：kbId -> (chunkId -> VectorEntry)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, VectorEntry>> store = new ConcurrentHashMap<>();

    /**
     * 存储向量
     */
    public void upsert(String kbId, String chunkId, List<Float> vector, String content) {
        store.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>())
                .put(chunkId, new VectorEntry(chunkId, vector, content));
        log.debug("向量存储成功: kbId={}, chunkId={}, dimensions={}", kbId, chunkId, vector.size());
    }

    /**
     * 批量存储向量
     */
    public void upsertBatch(String kbId, List<String> chunkIds, List<List<Float>> vectors, List<String> contents) {
        ConcurrentHashMap<String, VectorEntry> kbStore = store.computeIfAbsent(kbId, k -> new ConcurrentHashMap<>());
        for (int i = 0; i < chunkIds.size(); i++) {
            kbStore.put(chunkIds.get(i), new VectorEntry(chunkIds.get(i), vectors.get(i), contents.get(i)));
        }
        log.info("批量向量存储成功: kbId={}, count={}", kbId, chunkIds.size());
    }

    /**
     * 向量检索
     *
     * @param kbIds       要搜索的知识库ID列表
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @return 相似度排序的结果
     */
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

        allResults.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        List<VectorSearchResult> topResults = allResults.stream().limit(topK).collect(Collectors.toList());
        log.info("向量检索完成: kbIds={}, candidateCount={}, topK={}, resultCount={}",
                kbIds, allResults.size(), topK, topResults.size());
        return topResults;
    }

    /**
     * 删除指定文档的所有向量
     */
    public void deleteByDocChunks(String kbId, List<String> chunkIds) {
        ConcurrentHashMap<String, VectorEntry> kbStore = store.get(kbId);
        if (kbStore != null) {
            chunkIds.forEach(kbStore::remove);
            log.info("删除向量成功: kbId={}, count={}", kbId, chunkIds.size());
        }
    }

    /**
     * 删除整个知识库的向量
     */
    public void deleteByKbId(String kbId) {
        store.remove(kbId);
        log.info("删除知识库向量成功: kbId={}", kbId);
    }

    /**
     * 获取知识库向量数量
     */
    public int getVectorCount(String kbId) {
        ConcurrentHashMap<String, VectorEntry> kbStore = store.get(kbId);
        return kbStore == null ? 0 : kbStore.size();
    }

    /**
     * 余弦相似度计算
     */
    private float cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }

        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 向量条目
     */
    @Data
    public static class VectorEntry {
        private final String chunkId;
        private final List<Float> vector;
        private final String content;

        public VectorEntry(String chunkId, List<Float> vector, String content) {
            this.chunkId = chunkId;
            this.vector = vector;
            this.content = content;
        }
    }

    /**
     * 向量搜索结果
     */
    @Data
    public static class VectorSearchResult {
        private final String chunkId;
        private final String kbId;
        private final String content;
        private final float score;

        public VectorSearchResult(String chunkId, String kbId, String content, float score) {
            this.chunkId = chunkId;
            this.kbId = kbId;
            this.content = content;
            this.score = score;
        }
    }
}
