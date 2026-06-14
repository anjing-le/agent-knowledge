package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.DocumentParseResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts doc-parser transport DTOs into parser-neutral ingestion DTOs.
 */
@Component
public class DocumentParseResultMapper {

    public DocumentParseResult fromClientResult(DocParserClient.ParseResult source) {
        if (source == null) {
            return DocumentParseResult.error("doc-parser 响应为空");
        }
        if (!source.isSuccess()) {
            return DocumentParseResult.error(source.getErrorMessage());
        }

        DocumentParseResult target = new DocumentParseResult();
        target.setSuccess(true);
        target.setContent(source.getContent());
        target.setMetadata(source.getMetadata());
        target.setChunks(mapChunks(source.getChunks()));
        return target;
    }

    private List<DocumentParseResult.ChunkData> mapChunks(List<DocParserClient.ChunkData> chunks) {
        if (chunks == null) {
            return null;
        }
        return chunks.stream()
                .map(this::mapChunk)
                .toList();
    }

    private DocumentParseResult.ChunkData mapChunk(DocParserClient.ChunkData source) {
        DocumentParseResult.ChunkData target = new DocumentParseResult.ChunkData();
        target.setContent(source.getContent());
        target.setIndex(source.getIndex());
        target.setLength(source.getLength());
        target.setTokenCount(source.getTokenCount());
        target.setMetadata(source.getMetadata());
        return target;
    }
}
