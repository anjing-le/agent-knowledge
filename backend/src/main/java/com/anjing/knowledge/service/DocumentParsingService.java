package com.anjing.knowledge.service;

import com.anjing.knowledge.client.DocParserClient;
import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.repository.FileStorageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Calls the Python doc-parser service for the document parsing stage.
 */
@Service
@RequiredArgsConstructor
public class DocumentParsingService {

    private final DocParserClient docParserClient;
    private final FileStorageRepository fileStorageRepository;

    public DocParserClient.ParseResult parseDocument(Document document) {
        String filePath = fileStorageRepository.findById(document.getFileId())
                .map(fileStorage -> fileStorage.getStoragePath())
                .orElse(null);

        if (filePath == null) {
            return DocParserClient.ParseResult.error("文件存储路径不存在");
        }

        if (!docParserClient.isHealthy()) {
            return DocParserClient.ParseResult.error("doc-parser 服务不可用，请确保 doc-parser 已启动（端口9001）");
        }

        return docParserClient.parseDocument(filePath, mapDocType(document.getDocType()));
    }

    private String mapDocType(String fileExtension) {
        return switch (fileExtension.toLowerCase()) {
            case "pdf", "doc", "docx" -> "DOCUMENT_BASIC";
            case "xls", "xlsx" -> "STANDARD_WORKBOOK";
            case "txt", "md" -> "PLAIN_TEXT";
            default -> "DOCUMENT_BASIC";
        };
    }
}
