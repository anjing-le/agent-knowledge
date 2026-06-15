package com.anjing.knowledge.service;

import com.anjing.client.RemoteHttpClient;
import com.anjing.client.RemoteHttpRequest;
import com.anjing.config.properties.KeywordSearchProperties;
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

class ElasticsearchKeywordSearchProviderTest {

    private static final String BASE_URL = "https://elasticsearch.example.test";

    private final RemoteHttpClient remoteHttpClient = mock(RemoteHttpClient.class);
    private final KeywordSearchProperties keywordSearchProperties = keywordSearchProperties();
    private final ElasticsearchKeywordSearchProvider keywordSearchProvider =
            new ElasticsearchKeywordSearchProvider(remoteHttpClient, keywordSearchProperties);

    @Test
    void searchShouldUseRemoteHttpClientAndMapHits() {
        ArgumentCaptor<RemoteHttpRequest> requestCaptor = forClass(RemoteHttpRequest.class);
        when(remoteHttpClient.exchange(requestCaptor.capture(), eq(Map.class)))
                .thenReturn(Map.of(
                        "hits",
                        Map.of(
                                "hits",
                                List.of(
                                        Map.of(
                                                "_id", "fallback-id",
                                                "_score", 2.5,
                                                "_source", Map.of(
                                                        "chunkId", "chunk-a",
                                                        "kbId", "kb-a",
                                                        "content", "agent scaffold retrieval adapter"
                                                )
                                        )
                                )
                        )
                ));

        List<KeywordSearchProvider.KeywordSearchHit> hits =
                keywordSearchProvider.search("agent scaffold", List.of("kb-a"), 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-a");
        assertThat(hits.get(0).kbId()).isEqualTo("kb-a");
        assertThat(hits.get(0).content()).isEqualTo("agent scaffold retrieval adapter");
        assertThat(hits.get(0).score()).isEqualTo(2.5f);

        RemoteHttpRequest request = requestCaptor.getValue();
        assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(request.getUrl()).isEqualTo(BASE_URL + "/ak_kb-a/_search");
        assertThat(request.getTargetService()).isEqualTo("keyword-search-provider");
        assertThat(request.isCheckResponse()).isFalse();
        assertThat(request.getHeaders())
                .containsEntry(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .containsEntry(HttpHeaders.AUTHORIZATION, "ApiKey test-key");

        Map<String, Object> body = (Map<String, Object>) request.getBody();
        assertThat(body)
                .containsEntry("size", 3)
                .containsEntry("_source", keywordSearchProperties.getElasticsearch().getSourceFields());
        assertThat((Map<String, Object>) body.get("query")).containsKey("multi_match");
    }

    @Test
    void searchShouldFallbackToHitIdAndRequestedKnowledgeBase() {
        when(remoteHttpClient.exchange(org.mockito.ArgumentMatchers.any(RemoteHttpRequest.class), eq(Map.class)))
                .thenReturn(Map.of(
                        "hits",
                        Map.of(
                                "hits",
                                List.of(Map.of(
                                        "_id", "chunk-from-id",
                                        "_score", "1.25",
                                        "_source", Map.of("content", "fallback content")
                                ))
                        )
                ));

        List<KeywordSearchProvider.KeywordSearchHit> hits =
                keywordSearchProvider.search("fallback", List.of("kb-fallback"), 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-from-id");
        assertThat(hits.get(0).kbId()).isEqualTo("kb-fallback");
        assertThat(hits.get(0).score()).isEqualTo(1.25f);
    }

    @Test
    void searchShouldSkipRemoteCallWhenQueryIsBlank() {
        assertThat(keywordSearchProvider.search(" ", List.of("kb-a"), 3)).isEmpty();

        verifyNoInteractions(remoteHttpClient);
    }

    private KeywordSearchProperties keywordSearchProperties() {
        KeywordSearchProperties properties = new KeywordSearchProperties();
        properties.getElasticsearch().setBaseUrl(BASE_URL + "/");
        properties.getElasticsearch().setIndexPrefix("ak_");
        properties.getElasticsearch().setApiKey("test-key");
        return properties;
    }
}
