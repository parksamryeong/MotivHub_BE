package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.dto.TaskPresenceMessage;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.repository.UserRepository;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
public class TaskPresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskPresenceEventListener.class);
    private static final Pattern PRESENCE_TOPIC_PATTERN = Pattern.compile("^/topic/tasks/(\\d+)/presence$");

    private final PresenceService presenceService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, PresenceSubscription> subscriptions = new ConcurrentHashMap<>();

    public TaskPresenceEventListener(PresenceService presenceService, UserRepository userRepository,
                                      SimpMessagingTemplate messagingTemplate) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : PRESENCE_TOPIC_PATTERN.matcher(destination);
            if (matcher == null || !matcher.matches()) {
                return;
            }
            Principal principal = event.getUser();
            if (principal == null) {
                return;
            }
            Long taskId = Long.valueOf(matcher.group(1));
            Long userId = Long.valueOf(principal.getName());
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();

            presenceService.join(taskId, sessionId, userId);
            subscriptions.put(subscriptionId, new PresenceSubscription(sessionId, taskId));
            broadcastViewers(taskId);
        } catch (Exception e) {
            log.warn("프레즌스 구독 처리 실패", e);
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String subscriptionId = accessor.getSubscriptionId();
            PresenceSubscription subscription = subscriptions.remove(subscriptionId);
            if (subscription == null) {
                return;
            }
            presenceService.leave(subscription.taskId(), subscription.sessionId());
            broadcastViewers(subscription.taskId());
        } catch (Exception e) {
            log.warn("프레즌스 구독 해제 처리 실패", e);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        try {
            String sessionId = event.getSessionId();
            List<Map.Entry<String, PresenceSubscription>> toRemove = subscriptions.entrySet().stream()
                    .filter(entry -> entry.getValue().sessionId().equals(sessionId))
                    .toList();
            for (Map.Entry<String, PresenceSubscription> entry : toRemove) {
                subscriptions.remove(entry.getKey());
                PresenceSubscription subscription = entry.getValue();
                presenceService.leave(subscription.taskId(), subscription.sessionId());
                broadcastViewers(subscription.taskId());
            }
        } catch (Exception e) {
            log.warn("프레즌스 연결 종료 처리 실패 - sessionId={}", event.getSessionId(), e);
        }
    }

    private void broadcastViewers(Long taskId) {
        List<Long> viewerIds = presenceService.currentViewerIds(taskId);
        List<UserSummary> viewers = userRepository.findAllById(viewerIds).stream()
                .map(UserSummary::from)
                .toList();
        messagingTemplate.convertAndSend("/topic/tasks/" + taskId + "/presence",
                new TaskPresenceMessage(taskId, viewers));
    }

    private record PresenceSubscription(String sessionId, Long taskId) {
    }
}
