package com.motivhub.be.realtime.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class TaskEditChannelRegistry {

    // 편집 토픽(/topic/tasks/{id}/{field}/edits)을 정상 SUBSCRIBE로 통과한 세션만 그에 대응하는
    // SEND(타이핑 업데이트/스냅샷 응답)를 보낼 수 있게, "이 세션이 이 토픽을 구독했다"는 사실만
    // 메모리에 기록해둔다. SEND마다 DB로 멤버십을 재확인하지 않기 위한 캐시 - 프레즌스가 세션별 구독을
    // 인메모리로 추적하는 것과 같은 이유.
    private final Set<String> authorizedSessionTopics = ConcurrentHashMap.newKeySet();

    public void authorize(String sessionId, String topic) {
        authorizedSessionTopics.add(key(sessionId, topic));
    }

    public boolean isAuthorized(String sessionId, String topic) {
        return authorizedSessionTopics.contains(key(sessionId, topic));
    }

    /**
     * 특정 세션의 특정 편집 토픽 SEND 인가만 회수한다. 워크스페이스에서 제외된 멤버의 편집 토픽
     * 구독을 강제 해제할 때(WorkspaceMemberRemovedSessionCleaner) 같이 호출해서, 커넥션이 살아 있는
     * 동안 계속 편집을 릴레이·버퍼링하는 것을 막는다.
     */
    public void revoke(String sessionId, String topic) {
        authorizedSessionTopics.remove(key(sessionId, topic));
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String prefix = event.getSessionId() + "::";
        authorizedSessionTopics.removeIf(entry -> entry.startsWith(prefix));
    }

    private String key(String sessionId, String topic) {
        return sessionId + "::" + topic;
    }
}
