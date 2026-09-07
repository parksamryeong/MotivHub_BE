package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskChecklistItemResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
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

class TaskChecklistItemServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskChecklistItemService taskChecklistItemService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "checklist-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void ownerCanAddChecklistItemsInOrder() {
        User owner = newUser("owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskChecklistItemService.create(owner.getId(), task.id(), "첫 항목");
        taskChecklistItemService.create(owner.getId(), task.id(), "두번째 항목");

        List<TaskChecklistItemResponse> items = taskChecklistItemService.list(owner.getId(), task.id());
        assertThat(items).extracting(TaskChecklistItemResponse::content).containsExactly("첫 항목", "두번째 항목");
        assertThat(items).extracting(TaskChecklistItemResponse::orderIndex).containsExactly(0, 1);
        assertThat(items).allMatch(item -> !item.isDone());
    }

    @Test
    void nonAssigneeNonOwnerCannotAddChecklistItem() {
        User owner = newUser("owner2");
        User bystander = newUser("bystander2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 권한 워크스페이스");
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskChecklistItemService.create(bystander.getId(), task.id(), "몰래 항목"))
                .isInstanceOf(TaskEditForbiddenException.class);
    }

    @Test
    void assigneeCanAddChecklistItem() {
        User owner = newUser("owner3");
        User assignee = newUser("assignee3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 담당자 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(assignee.getId())));

        TaskChecklistItemResponse item = taskChecklistItemService.create(assignee.getId(), task.id(), "담당자가 추가");

        assertThat(item.content()).isEqualTo("담당자가 추가");
    }

    @Test
    void updatingContentOnlyKeepsDoneFlag() {
        User owner = newUser("owner4");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 수정 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("수정 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskChecklistItemResponse created = taskChecklistItemService.create(owner.getId(), task.id(), "원래 내용");

        TaskChecklistItemResponse updated = taskChecklistItemService.update(
                owner.getId(), task.id(), created.id(), "바뀐 내용", null);

        assertThat(updated.content()).isEqualTo("바뀐 내용");
        assertThat(updated.isDone()).isFalse();
    }

    @Test
    void togglingDoneOnlyKeepsContent() {
        User owner = newUser("owner5");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 토글 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("토글 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskChecklistItemResponse created = taskChecklistItemService.create(owner.getId(), task.id(), "토글 내용");

        TaskChecklistItemResponse updated = taskChecklistItemService.update(
                owner.getId(), task.id(), created.id(), null, true);

        assertThat(updated.content()).isEqualTo("토글 내용");
        assertThat(updated.isDone()).isTrue();
    }

    @Test
    void deletingItemRemovesItFromList() {
        User owner = newUser("owner6");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 삭제 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("삭제 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskChecklistItemResponse created = taskChecklistItemService.create(owner.getId(), task.id(), "삭제될 항목");

        taskChecklistItemService.delete(owner.getId(), task.id(), created.id());

        assertThat(taskChecklistItemService.list(owner.getId(), task.id())).isEmpty();
    }

    @Test
    void updatingUnknownItemThrows() {
        User owner = newUser("owner7");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 미존재 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("미존재 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskChecklistItemService.update(owner.getId(), task.id(), 999_999L, "x", null))
                .isInstanceOf(com.motivhub.be.task.exception.TaskChecklistItemNotFoundException.class);
    }
}
