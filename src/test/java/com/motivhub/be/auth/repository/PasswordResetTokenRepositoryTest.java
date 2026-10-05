package com.motivhub.be.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.auth.domain.PasswordResetToken;
import com.motivhub.be.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PasswordResetTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsUnconsumedToken() {
        PasswordResetToken saved = passwordResetTokenRepository.save(
                PasswordResetToken.create("111111", "findme@example.com", LocalDateTime.now().plusMinutes(5)));

        Optional<PasswordResetToken> found = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("findme@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsEmptyWhenConsumed() {
        PasswordResetToken token = PasswordResetToken.create(
                "222222", "consumed@example.com", LocalDateTime.now().plusMinutes(5));
        token.consume();
        passwordResetTokenRepository.save(token);

        Optional<PasswordResetToken> found = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("consumed@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsLatestWhenMultipleExist() {
        passwordResetTokenRepository.save(
                PasswordResetToken.create("333333", "multi@example.com", LocalDateTime.now().plusMinutes(5)));
        PasswordResetToken latest = passwordResetTokenRepository.save(
                PasswordResetToken.create("444444", "multi@example.com", LocalDateTime.now().plusMinutes(5)));

        Optional<PasswordResetToken> found = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("multi@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsEmptyForUnknownEmail() {
        Optional<PasswordResetToken> found = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("no-such-email@example.com");

        assertThat(found).isEmpty();
    }
}
