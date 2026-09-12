package com.motivhub.be.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentPromoteToIssueRequest(@NotBlank @Size(max = 100) String title) {
}
