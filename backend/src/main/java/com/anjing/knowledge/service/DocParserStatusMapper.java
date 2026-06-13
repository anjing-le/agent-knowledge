package com.anjing.knowledge.service;

import com.anjing.knowledge.model.enums.DocumentStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Maps Python doc-parser async task states to Java ingestion state.
 */
@Component
public class DocParserStatusMapper {

    private static final Map<String, MappedStatus> STATUS_MAPPING = Map.of(
            "PENDING", new MappedStatus(DocumentStatus.PARSING, "PENDING", "PARSING", 0.1f),
            "RUNNING", new MappedStatus(DocumentStatus.PARSING, "RUNNING", "PARSING", 0.2f),
            "SUCCEEDED", new MappedStatus(DocumentStatus.CHUNKING, "RUNNING", "CHUNKING", 0.3f),
            "FAILED", new MappedStatus(DocumentStatus.PARSE_FAILED, "FAILED", "PARSING", 0.0f),
            "CANCELED", new MappedStatus(DocumentStatus.PARSE_FAILED, "FAILED", "PARSING", 0.0f)
    );

    public MappedStatus map(String docParserStatus) {
        String normalizedStatus = normalizeStatus(docParserStatus);
        MappedStatus mappedStatus = STATUS_MAPPING.get(normalizedStatus);
        if (mappedStatus == null) {
            throw new IllegalArgumentException("Unsupported doc-parser status: " + docParserStatus);
        }
        return mappedStatus;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    public record MappedStatus(
            DocumentStatus documentStatus,
            String taskStatus,
            String taskPhase,
            Float progress
    ) {
    }
}
