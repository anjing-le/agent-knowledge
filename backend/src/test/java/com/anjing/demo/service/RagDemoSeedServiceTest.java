package com.anjing.demo.service;

import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.knowledge.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagDemoSeedServiceTest {

    @Autowired
    private RagDemoSeedService ragDemoSeedService;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private DocumentProcessingTaskRepository taskRepository;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void seedTeachingDemoShouldBeRepeatableAndSearchable() {
        RagDemoSeedResponse first = ragDemoSeedService.seedTeachingDemo();
        RagDemoSeedResponse second = ragDemoSeedService.seedTeachingDemo();

        assertThat(second.getKbId()).isEqualTo(first.getKbId());
        assertThat(second.getKbName()).isEqualTo(RagDemoSeedService.DEMO_KB_NAME);
        assertThat(second.getDocId()).isEqualTo(RagDemoSeedService.DEMO_DOC_ID);
        assertThat(second.getChunkIds()).hasSize(3);
        assertThat(second.getVectorCount()).isEqualTo(3);
        assertThat(second.getSampleResultCount()).isGreaterThan(0);
        assertThat(second.getTopScoreExplanation()).contains("rank=1");
        assertThat(second.getRetrievalRoute())
                .contains("/kb/retrieval?q=")
                .contains("kbIds=" + second.getKbId())
                .contains("source=demo")
                .contains("autoSearch=1");
        assertThat(second.getChatRoute()).contains("source=retrieval").contains("autoSend=1");

        assertThat(knowledgeBaseRepository.findByNameAndIsDeletedFalse(RagDemoSeedService.DEMO_KB_NAME)).isPresent();
        assertThat(documentRepository.findByDocIdAndIsDeletedFalse(RagDemoSeedService.DEMO_DOC_ID)).isPresent();
        assertThat(chunkRepository.countByDocId(RagDemoSeedService.DEMO_DOC_ID)).isEqualTo(3);
        assertThat(taskRepository.findByDocIdOrderByCreatedAtDesc(RagDemoSeedService.DEMO_DOC_ID)).hasSize(1);
        assertThat(vectorStoreService.getVectorCount(second.getKbId())).isEqualTo(3);
    }
}
