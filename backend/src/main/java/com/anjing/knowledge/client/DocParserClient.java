package com.anjing.knowledge.client;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务客户端
 * 
 * 调用Python doc-parser服务进行文档解析
 */
@Slf4j
@Component
public class DocParserClient {

    private static final String DOC_PARSER_SERVICE_ID = "agent-doc-parser";
    private static final String ASYNC_PARSE_PATH = "/loader/deep_parse/async";
    private static final String ASYNC_STATUS_PATH = "/loader/status";

    @Value("${app.doc-parser.base-url:http://localhost:9001}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final RemoteHttpClient remoteHttpClient;
    private final ObjectMapper objectMapper;

    public DocParserClient(RestTemplate restTemplate, RemoteHttpClient remoteHttpClient, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.remoteHttpClient = remoteHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析文档
     *
     * @param filePath 文件路径
     * @param docType  文档类型配置 (DOCUMENT_BASIC, DOCUMENT_ADVANCED, PLAIN_TEXT, STANDARD_WORKBOOK, TRANSPOSED_WORKBOOK)
     * @return 解析结果
     */
    public ParseResult parseDocument(String filePath, String docType) {
        try {
            String url = baseUrl + "/parse";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(new File(filePath)));
            body.add("doc_type", docType != null ? docType : "DOCUMENT_BASIC");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            } else {
                log.error("解析文档失败: status={}", response.getStatusCode());
                return ParseResult.error("解析文档失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用解析服务异常", e);
            return ParseResult.error("调用解析服务异常: " + e.getMessage());
        }
    }

    /**
     * 解析文档（通过URL）
     *
     * @param fileUrl 文件URL
     * @param docType 文档类型配置
     * @return 解析结果
     */
    public ParseResult parseDocumentByUrl(String fileUrl, String docType) {
        try {
            String url = baseUrl + "/parse_url";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "file_url", fileUrl,
                    "doc_type", docType != null ? docType : "DOCUMENT_BASIC"
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseResponse(response.getBody());
            } else {
                log.error("解析文档失败: status={}", response.getStatusCode());
                return ParseResult.error("解析文档失败: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用解析服务异常", e);
            return ParseResult.error("调用解析服务异常: " + e.getMessage());
        }
    }

    /**
     * 提交异步深度解析任务（文件上传）。
     */
    public AsyncParseTask submitAsyncParseDocument(String filePath, String docType, AsyncParseMetadata metadata) {
        try {
            String url = baseUrl + ASYNC_PARSE_PATH;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(new File(filePath)));
            body.add("doc_type", docType != null ? docType : "DOCUMENT_BASIC");
            if (metadata != null) {
                body.add("metadata", objectMapper.writeValueAsString(metadata));
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseAsyncTaskResponse(response.getBody());
            }
            return AsyncParseTask.error("提交异步解析任务失败: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("提交异步解析任务异常", e);
            return AsyncParseTask.error("提交异步解析任务异常: " + e.getMessage());
        }
    }

    /**
     * 提交异步深度解析任务（URL）。
     */
    public AsyncParseTask submitAsyncParseDocumentByUrl(String fileUrl, String docType, AsyncParseMetadata metadata) {
        try {
            Map<String, Object> body = Map.of(
                    "file_url", fileUrl,
                    "doc_type", docType != null ? docType : "DOCUMENT_BASIC",
                    "metadata", metadata != null ? metadata : new AsyncParseMetadata()
            );

            String response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .serviceId(DOC_PARSER_SERVICE_ID)
                            .path(ASYNC_PARSE_PATH)
                            .body(body)
                            .checkResponse(false)
                            .build(),
                    String.class
            );

            if (response != null) {
                return parseAsyncTaskResponse(response);
            }
            return AsyncParseTask.error("提交异步解析任务失败: doc-parser 响应为空");
        } catch (Exception e) {
            log.error("提交异步解析任务异常", e);
            return AsyncParseTask.error("提交异步解析任务异常: " + e.getMessage());
        }
    }

    /**
     * 查询异步深度解析任务状态。
     */
    public AsyncParseStatus getAsyncParseStatus(String taskId) {
        try {
            String response = remoteHttpClient.exchange(
                    RemoteHttpRequest.builder()
                            .method(HttpMethod.POST)
                            .serviceId(DOC_PARSER_SERVICE_ID)
                            .path(ASYNC_STATUS_PATH)
                            .body(Map.of("task_id", taskId))
                            .checkResponse(false)
                            .build(),
                    String.class
            );

            if (response != null) {
                return parseAsyncStatusResponse(response);
            }
            return AsyncParseStatus.error(taskId, "查询异步解析状态失败: doc-parser 响应为空");
        } catch (Exception e) {
            log.error("查询异步解析状态异常", e);
            return AsyncParseStatus.error(taskId, "查询异步解析状态异常: " + e.getMessage());
        }
    }

