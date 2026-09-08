package com.motivhub.be.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WorkspaceFileConfirmRequest(
        @NotBlank String fileKey, @NotBlank String fileName,
        @NotNull @Positive Long fileSize, @NotBlank String contentType) {
}
