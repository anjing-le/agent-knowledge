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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalResultEnrichmentServiceTest {

    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final RetrievalResultEnrichmentService enrichmentService = new RetrievalResultEnrichmentService(
            knowledgeBaseRepository,
            documentRepository,
            chunkRepository,
            new ObjectMapper()
    );

    @Test
    void enrichShouldAttachCitationMetadata() {
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk_001");
        chunk.setDocId("doc_001");
        chunk.setMetadata("{\"page_idx\":[1],\"content_type\":\"markdown\"}");

        Document document = new Document();
        document.setDocId("doc_001");
        document.setDocName("脚手架到 RAG.md");

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb_001");
        knowledgeBase.setName("RAG 教学库");

        when(chunkRepository.findById("chunk_001")).thenReturn(Optional.of(chunk));
        when(documentRepository.findById("doc_001")).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findById("kb_001")).thenReturn(Optional.of(knowledgeBase));

        SearchResult result = enrichmentService.enrich(
                new VectorStoreService.VectorSearchResult("chunk_001", "kb_001", "命中文本", 0.91f)
        );

        assertThat(result.getChunkId()).isEqualTo("chunk_001");
        assertThat(result.getDocId()).isEqualTo("doc_001");
        assertThat(result.getKbId()).isEqualTo("kb_001");
        assertThat(result.getDocName()).isEqualTo("脚手架到 RAG.md");
        assertThat(result.getKbName()).isEqualTo("RAG 教学库");
        assertThat(result.getSimilarityScore()).isEqualTo(0.91f);
        assertThat(result.getFinalScore()).isEqualTo(0.91f);
        assertThat(result.getMetadata()).containsEntry("content_type", "markdown");
        assertThat(result.getMetadata().get("page_idx")).isEqualTo(List.of(1));
    }

    @Test
    void enrichShouldKeepRawMetadataWhenMetadataJsonIsInvalid() {
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk_001");
        chunk.setDocId("doc_001");
        chunk.setMetadata("page=1");

        when(chunkRepository.findById("chunk_001")).thenReturn(Optional.of(chunk));

        SearchResult result = enrichmentService.enrich(
                new VectorStoreService.VectorSearchResult("chunk_001", "kb_001", "命中文本", 0.8f)
        );

        assertThat(result.getMetadata()).containsEntry("raw", "page=1");
    }
}
