package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskNotFoundException;
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

class TaskServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "task-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    @Test
    void memberCanCreateTask() {
        User creator = newUser("creator1");
        WorkspaceResponse workspace = workspaceService.create(creator.getId(), "태스크 워크스페이스");

        TaskResponse task = taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("첫 태스크", "설명", LocalDate.now(), LocalDate.now().plusDays(3), List.of()));

        assertThat(task.name()).isEqualTo("첫 태스크");
        assertThat(task.status().name()).isEqualTo("WAITING");
    }

    @Test
    void createdTaskAppearsInWorkspaceList() {
        User creator = newUser("creator2");
        WorkspaceResponse workspace = workspaceService.create(creator.getId(), "목록 워크스페이스");
        taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("태스크A", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        List<TaskResponse> tasks = taskService.listByWorkspace(creator.getId(), workspace.id());

        assertThat(tasks).extracting(TaskResponse::name).containsExactly("태스크A");
    }

    @Test
    void nonMemberCannotCreateTask() {
        User owner = newUser("owner3");
        User outsider = newUser("outsider3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "비공개 워크스페이스");

        assertThatThrownBy(() -> taskService.create(outsider.getId(), workspace.id(),
                new TaskCreateRequest("몰래 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of())))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void getDetailFailsForUnknownTask() {
        User user = newUser("detail4");

        assertThatThrownBy(() -> taskService.getDetail(user.getId(), 999_999L))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
