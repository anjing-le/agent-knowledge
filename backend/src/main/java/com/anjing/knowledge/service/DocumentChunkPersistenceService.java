package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Persists generated chunks and updates document chunk/token statistics.
 */
@Service
@RequiredArgsConstructor
public class DocumentChunkPersistenceService {

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public PersistedChunks saveChunks(Document document, List<Chunk> chunks) {
        chunkRepository.saveAll(chunks);

        int totalTokens = chunks.stream()
                .mapToInt(chunk -> chunk.getTokenCount() != null ? chunk.getTokenCount() : 0)
                .sum();
        document.setChunkNum(chunks.size());
        document.setTokenNum(totalTokens);
        documentRepository.save(document);

        return new PersistedChunks(chunks.size(), totalTokens);
    }

    public record PersistedChunks(int chunkCount, int totalTokens) {
    }
}
