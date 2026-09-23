package com.motivhub.be.notification.controller;

import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.dto.NotificationSettingResponse;
import com.motivhub.be.notification.dto.NotificationSettingUpdateRequest;
import com.motivhub.be.notification.dto.UnreadCountResponse;
import com.motivhub.be.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<Page<NotificationResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(notificationService.list(userId, pageable));
    }

    @GetMapping("/api/notifications/settings")
    public ResponseEntity<List<NotificationSettingResponse>> getSettings(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(notificationService.getSettings(userId));
    }

    @PatchMapping("/api/notifications/settings/{type}")
    public ResponseEntity<Void> updateSetting(
            @AuthenticationPrincipal Long userId, @PathVariable NotificationType type,
            @Valid @RequestBody NotificationSettingUpdateRequest request) {
        notificationService.updateSetting(userId, type, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/notifications/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.unreadCount(userId)));
    }

    @PatchMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        notificationService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
