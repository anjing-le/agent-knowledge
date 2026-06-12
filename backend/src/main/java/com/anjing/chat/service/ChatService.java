package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.entity.Message;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.repository.ConversationRepository;
import com.anjing.chat.repository.MessageRepository;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.LLMService;
import com.anjing.knowledge.service.RetrievalService;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import com.anjing.util.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

/**
 * 聊天服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RetrievalService retrievalService;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    private static final AtomicInteger CONV_COUNTER = new AtomicInteger(0);
    private static final AtomicInteger MSG_COUNTER = new AtomicInteger(0);
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
        
        if (request.getKbIds() != null && !request.getKbIds().isEmpty()) {
            conversation.setKbIds(toJson(request.getKbIds()));
        }
        
        if (request.getConfig() != null) {
            conversation.setConfig(toJson(request.getConfig()));
        }
        
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
        messageRepository.deleteByConversationId(conversationId);

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
        Message userMessage = saveMessage(conversation.getConversationId(), "user", request.getContent());

        // 确定知识库：请求显式传了 kbIds 就用请求的（即使为空也代表用户明确不选），否则用会话配置的
        List<String> kbIds;
        if (request.getKbIds() != null) {
            kbIds = request.getKbIds();
        } else if (StringUtils.isNotBlank(conversation.getKbIds())) {
            kbIds = fromJsonList(conversation.getKbIds());
        } else {
            kbIds = new ArrayList<>();
        }

        // 同步更新会话的 kbIds
        conversation.setKbIds(kbIds.isEmpty() ? null : toJson(kbIds));

        // 执行知识检索
        List<SearchResult> searchResults = new ArrayList<>();
        if (kbIds != null && !kbIds.isEmpty()) {
            searchResults = retrieveKnowledge(request.getContent(), kbIds);
        }

        // 生成AI响应
        String aiResponse = generateResponse(request.getContent(), searchResults, conversation);

        // 保存AI消息
        Message aiMessage = saveMessage(conversation.getConversationId(), "assistant", aiResponse);
        
        // 保存引用信息
        if (!searchResults.isEmpty()) {
            aiMessage.setReferences(toJson(searchResults));
            messageRepository.save(aiMessage);
        }

        // 更新会话消息数量
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversationRepository.save(conversation);

        return MessageResponse.fromEntity(aiMessage);
    }

    /**
     * 获取会话消息历史
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String conversationId) {
        // 验证会话存在
        conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));

        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        return messages.stream()
                .map(MessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 保存消息
     */
    private Message saveMessage(String conversationId, String role, String content) {
        Integer maxSequence = messageRepository.getMaxSequence(conversationId);
        
        Message message = new Message();
        message.setMessageId(generateMessageId());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSequence(maxSequence + 1);
        message.setCreatedAt(DateUtils.nowLocalDateTime());

        return messageRepository.save(message);
    }

    /**
     * 执行知识检索
     */
    private List<SearchResult> retrieveKnowledge(String query, List<String> kbIds) {
        try {
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.setQuery(query);
            searchRequest.setKbIds(kbIds);
            searchRequest.setTopK(5);

            return retrievalService.search(searchRequest);
        } catch (Exception e) {
            log.error("知识检索失败: query={}, error={}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 生成AI响应（带对话历史）
     */
    private String generateResponse(String userMessage, List<SearchResult> searchResults, Conversation conversation) {
        List<Map<String, String>> historyMessages = buildHistoryMessages(conversation.getConversationId());
        return llmService.generateRAGResponse(userMessage, searchResults, historyMessages);
    }

    /**
     * 构建对话历史（取最近10轮对话，即20条消息）
     */
    private List<Map<String, String>> buildHistoryMessages(String conversationId) {
        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceAsc(conversationId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        int maxMessages = 20;
        int start = Math.max(0, messages.size() - maxMessages);
        List<Map<String, String>> history = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            Message msg = messages.get(i);
            history.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        log.info("加载对话历史: conversationId={}, historyCount={}", conversationId, history.size());
        return history;
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

    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = MSG_COUNTER.incrementAndGet();
        return String.format("msg_%s_%04d", dateStr, counter);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON 反序列化失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
