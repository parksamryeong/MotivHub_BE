package com.motivhub.be.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void withdrawSetsStatusToWithdrawnAndMasksPersonalInfo() {
        User user = User.create(SocialProvider.GITHUB, "p1", "a@test.com", "nickname1", "http://img");

        user.withdraw();

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getNickname()).isNotEqualTo("nickname1");
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void reactivateSetsStatusBackToActiveAndClearsDeletedAt() {
        User user = User.create(SocialProvider.GITHUB, "p2", "a@test.com", "nickname2", "http://img");
        user.withdraw();

        user.reactivate("new@test.com", "http://new", "newnickname");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://new");
        assertThat(user.getNickname()).isEqualTo("newnickname");
        assertThat(user.isNicknameConfigured()).isFalse();
    }

    @Test
    void updateNicknameSetsNicknameAndMarksConfigured() {
        User user = User.create(SocialProvider.GITHUB, "p3", "a@test.com", "nickname3", "http://img");

        user.updateNickname("changednick");

        assertThat(user.getNickname()).isEqualTo("changednick");
        assertThat(user.isNicknameConfigured()).isTrue();
    }

    @Test
    void isWithdrawnReflectsStatusBeforeAndAfterWithdraw() {
        User user = User.create(SocialProvider.GITHUB, "p4", "a@test.com", "nickname4", "http://img");

        assertThat(user.isWithdrawn()).isFalse();

        user.withdraw();

        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    void createEmailAccountUsesEmailAsProviderIdAndStoresPassword() {
        User user = User.createEmailAccount("test@example.com", "테스트닉네임", "encoded-password-hash");

        assertThat(user.getProvider()).isEqualTo(SocialProvider.EMAIL);
        assertThat(user.getProviderId()).isEqualTo("test@example.com");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getNickname()).isEqualTo("테스트닉네임");
        assertThat(user.getPassword()).isEqualTo("encoded-password-hash");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isNicknameConfigured()).isTrue();
    }

    @Test
    void withdrawTombstonesProviderIdAndClearsPasswordForEmailAccount() {
        User user = User.createEmailAccount("withdraw-test@example.com", "withdrawtestuser", "encoded-password-hash");

        user.withdraw();

        assertThat(user.getProviderId()).isNotEqualTo("withdraw-test@example.com");
        assertThat(user.getProviderId()).startsWith("withdrawn:");
        assertThat(user.getPassword()).isNull();
        assertThat(user.isWithdrawn()).isTrue();
    }
}
