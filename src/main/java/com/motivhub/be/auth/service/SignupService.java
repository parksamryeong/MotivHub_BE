package com.motivhub.be.auth.service;

import com.motivhub.be.auth.domain.EmailVerificationToken;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.exception.EmailAlreadyRegisteredException;
import com.motivhub.be.auth.exception.InvalidVerificationTokenException;
import com.motivhub.be.auth.exception.VerificationTokenAlreadyUsedException;
import com.motivhub.be.auth.exception.VerificationTokenExpiredException;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.repository.EmailVerificationTokenRepository;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.InvalidNicknameException;
import com.motivhub.be.user.exception.NicknameDuplicateException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.user.service.NicknameValidator;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SignupService {

    private static final long EXPIRE_DAYS = 7;

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationMailService emailVerificationMailService;
    private final NicknameValidator nicknameValidator;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public SignupService(EmailVerificationTokenRepository emailVerificationTokenRepository,
                          UserRepository userRepository,
                          EmailVerificationMailService emailVerificationMailService,
                          NicknameValidator nicknameValidator,
                          PasswordEncoder passwordEncoder,
                          JwtProvider jwtProvider,
                          RefreshTokenService refreshTokenService) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.userRepository = userRepository;
        this.emailVerificationMailService = emailVerificationMailService;
        this.nicknameValidator = nicknameValidator;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void requestVerification(String email) {
        if (userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, email).isPresent()) {
            throw new EmailAlreadyRegisteredException("이미 가입된 이메일입니다.");
        }
        String token = UUID.randomUUID().toString();
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create(token, email, LocalDateTime.now().plusDays(EXPIRE_DAYS)));
        emailVerificationMailService.sendVerification(email, token);
    }

    @Transactional
    public TokenPair completeSignup(String token, String password, String nickname) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidVerificationTokenException("유효하지 않은 인증 링크입니다."));
        if (verificationToken.getConsumedAt() != null) {
            throw new VerificationTokenAlreadyUsedException("이미 가입 처리된 링크입니다.");
        }
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationTokenExpiredException("인증 링크가 만료되었습니다.");
        }
        if (!nicknameValidator.isValidFormat(nickname)) {
            throw new InvalidNicknameException("닉네임 형식이 올바르지 않습니다.");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new NicknameDuplicateException("이미 사용중인 닉네임입니다.");
        }

        User user = userRepository.save(User.createEmailAccount(
                verificationToken.getEmail(), nickname, passwordEncoder.encode(password)));
        verificationToken.consume();

        String deviceId = UUID.randomUUID().toString();
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), deviceId);
        refreshTokenService.save(user.getId(), deviceId, refreshToken);
        return new TokenPair(accessToken, refreshToken);
    }
}
