package com.motivhub.be.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    // jackson-datatype-jsr310 is now on the classpath (build.gradle), so LocalDateTime
    // serialization works via ServiceLoader-discovered modules, same as Spring Boot's
    // autoconfigured ObjectMapper bean would pick it up in the real app.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);

    @Test
    void writesTokenExpiredResponseWhenAuthErrorCodeIsTokenExpired() throws Exception {
        when(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTR))
                .thenReturn(JwtAuthenticationFilter.TOKEN_EXPIRED);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        entryPoint.commence(request, response, null);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setCharacterEncoding("UTF-8");
        JsonNode json = objectMapper.readTree(body.toString());
        assertThat(json.get("code").asText()).isEqualTo(JwtAuthenticationFilter.TOKEN_EXPIRED);
        assertThat(json.get("message").asText()).contains("만료");
    }

    @Test
    void writesUnauthorizedResponseWhenAuthErrorCodeIsAbsent() throws Exception {
        when(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTR)).thenReturn(null);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        entryPoint.commence(request, response, null);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setCharacterEncoding("UTF-8");
        JsonNode json = objectMapper.readTree(body.toString());
        assertThat(json.get("code").asText()).isEqualTo("UNAUTHORIZED");
    }
}
