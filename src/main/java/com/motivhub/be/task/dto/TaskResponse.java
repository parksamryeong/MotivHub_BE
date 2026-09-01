package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskResponse(
        Long id,
        Long workspaceId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate dueDate,
        TaskStatus status,
        List<Long> assigneeIds,
        Long createdById,
        LocalDateTime createdAt) {

    public static TaskResponse of(Task task, List<Long> assigneeIds) {
        return new TaskResponse(
                task.getId(), task.getWorkspace().getId(), task.getName(), task.getDescription(),
                task.getStartDate(), task.getDueDate(), task.getStatus(), assigneeIds,
                task.getCreatedBy().getId(), task.getCreatedAt());
    }
}
