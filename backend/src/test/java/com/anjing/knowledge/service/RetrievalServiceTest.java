package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final RetrievalService retrievalService = new RetrievalService(
            knowledgeBaseRepository,
            documentRepository,
            chunkRepository,
            embeddingService,
            vectorStoreService,
            new ObjectMapper()
    );

    @Test
    void searchShouldEnrichVectorResultsWithCitationMetadata() {
        SearchRequest request = new SearchRequest();
        request.setQuery("脚手架如何生长出 RAG agent");
        request.setKbIds(List.of("kb-a"));
        request.setCandidateCount(10);
        request.setTopK(5);
        request.setSimilarityThreshold(0.5f);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb-a");
        knowledgeBase.setName("RAG 教学库");
        knowledgeBase.setIsEnabled(true);

        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk-a1");
        chunk.setDocId("doc-a1");
        chunk.setKbId("kb-a");
        chunk.setMetadata("{\"page\":3,\"content_type\":\"markdown\"}");

        Document document = new Document();
        document.setDocId("doc-a1");
        document.setDocName("脚手架到 RAG.pdf");

        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseRepository.findById("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(embeddingService.embed(request.getQuery())).thenReturn(List.of(1.0f, 0.0f, 0.0f));
        when(vectorStoreService.search(List.of("kb-a"), List.of(1.0f, 0.0f, 0.0f), 10))
                .thenReturn(List.of(
                        new VectorStoreService.VectorSearchResult("chunk-a1", "kb-a", "命中文本", 0.92f),
                        new VectorStoreService.VectorSearchResult("chunk-low", "kb-a", "低分文本", 0.2f)
                ));
        when(chunkRepository.findById("chunk-a1")).thenReturn(Optional.of(chunk));
        when(documentRepository.findById("doc-a1")).thenReturn(Optional.of(document));

        List<SearchResult> results = retrievalService.search(request);

        assertEquals(1, results.size());
        SearchResult result = results.get(0);
        assertEquals("chunk-a1", result.getChunkId());
        assertEquals("doc-a1", result.getDocId());
        assertEquals("kb-a", result.getKbId());
        assertEquals("命中文本", result.getContent());
        assertEquals("脚手架到 RAG.pdf", result.getDocName());
        assertEquals("RAG 教学库", result.getKbName());
        assertEquals(0.92f, result.getFinalScore());
        assertEquals(3, result.getMetadata().get("page"));
        assertEquals("markdown", result.getMetadata().get("content_type"));
        verify(vectorStoreService).search(List.of("kb-a"), List.of(1.0f, 0.0f, 0.0f), 10);
    }

    @Test
    void searchShouldKeepRawMetadataWhenChunkMetadataIsInvalidJson() {
        SearchRequest request = new SearchRequest();
        request.setQuery("引用元数据");
        request.setKbIds(List.of("kb-a"));
        request.setSimilarityThreshold(0.0f);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb-a");
        knowledgeBase.setName("RAG 教学库");
        knowledgeBase.setIsEnabled(true);

        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk-a1");
        chunk.setDocId("doc-a1");
        chunk.setKbId("kb-a");
        chunk.setMetadata("page=3");

        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseRepository.findById("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(embeddingService.embed(request.getQuery())).thenReturn(List.of(1.0f));
        when(vectorStoreService.search(List.of("kb-a"), List.of(1.0f), 20))
                .thenReturn(List.of(new VectorStoreService.VectorSearchResult("chunk-a1", "kb-a", "命中文本", 0.8f)));
        when(chunkRepository.findById("chunk-a1")).thenReturn(Optional.of(chunk));

        List<SearchResult> results = retrievalService.search(request);

        assertEquals(1, results.size());
        assertEquals("page=3", results.get(0).getMetadata().get("raw"));
        assertInstanceOf(String.class, results.get(0).getMetadata().get("raw"));
    }

    @Test
    void searchShouldExcludeConfiguredChunksAndDocuments() {
        SearchRequest request = new SearchRequest();
        request.setQuery("过滤引用");
        request.setKbIds(List.of("kb-a"));
        request.setExcludeChunkIds(List.of("chunk-excluded"));
        request.setExcludeDocIds(List.of("doc-excluded"));
        request.setSimilarityThreshold(0.0f);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb-a");
        knowledgeBase.setName("RAG 教学库");
        knowledgeBase.setIsEnabled(true);

        Chunk kept = chunk("chunk-kept", "doc-kept");
        Chunk excludedByDoc = chunk("chunk-doc-excluded", "doc-excluded");

        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseRepository.findById("kb-a")).thenReturn(Optional.of(knowledgeBase));
        when(embeddingService.embed(request.getQuery())).thenReturn(List.of(1.0f));
        when(vectorStoreService.search(List.of("kb-a"), List.of(1.0f), 20))
                .thenReturn(List.of(
                        new VectorStoreService.VectorSearchResult("chunk-excluded", "kb-a", "排除 chunk", 0.9f),
                        new VectorStoreService.VectorSearchResult("chunk-doc-excluded", "kb-a", "排除 doc", 0.8f),
                        new VectorStoreService.VectorSearchResult("chunk-kept", "kb-a", "保留", 0.7f)
                ));
        when(chunkRepository.findById("chunk-doc-excluded")).thenReturn(Optional.of(excludedByDoc));
        when(chunkRepository.findById("chunk-kept")).thenReturn(Optional.of(kept));

        List<SearchResult> results = retrievalService.search(request);

        assertEquals(1, results.size());
        assertEquals("chunk-kept", results.get(0).getChunkId());
        assertTrue(results.stream().noneMatch(result -> "doc-excluded".equals(result.getDocId())));
    }

    private Chunk chunk(String chunkId, String docId) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setDocId(docId);
        chunk.setKbId("kb-a");
        return chunk;
    }
}
