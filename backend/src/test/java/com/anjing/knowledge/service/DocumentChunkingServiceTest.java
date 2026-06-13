package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    private final DocumentChunkingService chunkingService = new DocumentChunkingService(new ObjectMapper());

    @Test
    void createChunksShouldPreferDocParserChunksAndPreserveMetadata() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);
        DocParserClient.ChunkData chunkData = new DocParserClient.ChunkData();
        chunkData.setContent("脚手架生长 RAG");
        chunkData.setIndex(7);
        chunkData.setTokenCount(0);
        chunkData.setMetadata(Map.of("page_idx", List.of(1), "content_type", "TEXT"));
        parseResult.setChunks(List.of(chunkData));
        parseResult.setContent("fallback content");

        List<Chunk> chunks = chunkingService.createChunks(document(), parseResult, 500, 50);

        assertThat(chunks).hasSize(1);
        Chunk chunk = chunks.get(0);
        assertThat(chunk.getDocId()).isEqualTo("doc_001");
        assertThat(chunk.getKbId()).isEqualTo("kb_001");
        assertThat(chunk.getChunkIndex()).isEqualTo(7);
        assertThat(chunk.getContent()).isEqualTo("脚手架生长 RAG");
        assertThat(chunk.getTokenCount()).isGreaterThan(0);
        assertThat(chunk.getMetadata()).contains("\"content_type\":\"TEXT\"");
        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.NOT_EMBEDDED.getCode());
        assertThat(chunk.getIsEnabled()).isTrue();
    }

    @Test
    void createChunksShouldFallbackToConfiguredChunkingWithOverlap() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);
        parseResult.setContent("abcdefghi");

        List<Chunk> chunks = chunkingService.createChunks(document(), parseResult, 4, 1);

        assertThat(chunks).extracting(Chunk::getContent).containsExactly("abcd", "defg", "ghi");
        assertThat(chunks).extracting(Chunk::getChunkIndex).containsExactly(0, 1, 2);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getTaskId()).startsWith("task_");
            assertThat(chunk.getChunkId()).startsWith("chunk_");
            assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.NOT_EMBEDDED.getCode());
        });
    }

    @Test
    void createChunksShouldReturnEmptyWhenParserHasNoContent() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);

        assertThat(chunkingService.createChunks(document(), parseResult, 500, 50)).isEmpty();
    }

    private Document document() {
        Document document = new Document();
        document.setDocId("doc_001");
        document.setKbId("kb_001");
        return document;
    }
}
