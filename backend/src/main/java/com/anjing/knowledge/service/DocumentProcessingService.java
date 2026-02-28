package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.FileStorageRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档处理服务（RAG 核心编排）
 *
 * 编排完整的文档处理流程：解析 → 分块 → 向量化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocParserClient docParserClient;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChunkRepository chunkRepository;
    private final FileStorageRepository fileStorageRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private static final AtomicInteger CHUNK_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 异步处理文档（完整 RAG 管道）
     *
     * 流程：文档解析 → 文本分块 → 向量化 → 向量存储
     */
    @Async
    public void processDocumentAsync(String docId) {
        log.info("开始异步处理文档: docId={}", docId);
        try {
            processDocument(docId);
        } catch (Exception e) {
            log.error("文档处理异常: docId={}, error={}", docId, e.getMessage(), e);
            documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f, "处理异常: " + e.getMessage());
        }
    }

    /**
     * 处理文档（同步执行）
     * 不加 @Transactional，让每个阶段的状态更新能独立提交，前端可实时看到进度
     */
    public void processDocument(String docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + docId));

        String kbId = doc.getKbId();
        KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在: " + kbId));

        // === 阶段1：文档解析 ===
        log.info("[RAG] 阶段1 - 文档解析: docId={}, docName={}", docId, doc.getDocName());
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSING, 0.1f, "正在解析文档...");

        DocParserClient.ParseResult parseResult = parseDocument(doc);
        if (!parseResult.isSuccess()) {
            documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f,
                    "解析失败: " + parseResult.getErrorMessage());
            return;
        }

        // === 阶段2：文本分块 ===
        log.info("[RAG] 阶段2 - 文本分块: docId={}", docId);
        documentService.updateDocumentStatus(docId, DocumentStatus.CHUNKING, 0.3f, "正在分块...");

        List<Chunk> chunks = createChunks(doc, parseResult, kb.getChunkSize(), kb.getChunkOverlap());
        if (chunks.isEmpty()) {
            documentService.updateDocumentStatus(docId, DocumentStatus.CHUNK_FAILED, 0.0f, "分块失败: 未生成有效分片");
            return;
        }

        // 保存 chunks 到数据库
        chunkRepository.saveAll(chunks);

        // 更新文档的 chunk 数量和 token 数量
        doc.setChunkNum(chunks.size());
        int totalTokens = chunks.stream().mapToInt(c -> c.getTokenCount() != null ? c.getTokenCount() : 0).sum();
        doc.setTokenNum(totalTokens);
        documentRepository.save(doc);

        log.info("[RAG] 分块完成: docId={}, chunkCount={}, totalTokens={}", docId, chunks.size(), totalTokens);

        // === 阶段3：向量化 ===
        log.info("[RAG] 阶段3 - 向量化: docId={}, embeddingModel={}", docId, kb.getEmbeddingModel());
        documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");

        boolean embeddingSuccess = embedChunks(kbId, chunks, kb.getEmbeddingModel());
        if (!embeddingSuccess) {
            documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
            return;
        }

        // === 完成 ===
        documentService.updateDocumentStatus(docId, DocumentStatus.COMPLETED, 1.0f, "处理完成");
        log.info("[RAG] 文档处理完成: docId={}, chunks={}, tokens={}", docId, chunks.size(), totalTokens);
    }

    /**
     * 解析文档（必须依赖 doc-parser 服务，不做降级）
     */
    private DocParserClient.ParseResult parseDocument(Document doc) {
        String filePath = fileStorageRepository.findById(doc.getFileId())
                .map(fs -> fs.getStoragePath())
                .orElse(null);

        if (filePath == null) {
            return DocParserClient.ParseResult.error("文件存储路径不存在");
        }

        if (!docParserClient.isHealthy()) {
            return DocParserClient.ParseResult.error("doc-parser 服务不可用，请确保 doc-parser 已启动（端口9001）");
        }

        return docParserClient.parseDocument(filePath, mapDocType(doc.getDocType()));
    }

    /**
     * 创建文档分片（使用知识库配置的 chunkSize 和 overlap）
     */
    private List<Chunk> createChunks(Document doc, DocParserClient.ParseResult parseResult, int chunkSize, int chunkOverlap) {
        List<Chunk> chunks = new ArrayList<>();
        String taskId = generateTaskId();

        // 优先使用 doc-parser 返回的分块结果
        if (parseResult.getChunks() != null && !parseResult.getChunks().isEmpty()) {
            for (DocParserClient.ChunkData chunkData : parseResult.getChunks()) {
                Chunk chunk = new Chunk();
                chunk.setChunkId(generateChunkId());
                chunk.setDocId(doc.getDocId());
                chunk.setKbId(doc.getKbId());
                chunk.setTaskId(taskId);
                chunk.setContent(chunkData.getContent());
                chunk.setChunkIndex(chunkData.getIndex());
                chunk.setChunkLength(chunkData.getContent().length());
                chunk.setTokenCount(chunkData.getTokenCount() > 0 ? chunkData.getTokenCount() : estimateTokens(chunkData.getContent()));
                chunk.setMetadata(chunkData.getMetadata() != null ? toJsonString(chunkData.getMetadata()) : null);
                chunk.setEmbeddingStatus(EmbeddingStatus.NOT_EMBEDDED.getCode());
                chunk.setIsEnabled(true);
                chunk.setCreatedAt(LocalDateTime.now());
                chunks.add(chunk);
            }
        } else if (parseResult.getContent() != null && !parseResult.getContent().isEmpty()) {
            // 如果没有分块结果，使用简单的固定长度分块
            chunks = simpleChunking(doc, parseResult.getContent(), taskId, chunkSize, chunkOverlap);
        }

        return chunks;
    }

    /**
     * 固定长度分块（参数由知识库配置决定）
     */
    private List<Chunk> simpleChunking(Document doc, String content, String taskId, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        log.info("[RAG] 分块参数: chunkSize={}, overlap={}, docId={}", chunkSize, overlap, doc.getDocId());
        int index = 0;

        int pos = 0;
        while (pos < content.length()) {
            int end = Math.min(pos + chunkSize, content.length());
            String chunkContent = content.substring(pos, end);

            // 尝试在句子边界处截断
            if (end < content.length()) {
                int lastPeriod = chunkContent.lastIndexOf('。');
                int lastNewline = chunkContent.lastIndexOf('\n');
                int breakPoint = Math.max(lastPeriod, lastNewline);
                if (breakPoint > chunkSize / 2) {
                    chunkContent = chunkContent.substring(0, breakPoint + 1);
                    end = pos + breakPoint + 1;
                }
            }

            Chunk chunk = new Chunk();
            chunk.setChunkId(generateChunkId());
            chunk.setDocId(doc.getDocId());
            chunk.setKbId(doc.getKbId());
            chunk.setTaskId(taskId);
            chunk.setContent(chunkContent.trim());
            chunk.setChunkIndex(index);
            chunk.setChunkLength(chunkContent.trim().length());
            chunk.setTokenCount(estimateTokens(chunkContent));
            chunk.setEmbeddingStatus(EmbeddingStatus.NOT_EMBEDDED.getCode());
            chunk.setIsEnabled(true);
            chunk.setCreatedAt(LocalDateTime.now());
            chunks.add(chunk);

            pos = end - overlap;
            if (pos <= chunks.get(chunks.size() - 1).getChunkIndex()) {
                pos = end;
            }
            pos = Math.max(pos, end - overlap);
            if (end >= content.length()) {
                break;
            }
            index++;
        }

        return chunks;
    }

    /**
     * 对 chunks 进行向量化并存储
     */
    private boolean embedChunks(String kbId, List<Chunk> chunks, String embeddingModel) {
        try {
            int batchSize = 20;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chunks.size());
                List<Chunk> batch = chunks.subList(i, end);

                List<String> contents = new ArrayList<>();
                List<String> chunkIds = new ArrayList<>();
                for (Chunk chunk : batch) {
                    contents.add(chunk.getContent());
                    chunkIds.add(chunk.getChunkId());
                }

                // 更新状态为向量化中
                chunkRepository.batchUpdateEmbeddingStatus(chunkIds, EmbeddingStatus.EMBEDDING.getCode());

                List<List<Float>> vectors = embeddingService.embedBatch(contents, embeddingModel);

                if (vectors.size() != contents.size()) {
                    log.error("Embedding 返回数量不匹配: expected={}, actual={}", contents.size(), vectors.size());
                    chunkRepository.batchUpdateEmbeddingStatus(chunkIds, EmbeddingStatus.FAILED.getCode());
                    return false;
                }

                // 存储到向量库
                vectorStoreService.upsertBatch(kbId, chunkIds, vectors, contents);

                // 更新 chunk 的向量化状态和向量 ID
                for (int j = 0; j < batch.size(); j++) {
                    Chunk chunk = batch.get(j);
                    chunk.setEmbeddingStatus(EmbeddingStatus.EMBEDDED.getCode());
                    chunk.setVectorId(chunk.getChunkId());
                }
                chunkRepository.saveAll(batch);

                log.info("[RAG] 向量化批次完成: batch={}/{}, kbId={}", (i / batchSize) + 1,
                        (chunks.size() + batchSize - 1) / batchSize, kbId);
            }

            return true;
        } catch (Exception e) {
            log.error("向量化处理异常: kbId={}, error={}", kbId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 估算 token 数（简单估算：中文1字≈1.5token，英文1词≈1token）
     */
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

    /**
     * 映射文档类型到 doc-parser 的类型
     */
    private String mapDocType(String fileExtension) {
        return switch (fileExtension.toLowerCase()) {
            case "pdf", "doc", "docx" -> "DOCUMENT_BASIC";
            case "xls", "xlsx" -> "STANDARD_WORKBOOK";
            case "txt", "md" -> "PLAIN_TEXT";
            default -> "DOCUMENT_BASIC";
        };
    }

    private String generateChunkId() {
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        int counter = CHUNK_COUNTER.incrementAndGet();
        return String.format("chunk_%s_%04d", dateStr, counter);
    }

    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
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
