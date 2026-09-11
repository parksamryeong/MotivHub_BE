package com.motivhub.be.notification.dto;

import com.motivhub.be.notification.domain.Notification;
import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(Long id, NotificationType type, NotificationTargetType targetType,
                                    Long targetId, String message, boolean isRead,
                                    LocalDateTime createdAt, LocalDateTime readAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTargetType(),
                notification.getTargetId(), notification.getMessage(), notification.isRead(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
