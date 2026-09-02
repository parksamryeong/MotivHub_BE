package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TaskServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "task-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
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

    @Test
    void assigneeCanUpdateContent() {
        User owner = newUser("content-owner");
        User assignee = newUser("content-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내용수정 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원래 이름", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        TaskResponse updated = taskService.updateContent(assignee.getId(), task.id(), "바뀐 이름", "바뀐 설명");

        assertThat(updated.name()).isEqualTo("바뀐 이름");
    }

    @Test
    void nonAssigneeCannotUpdateContent() {
        User owner = newUser("content-owner2");
        User assignee = newUser("content-assignee2");
        User bystander = newUser("content-bystander2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내용수정 워크스페이스2");
        joinAsMember(workspace.id(), assignee);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원래 이름2", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        assertThatThrownBy(() -> taskService.updateContent(bystander.getId(), task.id(), "바뀐 이름2", null))
                .isInstanceOf(TaskEditForbiddenException.class);
    }

    @Test
    void onlyOwnerCanUpdatePeriod() {
        User owner = newUser("period-owner");
        User assignee = newUser("period-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "기간수정 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("기간 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        assertThatThrownBy(() -> taskService.updatePeriod(assignee.getId(), task.id(), LocalDate.now(), LocalDate.now().plusDays(10)))
                .isInstanceOf(TaskPeriodEditForbiddenException.class);

        TaskResponse updated = taskService.updatePeriod(owner.getId(), task.id(), LocalDate.now(), LocalDate.now().plusDays(10));
        assertThat(updated.dueDate()).isEqualTo(LocalDate.now().plusDays(10));
    }

    @Test
    void creatorCanDeleteOwnTask() {
        User owner = newUser("del-owner3");
        User member = newUser("del-member3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(member.getId(), workspace.id(),
                new TaskCreateRequest("내가 만든 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.delete(member.getId(), task.id());

        assertThatThrownBy(() -> taskService.getDetail(owner.getId(), task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void nonCreatorNonOwnerCannotDelete() {
        User owner = newUser("del-owner4");
        User creator = newUser("del-creator4");
        User bystander = newUser("del-bystander4");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스2");
        joinAsMember(workspace.id(), creator);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("남의 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskService.delete(bystander.getId(), task.id()))
                .isInstanceOf(TaskEditForbiddenException.class);
    }
}
