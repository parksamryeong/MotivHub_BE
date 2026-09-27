package com.motivhub.be.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.auth.domain.EmailVerificationToken;
import com.motivhub.be.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmailVerificationTokenRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsUnconsumedToken() {
        EmailVerificationToken saved = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("111111", "findme@example.com", LocalDateTime.now().plusMinutes(5)));

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("findme@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsEmptyWhenConsumed() {
        EmailVerificationToken token = EmailVerificationToken.create(
                "222222", "consumed@example.com", LocalDateTime.now().plusMinutes(5));
        token.consume();
        emailVerificationTokenRepository.save(token);

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("consumed@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsLatestWhenMultipleExist() {
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("333333", "multi@example.com", LocalDateTime.now().plusMinutes(5)));
        EmailVerificationToken latest = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("444444", "multi@example.com", LocalDateTime.now().plusMinutes(5)));

        Optional<EmailVerificationToken> found = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("multi@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(latest.getId());
    }

    @Test
    void findFirstByEmailAndConsumedAtIsNullOrderByIdDescReturnsEmptyForUnknownEmail() {
        Optional<EmailVerificationToken> found = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc("no-such-email@example.com");

        assertThat(found).isEmpty();
    }
}
