package com.anjing.knowledge.service;

import com.anjing.config.properties.KeywordSearchProperties;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Bm25KeywordSearchProviderTest {

    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final KeywordSearchProperties keywordSearchProperties = new KeywordSearchProperties();
    private final Bm25KeywordSearchProvider keywordSearchProvider =
            new Bm25KeywordSearchProvider(chunkRepository, keywordSearchProperties);

    @Test
    void searchShouldRankTermFrequencyAndInverseDocumentFrequency() {
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(
                chunk("chunk-common", "agent agent runtime common"),
                chunk("chunk-specific", "agent scaffold scaffold retrieval"),
                chunk("chunk-unrelated", "database migration")
        ));

        List<KeywordSearchProvider.KeywordSearchHit> results =
                keywordSearchProvider.search("agent scaffold retrieval", List.of("kb-a"), 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-specific");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
    }

    @Test
    void searchShouldSupportChineseTeachingQueries() {
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(
                chunk("chunk-unrelated", "数据库迁移和缓存配置"),
                chunk("chunk-relevant", "脚手架生长出 RAG agent 的知识库链路")
        ));

        List<KeywordSearchProvider.KeywordSearchHit> results =
                keywordSearchProvider.search("脚手架 RAG agent", List.of("kb-a"), 3);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-relevant");
        assertThat(results.get(0).score()).isGreaterThan(0.0f);
    }

    @Test
    void searchShouldRespectMinimumScore() {
        keywordSearchProperties.getBm25().setMinimumScore(10.0f);
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(
                chunk("chunk-low", "agent scaffold")
        ));

        List<KeywordSearchProvider.KeywordSearchHit> results =
                keywordSearchProvider.search("agent scaffold", List.of("kb-a"), 3);

        assertThat(results).isEmpty();
    }

    @Test
    void searchShouldSkipRepositoryWhenQueryIsBlank() {
        assertThat(keywordSearchProvider.search(" ", List.of("kb-a"), 3)).isEmpty();

        verifyNoInteractions(chunkRepository);
    }

    private Chunk chunk(String chunkId, String content) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setKbId("kb-a");
        chunk.setContent(content);
        chunk.setIsEnabled(true);
        return chunk;
    }
}
