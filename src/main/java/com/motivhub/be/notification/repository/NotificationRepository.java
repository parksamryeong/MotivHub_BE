package com.motivhub.be.notification.repository;

import com.motivhub.be.notification.domain.Notification;
import com.motivhub.be.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    List<Notification> findByRecipientIdAndReadFalse(Long recipientId);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    boolean existsByRecipientIdAndTypeAndTargetIdAndCreatedAtGreaterThanEqual(
            Long recipientId, NotificationType type, Long targetId, LocalDateTime after);
}
