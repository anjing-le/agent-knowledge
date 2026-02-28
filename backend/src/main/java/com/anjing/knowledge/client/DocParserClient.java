package com.anjing.knowledge.client;

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

    @Value("${app.doc-parser.base-url:http://localhost:9001}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DocParserClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
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
}

