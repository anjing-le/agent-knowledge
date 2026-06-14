package com.anjing.knowledge.service;

import com.anjing.context.GlobalRequestContextHolder;
import com.anjing.config.properties.DocParserProperties;
import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.DocumentParseResult;
import com.anjing.knowledge.model.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Polls Python doc-parser async tasks and maps them into the Java ingestion lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAsyncParsingService {

    private final DocParserClient docParserClient;
    private final DocumentProcessingProgressService progressService;
    private final DocParserProperties docParserProperties;
    private final DocumentParseResultMapper parseResultMapper;

    public DocumentParseResult parseDocument(Document document, String filePath, String docType) {
        DocParserClient.AsyncParseTask task = docParserClient.submitAsyncParseDocument(
                filePath,
                docType,
                buildMetadata(document)
        );
        if (task == null) {
            return DocumentParseResult.error("提交异步解析任务失败: doc-parser 响应为空");
        }
        if (!task.isSuccess()) {
            return DocumentParseResult.error(firstText(task.getErrorMessage(), "提交异步解析任务失败"));
        }
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            return DocumentParseResult.error("提交异步解析任务失败: doc-parser task_id 为空");
        }

        progressService.applyDocParserStatus(document.getDocId(), toStatus(task));
        return pollUntilTerminal(document, task.getTaskId());
    }

    private DocumentParseResult pollUntilTerminal(Document document, String parserTaskId) {
        int maxPollAttempts = Math.max(1, docParserProperties.getAsync().getMaxPollAttempts());
        for (int attempt = 1; attempt <= maxPollAttempts; attempt++) {
            if (!waitBeforePoll(attempt)) {
                return DocumentParseResult.error("doc-parser 异步轮询被中断: taskId=" + parserTaskId);
            }

            DocParserClient.AsyncParseStatus status = docParserClient.getAsyncParseStatus(parserTaskId);
            if (status == null) {
                progressService.applyDocParserStatus(document.getDocId(), null);
                return DocumentParseResult.error("查询异步解析状态失败: doc-parser 响应为空");
            }
            try {
                progressService.applyDocParserStatus(document.getDocId(), status);
            } catch (IllegalArgumentException e) {
                return DocumentParseResult.error(e.getMessage());
            }

            if (!status.isSuccess()) {
                return DocumentParseResult.error(firstText(status.getErrorMessage(), "查询异步解析状态失败"));
            }

            String normalizedStatus = normalize(status.getStatus());
            if ("SUCCEEDED".equals(normalizedStatus)) {
                if (status.getResult() == null) {
                    return DocumentParseResult.error("doc-parser 异步解析完成但结果为空");
                }
                return parseResultMapper.fromClientResult(status.getResult());
            }
            if ("FAILED".equals(normalizedStatus) || "CANCELED".equals(normalizedStatus)) {
                return DocumentParseResult.error(firstText(
                        status.getError(),
                        status.getErrorMessage(),
                        status.getMessage(),
                        "doc-parser 异步解析失败"
                ));
            }
        }

        return DocumentParseResult.error("doc-parser 异步解析超时: taskId=" + parserTaskId);
    }

    private DocParserClient.AsyncParseMetadata buildMetadata(Document document) {
        DocParserClient.AsyncParseMetadata metadata = new DocParserClient.AsyncParseMetadata();
        metadata.setDocId(document.getDocId());
        metadata.setKbId(document.getKbId());
        metadata.setRequestId(GlobalRequestContextHolder.requestIdOrNull());
        return metadata;
    }

    private DocParserClient.AsyncParseStatus toStatus(DocParserClient.AsyncParseTask task) {
        DocParserClient.AsyncParseStatus status = new DocParserClient.AsyncParseStatus();
        status.setSuccess(true);
        status.setTaskId(task.getTaskId());
        status.setStatus(firstText(task.getStatus(), "PENDING"));
        status.setMessage(task.getMessage());
        return status;
    }

    private boolean waitBeforePoll(int attempt) {
        long pollIntervalMs = Math.max(0L, docParserProperties.getAsync().getPollIntervalMs());
        if (attempt == 1 || pollIntervalMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(pollIntervalMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("doc-parser 异步轮询被中断");
            return false;
        }
    }

    private String normalize(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
