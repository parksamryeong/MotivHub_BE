package com.motivhub.be.notification.repository;

import com.motivhub.be.notification.domain.NotificationSetting;
import com.motivhub.be.notification.domain.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findByUserId(Long userId);

    Optional<NotificationSetting> findByUserIdAndType(Long userId, NotificationType type);
}
