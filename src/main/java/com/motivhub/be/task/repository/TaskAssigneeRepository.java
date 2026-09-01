package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskAssignee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, Long> {
    List<TaskAssignee> findByTaskId(Long taskId);
    List<TaskAssignee> findByTaskIdIn(List<Long> taskIds);
    Optional<TaskAssignee> findByTaskIdAndUserId(Long taskId, Long userId);
    boolean existsByTaskIdAndUserId(Long taskId, Long userId);
}
