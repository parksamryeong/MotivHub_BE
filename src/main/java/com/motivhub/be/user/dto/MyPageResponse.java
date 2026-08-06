package com.motivhub.be.user.dto;

import com.motivhub.be.user.domain.User;
import java.time.LocalDateTime;

public record MyPageResponse(
        String nickname,
        String email,
        String profileImageUrl,
        LocalDateTime createdAt) {

    public static MyPageResponse from(User user) {
        return new MyPageResponse(
                user.getNickname(), user.getEmail(), user.getProfileImageUrl(), user.getCreatedAt());
    }
}
