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

    /**
     * Current adapter provider: memory, milvus, pgvector.
     */
    private String provider = "memory";

    /**
     * Logical collection prefix for external vector stores.
     */
    private String collectionPrefix = "kb_";
}
