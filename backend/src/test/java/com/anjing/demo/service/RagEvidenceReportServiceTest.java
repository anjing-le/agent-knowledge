package com.anjing.demo.service;

import com.anjing.demo.model.response.RagEvidenceReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagEvidenceReportServiceTest {

    @Autowired
    private RagEvidenceReportService reportService;

    @Test
    void buildTeachingEvidenceReportShouldAssembleRuntimeDemoEvidence() {
        RagEvidenceReportResponse response = reportService.buildTeachingEvidenceReport();

        assertThat(response.getStatus()).isEqualTo("Ready");
        assertThat(response.getSummary())
                .contains("Spring Boot 后端")
                .contains("doc-parser")
                .contains("Python 服务边界");
        assertThat(response.getDemo().getKbId()).isEqualTo(RagDemoSeedService.DEMO_KB_ID);
        assertThat(response.getEvaluation().getPassed()).isTrue();
        assertThat(response.getAdapterStatus().getAdapters())
                .extracting("axis")
                .contains("vectorStore", "keywordSearch", "rerank", "docParser");
        assertThat(response.getStats()).hasSize(4)
                .extracting("label")
                .contains("Scaffold Stack", "Demo", "Evaluation", "Evidence");
        assertThat(response.getIngestionBoundary().getUploadApi())
                .isEqualTo("POST /api/knowledge/bases/{kbId}/documents");
        assertThat(response.getIngestionBoundary().getPythonBoundary())
                .contains("agent-doc-parser Python service");
        assertThat(response.getEvidenceCommands())
                .contains("curl -fsS -X POST http://localhost:10001/api/test/rag-demo/evidence-report")
                .contains("./scripts/probe-rag-ingestion-runtime.sh")
                .contains("./scripts/check-contracts.sh");
        assertThat(response.getMarkdown())
                .contains("# agent-knowledge RAG Demo Evidence")
                .contains("## Scaffold Stack")
                .contains("## Runtime Adapter Status")
                .contains("## Ingestion Boundary")
                .contains("Doc Parser: Python service over HTTP");
    }
}
