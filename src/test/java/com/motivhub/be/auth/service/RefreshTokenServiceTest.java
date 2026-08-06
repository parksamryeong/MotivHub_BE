package com.motivhub.be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshTokenServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void findsSavedRefreshTokenByUserId() {
        refreshTokenService.save(1L, "refresh-token-value");

        assertThat(refreshTokenService.find(1L)).contains("refresh-token-value");
    }

    @Test
    void deletedTokenIsNoLongerFound() {
        refreshTokenService.save(2L, "some-token");

        refreshTokenService.delete(2L);

        assertThat(refreshTokenService.find(2L)).isEmpty();
    }

    @Test
    void returnsEmptyForUserIdNeverSaved() {
        assertThat(refreshTokenService.find(999L)).isEmpty();
    }
}