    /**
     * 检查服务健康状态
     */
    public boolean isHealthy() {
        try {
            String url = baseUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("解析服务健康检查失败", e);
            return false;
        }
    }

    /**
     * 解析响应
     */
    private ParseResult parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("success") && !root.get("success").asBoolean()) {
                String message = root.has("error")
                        ? root.get("error").asText()
                        : root.path("message").asText("doc-parser 返回失败");
                return ParseResult.error(message);
            }
            
            ParseResult result = new ParseResult();
            result.setSuccess(true);
            
            if (root.has("content")) {
                result.setContent(root.get("content").asText());
            }
            
            if (root.has("chunks")) {
                JsonNode chunksNode = root.get("chunks");
                if (chunksNode.isArray()) {
                    List<ChunkData> chunks = objectMapper.convertValue(
                            chunksNode,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, ChunkData.class)
                    );
                    result.setChunks(chunks);
                }
            }
            
            if (root.has("metadata")) {
                result.setMetadata(objectMapper.convertValue(root.get("metadata"), Map.class));
            }
            
            return result;
        } catch (Exception e) {
            log.error("解析响应失败", e);
            return ParseResult.error("解析响应失败: " + e.getMessage());
        }
    }

    private AsyncParseTask parseAsyncTaskResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("success") && !root.get("success").asBoolean()) {
                return AsyncParseTask.error(extractErrorMessage(root));
            }

            AsyncParseTask task = new AsyncParseTask();
            task.setSuccess(true);
            task.setTaskId(root.path("task_id").asText(null));
            task.setStatus(root.path("status").asText(null));
            task.setMessage(root.path("message").asText(null));
            return task;
        } catch (Exception e) {
            log.error("解析异步任务响应失败", e);
            return AsyncParseTask.error("解析异步任务响应失败: " + e.getMessage());
        }
    }

    private AsyncParseStatus parseAsyncStatusResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String taskId = root.path("task_id").asText(null);
            if (root.has("success") && !root.get("success").asBoolean()) {
                return AsyncParseStatus.error(taskId, extractErrorMessage(root));
            }

            AsyncParseStatus status = new AsyncParseStatus();
            status.setSuccess(true);
            status.setTaskId(taskId);
            status.setStatus(root.path("status").asText(null));
            status.setProgress(root.path("progress").asDouble(0.0));
            status.setMessage(root.path("message").asText(null));
            status.setError(root.path("error").asText(null));
            if (root.has("result") && !root.get("result").isNull()) {
                status.setResult(parseResponse(root.get("result").toString()));
            }
            return status;
        } catch (Exception e) {
            log.error("解析异步状态响应失败", e);
            return AsyncParseStatus.error(null, "解析异步状态响应失败: " + e.getMessage());
        }
    }

    private String extractErrorMessage(JsonNode root) {
        if (root.hasNonNull("error")) {
            return root.get("error").asText();
        }
        return root.path("message").asText("doc-parser 返回失败");
    }

    /**
     * 解析结果
     */
    @Data
    public static class ParseResult {
        private boolean success;
        private String content;
        private List<ChunkData> chunks;
        private Map<String, Object> metadata;
        private String errorMessage;

        public static ParseResult error(String message) {
            ParseResult result = new ParseResult();
            result.setSuccess(false);
            result.setErrorMessage(message);
            return result;
        }
    }

    /**
     * 分片数据
     */
    @Data
    public static class ChunkData {
        private String content;
        private int index;
        private int length;
        private int tokenCount;
        private Map<String, Object> metadata;
    }

    /**
     * 异步解析任务关联信息。
     */
    @Data
    public static class AsyncParseMetadata {
        private Long docId;
        private Long kbId;
        private String requestId;
    }

    /**
     * 异步解析任务提交结果。
     */
    @Data
    public static class AsyncParseTask {
        private boolean success;
        private String taskId;
        private String status;
        private String message;
        private String errorMessage;

        public static AsyncParseTask error(String message) {
            AsyncParseTask task = new AsyncParseTask();
            task.setSuccess(false);
            task.setErrorMessage(message);
            return task;
        }
    }

    /**
     * 异步解析任务状态。
     */
    @Data
    public static class AsyncParseStatus {
        private boolean success;
        private String taskId;
        private String status;
        private double progress;
        private String message;
        private String error;
        private ParseResult result;
        private String errorMessage;

        public static AsyncParseStatus error(String taskId, String message) {
            AsyncParseStatus status = new AsyncParseStatus();
            status.setSuccess(false);
            status.setTaskId(taskId);
            status.setErrorMessage(message);
            return status;
        }
    }
}
