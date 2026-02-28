package com.anjing.chat.repository;

import com.anjing.chat.model.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 会话Repository
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    /**
     * 根据ID查询未删除的会话
     */
    Optional<Conversation> findByConversationIdAndIsDeletedFalse(String conversationId);

    /**
     * 分页查询未删除的会话
     */
    Page<Conversation> findByIsDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * 查询所有未删除的会话
     */
    List<Conversation> findByIsDeletedFalseOrderByUpdatedAtDesc();

    /**
     * 统计未删除的会话数量
     */
    long countByIsDeletedFalse();
}

