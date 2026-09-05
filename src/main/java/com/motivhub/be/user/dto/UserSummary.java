package com.motivhub.be.user.dto;

import com.motivhub.be.user.domain.User;

public record UserSummary(Long id, String nickname, String profileImageUrl) {
    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
