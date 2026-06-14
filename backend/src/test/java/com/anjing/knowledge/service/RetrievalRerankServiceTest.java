package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalRerankServiceTest {

    private final RetrievalRerankService rerankService = new RetrievalRerankService();

    @Test
    void rerankShouldPreferLexicallyRelevantContent() {
        SearchResult highVectorOnly = result("chunk-high-vector", "database migration", 0.9f);
        SearchResult relevant = result("chunk-relevant", "agent scaffold retrieval rerank", 0.8f);

        rerankService.rerank("agent scaffold", List.of(highVectorOnly, relevant), "local-lexical");

        assertTrue(relevant.getRerankScore() > highVectorOnly.getRerankScore());
        assertTrue(relevant.getFinalScore() > highVectorOnly.getFinalScore());
    }

    @Test
    void rerankShouldHandleChineseTeachingQueries() {
        SearchResult unrelated = result("chunk-unrelated", "数据库迁移和缓存配置", 0.85f);
        SearchResult relevant = result("chunk-relevant", "脚手架生长出 RAG agent 的知识库链路", 0.75f);

        rerankService.rerank("脚手架 RAG agent", List.of(unrelated, relevant), "local-lexical");

        assertTrue(relevant.getRerankScore() > unrelated.getRerankScore());
        assertTrue(relevant.getFinalScore() > unrelated.getFinalScore());
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
