package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.enums.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralizes document processing task and document status transitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingProgressService {

    private final DocumentService documentService;
    private final DocumentProcessingTaskService taskService;
    private final DocParserStatusMapper docParserStatusMapper;

    public void start(Document document) {
        taskService.ensureLatestTask(document, "文档开始处理");
    }

    public void markParsing(String docId) {
        taskService.markRunning(docId, "PARSING", 0.1f, "正在调用 Python doc-parser 解析文档");
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSING, 0.1f, "正在解析文档...");
    }

    public void markParsingFailed(String docId, String message) {
        taskService.markFailed(docId, "PARSING", message);
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f, "解析失败: " + message);
    }

    public void applyDocParserStatus(String docId, DocParserClient.AsyncParseStatus status) {
        if (status == null) {
            markDocParserFailedStatus(docId, null, "doc-parser 状态为空");
            return;
        }
        if (!status.isSuccess()) {
            markDocParserFailedStatus(docId, status.getTaskId(), firstText(status.getErrorMessage(), "doc-parser 状态查询失败"));
            return;
        }

        DocParserStatusMapper.MappedStatus mappedStatus = docParserStatusMapper.map(status.getStatus());
        String errorMessage = firstTextOrNull(status.getError(), status.getErrorMessage());
        String message = buildDocParserMessage(status, mappedStatus, errorMessage);
        taskService.markDocParserStatus(
                docId,
                status.getTaskId(),
                mappedStatus.taskStatus(),
                mappedStatus.taskPhase(),
                mappedStatus.progress(),
                message,
                errorMessage
        );
        documentService.updateDocumentStatus(docId, mappedStatus.documentStatus(), mappedStatus.progress(), message);
    }

    public void markChunking(String docId) {
        taskService.markRunning(docId, "CHUNKING", 0.3f, "正在生成文档切片");
        documentService.updateDocumentStatus(docId, DocumentStatus.CHUNKING, 0.3f, "正在分块...");
    }

    public void markChunkingFailed(String docId, String message) {
        taskService.markFailed(docId, "CHUNKING", message);
        documentService.updateDocumentStatus(docId, DocumentStatus.CHUNK_FAILED, 0.0f, "分块失败: " + message);
    }

    public void markEmbedding(String docId) {
        taskService.markRunning(docId, "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");
        documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");
    }

    public void markEmbeddingFailed(String docId, String message) {
        taskService.markFailed(docId, "EMBEDDING", message);
        documentService.updateDocumentStatus(docId, DocumentStatus.EMBEDDING_FAILED, 0.0f, message);
    }

    public void markSucceeded(String docId) {
        taskService.markSucceeded(docId, "文档处理完成");
        documentService.updateDocumentStatus(docId, DocumentStatus.COMPLETED, 1.0f, "处理完成");
    }

    public void markUnexpectedFailed(String docId, String message) {
        try {
            taskService.markFailed(docId, "FAILED", message);
        } catch (Exception taskError) {
            log.warn("更新文档处理任务失败: docId={}, error={}", docId, taskError.getMessage());
        }
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f, "处理异常: " + message);
    }

    private void markDocParserFailedStatus(String docId, String parserTaskId, String errorMessage) {
        String message = "解析失败: " + errorMessage;
        taskService.markDocParserStatus(docId, parserTaskId, "FAILED", "PARSING", 0.0f, message, errorMessage);
        documentService.updateDocumentStatus(docId, DocumentStatus.PARSE_FAILED, 0.0f, message);
    }

    private String buildDocParserMessage(DocParserClient.AsyncParseStatus status,
                                         DocParserStatusMapper.MappedStatus mappedStatus,
                                         String errorMessage) {
        if (mappedStatus.documentStatus().isFailedState()) {
            return "解析失败: " + firstText(errorMessage, status.getMessage(), status.getStatus());
        }
        if (status.getMessage() != null && !status.getMessage().isBlank()) {
            return status.getMessage();
        }
        if ("CHUNKING".equals(mappedStatus.taskPhase())) {
            return "doc-parser 解析完成，进入切片阶段";
        }
        return "doc-parser 状态: " + status.getStatus();
    }

    private String firstText(String... values) {
        String firstText = firstTextOrNull(values);
        return firstText == null ? "" : firstText;
    }

    private String firstTextOrNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
