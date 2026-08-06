package com.motivhub.be.user.dto;

import jakarta.validation.constraints.NotBlank;

public record NicknameUpdateRequest(@NotBlank String nickname) {
}
