package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 聊天服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationLifecycleService chatConversationLifecycleService;
    private final ChatConversationConfigService chatConversationConfigService;
    private final ChatMessagePersistenceService chatMessagePersistenceService;
    private final RagChatOrchestrationService ragChatOrchestrationService;

    /**
     * 创建会话
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationResponse createConversation(CreateConversationRequest request) {
        return ConversationResponse.fromEntity(chatConversationLifecycleService.createConversation(request));
    }

    /**
     * 获取会话详情
     */
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String conversationId) {
        return ConversationResponse.fromEntity(chatConversationLifecycleService.requireConversation(conversationId));
    }

    /**
     * 获取会话列表
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(int page, int size) {
        return chatConversationLifecycleService.listConversations(page, size);
    }

    /**
     * 删除会话
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId) {
        chatConversationLifecycleService.deleteConversation(conversationId);
    }

    /**
     * 发送消息（非流式）
     */
    @Transactional(rollbackFor = Exception.class)
    public MessageResponse sendMessage(SendMessageRequest request) {
        // 获取会话
        Conversation conversation = chatConversationLifecycleService.requireConversation(request.getConversationId());

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
        chatConversationLifecycleService.incrementMessageCount(conversation, 2);

        return aiMessage;
    }

    /**
     * 获取会话消息历史
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String conversationId) {
        // 验证会话存在
        chatConversationLifecycleService.requireConversation(conversationId);

        return chatMessagePersistenceService.listMessages(conversationId);
    }

    /**
     * 更新会话标题
     */
    @Transactional(rollbackFor = Exception.class)
    public ConversationResponse updateConversationTitle(String conversationId, String title) {
        return ConversationResponse.fromEntity(chatConversationLifecycleService.updateTitle(conversationId, title));
    }

}
