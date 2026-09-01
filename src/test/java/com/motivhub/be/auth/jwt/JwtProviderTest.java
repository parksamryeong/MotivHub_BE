package com.motivhub.be.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "dev-only-secret-key-change-in-production-please-32bytes-min",
                3600000L,
                1209600000L
        );
    }

    @Test
    void generatesAccessTokenAndExtractsUserId() {
        String token = jwtProvider.generateAccessToken(42L);

        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
        assertThat(jwtProvider.isValid(token)).isTrue();
    }

    @Test
    void generatesRefreshTokenAndExtractsUserId() {
        String token = jwtProvider.generateRefreshToken(7L, "device-A");

        assertThat(jwtProvider.getUserId(token)).isEqualTo(7L);
    }

    @Test
    void accessTokenHasAccessType() {
        String token = jwtProvider.generateAccessToken(42L);

        assertThat(jwtProvider.getTokenType(token)).isEqualTo("access");
    }

    @Test
    void refreshTokenHasRefreshType() {
        String token = jwtProvider.generateRefreshToken(7L, "device-A");

        assertThat(jwtProvider.getTokenType(token)).isEqualTo("refresh");
    }

    @Test
    void refreshTokenCarriesDeviceIdClaim() {
        String token = jwtProvider.generateRefreshToken(7L, "device-A");

        assertThat(jwtProvider.getDeviceId(token)).isEqualTo("device-A");
    }

    @Test
    void accessTokenHasNoDeviceIdClaim() {
        String token = jwtProvider.generateAccessToken(7L);

        assertThat(jwtProvider.getDeviceId(token)).isNull();
    }

    @Test
    void throwsExceptionWhenExtractingTokenTypeFromTamperedToken() {
        String token = jwtProvider.generateAccessToken(1L) + "tampered";

        assertThatThrownBy(() -> jwtProvider.getTokenType(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtProvider.generateAccessToken(1L) + "tampered";

        assertThat(jwtProvider.isValid(token)).isFalse();
    }

    @Test
    void throwsExceptionWhenExtractingUserIdFromTamperedToken() {
        String token = jwtProvider.generateAccessToken(1L) + "tampered";

        assertThatThrownBy(() -> jwtProvider.getUserId(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtProvider shortLived = new JwtProvider(
                "dev-only-secret-key-change-in-production-please-32bytes-min",
                -1000L,
                1209600000L
        );
        String token = shortLived.generateAccessToken(1L);

        assertThat(shortLived.isValid(token)).isFalse();
    }
}
