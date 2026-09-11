package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.service.NotificationService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskCommentForbiddenException;
import com.motivhub.be.task.exception.TaskCommentNotFoundException;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;

class TaskCommentServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskCommentService taskCommentService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "comment-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void anyMemberCanComment() {
        User owner = newUser("comment-owner");
        User member = newUser("comment-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskCommentResponse comment = taskCommentService.create(member.getId(), task.id(), "화이팅입니다");

        assertThat(comment.content()).isEqualTo("화이팅입니다");
    }

    @Test
    void commentsAreListedInCreationOrder() {
        User owner = newUser("comment-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 워크스페이스2");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 태스크2", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskCommentService.create(owner.getId(), task.id(), "첫 댓글");
        taskCommentService.create(owner.getId(), task.id(), "두번째 댓글");

        List<TaskCommentResponse> comments = taskCommentService.list(owner.getId(), task.id());

        assertThat(comments).extracting(TaskCommentResponse::content)
                .containsExactly("첫 댓글", "두번째 댓글");
    }

    @Test
    void nonMemberCannotComment() {
        User owner = newUser("comment-owner3");
        User outsider = newUser("comment-outsider3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 워크스페이스3");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 태스크3", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskCommentService.create(outsider.getId(), task.id(), "몰래 댓글"))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void commentIncludesAuthorNickname() {
        User owner = newUser("comment-owner4");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 닉네임 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 닉네임 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskCommentResponse comment = taskCommentService.create(owner.getId(), task.id(), "닉네임 확인용 댓글");

        assertThat(comment.author().nickname()).isEqualTo(owner.getNickname());
    }

    @Test
    void withdrawnAuthorStillShowsMaskedNicknameInCommentList() {
        User owner = newUser("comment-owner5");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "탈퇴 댓글 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("탈퇴 댓글 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskCommentService.create(owner.getId(), task.id(), "탈퇴 전 댓글");
        owner.withdraw();
        userRepository.save(owner);

        List<TaskCommentResponse> comments = taskCommentService.list(owner.getId(), task.id());

        assertThat(comments.get(0).author().nickname()).startsWith("탈퇴한 사용자_");
    }

    @Test
    void newCommentHasUpdatedAtEqualToCreatedAt() {
        User owner = newUser("edit-new-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 신규 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 신규 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskCommentResponse comment = taskCommentService.create(owner.getId(), task.id(), "첫 내용");

        assertThat(comment.updatedAt()).isEqualTo(comment.createdAt());
    }

    @Test
    void authorCanUpdateOwnComment() {
        User owner = newUser("edit-author-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 수정 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 수정 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(owner.getId(), task.id(), "원래 내용");

        TaskCommentResponse updated = taskCommentService.update(owner.getId(), task.id(), created.id(), "바뀐 내용");

        assertThat(updated.content()).isEqualTo("바뀐 내용");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(updated.createdAt());
    }

    @Test
    void nonAuthorMemberCannotUpdateComment() {
        User owner = newUser("edit-owner2");
        User author = newUser("edit-author2");
        User bystander = newUser("edit-bystander2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 수정 권한 워크스페이스");
        joinAsMember(workspace.id(), author);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 수정 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "작성자 댓글");

        assertThatThrownBy(() -> taskCommentService.update(bystander.getId(), task.id(), created.id(), "몰래 수정"))
                .isInstanceOf(TaskCommentForbiddenException.class);
    }

    @Test
    void ownerCannotUpdateOthersComment() {
        User owner = newUser("edit-owner3");
        User author = newUser("edit-author3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 수정 오너 워크스페이스");
        joinAsMember(workspace.id(), author);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 수정 오너 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "작성자 댓글2");

        assertThatThrownBy(() -> taskCommentService.update(owner.getId(), task.id(), created.id(), "오너가 수정 시도"))
                .isInstanceOf(TaskCommentForbiddenException.class);
    }

    @Test
    void authorCanDeleteOwnComment() {
        User owner = newUser("del-author-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 삭제 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 삭제 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(owner.getId(), task.id(), "삭제될 댓글");

        taskCommentService.delete(owner.getId(), task.id(), created.id());

        assertThat(taskCommentService.list(owner.getId(), task.id())).isEmpty();
    }

    @Test
    void ownerCanDeleteOthersComment() {
        User owner = newUser("del-owner2");
        User author = newUser("del-author2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 삭제 오너 워크스페이스");
        joinAsMember(workspace.id(), author);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 삭제 오너 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "오너가 지울 댓글");

        taskCommentService.delete(owner.getId(), task.id(), created.id());

        assertThat(taskCommentService.list(owner.getId(), task.id())).isEmpty();
    }

    @Test
    void nonAuthorNonOwnerMemberCannotDeleteComment() {
        User owner = newUser("del-owner3");
        User author = newUser("del-author3");
        User bystander = newUser("del-bystander3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 삭제 권한 워크스페이스");
        joinAsMember(workspace.id(), author);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 삭제 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "구경꾼이 못 지움");

        assertThatThrownBy(() -> taskCommentService.delete(bystander.getId(), task.id(), created.id()))
                .isInstanceOf(TaskCommentForbiddenException.class);
    }

    @Test
    void updatingUnknownCommentThrows() {
        User owner = newUser("edit-unknown-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 미존재 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 미존재 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskCommentService.update(owner.getId(), task.id(), 999_999L, "x"))
                .isInstanceOf(TaskCommentNotFoundException.class);
    }

    @Test
    void updatingCommentFromDifferentTaskThrows() {
        User owner = newUser("edit-crosstask-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 cross-task 워크스페이스");
        TaskResponse taskA = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("태스크A", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskResponse taskB = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("태스크B", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse commentOnA = taskCommentService.create(owner.getId(), taskA.id(), "A 태스크 댓글");

        assertThatThrownBy(() -> taskCommentService.update(owner.getId(), taskB.id(), commentOnA.id(), "잘못된 접근"))
                .isInstanceOf(TaskCommentNotFoundException.class);
    }

    @Test
    void kickedMemberCannotUpdateOwnComment() {
        User owner = newUser("edit-kick-owner");
        User author = newUser("edit-kick-author");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 추방 수정 워크스페이스");
        joinAsMember(workspace.id(), author);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 추방 수정 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "추방 전 댓글");
        workspaceService.kick(owner.getId(), workspace.id(), author.getId());

        assertThatThrownBy(() -> taskCommentService.update(author.getId(), task.id(), created.id(), "추방 후 수정 시도"))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void creatingCommentNotifiesAssigneesAndCreatorButNotAuthor() {
        User owner = newUser("comment-notify-owner");
        User assignee = newUser("comment-notify-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 알림 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 알림 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        taskCommentService.create(assignee.getId(), task.id(), "담당자가 남긴 댓글");
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void kickedMemberCannotDeleteOwnComment() {
        User owner = newUser("del-kick-owner");
        User author = newUser("del-kick-author");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "댓글 추방 삭제 워크스페이스");
        joinAsMember(workspace.id(), author);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("댓글 추방 삭제 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        TaskCommentResponse created = taskCommentService.create(author.getId(), task.id(), "추방 전 댓글2");
        workspaceService.kick(owner.getId(), workspace.id(), author.getId());

        assertThatThrownBy(() -> taskCommentService.delete(author.getId(), task.id(), created.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }
}
