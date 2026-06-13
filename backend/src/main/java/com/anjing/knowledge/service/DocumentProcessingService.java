package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
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
    private final DocumentService documentService;
    private final DocumentProcessingTaskService taskService;
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
            try {
                taskService.markFailed(docId, "FAILED", e.getMessage());
            } catch (Exception taskError) {
                log.warn("更新文档处理任务失败: docId={}, error={}", docId, taskError.getMessage());
            }
            documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f, "处理异常: " + e.getMessage());
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
        taskService.ensureLatestTask(doc, "文档开始处理");

        // === 阶段1：文档解析 ===
        log.info("[RAG] 阶段1 - 文档解析: docId={}, docName={}", docId, doc.getDocName());
        taskService.markRunning(docId, "PARSING", 0.1f, "正在调用 Python doc-parser 解析文档");
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSING, 0.1f, "正在解析文档...");

        var parseResult = parsingService.parseDocument(doc);
        if (!parseResult.isSuccess()) {
            taskService.markFailed(docId, "PARSING", parseResult.getErrorMessage());
            documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f,
                    "解析失败: " + parseResult.getErrorMessage());
            return;
        }

        // === 阶段2：文本分块 ===
        log.info("[RAG] 阶段2 - 文本分块: docId={}", docId);
        taskService.markRunning(docId, "CHUNKING", 0.3f, "正在生成文档切片");
        documentService.updateDocumentStatus(docId, DocumentStatus.CHUNKING, 0.3f, "正在分块...");

        List<Chunk> chunks = chunkingService.createChunks(doc, parseResult, kb.getChunkSize(), kb.getChunkOverlap());
        if (chunks.isEmpty()) {
            taskService.markFailed(docId, "CHUNKING", "未生成有效分片");
            documentService.updateDocumentStatus(docId, DocumentStatus.CHUNK_FAILED, 0.0f, "分块失败: 未生成有效分片");
            return;
        }

        DocumentChunkPersistenceService.PersistedChunks persistedChunks =
                chunkPersistenceService.saveChunks(doc, chunks);

        log.info("[RAG] 分块完成: docId={}, chunkCount={}, totalTokens={}",
                docId, persistedChunks.chunkCount(), persistedChunks.totalTokens());

        // === 阶段3：向量化 ===
        log.info("[RAG] 阶段3 - 向量化: docId={}, embeddingModel={}", docId, kb.getEmbeddingModel());
        taskService.markRunning(docId, "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");
        documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");

        boolean embeddingSuccess = documentEmbeddingService.embedChunks(kbId, chunks, kb.getEmbeddingModel());
        if (!embeddingSuccess) {
            taskService.markFailed(docId, "EMBEDDING", "向量化失败");
            documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
            return;
        }

        // === 完成 ===
        taskService.markSucceeded(docId, "文档处理完成");
        documentService.updateDocumentStatus(docId, DocumentStatus.COMPLETED, 1.0f, "处理完成");
        log.info("[RAG] 文档处理完成: docId={}, chunks={}, tokens={}",
                docId, persistedChunks.chunkCount(), persistedChunks.totalTokens());
    }

}
