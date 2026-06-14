package com.anjing.knowledge.service;

import com.anjing.config.properties.DocParserProperties;
import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.DocumentParseResult;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentParserRecoveryPollingServiceTest {

    private final DocumentProcessingTaskRepository taskRepository = mock(DocumentProcessingTaskRepository.class);
    private final DocParserClient docParserClient = mock(DocParserClient.class);
    private final DocumentProcessingProgressService progressService = mock(DocumentProcessingProgressService.class);
    private final DocumentProcessingService processingService = mock(DocumentProcessingService.class);
    private final DocumentParseResultMapper parseResultMapper = mock(DocumentParseResultMapper.class);
    private final DocParserProperties properties = new DocParserProperties();
    private final DocumentParserRecoveryPollingService pollingService = new DocumentParserRecoveryPollingService(
            taskRepository,
            docParserClient,
            progressService,
            processingService,
            parseResultMapper,
            properties
    );

    @BeforeEach
    void setUp() {
        properties.setMode("async");
        properties.getAsync().setRecoveryEnabled(true);
        properties.getAsync().setRecoveryBatchSize(5);
    }

    @Test
    void pollRecoverableTasksOnceShouldSkipWhenRecoveryIsDisabled() {
        properties.getAsync().setRecoveryEnabled(false);

        int count = pollingService.pollRecoverableTasksOnce();

        assertThat(count).isZero();
        verifyNoInteractions(taskRepository, docParserClient, progressService, processingService);
    }

    @Test
    void pollRecoverableTasksOnceShouldContinuePipelineWhenParserSucceeded() {
        DocumentProcessingTask task = task();
        DocParserClient.ParseResult clientResult = new DocParserClient.ParseResult();
        clientResult.setSuccess(true);
        clientResult.setContent("parsed content");
        DocParserClient.AsyncParseStatus status = status("SUCCEEDED");
        status.setResult(clientResult);
        DocumentParseResult parseResult = new DocumentParseResult();
        parseResult.setSuccess(true);
        parseResult.setContent("parsed content");

        when(taskRepository.findRecoverableParserTasks(eq(List.of("PENDING", "RUNNING")), eq("PARSING"), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(status);
        when(parseResultMapper.fromClientResult(clientResult)).thenReturn(parseResult);

        int count = pollingService.pollRecoverableTasksOnce();

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskRepository).findRecoverableParserTasks(eq(List.of("PENDING", "RUNNING")), eq("PARSING"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        verify(progressService).applyDocParserStatus("doc_001", status);
        verify(processingService).continueAfterParsing("doc_001", parseResult);
    }

    @Test
    void pollRecoverableTasksOnceShouldOnlyUpdateStatusWhenParserIsStillRunning() {
        DocumentProcessingTask task = task();
        DocParserClient.AsyncParseStatus status = status("RUNNING");

        when(taskRepository.findRecoverableParserTasks(eq(List.of("PENDING", "RUNNING")), eq("PARSING"), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(status);

        int count = pollingService.pollRecoverableTasksOnce();

        assertThat(count).isEqualTo(1);
        verify(progressService).applyDocParserStatus("doc_001", status);
        verify(processingService, never()).continueAfterParsing(eq("doc_001"), any(DocumentParseResult.class));
    }

    @Test
    void pollRecoverableTasksOnceShouldFailWhenSucceededStatusHasNoResult() {
        DocumentProcessingTask task = task();
        DocParserClient.AsyncParseStatus status = status("SUCCEEDED");

        when(taskRepository.findRecoverableParserTasks(eq(List.of("PENDING", "RUNNING")), eq("PARSING"), any(Pageable.class)))
                .thenReturn(List.of(task));
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(status);

        pollingService.pollRecoverableTasksOnce();

        verify(progressService).applyDocParserStatus("doc_001", status);
        verify(progressService).markParsingFailed("doc_001", "doc-parser 异步解析完成但结果为空");
        verify(processingService, never()).continueAfterParsing(eq("doc_001"), any(DocumentParseResult.class));
    }

    private DocumentProcessingTask task() {
        DocumentProcessingTask task = new DocumentProcessingTask();
        task.setTaskId("task_001");
        task.setDocId("doc_001");
        task.setKbId("kb_001");
        task.setParserTaskId("parser_task_001");
        task.setStatus("RUNNING");
        task.setPhase("PARSING");
        return task;
    }

    private DocParserClient.AsyncParseStatus status(String parserStatus) {
        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId("parser_task_001");
        status.setStatus(parserStatus);
        return status;
    }
}
