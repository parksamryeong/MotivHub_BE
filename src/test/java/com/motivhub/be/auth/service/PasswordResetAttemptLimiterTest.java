package com.motivhub.be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class PasswordResetAttemptLimiterTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void secondCooldownAttemptWithinWindowIsRejected() {
        PasswordResetAttemptLimiter limiter = new PasswordResetAttemptLimiter(redisTemplate, 30);
        String email = "pwreset-cooldown-test@example.com";

        assertThat(limiter.tryStartCooldown(email)).isTrue();
        assertThat(limiter.tryStartCooldown(email)).isFalse();
    }

    @Test
    void attemptCounterAccumulatesAcrossMultipleFailuresThenResets() {
        PasswordResetAttemptLimiter limiter = new PasswordResetAttemptLimiter(redisTemplate, 0);
        String email = "pwreset-attempt-counter-test@example.com";

        long lastAttempt = 0;
        for (int i = 0; i < 5; i++) {
            lastAttempt = limiter.recordAttempt(email);
            assertThat(limiter.exceedsLimit(lastAttempt)).isFalse();
        }
        assertThat(lastAttempt).isEqualTo(5L);

        long sixthAttempt = limiter.recordAttempt(email);
        assertThat(limiter.exceedsLimit(sixthAttempt)).isTrue();

        limiter.reset(email);
        long afterReset = limiter.recordAttempt(email);
        assertThat(limiter.exceedsLimit(afterReset)).isFalse();
        assertThat(afterReset).isEqualTo(1L);
    }
}
