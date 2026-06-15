package com.anjing.knowledge.controller;

import com.anjing.knowledge.model.response.DocumentResponse;
import com.anjing.knowledge.service.DocumentIngestionService;
import com.anjing.knowledge.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DocumentControllerTest {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final MockMvc mockMvc = standaloneSetup(new DocumentController(documentService, ingestionService)).build();

    @Test
    void uploadDocumentShouldAcceptMultipartFileWithoutQueryParam() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rag-upload.txt",
                "text/plain",
                "rag upload content".getBytes()
        );
        when(ingestionService.uploadDocument(eq("kb_001"), any(), eq(null), eq(null)))
                .thenReturn(document("doc_001", "rag-upload.txt"));

        mockMvc.perform(multipart("/api/knowledge/bases/kb_001/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.docId").value("doc_001"));

        verify(ingestionService).uploadDocument(eq("kb_001"), any(), eq(null), eq(null));
    }

    @Test
    void batchUploadDocumentsShouldUseExplicitBatchUploadPath() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "rag-batch.txt",
                "text/plain",
                "rag batch content".getBytes()
        );
        when(ingestionService.batchUploadDocuments(eq("kb_001"), any(), eq(null), eq(null)))
                .thenReturn(List.of(document("doc_002", "rag-batch.txt")));

        mockMvc.perform(multipart("/api/knowledge/bases/kb_001/documents/batch-upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[0].docId").value("doc_002"));

        verify(ingestionService).batchUploadDocuments(eq("kb_001"), any(), eq(null), eq(null));
    }

    private DocumentResponse document(String docId, String docName) {
        DocumentResponse response = new DocumentResponse();
        response.setDocId(docId);
        response.setKbId("kb_001");
        response.setDocName(docName);
        response.setStatus("PENDING");
        return response;
    }
}
