package com.anjing.knowledge.service;

import com.anjing.config.properties.VectorStoreProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final VectorStoreProperties vectorStoreProperties = vectorStoreProperties();
    private final PgVectorStoreService vectorStoreService = new PgVectorStoreService(jdbcTemplate, vectorStoreProperties);

    @Test
    void upsertShouldUsePgvectorLiteralAndConfiguredTable() {
        vectorStoreService.upsert("kb-a", "chunk-a", List.of(0.1f, 0.2f), "agent scaffold");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("chunk-a"),
                eq("kb-a"),
                eq("[0.1,0.2]"),
                eq("agent scaffold")
        );
        assertThat(sqlCaptor.getValue())
                .contains("INSERT INTO ak_vectors")
                .contains("?::vector")
                .contains("ON CONFLICT (chunk_id)");
    }

    @Test
    void searchShouldQueryEachKnowledgeBaseAndReturnTopScores() {
        when(jdbcTemplate.query(
                anyString(),
                any(Object[].class),
                anyRowMapper()
        )).thenReturn(
                List.of(new VectorStoreService.VectorSearchResult("chunk-a", "kb-a", "alpha", 0.72f)),
                List.of(new VectorStoreService.VectorSearchResult("chunk-b", "kb-b", "beta", 0.91f))
        );

        List<VectorStoreService.VectorSearchResult> results =
                vectorStoreService.search(List.of("kb-a", "kb-b"), List.of(1.0f, 0.0f), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getChunkId()).isEqualTo("chunk-b");
        assertThat(results.get(0).getScore()).isEqualTo(0.91f);
    }

    @Test
    void deleteAndCountShouldStayScopedToKnowledgeBase() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("kb-a"))).thenReturn(2);

        vectorStoreService.deleteByDocChunks("kb-a", List.of("chunk-a", "chunk-b"));
        vectorStoreService.deleteByKbId("kb-b");

        verify(jdbcTemplate).update("DELETE FROM ak_vectors WHERE kb_id = ? AND chunk_id = ?", "kb-a", "chunk-a");
        verify(jdbcTemplate).update("DELETE FROM ak_vectors WHERE kb_id = ? AND chunk_id = ?", "kb-a", "chunk-b");
        verify(jdbcTemplate).update("DELETE FROM ak_vectors WHERE kb_id = ?", "kb-b");
        assertThat(vectorStoreService.getVectorCount("kb-a")).isEqualTo(2);
    }

    @Test
    void tableNameShouldRejectUnsafeSqlIdentifier() {
        vectorStoreProperties.getPgvector().setTableName("rag_vectors;drop");

        assertThrows(IllegalStateException.class, () ->
                vectorStoreService.upsert("kb-a", "chunk-a", List.of(0.1f), "content")
        );
    }

    private VectorStoreProperties vectorStoreProperties() {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.getPgvector().setTableName("ak_vectors");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private RowMapper<VectorStoreService.VectorSearchResult> anyRowMapper() {
        return any(RowMapper.class);
    }
}
