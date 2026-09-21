package com.motivhub.be.task.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.dto.TaskChecklistItemCreateRequest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskPeriodUpdateRequest;
import com.motivhub.be.task.dto.TaskPriorityUpdateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.dto.TaskStatusUpdateRequest;
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
class TaskControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private TaskService taskService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "task-ctrl-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getId());
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
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

    @Test
    void createAndListResponsesIncludeAssigneeAndCreatorNicknames() throws Exception {
        User owner = newUser("t3-owner");
        User assignee = newUser("t3-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "닉네임 API 워크스페이스");
        joinAsMember(workspace.id(), assignee);

        mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCreateRequest(
                                "닉네임 API 태스크", "설명", LocalDate.now(), LocalDate.now().plusDays(1),
                                List.of(assignee.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy.id").value(owner.getId()))
                .andExpect(jsonPath("$.createdBy.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$.assignees[0].id").value(assignee.getId()))
                .andExpect(jsonPath("$.assignees[0].nickname").value(assignee.getNickname()));

        mockMvc.perform(get("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].createdBy.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$[0].assignees[0].nickname").value(assignee.getNickname()));
    }

    @Test
    void listActivitiesReturnsRecordedChangesNewestFirst() throws Exception {
        User owner = newUser("t4-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("활동로그 API 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskStatusUpdateRequest(TaskStatus.IN_PROGRESS))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{id}/activities", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("CHANGE_STATUS"))
                .andExpect(jsonPath("$[0].newValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].actor.nickname").value(owner.getNickname()))
                .andExpect(jsonPath("$[1].action").value("CREATE"));
    }

    @Test
    void getDetailIncludesChecklistItems() throws Exception {
        User owner = newUser("t5-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "상세 체크리스트 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("상세 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();
        mockMvc.perform(post("/api/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemCreateRequest("상세 확인용 항목"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checklistItems.length()").value(1))
                .andExpect(jsonPath("$.checklistItems[0].content").value("상세 확인용 항목"))
                .andExpect(jsonPath("$.checklistItems[0].isDone").value(false))
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void gettingDescriptionYjsStateBeforeAnyoneSavesReturnsNullState() throws Exception {
        User owner = createUniqueUser("desc-yjs-ctrl-empty");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "설명 yjs API 빈 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 yjs API 빈 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(get("/api/tasks/{id}/description/yjs-state", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").doesNotExist());
    }

    @Test
    void gettingDescriptionYjsStateAfterSaveReturnsBase64EncodedValue() throws Exception {
        User owner = createUniqueUser("desc-yjs-ctrl-saved");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "설명 yjs API 저장 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 yjs API 저장 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        byte[] state = new byte[] {1, 2, 3};
        taskService.updateDescriptionYjsState(task.id(), state);

        String responseBody = mockMvc.perform(get("/api/tasks/{id}/description/yjs-state", task.id())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        assertThat(json.get("state").asText()).isEqualTo(java.util.Base64.getEncoder().encodeToString(state));
    }

    @Test
    void plainMemberWithoutEditPermissionCannotGetDescriptionYjsState() throws Exception {
        User owner = createUniqueUser("desc-yjs-ctrl-perm-owner");
        User plainMember = createUniqueUser("desc-yjs-ctrl-perm-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "설명 yjs API 권한 워크스페이스");
        joinAsMember(workspace.id(), plainMember);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 yjs API 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        mockMvc.perform(get("/api/tasks/{id}/description/yjs-state", task.id())
                        .header("Authorization", "Bearer " + tokenFor(plainMember)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listMineReturnsTasksAssignedToCallerViaApi() throws Exception {
        User user = newUser("ctrl-mine-1");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 API 워크스페이스");

        mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskCreateRequest(
                                "API 내할일 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                                List.of(user.getId())))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/mine")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("API 내할일 태스크"))
                .andExpect(jsonPath("$[0].workspaceId").value(workspace.id()))
                .andExpect(jsonPath("$[0].checklistTotal").value(0))
                .andExpect(jsonPath("$[0].hasComments").value(false));
    }

    @Test
    void listMineReturnsEmptyArrayWhenNoAssignmentsViaApi() throws Exception {
        User user = newUser("ctrl-mine-empty");

        mockMvc.perform(get("/api/tasks/mine")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createdTaskDefaultsToMediumPriorityViaApi() throws Exception {
        User owner = newUser("prio-default");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 기본값 API 워크스페이스");

        mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("기본 우선순위 태스크", null, LocalDate.now(),
                                        LocalDate.now().plusDays(1), List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    void changePriorityUpdatesItViaApi() throws Exception {
        User owner = newUser("prio-change");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 변경 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("우선순위 변경 태스크", null, LocalDate.now(),
                                        LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{id}/priority", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskPriorityUpdateRequest(TaskPriority.URGENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    void nonMemberCannotChangePriorityViaApiReturns403() throws Exception {
        User owner = newUser("prio-forbidden-owner");
        User outsider = newUser("prio-forbidden-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 권한 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("권한 확인 태스크", null, LocalDate.now(),
                                        LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/{id}/priority", taskId)
                        .header("Authorization", "Bearer " + tokenFor(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskPriorityUpdateRequest(TaskPriority.HIGH))))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateTaskViaApiReturnsNewTask() throws Exception {
        User owner = newUser("dup-api-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 API 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("API 복제 원본", null, LocalDate.now(),
                                        LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/tasks/{id}/duplicate", taskId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API 복제 원본"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(taskId)));
    }

    @Test
    void nonMemberCannotDuplicateTaskViaApiReturns403() throws Exception {
        User owner = newUser("dup-api-forbidden-owner");
        User outsider = newUser("dup-api-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 API 권한 워크스페이스");
        String createResponse = mockMvc.perform(post("/api/workspaces/{id}/tasks", workspace.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("API 권한 확인 태스크", null, LocalDate.now(),
                                        LocalDate.now().plusDays(1), List.of()))))
                .andReturn().getResponse().getContentAsString();
        Long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(post("/api/tasks/{id}/duplicate", taskId)
                        .header("Authorization", "Bearer " + tokenFor(outsider)))
                .andExpect(status().isForbidden());
    }
}
