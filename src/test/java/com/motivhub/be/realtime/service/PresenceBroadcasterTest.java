package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.motivhub.be.realtime.dto.TaskPresenceMessage;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PresenceBroadcasterTest {

    @Mock private PresenceService presenceService;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private static User testUser(String suffix) {
        return User.create(SocialProvider.GITHUB, "presence-broadcaster-" + suffix,
                suffix + "@test.com", "user_" + suffix, null);
    }

    @Test
    void firstRequestBroadcastsImmediatelyWithCurrentViewers() {
        PresenceBroadcaster broadcaster =
                new PresenceBroadcaster(presenceService, userRepository, messagingTemplate, 1000);
        when(presenceService.currentViewerIds(7001L)).thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(testUser("1")));

        broadcaster.requestBroadcast(7001L);

        ArgumentCaptor<TaskPresenceMessage> captor = ArgumentCaptor.forClass(TaskPresenceMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/tasks/7001/presence"), captor.capture());
        assertThat(captor.getValue().taskId()).isEqualTo(7001L);
        assertThat(captor.getValue().viewers()).hasSize(1);
    }

    @Test
    void repeatedRequestsWithinWindowBroadcastOnlyOnce() {
        PresenceBroadcaster broadcaster =
                new PresenceBroadcaster(presenceService, userRepository, messagingTemplate, 1000);
        when(presenceService.currentViewerIds(7002L)).thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(testUser("2")));

        broadcaster.requestBroadcast(7002L);
        broadcaster.requestBroadcast(7002L);
        broadcaster.requestBroadcast(7002L);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/tasks/7002/presence"), any(TaskPresenceMessage.class));
    }

    @Test
    void flushPendingSendsOneMoreBroadcastAfterWindowElapses() throws InterruptedException {
        PresenceBroadcaster broadcaster =
                new PresenceBroadcaster(presenceService, userRepository, messagingTemplate, 20);
        when(presenceService.currentViewerIds(7003L)).thenReturn(List.of(1L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(testUser("3")));

        broadcaster.requestBroadcast(7003L); // 즉시 1회 전송(leading edge)
        broadcaster.requestBroadcast(7003L); // 쿨다운(20ms) 안이라 pending으로만 쌓임

        Thread.sleep(50); // 쿨다운 창을 확실히 넘긴다
        broadcaster.flushPending();

        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/tasks/7003/presence"), any(TaskPresenceMessage.class));
    }

    @Test
    void differentTaskIdsAreDebouncedIndependently() {
        PresenceBroadcaster broadcaster =
                new PresenceBroadcaster(presenceService, userRepository, messagingTemplate, 1000);
        when(presenceService.currentViewerIds(7004L)).thenReturn(List.of(1L));
        when(presenceService.currentViewerIds(7005L)).thenReturn(List.of(2L));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(testUser("4")));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(testUser("5")));

        broadcaster.requestBroadcast(7004L);
        broadcaster.requestBroadcast(7005L);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/tasks/7004/presence"), any(TaskPresenceMessage.class));
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/tasks/7005/presence"), any(TaskPresenceMessage.class));
    }

    @Test
    void flushPendingDoesNothingWhenNothingIsPending() {
        PresenceBroadcaster broadcaster =
                new PresenceBroadcaster(presenceService, userRepository, messagingTemplate, 1000);

        broadcaster.flushPending();

        verifyNoInteractions(messagingTemplate);
    }
}
