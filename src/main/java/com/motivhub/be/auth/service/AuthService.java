package com.motivhub.be.auth.service;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.exception.InvalidCodeException;
import com.motivhub.be.auth.exception.InvalidRefreshTokenException;
import com.motivhub.be.auth.exception.InvalidTokenException;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.domain.UserStatus;
import com.motivhub.be.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TempAuthCodeService tempAuthCodeService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public AuthService(TempAuthCodeService tempAuthCodeService, RefreshTokenService refreshTokenService,
                        JwtProvider jwtProvider, UserRepository userRepository) {
        this.tempAuthCodeService = tempAuthCodeService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    public TokenPair exchange(String code) {
        return tempAuthCodeService.consume(code)
                .orElseThrow(() -> new InvalidCodeException("유효하지 않거나 만료된 code입니다."));
    }

    public TokenPair refresh(String refreshToken) {
        Long userId;
        try {
            userId = jwtProvider.getUserId(refreshToken);
            if (!"refresh".equals(jwtProvider.getTokenType(refreshToken))) {
                log.warn("refresh 실패: refresh token이 아닌 토큰 타입입니다. userId={}", userId);
                throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
            }
        } catch (InvalidTokenException e) {
            log.warn("refresh 실패: 유효하지 않은 refresh token입니다.");
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("refresh 실패: 존재하지 않거나 탈퇴한 사용자입니다. userId={}", userId);
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        String stored = refreshTokenService.find(userId)
                .orElseThrow(() -> {
                    log.warn("refresh 실패: 저장된 refresh token이 없습니다. userId={}", userId);
                    return new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
                });
        if (!stored.equals(refreshToken)) {
            log.warn("refresh 실패: refresh token이 일치하지 않습니다. userId={}", userId);
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
    }
}
