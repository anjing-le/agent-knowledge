package com.anjing.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python doc-parser service boundary configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.doc-parser")
public class DocParserProperties {

    /**
     * Base URL of the independent doc-parser service.
     */
    private String baseUrl = "http://localhost:9001";

    /**
     * Parsing mode: sync for V1 demos, async for V2 long-running parsing.
     */
    private String mode = "sync";

    /**
     * Legacy sync HTTP timeout in milliseconds.
     */
    private long timeout = 300000L;

    /**
     * Async submit/poll tuning.
     */
    private Async async = new Async();

    public boolean isAsyncMode() {
        return "async".equalsIgnoreCase(mode);
    }

    @Data
    public static class Async {

        /**
         * Maximum polling attempts before Java marks parsing as failed.
         */
        private int maxPollAttempts = 30;

        /**
         * Delay between polling attempts.
         */
        private long pollIntervalMs = 1000L;

        /**
         * Whether async parsing should return after submit and let recovery polling continue.
         */
        private boolean submitOnlyEnabled = false;

        /**
         * Whether scheduled recovery polling should scan parser tasks.
         */
        private boolean recoveryEnabled = false;

        /**
         * Delay between recovery polling batches.
         */
        private long recoveryFixedDelayMs = 15000L;

        /**
         * Maximum parser tasks scanned in one recovery batch.
         */
        private int recoveryBatchSize = 20;
    }
}
