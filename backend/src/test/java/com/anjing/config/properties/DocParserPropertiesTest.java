package com.anjing.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocParserPropertiesTest {

    @Test
    void defaultsShouldKeepSyncDocParserBoundary() {
        DocParserProperties properties = new DocParserProperties();

        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:9001");
        assertThat(properties.getMode()).isEqualTo("sync");
        assertThat(properties.isAsyncMode()).isFalse();
        assertThat(properties.getTimeout()).isEqualTo(300000L);
        assertThat(properties.getAsync().getMaxPollAttempts()).isEqualTo(30);
        assertThat(properties.getAsync().getPollIntervalMs()).isEqualTo(1000L);
        assertThat(properties.getAsync().isSubmitOnlyEnabled()).isFalse();
        assertThat(properties.getAsync().isRecoveryEnabled()).isFalse();
        assertThat(properties.getAsync().getRecoveryFixedDelayMs()).isEqualTo(15000L);
        assertThat(properties.getAsync().getRecoveryBatchSize()).isEqualTo(20);
    }

    @Test
    void isAsyncModeShouldBeCaseInsensitive() {
        DocParserProperties properties = new DocParserProperties();

        properties.setMode("ASYNC");

        assertThat(properties.isAsyncMode()).isTrue();
    }
}
