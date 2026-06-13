package com.anjing.chat.service;

import com.anjing.chat.model.entity.Message;
import com.anjing.chat.model.response.MessageResponse;
import com.anjing.chat.repository.MessageRepository;
import com.anjing.knowledge.model.response.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessagePersistenceServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final ChatMessagePersistenceService persistenceService = new ChatMessagePersistenceService(
            messageRepository,
            new ObjectMapper()
    );

    @Test
    void saveUserMessageShouldCreateSequentialUserMessage() {
        when(messageRepository.getMaxSequence("conv_001")).thenReturn(0);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message message = persistenceService.saveUserMessage("conv_001", "你好");

        assertThat(message.getMessageId()).startsWith("msg_");
        assertThat(message.getConversationId()).isEqualTo("conv_001");
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("你好");
        assertThat(message.getSequence()).isEqualTo(1);
        assertThat(message.getCreatedAt()).isNotNull();
        verify(messageRepository).save(message);
    }

    @Test
    void saveAssistantMessageShouldPersistCitationReferences() {
        SearchResult reference = new SearchResult();
        reference.setChunkId("chunk_001");
        reference.setDocName("脚手架到 RAG.pdf");
        reference.setFinalScore(0.92f);

        when(messageRepository.getMaxSequence("conv_001")).thenReturn(1);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message message = persistenceService.saveAssistantMessage("conv_001", "基于知识库的回答", List.of(reference));

        assertThat(message.getRole()).isEqualTo("assistant");
        assertThat(message.getSequence()).isEqualTo(2);
        assertThat(message.getReferences())
                .contains("chunk_001")
                .contains("脚手架到 RAG.pdf")
                .contains("0.92");
        verify(messageRepository).save(message);
    }

    @Test
    void listMessagesShouldMapMessagesToResponses() {
        Message message = new Message();
        message.setMessageId("msg_001");
        message.setConversationId("conv_001");
        message.setRole("assistant");
        message.setContent("回答");

        when(messageRepository.findByConversationIdOrderBySequenceAsc("conv_001")).thenReturn(List.of(message));

        List<MessageResponse> responses = persistenceService.listMessages("conv_001");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getMessageId()).isEqualTo("msg_001");
        assertThat(responses.get(0).getContent()).isEqualTo("回答");
    }

    @Test
    void deleteConversationMessagesShouldDelegateToRepository() {
        when(messageRepository.deleteByConversationId("conv_001")).thenReturn(2);

        int deleted = persistenceService.deleteConversationMessages("conv_001");

        assertThat(deleted).isEqualTo(2);
        verify(messageRepository).deleteByConversationId("conv_001");
    }
}
