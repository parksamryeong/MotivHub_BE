package com.motivhub.be.auth.service;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.exception.InvalidCodeException;
import com.motivhub.be.auth.exception.InvalidRefreshTokenException;
import com.motivhub.be.auth.exception.InvalidTokenException;
import com.motivhub.be.auth.exception.LogoutForbiddenException;
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
        String deviceId;
        try {
            userId = jwtProvider.getUserId(refreshToken);
            if (!"refresh".equals(jwtProvider.getTokenType(refreshToken))) {
                log.warn("refresh 실패: refresh token이 아닌 토큰 타입입니다. userId={}", userId);
                throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
            }
            deviceId = jwtProvider.getDeviceId(refreshToken);
        } catch (InvalidTokenException e) {
            log.warn("refresh 실패: 유효하지 않은 refresh token입니다.");
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }
        if (deviceId == null) {
            log.warn("refresh 실패: deviceId 클레임이 없는 토큰입니다. userId={}", userId);
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() == UserStatus.WITHDRAWN) {
            log.warn("refresh 실패: 존재하지 않거나 탈퇴한 사용자입니다. userId={}", userId);
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        String stored = refreshTokenService.find(userId, deviceId)
                .orElseThrow(() -> {
                    log.warn("refresh 실패: 저장된 refresh token이 없습니다. userId={}, deviceId={}", userId, deviceId);
                    return new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
                });
        if (!stored.equals(refreshToken)) {
            log.warn("refresh 실패: refresh token이 일치하지 않습니다. userId={}, deviceId={}", userId, deviceId);
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId, deviceId);
        refreshTokenService.save(userId, deviceId, newRefreshToken);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    public void logout(Long authenticatedUserId, String refreshToken) {
        Long tokenUserId;
        String deviceId;
        try {
            tokenUserId = jwtProvider.getUserId(refreshToken);
            deviceId = jwtProvider.getDeviceId(refreshToken);
        } catch (InvalidTokenException e) {
            log.info("logout: 이미 만료되었거나 유효하지 않은 refresh token입니다. 로그아웃 처리로 간주합니다.");
            return;
        }
        if (!tokenUserId.equals(authenticatedUserId)) {
            log.warn("logout 실패: 인증된 사용자와 refresh token의 사용자가 일치하지 않습니다. "
                    + "authenticatedUserId={}, tokenUserId={}", authenticatedUserId, tokenUserId);
            throw new LogoutForbiddenException("다른 사용자의 세션을 로그아웃할 수 없습니다.");
        }
        if (deviceId == null) {
            log.info("logout: deviceId 클레임이 없는 refresh token입니다. 로그아웃 처리로 간주합니다.");
            return;
        }
        refreshTokenService.delete(tokenUserId, deviceId);
    }
}
