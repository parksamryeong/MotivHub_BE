package com.motivhub.be.realtime.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * 편집 토픽에서 누군가 구독을 해제하면(= 문서를 닫았을 가능성이 있으면) 미저장 버퍼가 남아 있는 한
 * 즉시 저장을 시도한다.
 *
 * <p>{@code SimpUserRegistry}로 "정말 마지막 구독자인지"를 정밀 판정하지 않는다 - 구독 해제 이벤트와
 * 레지스트리 갱신의 순서가 보장되지 않아 위험하기 때문이다(프레즌스가 같은 이유로 자체 북키핑을 쓴다).
 * 대신 구독 해제마다 무조건 저장을 시도하되, {@link TaskEditAutosaveScheduler#requestSaveIfDue}의
 * 재요청 억제(retry-seconds) 로직에 중복 브로드캐스트 방지를 위임한다. CRDT 수렴 특성상 누가 응답해도
 * 같은 텍스트가 저장되므로 조금 이른 저장은 무해하다.
 *
 * <p>STOMP UNSUBSCRIBE 프레임에는 {@code destination} 헤더가 없고 SUBSCRIBE 때 클라이언트가 정한
 * {@code id}만 들어온다. 그래서 목적지를 UNSUBSCRIBE에서 곧바로 읽을 수 없고, SUBSCRIBE 시점에
 * (sessionId, subscriptionId) -> (taskId, field)를 인메모리로 기록해 두고 해제 때 되찾는다 -
 * {@code TaskPresenceEventListener}가 같은 제약 때문에 쓰는 방식과 동일하다. subscriptionId는 커넥션
 * 안에서만 유일하므로(Spring의 DefaultStompSession, stomp.js 모두 커넥션마다 "0"부터 다시 매긴다)
 * 반드시 sessionId와 조합해서 키로 써야 서로 다른 커넥션의 첫 구독끼리 덮어쓰지 않는다.
 */
@Component
public class TaskEditLastViewerFlushListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEditLastViewerFlushListener.class);
    private static final Pattern EDIT_TOPIC_PATTERN =
            Pattern.compile("^/topic/tasks/(\\d+)/(description|note)/edits$");

    private final TaskEditBufferService bufferService;
    private final TaskEditAutosaveScheduler autosaveScheduler;

    private final Map<String, EditSubscription> subscriptions = new ConcurrentHashMap<>();

    public TaskEditLastViewerFlushListener(TaskEditBufferService bufferService,
                                            TaskEditAutosaveScheduler autosaveScheduler) {
        this.bufferService = bufferService;
        this.autosaveScheduler = autosaveScheduler;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : EDIT_TOPIC_PATTERN.matcher(destination);
            if (matcher == null || !matcher.matches()) {
                return;
            }
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();
            // STOMP상 SUBSCRIBE의 id 헤더는 필수가 아니다. 둘 중 하나라도 없으면 나중에 UNSUBSCRIBE와
            // 짝지을 수 없으므로 아무것도 기록하지 않는다(자동저장 폴러가 대신 커버한다).
            if (sessionId == null || subscriptionId == null) {
                return;
            }
            subscriptions.put(subscriptionKey(sessionId, subscriptionId), new EditSubscription(
                    sessionId, Long.valueOf(matcher.group(1)),
                    TaskEditableField.fromPathSegment(matcher.group(2))));
        } catch (Exception e) {
            log.warn("편집 토픽 구독 기록 실패", e);
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
            EditSubscription subscription = subscriptions.remove(subscriptionKey(sessionId, subscriptionId));
            if (subscription == null) {
                return;
            }
            if (bufferService.isEmpty(subscription.taskId(), subscription.field())) {
                return;
            }
            autosaveScheduler.requestSaveIfDue(subscription.taskId(), subscription.field());
        } catch (Exception e) {
            log.warn("구독 해제 시 자동저장 트리거 실패", e);
        }
    }

    // 커넥션이 끊기면 UNSUBSCRIBE 없이 사라지므로, 기록만 정리한다(맵이 무한히 자라지 않도록).
    // 여기서 저장을 시도하지 않는 이유: 아직 다른 뷰어가 남아 있다면 자동저장 폴러가 유휴 3초 안에
    // 어차피 저장을 요청하고, 아무도 남지 않았다면 평문을 만들어줄 클라이언트가 없어 저장 자체가
    // 불가능하다(설계 문서의 "브라우저 강제 종료 시 유실"은 의도적으로 수용한 한계).
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        try {
            String sessionId = event.getSessionId();
            List<String> keysToRemove = subscriptions.entrySet().stream()
                    .filter(entry -> entry.getValue().sessionId().equals(sessionId))
                    .map(Map.Entry::getKey)
                    .toList();
            keysToRemove.forEach(subscriptions::remove);
        } catch (Exception e) {
            log.warn("편집 토픽 구독 기록 정리 실패 - sessionId={}", event.getSessionId(), e);
        }
    }

    private static String subscriptionKey(String sessionId, String subscriptionId) {
        return sessionId + "::" + subscriptionId;
    }

    private record EditSubscription(String sessionId, Long taskId, TaskEditableField field) {
    }
}
