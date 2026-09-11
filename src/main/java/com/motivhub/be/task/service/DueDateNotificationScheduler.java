package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.event.DueDateApproachingEvent;
import com.motivhub.be.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DueDateNotificationScheduler {

    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DueDateNotificationScheduler(TaskRepository taskRepository, ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void notifyApproachingDueDates() {
        LocalDate targetDate = LocalDate.now().plusDays(2);
        List<Task> tasks = taskRepository.findByStatusInAndDueDate(
                List.of(TaskStatus.WAITING, TaskStatus.IN_PROGRESS), targetDate);
        tasks.forEach(task -> eventPublisher.publishEvent(new DueDateApproachingEvent(task.getId())));
    }
}
