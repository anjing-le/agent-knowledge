package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentProcessingContextServiceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final DocumentProcessingContextService contextService = new DocumentProcessingContextService(
            documentRepository,
            knowledgeBaseRepository
    );

    @Test
    void loadContextShouldReturnDocumentAndKnowledgeBase() {
        Document document = document("doc_001", "kb_001");
        KnowledgeBase knowledgeBase = knowledgeBase("kb_001");

        when(documentRepository.findById("doc_001")).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb_001")).thenReturn(Optional.of(knowledgeBase));

        DocumentProcessingContextService.DocumentProcessingContext context = contextService.loadContext("doc_001");

        assertThat(context.document()).isSameAs(document);
        assertThat(context.knowledgeBase()).isSameAs(knowledgeBase);
        assertThat(context.kbId()).isEqualTo("kb_001");
    }

    @Test
    void loadContextShouldFailWhenDocumentMissing() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.loadContext("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("文档不存在: missing");

        verifyNoInteractions(knowledgeBaseRepository);
    }

    @Test
    void loadContextShouldFailWhenKnowledgeBaseMissing() {
        Document document = document("doc_001", "kb_missing");
        when(documentRepository.findById("doc_001")).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByKbIdAndIsDeletedFalse("kb_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contextService.loadContext("doc_001"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("知识库不存在: kb_missing");
    }

    private Document document(String docId, String kbId) {
        Document document = new Document();
        document.setDocId(docId);
        document.setKbId(kbId);
        return document;
    }

    private KnowledgeBase knowledgeBase(String kbId) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKbId(kbId);
        return knowledgeBase;
    }
}
