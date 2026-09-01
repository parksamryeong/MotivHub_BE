package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate dueDate,
        List<Long> assigneeIds) {
}
