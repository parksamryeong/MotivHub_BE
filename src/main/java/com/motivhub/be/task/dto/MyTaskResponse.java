package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.domain.TaskStatus;
import java.time.LocalDate;

public record MyTaskResponse(
        Long taskId,
        String name,
        LocalDate dueDate,
        TaskStatus status,
        TaskPriority priority,
        Long workspaceId,
        String workspaceName,
        long checklistTotal,
        long checklistCompleted,
        boolean hasComments) {

    public static MyTaskResponse of(Task task, long checklistTotal, long checklistCompleted, boolean hasComments) {
        return new MyTaskResponse(
                task.getId(), task.getName(), task.getDueDate(), task.getStatus(), task.getPriority(),
                task.getWorkspace().getId(), task.getWorkspace().getName(),
                checklistTotal, checklistCompleted, hasComments);
    }
}
