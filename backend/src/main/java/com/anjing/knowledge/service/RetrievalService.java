package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 知识检索服务
 * 
 * 核心功能：
 * 1. 向量相似度搜索（基于内存向量库 / 可扩展为 Milvus）
 * 2. 可选的 Rerank 重排序
 * 3. 结果过滤和排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_CANDIDATE_COUNT = 20;
    private static final float DEFAULT_SIMILARITY_THRESHOLD = 0.3f;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RetrievalResultEnrichmentService resultEnrichmentService;

    /**
     * 执行知识检索
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(SearchRequest request) {
        log.info("开始知识检索: query={}, kbIds={}, topK={}",
                request.getQuery(), request.getKbIds(), effectiveTopK(request));

        // 1. 验证知识库
        validateKnowledgeBases(request.getKbIds());

        // 2. 获取查询向量
        List<Float> queryVector = embeddingService.embed(request.getQuery());

        if (queryVector == null || queryVector.isEmpty()) {
            throw new BizException("Embedding 服务不可用，无法生成查询向量，请检查 API Key 配置", CommonErrorCode.SYSTEM_ERROR);
        }

        // 3. 向量搜索
        List<SearchResult> results = vectorSearch(request, queryVector);

        // 4. 如果启用Rerank，进行重排序
        if (Boolean.TRUE.equals(request.getRerank()) && !results.isEmpty()) {
            results = rerankResults(request.getQuery(), results, request.getRerankLlmId());
        }

        // 5. 过滤和限制结果数量
        results = filterAndLimit(results, request);

        log.info("知识检索完成: resultCount={}", results.size());
        return results;
    }

    /**
     * 向量相似度搜索
     */
    private List<SearchResult> vectorSearch(SearchRequest request, List<Float> queryVector) {
        List<VectorStoreService.VectorSearchResult> vectorResults =
                vectorStoreService.search(request.getKbIds(), queryVector, effectiveCandidateCount(request));

        return resultEnrichmentService.enrich(vectorResults);
    }

    /**
     * 验证知识库
     */
    private void validateKnowledgeBases(List<String> kbIds) {
        for (String kbId : kbIds) {
            KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                    .orElseThrow(() -> new BizException("知识库不存在: " + kbId,
                            CommonErrorCode.DATA_NOT_FOUND));
            
            if (!kb.getIsEnabled()) {
                throw new BizException("知识库已禁用: " + kbId,
                        CommonErrorCode.PARAM_INVALID);
            }
        }
    }

    /**
     * Rerank重排序
     */
    private List<SearchResult> rerankResults(String query, List<SearchResult> results, String rerankLlmId) {
        log.info("执行Rerank重排序: query={}, candidateCount={}", query, results.size());
        
        // TODO: 调用Rerank服务进行重排序
        // List<Float> rerankScores = rerankUtil.rerank(query, 
        //         results.stream().map(SearchResult::getContent).collect(Collectors.toList()),
        //         rerankLlmId);
        
        // 临时实现：保持原有顺序
        return results;
    }

    /**
     * 过滤和限制结果
     */
    private List<SearchResult> filterAndLimit(List<SearchResult> results, SearchRequest request) {
        float similarityThreshold = effectiveSimilarityThreshold(request);
        int topK = effectiveTopK(request);

        if (!results.isEmpty()) {
            log.info("过滤前结果: count={}, scoreRange=[{} ~ {}], threshold={}",
                    results.size(),
                    results.stream().map(result -> scoreOrZero(result.getFinalScore())).min(Float::compare).orElse(0f),
                    results.stream().map(result -> scoreOrZero(result.getFinalScore())).max(Float::compare).orElse(0f),
                    similarityThreshold);
        }

        List<SearchResult> filtered = results.stream()
                .filter(r -> scoreOrZero(r.getFinalScore()) >= similarityThreshold)
                .filter(r -> request.getExcludeChunkIds() == null || 
                        !request.getExcludeChunkIds().contains(r.getChunkId()))
                .filter(r -> request.getExcludeDocIds() == null || 
                        !request.getExcludeDocIds().contains(r.getDocId()))
                .sorted((left, right) -> Float.compare(
                        scoreOrZero(right.getFinalScore()),
                        scoreOrZero(left.getFinalScore())
                ))
                .limit(topK)
                .collect(Collectors.toList());

        annotateScoreExplanations(filtered, request, similarityThreshold);

        log.info("过滤后结果: count={}", filtered.size());
        return filtered;
    }

    private void annotateScoreExplanations(List<SearchResult> results, SearchRequest request, float threshold) {
        for (int index = 0; index < results.size(); index++) {
            SearchResult result = results.get(index);
            int rank = index + 1;
            result.setRank(rank);
            result.setScoreExplanation(String.format(Locale.ROOT,
                    "rank=%d final=%.4f similarity=%.4f rerank=%s threshold=%.4f",
                    rank,
                    scoreOrZero(result.getFinalScore()),
                    scoreOrZero(result.getSimilarityScore()),
                    rerankExplanation(result, request),
                    threshold));
        }
    }

    private String rerankExplanation(SearchResult result, SearchRequest request) {
        if (result.getRerankScore() != null) {
            return String.format(Locale.ROOT, "%.4f", result.getRerankScore());
        }
        return Boolean.TRUE.equals(request.getRerank()) ? "enabled-no-score" : "disabled";
    }

    private int effectiveTopK(SearchRequest request) {
        Integer topK = request.getTopK();
        if (topK == null || topK < 1) {
            return DEFAULT_TOP_K;
        }
        return topK;
    }

    private int effectiveCandidateCount(SearchRequest request) {
        Integer candidateCount = request.getCandidateCount();
        int effectiveCandidateCount = candidateCount == null || candidateCount < 1
                ? DEFAULT_CANDIDATE_COUNT
                : candidateCount;
        return Math.max(effectiveTopK(request), effectiveCandidateCount);
    }

    private float effectiveSimilarityThreshold(SearchRequest request) {
        Float threshold = request.getSimilarityThreshold();
        if (threshold == null) {
            return DEFAULT_SIMILARITY_THRESHOLD;
        }
        return Math.max(0f, Math.min(1f, threshold));
    }

    private float scoreOrZero(Float score) {
        return score == null ? 0f : score;
    }
}
