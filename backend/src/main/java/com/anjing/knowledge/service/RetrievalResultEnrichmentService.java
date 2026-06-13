package com.anjing.knowledge.service;

import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Enriches vector hits with chunk, document, knowledge base and citation metadata.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalResultEnrichmentService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    public List<SearchResult> enrich(List<VectorStoreService.VectorSearchResult> vectorResults) {
        return vectorResults.stream()
                .map(this::enrich)
                .toList();
    }

    public SearchResult enrich(VectorStoreService.VectorSearchResult vectorResult) {
        SearchResult result = new SearchResult();
        result.setChunkId(vectorResult.getChunkId());
        result.setKbId(vectorResult.getKbId());
        result.setContent(vectorResult.getContent());
        result.setSimilarityScore(vectorResult.getScore());
        result.setFinalScore(vectorResult.getScore());

        chunkRepository.findById(vectorResult.getChunkId()).ifPresent(chunk -> {
            result.setDocId(chunk.getDocId());
            result.setMetadata(parseMetadata(chunk.getMetadata()));
            documentRepository.findById(chunk.getDocId())
                    .ifPresent(doc -> result.setDocName(doc.getDocName()));
        });

        knowledgeBaseRepository.findById(vectorResult.getKbId())
                .ifPresent(kb -> result.setKbName(kb.getName()));

        return result;
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
        } catch (Exception error) {
            log.warn("解析 chunk metadata 失败: {}", error.getMessage());
            return Map.of("raw", metadata);
        }
    }
}
