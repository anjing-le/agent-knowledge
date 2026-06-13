package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.response.ConversationResponse;
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

import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages chat conversation lifecycle and persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationLifecycleService {

    private static final AtomicInteger CONV_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ConversationRepository conversationRepository;
    private final ChatConversationConfigService chatConversationConfigService;
    private final ChatMessagePersistenceService chatMessagePersistenceService;

    public Conversation createConversation(CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(generateConversationId());
        conversation.setTitle(StringUtils.defaultIfBlank(request.getTitle(), "新会话"));
        conversation.setDescription(request.getDescription());
        chatConversationConfigService.applyCreateRequest(conversation, request);
        conversation.setMessageCount(0);
        conversation.setIsDeleted(false);

        Conversation saved = conversationRepository.save(conversation);
        log.info("创建会话成功: conversationId={}", saved.getConversationId());
        return saved;
    }

    public Conversation requireConversation(String conversationId) {
        return conversationRepository.findByConversationIdAndIsDeletedFalse(conversationId)
                .orElseThrow(() -> new BizException("会话不存在", CommonErrorCode.DATA_NOT_FOUND));
    }

    public Page<ConversationResponse> listConversations(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Conversation> conversationPage = conversationRepository.findByIsDeletedFalseOrderByUpdatedAtDesc(pageable);
        return conversationPage.map(ConversationResponse::fromEntity);
    }

    public void deleteConversation(String conversationId) {
        Conversation conversation = requireConversation(conversationId);
        conversation.setIsDeleted(true);
        conversationRepository.save(conversation);
        chatMessagePersistenceService.deleteConversationMessages(conversationId);
        log.info("删除会话成功: conversationId={}", conversationId);
    }

    public Conversation updateTitle(String conversationId, String title) {
        Conversation conversation = requireConversation(conversationId);
        conversation.setTitle(title);
        return conversationRepository.save(conversation);
    }

    public void incrementMessageCount(Conversation conversation, int delta) {
        conversation.setMessageCount(conversation.getMessageCount() + delta);
        conversationRepository.save(conversation);
    }

    private String generateConversationId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = CONV_COUNTER.incrementAndGet();
        return String.format("conv_%s_%04d", dateStr, counter);
    }
}
