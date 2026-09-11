package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {

    @Query("SELECT tci FROM TaskChecklistItem tci WHERE tci.task.id = :taskId ORDER BY tci.orderIndex ASC, tci.id ASC")
    List<TaskChecklistItem> findByTaskIdOrderByOrderIndexAsc(@Param("taskId") Long taskId);

    @Query("SELECT COALESCE(MAX(tci.orderIndex), -1) FROM TaskChecklistItem tci WHERE tci.task.id = :taskId")
    int findMaxOrderIndexByTaskId(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);

    long countByTaskId(Long taskId);

    long countByTaskIdAndDoneFalse(Long taskId);
}
