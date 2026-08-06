package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.SocialProvider;
import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> kakaoAccount;
    private final Map<String, Object> profile;

    @SuppressWarnings("unchecked")
    public KakaoUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.kakaoAccount = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        this.profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getEmail() {
        return (String) kakaoAccount.get("email");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) profile.get("profile_image_url");
    }
}
