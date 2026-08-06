package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.SocialProvider;
import java.util.Map;

public class GithubUserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public GithubUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.GITHUB;
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) attributes.get("avatar_url");
    }
}
