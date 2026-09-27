package com.motivhub.be.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EmailVerificationTokenTest {

    @Test
    void createStartsUnconsumed() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        EmailVerificationToken token = EmailVerificationToken.create("123456", "test@example.com", expiresAt);

        assertThat(token.getCode()).isEqualTo("123456");
        assertThat(token.getEmail()).isEqualTo("test@example.com");
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test
    void consumeSetsConsumedAt() {
        EmailVerificationToken token = EmailVerificationToken.create(
                "123456", "test@example.com", LocalDateTime.now().plusMinutes(5));

        token.consume();

        assertThat(token.getConsumedAt()).isNotNull();
    }

    @Test
    void matchesCodeReturnsTrueOnlyForExactCode() {
        EmailVerificationToken token = EmailVerificationToken.create(
                "123456", "test@example.com", LocalDateTime.now().plusMinutes(5));

        assertThat(token.matchesCode("123456")).isTrue();
        assertThat(token.matchesCode("654321")).isFalse();
    }
}
