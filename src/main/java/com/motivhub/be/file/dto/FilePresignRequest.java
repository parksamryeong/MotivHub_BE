package com.motivhub.be.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FilePresignRequest(
        @NotBlank String fileName, @NotBlank String contentType, @NotNull @Positive Long fileSize) {
}
