package com.anjing.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keyword search adapter configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.keyword-search")
public class KeywordSearchProperties {

    public static final String LOCAL_PROVIDER = "local";
    public static final String ELASTICSEARCH_PROVIDER = "elasticsearch";

    /**
     * Current keyword search provider: local, elasticsearch.
     */
    private String provider = LOCAL_PROVIDER;

    /**
     * Elasticsearch adapter configuration.
     */
    private Elasticsearch elasticsearch = new Elasticsearch();

    @Data
    public static class Elasticsearch {

        /**
         * Elasticsearch or OpenSearch base URL.
         */
        private String baseUrl = "http://localhost:9200";

        /**
         * Index prefix. The effective index name is indexPrefix + kbId.
         */
        private String indexPrefix = "kb_";

        /**
         * Optional Elasticsearch API key value without the "ApiKey " prefix.
         */
        private String apiKey = "";

        /**
         * Fields used by the multi_match query.
         */
        private List<String> fields = List.of("content^2", "doc_name", "metadata.text");

        /**
         * Source fields required to map hits back into RAG chunks.
         */
        private List<String> sourceFields = List.of("chunkId", "chunk_id", "kbId", "kb_id", "content");
    }
}
