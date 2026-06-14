package com.anjing.knowledge.repository;

import com.anjing.knowledge.model.entity.DocumentProcessingTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Document processing task repository.
 */
@Repository
public interface DocumentProcessingTaskRepository extends JpaRepository<DocumentProcessingTask, String> {

    List<DocumentProcessingTask> findByDocIdOrderByCreatedAtDesc(String docId);

    Optional<DocumentProcessingTask> findFirstByDocIdOrderByCreatedAtDesc(String docId);

    @Query("""
            select task from DocumentProcessingTask task
            where task.parserTaskId is not null
              and task.status in :statuses
              and task.phase = :phase
            order by task.updatedAt asc
            """)
    List<DocumentProcessingTask> findRecoverableParserTasks(@Param("statuses") Collection<String> statuses,
                                                            @Param("phase") String phase,
                                                            Pageable pageable);
}
