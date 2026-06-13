package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.repository.ConversationRepository;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import com.anjing.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatConversationConfigService chatConversationConfigService;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final RagChatOrchestrationService ragChatOrchestrationService;

    private static final AtomicInteger CONV_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 创建会话
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationResponse createConversation(CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(generateConversationId());
        conversation.setTitle(StringUtils.defaultIfBlank(request.getTitle(), "新会话"));
        conversation.setDescription(request.getDescription());
        chatConversationConfigService.applyCreateRequest(conversation, request);
        
        conversation.setMessageCount(0);
        conversation.setIsDeleted(false);

        conversation = conversationRepository.save(conversation);
        log.info("创建会话成功: conversationId={}", conversation.getConversationId());

        return ConversationResponse.fromEntity(conversation);
    }

    /**
     * 获取会话详情
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String conversationId) {
        Conversation conversation = conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));
        return ConversationResponse.fromEntity(conversation);
    }

    /**
     * 获取会话列表
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Conversation> conversationPage = conversationRepository.findByIsDeletedFalseOrderByUpdatedAtDesc(pageable);
        return conversationPage.map(ConversationResponse::fromEntity);
    }

    /**
     * 删除会话
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId) {
        Conversation conversation = conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));

        conversation.setIsDeleted(true);
        conversationRepository.save(conversation);

        // 删除关联的消息
        chatMessagePersistenceService.deleteConversationMessages(conversationId);

        log.info("删除会话成功: conversationId={}", conversationId);
    }

    /**
     * 发送消息（非流式）
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageResponse sendMessage(SendMessageRequest request) {
        // 获取会话
        Conversation conversation = conversationRepository.findByConversationIdAndIsDeletedFalse(request.getConversationId())
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 保存用户消息
        chatMessagePersistenceService.saveUserMessage(conversation.getConversationId(), request.getContent());

        // 确定知识库：请求显式传了 kbIds 就用请求的（即使为空也代表用户明确不选），否则用会话配置的
        List<String> kbIds = chatConversationConfigService.resolveKnowledgeBaseIds(request, conversation);

        // 同步更新会话的 kbIds
        chatConversationConfigService.syncKnowledgeBaseIds(conversation, kbIds);

        RagChatOrchestrationService.RagChatAnswer ragAnswer = ragChatOrchestrationService.generateAnswer(
                conversation.getConversationId(),
                request.getContent(),
                kbIds
        );

        MessageResponse aiMessage = MessageResponse.fromEntity(chatMessagePersistenceService.saveAssistantMessage(
                conversation.getConversationId(),
                ragAnswer.content(),
                ragAnswer.references()
        ));

        // 更新会话消息数量
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversationRepository.save(conversation);

        return aiMessage;
    }

    /**
     * 获取会话消息历史
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String conversationId) {
        // 验证会话存在
        conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));

        return chatMessagePersistenceService.listMessages(conversationId);
    }

    /**
     * 更新会话标题
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationResponse updateConversationTitle(String conversationId, String title) {
        Conversation conversation = conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));

        conversation.setTitle(title);
        conversation = conversationRepository.save(conversation);

        return ConversationResponse.fromEntity(conversation);
    }

    /**
     * 生成会话ID
     */
    private String generateConversationId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = CONV_COUNTER.incrementAndGet();
        return String.format("conv_%s_%04d", dateStr, counter);
    }

}
