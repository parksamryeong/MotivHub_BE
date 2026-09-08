package com.motivhub.be.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.exception.IssueForbiddenException;
import com.motivhub.be.issue.exception.IssueNotFoundException;
import com.motivhub.be.issue.repository.IssueCommentRepository;
import com.motivhub.be.support.AbstractIntegrationTest;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IssueServiceTest extends AbstractIntegrationTest {

    @Autowired private IssueService issueService;
    @Autowired private IssueCommentService issueCommentService;
    @Autowired private IssueCommentRepository issueCommentRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "issue-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void memberCanCreateIssue() {
        User author = newUser("create-owner1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 워크스페이스1");

        IssueResponse issue = issueService.create(
                author.getId(), workspace.id(), "빌드 실패", "gradle build가 안 됨", null);

        assertThat(issue.title()).isEqualTo("빌드 실패");
        assertThat(issue.workspaceId()).isEqualTo(workspace.id());
        assertThat(issue.workspaceName()).isEqualTo("이슈 워크스페이스1");
        assertThat(issue.solution()).isNull();
        assertThat(issue.author().id()).isEqualTo(author.getId());
    }

    @Test
    void creatingWithBlankSolutionNormalizesToNull() {
        User author = newUser("create-owner2");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 워크스페이스2");

        IssueResponse issue = issueService.create(
                author.getId(), workspace.id(), "질문", "왜 안되지", "   ");

        assertThat(issue.solution()).isNull();
    }

    @Test
    void nonMemberCannotCreateIssue() {
        User owner = newUser("create-owner3");
        User outsider = newUser("create-outsider3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이슈 워크스페이스3");

        assertThatThrownBy(() -> issueService.create(
                outsider.getId(), workspace.id(), "제목", "설명", null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void listReturnsIssuesAcrossWorkspacesNewestFirst() {
        User authorA = newUser("list-authorA");
        User authorB = newUser("list-authorB");
        WorkspaceResponse workspaceA = workspaceService.create(authorA.getId(), "이슈 목록 워크스페이스A");
        WorkspaceResponse workspaceB = workspaceService.create(authorB.getId(), "이슈 목록 워크스페이스B");
        issueService.create(authorA.getId(), workspaceA.id(), "첫번째", "설명1", null);
        issueService.create(authorB.getId(), workspaceB.id(), "두번째", "설명2", null);

        List<IssueResponse> issues = issueService.list();

        assertThat(issues).extracting(IssueResponse::title).containsExactly("두번째", "첫번째");
    }

    @Test
    void getDetailReturnsIssueFromAnyUserRegardlessOfWorkspaceMembership() {
        User author = newUser("detail-author1");
        User outsider = newUser("detail-outsider1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 상세 워크스페이스1");
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "제목", "설명", "해결방법");

        IssueResponse detail = issueService.getDetail(created.id());

        assertThat(detail.title()).isEqualTo("제목");
        assertThat(detail.solution()).isEqualTo("해결방법");
        // outsider가 이 워크스페이스 멤버가 아니어도 getDetail 자체는 워크스페이스를 인자로도 안 받음 —
        // 컨트롤러 레벨에서 인증된 유저 누구나 호출 가능하다는 게 이 테스트가 보여주는 지점(서비스는
        // 원래도 workspaceId나 outsider의 존재를 아예 신경 안 씀).
        assertThat(outsider).isNotNull();
    }

    @Test
    void getDetailFailsForUnknownIssue() {
        assertThatThrownBy(() -> issueService.getDetail(999_999L))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void authorCanUpdateOwnIssue() {
        User author = newUser("update-author1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 수정 워크스페이스1");
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "원래 제목", "원래 설명", null);

        IssueResponse updated = issueService.update(
                author.getId(), created.id(), "바뀐 제목", null, "해결됨");

        assertThat(updated.title()).isEqualTo("바뀐 제목");
        assertThat(updated.problemDescription()).isEqualTo("원래 설명");
        assertThat(updated.solution()).isEqualTo("해결됨");
    }

    @Test
    void updatingSolutionToEmptyStringClearsIt() {
        User author = newUser("update-author2");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 수정 워크스페이스2");
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "제목", "설명", "해결방법");

        IssueResponse updated = issueService.update(author.getId(), created.id(), null, null, "");

        assertThat(updated.solution()).isNull();
    }

    @Test
    void ownerCannotUpdateOthersIssue() {
        User owner = newUser("update-owner3");
        User author = newUser("update-author3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이슈 수정 워크스페이스3");
        joinAsMember(workspace.id(), author);
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "제목", "설명", null);

        assertThatThrownBy(() -> issueService.update(owner.getId(), created.id(), "해킹 시도", null, null))
                .isInstanceOf(IssueForbiddenException.class);
    }

    @Test
    void authorCanDeleteOwnIssue() {
        User author = newUser("delete-author1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 삭제 워크스페이스1");
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "제목", "설명", null);

        issueService.delete(author.getId(), created.id());

        assertThatThrownBy(() -> issueService.getDetail(created.id()))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void ownerCannotDeleteOthersIssue() {
        User owner = newUser("delete-owner2");
        User author = newUser("delete-author2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이슈 삭제 워크스페이스2");
        joinAsMember(workspace.id(), author);
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "제목", "설명", null);

        assertThatThrownBy(() -> issueService.delete(owner.getId(), created.id()))
                .isInstanceOf(IssueForbiddenException.class);
    }

    @Test
    void deletingIssueRemovesItEvenWithComments() {
        User author = newUser("delete-with-comments1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "이슈 댓글 삭제 검증 워크스페이스");
        IssueResponse created = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);
        issueCommentService.create(author.getId(), created.id(), "댓글");

        issueService.delete(author.getId(), created.id());

        assertThatThrownBy(() -> issueService.getDetail(created.id()))
                .isInstanceOf(IssueNotFoundException.class);
    }
}
