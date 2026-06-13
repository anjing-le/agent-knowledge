package com.anjing.knowledge.service;

import com.anjing.knowledge.model.entity.Document;
import com.anjing.knowledge.model.entity.FileStorage;
import com.anjing.knowledge.model.enums.DocumentStatus;
import com.anjing.knowledge.model.response.DocumentResponse;
import com.anjing.knowledge.repository.ChunkRepository;
import com.anjing.knowledge.repository.DocumentRepository;
import com.anjing.knowledge.repository.FileStorageRepository;
import com.anjing.knowledge.repository.KnowledgeBaseRepository;
import com.anjing.model.exception.BizException;
import com.anjing.model.errorcode.CommonErrorCode;
import com.anjing.model.response.PageResult;
import com.anjing.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final FileStorageRepository fileStorageRepository;
    private final ChunkRepository chunkRepository;

    @org.springframework.beans.factory.annotation.Value("${app.upload.base-dir:./uploads}")
    private String uploadBaseDir;

    // 用于生成Doc ID的计数器
    private static final AtomicInteger DOC_COUNTER = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 创建已上传文档记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document createUploadedDocument(String kbId, MultipartFile file,
                                           String parserStrategyId, String chunkStrategyId) throws IOException {
        // 验证知识库是否存在
        knowledgeBaseRepository.findByKbIdAndIsDeletedFalse(kbId)
                .orElseThrow(() -> new BizException("知识库不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 计算文件MD5
        String fileMd5 = DigestUtils.md5Hex(file.getInputStream());

        // 检查文件是否已存在（文件去重）
        Optional<FileStorage> existingFileStorage = fileStorageRepository.findByFileMd5(fileMd5);
        FileStorage fileStorage = existingFileStorage.orElseGet(() -> createFileStorage(file, fileMd5));

        // 如果文件已存在，增加引用计数
        if (existingFileStorage.isPresent()) {
            fileStorageRepository.incrementRefCount(fileStorage.getFileId());
        }

        // 创建文档记录
        Document doc = new Document();
        doc.setDocId(generateDocId());
        doc.setKbId(kbId);
        doc.setFileId(fileStorage.getFileId());
        doc.setDocName(file.getOriginalFilename());
        doc.setDocType(getFileExtension(file.getOriginalFilename()));
        doc.setDocSize(file.getSize());
        doc.setStatus(DocumentStatus.PENDING.getCode());
        doc.setProgress(0.0f);
        doc.setParserStrategyId(parserStrategyId);
        doc.setChunkStrategyId(chunkStrategyId);
        doc.setIsDeleted(false);
        doc.setIsEnabled(true);

        doc = documentRepository.save(doc);
        log.info("上传文档成功: docId={}, docName={}, kbId={}", doc.getDocId(), doc.getDocName(), kbId);
        return doc;
    }

    /**
     * 创建文件存储记录并将文件写入磁盘
     */
    private FileStorage createFileStorage(MultipartFile file, String fileMd5) {
        String fileId = generateFileId();

        Path targetDir = Paths.get(uploadBaseDir, fileId);
        Path targetFile = targetDir.resolve(file.getOriginalFilename());
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已落盘: {}", targetFile.toAbsolutePath());
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage(), CommonErrorCode.SYSTEM_ERROR);
        }

        FileStorage storage = new FileStorage();
        storage.setFileId(fileId);
        storage.setOriginalName(file.getOriginalFilename());
        storage.setFileSize(file.getSize());
        storage.setFileMd5(fileMd5);
        storage.setFileType(file.getContentType());
        storage.setStoragePath(targetFile.toAbsolutePath().toString());
        storage.setRefCount(1);
        storage.setCreatedAt(DateUtils.nowLocalDateTime());
        
        return fileStorageRepository.save(storage);
    }

    /**
     * 获取文档详情
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(String docId) {
        Document doc = documentRepository.findByDocIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new BizException("文档不存在", CommonErrorCode.DATA_NOT_FOUND));
        return DocumentResponse.fromEntity(doc);
    }

    /**
     * 分页查询知识库下的文档
     */
    @Transactional(readOnly = true)
    public PageResult<DocumentResponse> listDocuments(String kbId, int page, int size, String keyword) {
        // 验证知识库是否存在
        if (!knowledgeBaseRepository.existsById(kbId)) {
            throw new BizException("知识库不存在", CommonErrorCode.DATA_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Document> docPage;
        if (StringUtils.isNotBlank(keyword)) {
            docPage = documentRepository.searchByKeyword(kbId, keyword, pageable);
        } else {
            docPage = documentRepository.findByKbIdAndIsDeletedFalse(kbId, pageable);
        }

        Page<DocumentResponse> responsePage = docPage.map(DocumentResponse::fromEntity);
        return PageResult.of(
                responsePage.getContent(),
                responsePage.getTotalElements(),
                responsePage.getNumber() + 1,
                responsePage.getSize()
        );
    }

    /**
     * 删除文档（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String docId) {
        Document doc = documentRepository.findByDocIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new BizException("文档不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 软删除文档
        doc.setIsDeleted(true);
        doc.setUpdatedAt(DateUtils.nowLocalDateTime());
        documentRepository.save(doc);

        // 删除关联的chunks
        chunkRepository.deleteByDocId(docId);

        // 减少文件引用计数
        fileStorageRepository.decrementRefCount(doc.getFileId());

        log.info("删除文档成功: docId={}", docId);
    }

    /**
     * 批量删除文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteDocuments(List<String> docIds) {
        for (String docId : docIds) {
            try {
                deleteDocument(docId);
            } catch (Exception e) {
                log.error("删除文档失败: docId={}, error={}", docId, e.getMessage());
            }
        }
    }

    /**
     * 更新文档状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDocumentStatus(String docId, DocumentStatus status, Float progress, String message) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new BizException("文档不存在", CommonErrorCode.DATA_NOT_FOUND));

        doc.setStatus(status.getCode());
        if (progress != null) {
            doc.setProgress(progress);
        }
        if (message != null) {
            doc.setProgressMsg(message);
        }
        if (status == DocumentStatus.COMPLETED) {
            doc.setCompletedAt(DateUtils.nowLocalDateTime());
        }

        documentRepository.save(doc);
        log.info("更新文档状态: docId={}, status={}", docId, status.getCode());
    }

    /**
     * 启用/禁用文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void setDocumentEnabled(String docId, boolean enabled) {
        Document doc = documentRepository.findByDocIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new BizException("文档不存在", CommonErrorCode.DATA_NOT_FOUND));

        doc.setIsEnabled(enabled);
        documentRepository.save(doc);
        log.info("设置文档启用状态: docId={}, enabled={}", docId, enabled);
    }

    /**
     * 重置文档重新处理状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public Document resetDocumentForReprocess(String docId) {
        Document doc = documentRepository.findByDocIdAndIsDeletedFalse(docId)
                .orElseThrow(() -> new BizException("文档不存在", CommonErrorCode.DATA_NOT_FOUND));

        // 删除旧的 chunks
        chunkRepository.deleteByDocId(docId);

        // 重置文档状态
        doc.setStatus(DocumentStatus.PENDING.getCode());
        doc.setProgress(0.0f);
        doc.setProgressMsg("等待重新处理...");
        doc.setChunkNum(0);
        doc.setTokenNum(0);
        doc.setCompletedAt(null);
        doc = documentRepository.save(doc);
        return doc;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (StringUtils.isBlank(filename)) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 生成文档ID
     */
    private String generateDocId() {
        String dateStr = DateUtils.nowLocalDateTime().format(DATE_FORMAT);
        int counter = DOC_COUNTER.incrementAndGet();
        return String.format("doc_%s_%04d", dateStr, counter);
    }

    /**
     * 生成文件ID
     */
    private String generateFileId() {
        return "file_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
    }
}
