package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentCreateRequest(@NotBlank @Size(max = 1000) String content) {
}
