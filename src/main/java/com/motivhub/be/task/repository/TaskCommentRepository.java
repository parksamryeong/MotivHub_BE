package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    @Query("SELECT tc FROM TaskComment tc JOIN FETCH tc.author WHERE tc.task.id = :taskId ORDER BY tc.createdAt ASC, tc.id ASC")
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
