package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Chunk;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.model.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Chunk query and state management.
 */
@Service
@RequiredArgsConstructor
public class ChunkService {

    private final ChunkRepository chunkRepository;

    @Transactional(readOnly = true)
    public PageResult<Chunk> listChunks(String docId, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(current - 1, pageSize);
        Page<Chunk> chunkPage = chunkRepository.findByDocIdOrderByChunkIndexAsc(docId, pageable);
        return PageResult.of(
                chunkPage.getContent(),
                chunkPage.getTotalElements(),
                chunkPage.getNumber() + 1,
                chunkPage.getSize()
        );
    }

    @Transactional(readOnly = true)
    public Optional<Chunk> getChunk(String chunkId) {
        return chunkRepository.findById(chunkId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateChunkEnabled(String chunkId, Boolean enabled) {
        Optional<Chunk> optionalChunk = chunkRepository.findById(chunkId);
        if (optionalChunk.isEmpty()) {
            return false;
        }

        Chunk chunk = optionalChunk.get();
        chunk.setIsEnabled(Boolean.TRUE.equals(enabled));
        chunkRepository.save(chunk);
        return true;
    }

    @Transactional(readOnly = true)
    public long countByDocument(String docId) {
        return chunkRepository.countByDocId(docId);
    }
}
