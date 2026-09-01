package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByWorkspaceId(Long workspaceId);
    List<Task> findByStatusInAndDueDateBefore(List<TaskStatus> statuses, LocalDate date);
}
