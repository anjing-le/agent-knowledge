package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.repository.ChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEmbeddingServiceTest {

    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final DocumentEmbeddingService documentEmbeddingService = new DocumentEmbeddingService(
            chunkRepository,
            embeddingService,
            vectorStoreService
    );

    @Test
    void embedChunksShouldUpdateStatusUpsertVectorsAndSaveEmbeddedChunks() {
        Chunk chunk = chunk("chunk_001", "脚手架生长 RAG");
        when(embeddingService.embedBatch(List.of("脚手架生长 RAG"), "text-embedding-3-small"))
                .thenReturn(List.of(List.of(0.1f, 0.2f, 0.3f)));

        boolean success = documentEmbeddingService.embedChunks("kb_001", List.of(chunk), "text-embedding-3-small");

        assertThat(success).isTrue();
        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.EMBEDDED.getCode());
        assertThat(chunk.getVectorId()).isEqualTo("chunk_001");
        verify(chunkRepository).batchUpdateEmbeddingStatus(
                List.of("chunk_001"),
                EmbeddingStatus.EMBEDDING.getCode()
        );
        verify(vectorStoreService).upsertBatch(
                "kb_001",
                List.of("chunk_001"),
                List.of(List.of(0.1f, 0.2f, 0.3f)),
                List.of("脚手架生长 RAG")
        );
        verify(chunkRepository).saveAll(List.of(chunk));
    }

    @Test
    void embedChunksShouldMarkFailedWhenEmbeddingCountDoesNotMatch() {
        Chunk chunk = chunk("chunk_001", "需要向量化的内容");
        when(embeddingService.embedBatch(List.of("需要向量化的内容"), "text-embedding-3-small"))
                .thenReturn(List.of());

        boolean success = documentEmbeddingService.embedChunks("kb_001", List.of(chunk), "text-embedding-3-small");

        assertThat(success).isFalse();
        verify(chunkRepository).batchUpdateEmbeddingStatus(
                List.of("chunk_001"),
                EmbeddingStatus.EMBEDDING.getCode()
        );
        verify(chunkRepository).batchUpdateEmbeddingStatus(
                List.of("chunk_001"),
                EmbeddingStatus.FAILED.getCode()
        );
        verify(vectorStoreService, never()).upsertBatch(anyString(), anyList(), anyList(), anyList());
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void embedChunksShouldReturnFalseWhenProviderThrows() {
        Chunk chunk = chunk("chunk_001", "异常内容");
        when(embeddingService.embedBatch(List.of("异常内容"), "text-embedding-3-small"))
                .thenThrow(new IllegalStateException("provider down"));

        boolean success = documentEmbeddingService.embedChunks("kb_001", List.of(chunk), "text-embedding-3-small");

        assertThat(success).isFalse();
        verify(chunkRepository).batchUpdateEmbeddingStatus(
                List.of("chunk_001"),
                EmbeddingStatus.EMBEDDING.getCode()
        );
        verify(chunkRepository).batchUpdateEmbeddingStatus(
                List.of("chunk_001"),
                EmbeddingStatus.FAILED.getCode()
        );
        verify(vectorStoreService, never()).upsertBatch(anyString(), anyList(), anyList(), anyList());
        verify(chunkRepository, never()).saveAll(anyList());
    }

    private Chunk chunk(String chunkId, String content) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setContent(content);
        return chunk;
    }
}
