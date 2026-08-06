package com.motivhub.be.auth.handler;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.oauth.CustomOAuth2User;
import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.auth.service.TempAuthCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final TempAuthCodeService tempAuthCodeService;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            TempAuthCodeService tempAuthCodeService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.tempAuthCodeService = tempAuthCodeService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = principal.getUserId();

        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenService.save(userId, refreshToken);

        String code = tempAuthCodeService.issue(new TokenPair(accessToken, refreshToken));

        response.sendRedirect(frontendUrl + "/oauth/callback?code=" + code);
    }
}
