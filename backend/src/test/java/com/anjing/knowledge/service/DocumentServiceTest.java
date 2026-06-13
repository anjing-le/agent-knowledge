package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.FileStorage;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.FileStorageRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final FileStorageRepository fileStorageRepository = mock(FileStorageRepository.class);
    private final ChunkRepository chunkRepository = mock(ChunkRepository.class);
    private final DocumentService documentService = new DocumentService(
            documentRepository,
            knowledgeBaseRepository,
            fileStorageRepository,
            chunkRepository
    );

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(documentService, "uploadBaseDir", uploadDir.toString());
    }

    @Test
    void createUploadedDocumentShouldPersistNewFileWithSingleReference() throws Exception {
        MultipartFile file = uploadFile("guide.md", "text/markdown", "hello rag");
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb_001");

        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb_001"))
                .thenReturn(Optional.of(knowledgeBase));
        when(fileStorageRepository.findByFileMd5("5b181270c6cbfdbbf943e070fdbd98a5"))
                .thenReturn(Optional.empty());
        when(fileStorageRepository.save(org.mockito.ArgumentMatchers.any(FileStorage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.createUploadedDocument("kb_001", file, "parser", "chunk");

        ArgumentCaptor<FileStorage> storageCaptor = forClass(FileStorage.class);
        verify(fileStorageRepository).save(storageCaptor.capture());
        verify(fileStorageRepository, never()).incrementRefCount(org.mockito.ArgumentMatchers.anyString());

        FileStorage storage = storageCaptor.getValue();
        assertThat(storage.getRefCount()).isEqualTo(1);
        assertThat(storage.getFileId()).matches("file_\\d{14}_\\d{4}");
        assertThat(Files.exists(Path.of(storage.getStoragePath()))).isTrue();
        assertThat(document.getFileId()).isEqualTo(storage.getFileId());
        assertThat(document.getKbId()).isEqualTo("kb_001");
        assertThat(document.getDocType()).isEqualTo("md");
    }

    @Test
    void createUploadedDocumentShouldIncrementReferenceForExistingFile() throws Exception {
        MultipartFile file = uploadFile("guide.md", "text/markdown", "hello rag");
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId("kb_001");
        FileStorage existingStorage = new FileStorage();
        existingStorage.setFileId("file_existing");

        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb_001"))
                .thenReturn(Optional.of(knowledgeBase));
        when(fileStorageRepository.findByFileMd5("5b181270c6cbfdbbf943e070fdbd98a5"))
                .thenReturn(Optional.of(existingStorage));
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Document document = documentService.createUploadedDocument("kb_001", file, null, null);

        verify(fileStorageRepository).incrementRefCount("file_existing");
        verify(fileStorageRepository, never()).save(org.mockito.ArgumentMatchers.any(FileStorage.class));
        assertThat(document.getFileId()).isEqualTo("file_existing");
    }

    private MultipartFile uploadFile(String filename, String contentType, String content) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) bytes.length);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(bytes));
        return file;
    }
}
