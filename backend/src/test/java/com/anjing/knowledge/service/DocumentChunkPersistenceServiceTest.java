package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentChunkPersistenceServiceTest {

    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentChunkPersistenceService persistenceService = new DocumentChunkPersistenceService(
            chunkRepository,
            documentRepository
    );

    @Test
    void saveChunksShouldPersistChunksAndUpdateDocumentStatistics() {
        Document document = new Document();
        document.setDocId("doc_001");
        Chunk first = chunk("chunk_001", 12);
        Chunk second = chunk("chunk_002", null);

        DocumentChunkPersistenceService.PersistedChunks result =
                persistenceService.saveChunks(document, List.of(first, second));

        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.totalTokens()).isEqualTo(12);
        assertThat(document.getChunkNum()).isEqualTo(2);
        assertThat(document.getTokenNum()).isEqualTo(12);
        verify(chunkRepository).saveAll(List.of(first, second));
        verify(documentRepository).save(document);
    }

    private Chunk chunk(String chunkId, Integer tokenCount) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setTokenCount(tokenCount);
        return chunk;
    }
}
