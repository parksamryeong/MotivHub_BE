package com.motivhub.be.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueCreateRequest(
        @NotNull Long workspaceId, @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String problemDescription, @Size(max = 2000) String solution) {
}
