package com.motivhub.be.user.service;

import com.motivhub.be.auth.oauth.OAuth2UserInfo;
import com.motivhub.be.auth.oauth.OAuth2UserInfoFactory;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepository;
    private final RandomNicknameGenerator nicknameGenerator;

    public UserRegistrationService(UserRepository userRepository, RandomNicknameGenerator nicknameGenerator) {
        this.userRepository = userRepository;
        this.nicknameGenerator = nicknameGenerator;
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
        User saved = userRepository.save(user);
        log.info("New user registered: provider={}, providerId={}", userInfo.getProvider(), userInfo.getProviderId());
        return saved;
    }

    private User reactivateIfWithdrawn(User user, OAuth2UserInfo userInfo) {
        if (user.isWithdrawn()) {
            user.reactivate(userInfo.getEmail(), userInfo.getProfileImageUrl(), nicknameGenerator.generate());
            User saved = userRepository.save(user);
            log.info("User reactivated: userId={}", saved.getId());
            return saved;
        }
        return user;
    }
}
