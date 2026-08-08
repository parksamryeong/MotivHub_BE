package com.motivhub.be.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.domain.UserStatus;
import com.motivhub.be.user.dto.NicknameUpdateRequest;
import com.motivhub.be.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UserControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtProvider jwtProvider;

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void getsMyProfile() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p1", "a@test.com", "user_p1nick", null));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("user_p1nick"));
    }

    @Test
    void getsMyPage() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p2", "b@test.com", "user_p2nick", null));

        mockMvc.perform(get("/api/users/me/mypage").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("user_p2nick"));
    }

    @Test
    void returnsAvailableTrueForUsableNickname() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p9", "g@test.com", "user_p9nick", null));

        mockMvc.perform(get("/api/users/nickname-check")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .param("nickname", "brandnewnick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void returnsAvailableFalseForExistingNickname() throws Exception {
        userRepository.save(User.create(SocialProvider.GITHUB, "p3", null, "takennick", null));
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p10", "h@test.com", "user_p10nick", null));

        mockMvc.perform(get("/api/users/nickname-check")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .param("nickname", "takennick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void updatesNickname() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p4", "c@test.com", "user_p4nick", null));

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameUpdateRequest("새닉네임"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.nicknameConfigured").value(true));
    }

    @Test
    void returns409WhenUpdatingToDuplicateNickname() throws Exception {
        userRepository.save(User.create(SocialProvider.GITHUB, "p5", null, "alreadyused", null));
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p6", "d@test.com", "user_p6nick", null));

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameUpdateRequest("alreadyused"))))
                .andExpect(status().isConflict());
    }

    @Test
    void returns400ForInvalidNicknameFormat() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p7", "e@test.com", "user_p7nick", null));

        mockMvc.perform(patch("/api/users/me/nickname")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameUpdateRequest("a"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void softDeletesUserOnWithdrawal() throws Exception {
        User user = userRepository.save(
                User.create(SocialProvider.GITHUB, "p8", "f@test.com", "user_p8nick", null));

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isNoContent());

        User withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getEmail()).isNull();
    }
}
