package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.dto.TaskActivityLogResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskActivityLogServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskActivityLogService taskActivityLogService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "activity-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    @Test
    void recordedActivityAppearsInList() {
        User owner = newUser("owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 워크스페이스");
        TaskResponse taskResponse = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("활동로그 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        Task task = taskService.getTask(taskResponse.id());

        taskActivityLogService.record(task, owner, TaskActivityAction.UPDATE_CONTENT, "name", "이전 이름", "새 이름");

        List<TaskActivityLogResponse> activities = taskActivityLogService.list(owner.getId(), taskResponse.id());

        assertThat(activities).hasSize(2);
        assertThat(activities.get(0).action()).isEqualTo(TaskActivityAction.UPDATE_CONTENT);
        assertThat(activities.get(0).field()).isEqualTo("name");
        assertThat(activities.get(0).oldValue()).isEqualTo("이전 이름");
        assertThat(activities.get(0).newValue()).isEqualTo("새 이름");
        assertThat(activities.get(0).actor().nickname()).isEqualTo(owner.getNickname());
    }

    @Test
    void listOrdersNewestFirst() {
        User owner = newUser("owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 순서 워크스페이스");
        TaskResponse taskResponse = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("순서 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        Task task = taskService.getTask(taskResponse.id());

        taskActivityLogService.record(task, owner, TaskActivityAction.UPDATE_CONTENT, "name", "A", "B");
        taskActivityLogService.record(task, owner, TaskActivityAction.UPDATE_CONTENT, "name", "B", "C");

        List<TaskActivityLogResponse> activities = taskActivityLogService.list(owner.getId(), taskResponse.id());

        assertThat(activities).extracting(TaskActivityLogResponse::newValue).containsExactly("C", "B", null);
    }

    @Test
    void nonMemberCannotListActivities() {
        User owner = newUser("owner3");
        User outsider = newUser("outsider3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 비멤버 워크스페이스");
        TaskResponse taskResponse = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("비멤버 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskActivityLogService.list(outsider.getId(), taskResponse.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }
}
