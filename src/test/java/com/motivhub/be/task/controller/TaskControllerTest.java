package com.motivhub.be.task.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskPeriodUpdateRequest;
import com.motivhub.be.task.dto.TaskResponse;
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
class TaskControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "task-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void createsAndListsTask() throws Exception {
        User owner = newUser("t1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "태스크 API 워크스페이스");

        mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("API 태스크", "설명", LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(get("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void memberCannotUpdatePeriodReturns403() throws Exception {
        User owner = newUser("t2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "기간 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("기간 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();
        User outsider = newUser("t2-outsider");

        mockMvc.perform(patch("/api/tasks/{id}/period", taskId)
                        .header("Authorization", "Bearer " + tokenFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskPeriodUpdateRequest(LocalDate.now(), LocalDate.now().plusDays(20)))))
                .andExpect(status().isForbidden());
    }
}
