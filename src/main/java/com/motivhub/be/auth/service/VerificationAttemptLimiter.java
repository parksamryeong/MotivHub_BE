package com.motivhub.be.auth.service;

import java.time.Duration;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class VerificationAttemptLimiter {

    private static final String ATTEMPT_KEY_PREFIX = "email-verification-attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "email-verification-cooldown:";
    private static final long ATTEMPT_WINDOW_SECONDS = Duration.ofMinutes(30).getSeconds();
    private static final int MAX_ATTEMPTS = 5;

    // INCR와 EXPIRE를 하나의 원자적 연산으로 묶는다 - 따로 호출하면 그 사이에 죽었을 때
    // TTL이 영영 안 걸려서(영구 잠금) 위험하고, 무엇보다 "확인 후 증가"가 아니라 "증가 후
    // 결과값으로 판단"해야 동시 요청 사이의 경쟁을 원천 차단할 수 있다. count==1이 아니라
    // TTL이 실제로 없는지(-1)를 확인해서, 무슨 이유로든 TTL 없이 남아있는 키를 만나도
    // 스스로 복구된다.
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long resendCooldownSeconds;

    public VerificationAttemptLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.email-verification.resend-cooldown-seconds:30}") long resendCooldownSeconds) {
        this.redisTemplate = redisTemplate;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    /**
     * 재발급 자체에 쿨다운을 건다 - true면 이번 요청을 허용(쿨다운 시작), false면 아직 쿨다운 중이라 거부.
     * cooldownSeconds가 0 이하면 쿨다운을 아예 쓰지 않는다(테스트 프로파일에서 빠른 반복 호출을 위해).
     */
    public boolean tryStartCooldown(String email) {
        if (resendCooldownSeconds <= 0) {
            return true;
        }
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(COOLDOWN_KEY_PREFIX + email, "1", Duration.ofSeconds(resendCooldownSeconds));
        return Boolean.TRUE.equals(firstTime);
    }

    /**
     * 이 이메일의 시도 횟수를 원자적으로 하나 증가시키고 그 결과(1부터 시작)를 반환한다.
     * 호출자는 반환값이 {@link #exceedsLimit}이면 코드 비교 자체를 하지 말고 즉시 거부해야
     * 한다 - 비교 후에 증가시키면 동시 요청 사이에서 경쟁이 발생한다.
     */
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
