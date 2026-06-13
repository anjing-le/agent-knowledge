package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads the document and knowledge base required by a RAG processing run.
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessingContextService {

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public DocumentProcessingContext loadContext(String docId) {
        Document document = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + docId));

        String kbId = document.getKbId();
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new RuntimeException("知识库不存在: " + kbId));

        return new DocumentProcessingContext(document, knowledgeBase);
    }

    public record DocumentProcessingContext(Document document, KnowledgeBase knowledgeBase) {

        public String kbId() {
            return knowledgeBase.getKbId();
        }
    }
}
