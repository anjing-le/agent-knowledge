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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    private final DocParserClient docParserClient;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChunkRepository chunkRepository;
    private final FileStorageRepository fileStorageRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentService documentService;
    private final DocumentProcessingTaskService taskService;
    private final DocumentChunkingService chunkingService;

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
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + docId));

        String kbId = doc.getKbId();
        KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在: " + kbId));
        taskService.ensureLatestTask(doc, "文档开始处理");

        // === 阶段1：文档解析 ===
        log.info("[RAG] 阶段1 - 文档解析: docId={}, docName={}", docId, doc.getDocName());
        taskService.markRunning(docId, "PARSING", 0.1f, "正在调用 Python doc-parser 解析文档");
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSING, 0.1f, "正在解析文档...");

        DocParserClient.ParseResult parseResult = parseDocument(doc);
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
        taskService.markRunning(docId, "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");
        documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");

        boolean embeddingSuccess = embedChunks(kbId, chunks, kb.getEmbeddingModel());
        if (!embeddingSuccess) {
            taskService.markFailed(docId, "EMBEDDING", "向量化失败");
            documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
            return;
        }

        // === 完成 ===
        taskService.markSucceeded(docId, "文档处理完成");
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

}
