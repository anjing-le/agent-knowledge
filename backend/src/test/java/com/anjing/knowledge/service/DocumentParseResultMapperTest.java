package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.DocumentParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParseResultMapperTest {

    private final DocumentParseResultMapper mapper = new DocumentParseResultMapper();

    @Test
    void fromClientResultShouldMapParserTransportDtoIntoIngestionDto() {
        DocParserClient.ParseResult source = new DocParserClient.ParseResult();
        source.setSuccess(true);
        source.setContent("parsed content");
        source.setMetadata(Map.of("parser_id", "general"));
        DocParserClient.ChunkData chunk = new DocParserClient.ChunkData();
        chunk.setContent("chunk content");
        chunk.setIndex(3);
        chunk.setLength(13);
        chunk.setTokenCount(4);
        chunk.setMetadata(Map.of("page_idx", List.of(1)));
        source.setChunks(List.of(chunk));

        DocumentParseResult result = mapper.fromClientResult(source);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("parsed content");
        assertThat(result.getMetadata()).containsEntry("parser_id", "general");
        assertThat(result.getChunks()).hasSize(1);
        assertThat(result.getChunks().get(0).getContent()).isEqualTo("chunk content");
        assertThat(result.getChunks().get(0).getIndex()).isEqualTo(3);
        assertThat(result.getChunks().get(0).getLength()).isEqualTo(13);
        assertThat(result.getChunks().get(0).getTokenCount()).isEqualTo(4);
        assertThat(result.getChunks().get(0).getMetadata()).containsEntry("page_idx", List.of(1));
    }

    @Test
    void fromClientResultShouldMapNullAndFailuresToFailedResult() {
        assertThat(mapper.fromClientResult(null).isSuccess()).isFalse();

        DocParserClient.ParseResult source = DocParserClient.ParseResult.error("unsupported file type");

        DocumentParseResult result = mapper.fromClientResult(source);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("unsupported file type");
    }
}
