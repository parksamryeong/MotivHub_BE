package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskNoteUpdateRequest(@NotNull @Size(max = 50000) String content) {
}
