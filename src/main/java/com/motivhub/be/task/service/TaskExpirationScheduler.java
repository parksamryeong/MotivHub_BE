package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskExpirationScheduler {

    private final TaskRepository taskRepository;

    public TaskExpirationScheduler(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void expireOverdueTasks() {
        List<Task> overdueTasks = taskRepository.findByStatusInAndDueDateBefore(
                List.of(TaskStatus.WAITING, TaskStatus.IN_PROGRESS), LocalDate.now());
        overdueTasks.forEach(Task::expire);
    }
}
