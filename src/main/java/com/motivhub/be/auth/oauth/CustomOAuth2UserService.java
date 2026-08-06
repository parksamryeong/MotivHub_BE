package com.motivhub.be.auth.oauth;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.user.service.RandomNicknameGenerator;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RandomNicknameGenerator nicknameGenerator;

    public CustomOAuth2UserService(UserRepository userRepository, RandomNicknameGenerator nicknameGenerator) {
        this.userRepository = userRepository;
        this.nicknameGenerator = nicknameGenerator;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User user = resolveUser(registrationId, oAuth2User.getAttributes());
        return new CustomOAuth2User(user.getId(), oAuth2User.getAttributes());
    }

    @Transactional
    public User resolveUser(String registrationId, Map<String, Object> attributes) {
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.of(registrationId, attributes);
        return userRepository.findByProviderAndProviderId(userInfo.getProvider(), userInfo.getProviderId())
                .map(existing -> reactivateIfWithdrawn(existing, userInfo))
                .orElseGet(() -> createUser(userInfo));
    }

    private User createUser(OAuth2UserInfo userInfo) {
        User user = User.create(
                userInfo.getProvider(), userInfo.getProviderId(), userInfo.getEmail(),
                nicknameGenerator.generate(), userInfo.getProfileImageUrl());
        return userRepository.save(user);
    }

    private User reactivateIfWithdrawn(User user, OAuth2UserInfo userInfo) {
        if (user.isWithdrawn()) {
            user.reactivate(userInfo.getEmail(), userInfo.getProfileImageUrl(), nicknameGenerator.generate());
            return userRepository.save(user);
        }
        return user;
    }
}
