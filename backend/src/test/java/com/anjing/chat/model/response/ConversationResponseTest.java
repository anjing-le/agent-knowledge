package com.anjing.chat.model.response;

import com.anjing.chat.model.entity.Conversation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationResponseTest {

    @Test
    void shouldParseKnowledgeBaseIdsAndConfig() {
        Conversation conversation = new Conversation();
        conversation.setConversationId("conv_001");
        conversation.setTitle("RAG chat");
        conversation.setKbIds("[\"kb_001\",\"kb_002\"]");
        conversation.setConfig("{\"topK\":5,\"rerank\":false}");

        ConversationResponse response = ConversationResponse.fromEntity(conversation);

        assertThat(response.getKbIds()).containsExactly("kb_001", "kb_002");
        assertThat(response.getConfig())
                .containsEntry("topK", 5)
                .containsEntry("rerank", false);
    }

    @Test
    void shouldReturnEmptyCollectionsWhenJsonFieldsAreBlankOrInvalid() {
        Conversation conversation = new Conversation();
        conversation.setConversationId("conv_002");
        conversation.setKbIds("not-json");
        conversation.setConfig("");

        ConversationResponse response = ConversationResponse.fromEntity(conversation);

        assertThat(response.getKbIds()).isEmpty();
        assertThat(response.getConfig()).isEmpty();
    }

    @Test
    void shouldReturnEmptyCollectionsWhenJsonFieldsAreMissing() {
        Conversation conversation = new Conversation();
        conversation.setConversationId("conv_003");

        ConversationResponse response = ConversationResponse.fromEntity(conversation);

        assertThat(response.getKbIds()).isEqualTo(List.of());
        assertThat(response.getConfig()).isEmpty();
    }
}
