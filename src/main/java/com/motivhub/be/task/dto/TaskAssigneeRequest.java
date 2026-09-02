package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotNull;

public record TaskAssigneeRequest(@NotNull Long userId) {
}
