package com.motivhub.be.auth.handler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.oauth.CustomOAuth2User;
import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.auth.service.TempAuthCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TempAuthCodeService tempAuthCodeService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    @Test
    void redirectsToFrontendWithTempCodeOnLoginSuccess() throws Exception {
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                jwtProvider, refreshTokenService, tempAuthCodeService, "http://localhost:3000");

        CustomOAuth2User principal = new CustomOAuth2User(10L, Map.of());
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtProvider.generateAccessToken(10L)).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(10L)).thenReturn("refresh-token");
        when(tempAuthCodeService.issue(new TokenPair("access-token", "refresh-token")))
                .thenReturn("temp-code-123");

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(refreshTokenService).save(10L, "refresh-token");
        verify(response).sendRedirect("http://localhost:3000/oauth/callback?code=temp-code-123");
    }
}
