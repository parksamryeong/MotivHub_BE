package com.motivhub.be.auth.service;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.exception.InvalidCodeException;
import com.motivhub.be.auth.exception.InvalidRefreshTokenException;
import com.motivhub.be.auth.exception.InvalidTokenException;
import com.motivhub.be.auth.jwt.JwtProvider;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final TempAuthCodeService tempAuthCodeService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;

    public AuthService(TempAuthCodeService tempAuthCodeService, RefreshTokenService refreshTokenService,
                        JwtProvider jwtProvider) {
        this.tempAuthCodeService = tempAuthCodeService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProvider = jwtProvider;
    }

    public TokenPair exchange(String code) {
        return tempAuthCodeService.consume(code)
                .orElseThrow(() -> new InvalidCodeException("유효하지 않거나 만료된 code입니다."));
    }

    public TokenPair refresh(String refreshToken) {
        Long userId;
        try {
            userId = jwtProvider.getUserId(refreshToken);
        } catch (InvalidTokenException e) {
            throw new InvalidRefreshTokenException("유효하지 않은 refresh token입니다.");
        }

        String stored = refreshTokenService.find(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("유효하지 않은 refresh token입니다."));
        if (!stored.equals(refreshToken)) {
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
