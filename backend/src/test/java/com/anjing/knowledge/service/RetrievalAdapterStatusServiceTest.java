package com.anjing.knowledge.service;

import com.anjing.config.properties.DocParserProperties;
import com.anjing.config.properties.KeywordSearchProperties;
import com.anjing.config.properties.RerankProperties;
import com.anjing.config.properties.VectorStoreProperties;
import com.anjing.knowledge.model.response.RetrievalAdapterStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalAdapterStatusServiceTest {

    private final VectorStoreProperties vectorStoreProperties = new VectorStoreProperties();
    private final KeywordSearchProperties keywordSearchProperties = new KeywordSearchProperties();
    private final RerankProperties rerankProperties = new RerankProperties();
    private final DocParserProperties docParserProperties = new DocParserProperties();
    private final RetrievalAdapterStatusService statusService = new RetrievalAdapterStatusService(
            vectorStoreProperties,
            keywordSearchProperties,
            rerankProperties,
            docParserProperties
    );

    @Test
    void getStatusShouldExposeDefaultTeachingProviders() {
        RetrievalAdapterStatusResponse response = statusService.getStatus();
        Map<String, RetrievalAdapterStatusResponse.AdapterStatus> adapters = adaptersByAxis(response);

        assertThat(response.getSummary()).contains("Runtime provider snapshot");
        assertThat(adapters).containsKeys("vectorStore", "keywordSearch", "rerank", "docParser");
        assertThat(adapters.get("vectorStore").getCurrentProvider()).isEqualTo("memory");
        assertThat(adapters.get("vectorStore").getCurrentImplementation()).isEqualTo("MemoryVectorStoreService");
        assertThat(adapters.get("keywordSearch").getCurrentProvider()).isEqualTo("local");
        assertThat(adapters.get("keywordSearch").getBridgeProviders()).contains("bm25");
        assertThat(adapters.get("rerank").getCurrentProvider()).isEqualTo("local-demo");
        assertThat(adapters.get("docParser").getCurrentProvider()).isEqualTo("sync");
        assertThat(adapters.get("docParser").getContractPath()).isEqualTo("contracts/doc-parser-contract.json");
    }

    @Test
    void getStatusShouldExposeProductionAndBridgeProviders() {
        vectorStoreProperties.setProvider("pgvector");
        keywordSearchProperties.setProvider("bm25");
        rerankProperties.setProvider("remote");
        docParserProperties.setMode("async");
        docParserProperties.getAsync().setRecoveryEnabled(true);

        Map<String, RetrievalAdapterStatusResponse.AdapterStatus> adapters =
                adaptersByAxis(statusService.getStatus());

        assertThat(adapters.get("vectorStore").getCurrentImplementation()).isEqualTo("PgVectorStoreService");
        assertThat(adapters.get("vectorStore").getRuntimeStatus()).isEqualTo("production");
        assertThat(adapters.get("keywordSearch").getCurrentImplementation()).isEqualTo("Bm25KeywordSearchProvider");
        assertThat(adapters.get("keywordSearch").getSwitchCommand()).isEqualTo("KEYWORD_SEARCH_PROVIDER=bm25");
        assertThat(adapters.get("rerank").getCurrentImplementation()).isEqualTo("RerankProviderClient");
        assertThat(adapters.get("docParser").getCurrentImplementation())
                .isEqualTo("DocumentParserRecoveryPollingService");
        assertThat(adapters.get("docParser").getRuntimeStatus()).isEqualTo("async-recovery");
    }

    private Map<String, RetrievalAdapterStatusResponse.AdapterStatus> adaptersByAxis(
            RetrievalAdapterStatusResponse response
    ) {
        return response.getAdapters().stream()
                .collect(Collectors.toMap(
                        RetrievalAdapterStatusResponse.AdapterStatus::getAxis,
                        Function.identity()
                ));
    }
}
