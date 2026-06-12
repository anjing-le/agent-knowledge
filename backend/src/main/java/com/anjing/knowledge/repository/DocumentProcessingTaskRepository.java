package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Document processing task repository.
 */
@Repository
public interface DocumentProcessingTaskRepository extends JpaRepository<DocumentProcessingTask, String> {

    List<DocumentProcessingTask> findByDocIdOrderByCreatedAtDesc(String docId);

    Optional<DocumentProcessingTask> findFirstByDocIdOrderByCreatedAtDesc(String docId);
}
