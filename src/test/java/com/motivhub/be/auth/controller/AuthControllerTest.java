package com.motivhub.be.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.domain.EmailVerificationToken;
import com.motivhub.be.auth.dto.ExchangeRequest;
import com.motivhub.be.auth.dto.LoginRequest;
import com.motivhub.be.auth.dto.RefreshRequest;
import com.motivhub.be.auth.dto.SignupCompleteRequest;
import com.motivhub.be.auth.dto.SignupRequestVerificationRequest;
import com.motivhub.be.auth.dto.TokenPair;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.auth.repository.EmailVerificationTokenRepository;
import com.motivhub.be.auth.service.EmailVerificationMailService;
import com.motivhub.be.auth.service.RefreshTokenService;
import com.motivhub.be.auth.service.TempAuthCodeService;
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
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TempAuthCodeService tempAuthCodeService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private EmailVerificationMailService emailVerificationMailService;

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
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "refresh-test-1", "refresh@test.com", "user_refresh1", null));
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), "device-1");
        refreshTokenService.save(user.getId(), "device-1", refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk());
    }

    @Test
    void returns401ForRefreshTokenNotInRedis() throws Exception {
        String refreshToken = jwtProvider.generateRefreshToken(56L, "device-1");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secondDeviceLoginDoesNotInvalidateFirstDevicesRefreshToken() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "multi-device-1", "multi@test.com", "user_multi1", null));
        String refreshTokenDeviceA = jwtProvider.generateRefreshToken(user.getId(), "device-A");
        String refreshTokenDeviceB = jwtProvider.generateRefreshToken(user.getId(), "device-B");
        refreshTokenService.save(user.getId(), "device-A", refreshTokenDeviceA);
        refreshTokenService.save(user.getId(), "device-B", refreshTokenDeviceB);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshTokenDeviceA))))
                .andExpect(status().isOk());
    }

    @Test
    void deletesRefreshTokenWhenAuthenticatedUserLogsOut() throws Exception {
        Long userId = 57L;
        String deviceId = "device-1";
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId, deviceId);
        refreshTokenService.save(userId, deviceId, refreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenService.find(userId, deviceId)).isEmpty();
    }

    @Test
    void logoutOnOneDeviceDoesNotAffectAnotherDevice() throws Exception {
        Long userId = 58L;
        String accessTokenDeviceA = jwtProvider.generateAccessToken(userId);
        String refreshTokenDeviceA = jwtProvider.generateRefreshToken(userId, "device-A");
        String refreshTokenDeviceB = jwtProvider.generateRefreshToken(userId, "device-B");
        refreshTokenService.save(userId, "device-A", refreshTokenDeviceA);
        refreshTokenService.save(userId, "device-B", refreshTokenDeviceB);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessTokenDeviceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshTokenDeviceA))))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenService.find(userId, "device-A")).isEmpty();
        assertThat(refreshTokenService.find(userId, "device-B")).contains(refreshTokenDeviceB);
    }

    @Test
    void returns403WhenLogoutRefreshTokenBelongsToDifferentUser() throws Exception {
        Long authenticatedUserId = 59L;
        Long otherUserId = 60L;
        String accessToken = jwtProvider.generateAccessToken(authenticatedUserId);
        String otherUsersRefreshToken = jwtProvider.generateRefreshToken(otherUserId, "device-A");
        refreshTokenService.save(otherUserId, "device-A", otherUsersRefreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(otherUsersRefreshToken))))
                .andExpect(status().isForbidden());

        assertThat(refreshTokenService.find(otherUserId, "device-A")).contains(otherUsersRefreshToken);
    }

    @Test
    void logoutWithInvalidRefreshTokenStillReturns204() throws Exception {
        Long userId = 61L;
        String accessToken = jwtProvider.generateAccessToken(userId);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("garbage-token"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void requestVerificationThenCompleteSignupCreatesEmailUser() throws Exception {
        String email = "signup-flow@example.com";

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isOk());

        EmailVerificationToken token = emailVerificationTokenRepository.findAll().stream()
                .filter(t -> t.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(token.getToken(), "password123", "signupflowuser"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, email)).isPresent();
    }

    @Test
    void requestVerificationRejectsAlreadyRegisteredEmail() throws Exception {
        String email = "already-registered@example.com";
        userRepository.save(User.createEmailAccount(email, "alreadyregistered", passwordEncoder.encode("password123")));

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isConflict());
    }

    @Test
    void completeSignupRejectsUnknownToken() throws Exception {
        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest("no-such-token", "password123", "unknowntokenuser"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void completeSignupRejectsExpiredToken() throws Exception {
        EmailVerificationToken token = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("expired-token", "expired@example.com", LocalDateTime.now().minusDays(1)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(token.getToken(), "password123", "expiredtokenuser"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsAlreadyUsedToken() throws Exception {
        EmailVerificationToken token = EmailVerificationToken.create(
                "used-token", "used@example.com", LocalDateTime.now().plusDays(7));
        token.consume();
        emailVerificationTokenRepository.save(token);

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(token.getToken(), "password123", "usedtokenuser"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsWeakPassword() throws Exception {
        EmailVerificationToken token = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("weak-password-token", "weakpw@example.com",
                        LocalDateTime.now().plusDays(7)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(token.getToken(), "short", "weakpassworduser"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsNullPassword() throws Exception {
        EmailVerificationToken token = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("null-password-token", "nullpw@example.com",
                        LocalDateTime.now().plusDays(7)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getToken() + "\",\"password\":null,\"nickname\":\"nullpassworduser\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsDuplicateNickname() throws Exception {
        userRepository.save(User.create(SocialProvider.GITHUB, "dup-nick-1", "dup1@test.com", "duplicatenick", null));
        EmailVerificationToken token = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("dup-nickname-token", "dupnick@example.com",
                        LocalDateTime.now().plusDays(7)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(token.getToken(), "password123", "duplicatenick"))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginSucceedsWithCorrectEmailAndPassword() throws Exception {
        String email = "login-success@example.com";
        userRepository.save(User.createEmailAccount(email, "loginsuccessuser", passwordEncoder.encode("password123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk());
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        String email = "login-wrong-password@example.com";
        userRepository.save(User.createEmailAccount(email, "loginwrongpwuser", passwordEncoder.encode("password123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void loginFailsWithUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("no-such-account@example.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void loginFailsForWithdrawnEmailAccount() throws Exception {
        String email = "login-withdrawn@example.com";
        User user = userRepository.save(
                User.createEmailAccount(email, "loginwithdrawnuser", passwordEncoder.encode("password123")));
        user.withdraw();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void loginRejectsOverlongPassword() throws Exception {
        String email = "login-overlong-password@example.com";
        userRepository.save(User.createEmailAccount(email, "loginoverlonguser", passwordEncoder.encode("password123")));
        String overlongPassword = "a".repeat(100);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, overlongPassword))))
                .andExpect(status().isBadRequest());
    }
}
