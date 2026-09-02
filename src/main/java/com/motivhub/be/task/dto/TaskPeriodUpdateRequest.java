package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TaskPeriodUpdateRequest(@NotNull LocalDate startDate, @NotNull LocalDate dueDate) {
}
