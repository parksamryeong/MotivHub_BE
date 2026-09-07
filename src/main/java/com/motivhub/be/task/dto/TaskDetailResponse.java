package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskDetailResponse(
        Long id, Long workspaceId, String name, String description,
        LocalDate startDate, LocalDate dueDate, TaskStatus status,
        List<UserSummary> assignees, UserSummary createdBy, LocalDateTime createdAt,
        List<TaskChecklistItemResponse> checklistItems) {

    public static TaskDetailResponse of(TaskResponse task, List<TaskChecklistItemResponse> checklistItems) {
        return new TaskDetailResponse(
                task.id(), task.workspaceId(), task.name(), task.description(),
                task.startDate(), task.dueDate(), task.status(),
                task.assignees(), task.createdBy(), task.createdAt(), checklistItems);
    }
}
