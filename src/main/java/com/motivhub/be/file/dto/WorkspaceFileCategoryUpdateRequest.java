package com.motivhub.be.file.dto;

import jakarta.validation.constraints.Size;

public record WorkspaceFileCategoryUpdateRequest(@Size(max = 50) String category) {
}
