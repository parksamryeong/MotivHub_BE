package com.motivhub.be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class VerificationAttemptLimiterTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void secondCooldownAttemptWithinWindowIsRejected() {
        VerificationAttemptLimiter limiter = new VerificationAttemptLimiter(redisTemplate, 30);
        String email = "cooldown-test@example.com";

        assertThat(limiter.tryStartCooldown(email)).isTrue();
        assertThat(limiter.tryStartCooldown(email)).isFalse();
    }

    @Test
    void attemptCounterAccumulatesAcrossMultipleFailuresThenResets() {
        VerificationAttemptLimiter limiter = new VerificationAttemptLimiter(redisTemplate, 0);
        String email = "attempt-counter-test@example.com";

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

    @Test
    void concurrentAttemptsAreEachCountedExactlyOnceWithNoLostUpdates() throws Exception {
        VerificationAttemptLimiter limiter = new VerificationAttemptLimiter(redisTemplate, 0);
        String email = "concurrent-attempt-test@example.com";
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return limiter.recordAttempt(email);
            }));
        }
        ready.await();
        start.countDown();

        Set<Long> results = new HashSet<>();
        for (Future<Long> future : futures) {
            results.add(future.get());
        }
        executor.shutdown();

        // 원자적 INCR라면 20개 스레드가 1..20 사이의 서로 다른 값을 정확히 하나씩 받아야 한다
        // (유실된 업데이트가 있다면 중복되거나 20개보다 적은 고유값이 나온다).
        assertThat(results).containsExactlyInAnyOrderElementsOf(
                LongStream.rangeClosed(1, threadCount).boxed().toList());

        long acceptedCount = results.stream().filter(n -> !limiter.exceedsLimit(n)).count();
        assertThat(acceptedCount).isEqualTo(5L);
    }
}
