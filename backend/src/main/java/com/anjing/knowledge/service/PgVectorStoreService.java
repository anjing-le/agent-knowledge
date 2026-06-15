package com.anjing.knowledge.service;

import com.anjing.config.properties.VectorStoreProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * pgvector adapter for production vector retrieval.
 *
 * <p>The default dev/test provider remains memory. This adapter is only enabled
 * when app.vector-store.provider=pgvector.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.vector-store", name = "provider", havingValue = "pgvector")
public class PgVectorStoreService implements VectorStoreService {

    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final VectorStoreProperties vectorStoreProperties;

    @PostConstruct
    void maybeInitializeSchema() {
        if (!vectorStoreProperties.getPgvector().isSchemaInitializationEnabled()) {
            return;
        }
        String tableName = tableName();
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    chunk_id VARCHAR(128) PRIMARY KEY,
                    kb_id VARCHAR(128) NOT NULL,
                    embedding vector,
                    content TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(tableName));
        log.info("pgvector schema initialized: table={}", tableName);
    }

    @Override
    public void upsert(String kbId, String chunkId, List<Float> vector, String content) {
        upsertBatch(kbId, List.of(chunkId), List.of(vector), List.of(content));
    }

    @Override
    public void upsertBatch(String kbId, List<String> chunkIds, List<List<Float>> vectors, List<String> contents) {
        validateBatch(chunkIds, vectors, contents);

        String sql = """
                INSERT INTO %s (chunk_id, kb_id, embedding, content, updated_at)
                VALUES (?, ?, ?::vector, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (chunk_id) DO UPDATE SET
                    kb_id = EXCLUDED.kb_id,
                    embedding = EXCLUDED.embedding,
                    content = EXCLUDED.content,
                    updated_at = CURRENT_TIMESTAMP
                """.formatted(tableName());

        for (int index = 0; index < chunkIds.size(); index++) {
            jdbcTemplate.update(
                    sql,
                    chunkIds.get(index),
                    kbId,
                    vectorLiteral(vectors.get(index)),
                    contents.get(index)
            );
        }
        log.info("pgvector 批量写入完成: kbId={}, count={}", kbId, chunkIds.size());
    }

    @Override
    public List<VectorSearchResult> search(List<String> kbIds, List<Float> queryVector, int topK) {
        if (kbIds == null || kbIds.isEmpty() || queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }

        String vector = vectorLiteral(queryVector);
        List<VectorSearchResult> allResults = new ArrayList<>();
        for (String kbId : kbIds) {
            allResults.addAll(searchKnowledgeBase(kbId, vector, topK));
        }

        List<VectorSearchResult> results = allResults.stream()
                .sorted(Comparator.comparing(VectorSearchResult::getScore).reversed())
                .limit(topK)
                .toList();
        log.info("pgvector 检索完成: kbIds={}, candidateCount={}, topK={}, resultCount={}",
                kbIds, allResults.size(), topK, results.size());
        return results;
    }

    private List<VectorSearchResult> searchKnowledgeBase(String kbId, String queryVector, int topK) {
        String sql = """
                SELECT chunk_id, kb_id, content, 1 - (embedding <=> ?::vector) AS score
                FROM %s
                WHERE kb_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(tableName());

        return jdbcTemplate.query(
                sql,
                new Object[]{queryVector, kbId, queryVector, topK},
                (rs, rowNum) -> new VectorSearchResult(
                        rs.getString("chunk_id"),
                        rs.getString("kb_id"),
                        rs.getString("content"),
                        rs.getFloat("score")
                )
        );
    }

    @Override
    public void deleteByDocChunks(String kbId, List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM %s WHERE kb_id = ? AND chunk_id = ?".formatted(tableName());
        for (String chunkId : chunkIds) {
            jdbcTemplate.update(sql, kbId, chunkId);
        }
        log.info("pgvector 删除文档向量完成: kbId={}, count={}", kbId, chunkIds.size());
    }

    @Override
    public void deleteByKbId(String kbId) {
        jdbcTemplate.update("DELETE FROM %s WHERE kb_id = ?".formatted(tableName()), kbId);
        log.info("pgvector 删除知识库向量完成: kbId={}", kbId);
    }

    @Override
    public int getVectorCount(String kbId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE kb_id = ?".formatted(tableName()),
                Integer.class,
                kbId
        );
        return count == null ? 0 : count;
    }

    private void validateBatch(List<String> chunkIds, List<List<Float>> vectors, List<String> contents) {
        if (chunkIds == null || vectors == null || contents == null) {
            throw new IllegalArgumentException("pgvector 批量写入参数不能为空");
        }
        if (chunkIds.size() != vectors.size() || chunkIds.size() != contents.size()) {
            throw new IllegalArgumentException("pgvector 批量写入参数数量不一致");
        }
    }

    private String vectorLiteral(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("pgvector 向量不能为空");
        }
        return vector.stream()
                .map(this::componentLiteral)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String componentLiteral(Float value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("pgvector 向量不能包含非法值");
        }
        return Float.toString(value);
    }

    private String tableName() {
        String tableName = vectorStoreProperties.getPgvector().getTableName();
        if (tableName == null || !SQL_IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw new IllegalStateException("pgvector tableName must be a safe SQL identifier");
        }
        return tableName;
    }
}
