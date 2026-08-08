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
}
