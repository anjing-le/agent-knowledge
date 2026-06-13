package com.anjing.knowledge.service;

import com.anjing.annotation.Facade;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.response.DocumentProcessingTaskResponse;
import com.anjing.knowledge.model.response.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for the visible RAG ingestion workflow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentService documentService;
    private final DocumentProcessingTaskService taskService;
    private final DocumentProcessingService processingService;
    private final PlatformTransactionManager transactionManager;

    @Facade(scene = "上传 RAG 文档", enableValidation = false)
    public DocumentResponse uploadDocument(String kbId, MultipartFile file,
                                           String parserStrategyId, String chunkStrategyId) throws IOException {
        Document document;
        try {
            document = runInTransaction(() -> {
                try {
                    Document created = documentService.createUploadedDocument(kbId, file, parserStrategyId, chunkStrategyId);
                    taskService.createPendingTask(created, "文档已上传，等待处理");
                    return created;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }

        triggerProcessing(document.getDocId(), "已触发文档异步处理");
        return DocumentResponse.fromEntity(document);
    }

    @Facade(scene = "批量上传 RAG 文档", enableValidation = false)
    public List<DocumentResponse> batchUploadDocuments(String kbId, MultipartFile[] files,
                                                       String parserStrategyId, String chunkStrategyId) {
        List<DocumentResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                responses.add(uploadDocument(kbId, file, parserStrategyId, chunkStrategyId));
            } catch (Exception e) {
                log.error("上传文档失败: fileName={}, error={}", file.getOriginalFilename(), e.getMessage());
            }
        }
        return responses;
    }

    @Facade(scene = "重新处理 RAG 文档", enableValidation = false)
    public void reprocessDocument(String docId) {
        Document document = runInTransaction(() -> {
            Document reset = documentService.resetDocumentForReprocess(docId);
            taskService.createPendingTask(reset, "文档已提交重新处理");
            return reset;
        });

        triggerProcessing(document.getDocId(), "已触发文档重新处理");
    }

    @Facade(scene = "查询文档处理任务", enableValidation = false)
    public List<DocumentProcessingTaskResponse> listDocumentTasks(String docId) {
        return taskService.listByDocument(docId);
    }

    private <T> T runInTransaction(TransactionWork<T> work) {
        T result = new TransactionTemplate(transactionManager).execute(status -> work.execute());
        if (result == null) {
            throw new IllegalStateException("事务执行未返回结果");
        }
        return result;
    }

    private void triggerProcessing(String docId, String successMessage) {
        try {
            processingService.processDocumentAsync(docId);
            log.info("{}: docId={}", successMessage, docId);
        } catch (Exception e) {
            log.error("触发文档处理失败: docId={}, error={}", docId, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute();
    }
}
