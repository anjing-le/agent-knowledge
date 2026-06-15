package com.anjing.knowledge.service;

import com.anjing.config.properties.DocParserProperties;
import com.anjing.config.properties.KeywordSearchProperties;
import com.anjing.config.properties.RerankProperties;
import com.anjing.config.properties.VectorStoreProperties;
import com.anjing.knowledge.model.response.RetrievalAdapterStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Exposes runtime adapter providers for the RAG teaching workspace.
 */
@Service
@RequiredArgsConstructor
public class RetrievalAdapterStatusService {

    private static final String CONTRACT_PATH = "contracts/retrieval-adapter-contract.json";
    private static final String DOC_PARSER_CONTRACT_PATH = "contracts/doc-parser-contract.json";

    private final VectorStoreProperties vectorStoreProperties;
    private final KeywordSearchProperties keywordSearchProperties;
    private final RerankProperties rerankProperties;
    private final DocParserProperties docParserProperties;

    public RetrievalAdapterStatusResponse getStatus() {
        RetrievalAdapterStatusResponse response = new RetrievalAdapterStatusResponse();
        response.setSummary("Runtime provider snapshot for scaffold-grown RAG adapters.");
        response.setAdapters(List.of(
                vectorStoreStatus(),
                keywordSearchStatus(),
                rerankStatus(),
                docParserStatus()
        ));
        return response;
    }

    private RetrievalAdapterStatusResponse.AdapterStatus vectorStoreStatus() {
        String provider = normalize(vectorStoreProperties.getProvider(), VectorStoreProperties.MEMORY_PROVIDER);
        return adapterStatus(
                "vectorStore",
                "Vector Store",
                provider,
                VectorStoreProperties.MEMORY_PROVIDER,
                List.of(),
                List.of(VectorStoreProperties.PGVECTOR_PROVIDER),
                vectorStoreImplementation(provider),
                "VectorStoreService",
                "app.vector-store.provider",
                "VECTOR_STORE_PROVIDER=pgvector",
                CONTRACT_PATH,
                runtimeStatus(provider, VectorStoreProperties.MEMORY_PROVIDER)
        );
    }

    private RetrievalAdapterStatusResponse.AdapterStatus keywordSearchStatus() {
        String provider = normalize(keywordSearchProperties.getProvider(), KeywordSearchProperties.LOCAL_PROVIDER);
        return adapterStatus(
                "keywordSearch",
                "Keyword Search",
                provider,
                KeywordSearchProperties.LOCAL_PROVIDER,
                List.of(KeywordSearchProperties.BM25_PROVIDER),
                List.of(KeywordSearchProperties.ELASTICSEARCH_PROVIDER),
                keywordSearchImplementation(provider),
                "KeywordSearchProvider",
                "app.keyword-search.provider",
                "KEYWORD_SEARCH_PROVIDER=bm25",
                CONTRACT_PATH,
                runtimeStatus(provider, KeywordSearchProperties.LOCAL_PROVIDER)
        );
    }

    private RetrievalAdapterStatusResponse.AdapterStatus rerankStatus() {
        String provider = normalize(rerankProperties.getProvider(), RerankProperties.LOCAL_DEMO_PROVIDER);
        return adapterStatus(
                "rerank",
                "Rerank",
                provider,
                RerankProperties.LOCAL_DEMO_PROVIDER,
                List.of(RerankProperties.LOCAL_LEXICAL_PROVIDER),
                List.of("remote"),
                rerankImplementation(provider),
                "RetrievalRerankService",
                "app.rerank.provider",
                "RERANK_PROVIDER=remote",
                CONTRACT_PATH,
                runtimeStatus(provider, RerankProperties.LOCAL_DEMO_PROVIDER)
        );
    }

    private RetrievalAdapterStatusResponse.AdapterStatus docParserStatus() {
        String mode = normalize(docParserProperties.getMode(), "sync");
        String runtimeStatus = docParserProperties.isAsyncMode()
                ? docParserAsyncRuntimeStatus()
                : "default";
        return adapterStatus(
                "docParser",
                "Doc Parser",
                mode,
                "sync",
                List.of("recovery"),
                List.of("async"),
                docParserImplementation(mode),
                "DocParserClient",
                "app.doc-parser.mode",
                "DOC_PARSER_MODE=async",
                DOC_PARSER_CONTRACT_PATH,
                runtimeStatus
        );
    }

    private RetrievalAdapterStatusResponse.AdapterStatus adapterStatus(
            String axis,
            String displayName,
            String currentProvider,
            String defaultProvider,
            List<String> bridgeProviders,
            List<String> productionProviders,
            String currentImplementation,
            String boundary,
            String configKey,
            String switchCommand,
            String contractPath,
            String runtimeStatus
    ) {
        RetrievalAdapterStatusResponse.AdapterStatus status = new RetrievalAdapterStatusResponse.AdapterStatus();
        status.setAxis(axis);
        status.setDisplayName(displayName);
        status.setCurrentProvider(currentProvider);
        status.setDefaultProvider(defaultProvider);
        status.setBridgeProviders(bridgeProviders);
        status.setProductionProviders(productionProviders);
        status.setCurrentImplementation(currentImplementation);
        status.setBoundary(boundary);
        status.setConfigKey(configKey);
        status.setSwitchCommand(switchCommand);
        status.setContractPath(contractPath);
        status.setRuntimeStatus(runtimeStatus);
        return status;
    }

    private String vectorStoreImplementation(String provider) {
        if (VectorStoreProperties.PGVECTOR_PROVIDER.equalsIgnoreCase(provider)) {
            return "PgVectorStoreService";
        }
        return "MemoryVectorStoreService";
    }

    private String keywordSearchImplementation(String provider) {
        if (KeywordSearchProperties.BM25_PROVIDER.equalsIgnoreCase(provider)) {
            return "Bm25KeywordSearchProvider";
        }
        if (KeywordSearchProperties.ELASTICSEARCH_PROVIDER.equalsIgnoreCase(provider)) {
            return "ElasticsearchKeywordSearchProvider";
        }
        return "LocalKeywordSearchProvider";
    }

    private String rerankImplementation(String provider) {
        if (RerankProperties.LOCAL_DEMO_PROVIDER.equalsIgnoreCase(provider)
                || RerankProperties.LOCAL_LEXICAL_PROVIDER.equalsIgnoreCase(provider)) {
            return "RetrievalRerankService";
        }
        return "RerankProviderClient";
    }

    private String docParserImplementation(String mode) {
        if ("async".equalsIgnoreCase(mode)) {
            return docParserProperties.getAsync().isRecoveryEnabled()
                    ? "DocumentParserRecoveryPollingService"
                    : "DocumentAsyncParsingService";
        }
        return "DocumentParsingService";
    }

    private String docParserAsyncRuntimeStatus() {
        if (docParserProperties.getAsync().isRecoveryEnabled()) {
            return "async-recovery";
        }
        if (docParserProperties.getAsync().isSubmitOnlyEnabled()) {
            return "async-submit-only";
        }
        return "production";
    }

    private String runtimeStatus(String provider, String defaultProvider) {
        return defaultProvider.equalsIgnoreCase(provider) ? "default" : "production";
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
