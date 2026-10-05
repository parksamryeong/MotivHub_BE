package com.motivhub.be.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsUserByProviderAndProviderId() {
        User user = User.create(SocialProvider.GITHUB, "12345", "a@test.com", "user_abc123", null);
        userRepository.save(user);

        var found = userRepository.findByProviderAndProviderId(SocialProvider.GITHUB, "12345");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("user_abc123");
    }

    @Test
    void returnsEmptyForUnknownProviderCombo() {
        var found = userRepository.findByProviderAndProviderId(SocialProvider.GOOGLE, "no-such-id");

        assertThat(found).isEmpty();
    }

    @Test
    void checksNicknameDuplicateStatus() {
        userRepository.save(User.create(SocialProvider.KAKAO, "999", null, "unique_nick", null));

        assertThat(userRepository.existsByNickname("unique_nick")).isTrue();
        assertThat(userRepository.existsByNickname("no_such_nick")).isFalse();
    }

    @Test
    void findFirstByEmailOrderByIdAscReturnsEarliestAccountWhenMultipleShareEmail() {
        User earlier = userRepository.save(
                User.create(SocialProvider.GOOGLE, "google-1", "shared@example.com", "earlieruser", null));
        userRepository.save(
                User.create(SocialProvider.GITHUB, "github-1", "shared@example.com", "lateruser", null));

        var found = userRepository.findFirstByEmailOrderByIdAsc("shared@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(earlier.getId());
    }

    @Test
    void findFirstByEmailOrderByIdAscReturnsEmptyForUnknownEmail() {
        var found = userRepository.findFirstByEmailOrderByIdAsc("no-account@example.com");

        assertThat(found).isEmpty();
    }
}
