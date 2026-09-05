package com.motivhub.be.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import org.junit.jupiter.api.Test;

class UserSummaryTest {

    @Test
    void fromCopiesIdNicknameAndProfileImageUrl() {
        User user = User.create(SocialProvider.GITHUB, "p1", "a@test.com", "닉네임1", "http://img");

        UserSummary summary = UserSummary.from(user);

        assertThat(summary.id()).isEqualTo(user.getId());
        assertThat(summary.nickname()).isEqualTo("닉네임1");
        assertThat(summary.profileImageUrl()).isEqualTo("http://img");
    }
}
