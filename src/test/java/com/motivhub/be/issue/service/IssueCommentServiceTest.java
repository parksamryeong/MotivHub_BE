package com.motivhub.be.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.issue.dto.IssueCommentResponse;
import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.exception.IssueNotFoundException;
import com.motivhub.be.issue.repository.IssueCommentRepository;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IssueCommentServiceTest extends AbstractIntegrationTest {

    @Autowired private IssueService issueService;
    @Autowired private IssueCommentService issueCommentService;
    @Autowired private IssueCommentRepository issueCommentRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "issue-comment-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    @Test
    void anyAuthenticatedUserCanCommentRegardlessOfWorkspace() {
        User author = newUser("comment-owner1");
        User outsider = newUser("comment-outsider1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 댓글 워크스페이스1");
        IssueResponse issue = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);

        IssueCommentResponse comment = issueCommentService.create(outsider.getId(), issue.id(), "저도 겪었어요");

        assertThat(comment.content()).isEqualTo("저도 겪었어요");
        assertThat(comment.author().id()).isEqualTo(outsider.getId());
    }

    @Test
    void commentingOnUnknownIssueThrows() {
        User user = newUser("comment-owner2");

        assertThatThrownBy(() -> issueCommentService.create(user.getId(), 999_999L, "내용"))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void listReturnsCommentsInCreationOrder() {
        User author = newUser("comment-owner3");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 댓글 워크스페이스3");
        IssueResponse issue = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);
        issueCommentService.create(author.getId(), issue.id(), "첫 댓글");
        issueCommentService.create(author.getId(), issue.id(), "두번째 댓글");

        List<IssueCommentResponse> comments = issueCommentService.list(issue.id());

        assertThat(comments).extracting(IssueCommentResponse::content).containsExactly("첫 댓글", "두번째 댓글");
    }

    @Test
    void deletingIssueAlsoDeletesComments() {
        User author = newUser("comment-owner4");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 댓글 삭제 워크스페이스4");
        IssueResponse issue = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);
        issueCommentService.create(author.getId(), issue.id(), "댓글");

        issueService.delete(author.getId(), issue.id());

        assertThat(issueCommentRepository.findByIssueIdOrderByCreatedAtAsc(issue.id())).isEmpty();
    }

    @Test
    void listingCommentsOnUnknownIssueThrows() {
        assertThatThrownBy(() -> issueCommentService.list(999_999L))
                .isInstanceOf(IssueNotFoundException.class);
    }
}
