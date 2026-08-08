package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.service.UserRegistrationService;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRegistrationService userRegistrationService;

    public CustomOAuth2UserService(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        try {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            User user = resolveUser(registrationId, oAuth2User.getAttributes());
            return new CustomOAuth2User(user.getId(), oAuth2User.getAttributes());
        } catch (RuntimeException e) {
            throw new OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("server_error", e.getMessage(), null), e);
        }
    }

    public User resolveUser(String registrationId, Map<String, Object> attributes) {
        return userRegistrationService.resolveUser(registrationId, attributes);
    }
}
