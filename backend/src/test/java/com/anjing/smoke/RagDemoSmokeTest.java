package com.anjing.smoke;

import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.service.ChatService;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.model.request.CreateKnowledgeBaseRequest;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.KnowledgeBaseResponse;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.service.DocumentEmbeddingService;
import com.anjing.knowledge.service.KnowledgeBaseService;
import com.anjing.knowledge.service.RetrievalService;
import com.anjing.knowledge.service.VectorStoreService;
import com.anjing.util.DateUtils;
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
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private DocumentEmbeddingService documentEmbeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ChatService chatService;

    @Test
    void ragDemoShouldRunKnowledgeRetrievalAndChatWithLocalProviders() {
        KnowledgeBaseResponse knowledgeBase = createKnowledgeBase();
        Document document = createCompletedDocument(knowledgeBase.getKbId());
        List<Chunk> chunks = createChunks(knowledgeBase.getKbId(), document.getDocId());

        boolean embedded = documentEmbeddingService.embedChunks(
                knowledgeBase.getKbId(),
                chunks,
                "local-demo-embedding"
        );

        assertThat(embedded).isTrue();
        assertThat(vectorStoreService.getVectorCount(knowledgeBase.getKbId())).isEqualTo(2);
        assertThat(chunkRepository.findById("chunk_rag_demo_001"))
                .get()
                .extracting(Chunk::getEmbeddingStatus)
                .isEqualTo(EmbeddingStatus.EMBEDDED.getCode());

        List<SearchResult> searchResults = searchKnowledge(knowledgeBase.getKbId());

        assertThat(searchResults).isNotEmpty();
        SearchResult first = searchResults.get(0);
        assertThat(first.getChunkId()).startsWith("chunk_rag_demo_");
        assertThat(first.getDocName()).isEqualTo("scaffold-rag-demo.md");
        assertThat(first.getKbName()).isEqualTo("RAG Demo Smoke KB");
        assertThat(first.getRank()).isEqualTo(1);
        assertThat(first.getScoreExplanation()).contains("rank=1");
        assertThat(first.getMetadata()).containsEntry("content_type", "markdown");

        MessageResponse answer = chatWithKnowledge(knowledgeBase.getKbId());

        assertThat(answer.getRole()).isEqualTo("assistant");
        assertThat(answer.getContent())
                .contains("本地演示回答")
                .contains("引用仍来自真实检索结果");
        assertThat(answer.getReferences()).isNotEmpty();
        assertThat(answer.getReferences().get(0).getDocName()).isEqualTo("scaffold-rag-demo.md");
        assertThat(chatService.getMessages(answer.getConversationId())).hasSize(2);
    }

    private KnowledgeBaseResponse createKnowledgeBase() {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("RAG Demo Smoke KB");
        request.setDescription("End-to-end local smoke for scaffold-grown RAG agent teaching.");
        request.setEmbeddingModel("local-demo-embedding");
        request.setChunkSize(300);
        request.setChunkOverlap(30);
        return knowledgeBaseService.createKnowledgeBase(request);
    }

    private Document createCompletedDocument(String kbId) {
        Document document = new Document();
        document.setDocId("doc_rag_demo");
        document.setKbId(kbId);
        document.setFileId("file_rag_demo");
        document.setDocName("scaffold-rag-demo.md");
        document.setDocType("MARKDOWN");
        document.setDocSize(1024L);
        document.setStatus(DocumentStatus.COMPLETED.getCode());
        document.setProgress(1.0f);
        document.setProgressMsg("RAG demo smoke seed");
        document.setChunkNum(2);
        document.setTokenNum(96);
        document.setDocMeta("{\"source\":\"smoke-rag-demo\",\"parser\":\"local-demo\"}");
        document.setCompletedAt(DateUtils.nowLocalDateTime());
        document.setIsDeleted(false);
        document.setIsEnabled(true);
        return documentRepository.saveAndFlush(document);
    }

    private List<Chunk> createChunks(String kbId, String docId) {
        Chunk first = chunk(
                "chunk_rag_demo_001",
                kbId,
                docId,
                0,
                "infra-dev-scaffolding 提供 Spring Boot、统一响应、分页、OpenAPI、请求上下文和质量门禁。agent-knowledge 只在这个底座上生长 RAG 业务边界。",
                "{\"page_idx\":[1],\"content_type\":\"markdown\",\"section\":\"scaffold-foundation\"}"
        );
        Chunk second = chunk(
                "chunk_rag_demo_002",
                kbId,
                docId,
                1,
                "RAG 链路包含文档上传、Python doc-parser 解析、切片、Embedding、向量检索、上下文组装、LLM 回答和答案引用。",
                "{\"page_idx\":[2],\"content_type\":\"markdown\",\"section\":\"rag-pipeline\"}"
        );
        return chunkRepository.saveAllAndFlush(List.of(first, second));
    }

    private Chunk chunk(String chunkId, String kbId, String docId, int index, String content, String metadata) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setKbId(kbId);
        chunk.setDocId(docId);
        chunk.setTaskId("task_rag_demo");
        chunk.setContent(content);
        chunk.setChunkIndex(index);
        chunk.setChunkLength(content.length());
        chunk.setTokenCount(Math.max(1, content.length() / 2));
        chunk.setMetadata(metadata);
        chunk.setEmbeddingStatus(EmbeddingStatus.NOT_EMBEDDED.getCode());
        chunk.setIsEnabled(true);
        return chunk;
    }

    private List<SearchResult> searchKnowledge(String kbId) {
        SearchRequest request = new SearchRequest();
        request.setQuery("脚手架如何生长成 RAG agent，并保留工程最佳实践？");
        request.setKbIds(List.of(kbId));
        request.setTopK(2);
        request.setCandidateCount(4);
        request.setSimilarityThreshold(0.0f);
        return retrievalService.search(request);
    }

    private MessageResponse chatWithKnowledge(String kbId) {
        CreateConversationRequest createRequest = new CreateConversationRequest();
        createRequest.setTitle("RAG demo smoke");
        createRequest.setKbIds(List.of(kbId));
        ConversationResponse conversation = chatService.createConversation(createRequest);

        SendMessageRequest sendRequest = new SendMessageRequest();
        sendRequest.setConversationId(conversation.getConversationId());
        sendRequest.setContent("脚手架到 RAG agent 的教学主线是什么？");
        sendRequest.setKbIds(List.of(kbId));
        return chatService.sendMessage(sendRequest);
    }
}
