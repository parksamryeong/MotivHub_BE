package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskWatcher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, Long> {

    @Query("SELECT tw FROM TaskWatcher tw JOIN FETCH tw.user WHERE tw.task.id = :taskId")
    List<TaskWatcher> findByTaskId(@Param("taskId") Long taskId);

    Optional<TaskWatcher> findByTaskIdAndUserId(Long taskId, Long userId);
    boolean existsByTaskIdAndUserId(Long taskId, Long userId);
    void deleteByTaskId(Long taskId);
}
