package com.anjing.knowledge.service;

import com.anjing.config.properties.DocParserProperties;
import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.model.entity.FileStorage;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.model.response.DocumentResponse;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import com.anjing.knowledge.repository.FileStorageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentSubmitOnlyRecoveryFlowTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentProcessingTaskRepository taskRepository = mock(DocumentProcessingTaskRepository.class);
    private final DocumentProcessingTaskService taskService = new DocumentProcessingTaskService(taskRepository);
    private final DocParserStatusMapper statusMapper = new DocParserStatusMapper();
    private final DocumentProcessingProgressService progressService = new DocumentProcessingProgressService(
            documentService,
            taskService,
            statusMapper
    );
    private final FileStorageRepository fileStorageRepository = mock(FileStorageRepository.class);
    private final DocParserClient docParserClient = mock(DocParserClient.class);
    private final DocParserProperties docParserProperties = new DocParserProperties();
    private final DocumentParseResultMapper parseResultMapper = new DocumentParseResultMapper();
    private final DocumentAsyncParsingService asyncParsingService = new DocumentAsyncParsingService(
            docParserClient,
            progressService,
            docParserProperties,
            parseResultMapper
    );
    private final DocumentParsingService parsingService = new DocumentParsingService(
            docParserClient,
            fileStorageRepository,
            asyncParsingService,
            docParserProperties,
            parseResultMapper
    );
    private final DocumentChunkingService chunkingService = new DocumentChunkingService(new ObjectMapper());
    private final DocumentChunkPersistenceService chunkPersistenceService = mock(DocumentChunkPersistenceService.class);
    private final DocumentEmbeddingService embeddingService = mock(DocumentEmbeddingService.class);
    private final DocumentProcessingContextService contextService = mock(DocumentProcessingContextService.class);
    private final DocumentProcessingService processingService = new DocumentProcessingService(
            contextService,
            progressService,
            parsingService,
            chunkingService,
            chunkPersistenceService,
            embeddingService
    );
    private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    private final DocumentIngestionService ingestionService = new DocumentIngestionService(
            documentService,
            taskService,
            processingService,
            transactionManager
    );
    private final DocumentParserRecoveryPollingService recoveryPollingService = new DocumentParserRecoveryPollingService(
            taskRepository,
            docParserClient,
            progressService,
            processingService,
            parseResultMapper,
            docParserProperties
    );
    private final List<DocumentProcessingTask> taskStore = new ArrayList<>();

    private Document document;
    private KnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() throws Exception {
        docParserProperties.setMode("async");
        docParserProperties.getAsync().setSubmitOnlyEnabled(true);
        docParserProperties.getAsync().setRecoveryEnabled(true);
        docParserProperties.getAsync().setRecoveryBatchSize(10);

        document = document();
        knowledgeBase = knowledgeBase();
        configureTaskRepository();

        MultipartFile file = mock(MultipartFile.class);
        when(documentService.createUploadedDocument("kb_001", file, "parser", "chunk")).thenReturn(document);
        when(contextService.loadContext("doc_001"))
                .thenReturn(new DocumentProcessingContextService.DocumentProcessingContext(document, knowledgeBase));
        when(fileStorageRepository.findById("file_001")).thenReturn(Optional.of(fileStorage("/tmp/rag.pdf")));
        when(docParserClient.isHealthy()).thenReturn(true);
        when(docParserClient.submitAsyncParseDocument(
                eq("/tmp/rag.pdf"),
                eq("DOCUMENT_BASIC"),
                any(DocParserClient.AsyncParseMetadata.class)
        )).thenReturn(submittedTask());
        when(chunkPersistenceService.saveChunks(eq(document), anyList()))
                .thenReturn(new DocumentChunkPersistenceService.PersistedChunks(1, 6));
        when(embeddingService.embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small")))
                .thenReturn(true);

        uploadFile = file;
    }

    private MultipartFile uploadFile;

    @Test
    void uploadShouldSubmitParserTaskAndRecoveryShouldCompletePipeline() throws Exception {
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(succeededStatus());

        DocumentResponse response = ingestionService.uploadDocument("kb_001", uploadFile, "parser", "chunk");

        assertThat(response.getDocId()).isEqualTo("doc_001");
        assertThat(transactionManager.commits).isEqualTo(1);
        DocumentProcessingTask submitted = latestTask();
        assertThat(submitted.getParserTaskId()).isEqualTo("parser_task_001");
        assertThat(submitted.getParserStatus()).isEqualTo("PENDING");
        assertThat(submitted.getStatus()).isEqualTo("PENDING");
        assertThat(submitted.getPhase()).isEqualTo("PARSING");
        verify(docParserClient, never()).getAsyncParseStatus("parser_task_001");

        int polledCount = recoveryPollingService.pollRecoverableTasksOnce();

        assertThat(polledCount).isEqualTo(1);
        DocumentProcessingTask completed = latestTask();
        assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(completed.getPhase()).isEqualTo("COMPLETED");
        assertThat(completed.getProgress()).isEqualTo(1.0f);
        verify(embeddingService).embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small"));
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.COMPLETED, 1.0f, "处理完成");
    }

    private void configureTaskRepository() {
        when(taskRepository.save(any(DocumentProcessingTask.class))).thenAnswer(invocation -> {
            DocumentProcessingTask task = invocation.getArgument(0);
            taskStore.removeIf(existing -> Objects.equals(existing.getTaskId(), task.getTaskId()));
            taskStore.add(0, task);
            return task;
        });
        when(taskRepository.findFirstByDocIdOrderByCreatedAtDesc(anyString())).thenAnswer(invocation -> {
            String docId = invocation.getArgument(0);
            return taskStore.stream()
                    .filter(task -> docId.equals(task.getDocId()))
                    .findFirst();
        });
        when(taskRepository.findRecoverableParserTasks(anyCollection(), eq("PARSING"), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Collection<String> statuses = invocation.getArgument(0);
                    Pageable pageable = invocation.getArgument(2);
                    return taskStore.stream()
                            .filter(task -> task.getParserTaskId() != null)
                            .filter(task -> "PARSING".equals(task.getPhase()))
                            .filter(task -> statuses.contains(task.getStatus()))
                            .limit(pageable.getPageSize())
                            .toList();
                });
    }

    private DocumentProcessingTask latestTask() {
        return taskStore.get(0);
    }

    private Document document() {
        Document document = new Document();
        document.setDocId("doc_001");
        document.setKbId("kb_001");
        document.setFileId("file_001");
        document.setDocName("RAG guide.pdf");
        document.setDocType("pdf");
        document.setDocSize(1024L);
        document.setIsEnabled(true);
        return document;
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb_001");
        knowledgeBase.setName("RAG 教学库");
        knowledgeBase.setChunkSize(500);
        knowledgeBase.setChunkOverlap(50);
        knowledgeBase.setEmbeddingModel("text-embedding-3-small");
        return knowledgeBase;
    }

    private FileStorage fileStorage(String path) {
        FileStorage fileStorage = new FileStorage();
        fileStorage.setStoragePath(path);
        return fileStorage;
    }

    private DocParserClient.AsyncParseTask submittedTask() {
        DocParserClient.AsyncParseTask task = new DocParserClient.AsyncParseTask();
        task.setSuccess(true);
        task.setTaskId("parser_task_001");
        task.setStatus("PENDING");
        task.setMessage("task accepted");
        return task;
    }

    private DocParserClient.AsyncParseStatus succeededStatus() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);
        DocParserClient.ChunkData chunk = new DocParserClient.ChunkData();
        chunk.setContent("脚手架生长出 RAG agent");
        chunk.setIndex(0);
        chunk.setTokenCount(6);
        parseResult.setChunks(List.of(chunk));

        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId("parser_task_001");
        status.setStatus("SUCCEEDED");
        status.setProgress(1.0);
        status.setResult(parseResult);
        return status;
    }

    private static class RecordingTransactionManager implements PlatformTransactionManager {
        private int commits;

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
        }
    }
}
