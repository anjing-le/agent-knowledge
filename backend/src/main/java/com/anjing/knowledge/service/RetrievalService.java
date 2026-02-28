package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /**
     * 执行知识检索
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(SearchRequest request) {
        log.info("开始知识检索: query={}, kbIds={}, topK={}", 
                request.getQuery(), request.getKbIds(), request.getTopK());

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
                vectorStoreService.search(request.getKbIds(), queryVector, request.getCandidateCount());

        List<SearchResult> results = new ArrayList<>();
        for (VectorStoreService.VectorSearchResult vr : vectorResults) {
            SearchResult result = new SearchResult();
            result.setChunkId(vr.getChunkId());
            result.setKbId(vr.getKbId());
            result.setContent(vr.getContent());
            result.setSimilarityScore(vr.getScore());
            result.setFinalScore(vr.getScore());

            // 查找关联的 chunk 和文档信息
            chunkRepository.findById(vr.getChunkId()).ifPresent(chunk -> {
                result.setDocId(chunk.getDocId());
                documentRepository.findById(chunk.getDocId())
                        .ifPresent(doc -> result.setDocName(doc.getDocName()));
            });

            knowledgeBaseRepository.findById(vr.getKbId())
                    .ifPresent(kb -> result.setKbName(kb.getName()));

            results.add(result);
        }

        return results;
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
        if (!results.isEmpty()) {
            log.info("过滤前结果: count={}, scoreRange=[{} ~ {}], threshold={}",
                    results.size(),
                    results.stream().map(SearchResult::getFinalScore).min(Float::compare).orElse(0f),
                    results.stream().map(SearchResult::getFinalScore).max(Float::compare).orElse(0f),
                    request.getSimilarityThreshold());
        }

        List<SearchResult> filtered = results.stream()
                .filter(r -> r.getFinalScore() >= request.getSimilarityThreshold())
                .filter(r -> request.getExcludeChunkIds() == null || 
                        !request.getExcludeChunkIds().contains(r.getChunkId()))
                .filter(r -> request.getExcludeDocIds() == null || 
                        !request.getExcludeDocIds().contains(r.getDocId()))
                .limit(request.getTopK())
                .collect(Collectors.toList());

        log.info("过滤后结果: count={}", filtered.size());
        return filtered;
    }
}

