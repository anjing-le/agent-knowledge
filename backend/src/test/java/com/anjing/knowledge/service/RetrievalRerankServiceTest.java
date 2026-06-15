package com.anjing.knowledge.service;

import com.anjing.config.properties.RerankProperties;
import com.anjing.knowledge.model.response.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetrievalRerankServiceTest {

    private final RerankProviderClient rerankProviderClient = mock(RerankProviderClient.class);
    private final RerankProperties rerankProperties = new RerankProperties();
    private final RetrievalRerankService rerankService = new RetrievalRerankService(rerankProviderClient, rerankProperties);

    @Test
    void rerankShouldPreferLexicallyRelevantContent() {
        SearchResult highVectorOnly = result("chunk-high-vector", "database migration", 0.9f);
        SearchResult relevant = result("chunk-relevant", "agent scaffold retrieval rerank", 0.8f);

        rerankService.rerank("agent scaffold", List.of(highVectorOnly, relevant), "local-lexical");

        assertTrue(relevant.getRerankScore() > highVectorOnly.getRerankScore());
        assertTrue(relevant.getFinalScore() > highVectorOnly.getFinalScore());
        assertEquals("local-lexical", relevant.getRerankProvider());
        verifyNoInteractions(rerankProviderClient);
    }

    @Test
    void rerankShouldHandleChineseTeachingQueries() {
        SearchResult unrelated = result("chunk-unrelated", "数据库迁移和缓存配置", 0.85f);
        SearchResult relevant = result("chunk-relevant", "脚手架生长出 RAG agent 的知识库链路", 0.75f);

        rerankService.rerank("脚手架 RAG agent", List.of(unrelated, relevant), "local-lexical");

        assertTrue(relevant.getRerankScore() > unrelated.getRerankScore());
        assertTrue(relevant.getFinalScore() > unrelated.getFinalScore());
        assertEquals("local-lexical", relevant.getRerankProvider());
    }

    @Test
    void rerankShouldUseRemoteProviderScoresWhenConfigured() {
        rerankProperties.setProvider("remote");
        SearchResult highVectorOnly = result("chunk-high-vector", "database migration", 0.9f);
        SearchResult relevant = result("chunk-relevant", "agent scaffold retrieval rerank", 0.8f);

        when(rerankProviderClient.rerank(
                eq("agent scaffold"),
                eq(List.of("database migration", "agent scaffold retrieval rerank")),
                eq("rerank-v3.5")
        )).thenReturn(List.of(0.1f, 0.95f));

        rerankService.rerank("agent scaffold", List.of(highVectorOnly, relevant), "rerank-v3.5");

        assertEquals(0.95f, relevant.getRerankScore());
        assertEquals("rerank-v3.5", relevant.getRerankProvider());
        assertTrue(relevant.getFinalScore() > highVectorOnly.getFinalScore());
    }

    private SearchResult result(String chunkId, String content, float similarityScore) {
        SearchResult result = new SearchResult();
        result.setChunkId(chunkId);
        result.setContent(content);
        result.setSimilarityScore(similarityScore);
        result.setFinalScore(similarityScore);
        return result;
    }
}
