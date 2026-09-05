package com.motivhub.be.workspace.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.TransferOwnershipRequest;
import com.motivhub.be.workspace.dto.WorkspaceCreateRequest;
import com.motivhub.be.workspace.dto.WorkspaceDetailResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.dto.WorkspaceUpdateRequest;
import com.motivhub.be.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WorkspaceControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "ws-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsWorkspaceAndReturnsOwnerRole() throws Exception {
        User user = newUser("c1");

        mockMvc.perform(post("/api/workspaces")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceCreateRequest("테스트 워크스페이스"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OWNER"));
    }

    @Test
    void listsOnlyMyWorkspaces() throws Exception {
        User me = newUser("c2");
        User other = newUser("c2-other");
        workspaceService.create(me.getId(), "내 워크스페이스");
        workspaceService.create(other.getId(), "남의 워크스페이스");

        mockMvc.perform(get("/api/workspaces").header("Authorization", "Bearer " + tokenFor(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("내 워크스페이스"));
    }

    @Test
    void deletesWorkspaceAsOwner() throws Exception {
        User owner = newUser("c3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제될 워크스페이스");

        mockMvc.perform(delete("/api/workspaces/{id}", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void returns403WhenNonOwnerTriesToDelete() throws Exception {
        User owner = newUser("c4-owner");
        User member = newUser("c4-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "워크스페이스4");

        mockMvc.perform(delete("/api/workspaces/{id}", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void renamesWorkspaceAsOwner() throws Exception {
        User owner = newUser("c5");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이름수정 전");

        mockMvc.perform(patch("/api/workspaces/{id}", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceUpdateRequest("이름수정 후"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름수정 후"));
    }

    @Test
    void returns403WhenNonOwnerTriesToRename() throws Exception {
        User owner = newUser("c6-owner");
        User member = newUser("c6-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "워크스페이스6");

        mockMvc.perform(patch("/api/workspaces/{id}", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceUpdateRequest("몰래 변경"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDetailReturnsMembersWithNicknames() throws Exception {
        User owner = newUser("c7-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "상세 워크스페이스7");

        mockMvc.perform(get("/api/workspaces/{id}", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].user.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }
}
