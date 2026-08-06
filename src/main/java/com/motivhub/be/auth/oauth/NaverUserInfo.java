package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.SocialProvider;
import java.util.Map;

public class NaverUserInfo implements OAuth2UserInfo {

    private final Map<String, Object> response;

    @SuppressWarnings("unchecked")
    public NaverUserInfo(Map<String, Object> attributes) {
        this.response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.NAVER;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(response.get("id"));
    }

    @Override
    public String getEmail() {
        return (String) response.get("email");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) response.get("profile_image");
    }
}
