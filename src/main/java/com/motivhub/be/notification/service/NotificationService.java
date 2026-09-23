package com.motivhub.be.notification.service;

import com.motivhub.be.notification.domain.Notification;
import com.motivhub.be.notification.domain.NotificationSetting;
import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.dto.NotificationSettingResponse;
import com.motivhub.be.notification.exception.NotificationNotFoundException;
import com.motivhub.be.notification.repository.NotificationRepository;
import com.motivhub.be.notification.repository.NotificationSettingRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationSettingRepository notificationSettingRepository,
                                UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long recipientId, NotificationType type, NotificationTargetType targetType,
                        Long targetId, String message) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        notificationRepository.save(Notification.create(recipient, type, targetType, targetId, message));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean alreadyNotifiedToday(Long recipientId, NotificationType type, Long targetId) {
        return notificationRepository.existsByRecipientIdAndTypeAndTargetIdAndCreatedAtGreaterThanEqual(
                recipientId, type, targetId, LocalDate.now().atStartOfDay());
    }

    public Page<NotificationResponse> list(Long userId, Pageable pageable) {
        return notificationRepository.findByRecipientId(userId, pageable).map(NotificationResponse::from);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException("알림을 찾을 수 없습니다."));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.findByRecipientIdAndReadFalse(userId).forEach(Notification::markRead);
    }

    public List<NotificationSettingResponse> getSettings(Long userId) {
        Map<NotificationType, Boolean> overrides = notificationSettingRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationSetting::getType, NotificationSetting::isEnabled));
        return Arrays.stream(NotificationType.values())
                .map(type -> new NotificationSettingResponse(type, overrides.getOrDefault(type, true)))
                .toList();
    }

    @Transactional
    public void updateSetting(Long userId, NotificationType type, boolean enabled) {
        notificationSettingRepository.findByUserIdAndType(userId, type)
                .ifPresentOrElse(
                        setting -> setting.changeEnabled(enabled),
                        () -> {
                            User user = userRepository.findById(userId)
                                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
                            notificationSettingRepository.save(NotificationSetting.create(user, type, enabled));
                        });
    }

    public boolean isEnabled(Long userId, NotificationType type) {
        return notificationSettingRepository.findByUserIdAndType(userId, type)
                .map(NotificationSetting::isEnabled)
                .orElse(true);
    }
}
