package com.anjing.chat.service;

import com.anjing.chat.model.entity.Conversation;
import com.anjing.chat.model.request.CreateConversationRequest;
import com.anjing.chat.model.request.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatConversationConfigServiceTest {

    private final ChatConversationConfigService configService = new ChatConversationConfigService(new ObjectMapper());

    @Test
    void applyCreateRequestShouldSerializeKnowledgeBasesAndConfig() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setKbIds(List.of("kb_001", "kb_002"));
        CreateConversationRequest.ConversationConfig config = new CreateConversationRequest.ConversationConfig();
        config.setModelId("gpt-4o-mini");
        config.setTopK(8);
        request.setConfig(config);

        Conversation conversation = new Conversation();

        configService.applyCreateRequest(conversation, request);

        assertThat(conversation.getKbIds()).contains("kb_001").contains("kb_002");
        assertThat(conversation.getConfig()).contains("gpt-4o-mini").contains("\"topK\":8");
    }

    @Test
    void resolveKnowledgeBaseIdsShouldPreferRequestEvenWhenRequestIsEmpty() {
        Conversation conversation = new Conversation();
        conversation.setKbIds("[\"kb_old\"]");
        SendMessageRequest request = new SendMessageRequest();
        request.setKbIds(List.of());

        List<String> kbIds = configService.resolveKnowledgeBaseIds(request, conversation);

        assertThat(kbIds).isEmpty();
    }

    @Test
    void resolveKnowledgeBaseIdsShouldUseConversationWhenRequestOmitsKnowledgeBases() {
        Conversation conversation = new Conversation();
        conversation.setKbIds("[\"kb_001\",\"kb_002\"]");
        SendMessageRequest request = new SendMessageRequest();

        List<String> kbIds = configService.resolveKnowledgeBaseIds(request, conversation);

        assertThat(kbIds).containsExactly("kb_001", "kb_002");
    }

    @Test
    void resolveKnowledgeBaseIdsShouldReturnEmptyListWhenConversationJsonIsInvalid() {
        Conversation conversation = new Conversation();
        conversation.setKbIds("not-json");
        SendMessageRequest request = new SendMessageRequest();

        List<String> kbIds = configService.resolveKnowledgeBaseIds(request, conversation);

        assertThat(kbIds).isEmpty();
    }

    @Test
    void syncKnowledgeBaseIdsShouldClearEmptyKnowledgeBases() {
        Conversation conversation = new Conversation();
        conversation.setKbIds("[\"kb_old\"]");

        configService.syncKnowledgeBaseIds(conversation, List.of());

        assertThat(conversation.getKbIds()).isNull();
    }
}
