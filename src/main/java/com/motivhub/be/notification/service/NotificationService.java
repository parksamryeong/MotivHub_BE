package com.motivhub.be.notification.service;

import com.motivhub.be.notification.domain.Notification;
import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.exception.NotificationNotFoundException;
import com.motivhub.be.notification.repository.NotificationRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
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
}
