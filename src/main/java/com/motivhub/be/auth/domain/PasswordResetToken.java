package com.motivhub.be.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "password_reset_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    private PasswordResetToken(String code, String email, LocalDateTime expiresAt) {
        this.code = code;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public static PasswordResetToken create(String code, String email, LocalDateTime expiresAt) {
        return new PasswordResetToken(code, email, expiresAt);
    }

    public void consume() {
        this.consumedAt = LocalDateTime.now();
    }

    public boolean matchesCode(String candidate) {
        return this.code.equals(candidate);
    }
}
