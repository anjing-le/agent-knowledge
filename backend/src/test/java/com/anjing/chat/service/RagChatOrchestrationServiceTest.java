package com.anjing.chat.service;

import com.anjing.chat.model.entity.Message;
import com.anjing.chat.repository.MessageRepository;
import com.anjing.knowledge.model.request.SearchRequest;
import com.anjing.knowledge.model.response.SearchResult;
import com.anjing.knowledge.service.LLMService;
import com.anjing.knowledge.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatOrchestrationServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final LLMService llmService = mock(LLMService.class);
    private final RagChatOrchestrationService orchestrationService = new RagChatOrchestrationService(
            messageRepository,
            retrievalService,
            llmService
    );

    @Test
    void generateAnswerShouldRetrieveKnowledgeBuildHistoryAndCallLlm() {
        SearchResult reference = new SearchResult();
        reference.setChunkId("chunk_001");
        reference.setContent("命中的知识片段");

        List<Message> messages = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            messages.add(message(i, i % 2 == 0 ? "assistant" : "user", "history-" + i));
        }

        when(messageRepository.findByConversationIdOrderBySequenceAsc("conv_001")).thenReturn(messages);
        when(retrievalService.search(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenReturn(List.of(reference));
        when(llmService.generateRAGResponse(eq("怎么从脚手架长出 RAG agent"), eq(List.of(reference)), anyList()))
                .thenReturn("基于知识库的回答");

        RagChatOrchestrationService.RagChatAnswer answer = orchestrationService.generateAnswer(
                "conv_001",
                "怎么从脚手架长出 RAG agent",
                List.of("kb_001")
        );

        assertThat(answer.content()).isEqualTo("基于知识库的回答");
        assertThat(answer.references()).containsExactly(reference);

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(retrievalService).search(searchRequestCaptor.capture());
        SearchRequest searchRequest = searchRequestCaptor.getValue();
        assertThat(searchRequest.getQuery()).isEqualTo("怎么从脚手架长出 RAG agent");
        assertThat(searchRequest.getKbIds()).containsExactly("kb_001");
        assertThat(searchRequest.getTopK()).isEqualTo(5);

        ArgumentCaptor<List> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateRAGResponse(
                eq("怎么从脚手架长出 RAG agent"),
                eq(List.of(reference)),
                historyCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = historyCaptor.getValue();
        assertThat(history).hasSize(20);
        assertThat(history.get(0)).containsEntry("content", "history-2");
        assertThat(history.get(19)).containsEntry("content", "history-21");
    }

    @Test
    void generateAnswerShouldSkipRetrievalWhenKnowledgeBaseIdsAreEmpty() {
        when(messageRepository.findByConversationIdOrderBySequenceAsc("conv_001")).thenReturn(List.of());
        when(llmService.generateRAGResponse(eq("普通聊天"), eq(List.of()), eq(List.of())))
                .thenReturn("无引用回答");

        RagChatOrchestrationService.RagChatAnswer answer = orchestrationService.generateAnswer(
                "conv_001",
                "普通聊天",
                List.of()
        );

        assertThat(answer.content()).isEqualTo("无引用回答");
        assertThat(answer.references()).isEmpty();
        verify(retrievalService, never()).search(org.mockito.ArgumentMatchers.any(SearchRequest.class));
    }

    @Test
    void generateAnswerShouldContinueWithEmptyReferencesWhenRetrievalFails() {
        when(messageRepository.findByConversationIdOrderBySequenceAsc("conv_001")).thenReturn(List.of());
        when(retrievalService.search(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("vector store unavailable"));
        when(llmService.generateRAGResponse(eq("检索失败怎么办"), eq(List.of()), eq(List.of())))
                .thenReturn("降级回答");

        RagChatOrchestrationService.RagChatAnswer answer = orchestrationService.generateAnswer(
                "conv_001",
                "检索失败怎么办",
                List.of("kb_001")
        );

        assertThat(answer.content()).isEqualTo("降级回答");
        assertThat(answer.references()).isEmpty();
    }

    private Message message(int sequence, String role, String content) {
        Message message = new Message();
        message.setSequence(sequence);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
