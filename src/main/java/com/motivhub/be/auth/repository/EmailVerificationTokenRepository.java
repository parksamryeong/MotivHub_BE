package com.motivhub.be.auth.repository;

import com.motivhub.be.auth.domain.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(String email);
}
