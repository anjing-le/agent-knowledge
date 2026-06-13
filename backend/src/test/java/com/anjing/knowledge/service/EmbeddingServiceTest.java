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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    private static final String API_URL = "https://llm.example.test/v1/embeddings";

    private final RemoteHttpClient remoteHttpClient = mock(RemoteHttpClient.class);
    private final EmbeddingService embeddingService = new EmbeddingService(remoteHttpClient);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(embeddingService, "apiUrl", API_URL);
        ReflectionTestUtils.setField(embeddingService, "provider", "remote");
        ReflectionTestUtils.setField(embeddingService, "apiKey", "test-key");
        ReflectionTestUtils.setField(embeddingService, "model", "text-embedding-3-small");
        ReflectionTestUtils.setField(embeddingService, "dimensions", 3);
    }

    @Test
    void embedBatchShouldUseRemoteHttpClientAndMapEmbeddings() {
        ArgumentCaptor<RemoteHttpRequest> requestCaptor = forClass(RemoteHttpRequest.class);
        when(remoteHttpClient.exchange(requestCaptor.capture(), eq(Map.class)))
                .thenReturn(Map.of(
                        "data",
                        List.of(
                                Map.of("embedding", List.of(0.1, 0.2, 0.3)),
                                Map.of("embedding", List.of(1, 2, 3))
                        )
                ));

        List<List<Float>> embeddings = embeddingService.embedBatch(List.of("alpha", "beta"), "custom-model");

        assertThat(embeddings)
                .containsExactly(
                        List.of(0.1f, 0.2f, 0.3f),
                        List.of(1.0f, 2.0f, 3.0f)
                );
        RemoteHttpRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUrl()).isEqualTo(API_URL);
        assertThat(request.getTargetService()).isEqualTo("embedding-provider");
        assertThat(request.isCheckResponse()).isFalse();
        assertThat(request.getHeaders())
                .containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .containsEntry(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        assertThat((Map<String, Object>) request.getBody())
                .containsEntry("model", "custom-model")
                .containsEntry("input", List.of("alpha", "beta"))
                .containsEntry("dimensions", 3);
    }

    @Test
    void embedBatchShouldSkipRemoteCallWhenInputIsEmpty() {
        assertThat(embeddingService.embedBatch(List.of())).isEmpty();

        verifyNoInteractions(remoteHttpClient);
    }

    @Test
    void embedBatchShouldReturnEmptyListWhenProviderFails() {
        when(remoteHttpClient.exchange(org.mockito.ArgumentMatchers.any(RemoteHttpRequest.class), eq(Map.class)))
                .thenThrow(new IllegalStateException("provider down"));

        assertThat(embeddingService.embedBatch(List.of("alpha"))).isEmpty();
    }

    @Test
    void embedBatchShouldUseLocalDemoProviderWithoutRemoteCall() {
        ReflectionTestUtils.setField(embeddingService, "provider", "local-demo");
        ReflectionTestUtils.setField(embeddingService, "dimensions", 8);

        List<List<Float>> embeddings = embeddingService.embedBatch(List.of("脚手架 RAG", "脚手架 RAG"));

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).hasSize(8);
        assertThat(embeddings.get(0)).isEqualTo(embeddings.get(1));
        assertThat(embeddings.get(0)).anyMatch(value -> value > 0.0f);
        verifyNoInteractions(remoteHttpClient);
    }
}
