package com.motivhub.be.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshTokenServiceTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void findsSavedRefreshTokenByUserIdAndDeviceId() {
        refreshTokenService.save(1L, "device-A", "refresh-token-value");

        assertThat(refreshTokenService.find(1L, "device-A")).contains("refresh-token-value");
    }

    @Test
    void deletedTokenIsNoLongerFound() {
        refreshTokenService.save(2L, "device-A", "some-token");

        refreshTokenService.delete(2L, "device-A");

        assertThat(refreshTokenService.find(2L, "device-A")).isEmpty();
    }

    @Test
    void returnsEmptyForUserIdNeverSaved() {
        assertThat(refreshTokenService.find(999L, "device-A")).isEmpty();
    }

    @Test
    void differentDevicesForSameUserDoNotOverwriteEachOther() {
        refreshTokenService.save(3L, "device-A", "token-for-device-a");
        refreshTokenService.save(3L, "device-B", "token-for-device-b");

        assertThat(refreshTokenService.find(3L, "device-A")).contains("token-for-device-a");
        assertThat(refreshTokenService.find(3L, "device-B")).contains("token-for-device-b");
    }

    @Test
    void deleteAllRemovesEveryDeviceKeyForUser() {
        refreshTokenService.save(4L, "device-A", "token-a");
        refreshTokenService.save(4L, "device-B", "token-b");

        refreshTokenService.deleteAll(4L);

        assertThat(refreshTokenService.find(4L, "device-A")).isEmpty();
        assertThat(refreshTokenService.find(4L, "device-B")).isEmpty();
    }

    @Test
    void deleteAllForUserWithNoSavedTokensDoesNotThrow() {
        assertThatCode(() -> refreshTokenService.deleteAll(888L)).doesNotThrowAnyException();
    }
}
