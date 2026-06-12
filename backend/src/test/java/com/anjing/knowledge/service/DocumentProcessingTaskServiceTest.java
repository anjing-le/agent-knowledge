package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.model.response.DocumentProcessingTaskResponse;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingTaskServiceTest {

    @Mock
    private DocumentProcessingTaskRepository taskRepository;

    @InjectMocks
    private DocumentProcessingTaskService taskService;

    @Test
    void createPendingTaskShouldInitializeVisibleIngestionState() {
        Document document = new Document();
        document.setDocId("doc_001");
        document.setKbId("kb_001");

        when(taskRepository.save(any(DocumentProcessingTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingTask task = taskService.createPendingTask(document, "文档已上传，等待处理");

        assertThat(task.getTaskId()).startsWith("task_");
        assertThat(task.getDocId()).isEqualTo("doc_001");
        assertThat(task.getKbId()).isEqualTo("kb_001");
        assertThat(task.getTaskType()).isEqualTo("INGESTION");
        assertThat(task.getPhase()).isEqualTo("PENDING");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getProgress()).isEqualTo(0.0f);
        assertThat(task.getMessage()).isEqualTo("文档已上传，等待处理");
    }

    @Test
    void markRunningAndSucceededShouldAdvanceLatestTask() {
        DocumentProcessingTask latestTask = new DocumentProcessingTask();
        latestTask.setTaskId("task_001");
        latestTask.setDocId("doc_001");
        latestTask.setKbId("kb_001");
        latestTask.setStatus("PENDING");
        latestTask.setPhase("PENDING");
        latestTask.setProgress(0.0f);

        when(taskRepository.findFirstByDocIdOrderByCreatedAtDesc("doc_001"))
                .thenReturn(Optional.of(latestTask));
        when(taskRepository.save(any(DocumentProcessingTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingTask running =
                taskService.markRunning("doc_001", "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");

        assertThat(running.getStatus()).isEqualTo("RUNNING");
        assertThat(running.getPhase()).isEqualTo("EMBEDDING");
        assertThat(running.getProgress()).isEqualTo(0.6f);
        assertThat(running.getStartedAt()).isNotNull();

        DocumentProcessingTask succeeded = taskService.markSucceeded("doc_001", "文档处理完成");

        assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.getPhase()).isEqualTo("COMPLETED");
        assertThat(succeeded.getProgress()).isEqualTo(1.0f);
        assertThat(succeeded.getCompletedAt()).isNotNull();
        assertThat(succeeded.getMessage()).isEqualTo("文档处理完成");
    }

    @Test
    void markFailedShouldExposeFailedPhaseAndErrorMessage() {
        DocumentProcessingTask latestTask = new DocumentProcessingTask();
        latestTask.setTaskId("task_001");
        latestTask.setDocId("doc_001");
        latestTask.setKbId("kb_001");

        when(taskRepository.findFirstByDocIdOrderByCreatedAtDesc("doc_001"))
                .thenReturn(Optional.of(latestTask));
        when(taskRepository.save(any(DocumentProcessingTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProcessingTask failed = taskService.markFailed("doc_001", "PARSING", "doc-parser 服务不可用");

        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getPhase()).isEqualTo("PARSING");
        assertThat(failed.getMessage()).isEqualTo("处理失败");
        assertThat(failed.getErrorMessage()).isEqualTo("doc-parser 服务不可用");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    void listByDocumentShouldMapTasksForFrontendTimeline() {
        DocumentProcessingTask task = new DocumentProcessingTask();
        task.setTaskId("task_001");
        task.setDocId("doc_001");
        task.setKbId("kb_001");
        task.setTaskType("INGESTION");
        task.setPhase("EMBEDDING");
        task.setStatus("RUNNING");
        task.setProgress(0.6f);
        task.setMessage("正在生成 Embedding 并写入向量库");

        when(taskRepository.findByDocIdOrderByCreatedAtDesc("doc_001"))
                .thenReturn(List.of(task));

        List<DocumentProcessingTaskResponse> responses = taskService.listByDocument("doc_001");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTaskId()).isEqualTo("task_001");
        assertThat(responses.get(0).getPhase()).isEqualTo("EMBEDDING");
        assertThat(responses.get(0).getStatus()).isEqualTo("RUNNING");
        assertThat(responses.get(0).getProgress()).isEqualTo(0.6f);
    }

    @Test
    void markRunningShouldFailWhenDocumentHasNoTask() {
        when(taskRepository.findFirstByDocIdOrderByCreatedAtDesc("doc_missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.markRunning("doc_missing", "PARSING", 0.1f, "开始解析"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("文档处理任务不存在");
    }
}
