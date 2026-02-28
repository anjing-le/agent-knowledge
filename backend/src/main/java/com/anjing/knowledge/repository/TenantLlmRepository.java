package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.TenantLlm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LLM模型配置Repository
 */
@Repository
public interface TenantLlmRepository extends JpaRepository<TenantLlm, String> {

    /**
     * 根据模型类型查询启用的模型
     */
    List<TenantLlm> findByModelTypeAndIsEnabledTrue(String modelType);

    /**
     * 查询默认的embedding模型
     */
    Optional<TenantLlm> findByModelTypeAndIsDefaultTrueAndIsEnabledTrue(String modelType);

    /**
     * 根据模型名称查询
     */
    Optional<TenantLlm> findByLlmNameAndIsEnabledTrue(String llmName);

    /**
     * 根据模型类型和提供商查询
     */
    List<TenantLlm> findByModelTypeAndModelFactoryAndIsEnabledTrue(String modelType, String modelFactory);

    /**
     * 查询所有启用的模型
     */
    List<TenantLlm> findByIsEnabledTrueOrderByModelTypeAscCreatedAtDesc();
}

