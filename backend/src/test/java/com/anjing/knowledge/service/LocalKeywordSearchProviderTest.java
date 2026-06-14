package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalKeywordSearchProviderTest {

    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final LocalKeywordSearchProvider keywordSearchProvider = new LocalKeywordSearchProvider(chunkRepository);

    @Test
    void searchShouldRankLexicalMatches() {
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(
                chunk("chunk-unrelated", "database migration"),
                chunk("chunk-partial", "agent runtime"),
                chunk("chunk-relevant", "agent scaffold retrieval")
        ));

        List<KeywordSearchProvider.KeywordSearchHit> results =
                keywordSearchProvider.search("agent scaffold", List.of("kb-a"), 2);

        assertEquals(2, results.size());
        assertEquals("chunk-relevant", results.get(0).chunkId());
        assertTrue(results.get(0).score() > results.get(1).score());
    }

    @Test
    void searchShouldSupportChineseTeachingQueries() {
        when(chunkRepository.findByKbIdAndIsEnabledTrue("kb-a")).thenReturn(List.of(
                chunk("chunk-unrelated", "数据库迁移和缓存配置"),
                chunk("chunk-relevant", "脚手架生长出 RAG agent 的知识库链路")
        ));

        List<KeywordSearchProvider.KeywordSearchHit> results =
                keywordSearchProvider.search("脚手架 RAG agent", List.of("kb-a"), 3);

        assertEquals(1, results.size());
        assertEquals("chunk-relevant", results.get(0).chunkId());
        assertTrue(results.get(0).score() > 0.0f);
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
