package com.motivhub.be.user.dto;

import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String nickname,
        boolean nicknameConfigured,
        String email,
        String profileImageUrl,
        SocialProvider provider,
        LocalDateTime createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getNickname(), user.isNicknameConfigured(), user.getEmail(),
                user.getProfileImageUrl(), user.getProvider(), user.getCreatedAt());
    }
}
