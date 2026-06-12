package com.anjing.knowledge.service;

import lombok.Data;

import java.util.List;

/**
 * Vector store boundary for retrieval.
 *
 * Current runtime uses an in-memory implementation. Production adapters can
 * implement this interface with Milvus, pgvector, or a hosted vector database.
 */
public interface VectorStoreService {

    void upsert(String kbId, String chunkId, List<Float> vector, String content);

    void upsertBatch(String kbId, List<String> chunkIds, List<List<Float>> vectors, List<String> contents);

    List<VectorSearchResult> search(List<String> kbIds, List<Float> queryVector, int topK);

    void deleteByDocChunks(String kbId, List<String> chunkIds);

    void deleteByKbId(String kbId);

    int getVectorCount(String kbId);

    /**
     * In-memory vector entry representation.
     */
    @Data
    class VectorEntry {
        private final String chunkId;
        private final List<Float> vector;
        private final String content;
    }

    /**
     * Vector search result before repository enrichment.
     */
    @Data
    class VectorSearchResult {
        private final String chunkId;
        private final String kbId;
        private final String content;
        private final float score;
    }
}
