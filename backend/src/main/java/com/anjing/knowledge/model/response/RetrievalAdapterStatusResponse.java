package com.anjing.knowledge.model.response;

import lombok.Data;

import java.util.List;

/**
 * Runtime snapshot of RAG adapter providers.
 */
@Data
public class RetrievalAdapterStatusResponse {

    private String summary;
    private List<AdapterStatus> adapters;

    @Data
    public static class AdapterStatus {
        private String axis;
        private String displayName;
        private String currentProvider;
        private String defaultProvider;
        private List<String> bridgeProviders;
        private List<String> productionProviders;
        private String currentImplementation;
        private String boundary;
        private String configKey;
        private String switchCommand;
        private String contractPath;
        private String runtimeStatus;
    }
}
