package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAsyncParsingServiceTest {

    private final DocParserClient docParserClient = mock(DocParserClient.class);
    private final DocumentProcessingProgressService progressService = mock(DocumentProcessingProgressService.class);
    private final DocumentAsyncParsingService parsingService = new DocumentAsyncParsingService(
            docParserClient,
            progressService
    );

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(parsingService, "maxPollAttempts", 3);
        ReflectionTestUtils.setField(parsingService, "pollIntervalMs", 0L);
    }

    @Test
    void parseDocumentShouldSubmitTaskPollUntilSucceededAndReturnResult() {
        Document document = document();
        DocParserClient.AsyncParseTask task = task("parser_task_001", "PENDING");
        DocParserClient.AsyncParseStatus running = status("parser_task_001", "RUNNING", null);
        DocParserClient.ParseResult expectedResult = new DocParserClient.ParseResult();
        expectedResult.setSuccess(true);
        expectedResult.setContent("parsed content");
        DocParserClient.AsyncParseStatus succeeded = status("parser_task_001", "SUCCEEDED", expectedResult);

        when(docParserClient.submitAsyncParseDocument(eq("/tmp/rag.pdf"), eq("DOCUMENT_BASIC"),
                org.mockito.ArgumentMatchers.any(DocParserClient.AsyncParseMetadata.class))).thenReturn(task);
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(running, succeeded);

        DocParserClient.ParseResult result = parsingService.parseDocument(document, "/tmp/rag.pdf", "DOCUMENT_BASIC");

        assertThat(result).isSameAs(expectedResult);
        ArgumentCaptor<DocParserClient.AsyncParseMetadata> metadataCaptor =
                ArgumentCaptor.forClass(DocParserClient.AsyncParseMetadata.class);
        verify(docParserClient).submitAsyncParseDocument(eq("/tmp/rag.pdf"), eq("DOCUMENT_BASIC"),
                metadataCaptor.capture());
        assertThat(metadataCaptor.getValue().getDocId()).isEqualTo("doc_001");
        assertThat(metadataCaptor.getValue().getKbId()).isEqualTo("kb_001");

        ArgumentCaptor<DocParserClient.AsyncParseStatus> statusCaptor =
                ArgumentCaptor.forClass(DocParserClient.AsyncParseStatus.class);
        verify(progressService, times(3)).applyDocParserStatus(eq("doc_001"), statusCaptor.capture());
        assertThat(statusCaptor.getAllValues())
                .extracting(DocParserClient.AsyncParseStatus::getStatus)
                .containsExactly("PENDING", "RUNNING", "SUCCEEDED");
    }

    @Test
    void parseDocumentShouldFailWhenSubmittedTaskHasNoTaskId() {
        when(docParserClient.submitAsyncParseDocument(eq("/tmp/rag.pdf"), eq("DOCUMENT_BASIC"),
                org.mockito.ArgumentMatchers.any(DocParserClient.AsyncParseMetadata.class)))
                .thenReturn(task(null, "PENDING"));

        DocParserClient.ParseResult result = parsingService.parseDocument(document(), "/tmp/rag.pdf", "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("task_id 为空");
        verify(docParserClient, never()).getAsyncParseStatus(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void parseDocumentShouldFailWhenTerminalStatusFailed() {
        when(docParserClient.submitAsyncParseDocument(eq("/tmp/rag.pdf"), eq("DOCUMENT_BASIC"),
                org.mockito.ArgumentMatchers.any(DocParserClient.AsyncParseMetadata.class)))
                .thenReturn(task("parser_task_001", "PENDING"));
        DocParserClient.AsyncParseStatus failed = status("parser_task_001", "FAILED", null);
        failed.setError("OCR failed");
        when(docParserClient.getAsyncParseStatus("parser_task_001")).thenReturn(failed);

        DocParserClient.ParseResult result = parsingService.parseDocument(document(), "/tmp/rag.pdf", "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("OCR failed");
        verify(progressService, times(2)).applyDocParserStatus(
                eq("doc_001"),
                org.mockito.ArgumentMatchers.any(DocParserClient.AsyncParseStatus.class)
        );
    }

    @Test
    void parseDocumentShouldFailWhenPollingTimesOut() {
        ReflectionTestUtils.setField(parsingService, "maxPollAttempts", 2);
        when(docParserClient.submitAsyncParseDocument(eq("/tmp/rag.pdf"), eq("DOCUMENT_BASIC"),
                org.mockito.ArgumentMatchers.any(DocParserClient.AsyncParseMetadata.class)))
                .thenReturn(task("parser_task_001", "PENDING"));
        when(docParserClient.getAsyncParseStatus("parser_task_001"))
                .thenReturn(status("parser_task_001", "RUNNING", null));

        DocParserClient.ParseResult result = parsingService.parseDocument(document(), "/tmp/rag.pdf", "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("异步解析超时");
        verify(docParserClient, times(2)).getAsyncParseStatus("parser_task_001");
    }

    private Document document() {
        Document document = new Document();
        document.setDocId("doc_001");
        document.setKbId("kb_001");
        return document;
    }

    private DocParserClient.AsyncParseTask task(String taskId, String status) {
        DocParserClient.AsyncParseTask task = new DocParserClient.AsyncParseTask();
        task.setSuccess(true);
        task.setTaskId(taskId);
        task.setStatus(status);
        return task;
    }

    private DocParserClient.AsyncParseStatus status(String taskId,
                                                   String status,
                                                   DocParserClient.ParseResult result) {
        DocParserClient.AsyncParseStatus asyncStatus = new DocParserClient.AsyncParseStatus();
        asyncStatus.setSuccess(true);
        asyncStatus.setTaskId(taskId);
        asyncStatus.setStatus(status);
        asyncStatus.setResult(result);
        return asyncStatus;
    }
}
