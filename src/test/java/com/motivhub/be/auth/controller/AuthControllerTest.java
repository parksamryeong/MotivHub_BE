package com.motivhub.be.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.dto.ExchangeRequest;
import com.motivhub.be.auth.dto.RefreshRequest;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.auth.service.TempAuthCodeService;
import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TempAuthCodeService tempAuthCodeService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JwtProvider jwtProvider;

    @Test
    void exchangesTokenWithValidCode() throws Exception {
        String code = tempAuthCodeService.issue(new TokenPair("acc", "ref"));

        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExchangeRequest(code))))
                .andExpect(status().isOk());
    }

    @Test
    void returns400ForUnknownCode() throws Exception {
        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExchangeRequest("no-such-code"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reissuesTokenWithValidRefreshToken() throws Exception {
        String refreshToken = jwtProvider.generateRefreshToken(55L);
        refreshTokenService.save(55L, refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    void returns401ForRefreshTokenNotInRedis() throws Exception {
        String refreshToken = jwtProvider.generateRefreshToken(56L);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletesRefreshTokenWhenAuthenticatedUserLogsOut() throws Exception {
        Long userId = 57L;
        String accessToken = jwtProvider.generateAccessToken(userId);
        refreshTokenService.save(userId, "some-refresh");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenService.find(userId)).isEmpty();
    }
}
