package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.util.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates RAG chunks from doc-parser output and knowledge-base chunk settings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkingService {

    private static final AtomicInteger CHUNK_COUNTER = new AtomicInteger(0);
    private static final AtomicInteger TASK_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ObjectMapper objectMapper;

    public List<Chunk> createChunks(Document doc, DocParserClient.ParseResult parseResult,
                                    int chunkSize, int chunkOverlap) {
        String taskId = generateTaskId();

        if (parseResult.getChunks() != null && !parseResult.getChunks().isEmpty()) {
            return parserChunks(doc, parseResult.getChunks(), taskId);
        }
        if (parseResult.getContent() != null && !parseResult.getContent().isEmpty()) {
            return simpleChunking(doc, parseResult.getContent(), taskId, chunkSize, chunkOverlap);
        }
        return List.of();
    }

    private List<Chunk> parserChunks(Document doc, List<DocParserClient.ChunkData> chunkDataList, String taskId) {
        List<Chunk> chunks = new ArrayList<>();
        for (DocParserClient.ChunkData chunkData : chunkDataList) {
            String content = chunkData.getContent() == null ? "" : chunkData.getContent();
            if (content.isBlank()) {
                continue;
            }
            Chunk chunk = baseChunk(doc, taskId, content, chunkData.getIndex());
            chunk.setTokenCount(chunkData.getTokenCount() > 0 ? chunkData.getTokenCount() : estimateTokens(content));
            chunk.setMetadata(chunkData.getMetadata() != null ? toJsonString(chunkData.getMetadata()) : null);
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<Chunk> simpleChunking(Document doc, String content, String taskId, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        int normalizedChunkSize = Math.max(chunkSize, 1);
        int normalizedOverlap = Math.max(Math.min(overlap, normalizedChunkSize - 1), 0);
        log.info("[RAG] 分块参数: chunkSize={}, overlap={}, docId={}",
                normalizedChunkSize, normalizedOverlap, doc.getDocId());

        int index = 0;
        int pos = 0;
        while (pos < content.length()) {
            int end = Math.min(pos + normalizedChunkSize, content.length());
            String chunkContent = content.substring(pos, end);

            if (end < content.length()) {
                int breakPoint = sentenceBreakPoint(chunkContent, normalizedChunkSize);
                if (breakPoint > -1) {
                    chunkContent = chunkContent.substring(0, breakPoint + 1);
                    end = pos + breakPoint + 1;
                }
            }

            String trimmedContent = chunkContent.trim();
            if (!trimmedContent.isEmpty()) {
                chunks.add(baseChunk(doc, taskId, trimmedContent, index));
                index++;
            }

            if (end >= content.length()) {
                break;
            }
            int nextPos = end - normalizedOverlap;
            pos = nextPos <= pos ? end : nextPos;
        }

        return chunks;
    }

    private Chunk baseChunk(Document doc, String taskId, String content, int chunkIndex) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(generateChunkId());
        chunk.setDocId(doc.getDocId());
        chunk.setKbId(doc.getKbId());
        chunk.setTaskId(taskId);
        chunk.setContent(content);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkLength(content.length());
        chunk.setTokenCount(estimateTokens(content));
        chunk.setEmbeddingStatus(EmbeddingStatus.NOT_EMBEDDED.getCode());
        chunk.setIsEnabled(true);
        chunk.setCreatedAt(DateUtils.nowLocalDateTime());
        return chunk;
    }

    private int sentenceBreakPoint(String chunkContent, int chunkSize) {
        int lastPeriod = chunkContent.lastIndexOf('。');
        int lastNewline = chunkContent.lastIndexOf('\n');
        int breakPoint = Math.max(lastPeriod, lastNewline);
        return breakPoint > chunkSize / 2 ? breakPoint : -1;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
    }

    private String generateChunkId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = CHUNK_COUNTER.incrementAndGet();
        return String.format("chunk_%s_%04d", dateStr, counter);
    }

    private String generateTaskId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = TASK_COUNTER.incrementAndGet();
        return String.format("task_%s_%04d", dateStr, counter);
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
