package com.motivhub.be.realtime.service;

import java.util.List;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PresenceService {

    private static final String KEY_PREFIX = "presence:task:";

    private final HashOperations<String, String, String> hashOperations;

    public PresenceService(StringRedisTemplate redisTemplate) {
        this.hashOperations = redisTemplate.opsForHash();
    }

    public void join(Long taskId, String sessionId, Long userId) {
        hashOperations.put(key(taskId), sessionId, String.valueOf(userId));
    }

    public void leave(Long taskId, String sessionId) {
        hashOperations.delete(key(taskId), sessionId);
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
