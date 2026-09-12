package com.motivhub.be.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WorkspaceFileConfirmRequest(
        @NotBlank String fileKey, @NotBlank @Size(max = 255) String fileName,
        @NotNull @Positive Long fileSize, @NotBlank @Size(max = 255) String contentType,
        @Size(max = 50) String category, Long taskId) {
}
