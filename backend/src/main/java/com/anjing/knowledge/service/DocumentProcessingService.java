package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档处理服务（RAG 核心编排）
 *
 * 编排完整的文档处理流程：解析 → 分块 → 向量化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentProcessingContextService contextService;
    private final DocumentProcessingProgressService progressService;
    private final DocumentParsingService parsingService;
    private final DocumentChunkingService chunkingService;
    private final DocumentChunkPersistenceService chunkPersistenceService;
    private final DocumentEmbeddingService documentEmbeddingService;

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
            progressService.markUnexpectedFailed(docId, e.getMessage());
        }
    }

    /**
     * 处理文档（同步执行）
     * 不加 @Transactional，让每个阶段的状态更新能独立提交，前端可实时看到进度
     */
    public void processDocument(String docId) {
        DocumentProcessingContextService.DocumentProcessingContext context = contextService.loadContext(docId);
        Document doc = context.document();
        KnowledgeBase kb = context.knowledgeBase();
        String kbId = context.kbId();
        progressService.start(doc);

        // === 阶段1：文档解析 ===
        log.info("[RAG] 阶段1 - 文档解析: docId={}, docName={}", docId, doc.getDocName());
        progressService.markParsing(docId);

        var parseResult = parsingService.parseDocument(doc);
        if (!parseResult.isSuccess()) {
            progressService.markParsingFailed(docId, parseResult.getErrorMessage());
            return;
        }

        // === 阶段2：文本分块 ===
        log.info("[RAG] 阶段2 - 文本分块: docId={}", docId);
        progressService.markChunking(docId);

        List<Chunk> chunks = chunkingService.createChunks(doc, parseResult, kb.getChunkSize(), kb.getChunkOverlap());
        if (chunks.isEmpty()) {
            progressService.markChunkingFailed(docId, "未生成有效分片");
            return;
        }

        DocumentChunkPersistenceService.PersistedChunks persistedChunks =
                chunkPersistenceService.saveChunks(doc, chunks);

        log.info("[RAG] 分块完成: docId={}, chunkCount={}, totalTokens={}",
                docId, persistedChunks.chunkCount(), persistedChunks.totalTokens());

        // === 阶段3：向量化 ===
        log.info("[RAG] 阶段3 - 向量化: docId={}, embeddingModel={}", docId, kb.getEmbeddingModel());
        progressService.markEmbedding(docId);

        boolean embeddingSuccess = documentEmbeddingService.embedChunks(kbId, chunks, kb.getEmbeddingModel());
        if (!embeddingSuccess) {
            progressService.markEmbeddingFailed(docId, "向量化失败");
            return;
        }

        // === 完成 ===
        progressService.markSucceeded(docId);
        log.info("[RAG] 文档处理完成: docId={}, chunks={}, tokens={}",
                docId, persistedChunks.chunkCount(), persistedChunks.totalTokens());
    }

}
