package com.motivhub.be.realtime.config;

import java.security.Principal;

public class StompPrincipal implements Principal {

    private final String name;
    private final String nickname;
    private final String profileImageUrl;

    public StompPrincipal(Long userId) {
        this(userId, null, null);
    }

    // 커서 브로드캐스트처럼 메시지마다 발신자 닉네임/프로필 이미지가 필요한 경우를 위해
    // CONNECT 시점에 한 번만 User를 조회해 세션 Principal에 함께 담아둔다 - 메시지 전송마다
    // DB를 다시 조회하지 않기 위함이다.
    public StompPrincipal(Long userId, String nickname, String profileImageUrl) {
        this.name = String.valueOf(userId);
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
}
