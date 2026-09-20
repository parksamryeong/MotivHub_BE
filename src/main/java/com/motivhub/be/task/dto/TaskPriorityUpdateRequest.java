package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskPriority;
import jakarta.validation.constraints.NotNull;

public record TaskPriorityUpdateRequest(@NotNull TaskPriority priority) {
}
