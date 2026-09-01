package com.motivhub.be.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceCreateRequest(@NotBlank @Size(max = 50) String name) {
}
