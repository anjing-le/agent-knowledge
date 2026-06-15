package com.anjing.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Vector store adapter configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.vector-store")
public class VectorStoreProperties {

    public static final String MEMORY_PROVIDER = "memory";
    public static final String PGVECTOR_PROVIDER = "pgvector";

    /**
     * Current adapter provider: memory, milvus, pgvector.
     */
    private String provider = MEMORY_PROVIDER;

    /**
     * Logical collection prefix for external vector stores.
     */
    private String collectionPrefix = "kb_";

    /**
     * pgvector adapter configuration.
     */
    private Pgvector pgvector = new Pgvector();

    @Data
    public static class Pgvector {

        /**
         * Shared PostgreSQL table used by the pgvector adapter.
         */
        private String tableName = "rag_vectors";

        /**
         * Whether the adapter should create the extension and table at startup.
         */
        private boolean schemaInitializationEnabled = false;
    }
}
