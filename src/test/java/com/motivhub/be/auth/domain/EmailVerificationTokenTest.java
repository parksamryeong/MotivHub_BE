package com.motivhub.be.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmailVerificationTokenTest {

    @Test
    void createStartsUnconsumed() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        EmailVerificationToken token = EmailVerificationToken.create("token-value", "test@example.com", expiresAt);

        assertThat(token.getToken()).isEqualTo("token-value");
        assertThat(token.getEmail()).isEqualTo("test@example.com");
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test
    void consumeSetsConsumedAt() {
        EmailVerificationToken token = EmailVerificationToken.create(
                "token-value", "test@example.com", LocalDateTime.now().plusDays(7));

        token.consume();

        assertThat(token.getConsumedAt()).isNotNull();
    }
}
