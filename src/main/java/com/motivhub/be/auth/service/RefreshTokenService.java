package com.motivhub.be.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenExpireMs;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-expire-ms}") long refreshTokenExpireMs) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpireMs = refreshTokenExpireMs;
    }

    @Deprecated
    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId, refreshToken, Duration.ofMillis(refreshTokenExpireMs));
    }

    @Deprecated
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }
    
    @Deprecated
    public void delete(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    public void save(Long userId, String deviceId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId, deviceId), refreshToken, Duration.ofMillis(refreshTokenExpireMs));
    }

    public Optional<String> find(Long userId, String deviceId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId, deviceId)));
    }

    public void delete(Long userId, String deviceId) {
        redisTemplate.delete(key(userId, deviceId));
    }

    public void deleteAll(Long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(Long userId, String deviceId) {
        return KEY_PREFIX + userId + ":" + deviceId;
    }
}
