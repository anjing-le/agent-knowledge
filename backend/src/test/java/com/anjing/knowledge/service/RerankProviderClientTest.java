package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import com.anjing.config.properties.RerankProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RerankProviderClientTest {

    private static final String API_URL = "https://rerank.example.test/v2/rerank";

    private final RemoteHttpClient remoteHttpClient = mock(RemoteHttpClient.class);
    private final RerankProperties rerankProperties = rerankProperties();
    private final RerankProviderClient rerankProviderClient = new RerankProviderClient(remoteHttpClient, rerankProperties);

    @Test
    void rerankShouldUseRemoteHttpClientAndMapRankedScores() {
        ArgumentCaptor<RemoteHttpRequest> requestCaptor = forClass(RemoteHttpRequest.class);
        when(remoteHttpClient.exchange(requestCaptor.capture(), eq(Map.class)))
                .thenReturn(Map.of(
                        "results",
                        List.of(
                                Map.of("index", 1, "relevance_score", 0.92),
                                Map.of("index", 0, "relevance_score", 0.14)
                        )
                ));

        List<Float> scores = rerankProviderClient.rerank(
                "agent scaffold",
                List.of("database migration", "agent scaffold retrieval"),
                "custom-rerank"
        );

        assertThat(scores).containsExactly(0.14f, 0.92f);
        RemoteHttpRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUrl()).isEqualTo(API_URL);
        assertThat(request.getTargetService()).isEqualTo("rerank-provider");
        assertThat(request.isCheckResponse()).isFalse();
        assertThat(request.getHeaders())
                .containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .containsEntry(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        assertThat((Map<String, Object>) request.getBody())
                .containsEntry("model", "custom-rerank")
                .containsEntry("query", "agent scaffold")
                .containsEntry("documents", List.of("database migration", "agent scaffold retrieval"))
                .containsEntry("top_n", 2);
    }

    @Test
    void rerankShouldMapDirectScoresResponse() {
        when(remoteHttpClient.exchange(org.mockito.ArgumentMatchers.any(RemoteHttpRequest.class), eq(Map.class)))
                .thenReturn(Map.of("scores", List.of(0.25, 0.75)));

        List<Float> scores = rerankProviderClient.rerank("query", List.of("left", "right"), null);

        assertThat(scores).containsExactly(0.25f, 0.75f);
    }

    @Test
    void rerankShouldSkipRemoteCallWhenDocumentsAreEmpty() {
        assertThat(rerankProviderClient.rerank("query", List.of(), null)).isEmpty();

        verifyNoInteractions(remoteHttpClient);
    }

    private RerankProperties rerankProperties() {
        RerankProperties properties = new RerankProperties();
        properties.setApiUrl(API_URL);
        properties.setApiKey("test-key");
        properties.setModel("rerank-v3.5");
        return properties;
    }
}
