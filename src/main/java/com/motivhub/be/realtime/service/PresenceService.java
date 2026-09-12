package com.motivhub.be.realtime.service;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PresenceService {

    private static final String KEY_PREFIX = "presence:task:";

    // 서버가 크래시/재배포로 죽으면 정리를 담당하던 인메모리 구독 정보도 함께 사라져서
    // Redis에 남은 프레즌스 항목을 지울 방법이 없어진다. 실제로 한 번의 열람 세션이
    // 이보다 길게 지속되는 경우는 드물다고 보고 넉넉한 값으로 12시간을 사용한다.
    private static final Duration PRESENCE_TTL = Duration.ofHours(12);

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOperations;

    public PresenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    /**
     * @param presenceKey 하나의 프레즌스 구독을 유일하게 식별하는 키. STOMP sessionId만으로는
     *                    같은 연결에 프레즌스 구독이 두 개 이상 존재하는 경우(예: UI 리마운트로
     *                    구독이 겹치는 순간)를 구분할 수 없으므로, 호출하는 쪽에서 세션과 구독을
     *                    모두 아우르는 값(예: sessionId + "::" + subscriptionId)을 넘겨야 한다.
     */
    public void join(Long taskId, String presenceKey, Long userId) {
        String key = key(taskId);
        hashOperations.put(key, presenceKey, String.valueOf(userId));
        // 해시 필드가 아니라 키 전체에 걸리는 TTL이므로, 누군가 join할 때마다 갱신되어
        // 태스크에 활성 열람자가 있는 한 자연스럽게 만료되지 않는다.
        redisTemplate.expire(key, PRESENCE_TTL);
    }

    /**
     * @param presenceKey {@link #join}에 전달했던 것과 동일한 값이어야 한다.
     */
    public void leave(Long taskId, String presenceKey) {
        hashOperations.delete(key(taskId), presenceKey);
    }

    public List<Long> currentViewerIds(Long taskId) {
        return hashOperations.values(key(taskId)).stream()
                .map(Long::valueOf)
                .distinct()
                .toList();
    }

    private String key(Long taskId) {
        return KEY_PREFIX + taskId;
    }
}
