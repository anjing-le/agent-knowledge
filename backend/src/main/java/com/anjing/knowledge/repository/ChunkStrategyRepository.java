package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.ChunkStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 分块策略Repository
 */
@Repository
public interface ChunkStrategyRepository extends JpaRepository<ChunkStrategy, String> {

    /**
     * 查询启用的策略列表
     */
    List<ChunkStrategy> findByIsEnabledTrueOrderByCreatedAtDesc();

    /**
     * 查询默认策略
     */
    Optional<ChunkStrategy> findByIsDefaultTrueAndIsEnabledTrue();

    /**
     * 根据分块方法查询策略
     */
    List<ChunkStrategy> findByChunkMethodAndIsEnabledTrue(String chunkMethod);
}

