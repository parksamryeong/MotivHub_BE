package com.motivhub.be.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigCorsTest {

    @Test
    void allowsMultipleCommaSeparatedFrontendOrigins() {
        SecurityConfig securityConfig = new SecurityConfig("https://motivhub.cloud,https://motivhub-fe.vercel.app");
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks/mine");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://motivhub.cloud", "https://motivhub-fe.vercel.app");
    }

    @Test
    void trimsWhitespaceAroundOriginsAndIgnoresEmptySegments() {
        SecurityConfig securityConfig =
                new SecurityConfig(" https://motivhub.cloud , , https://motivhub-fe.vercel.app ");
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks/mine");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://motivhub.cloud", "https://motivhub-fe.vercel.app");
    }

    @Test
    void singleOriginStillWorksForLocalDevelopment() {
        SecurityConfig securityConfig = new SecurityConfig("http://localhost:3000");
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tasks/mine");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:3000");
    }
}
