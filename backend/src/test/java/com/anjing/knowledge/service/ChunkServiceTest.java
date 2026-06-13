package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.response.ChunkResponse;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.model.response.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkServiceTest {

    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final ChunkService chunkService = new ChunkService(chunkRepository);

    @Test
    void listChunksShouldNormalizePaginationAndReturnPageResult() {
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk_001");
        ArgumentCaptor<Pageable> pageableCaptor = forClass(Pageable.class);
        when(chunkRepository.findByDocIdOrderByChunkIndexAsc(eq("doc_001"), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(chunk), org.springframework.data.domain.PageRequest.of(0, 1), 3));

        PageResult<ChunkResponse> result = chunkService.listChunks("doc_001", 0, 0);

        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
        assertThat(result.getRecords()).extracting(ChunkResponse::getChunkId).containsExactly("chunk_001");
        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    void getChunkShouldReturnRepositoryResult() {
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk_001");
        chunk.setContent("chunk content");
        when(chunkRepository.findById("chunk_001")).thenReturn(Optional.of(chunk));

        assertThat(chunkService.getChunk("chunk_001"))
                .hasValueSatisfying(response -> {
                    assertThat(response.getChunkId()).isEqualTo("chunk_001");
                    assertThat(response.getContent()).isEqualTo("chunk content");
                });
    }

    @Test
    void updateChunkEnabledShouldUpdateAndSaveExistingChunk() {
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk_001");
        chunk.setIsEnabled(false);
        when(chunkRepository.findById("chunk_001")).thenReturn(Optional.of(chunk));

        boolean updated = chunkService.updateChunkEnabled("chunk_001", true);

        assertThat(updated).isTrue();
        assertThat(chunk.getIsEnabled()).isTrue();
        verify(chunkRepository).save(chunk);
    }

    @Test
    void updateChunkEnabledShouldReturnFalseWhenChunkDoesNotExist() {
        when(chunkRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(chunkService.updateChunkEnabled("missing", true)).isFalse();
    }

    @Test
    void countByDocumentShouldDelegateToRepository() {
        when(chunkRepository.countByDocId("doc_001")).thenReturn(12L);

        assertThat(chunkService.countByDocument("doc_001")).isEqualTo(12L);
    }
}
