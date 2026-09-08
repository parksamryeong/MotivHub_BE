package com.motivhub.be.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FilePresignRequest(
        @NotBlank @Size(max = 255) String fileName, @NotBlank @Size(max = 255) String contentType,
        @NotNull @Positive Long fileSize) {
}
