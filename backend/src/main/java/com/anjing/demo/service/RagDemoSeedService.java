package com.anjing.demo.service;

import com.anjing.demo.model.response.RagDemoSeedResponse;
import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.model.enums.EmbeddingStatus;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentProcessingTaskRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.knowledge.service.DocumentEmbeddingService;
import com.anjing.knowledge.service.RetrievalService;
import com.anjing.knowledge.service.VectorStoreService;
import com.anjing.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seeds a deterministic local RAG dataset for scaffold teaching demos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagDemoSeedService {

    public static final String DEMO_KB_ID = "kb_rag_demo_teaching";
    public static final String DEMO_KB_NAME = "RAG Demo Teaching KB";
    public static final String DEMO_DOC_ID = "doc_rag_demo_teaching";
    public static final String DEMO_DOC_NAME = "scaffold-rag-demo.md";
    public static final String DEMO_TASK_ID = "task_rag_demo_teaching";
    public static final String DEMO_EMBEDDING_MODEL = "local-demo-embedding";
    public static final String DEMO_RETRIEVAL_QUERY = "脚手架如何生长成 RAG agent，并保留工程最佳实践？";
    public static final String DEMO_CHAT_QUESTION = "脚手架到 RAG agent 的教学主线是什么？";

    private static final List<String> DEMO_CHUNK_IDS = List.of(
            "chunk_rag_demo_teaching_001",
            "chunk_rag_demo_teaching_002",
            "chunk_rag_demo_teaching_003"
    );

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentProcessingTaskRepository taskRepository;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final RetrievalService retrievalService;

    @Transactional(rollbackFor = Exception.class)
    public RagDemoSeedResponse seedTeachingDemo() {
        KnowledgeBase knowledgeBase = upsertDemoKnowledgeBase();
        resetDemoDocumentData(knowledgeBase.getKbId());

        Document document = documentRepository.saveAndFlush(createCompletedDocument(knowledgeBase.getKbId()));
        taskRepository.save(createCompletedTask(document));
        List<Chunk> chunks = chunkRepository.saveAllAndFlush(createChunks(knowledgeBase.getKbId(), document.getDocId()));

        boolean embedded = documentEmbeddingService.embedChunks(
                knowledgeBase.getKbId(),
                chunks,
                DEMO_EMBEDDING_MODEL
        );
        if (!embedded) {
            throw new IllegalStateException("RAG demo seed failed to embed demo chunks");
        }

        List<SearchResult> sampleResults = searchDemoKnowledge(knowledgeBase.getKbId());
        log.info("RAG demo seed completed: kbId={}, docId={}, chunks={}, sampleResults={}",
                knowledgeBase.getKbId(), document.getDocId(), chunks.size(), sampleResults.size());

        return buildResponse(knowledgeBase, document, sampleResults);
    }

    private KnowledgeBase upsertDemoKnowledgeBase() {
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByNameAndIsDeletedFalse(DEMO_KB_NAME)
                .orElseGet(this::newDemoKnowledgeBase);
        knowledgeBase.setName(DEMO_KB_NAME);
        knowledgeBase.setDescription("Local teaching dataset showing how infra-dev-scaffolding grows into a RAG agent.");
        knowledgeBase.setAvatar(null);
        knowledgeBase.setEmbeddingModel(DEMO_EMBEDDING_MODEL);
        knowledgeBase.setKbType("");
        knowledgeBase.setChunkSize(300);
        knowledgeBase.setChunkOverlap(30);
        knowledgeBase.setRaptorEnabled(false);
        knowledgeBase.setRaptorConfig(null);
        knowledgeBase.setIsEnabled(true);
        knowledgeBase.setIsDeleted(false);
        return knowledgeBaseRepository.saveAndFlush(knowledgeBase);
    }

    private KnowledgeBase newDemoKnowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId(DEMO_KB_ID);
        knowledgeBase.setCreatedAt(DateUtils.nowLocalDateTime());
        return knowledgeBase;
    }

    private void resetDemoDocumentData(String kbId) {
        vectorStoreService.deleteByDocChunks(kbId, DEMO_CHUNK_IDS);
        chunkRepository.deleteByDocId(DEMO_DOC_ID);
        chunkRepository.flush();
        taskRepository.deleteAll(taskRepository.findByDocIdOrderByCreatedAtDesc(DEMO_DOC_ID));
        taskRepository.flush();
        documentRepository.findById(DEMO_DOC_ID).ifPresent(documentRepository::delete);
        documentRepository.flush();
    }

    private Document createCompletedDocument(String kbId) {
        Document document = new Document();
        document.setDocId(DEMO_DOC_ID);
        document.setKbId(kbId);
        document.setFileId("file_rag_demo_teaching");
        document.setDocName(DEMO_DOC_NAME);
        document.setDocType("MARKDOWN");
        document.setDocSize(1536L);
        document.setStatus(DocumentStatus.COMPLETED.getCode());
        document.setProgress(1.0f);
        document.setProgressMsg("RAG demo seed completed with local-demo providers");
        document.setParserStrategyId("agent-doc-parser");
        document.setChunkStrategyId("scaffold-rag-demo-chunking");
        document.setParserTaskId("parser_rag_demo_teaching");
        document.setChunkNum(DEMO_CHUNK_IDS.size());
        document.setTokenNum(168);
        document.setImageNum(0);
        document.setDocMeta("{\"source\":\"seed-rag-demo\",\"parser\":\"agent-doc-parser\",\"mode\":\"local-demo\"}");
        document.setCompletedAt(DateUtils.nowLocalDateTime());
        document.setIsDeleted(false);
        document.setIsEnabled(true);
        return document;
    }

    private DocumentProcessingTask createCompletedTask(Document document) {
        DocumentProcessingTask task = new DocumentProcessingTask();
        task.setTaskId(DEMO_TASK_ID);
        task.setDocId(document.getDocId());
        task.setKbId(document.getKbId());
        task.setTaskType("INGESTION");
        task.setPhase("COMPLETED");
        task.setStatus("SUCCEEDED");
        task.setProgress(1.0f);
        task.setMessage("Demo seed shows upload -> parse -> chunk -> embedding -> retrieval");
        task.setParserTaskId(document.getParserTaskId());
        task.setRetryCount(0);
        task.setStartedAt(DateUtils.nowLocalDateTime());
        task.setCompletedAt(DateUtils.nowLocalDateTime());
        return task;
    }

    private List<Chunk> createChunks(String kbId, String docId) {
        return List.of(
                chunk(
                        DEMO_CHUNK_IDS.get(0),
                        kbId,
                        docId,
                        0,
                        "infra-dev-scaffolding 提供 Spring Boot、统一响应 APIResponse、分页 PageResult、OpenAPI、请求上下文、RemoteHttpClient 和质量门禁。agent-knowledge 只在这个底座上生长 RAG 业务边界。",
                        "{\"page_idx\":[1],\"content_type\":\"markdown\",\"section\":\"scaffold-foundation\",\"source_parser_result_ids\":[\"parser_demo_001\"]}"
                ),
                chunk(
                        DEMO_CHUNK_IDS.get(1),
                        kbId,
                        docId,
                        1,
                        "RAG 链路包含文档上传、agent-doc-parser HTTP 解析、切片、Embedding、向量检索、上下文组装、LLM 回答和答案引用。",
                        "{\"page_idx\":[2],\"content_type\":\"markdown\",\"section\":\"rag-pipeline\",\"source_parser_result_ids\":[\"parser_demo_002\"]}"
                ),
                chunk(
                        DEMO_CHUNK_IDS.get(2),
                        kbId,
                        docId,
                        2,
                        "Java 后端负责知识库、文档任务、Chunk、检索和 Chat 编排；agent-doc-parser 保持独立服务，只通过 HTTP contract 返回解析文本和 metadata。",
                        "{\"page_idx\":[3],\"content_type\":\"markdown\",\"section\":\"java-python-boundary\",\"source_parser_result_ids\":[\"parser_demo_003\"]}"
                )
        );
    }

    private Chunk chunk(String chunkId, String kbId, String docId, int index, String content, String metadata) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(chunkId);
        chunk.setKbId(kbId);
        chunk.setDocId(docId);
        chunk.setTaskId(DEMO_TASK_ID);
        chunk.setContent(content);
        chunk.setChunkIndex(index);
        chunk.setChunkLength(content.length());
        chunk.setTokenCount(Math.max(1, content.length() / 2));
        chunk.setMetadata(metadata);
        chunk.setEmbeddingStatus(EmbeddingStatus.NOT_EMBEDDED.getCode());
        chunk.setIsEnabled(true);
        return chunk;
    }

    private List<SearchResult> searchDemoKnowledge(String kbId) {
        SearchRequest request = new SearchRequest();
        request.setQuery(DEMO_RETRIEVAL_QUERY);
        request.setKbIds(List.of(kbId));
        request.setTopK(3);
        request.setCandidateCount(6);
        request.setSimilarityThreshold(0.0f);
        request.setRerank(false);
        return retrievalService.search(request);
    }

    private RagDemoSeedResponse buildResponse(KnowledgeBase knowledgeBase, Document document, List<SearchResult> results) {
        RagDemoSeedResponse response = new RagDemoSeedResponse();
        response.setKbId(knowledgeBase.getKbId());
        response.setKbName(knowledgeBase.getName());
        response.setDocId(document.getDocId());
        response.setDocName(document.getDocName());
        response.setChunkIds(DEMO_CHUNK_IDS);
        response.setVectorCount(vectorStoreService.getVectorCount(knowledgeBase.getKbId()));
        response.setRetrievalQuery(DEMO_RETRIEVAL_QUERY);
        response.setSampleResultCount(results.size());
        if (!results.isEmpty()) {
            response.setTopChunkId(results.get(0).getChunkId());
            response.setTopScoreExplanation(results.get(0).getScoreExplanation());
        }
        response.setChatQuestion(DEMO_CHAT_QUESTION);
        response.setPipelineRoute("/kb/pipeline");
        response.setKnowledgeRoute("/kb/knowledge/detail/" + knowledgeBase.getKbId());
        response.setRetrievalRoute("/kb/retrieval?q=" + encode(DEMO_RETRIEVAL_QUERY)
                + "&kbIds=" + encode(knowledgeBase.getKbId())
                + "&topK=3"
                + "&candidateCount=6"
                + "&similarityThreshold=0"
                + "&source=demo"
                + "&autoSearch=1");
        response.setChatRoute("/kb/chat?q=" + encode(DEMO_CHAT_QUESTION)
                + "&kbIds=" + encode(knowledgeBase.getKbId())
                + "&source=retrieval");
        response.setEvidenceCommands(List.of(
                "./scripts/seed-rag-demo.sh",
                "./scripts/smoke-rag-demo.sh",
                "./scripts/check-template.sh",
                "./scripts/check-contracts.sh"
        ));
        return response;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
