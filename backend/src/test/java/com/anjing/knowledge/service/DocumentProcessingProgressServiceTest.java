package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.enums.DocumentStatus;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DocumentProcessingProgressServiceTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentProcessingTaskService taskService = mock(DocumentProcessingTaskService.class);
    private final DocumentProcessingProgressService progressService = new DocumentProcessingProgressService(
            documentService,
            taskService,
            new DocParserStatusMapper()
    );

    @Test
    void startShouldEnsureLatestTask() {
        Document document = new Document();

        progressService.start(document);

        verify(taskService).ensureLatestTask(document, "文档开始处理");
    }

    @Test
    void markParsingShouldUpdateTaskAndDocumentStatus() {
        progressService.markParsing("doc_001");

        verify(taskService).markRunning("doc_001", "PARSING", 0.1f, "正在调用 Python doc-parser 解析文档");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.PARSING, 0.1f, "正在解析文档...");
    }

    @Test
    void markParsingFailedShouldUpdateTaskAndDocumentStatus() {
        progressService.markParsingFailed("doc_001", "doc-parser 服务不可用");

        verify(taskService).markFailed("doc_001", "PARSING", "doc-parser 服务不可用");
        verify(documentService).updateDocumentStatus(
                "doc_001",
                DocumentStatus.PARSE_FAILED,
                0.0f,
                "解析失败: doc-parser 服务不可用"
        );
    }

    @Test
    void applyDocParserStatusShouldExposeRunningParserTask() {
        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId("parser_task_001");
        status.setStatus("RUNNING");
        status.setMessage("OCR running");

        progressService.applyDocParserStatus("doc_001", status);

        verify(taskService).markDocParserStatus(
                "doc_001",
                "parser_task_001",
                "RUNNING",
                "PARSING",
                0.2f,
                "OCR running",
                null
        );
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.PARSING, 0.2f, "OCR running");
    }

    @Test
    void applyDocParserStatusShouldMoveSucceededParserTaskIntoChunking() {
        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId("parser_task_001");
        status.setStatus("SUCCEEDED");

        progressService.applyDocParserStatus("doc_001", status);

        verify(taskService).markDocParserStatus(
                "doc_001",
                "parser_task_001",
                "RUNNING",
                "CHUNKING",
                0.3f,
                "doc-parser 解析完成，进入切片阶段",
                null
        );
        verify(documentService).updateDocumentStatus(
                "doc_001",
                DocumentStatus.CHUNKING,
                0.3f,
                "doc-parser 解析完成，进入切片阶段"
        );
    }

    @Test
    void applyDocParserStatusShouldExposeFailedParserTask() {
        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId("parser_task_001");
        status.setStatus("FAILED");
        status.setError("OCR failed");

        progressService.applyDocParserStatus("doc_001", status);

        verify(taskService).markDocParserStatus(
                "doc_001",
                "parser_task_001",
                "FAILED",
                "PARSING",
                0.0f,
                "解析失败: OCR failed",
                "OCR failed"
        );
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.PARSE_FAILED, 0.0f, "解析失败: OCR failed");
    }

    @Test
    void markChunkingShouldUpdateTaskAndDocumentStatus() {
        progressService.markChunking("doc_001");

        verify(taskService).markRunning("doc_001", "CHUNKING", 0.3f, "正在生成文档切片");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.CHUNKING, 0.3f, "正在分块...");
    }

    @Test
    void markChunkingFailedShouldUpdateTaskAndDocumentStatus() {
        progressService.markChunkingFailed("doc_001", "未生成有效分片");

        verify(taskService).markFailed("doc_001", "CHUNKING", "未生成有效分片");
        verify(documentService).updateDocumentStatus(
                "doc_001",
                DocumentStatus.CHUNK_FAILED,
                0.0f,
                "分块失败: 未生成有效分片"
        );
    }

    @Test
    void markEmbeddingShouldUpdateTaskAndDocumentStatus() {
        progressService.markEmbedding("doc_001");

        verify(taskService).markRunning("doc_001", "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");
    }

    @Test
    void markEmbeddingFailedShouldUpdateTaskAndDocumentStatus() {
        progressService.markEmbeddingFailed("doc_001", "向量化失败");

        verify(taskService).markFailed("doc_001", "EMBEDDING", "向量化失败");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
    }

    @Test
    void markSucceededShouldUpdateTaskAndDocumentStatus() {
        progressService.markSucceeded("doc_001");

        verify(taskService).markSucceeded("doc_001", "文档处理完成");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.COMPLETED, 1.0f, "处理完成");
    }

    @Test
    void markUnexpectedFailedShouldStillUpdateDocumentWhenTaskUpdateFails() {
        doThrow(new RuntimeException("task store unavailable"))
                .when(taskService).markFailed("doc_001", "FAILED", "处理异常");

        progressService.markUnexpectedFailed("doc_001", "处理异常");

        verify(taskService).markFailed("doc_001", "FAILED", "处理异常");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.PARSE_FAILED, 0.0f, "处理异常: 处理异常");
    }
}
