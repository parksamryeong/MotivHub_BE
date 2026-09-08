package com.motivhub.be.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueCommentCreateRequest(@NotBlank @Size(max = 1000) String content) {
}
