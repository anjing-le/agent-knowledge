package com.anjing.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryVectorStoreServiceTest {

    @Test
    void searchShouldReturnMostSimilarChunksAcrossKnowledgeBases() {
        MemoryVectorStoreService service = new MemoryVectorStoreService();
        service.upsert("kb-a", "chunk-a1", List.of(1.0f, 0.0f, 0.0f), "alpha");
        service.upsert("kb-a", "chunk-a2", List.of(0.0f, 1.0f, 0.0f), "beta");
        service.upsert("kb-b", "chunk-b1", List.of(0.9f, 0.1f, 0.0f), "gamma");

        List<VectorStoreService.VectorSearchResult> results =
                service.search(List.of("kb-a", "kb-b"), List.of(1.0f, 0.0f, 0.0f), 2);

        assertEquals(2, results.size());
        assertEquals("chunk-a1", results.get(0).getChunkId());
        assertEquals("chunk-b1", results.get(1).getChunkId());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void searchShouldIgnoreVectorsWithDifferentDimensions() {
        MemoryVectorStoreService service = new MemoryVectorStoreService();
        service.upsert("kb-a", "chunk-a1", List.of(1.0f, 0.0f), "alpha");

        List<VectorStoreService.VectorSearchResult> results =
                service.search(List.of("kb-a"), List.of(1.0f, 0.0f, 0.0f), 1);

        assertEquals(1, results.size());
        assertEquals(0.0f, results.get(0).getScore());
    }

    @Test
    void searchShouldReturnEmptyWhenRequestCannotProduceCandidates() {
        MemoryVectorStoreService service = new MemoryVectorStoreService();
        service.upsert("kb-a", "chunk-a1", List.of(1.0f, 0.0f), "alpha");

        assertTrue(service.search(List.of("kb-a"), List.of(1.0f, 0.0f), 0).isEmpty());
        assertTrue(service.search(List.of(), List.of(1.0f, 0.0f), 1).isEmpty());
        assertTrue(service.search(List.of("kb-a"), List.of(), 1).isEmpty());
    }

    @Test
    void upsertBatchShouldRejectMismatchedPayloads() {
        MemoryVectorStoreService service = new MemoryVectorStoreService();

        assertThrows(IllegalArgumentException.class, () -> service.upsertBatch(
                "kb-a",
                List.of("chunk-a1", "chunk-a2"),
                List.of(List.of(1.0f, 0.0f)),
                List.of("alpha", "beta")
        ));
    }

    @Test
    void deleteShouldRemoveScopedVectorsOnly() {
        MemoryVectorStoreService service = new MemoryVectorStoreService();
        service.upsertBatch(
                "kb-a",
                List.of("chunk-a1", "chunk-a2"),
                List.of(List.of(1.0f, 0.0f), List.of(0.0f, 1.0f)),
                List.of("alpha", "beta")
        );
        service.upsert("kb-b", "chunk-b1", List.of(1.0f, 0.0f), "gamma");

        service.deleteByDocChunks("kb-a", List.of("chunk-a1"));

        assertEquals(1, service.getVectorCount("kb-a"));
        assertEquals(1, service.getVectorCount("kb-b"));

        service.deleteByKbId("kb-a");

        assertEquals(0, service.getVectorCount("kb-a"));
        assertEquals(1, service.getVectorCount("kb-b"));
    }
}
