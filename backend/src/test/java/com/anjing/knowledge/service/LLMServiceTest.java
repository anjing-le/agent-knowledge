package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LLMServiceTest {

    private static final String API_URL = "https://llm.example.test/v1/chat/completions";

    private final RemoteHttpClient remoteHttpClient = mock(RemoteHttpClient.class);
    private final LLMService llmService = new LLMService(remoteHttpClient);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(llmService, "apiUrl", API_URL);
        ReflectionTestUtils.setField(llmService, "apiKey", "test-key");
        ReflectionTestUtils.setField(llmService, "model", "gpt-4o-mini");
        ReflectionTestUtils.setField(llmService, "maxTokens", 1024);
        ReflectionTestUtils.setField(llmService, "temperature", 0.2f);
    }

    @Test
    void chatShouldUseRemoteHttpClientAndReturnFirstChoiceContent() {
        ArgumentCaptor<RemoteHttpRequest> requestCaptor = forClass(RemoteHttpRequest.class);
        when(remoteHttpClient.exchange(requestCaptor.capture(), eq(Map.class)))
                .thenReturn(Map.of(
                        "choices",
                        List.of(Map.of("message", Map.of("content", "基于知识库的回答")))
                ));

        String response = llmService.chat(
                "system prompt",
                "user question",
                List.of(Map.of("role", "assistant", "content", "history answer"))
        );

        assertThat(response).isEqualTo("基于知识库的回答");
        RemoteHttpRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUrl()).isEqualTo(API_URL);
        assertThat(request.getTargetService()).isEqualTo("llm-provider");
        assertThat(request.isCheckResponse()).isFalse();
        assertThat(request.getHeaders())
                .containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .containsEntry(HttpHeaders.AUTHORIZATION, "Bearer test-key");

        Map<String, Object> body = (Map<String, Object>) request.getBody();
        assertThat(body)
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("max_tokens", 1024)
                .containsEntry("temperature", 0.2f);
        assertThat((List<Map<String, String>>) body.get("messages"))
                .containsExactly(
                        Map.of("role", "system", "content", "system prompt"),
                        Map.of("role", "assistant", "content", "history answer"),
                        Map.of("role", "user", "content", "user question")
                );
    }

    @Test
    void chatShouldReturnFallbackMessageWhenProviderFails() {
        when(remoteHttpClient.exchange(org.mockito.ArgumentMatchers.any(RemoteHttpRequest.class), eq(Map.class)))
                .thenThrow(new IllegalStateException("provider down"));

        String response = llmService.chat("system prompt", "user question", null);

        assertThat(response).contains("AI 服务暂时不可用");
    }
}
