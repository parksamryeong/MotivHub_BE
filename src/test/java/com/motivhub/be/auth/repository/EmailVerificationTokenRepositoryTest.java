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
    void findByTokenReturnsMatchingToken() {
        EmailVerificationToken saved = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("find-by-token-test", "findme@example.com",
                        LocalDateTime.now().plusDays(7)));

        Optional<EmailVerificationToken> found =
                emailVerificationTokenRepository.findByToken("find-by-token-test");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByTokenReturnsEmptyForUnknownToken() {
        Optional<EmailVerificationToken> found =
                emailVerificationTokenRepository.findByToken("no-such-token");

        assertThat(found).isEmpty();
    }
}
