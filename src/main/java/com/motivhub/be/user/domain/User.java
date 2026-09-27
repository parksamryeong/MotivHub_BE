package com.motivhub.be.user.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(name = "nickname_configured", nullable = false)
    private boolean nicknameConfigured;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(SocialProvider provider, String providerId, String email,
                  String nickname, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.nicknameConfigured = false;
        this.profileImageUrl = profileImageUrl;
        this.status = UserStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public static User create(SocialProvider provider, String providerId, String email,
                               String randomNickname, String profileImageUrl) {
        return new User(provider, providerId, email, randomNickname, profileImageUrl);
    }

    // 이메일 회원가입 전용 - provider_id에 이메일 주소 자체를 넣어서, provider+provider_id
    // 유니크 제약이 "같은 이메일로 이메일 계정 중복 가입 방지"를 자동으로 보장하게 한다.
    // 이 관례를 아는 곳은 이 메서드 하나뿐이어야 한다(호출하는 쪽은 몰라도 됨).
    public static User createEmailAccount(String email, String nickname, String encodedPassword) {
        User user = new User(SocialProvider.EMAIL, email, email, nickname, null);
        user.password = encodedPassword;
        user.nicknameConfigured = true;
        return user;
    }

    public void reactivate(String email, String profileImageUrl, String randomNickname) {
        this.status = UserStatus.ACTIVE;
        this.deletedAt = null;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.nickname = randomNickname;
        this.nicknameConfigured = false;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
        this.nickname = "탈퇴한 사용자_" + this.id;
        this.email = null;
        this.profileImageUrl = null;
        if (this.provider == SocialProvider.EMAIL) {
            // 이메일 계정은 provider_id가 이메일 주소 자체라서, 그대로 두면 (provider, provider_id)
            // 유니크 제약 때문에 같은 이메일로 다시는 가입도 로그인도 할 수 없게 영구히 잠긴다.
            // 토큰스톤 값으로 바꿔서 이 주소를 진짜로 반환하고, 원한다면 나중에 완전히 새로운
            // 이메일 계정으로 재가입할 수 있게 한다.
            this.providerId = "withdrawn:" + this.id;
            this.password = null;
        }
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameConfigured = true;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
