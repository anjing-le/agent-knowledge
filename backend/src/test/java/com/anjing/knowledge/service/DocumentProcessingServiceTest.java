package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingServiceTest {

    private final DocumentProcessingContextService contextService = mock(DocumentProcessingContextService.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentProcessingTaskService taskService = mock(DocumentProcessingTaskService.class);
    private final DocumentParsingService parsingService = mock(DocumentParsingService.class);
    private final DocumentChunkingService chunkingService = new DocumentChunkingService(new ObjectMapper());
    private final DocumentChunkPersistenceService chunkPersistenceService = mock(DocumentChunkPersistenceService.class);
    private final DocumentEmbeddingService documentEmbeddingService = mock(DocumentEmbeddingService.class);

    private final DocumentProcessingService processingService = new DocumentProcessingService(
            contextService,
            documentService,
            taskService,
            parsingService,
            chunkingService,
            chunkPersistenceService,
            documentEmbeddingService
    );

    private Document document;
    private KnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() {
        document = new Document();
        document.setDocId("doc_001");
        document.setKbId("kb_001");
        document.setFileId("file_001");
        document.setDocName("RAG 入门.md");
        document.setDocType("md");

        knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb_001");
        knowledgeBase.setName("RAG 教学库");
        knowledgeBase.setChunkSize(500);
        knowledgeBase.setChunkOverlap(50);
        knowledgeBase.setEmbeddingModel("text-embedding-3-small");

        when(contextService.loadContext("doc_001"))
                .thenReturn(new DocumentProcessingContextService.DocumentProcessingContext(document, knowledgeBase));
    }

    @Test
    void processDocumentShouldRunParseChunkEmbeddingAndVectorUpsert() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);
        DocParserClient.ChunkData chunkData = new DocParserClient.ChunkData();
        chunkData.setContent("脚手架可以生长出 RAG agent");
        chunkData.setIndex(0);
        chunkData.setTokenCount(12);
        chunkData.setMetadata(Map.of("page_idx", List.of(1), "content_type", "TEXT"));
        parseResult.setChunks(List.of(chunkData));
        parseResult.setMetadata(Map.of("parser", "doc-parser"));

        when(parsingService.parseDocument(document)).thenReturn(parseResult);
        when(chunkPersistenceService.saveChunks(eq(document), anyList()))
                .thenReturn(new DocumentChunkPersistenceService.PersistedChunks(1, 12));
        when(documentEmbeddingService.embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small")))
                .thenReturn(true);

        processingService.processDocument("doc_001");

        verify(taskService).ensureLatestTask(document, "文档开始处理");
        verify(taskService).markRunning("doc_001", "PARSING", 0.1f, "正在调用 Python doc-parser 解析文档");
        verify(taskService).markRunning("doc_001", "CHUNKING", 0.3f, "正在生成文档切片");
        verify(taskService).markRunning("doc_001", "EMBEDDING", 0.6f, "正在生成 Embedding 并写入向量库");
        verify(taskService).markSucceeded("doc_001", "文档处理完成");

        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.PARSING, 0.1f, "正在解析文档...");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.CHUNKING, 0.3f, "正在分块...");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.EMBEDDING, 0.6f, "正在向量化...");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.COMPLETED, 1.0f, "处理完成");

        verify(documentEmbeddingService).embedChunks(
                eq("kb_001"),
                anyList(),
                eq("text-embedding-3-small")
        );
    }

    @Test
    void processDocumentShouldFailWhenEmbeddingStageFails() {
        DocParserClient.ParseResult parseResult = new DocParserClient.ParseResult();
        parseResult.setSuccess(true);
        DocParserClient.ChunkData chunkData = new DocParserClient.ChunkData();
        chunkData.setContent("需要向量化的片段");
        chunkData.setIndex(0);
        chunkData.setTokenCount(8);
        parseResult.setChunks(List.of(chunkData));

        when(parsingService.parseDocument(document)).thenReturn(parseResult);
        when(chunkPersistenceService.saveChunks(eq(document), anyList()))
                .thenReturn(new DocumentChunkPersistenceService.PersistedChunks(1, 8));
        when(documentEmbeddingService.embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small")))
                .thenReturn(false);

        processingService.processDocument("doc_001");

        verify(documentEmbeddingService).embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small"));
        verify(taskService).markFailed("doc_001", "EMBEDDING", "向量化失败");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
        verify(taskService, never()).markSucceeded(anyString(), anyString());
    }

    @Test
    void processDocumentShouldStopWhenParsingFails() {
        DocParserClient.ParseResult parseResult = DocParserClient.ParseResult.error("doc-parser 服务不可用");
        when(parsingService.parseDocument(document)).thenReturn(parseResult);

        processingService.processDocument("doc_001");

        verify(taskService).markFailed("doc_001", "PARSING", "doc-parser 服务不可用");
        verify(documentService).updateDocumentStatus(
                "doc_001",
                DocumentStatus.PARSE_FAILED,
                0.0f,
                "解析失败: doc-parser 服务不可用"
        );
        verify(chunkPersistenceService, never()).saveChunks(eq(document), anyList());
        verify(documentEmbeddingService, never()).embedChunks(anyString(), anyList(), anyString());
    }
}
