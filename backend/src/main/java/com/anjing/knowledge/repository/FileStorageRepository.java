package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.FileStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文件存储Repository
 */
@Repository
public interface FileStorageRepository extends JpaRepository<FileStorage, String> {

    /**
     * 根据MD5查询文件
     */
    Optional<FileStorage> findByFileMd5(String fileMd5);

    /**
     * 增加引用计数
     */
    @Modifying
    @Query("UPDATE FileStorage f SET f.refCount = f.refCount + 1 WHERE f.fileId = :fileId")
    int incrementRefCount(@Param("fileId") String fileId);

    /**
     * 减少引用计数
     */
    @Modifying
    @Query("UPDATE FileStorage f SET f.refCount = f.refCount - 1 WHERE f.fileId = :fileId AND f.refCount > 0")
    int decrementRefCount(@Param("fileId") String fileId);

    /**
     * 查询引用计数为0的文件
     */
    @Query("SELECT f FROM FileStorage f WHERE f.refCount <= 0")
    java.util.List<FileStorage> findUnreferencedFiles();
}

