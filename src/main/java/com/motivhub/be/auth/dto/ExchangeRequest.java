package com.motivhub.be.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ExchangeRequest(@NotBlank String code) {
}
