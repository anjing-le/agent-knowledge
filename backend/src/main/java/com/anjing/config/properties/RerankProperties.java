package com.anjing.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rerank provider adapter configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rerank")
public class RerankProperties {

    public static final String LOCAL_DEMO_PROVIDER = "local-demo";
    public static final String LOCAL_LEXICAL_PROVIDER = "local-lexical";

    /**
     * Current rerank provider: local-demo, local-lexical, remote.
     */
    private String provider = LOCAL_DEMO_PROVIDER;

    /**
     * Remote rerank provider endpoint.
     */
    private String apiUrl = "https://api.cohere.com/v2/rerank";

    /**
     * Remote rerank provider API key.
     */
    private String apiKey = "";

    /**
     * Default remote rerank model.
     */
    private String model = "rerank-v3.5";

    public boolean isRemoteProvider() {
        return provider != null
                && !provider.isBlank()
                && !LOCAL_DEMO_PROVIDER.equalsIgnoreCase(provider)
                && !LOCAL_LEXICAL_PROVIDER.equalsIgnoreCase(provider);
    }

    public String resolveModel(String overrideModel) {
        if (overrideModel != null && !overrideModel.isBlank()) {
            return overrideModel;
        }
        return model;
    }

    public String remoteProviderLabel(String overrideModel) {
        if (overrideModel != null && !overrideModel.isBlank()) {
            return overrideModel;
        }
        return provider == null || provider.isBlank() ? "remote" : provider;
    }
}
