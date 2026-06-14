package com.anjing.knowledge.service;

import com.anjing.config.properties.DocParserProperties;
import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.DocumentParseResult;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Recovers async doc-parser tasks after process restarts or transient worker failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserRecoveryPollingService {

    private static final List<String> RECOVERABLE_STATUSES = List.of("PENDING", "RUNNING");
    private static final String RECOVERABLE_PHASE = "PARSING";

    private final DocumentProcessingTaskRepository taskRepository;
    private final DocParserClient docParserClient;
    private final DocumentProcessingProgressService progressService;
    private final DocumentProcessingService processingService;
    private final DocumentParseResultMapper parseResultMapper;
    private final DocParserProperties docParserProperties;

    @Scheduled(fixedDelayString = "${app.doc-parser.async.recovery-fixed-delay-ms:15000}")
    public void pollRecoverableTasks() {
        int polledCount = pollRecoverableTasksOnce();
        if (polledCount > 0) {
            log.info("doc-parser 恢复轮询完成: taskCount={}", polledCount);
        }
    }

    public int pollRecoverableTasksOnce() {
        if (!isRecoveryEnabled()) {
            return 0;
        }

        int batchSize = Math.max(1, docParserProperties.getAsync().getRecoveryBatchSize());
        List<DocumentProcessingTask> tasks = taskRepository.findRecoverableParserTasks(
                RECOVERABLE_STATUSES,
                RECOVERABLE_PHASE,
                PageRequest.of(0, batchSize)
        );

        tasks.forEach(this::pollTask);
        return tasks.size();
    }

    private boolean isRecoveryEnabled() {
        return docParserProperties.isAsyncMode() && docParserProperties.getAsync().isRecoveryEnabled();
    }

    private void pollTask(DocumentProcessingTask task) {
        String parserTaskId = task.getParserTaskId();
        DocParserClient.AsyncParseStatus status = docParserClient.getAsyncParseStatus(parserTaskId);
        if (status == null) {
            progressService.applyDocParserStatus(task.getDocId(), null);
            return;
        }

        try {
            progressService.applyDocParserStatus(task.getDocId(), status);
        } catch (IllegalArgumentException e) {
            progressService.markParsingFailed(task.getDocId(), e.getMessage());
            return;
        }

        if (!status.isSuccess()) {
            return;
        }

        String normalizedStatus = normalize(status.getStatus());
        if ("SUCCEEDED".equals(normalizedStatus)) {
            continueAfterSucceeded(task.getDocId(), status);
        }
    }

    private void continueAfterSucceeded(String docId, DocParserClient.AsyncParseStatus status) {
        if (status.getResult() == null) {
            progressService.markParsingFailed(docId, "doc-parser 异步解析完成但结果为空");
            return;
        }

        DocumentParseResult parseResult = parseResultMapper.fromClientResult(status.getResult());
        processingService.continueAfterParsing(docId, parseResult);
    }

    private String normalize(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }
}
