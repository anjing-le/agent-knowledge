package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.KnowledgeBase;
import com.anjing.knowledge.model.request.CreateKnowledgeBaseRequest;
import com.anjing.knowledge.model.request.UpdateKnowledgeBaseRequest;
import com.anjing.knowledge.model.response.KnowledgeBaseResponse;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 知识库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;

    // 用于生成KB ID的计数器
    private static final AtomicInteger KB_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 创建知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        // 检查名称是否已存在
        if (knowledgeBaseRepository.existsByNameAndIsDeletedFalse(request.getName())) {
            throw new BizException("知识库名称已存在", CommonErrorCode.PARAM_INVALID);
        }

        // 创建实体
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbId(generateKbId());
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setAvatar(request.getAvatar());
        kb.setEmbeddingModel(StringUtils.defaultIfBlank(request.getEmbeddingModel(), "text-embedding-3-small"));
        kb.setChunkSize(request.getChunkSize() != null ? request.getChunkSize() : 500);
        kb.setChunkOverlap(request.getChunkOverlap() != null ? request.getChunkOverlap() : 50);
        kb.setKbType(StringUtils.defaultIfBlank(request.getKbType(), ""));
        kb.setRaptorEnabled(request.getRaptorEnabled() != null ? request.getRaptorEnabled() : false);
        kb.setRaptorConfig(request.getRaptorConfig());
        kb.setIsEnabled(true);
        kb.setIsDeleted(false);
        kb.setCreatedAt(LocalDateTime.now());
        kb.setUpdatedAt(LocalDateTime.now());

        kb = knowledgeBaseRepository.save(kb);
        log.info("创建知识库成功: kbId={}, name={}", kb.getKbId(), kb.getName());

        return KnowledgeBaseResponse.fromEntity(kb);
    }

    /**
     * 更新知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse updateKnowledgeBase(String kbId, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new BizException("知识库不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 检查名称是否冲突
        if (StringUtils.isNotBlank(request.getName()) && !request.getName().equals(kb.getName())) {
            if (knowledgeBaseRepository.existsByNameAndKbIdNotAndIsDeletedFalse(request.getName(), kbId)) {
                throw new BizException("知识库名称已存在", CommonErrorCode.PARAM_INVALID);
            }
            kb.setName(request.getName());
        }

        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        if (request.getAvatar() != null) {
            kb.setAvatar(request.getAvatar());
        }
        if (request.getEmbeddingModel() != null) {
            kb.setEmbeddingModel(request.getEmbeddingModel());
        }
        if (request.getChunkSize() != null) {
            kb.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            kb.setChunkOverlap(request.getChunkOverlap());
        }
        if (request.getRaptorEnabled() != null) {
            kb.setRaptorEnabled(request.getRaptorEnabled());
        }
        if (request.getRaptorConfig() != null) {
            kb.setRaptorConfig(request.getRaptorConfig());
        }
        if (request.getIsEnabled() != null) {
            kb.setIsEnabled(request.getIsEnabled());
        }

        kb = knowledgeBaseRepository.save(kb);
        log.info("更新知识库成功: kbId={}", kb.getKbId());

        return buildResponse(kb);
    }

    /**
     * 删除知识库（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(String kbId) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new BizException("知识库不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 软删除知识库
        kb.setIsDeleted(true);
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseRepository.save(kb);

        // 软删除关联的文档
        documentRepository.softDeleteByKbId(kbId);

        log.info("删除知识库成功: kbId={}", kbId);
    }

    /**
     * 获取知识库详情
     */
    @Transactional(readOnly = true)
    public KnowledgeBaseResponse getKnowledgeBase(String kbId) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new BizException("知识库不存在", CommonErrorCode.DATA_NOT_FOUND));

        return buildResponse(kb);
    }

    /**
     * 分页查询知识库列表
     */
    @Transactional(readOnly = true)
    public Page<KnowledgeBaseResponse> listKnowledgeBases(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<KnowledgeBase> kbPage;
        if (StringUtils.isNotBlank(keyword)) {
            kbPage = knowledgeBaseRepository.searchByKeyword(keyword, pageable);
        } else {
            kbPage = knowledgeBaseRepository.findByIsDeletedFalse(pageable);
        }

        return kbPage.map(this::buildResponse);
    }

    /**
     * 获取所有知识库列表（不分页）
     */
    @Transactional(readOnly = true)
    public List<KnowledgeBaseResponse> listAllKnowledgeBases() {
        return knowledgeBaseRepository.findByIsDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
    }

    /**
     * 构建响应对象（包含统计信息）
     */
    private KnowledgeBaseResponse buildResponse(KnowledgeBase kb) {
        KnowledgeBaseResponse response = KnowledgeBaseResponse.fromEntity(kb);
        
        response.setDocumentCount(documentRepository.countByKbIdAndIsDeletedFalse(kb.getKbId()));
        response.setChunkCount(chunkRepository.countByKbId(kb.getKbId()));
        
        return response;
    }

    /**
     * 生成知识库ID
     * 格式：kb_yyyyMMdd_序号
     */
    private String generateKbId() {
        String dateStr = LocalDateTime.now().format(DATE_FORMAT);
        int counter = KB_COUNTER.incrementAndGet();
        return String.format("kb_%s_%04d", dateStr, counter);
    }
}

