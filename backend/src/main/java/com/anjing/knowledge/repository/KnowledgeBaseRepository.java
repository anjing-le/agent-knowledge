package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库Repository
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, String> {

    /**
     * 根据ID查询未删除的知识库
     */
    Optional<KnowledgeBase> findByKbIdAndIsDeletedFalse(String kbId);

    /**
     * 根据名称查询未删除的知识库
     */
    Optional<KnowledgeBase> findByNameAndIsDeletedFalse(String name);

    /**
     * 检查名称是否已存在
     */
    boolean existsByNameAndIsDeletedFalse(String name);

    /**
     * 检查名称是否已存在（排除自身）
     */
    @Query("SELECT COUNT(kb) > 0 FROM KnowledgeBase kb WHERE kb.name = :name AND kb.kbId != :kbId AND kb.isDeleted = false")
    boolean existsByNameAndKbIdNotAndIsDeletedFalse(@Param("name") String name, @Param("kbId") String kbId);

    /**
     * 分页查询未删除的知识库
     */
    Page<KnowledgeBase> findByIsDeletedFalse(Pageable pageable);

    /**
     * 根据关键词搜索知识库
     */
    @Query("SELECT kb FROM KnowledgeBase kb WHERE kb.isDeleted = false AND " +
           "(kb.name LIKE %:keyword% OR kb.description LIKE %:keyword%)")
    Page<KnowledgeBase> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 查询所有未删除的知识库
     */
    List<KnowledgeBase> findByIsDeletedFalseOrderByCreatedAtDesc();

    /**
     * 根据类型查询知识库
     */
    List<KnowledgeBase> findByKbTypeAndIsDeletedFalse(String kbType);

    /**
     * 统计未删除的知识库数量
     */
    long countByIsDeletedFalse();
}

