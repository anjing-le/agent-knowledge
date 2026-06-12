package com.anjing.knowledge.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocParserClientTest {

    private static final String BASE_URL = "http://doc-parser.test";

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final DocParserClient client = new DocParserClient(restTemplate, new ObjectMapper());

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
    }

    @Test
    void isHealthyShouldReturnTrueWhenHealthEndpointIsSuccessful() {
        when(restTemplate.getForEntity(BASE_URL + "/health", String.class))
                .thenReturn(ResponseEntity.ok("{\"status\":\"ok\"}"));

        assertThat(client.isHealthy()).isTrue();
    }

    @Test
    void isHealthyShouldReturnFalseWhenHealthEndpointFails() {
        when(restTemplate.getForEntity(BASE_URL + "/health", String.class))
                .thenThrow(new IllegalStateException("connection refused"));

        assertThat(client.isHealthy()).isFalse();
    }

    @Test
    void parseDocumentShouldMapContentChunksAndMetadata() throws Exception {
        Path sampleFile = tempDir.resolve("sample.md");
        Files.writeString(sampleFile, "# RAG");
        String responseBody = """
                {
                  "success": true,
                  "content": "解析正文",
                  "chunks": [
                    {
                      "content": "第一段",
                      "index": 0,
                      "length": 3,
                      "tokenCount": 2,
                      "metadata": {
                        "page_idx": [1],
                        "content_type": "TEXT"
                      }
                    }
                  ],
                  "metadata": {
                    "parser": "doc-parser",
                    "pages": 1
                  }
                }
                """;
        when(restTemplate.exchange(
                eq(BASE_URL + "/parse"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseBody));

        DocParserClient.ParseResult result = client.parseDocument(sampleFile.toString(), "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("解析正文");
        assertThat(result.getMetadata())
                .containsEntry("parser", "doc-parser")
                .containsEntry("pages", 1);
        assertThat(result.getChunks()).hasSize(1);
        DocParserClient.ChunkData chunk = result.getChunks().get(0);
        assertThat(chunk.getContent()).isEqualTo("第一段");
        assertThat(chunk.getIndex()).isEqualTo(0);
        assertThat(chunk.getLength()).isEqualTo(3);
        assertThat(chunk.getTokenCount()).isEqualTo(2);
        assertThat(chunk.getMetadata())
                .containsEntry("page_idx", List.of(1))
                .containsEntry("content_type", "TEXT");
    }

    @Test
    void parseDocumentShouldReturnErrorWhenDocParserReturnsFailure() throws Exception {
        Path sampleFile = tempDir.resolve("sample.md");
        Files.writeString(sampleFile, "# RAG");
        when(restTemplate.exchange(
                eq(BASE_URL + "/parse"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "success": false,
                  "error": "unsupported file type"
                }
                """));

        DocParserClient.ParseResult result = client.parseDocument(sampleFile.toString(), "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("unsupported file type");
    }

    @Test
    void parseDocumentByUrlShouldUseJsonContractAndMapResult() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/parse_url"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "success": true,
                  "content": "URL 文档正文",
                  "metadata": {"source": "url"}
                }
                """));

        DocParserClient.ParseResult result =
                client.parseDocumentByUrl("https://example.com/a.pdf", "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("URL 文档正文");
        assertThat(result.getMetadata()).containsEntry("source", "url");
    }

    @Test
    void parseDocumentShouldReturnErrorWhenHttpStatusIsNotSuccessful() throws Exception {
        Path sampleFile = tempDir.resolve("sample.md");
        Files.writeString(sampleFile, "# RAG");
        when(restTemplate.exchange(
                eq(BASE_URL + "/parse"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("bad gateway"));

        DocParserClient.ParseResult result = client.parseDocument(sampleFile.toString(), "DOCUMENT_BASIC");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("502 BAD_GATEWAY");
    }

    @Test
    void submitAsyncParseDocumentShouldUseMultipartContractAndMapTask() throws Exception {
        Path sampleFile = tempDir.resolve("sample.pdf");
        Files.writeString(sampleFile, "PDF");
        DocParserClient.AsyncParseMetadata metadata = new DocParserClient.AsyncParseMetadata();
        metadata.setDocId(101L);
        metadata.setKbId(202L);
        metadata.setRequestId("req-1");
        ArgumentCaptor<HttpEntity> entityCaptor = forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq(BASE_URL + "/loader/deep_parse/async"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "success": true,
                  "task_id": "task-123",
                  "status": "PENDING",
                  "message": "task accepted"
                }
                """));

        DocParserClient.AsyncParseTask task =
                client.submitAsyncParseDocument(sampleFile.toString(), "DOCUMENT_ADVANCED", metadata);

        assertThat(task.isSuccess()).isTrue();
        assertThat(task.getTaskId()).isEqualTo("task-123");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getMessage()).isEqualTo("task accepted");
        assertThat(entityCaptor.getValue().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) entityCaptor.getValue().getBody();
        assertThat(body.getFirst("doc_type")).isEqualTo("DOCUMENT_ADVANCED");
        assertThat((String) body.getFirst("metadata"))
                .contains("\"docId\":101")
                .contains("\"kbId\":202")
                .contains("\"requestId\":\"req-1\"");
    }

    @Test
    void submitAsyncParseDocumentByUrlShouldUseJsonContractAndMapFailure() {
        ArgumentCaptor<HttpEntity> entityCaptor = forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq(BASE_URL + "/loader/deep_parse/async"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "success": false,
                  "error": "file_url is unreachable"
                }
                """));

        DocParserClient.AsyncParseTask task =
                client.submitAsyncParseDocumentByUrl("https://example.com/a.pdf", null, null);

        assertThat(task.isSuccess()).isFalse();
        assertThat(task.getErrorMessage()).isEqualTo("file_url is unreachable");
        assertThat(entityCaptor.getValue().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        Map<String, Object> body = (Map<String, Object>) entityCaptor.getValue().getBody();
        assertThat(body)
                .containsEntry("file_url", "https://example.com/a.pdf")
                .containsEntry("doc_type", "DOCUMENT_BASIC");
    }

    @Test
    void getAsyncParseStatusShouldMapProgressAndParseResult() {
        when(restTemplate.exchange(
                eq(BASE_URL + "/loader/status"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("""
                {
                  "success": true,
                  "task_id": "task-123",
                  "status": "SUCCEEDED",
                  "progress": 1.0,
                  "message": "done",
                  "result": {
                    "success": true,
                    "content": "异步解析正文",
                    "chunks": [
                      {
                        "content": "异步分片",
                        "index": 0,
                        "length": 4,
                        "tokenCount": 2,
                        "metadata": {"page_idx": [1]}
                      }
                    ],
                    "metadata": {"parser_id": "general"}
                  }
                }
                """));

        DocParserClient.AsyncParseStatus status = client.getAsyncParseStatus("task-123");

        assertThat(status.isSuccess()).isTrue();
        assertThat(status.getTaskId()).isEqualTo("task-123");
        assertThat(status.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(status.getProgress()).isEqualTo(1.0);
        assertThat(status.getMessage()).isEqualTo("done");
        assertThat(status.getResult().isSuccess()).isTrue();
        assertThat(status.getResult().getContent()).isEqualTo("异步解析正文");
        assertThat(status.getResult().getChunks()).hasSize(1);
        assertThat(status.getResult().getMetadata()).containsEntry("parser_id", "general");
    }
}
