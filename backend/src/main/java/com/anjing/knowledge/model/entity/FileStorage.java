package com.anjing.knowledge.model.entity;

import com.anjing.util.DateUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 📁 文件存储实体
 * 
 * 用于文件去重，基于MD5判断相同文件
 * 
 * @author Knowledge Base Team
 * @since 2025-01-20
 */
@Entity
@Table(name = "file_storage", indexes = {
        @Index(name = "idx_file_md5", columnList = "file_md5"),
        @Index(name = "idx_file_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileStorage {

    /**
     * 文件ID，主键
     */
    @Id
    @Column(name = "file_id", length = 64, nullable = false)
    private String fileId;

    /**
     * 原始文件名
     */
    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * 文件MD5
     */
    @Column(name = "file_md5", length = 64, nullable = false)
    private String fileMd5;

    /**
     * 文件类型（MIME类型）
     */
    @Column(name = "file_type", length = 128)
    private String fileType;

    /**
     * 存储路径（OSS的key）
     */
    @Column(name = "storage_path", length = 512, nullable = false)
    private String storagePath;

    /**
     * 存储桶名称
     */
    @Column(name = "bucket_name", length = 128)
    private String bucketName;

    /**
     * 引用计数
     */
    @Column(name = "ref_count", nullable = false)
    private Integer refCount = 1;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateUtils.nowLocalDateTime();
        }
    }
}
