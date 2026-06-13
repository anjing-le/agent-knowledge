package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.FileStorage;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.FileStorageRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingServiceTest {

    private final DocParserClient docParserClient = mock(DocParserClient.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final FileStorageRepository fileStorageRepository = mock(FileStorageRepository.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentProcessingTaskService taskService = mock(DocumentProcessingTaskService.class);
    private final DocumentChunkingService chunkingService = new DocumentChunkingService(new ObjectMapper());
    private final DocumentEmbeddingService documentEmbeddingService = mock(DocumentEmbeddingService.class);

    private final DocumentProcessingService processingService = new DocumentProcessingService(
            docParserClient,
            documentRepository,
            knowledgeBaseRepository,
            chunkRepository,
            fileStorageRepository,
            documentService,
            taskService,
            chunkingService,
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

        FileStorage fileStorage = new FileStorage();
        fileStorage.setFileId("file_001");
        fileStorage.setStoragePath("/tmp/rag.md");

        when(documentRepository.findById("doc_001")).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb_001")).thenReturn(Optional.of(knowledgeBase));
        when(fileStorageRepository.findById("file_001")).thenReturn(Optional.of(fileStorage));
        when(docParserClient.isHealthy()).thenReturn(true);
        when(chunkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        when(docParserClient.parseDocument("/tmp/rag.md", "PLAIN_TEXT")).thenReturn(parseResult);
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

        assertThat(document.getChunkNum()).isEqualTo(1);
        assertThat(document.getTokenNum()).isEqualTo(12);
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

        when(docParserClient.parseDocument("/tmp/rag.md", "PLAIN_TEXT")).thenReturn(parseResult);
        when(documentEmbeddingService.embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small")))
                .thenReturn(false);

        processingService.processDocument("doc_001");

        verify(documentEmbeddingService).embedChunks(eq("kb_001"), anyList(), eq("text-embedding-3-small"));
        verify(taskService).markFailed("doc_001", "EMBEDDING", "向量化失败");
        verify(documentService).updateDocumentStatus("doc_001", DocumentStatus.EMBEDDING_FAILED, 0.0f, "向量化失败");
        verify(taskService, never()).markSucceeded(anyString(), anyString());
    }
}
