package com.motivhub.be.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskNoteUpdateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
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
class TaskNoteControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private TaskService taskService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void gettingNoteBeforeAnyoneWritesReturns200WithNullContent() throws Exception {
        User owner = createUniqueUser("note-ctrl-get-empty");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 API 빈 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 API 빈 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        String responseBody = mockMvc.perform(get("/api/tasks/{taskId}/note", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.get("taskId").asLong()).isEqualTo(task.id());
        assertThat(json.get("content").isNull()).isTrue();
    }

    @Test
    void memberCreatesNoteViaPatch() throws Exception {
        User owner = createUniqueUser("note-ctrl-create-owner");
        User member = createUniqueUser("note-ctrl-create-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 API 생성 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 API 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(patch("/api/tasks/{taskId}/note", task.id())
                        .header("Authorization", "Bearer " + tokenFor(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskNoteUpdateRequest("회의록 초안"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("회의록 초안"))
                .andExpect(jsonPath("$.updatedBy.id").value(member.getId()));
    }

    @Test
    void patchWithNullContentReturns400() throws Exception {
        User owner = createUniqueUser("note-ctrl-invalid-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 API 검증 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 API 검증 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(patch("/api/tasks/{taskId}/note", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void nonMemberCannotGetNoteReturns403() throws Exception {
        User owner = createUniqueUser("note-ctrl-outsider-owner");
        User outsider = createUniqueUser("note-ctrl-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 API 비멤버 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 API 비멤버 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(get("/api/tasks/{taskId}/note", task.id())
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_WORKSPACE_MEMBER"));
    }

    @Test
    void gettingNoteForUnknownTaskReturns404() throws Exception {
        User owner = createUniqueUser("note-ctrl-unknown-owner");

        mockMvc.perform(get("/api/tasks/{taskId}/note", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }
}
