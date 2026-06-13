package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.response.ConversationResponse;
import com.anjing.chat.repository.ConversationRepository;
import com.anjing.model.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatConversationLifecycleServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatConversationConfigService configService = mock(ChatConversationConfigService.class);
    private final ChatMessagePersistenceService messagePersistenceService = mock(ChatMessagePersistenceService.class);
    private final ChatConversationLifecycleService lifecycleService = new ChatConversationLifecycleService(
            conversationRepository,
            configService,
            messagePersistenceService
    );

    @Test
    void createConversationShouldInitializeAndPersistConversation() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle("RAG chat");
        request.setDescription("教学会话");
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation conversation = lifecycleService.createConversation(request);

        assertThat(conversation.getConversationId()).startsWith("conv_");
        assertThat(conversation.getTitle()).isEqualTo("RAG chat");
        assertThat(conversation.getDescription()).isEqualTo("教学会话");
        assertThat(conversation.getMessageCount()).isZero();
        assertThat(conversation.getIsDeleted()).isFalse();
        verify(configService).applyCreateRequest(conversation, request);
        verify(conversationRepository).save(conversation);
    }

    @Test
    void createConversationShouldDefaultBlankTitle() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setTitle(" ");
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation conversation = lifecycleService.createConversation(request);

        assertThat(conversation.getTitle()).isEqualTo("新会话");
    }

    @Test
    void requireConversationShouldFailWhenConversationMissing() {
        when(conversationRepository.findByConversationIdAndIsDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lifecycleService.requireConversation("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话不存在");
    }

    @Test
    void listConversationsShouldMapPageToResponses() {
        Conversation conversation = new Conversation();
        conversation.setConversationId("conv_001");
        conversation.setTitle("RAG chat");
        when(conversationRepository.findByIsDeletedFalseOrderByUpdatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(conversation)));

        Page<ConversationResponse> page = lifecycleService.listConversations(1, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getConversationId()).isEqualTo("conv_001");
    }

    @Test
    void deleteConversationShouldSoftDeleteAndDeleteMessages() {
        Conversation conversation = conversation("conv_001");
        when(conversationRepository.findByConversationIdAndIsDeletedFalse("conv_001"))
                .thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        lifecycleService.deleteConversation("conv_001");

        assertThat(conversation.getIsDeleted()).isTrue();
        verify(conversationRepository).save(conversation);
        verify(messagePersistenceService).deleteConversationMessages("conv_001");
    }

    @Test
    void updateTitleShouldPersistNewTitle() {
        Conversation conversation = conversation("conv_001");
        when(conversationRepository.findByConversationIdAndIsDeletedFalse("conv_001"))
                .thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Conversation updated = lifecycleService.updateTitle("conv_001", "新标题");

        assertThat(updated.getTitle()).isEqualTo("新标题");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void incrementMessageCountShouldPersistConversation() {
        Conversation conversation = conversation("conv_001");
        conversation.setMessageCount(3);

        lifecycleService.incrementMessageCount(conversation, 2);

        assertThat(conversation.getMessageCount()).isEqualTo(5);
        verify(conversationRepository).save(conversation);
    }

    private Conversation conversation(String conversationId) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setTitle("RAG chat");
        conversation.setMessageCount(0);
        conversation.setIsDeleted(false);
        return conversation;
    }
}
