package com.motivhub.be.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.domain.PasswordResetToken;
import com.motivhub.be.auth.dto.LoginRequest;
import com.motivhub.be.auth.dto.PasswordResetCompleteRequest;
import com.motivhub.be.auth.dto.PasswordResetRequestRequest;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.repository.PasswordResetTokenRepository;
import com.motivhub.be.auth.service.PasswordResetMailService;
import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PasswordResetControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JwtProvider jwtProvider;
    @MockitoBean private PasswordResetMailService passwordResetMailService;

    @Test
    void fullResetFlowChangesPasswordAndInvalidatesExistingSessions() throws Exception {
        String email = "reset-flow@example.com";
        User user = userRepository.save(
                User.createEmailAccount(email, "resetflowuser", passwordEncoder.encode("oldpassword123")));
        String deviceId = "device-1";
        refreshTokenService.save(user.getId(), deviceId, jwtProvider.generateRefreshToken(user.getId(), deviceId));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        PasswordResetToken token = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, token.getCode(), "newpassword456"))))
                .andExpect(status().isOk());

        assertThat(refreshTokenService.find(user.getId(), deviceId)).isEmpty();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "newpassword456"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "oldpassword123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestResetForSocialAccountSendsNoticeInsteadOfCode() throws Exception {
        String email = "social-reset@example.com";
        userRepository.save(User.create(SocialProvider.GOOGLE, "google-reset-1", email, "socialresetuser", null));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)).isEmpty();
        verify(passwordResetMailService).sendSocialAccountNotice(eq(email), eq("GOOGLE"));
        verify(passwordResetMailService, never()).sendResetCode(anyString(), anyString());
    }

    @Test
    void requestResetForNonexistentEmailSendsNoMail() throws Exception {
        String email = "no-such-account-reset@example.com";

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        assertThat(passwordResetTokenRepository.findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)).isEmpty();
        verify(passwordResetMailService, never()).sendResetCode(anyString(), anyString());
        verify(passwordResetMailService, never()).sendSocialAccountNotice(anyString(), anyString());
    }

    @Test
    void completeResetRejectsExpiredCode() throws Exception {
        String email = "expired-reset@example.com";
        userRepository.save(User.createEmailAccount(email, "expiredresetuser", passwordEncoder.encode("password123")));
        passwordResetTokenRepository.save(
                PasswordResetToken.create("111111", email, LocalDateTime.now().minusMinutes(1)));

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, "111111", "newpassword456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_EXPIRED"));
    }

    @Test
    void completeResetRejectsWrongCode() throws Exception {
        String email = "wrong-code-reset@example.com";
        userRepository.save(User.createEmailAccount(email, "wrongcoderesetuser", passwordEncoder.encode("password123")));
        passwordResetTokenRepository.save(
                PasswordResetToken.create("222222", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, "999999", "newpassword456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_CODE_MISMATCH"));
    }

    @Test
    void completeResetRejectsWhenNoCodeWasRequested() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest("never-requested-reset@example.com", "123456", "newpassword456"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    @Test
    void completeResetRejectsAfterFiveFailedAttempts() throws Exception {
        String email = "too-many-reset-attempts@example.com";
        userRepository.save(User.createEmailAccount(email, "toomanyresetuser", passwordEncoder.encode("password123")));
        passwordResetTokenRepository.save(
                PasswordResetToken.create("333333", email, LocalDateTime.now().plusMinutes(5)));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PasswordResetCompleteRequest(email, "000000", "newpassword456"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, "333333", "newpassword456"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PASSWORD_RESET_ATTEMPTS"));
    }

    @Test
    void attemptCounterIsNotResetByResendingTheCode() throws Exception {
        String email = "resend-no-reset-counter@example.com";
        userRepository.save(User.createEmailAccount(email, "resendnoresetcounteruser", passwordEncoder.encode("password123")));

        // First request for reset code
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        // 5 failed attempts with wrong code
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/password-reset/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new PasswordResetCompleteRequest(email, "000000", "newpassword456"))))
                    .andExpect(status().isBadRequest());
        }

        // Re-request the reset code (invalidates old token but NOT the attempt counter)
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        // Fetch the NEW token's correct code
        PasswordResetToken newToken = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        // Attempt to complete with the NEW correct code - should still be rejected with 429
        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, newToken.getCode(), "newpassword456"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_PASSWORD_RESET_ATTEMPTS"));
    }

    @Test
    void requestingResetAgainInvalidatesThePreviousCode() throws Exception {
        String email = "resend-reset-invalidates@example.com";
        userRepository.save(User.createEmailAccount(email, "resendresetuser", passwordEncoder.encode("password123")));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());
        PasswordResetToken firstCode = passwordResetTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequestRequest(email))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, firstCode.getCode(), "newpassword456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_CODE_MISMATCH"));
    }

    @Test
    void completeResetRejectsWeakNewPassword() throws Exception {
        String email = "weak-new-password@example.com";
        userRepository.save(User.createEmailAccount(email, "weaknewpassworduser", passwordEncoder.encode("password123")));
        passwordResetTokenRepository.save(
                PasswordResetToken.create("444444", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetCompleteRequest(email, "444444", "short"))))
                .andExpect(status().isBadRequest());
    }
}
