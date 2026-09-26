package com.motivhub.be.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.service.TaskService;
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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DashboardControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private TaskService taskService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "dash-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    @Test
    void getStatsWithoutWorkspaceIdReturnsAllScope() throws Exception {
        User user = newUser("api1");

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.statusCounts.length()").value(4))
                .andExpect(jsonPath("$.priorityCounts.length()").value(4))
                .andExpect(jsonPath("$.completionTrend.length()").value(7));
    }

    @Test
    void getStatsWithWorkspaceIdReturnsWorkspaceScope() throws Exception {
        User user = newUser("api2");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "API 통계 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("API 통계 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        mockMvc.perform(get("/api/dashboard/stats").queryParam("workspaceId", workspace.id().toString())
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("WORKSPACE"))
                .andExpect(jsonPath("$.workspaceId").value(workspace.id()))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'WAITING')].count").value(1));
    }

    @Test
    void getStatsForWorkspaceYouAreNotAMemberOfReturns403() throws Exception {
        User owner = newUser("api3-owner");
        User outsider = newUser("api3-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "API 권한없음 워크스페이스");

        mockMvc.perform(get("/api/dashboard/stats").queryParam("workspaceId", workspace.id().toString())
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStatsWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }
}
