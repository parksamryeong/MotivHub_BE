package com.motivhub.be.task.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCommentCreateRequest;
import com.motivhub.be.task.dto.TaskCommentUpdateRequest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TaskCommentControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private TaskService taskService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "comment-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void createsCommentAndReturnsAuthorNickname() throws Exception {
        User owner = newUser("c1-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 API 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(post("/api/tasks/{taskId}/comments", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentCreateRequest("화이팅입니다"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("화이팅입니다"))
                .andExpect(jsonPath("$.author.id").value(owner.getId()))
                .andExpect(jsonPath("$.author.nickname").value(owner.getNickname()));
    }

    @Test
    void listsCommentsWithAuthorNickname() throws Exception {
        User owner = newUser("c2-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 목록 API 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 목록 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(post("/api/tasks/{taskId}/comments", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentCreateRequest("첫 댓글"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{taskId}/comments", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$[0].author.nickname").value(owner.getNickname()));
    }

    @Test
    void authorUpdatesAndDeletesOwnComment() throws Exception {
        User owner = newUser("c3-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 수정삭제 API 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 수정삭제 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        String createResponse = mockMvc.perform(post("/api/tasks/{taskId}/comments", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentCreateRequest("원래 댓글"))))
                .andReturn().getResponse().getContentAsString();
        Long commentId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{taskId}/comments/{commentId}", task.id(), commentId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentUpdateRequest("수정된 댓글"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("수정된 댓글"));

        mockMvc.perform(delete("/api/tasks/{taskId}/comments/{commentId}", task.id(), commentId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonAuthorCannotUpdateCommentReturns403() throws Exception {
        User owner = newUser("c4-owner");
        User bystander = newUser("c4-bystander");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 수정 권한 API 워크스페이스");
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 수정 권한 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        String createResponse = mockMvc.perform(post("/api/tasks/{taskId}/comments", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentCreateRequest("오너 댓글"))))
                .andReturn().getResponse().getContentAsString();
        Long commentId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{taskId}/comments/{commentId}", task.id(), commentId)
                        .header("Authorization", "Bearer " + tokenFor(bystander))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCommentUpdateRequest("몰래 수정"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingUnknownCommentReturns404() throws Exception {
        User owner = newUser("c5-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 미존재 API 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 미존재 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(delete("/api/tasks/{taskId}/comments/{commentId}", task.id(), 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNotFound());
    }
}
