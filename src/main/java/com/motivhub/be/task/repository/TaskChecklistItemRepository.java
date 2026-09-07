package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {

    @Query("SELECT tci FROM TaskChecklistItem tci WHERE tci.task.id = :taskId ORDER BY tci.orderIndex ASC")
    List<TaskChecklistItem> findByTaskIdOrderByOrderIndexAsc(@Param("taskId") Long taskId);

    long countByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
