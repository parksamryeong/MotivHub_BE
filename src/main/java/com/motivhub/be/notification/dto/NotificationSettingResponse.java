package com.motivhub.be.notification.dto;

import com.motivhub.be.notification.domain.NotificationType;

public record NotificationSettingResponse(NotificationType type, boolean enabled) {
}
