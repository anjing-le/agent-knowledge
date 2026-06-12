package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.model.response.DocumentProcessingTaskResponse;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks visible ingestion attempts for documents.
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessingTaskService {

    private static final AtomicInteger TASK_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DocumentProcessingTaskRepository taskRepository;

    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessingTask createPendingTask(Document document, String message) {
        DocumentProcessingTask task = new DocumentProcessingTask();
        task.setTaskId(generateTaskId());
        task.setDocId(document.getDocId());
        task.setKbId(document.getKbId());
        task.setTaskType("INGESTION");
        task.setPhase("PENDING");
        task.setStatus("PENDING");
        task.setProgress(0.0f);
        task.setMessage(message);
        return taskRepository.save(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessingTask ensureLatestTask(Document document, String message) {
        return taskRepository.findFirstByDocIdOrderByCreatedAtDesc(document.getDocId())
                .orElseGet(() -> createPendingTask(document, message));
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessingTask markRunning(String docId, String phase, Float progress, String message) {
        DocumentProcessingTask task = latestTask(docId);
        task.setStatus("RUNNING");
        task.setPhase(phase);
        task.setProgress(progress);
        task.setMessage(message);
        if (task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessingTask markSucceeded(String docId, String message) {
        DocumentProcessingTask task = latestTask(docId);
        task.setStatus("SUCCEEDED");
        task.setPhase("COMPLETED");
        task.setProgress(1.0f);
        task.setMessage(message);
        task.setCompletedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocumentProcessingTask markFailed(String docId, String phase, String errorMessage) {
        DocumentProcessingTask task = latestTask(docId);
        task.setStatus("FAILED");
        task.setPhase(phase);
        task.setErrorMessage(errorMessage);
        task.setMessage("处理失败");
        task.setCompletedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<DocumentProcessingTaskResponse> listByDocument(String docId) {
        return taskRepository.findByDocIdOrderByCreatedAtDesc(docId).stream()
                .map(DocumentProcessingTaskResponse::fromEntity)
                .toList();
    }

    private DocumentProcessingTask latestTask(String docId) {
        return taskRepository.findFirstByDocIdOrderByCreatedAtDesc(docId)
                .orElseThrow(() -> new IllegalStateException("文档处理任务不存在: " + docId));
    }

    private String generateTaskId() {
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        int counter = TASK_COUNTER.incrementAndGet();
        return String.format("task_%s_%04d", dateStr, counter);
    }
}
