package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(@NotNull TaskStatus status) {
}
