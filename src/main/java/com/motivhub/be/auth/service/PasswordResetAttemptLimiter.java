package com.motivhub.be.auth.service;

import java.time.Duration;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetAttemptLimiter {

    private static final String ATTEMPT_KEY_PREFIX = "password-reset-attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "password-reset-cooldown:";
    private static final long ATTEMPT_WINDOW_SECONDS = Duration.ofMinutes(30).getSeconds();
    private static final int MAX_ATTEMPTS = 5;

    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long resendCooldownSeconds;

    public PasswordResetAttemptLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.password-reset.resend-cooldown-seconds:30}") long resendCooldownSeconds) {
        this.redisTemplate = redisTemplate;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public boolean tryStartCooldown(String email) {
        if (resendCooldownSeconds <= 0) {
            return true;
        }
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY_PREFIX + email, "1", Duration.ofSeconds(resendCooldownSeconds));
        return Boolean.TRUE.equals(firstTime);
    }

    public long recordAttempt(String email) {
        Long count = redisTemplate.execute(
                INCREMENT_WITH_EXPIRY_SCRIPT,
                Collections.singletonList(ATTEMPT_KEY_PREFIX + email),
                String.valueOf(ATTEMPT_WINDOW_SECONDS));
        return count == null ? 1L : count;
    }

    public boolean exceedsLimit(long attemptNumber) {
        return attemptNumber > MAX_ATTEMPTS;
    }

    public void reset(String email) {
        redisTemplate.delete(ATTEMPT_KEY_PREFIX + email);
    }
}
