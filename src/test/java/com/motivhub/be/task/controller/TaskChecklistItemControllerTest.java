package com.motivhub.be.task.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskChecklistItemCreateRequest;
import com.motivhub.be.task.dto.TaskChecklistItemUpdateRequest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TaskChecklistItemControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "checklist-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsUpdatesAndDeletesChecklistItem() throws Exception {
        User owner = newUser("c1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 API 워크스페이스");
        String createTaskResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("체크리스트 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createTaskResponse).get("id").asLong();

        String createItemResponse = mockMvc.perform(post("/api/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemCreateRequest("할 일 1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("할 일 1"))
                .andExpect(jsonPath("$.isDone").value(false))
                .andReturn().getResponse().getContentAsString();
        Long itemId = objectMapper.readTree(createItemResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{taskId}/checklist-items/{itemId}", taskId, itemId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemUpdateRequest(null, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDone").value(true))
                .andExpect(jsonPath("$.content").value("할 일 1"));

        mockMvc.perform(delete("/api/tasks/{taskId}/checklist-items/{itemId}", taskId, itemId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNoContent());
    }

    @Test
    void nonMemberCannotCreateChecklistItemReturns403() throws Exception {
        User owner = newUser("c2-owner");
        User outsider = newUser("c2-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 권한 API 워크스페이스");
        String createTaskResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("권한 체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createTaskResponse).get("id").asLong();

        mockMvc.perform(post("/api/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + tokenFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemCreateRequest("몰래 항목"))))
                .andExpect(status().isForbidden());
    }
}
