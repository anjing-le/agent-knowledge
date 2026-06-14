package com.anjing.knowledge.service;

import java.util.List;

/**
 * Keyword recall boundary used by hybrid retrieval.
 */
public interface KeywordSearchProvider {

    List<KeywordSearchHit> search(String query, List<String> kbIds, int candidateCount);

    record KeywordSearchHit(String chunkId, String kbId, String content, float score) {
    }
}
