package com.motivhub.be.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.service.NotificationService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // NOTE: NotificationService.notify()는 @Transactional(propagation = REQUIRES_NEW)라서, 이 테스트
    // 메서드(외부 @Transactional)에서 방금 save한 유저는 아직 커밋되지 않은 상태다. notify()가 별도
    // 트랜잭션/커넥션에서 findById로 그 유저를 조회하면 안 보여서 UserNotFoundException이 발생한다
    // (NotificationServiceTest.newUser()에서 이미 확인/해결된 것과 동일한 패턴). 유저 저장을 즉시
    // 커밋시켜서 REQUIRES_NEW 트랜잭션에서도 보이게 한다.
    private User newUser(String suffix) {
        User user = userRepository.save(User.create(
                SocialProvider.GITHUB, "notif-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        return user;
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void listAndUnreadCountAndMarkReadFlow() throws Exception {
        User user = newUser("api1");
        notificationService.notify(user.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 1L, "API 테스트 알림");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("API 테스트 알림"))
                .andExpect(jsonPath("$.content[0].isRead").value(false));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        Long notificationId = notificationService.list(user.getId(), PageRequest.of(0, 20))
                .getContent().get(0).id();

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markingSomeoneElsesNotificationReadReturns404() throws Exception {
        User owner = newUser("api2-owner");
        User stranger = newUser("api2-stranger");
        notificationService.notify(owner.getId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, 1L, "남의 알림");
        Long notificationId = notificationService.list(owner.getId(), PageRequest.of(0, 20))
                .getContent().get(0).id();

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + tokenFor(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void markAllReadClearsUnreadCount() throws Exception {
        User user = newUser("api3");
        notificationService.notify(user.getId(), NotificationType.ASSIGNEE_ADDED, NotificationTargetType.TASK, 1L, "알림1");
        notificationService.notify(user.getId(), NotificationType.ASSIGNEE_ADDED, NotificationTargetType.TASK, 2L, "알림2");

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(jsonPath("$.count").value(0));
    }
}
