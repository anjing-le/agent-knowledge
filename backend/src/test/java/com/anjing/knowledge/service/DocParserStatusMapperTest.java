package com.anjing.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.anjing.knowledge.model.enums.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocParserStatusMapperTest {

    private final DocParserStatusMapper mapper = new DocParserStatusMapper();

    @Test
    void mapShouldConvertPendingToParsingPending() {
        DocParserStatusMapper.MappedStatus mapped = mapper.map("PENDING");

        assertThat(mapped.documentStatus()).isEqualTo(DocumentStatus.PARSING);
        assertThat(mapped.taskStatus()).isEqualTo("PENDING");
        assertThat(mapped.taskPhase()).isEqualTo("PARSING");
        assertThat(mapped.progress()).isEqualTo(0.1f);
    }

    @Test
    void mapShouldConvertRunningToParsingRunning() {
        DocParserStatusMapper.MappedStatus mapped = mapper.map("running");

        assertThat(mapped.documentStatus()).isEqualTo(DocumentStatus.PARSING);
        assertThat(mapped.taskStatus()).isEqualTo("RUNNING");
        assertThat(mapped.taskPhase()).isEqualTo("PARSING");
        assertThat(mapped.progress()).isEqualTo(0.2f);
    }

    @Test
    void mapShouldConvertSucceededToChunkingRunning() {
        DocParserStatusMapper.MappedStatus mapped = mapper.map("SUCCEEDED");

        assertThat(mapped.documentStatus()).isEqualTo(DocumentStatus.CHUNKING);
        assertThat(mapped.taskStatus()).isEqualTo("RUNNING");
        assertThat(mapped.taskPhase()).isEqualTo("CHUNKING");
        assertThat(mapped.progress()).isEqualTo(0.3f);
    }

    @Test
    void mapShouldConvertFailedAndCanceledToParseFailed() {
        assertFailureMapping(mapper.map("FAILED"));
        assertFailureMapping(mapper.map("CANCELED"));
    }

    @Test
    void mapShouldRejectUnknownStatus() {
        assertThatThrownBy(() -> mapper.map("PAUSED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported doc-parser status: PAUSED");
    }

    @Test
    void mapShouldStayAlignedWithDocParserContract() throws Exception {
        Path contractPath = Path.of(System.getProperty("user.dir"), "..", "contracts", "doc-parser-contract.json")
                .normalize();
        JsonNode mapping = new ObjectMapper().readTree(contractPath.toFile()).path("javaStatusMapping");

        Iterator<Map.Entry<String, JsonNode>> fields = mapping.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            DocParserStatusMapper.MappedStatus mapped = mapper.map(field.getKey());
            JsonNode expected = field.getValue();

            assertThat(mapped.documentStatus().getCode()).isEqualTo(expected.path("documentStatus").asText());
            assertThat(mapped.taskStatus()).isEqualTo(expected.path("taskStatus").asText());
            assertThat(mapped.taskPhase()).isEqualTo(expected.path("taskPhase").asText());
            assertThat(mapped.progress()).isEqualTo((float) expected.path("progress").asDouble());
        }
    }

    private void assertFailureMapping(DocParserStatusMapper.MappedStatus mapped) {
        assertThat(mapped.documentStatus()).isEqualTo(DocumentStatus.PARSE_FAILED);
        assertThat(mapped.taskStatus()).isEqualTo("FAILED");
        assertThat(mapped.taskPhase()).isEqualTo("PARSING");
        assertThat(mapped.progress()).isEqualTo(0.0f);
    }
}
