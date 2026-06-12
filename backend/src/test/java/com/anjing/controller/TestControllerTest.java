package com.anjing.controller;

import com.anjing.config.middleware.MiddlewareManager;
import com.anjing.knowledge.client.DocParserClient;
import com.anjing.model.response.APIResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void healthShouldExposeRequiredDocParserDownstreamStatus() {
        MiddlewareManager middlewareManager = mock(MiddlewareManager.class);
        DocParserClient docParserClient = mock(DocParserClient.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "agent-knowledge-test");
        when(docParserClient.isHealthy()).thenReturn(false);

        TestController controller = new TestController(middlewareManager, environment, docParserClient);

        APIResponse<Map<String, Object>> response = controller.health();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsEntry("status", "UP");
        assertThat(response.getData()).containsEntry("application", "agent-knowledge-test");

        Map<String, Object> downstreams = (Map<String, Object>) response.getData().get("downstreams");
        Map<String, Object> docParser = (Map<String, Object>) downstreams.get("docParser");
        assertThat(docParser)
                .containsEntry("serviceId", "agent-doc-parser")
                .containsEntry("status", "DOWN")
                .containsEntry("required", true);
    }
}
