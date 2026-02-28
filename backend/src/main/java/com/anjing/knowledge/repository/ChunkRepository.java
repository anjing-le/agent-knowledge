package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.Chunk;
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
 * Chunk Repository
 */
@Repository
public interface ChunkRepository extends JpaRepository<Chunk, String> {

    /**
     * 根据文档ID查询chunks
     */
    List<Chunk> findByDocIdOrderByChunkIndexAsc(String docId);

    /**
     * 根据文档ID分页查询chunks
     */
    Page<Chunk> findByDocIdOrderByChunkIndexAsc(String docId, Pageable pageable);

    /**
     * 根据知识库ID查询chunks
     */
    List<Chunk> findByKbId(String kbId);

    /**
     * 根据知识库ID分页查询chunks
     */
    Page<Chunk> findByKbId(String kbId, Pageable pageable);

    /**
     * 根据向量化状态查询chunks
     */
    List<Chunk> findByEmbeddingStatus(Integer embeddingStatus);

    /**
     * 根据文档ID和向量化状态查询chunks
     */
    List<Chunk> findByDocIdAndEmbeddingStatus(String docId, Integer embeddingStatus);

    /**
     * 根据知识库ID和向量化状态查询chunks
     */
    List<Chunk> findByKbIdAndEmbeddingStatus(String kbId, Integer embeddingStatus);

    /**
     * 统计文档的chunk数量
     */
    long countByDocId(String docId);

    /**
     * 统计知识库的chunk数量
     */
    long countByKbId(String kbId);

    /**
     * 删除文档的所有chunks
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Chunk c WHERE c.docId = :docId")
    int deleteByDocId(@Param("docId") String docId);

    /**
     * 删除知识库的所有chunks
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Chunk c WHERE c.kbId = :kbId")
    int deleteByKbId(@Param("kbId") String kbId);

    /**
     * 根据内容搜索chunks（全文搜索）
     */
    @Query("SELECT c FROM Chunk c WHERE c.kbId = :kbId AND c.content LIKE %:keyword%")
    Page<Chunk> searchByContent(@Param("kbId") String kbId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * 批量更新向量化状态
     */
    @Transactional
    @Modifying
    @Query("UPDATE Chunk c SET c.embeddingStatus = :status WHERE c.chunkId IN :chunkIds")
    int batchUpdateEmbeddingStatus(@Param("chunkIds") List<String> chunkIds, @Param("status") Integer status);

    /**
     * 根据chunkId列表查询
     */
    List<Chunk> findByChunkIdIn(List<String> chunkIds);

    /**
     * 查询启用的chunks
     */
    List<Chunk> findByKbIdAndIsEnabledTrue(String kbId);
}

