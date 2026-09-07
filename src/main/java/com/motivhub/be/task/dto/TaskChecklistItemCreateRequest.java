package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskChecklistItemCreateRequest(@NotBlank @Size(max = 200) String content) {
}
