package com.motivhub.be.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.exception.NotificationNotFoundException;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class NotificationServiceTest extends AbstractIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "notif-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    @Test
    void notifyCreatesUnreadNotificationVisibleInList() {
        User recipient = newUser("n1");

        notificationService.notify(recipient.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 999L, "'테스트 태스크'의 담당자로 지정되었습니다.");

        Page<NotificationResponse> page = notificationService.list(recipient.getId(), PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        NotificationResponse notification = page.getContent().get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.ASSIGNEE_ADDED);
        assertThat(notification.targetType()).isEqualTo(NotificationTargetType.TASK);
        assertThat(notification.targetId()).isEqualTo(999L);
        assertThat(notification.message()).isEqualTo("'테스트 태스크'의 담당자로 지정되었습니다.");
        assertThat(notification.isRead()).isFalse();
        assertThat(notificationService.unreadCount(recipient.getId())).isEqualTo(1L);
    }

    @Test
    void listReturnsNewestFirst() {
        User recipient = newUser("n2");
        notificationService.notify(recipient.getId(), NotificationType.TASK_COMMENT_ADDED,
                NotificationTargetType.TASK, 1L, "첫 알림");
        notificationService.notify(recipient.getId(), NotificationType.TASK_COMMENT_ADDED,
                NotificationTargetType.TASK, 1L, "두번째 알림");

        Page<NotificationResponse> page = notificationService.list(
                recipient.getId(), PageRequest.of(0, 20,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));

        assertThat(page.getContent()).extracting(NotificationResponse::message)
                .containsExactly("두번째 알림", "첫 알림");
    }

    @Test
    void markReadFlipsIsReadAndDropsUnreadCount() {
        User recipient = newUser("n3");
        notificationService.notify(recipient.getId(), NotificationType.CHECKLIST_COMPLETED,
                NotificationTargetType.TASK, 1L, "체크리스트 완료");
        Long notificationId = notificationService.list(recipient.getId(), PageRequest.of(0, 20))
                .getContent().get(0).id();

        notificationService.markRead(recipient.getId(), notificationId);

        assertThat(notificationService.unreadCount(recipient.getId())).isZero();
        assertThat(notificationService.list(recipient.getId(), PageRequest.of(0, 20))
                .getContent().get(0).isRead()).isTrue();
    }

    @Test
    void markReadOnSomeoneElsesNotificationThrows() {
        User recipient = newUser("n4");
        User stranger = newUser("n4-stranger");
        notificationService.notify(recipient.getId(), NotificationType.CHECKLIST_COMPLETED,
                NotificationTargetType.TASK, 1L, "남의 알림");
        Long notificationId = notificationService.list(recipient.getId(), PageRequest.of(0, 20))
                .getContent().get(0).id();

        assertThatThrownBy(() -> notificationService.markRead(stranger.getId(), notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markAllReadClearsUnreadCountForThatUserOnly() {
        User recipient = newUser("n5");
        User other = newUser("n5-other");
        notificationService.notify(recipient.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 1L, "알림 A");
        notificationService.notify(recipient.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 2L, "알림 B");
        notificationService.notify(other.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 3L, "다른 유저 알림");

        notificationService.markAllRead(recipient.getId());

        assertThat(notificationService.unreadCount(recipient.getId())).isZero();
        assertThat(notificationService.unreadCount(other.getId())).isEqualTo(1L);
    }

    @Test
    void alreadyNotifiedTodayIsFalseThenTrueAfterFirstNotify() {
        User recipient = newUser("n6");

        assertThat(notificationService.alreadyNotifiedToday(
                recipient.getId(), NotificationType.DUE_DATE_APPROACHING, 42L)).isFalse();

        notificationService.notify(recipient.getId(), NotificationType.DUE_DATE_APPROACHING,
                NotificationTargetType.TASK, 42L, "마감일 임박");

        assertThat(notificationService.alreadyNotifiedToday(
                recipient.getId(), NotificationType.DUE_DATE_APPROACHING, 42L)).isTrue();
    }
}
