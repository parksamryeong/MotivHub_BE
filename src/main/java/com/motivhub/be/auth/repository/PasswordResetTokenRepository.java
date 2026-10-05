package com.motivhub.be.auth.repository;

import com.motivhub.be.auth.domain.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(String email);
}
