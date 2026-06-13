package com.anjing.chat.service;

import com.anjing.chat.model.entity.Message;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.repository.MessageRepository;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.util.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Persists chat messages and citation references.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessagePersistenceService {

    private static final AtomicInteger MSG_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public Message saveUserMessage(String conversationId, String content) {
        return saveMessage(conversationId, "user", content, null);
    }

    public Message saveAssistantMessage(String conversationId, String content, List<SearchResult> references) {
        return saveMessage(conversationId, "assistant", content, references);
    }

    public List<MessageResponse> listMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderBySequenceAsc(conversationId).stream()
                .map(MessageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public int deleteConversationMessages(String conversationId) {
        return messageRepository.deleteByConversationId(conversationId);
    }

    private Message saveMessage(String conversationId, String role, String content, List<SearchResult> references) {
        Integer maxSequence = messageRepository.getMaxSequence(conversationId);

        Message message = new Message();
        message.setMessageId(generateMessageId());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSequence(maxSequence + 1);
        message.setCreatedAt(DateUtils.nowLocalDateTime());

        if (references != null && !references.isEmpty()) {
            message.setReferences(toJson(references));
        }

        return messageRepository.save(message);
    }

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
}
