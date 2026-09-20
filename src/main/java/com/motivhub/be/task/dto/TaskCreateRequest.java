package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskPriority;
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
        List<Long> assigneeIds,
        TaskPriority priority) {

    public TaskCreateRequest(String name, String description, LocalDate startDate, LocalDate dueDate,
                              List<Long> assigneeIds) {
        this(name, description, startDate, dueDate, assigneeIds, null);
    }
}
