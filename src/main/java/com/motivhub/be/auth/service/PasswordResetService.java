package com.motivhub.be.auth.service;

import com.motivhub.be.auth.domain.PasswordResetToken;
import com.motivhub.be.auth.exception.InvalidPasswordResetTokenException;
import com.motivhub.be.auth.exception.PasswordResetCodeMismatchException;
import com.motivhub.be.auth.exception.PasswordResetTokenExpiredException;
import com.motivhub.be.auth.exception.TooManyPasswordResetAttemptsException;
import com.motivhub.be.auth.repository.PasswordResetTokenRepository;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    private static final long EXPIRE_MINUTES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordResetMailService passwordResetMailService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetAttemptLimiter passwordResetAttemptLimiter;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                 UserRepository userRepository,
                                 PasswordResetMailService passwordResetMailService,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenService refreshTokenService,
                                 PasswordResetAttemptLimiter passwordResetAttemptLimiter) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordResetMailService = passwordResetMailService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetAttemptLimiter = passwordResetAttemptLimiter;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email = normalize(rawEmail);

        Optional<User> emailAccount = userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, email);
        if (emailAccount.isEmpty()) {
            Optional<User> anyAccount = userRepository.findFirstByEmailOrderByIdAsc(email);
            if (anyAccount.isEmpty()) {
                return;
            }
            User socialUser = anyAccount.get();
            if (!passwordResetAttemptLimiter.tryStartCooldown(email)) {
                return;
            }
            passwordResetMailService.sendSocialAccountNotice(email, socialUser.getProvider().name());
            return;
        }

        Optional<PasswordResetToken> existingToken = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email);
        String canonicalEmail = existingToken.map(PasswordResetToken::getEmail).orElse(email);
        if (!passwordResetAttemptLimiter.tryStartCooldown(canonicalEmail)) {
            return;
        }
        existingToken.ifPresent(PasswordResetToken::consume);

        String code = generateCode();
        passwordResetTokenRepository.save(
                PasswordResetToken.create(code, canonicalEmail, LocalDateTime.now().plusMinutes(EXPIRE_MINUTES)));
        passwordResetMailService.sendResetCode(canonicalEmail, code);
    }

    @Transactional
    public void completeReset(String rawEmail, String code, String newPassword) {
        String email = normalize(rawEmail);
        PasswordResetToken token = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("재설정 코드를 먼저 요청해주세요."));
        String canonicalEmail = token.getEmail();

        long attemptNumber = passwordResetAttemptLimiter.recordAttempt(canonicalEmail);
        if (passwordResetAttemptLimiter.exceedsLimit(attemptNumber)) {
            throw new TooManyPasswordResetAttemptsException("재설정 시도 횟수를 초과했습니다. 코드를 다시 요청해주세요.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new PasswordResetTokenExpiredException("재설정 코드가 만료되었습니다. 다시 요청해주세요.");
        }
        if (!token.matchesCode(code)) {
            throw new PasswordResetCodeMismatchException("재설정 코드가 일치하지 않습니다.");
        }

        User user = userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, canonicalEmail)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("재설정 코드를 먼저 요청해주세요."));

        user.changePassword(passwordEncoder.encode(newPassword));
        token.consume();
        passwordResetAttemptLimiter.reset(canonicalEmail);
        refreshTokenService.deleteAll(user.getId());
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
