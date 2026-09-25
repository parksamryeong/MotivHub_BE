package com.motivhub.be.auth.handler;

import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class OAuth2FailureHandlerTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @Test
    void redirectsToFrontendWithErrorQueryOnLoginFailure() throws Exception {
        OAuth2FailureHandler handler = new OAuth2FailureHandler("http://localhost:3000");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("fail"));

        verify(response).sendRedirect("http://localhost:3000/oauth/callback?error=oauth_failed");
    }

    @Test
    void redirectsUsingOnlyFirstOriginWhenFrontendUrlHasMultipleCommaSeparatedOrigins() throws Exception {
        OAuth2FailureHandler handler =
                new OAuth2FailureHandler("https://motivhub.cloud,https://motiv-hub-fe.vercel.app");

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("fail"));

        verify(response).sendRedirect("https://motivhub.cloud/oauth/callback?error=oauth_failed");
    }
}
