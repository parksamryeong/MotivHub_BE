package com.motivhub.be.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingUpdateRequest(@NotNull Boolean enabled) {
}
