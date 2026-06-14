package com.anjing.demo.service;

import com.anjing.demo.model.response.RagRetrievalEvaluationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagRetrievalEvaluationServiceTest {

    @Autowired
    private RagRetrievalEvaluationService evaluationService;

    @Test
    void evaluateDemoRetrievalShouldPassAllTeachingCases() {
        RagRetrievalEvaluationResponse response = evaluationService.evaluateDemoRetrieval();

        assertThat(response.getSuiteName()).isEqualTo("rag-demo-retrieval-v1");
        assertThat(response.getKbId()).isEqualTo(RagDemoSeedService.DEMO_KB_ID);
        assertThat(response.getTopK()).isEqualTo(3);
        assertThat(response.getTotalCases()).isEqualTo(3);
        assertThat(response.getPassedCases()).isEqualTo(3);
        Float recallAtK = response.getRecallAtK();
        assertThat(recallAtK).isEqualTo(1.0f);
        assertThat(response.getPassed()).isTrue();
        assertThat(response.getCases()).hasSize(3)
                .allSatisfy(result -> {
                    assertThat(result.getPassed()).isTrue();
                    assertThat(result.getExpectedRank()).isBetween(1, 3);
                    assertThat(result.getHitChunkIds()).isNotEmpty();
                    assertThat(result.getTopScoreExplanation())
                            .contains("rank=")
                            .contains("hybrid=")
                            .contains("rerank=");
                });
        assertThat(response.getEvidenceCommands()).contains("./scripts/evaluate-rag-retrieval.sh");
    }
}
