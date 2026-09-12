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

    // STOMP의 구독 id는 클라이언트가 정하며, 하나의 커넥션 안에서만 유일함이 보장된다(실제로
    // Spring의 DefaultStompSession이나 stomp.js는 커넥션마다 "0"부터 다시 번호를 매긴다).
    // 이 맵은 이 리스너 하나에서 앱 전체의 모든 커넥션을 함께 관리하므로, 키를 subscriptionId
    // 단독으로 쓰면 서로 다른 커넥션의 첫 구독끼리 "0"에서 충돌해 서로의 항목을 덮어써 버린다.
    // 그래서 sessionId(서버가 커넥션마다 부여하는, 전역적으로 유일한 값)와 subscriptionId를
    // 조합한 presenceKey를 맵 키이자 PresenceService에 넘기는 식별자로 사용한다.
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
            // STOMP 프로토콜상 SUBSCRIBE 프레임의 id 헤더는 필수가 아니다. sessionId나
            // subscriptionId가 없으면 presenceKey를 만들 수도, 나중에 정리할 수도 없으므로
            // Redis에 아무것도 쓰지 않고 여기서 그냥 무시한다.
            if (sessionId == null || subscriptionId == null) {
                return;
            }
            String presenceKey = presenceKey(sessionId, subscriptionId);

            // 로컬 북키핑을 먼저 기록해 둔다. 이후 presenceService.join(...)이 던지더라도
            // (예: Redis 일시 장애) 이 항목이 남아 있어야 나중에 UNSUBSCRIBE/DISCONNECT가
            // 정리를 시도할 수 있다. 반대 순서였다면 join만 성공하고 로컬 항목이 없어 Redis
            // 항목이 영원히 고아로 남을 수 있다.
            subscriptions.put(presenceKey, new PresenceSubscription(sessionId, taskId));
            presenceService.join(taskId, presenceKey, userId);
            broadcastViewers(taskId);
        } catch (Exception e) {
            log.warn("프레즌스 구독 처리 실패", e);
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();
            if (sessionId == null || subscriptionId == null) {
                return;
            }
            String presenceKey = presenceKey(sessionId, subscriptionId);
            PresenceSubscription subscription = subscriptions.remove(presenceKey);
            if (subscription == null) {
                return;
            }
            presenceService.leave(subscription.taskId(), presenceKey);
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
                // entry.getKey()가 곧 handleSubscribe에서 join(...)에 넘겼던 presenceKey와
                // 동일한 값이므로 그대로 재사용한다(다시 조합하지 않는다).
                presenceService.leave(subscription.taskId(), entry.getKey());
                broadcastViewers(subscription.taskId());
            }
        } catch (Exception e) {
            log.warn("프레즌스 연결 종료 처리 실패 - sessionId={}", event.getSessionId(), e);
        }
    }

    private static String presenceKey(String sessionId, String subscriptionId) {
        return sessionId + "::" + subscriptionId;
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
