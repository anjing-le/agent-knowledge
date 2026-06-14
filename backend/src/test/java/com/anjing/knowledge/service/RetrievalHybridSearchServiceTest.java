package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalHybridSearchServiceTest {

    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final RetrievalResultEnrichmentService resultEnrichmentService = new RetrievalResultEnrichmentService(
            knowledgeBaseRepository,
            documentRepository,
            chunkRepository,
            new ObjectMapper()
    );
    private final RetrievalHybridSearchService hybridSearchService = new RetrievalHybridSearchService(
            chunkRepository,
            resultEnrichmentService
    );

    @Test
    void mergeShouldCombineVectorAndKeywordResultsWithRrf() {
        KnowledgeBase knowledgeBase = knowledgeBase("kb-a");
        Chunk hybridChunk = chunk("chunk-vector", "doc-vector", "agent scaffold vector hit");
        Chunk keywordChunk = chunk("chunk-keyword", "doc-keyword", "agent scaffold keyword only");

        when(knowledgeBaseRepository.findById("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(hybridChunk, keywordChunk));
        when(chunkRepository.findById("chunk-vector")).thenReturn(Optional.of(hybridChunk));
        when(chunkRepository.findById("chunk-keyword")).thenReturn(Optional.of(keywordChunk));
        when(documentRepository.findById("doc-vector")).thenReturn(Optional.of(document("doc-vector", "Vector.md")));
        when(documentRepository.findById("doc-keyword")).thenReturn(Optional.of(document("doc-keyword", "Keyword.md")));

        List<SearchResult> results = hybridSearchService.merge(
                "agent scaffold",
                List.of("kb-a"),
                List.of(
                        vectorResult("chunk-vector", "kb-a", "agent scaffold vector hit", 0.9f),
                        vectorResult("chunk-semantic", "kb-a", "semantic only", 0.8f)
                ),
                3
        );

        assertEquals(3, results.size());
        assertEquals("chunk-vector", results.get(0).getChunkId());
        assertEquals("hybrid", results.get(0).getRetrievalSource());
        assertTrue(results.get(0).getHybridScore() > 0.9f);
        assertTrue(results.stream().anyMatch(result -> "keyword".equals(result.getRetrievalSource())));
    }

    @Test
    void mergeShouldReturnKeywordOnlyResultsWhenVectorRecallIsEmpty() {
        KnowledgeBase knowledgeBase = knowledgeBase("kb-a");
        Chunk keywordChunk = chunk("chunk-keyword", "doc-keyword", "脚手架生长出 RAG agent");

        when(knowledgeBaseRepository.findById("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(keywordChunk));
        when(chunkRepository.findById("chunk-keyword")).thenReturn(Optional.of(keywordChunk));

        List<SearchResult> results = hybridSearchService.merge(
                "脚手架 RAG agent",
                List.of("kb-a"),
                List.of(),
                3
        );

        assertEquals(1, results.size());
        assertEquals("chunk-keyword", results.get(0).getChunkId());
        assertEquals("keyword", results.get(0).getRetrievalSource());
        assertNull(results.get(0).getSimilarityScore());
        assertTrue(results.get(0).getKeywordScore() > 0.0f);
        assertTrue(results.get(0).getHybridScore() > 0.0f);
    }

    private SearchResult vectorResult(String chunkId, String kbId, String content, float score) {
        SearchResult result = new SearchResult();
        result.setChunkId(chunkId);
        result.setKbId(kbId);
        result.setContent(content);
        result.setSimilarityScore(score);
        result.setFinalScore(score);
        return result;
    }

    private Chunk chunk(String chunkId, String docId, String content) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setKbId("kb-a");
        chunk.setDocId(docId);
        chunk.setContent(content);
        chunk.setIsEnabled(true);
        return chunk;
    }

    private KnowledgeBase knowledgeBase(String kbId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId(kbId);
        knowledgeBase.setName("RAG 教学库");
        return knowledgeBase;
    }

    private Document document(String docId, String name) {
        Document document = new Document();
        document.setDocId(docId);
        document.setDocName(name);
        return document;
    }
}
