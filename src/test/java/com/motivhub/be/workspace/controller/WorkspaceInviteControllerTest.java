package com.motivhub.be.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceInviteCreateRequest;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceInviteMailService;
import com.motivhub.be.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WorkspaceInviteControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;
    @MockitoBean private WorkspaceInviteMailService workspaceInviteMailService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "invite-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsLinkInvite() throws Exception {
        User owner = newUser("ic1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "초대 테스트");

        mockMvc.perform(post("/api/workspaces/{id}/invites", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteCreateRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void acceptingInviteAddsMember() throws Exception {
        User owner = newUser("ic2-owner");
        User joiner = newUser("ic2-joiner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "수락 테스트");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/invites", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteCreateRequest(null))))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(createResponse).get("token").asText();

        mockMvc.perform(post("/api/invites/{token}/accept", token)
                        .header("Authorization", "Bearer " + tokenFor(joiner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
    }
}
