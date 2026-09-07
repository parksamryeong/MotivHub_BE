package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskActivityLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, Long> {

    @Query("SELECT tal FROM TaskActivityLog tal JOIN FETCH tal.actor WHERE tal.task.id = :taskId "
            + "ORDER BY tal.createdAt DESC, tal.id DESC")
    List<TaskActivityLog> findByTaskIdOrderByCreatedAtDesc(@Param("taskId") Long taskId);

    void deleteByTaskId(Long taskId);
}
