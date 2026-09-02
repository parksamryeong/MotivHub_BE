package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
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

class TaskCommentServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskCommentService taskCommentService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private UserRepository userRepository;

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
}
