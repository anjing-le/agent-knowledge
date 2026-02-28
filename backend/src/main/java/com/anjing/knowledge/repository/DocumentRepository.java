package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 文档Repository
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    /**
     * 根据ID查询未删除的文档
     */
    Optional<Document> findByDocIdAndIsDeletedFalse(String docId);

    /**
     * 根据知识库ID查询未删除的文档
     */
    List<Document> findByKbIdAndIsDeletedFalseOrderByCreatedAtDesc(String kbId);

    /**
     * 根据知识库ID分页查询未删除的文档
     */
    Page<Document> findByKbIdAndIsDeletedFalse(String kbId, Pageable pageable);

    /**
     * 根据知识库ID和状态查询文档
     */
    List<Document> findByKbIdAndStatusAndIsDeletedFalse(String kbId, String status);

    /**
     * 根据知识库ID和关键词搜索文档
     */
    @Query("SELECT d FROM Document d WHERE d.kbId = :kbId AND d.isDeleted = false AND d.docName LIKE %:keyword%")
    Page<Document> searchByKeyword(@Param("kbId") String kbId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 统计知识库下的文档数量
     */
    long countByKbIdAndIsDeletedFalse(String kbId);

    /**
     * 统计知识库下特定状态的文档数量
     */
    long countByKbIdAndStatusAndIsDeletedFalse(String kbId, String status);

    /**
     * 批量软删除文档
     */
    @Transactional
    @Modifying
    @Query("UPDATE Document d SET d.isDeleted = true WHERE d.kbId = :kbId")
    int softDeleteByKbId(@Param("kbId") String kbId);

    /**
     * 根据解析任务ID查询文档
     */
    Optional<Document> findByParserTaskIdAndIsDeletedFalse(String parserTaskId);

    /**
     * 查询指定状态的文档（用于任务处理）
     */
    List<Document> findByStatusInAndIsDeletedFalse(List<String> statuses);

    /**
     * 统计所有知识库的文档总数
     */
    long countByIsDeletedFalse();

    /**
     * 统计所有知识库的chunk总数
     */
    @Query("SELECT COALESCE(SUM(d.chunkNum), 0) FROM Document d WHERE d.isDeleted = false")
    Long sumChunkNum();

    /**
     * 统计所有知识库的token总数
     */
    @Query("SELECT COALESCE(SUM(d.tokenNum), 0) FROM Document d WHERE d.isDeleted = false")
    Long sumTokenNum();
}

