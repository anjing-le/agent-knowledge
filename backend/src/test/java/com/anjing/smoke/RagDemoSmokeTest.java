package com.anjing.smoke;

import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.demo.service.RagDemoSeedService;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.service.ChatService;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.service.RetrievalService;
import com.anjing.knowledge.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagDemoSmokeTest {

    @Autowired
    private RagDemoSeedService ragDemoSeedService;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ChatService chatService;

    @Test
    void ragDemoShouldRunKnowledgeRetrievalAndChatWithLocalProviders() {
        RagDemoSeedResponse demo = ragDemoSeedService.seedTeachingDemo();

        assertThat(demo.getKbName()).isEqualTo(RagDemoSeedService.DEMO_KB_NAME);
        assertThat(demo.getDocName()).isEqualTo(RagDemoSeedService.DEMO_DOC_NAME);
        assertThat(demo.getVectorCount()).isEqualTo(3);
        assertThat(demo.getSampleResultCount()).isGreaterThan(0);
        assertThat(demo.getRetrievalRoute()).contains("/kb/retrieval?q=").contains("autoSearch=1");
        assertThat(demo.getChatRoute()).contains("/kb/chat?q=").contains("autoSend=1");
        assertThat(demo.getEvidenceCommands()).contains("./scripts/create-demo-evidence.sh --dry-run");
        assertThat(demo.getEvidenceCommands()).contains("./scripts/probe-doc-parser-boundary.sh --contract-only");
        assertThat(vectorStoreService.getVectorCount(demo.getKbId())).isEqualTo(3);
        assertThat(chunkRepository.findById("chunk_rag_demo_teaching_001"))
                .get()
                .extracting(Chunk::getEmbeddingStatus)
                .isEqualTo(EmbeddingStatus.EMBEDDED.getCode());

        List<SearchResult> searchResults = searchKnowledge(demo.getKbId());

        assertThat(searchResults).isNotEmpty();
        SearchResult first = searchResults.get(0);
        assertThat(first.getChunkId()).startsWith("chunk_rag_demo_teaching_");
        assertThat(first.getDocName()).isEqualTo(RagDemoSeedService.DEMO_DOC_NAME);
        assertThat(first.getKbName()).isEqualTo(RagDemoSeedService.DEMO_KB_NAME);
        assertThat(first.getRank()).isEqualTo(1);
        assertThat(first.getScoreExplanation()).contains("rank=1");
        assertThat(first.getMetadata()).containsEntry("content_type", "markdown");

        MessageResponse answer = chatWithKnowledge(demo.getKbId());

        assertThat(answer.getRole()).isEqualTo("assistant");
        assertThat(answer.getContent())
                .contains("本地演示回答")
                .contains("引用仍来自真实检索结果");
        assertThat(answer.getReferences()).isNotEmpty();
        assertThat(answer.getReferences().get(0).getDocName()).isEqualTo(RagDemoSeedService.DEMO_DOC_NAME);
        assertThat(chatService.getMessages(answer.getConversationId())).hasSize(2);
    }

    private List<SearchResult> searchKnowledge(String kbId) {
        SearchRequest request = new SearchRequest();
        request.setQuery(RagDemoSeedService.DEMO_RETRIEVAL_QUERY);
        request.setKbIds(List.of(kbId));
        request.setTopK(2);
        request.setCandidateCount(4);
        request.setSimilarityThreshold(0.0f);
        return retrievalService.search(request);
    }

    private MessageResponse chatWithKnowledge(String kbId) {
        CreateConversationRequest createRequest = new CreateConversationRequest();
        createRequest.setTitle("RAG demo teaching smoke");
        createRequest.setKbIds(List.of(kbId));
        ConversationResponse conversation = chatService.createConversation(createRequest);

        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setConversationId(conversation.getConversationId());
        sendRequest.setContent(RagDemoSeedService.DEMO_CHAT_QUESTION);
        sendRequest.setKbIds(List.of(kbId));
        return chatService.sendMessage(sendRequest);
    }
}
