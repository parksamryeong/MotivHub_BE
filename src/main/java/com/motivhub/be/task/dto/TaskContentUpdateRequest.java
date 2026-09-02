package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskContentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description) {
}
