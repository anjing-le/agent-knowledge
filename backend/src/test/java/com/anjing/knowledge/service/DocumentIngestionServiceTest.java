package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.model.response.DocumentProcessingTaskResponse;
import com.anjing.knowledge.model.response.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIngestionServiceTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentProcessingTaskService taskService = mock(DocumentProcessingTaskService.class);
    private final DocumentProcessingService processingService = mock(DocumentProcessingService.class);
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            documentService,
            taskService,
            processingService,
            transactionManager
    );

    @Test
    void uploadDocumentShouldCreateTaskThenTriggerProcessingAfterTransaction() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        Document document = document("doc_001");
        DocumentProcessingTask task = new DocumentProcessingTask();

        when(documentService.createUploadedDocument("kb_001", file, "parser", "chunk"))
                .thenReturn(document);
        when(taskService.createPendingTask(document, "文档已上传，等待处理"))
                .thenReturn(task);

        DocumentResponse response = ingestionService.uploadDocument("kb_001", file, "parser", "chunk");

        assertThat(response.getDocId()).isEqualTo("doc_001");
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isZero();
        verify(taskService).createPendingTask(document, "文档已上传，等待处理");
        verify(processingService).processDocumentAsync("doc_001");
    }

    @Test
    void batchUploadDocumentsShouldKeepSuccessfulUploadsWhenOneFileFails() throws IOException {
        MultipartFile failedFile = mock(MultipartFile.class);
        MultipartFile successfulFile = mock(MultipartFile.class);
        when(failedFile.getOriginalFilename()).thenReturn("broken.pdf");
        Document document = document("doc_002");

        when(documentService.createUploadedDocument("kb_001", failedFile, null, null))
                .thenThrow(new IOException("read failed"));
        when(documentService.createUploadedDocument("kb_001", successfulFile, null, null))
                .thenReturn(document);

        List<DocumentResponse> responses = ingestionService.batchUploadDocuments(
                "kb_001",
                new MultipartFile[]{failedFile, successfulFile},
                null,
                null
        );

        assertThat(responses).extracting(DocumentResponse::getDocId).containsExactly("doc_002");
        assertThat(transactionManager.commits).isEqualTo(1);
        assertThat(transactionManager.rollbacks).isEqualTo(1);
        verify(processingService).processDocumentAsync("doc_002");
    }

    @Test
    void reprocessDocumentShouldResetTaskThenTriggerProcessing() {
        Document document = document("doc_003");
        when(documentService.resetDocumentForReprocess("doc_003")).thenReturn(document);

        ingestionService.reprocessDocument("doc_003");

        assertThat(transactionManager.commits).isEqualTo(1);
        verify(taskService).createPendingTask(document, "文档已提交重新处理");
        verify(processingService).processDocumentAsync("doc_003");
    }

    @Test
    void listDocumentTasksShouldDelegateToTaskService() {
        DocumentProcessingTaskResponse response = new DocumentProcessingTaskResponse();
        response.setTaskId("task_001");
        when(taskService.listByDocument("doc_001")).thenReturn(List.of(response));

        assertThat(ingestionService.listDocumentTasks("doc_001"))
                .extracting(DocumentProcessingTaskResponse::getTaskId)
                .containsExactly("task_001");
    }

    private Document document(String docId) {
        Document document = new Document();
        document.setDocId(docId);
        document.setKbId("kb_001");
        document.setFileId("file_001");
        document.setDocName("RAG guide.md");
        document.setDocType("md");
        document.setDocSize(128L);
        document.setIsEnabled(true);
        return document;
    }

    private static class RecordingTransactionManager implements PlatformTransactionManager {
        private int commits;
        private int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits += 1;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks += 1;
        }
    }
}
