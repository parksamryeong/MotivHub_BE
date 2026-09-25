package com.motivhub.be.auth.config;

import com.motivhub.be.auth.handler.OAuth2FailureHandler;
import com.motivhub.be.auth.handler.OAuth2SuccessHandler;
import com.motivhub.be.auth.jwt.JwtAuthenticationFilter;
import com.motivhub.be.auth.oauth.CustomOAuth2UserService;
import com.motivhub.be.global.exception.JwtAuthenticationEntryPoint;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/oauth2/**", "/login/**", "/api/auth/exchange", "/api/auth/refresh", "/actuator/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/ws/**"
    };

    private final List<String> allowedOrigins;

    // 콤마로 여러 origin을 받는다 - 배포 환경 전환기(예: 커스텀 도메인 연결 전 임시 Vercel 프리뷰
    // 주소와 최종 도메인을 동시에 허용해야 하는 경우)에 여러 프론트 출처를 한꺼번에 허용할 수 있어야
    // 한다. 앞뒤 공백은 무시하고, 빈 값은 걸러낸다.
    public SecurityConfig(@Value("${app.frontend-url}") String frontendUrl) {
        this.allowedOrigins = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            OAuth2FailureHandler oAuth2FailureHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        // SockJS는 연결 전 GET /ws/info 핸드셰이크에서 라이브러리 차원에서 강제로
        // withCredentials=true를 실어 보낸다(프론트에서 끌 수 있는 옵션이 없음). 이 요청이
        // 전역 CORS 설정(allowCredentials=false)에 걸려 매번 CORS 에러가 나서, /ws 경로만
        // 별도로 credentials를 허용한다. 이 앱은 쿠키 인증을 쓰지 않으므로(JWT는 STOMP CONNECT
        // 헤더로 전달) 보안 영향은 없다.
        CorsConfiguration wsConfiguration = new CorsConfiguration();
        wsConfiguration.setAllowedOrigins(allowedOrigins);
        wsConfiguration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        wsConfiguration.setAllowedHeaders(List.of("*"));
        wsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", wsConfiguration);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
