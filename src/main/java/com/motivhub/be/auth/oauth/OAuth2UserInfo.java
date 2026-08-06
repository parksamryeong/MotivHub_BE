package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.SocialProvider;

public interface OAuth2UserInfo {
    SocialProvider getProvider();
    String getProviderId();
    String getEmail();
    String getProfileImageUrl();
}
