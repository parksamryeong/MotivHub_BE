package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskNoteResponse;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.repository.TaskNoteRepository;
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

class TaskNoteServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskNoteService taskNoteService;
    @Autowired private TaskNoteRepository taskNoteRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "note-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void gettingNoteBeforeAnyoneWritesReturnsNullContent() {
        User owner = newUser("note-get-empty-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 없음 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 없음 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskNoteResponse response = taskNoteService.get(owner.getId(), task.id());

        assertThat(response.taskId()).isEqualTo(task.id());
        assertThat(response.content()).isNull();
        assertThat(response.updatedBy()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void nonAssigneeMemberCanCreateNoteViaUpsert() {
        User owner = newUser("note-create-owner");
        User member = newUser("note-create-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 생성 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskNoteResponse response = taskNoteService.upsert(member.getId(), task.id(), "회의록 초안");

        assertThat(response.content()).isEqualTo("회의록 초안");
        assertThat(response.updatedBy().id()).isEqualTo(member.getId());
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void upsertingAgainOverwritesContentAndUpdatedBy() {
        User owner = newUser("note-overwrite-owner");
        User member = newUser("note-overwrite-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 덮어쓰기 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 덮어쓰기 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskNoteService.upsert(owner.getId(), task.id(), "첫 버전");

        TaskNoteResponse response = taskNoteService.upsert(member.getId(), task.id(), "두번째 버전");

        assertThat(response.content()).isEqualTo("두번째 버전");
        assertThat(response.updatedBy().id()).isEqualTo(member.getId());

        TaskNoteResponse fetched = taskNoteService.get(owner.getId(), task.id());
        assertThat(fetched.content()).isEqualTo("두번째 버전");
    }

    @Test
    void nonMemberCannotGetOrUpsertNote() {
        User owner = newUser("note-outsider-owner");
        User outsider = newUser("note-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 비멤버 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 비멤버 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskNoteService.get(outsider.getId(), task.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
        assertThatThrownBy(() -> taskNoteService.upsert(outsider.getId(), task.id(), "몰래 적기"))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void deletingTaskAlsoDeletesItsNote() {
        User owner = newUser("note-cascade-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 삭제연쇄 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 삭제연쇄 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskNoteService.upsert(owner.getId(), task.id(), "지워질 노트");

        taskService.delete(owner.getId(), task.id());

        assertThat(taskNoteRepository.findByTaskId(task.id())).isEmpty();
    }
}
