package com.motivhub.be.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.user.domain.SocialProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OAuth2UserInfoFactoryTest {

    @Test
    void mapsGoogleAttributes() {
        Map<String, Object> attrs = Map.of(
                "sub", "google-123", "email", "a@gmail.com", "picture", "http://img/g.png");

        OAuth2UserInfo info = OAuth2UserInfoFactory.of("google", attrs);

        assertThat(info.getProvider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(info.getProviderId()).isEqualTo("google-123");
        assertThat(info.getEmail()).isEqualTo("a@gmail.com");
        assertThat(info.getProfileImageUrl()).isEqualTo("http://img/g.png");
    }

    @Test
    void mapsGithubAttributes() {
        Map<String, Object> attrs = Map.of(
                "id", 456, "email", "b@github.com", "avatar_url", "http://img/gh.png");

        OAuth2UserInfo info = OAuth2UserInfoFactory.of("github", attrs);

        assertThat(info.getProvider()).isEqualTo(SocialProvider.GITHUB);
        assertThat(info.getProviderId()).isEqualTo("456");
        assertThat(info.getEmail()).isEqualTo("b@github.com");
        assertThat(info.getProfileImageUrl()).isEqualTo("http://img/gh.png");
    }

    @Test
    void mapsKakaoNestedAttributes() {
        Map<String, Object> attrs = Map.of(
                "id", 789,
                "kakao_account", Map.of(
                        "email", "c@kakao.com",
                        "profile", Map.of("profile_image_url", "http://img/k.png")
                ));

        OAuth2UserInfo info = OAuth2UserInfoFactory.of("kakao", attrs);

        assertThat(info.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(info.getProviderId()).isEqualTo("789");
        assertThat(info.getEmail()).isEqualTo("c@kakao.com");
        assertThat(info.getProfileImageUrl()).isEqualTo("http://img/k.png");
    }

    @Test
    void mapsNaverAttributesNestedUnderResponse() {
        Map<String, Object> attrs = Map.of(
                "resultcode", "00",
                "response", Map.of(
                        "id", "naver-1",
                        "email", "d@naver.com",
                        "profile_image", "http://img/n.png"
                ));

        OAuth2UserInfo info = OAuth2UserInfoFactory.of("naver", attrs);

        assertThat(info.getProvider()).isEqualTo(SocialProvider.NAVER);
        assertThat(info.getProviderId()).isEqualTo("naver-1");
        assertThat(info.getEmail()).isEqualTo("d@naver.com");
        assertThat(info.getProfileImageUrl()).isEqualTo("http://img/n.png");
    }

    @Test
    void githubEmailIsNullWhenNotPublic() {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("id", 456);
        attrs.put("email", null);
        attrs.put("avatar_url", "http://img/gh.png");

        OAuth2UserInfo info = OAuth2UserInfoFactory.of("github", attrs);

        assertThat(info.getEmail()).isNull();
    }

    @Test
    void throwsExceptionForUnsupportedProvider() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> OAuth2UserInfoFactory.of("facebook", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
