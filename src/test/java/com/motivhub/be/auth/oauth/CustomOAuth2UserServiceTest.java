package com.motivhub.be.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.user.service.RandomNicknameGenerator;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RandomNicknameGenerator nicknameGenerator;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(userRepository, nicknameGenerator);
    }

    @Test
    void autoRegistersNewUser() {
        Map<String, Object> attrs = Map.of("id", 111, "email", "new@github.com", "avatar_url", "http://a");
        when(userRepository.findByProviderAndProviderId(SocialProvider.GITHUB, "111"))
                .thenReturn(Optional.empty());
        when(nicknameGenerator.generate()).thenReturn("user_abcdef");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.resolveUser("github", attrs);

        assertThat(result.getProvider()).isEqualTo(SocialProvider.GITHUB);
        assertThat(result.getProviderId()).isEqualTo("111");
        assertThat(result.getNickname()).isEqualTo("user_abcdef");
        assertThat(result.isNicknameConfigured()).isFalse();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void returnsExistingActiveUserAsIs() {
        Map<String, Object> attrs = Map.of("id", 222, "email", "e@github.com", "avatar_url", "http://a");
        User existing = User.create(SocialProvider.GITHUB, "222", "old@github.com", "user_old01", "http://old");
        when(userRepository.findByProviderAndProviderId(SocialProvider.GITHUB, "222"))
                .thenReturn(Optional.of(existing));

        User result = service.resolveUser("github", attrs);

        assertThat(result).isSameAs(existing);
        assertThat(result.getEmail()).isEqualTo("old@github.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void reactivatesWithdrawnUserOnRelogin() {
        Map<String, Object> attrs = Map.of("id", 333, "email", "re@github.com", "avatar_url", "http://re");
        User withdrawn = User.create(SocialProvider.GITHUB, "333", "old@github.com", "user_old02", "http://old");
        withdrawn.withdraw();
        when(userRepository.findByProviderAndProviderId(SocialProvider.GITHUB, "333"))
                .thenReturn(Optional.of(withdrawn));
        when(nicknameGenerator.generate()).thenReturn("user_newnick");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.resolveUser("github", attrs);

        assertThat(result.isWithdrawn()).isFalse();
        assertThat(result.getEmail()).isEqualTo("re@github.com");
        assertThat(result.isNicknameConfigured()).isFalse();
        assertThat(result.getNickname()).isEqualTo("user_newnick");
        verify(userRepository).save(withdrawn);
    }
}
