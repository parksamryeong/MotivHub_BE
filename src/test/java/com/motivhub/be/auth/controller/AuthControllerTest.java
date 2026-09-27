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

        EmailVerificationToken token = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, token.getCode(), "password123", "signupflowuser"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByProviderAndProviderId(SocialProvider.EMAIL, email)).isPresent();
    }

    @Test
    void requestVerificationReturns503WhenMailSendingFails() throws Exception {
        // 메일 발송 실패가 처리기 없이 흘러가면 컨테이너의 기본 /error 디스패치를 타게 되는데,
        // 그 경로가 인증이 필요한 경로로 취급돼서 500 대신 401로 잘못 보이는 버그가 있었다 -
        // GlobalExceptionHandler가 MailException을 직접 잡아 503으로 응답하는지 검증한다.
        String email = "mail-failure-test@example.com";
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailAuthenticationException("failed to connect, no password specified?"))
                .when(emailVerificationMailService).sendVerification(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAIL_SEND_FAILED"));
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
    void requestingVerificationAgainInvalidatesThePreviousCode() throws Exception {
        String email = "resend-invalidates@example.com";

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isOk());
        EmailVerificationToken firstCode = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, firstCode.getCode(), "password123", "resendinvalidateduser"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    void completeSignupRejectsWhenNoCodeWasRequested() throws Exception {
        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest("never-requested@example.com", "123456",
                                        "password123", "neverrequesteduser"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void completeSignupRejectsExpiredCode() throws Exception {
        String email = "expired-code@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("111111", email, LocalDateTime.now().minusMinutes(1)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, "111111", "password123", "expiredcodeuser"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOKEN_EXPIRED"));
    }

    @Test
    void completeSignupRejectsWrongCode() throws Exception {
        String email = "wrong-code@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("222222", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, "999999", "password123", "wrongcodeuser"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    void completeSignupRejectsAfterFiveFailedAttempts() throws Exception {
        String email = "too-many-attempts@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("333333", email, LocalDateTime.now().plusMinutes(5)));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupCompleteRequest(email, "000000", "password123", "toomanyattemptsuser"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, "333333", "password123", "toomanyattemptsuser"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VERIFICATION_ATTEMPTS"));
    }

    @Test
    void resendDoesNotResetTheAttemptCounter() throws Exception {
        String email = "resend-does-not-reset@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("111111", email, LocalDateTime.now().plusMinutes(5)));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupCompleteRequest(email, "000000", "password123", "resenduser"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(email))))
                .andExpect(status().isOk());
        EmailVerificationToken newCode = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, newCode.getCode(), "password123", "resenduser"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VERIFICATION_ATTEMPTS"));
    }

    @Test
    void attemptCounterIsSharedAcrossEmailCaseVariants() throws Exception {
        String email = "case-variant-test@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("222222", email, LocalDateTime.now().plusMinutes(5)));

        for (int i = 0; i < 5; i++) {
            String variantEmail = (i % 2 == 0) ? email.toUpperCase(java.util.Locale.ROOT) : email;
            mockMvc.perform(post("/api/auth/signup/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupCompleteRequest(variantEmail, "000000", "password123", "casevariantuser"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupCompleteRequest(
                                email.toUpperCase(java.util.Locale.ROOT), "222222", "password123", "casevariantuser"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VERIFICATION_ATTEMPTS"));
    }

    @Test
    void attemptCounterIsSharedAcrossAccentedEmailVariants() throws Exception {
        // MySQL의 기본 콜레이션(utf8mb4_0900_ai_ci)은 대소문자뿐 아니라 악센트/전각문자도 같은
        // 값으로 취급한다(á == a) - Redis 키를 클라이언트가 보낸 문자열 그대로 쓰면 이 변형마다
        // 독립된 시도 횟수를 받아서 우회할 수 있으므로, DB가 판단해준 정규 표기를 키로 써야 한다.
        String email = "accent-variant-test@example.com";
        String accentedVariant = "áccent-variant-test@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("333333", email, LocalDateTime.now().plusMinutes(5)));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupCompleteRequest(accentedVariant, "000000", "password123", "accentvariantuser"))))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(accentedVariant, "333333", "password123", "accentvariantuser"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VERIFICATION_ATTEMPTS"));
    }

    @Test
    void resendWithDifferentCaseVariantDoesNotResetTheAttemptCounter() throws Exception {
        // 재발급 시 새 토큰 row에 "이번 요청의 원본 문자열"이 아니라 기존 토큰의 정규 표기를
        // 그대로 이어서 저장해야 한다 - 그렇지 않으면 재발급할 때마다 정규 표기 자체가 바뀌어서
        // completeSignup의 시도 횟수 카운터가 매번 새 Redis 키를 가리키게 되고, 변형 이메일로
        // 재발급을 반복하는 것만으로 5회 제한이 무한히 리셋된다.
        String email = "resend-variant-no-reset@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("111111", email, LocalDateTime.now().plusMinutes(5)));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signup/complete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SignupCompleteRequest(email, "000000", "password123", "resendvariantuser"))))
                    .andExpect(status().isBadRequest());
        }

        String upperCaseVariant = email.toUpperCase(java.util.Locale.ROOT);
        mockMvc.perform(post("/api/auth/signup/request-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequestVerificationRequest(upperCaseVariant))))
                .andExpect(status().isOk());
        EmailVerificationToken newCode = emailVerificationTokenRepository
                .findFirstByEmailAndConsumedAtIsNullOrderByIdDesc(email)
                .orElseThrow();

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(upperCaseVariant, newCode.getCode(), "password123", "resendvariantuser"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VERIFICATION_ATTEMPTS"));
    }

    @Test
    void completeSignupRejectsWeakPassword() throws Exception {
        String email = "weakpw@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("444444", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, "444444", "short", "weakpassworduser"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsNullPassword() throws Exception {
        String email = "nullpw@example.com";
        EmailVerificationToken token = emailVerificationTokenRepository.save(
                EmailVerificationToken.create("555555", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + token.getCode()
                                + "\",\"password\":null,\"nickname\":\"nullpassworduser\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeSignupRejectsDuplicateNickname() throws Exception {
        userRepository.save(User.create(SocialProvider.GITHUB, "dup-nick-1", "dup1@test.com", "duplicatenick", null));
        String email = "dupnick@example.com";
        emailVerificationTokenRepository.save(
                EmailVerificationToken.create("666666", email, LocalDateTime.now().plusMinutes(5)));

        mockMvc.perform(post("/api/auth/signup/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupCompleteRequest(email, "666666", "password123", "duplicatenick"))))
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
