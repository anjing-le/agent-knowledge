package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.ParsingStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 解析策略Repository
 */
@Repository
public interface ParsingStrategyRepository extends JpaRepository<ParsingStrategy, String> {

    /**
     * 查询启用的策略列表
     */
    List<ParsingStrategy> findByIsEnabledTrueOrderByCreatedAtDesc();

    /**
     * 查询默认策略
     */
    Optional<ParsingStrategy> findByIsDefaultTrueAndIsEnabledTrue();

    /**
     * 根据解析器类型查询策略
     */
    List<ParsingStrategy> findByParserTypeAndIsEnabledTrue(String parserType);
}

