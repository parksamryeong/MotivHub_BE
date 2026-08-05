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
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameConfigured = true;
    }

    public boolean isWithdrawn() {
        return this.status == UserStatus.WITHDRAWN;
    }
}
