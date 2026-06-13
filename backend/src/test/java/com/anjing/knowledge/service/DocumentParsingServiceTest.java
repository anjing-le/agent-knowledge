package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.FileStorage;
import com.anjing.knowledge.repository.FileStorageRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentParsingServiceTest {

    private final DocParserClient docParserClient = mock(DocParserClient.class);
    private final FileStorageRepository fileStorageRepository = mock(FileStorageRepository.class);
    private final DocumentParsingService parsingService = new DocumentParsingService(
            docParserClient,
            fileStorageRepository
    );

    @Test
    void parseDocumentShouldCallPythonDocParserWithMappedType() {
        Document document = document("file_001", "md");
        FileStorage fileStorage = fileStorage("/tmp/rag.md");
        DocParserClient.ParseResult expected = new DocParserClient.ParseResult();
        expected.setSuccess(true);

        when(fileStorageRepository.findById("file_001")).thenReturn(Optional.of(fileStorage));
        when(docParserClient.isHealthy()).thenReturn(true);
        when(docParserClient.parseDocument("/tmp/rag.md", "PLAIN_TEXT")).thenReturn(expected);

        DocParserClient.ParseResult result = parsingService.parseDocument(document);

        assertThat(result).isSameAs(expected);
        verify(docParserClient).parseDocument("/tmp/rag.md", "PLAIN_TEXT");
    }

    @Test
    void parseDocumentShouldMapSpreadsheetType() {
        Document document = document("file_001", "xlsx");
        FileStorage fileStorage = fileStorage("/tmp/table.xlsx");
        DocParserClient.ParseResult expected = new DocParserClient.ParseResult();
        expected.setSuccess(true);

        when(fileStorageRepository.findById("file_001")).thenReturn(Optional.of(fileStorage));
        when(docParserClient.isHealthy()).thenReturn(true);
        when(docParserClient.parseDocument("/tmp/table.xlsx", "STANDARD_WORKBOOK")).thenReturn(expected);

        DocParserClient.ParseResult result = parsingService.parseDocument(document);

        assertThat(result.isSuccess()).isTrue();
        verify(docParserClient).parseDocument("/tmp/table.xlsx", "STANDARD_WORKBOOK");
    }

    @Test
    void parseDocumentShouldFailWhenFilePathIsMissing() {
        when(fileStorageRepository.findById("missing")).thenReturn(Optional.empty());

        DocParserClient.ParseResult result = parsingService.parseDocument(document("missing", "pdf"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("文件存储路径不存在");
        verify(docParserClient, never()).isHealthy();
        verify(docParserClient, never()).parseDocument(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void parseDocumentShouldFailWhenDocParserIsUnhealthy() {
        when(fileStorageRepository.findById("file_001")).thenReturn(Optional.of(fileStorage("/tmp/rag.pdf")));
        when(docParserClient.isHealthy()).thenReturn(false);

        DocParserClient.ParseResult result = parsingService.parseDocument(document("file_001", "pdf"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("doc-parser 服务不可用");
        verify(docParserClient, never()).parseDocument(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private Document document(String fileId, String docType) {
        Document document = new Document();
        document.setFileId(fileId);
        document.setDocType(docType);
        return document;
    }

    private FileStorage fileStorage(String path) {
        FileStorage fileStorage = new FileStorage();
        fileStorage.setStoragePath(path);
        return fileStorage;
    }
}
