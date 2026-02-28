package com.anjing.chat.repository;

import com.anjing.chat.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 消息Repository
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    /**
     * 根据会话ID查询消息，按时间排序
     */
    List<Message> findByConversationIdOrderBySequenceAsc(String conversationId);

    /**
     * 根据会话ID分页查询消息
     */
    Page<Message> findByConversationIdOrderBySequenceAsc(String conversationId, Pageable pageable);

    /**
     * 获取会话的最近N条消息
     */
    List<Message> findTop10ByConversationIdOrderBySequenceDesc(String conversationId);

    /**
     * 统计会话的消息数量
     */
    long countByConversationId(String conversationId);

    /**
     * 获取会话的最大序号
     */
    @Query("SELECT COALESCE(MAX(m.sequence), 0) FROM Message m WHERE m.conversationId = :conversationId")
    Integer getMaxSequence(@Param("conversationId") String conversationId);

    /**
     * 删除会话的所有消息
     */
    @Modifying
    @Query("DELETE FROM Message m WHERE m.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") String conversationId);
}

