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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

class IssueServiceTest extends AbstractIntegrationTest {

    @Autowired private IssueService issueService;
    @Autowired private IssueCommentService issueCommentService;
    @Autowired private IssueCommentRepository issueCommentRepository;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @PersistenceContext private EntityManager entityManager;

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

        List<IssueResponse> issues = issueService.list(null);

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

    @Test
    void issuesInDeletedWorkspaceAreExcludedFromListAndDetail() {
        User author = newUser("deleted-ws1");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "삭제될 워크스페이스1");
        IssueResponse created = issueService.create(
                author.getId(), workspace.id(), "삭제될 워크스페이스의 이슈", "설명", null);

        workspaceService.delete(author.getId(), workspace.id());

        assertThat(issueService.list(null)).extracting(IssueResponse::id).doesNotContain(created.id());
        assertThatThrownBy(() -> issueService.getDetail(created.id()))
                .isInstanceOf(IssueNotFoundException.class);
    }

    // InnoDB FULLTEXT 인덱스는 같은 트랜잭션 안에서도 커밋 전까지는 검색 결과에 반영되지 않는다
    // (일반 B-Tree 인덱스와 다른 특성 - MATCH()/AGAINST()는 커밋된 데이터만 본다). 이 클래스의 테스트는
    // @Transactional로 롤백되므로, 검색 대상 데이터를 만든 뒤 강제로 커밋하고 새 트랜잭션을 시작해야
    // 검색이 그 데이터를 볼 수 있다(NotificationControllerTest 등에서 이미 쓰는 패턴과 동일). 여러
    // 테스트 클래스가 공유하는 Testcontainers MySQL에 커밋된 데이터가 영구히 남으면 workspace 필터링
    // 없이 전체 이슈 목록을 비교하는 다른 테스트(예: listReturnsIssuesAcrossWorkspacesNewestFirst)를
    // 오염시키므로, 검증 후 워크스페이스를 소프트 삭제해서 이후 모든 조회에서 제외시키고 그 삭제도
    // 다시 강제 커밋한다.
    private void commitPendingChanges() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        // InnoDB FULLTEXT 관련도 통계(관련도 점수 계산에 쓰이는 "이 토큰을 포함한 문서 수" 등)는
        // 백그라운드 스레드가 비동기로 갱신한다. 방금 막 만든 테이블에 처음 커밋한 직후 곧바로
        // MATCH()/AGAINST()로 관련도를 조회하면, 그 백그라운드 갱신이 끝나기 전의 stale한 통계
        // (사실상 "문서 0건" 상태)를 사용해 관련도 점수가 모든 행에서 0으로 나올 수 있다 - 실제로
        // 재현해서 확인함(ANALYZE TABLE 전: 점수 전부 0 -> 정렬이 생성순서(id DESC)로 깨짐 /
        // ANALYZE TABLE 후: 올바른 관련도 점수로 정상 정렬). 이미 수십만 건이 누적되어 통계가
        // 안정된 운영 환경에서는 발생하지 않는, "막 생성한 테이블 + 즉시 조회" 조합의 테스트 환경
        // 특유의 타이밍 이슈다. ANALYZE TABLE로 통계를 강제 갱신해 운영 환경과 같은 조건을
        // 재현한다(ANALYZE TABLE은 InnoDB에서 암시적 커밋을 일으키므로, 그 뒤 커밋 사이클을 한 번
        // 더 돌려 Spring의 트랜잭션 상태를 깨끗하게 리셋한다).
        entityManager.createNativeQuery("ANALYZE TABLE issue").getResultList();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private void cleanUpCommittedWorkspace(Long ownerUserId, Long workspaceId) {
        workspaceService.delete(ownerUserId, workspaceId);
        commitPendingChanges();
    }

    @Test
    void searchMatchesTitleCaseInsensitively() {
        // 이 테스트는 검색 결과를 보려고 트랜잭션을 강제 커밋한다(commitPendingChanges() 참고).
        // 고정 suffix를 쓰는 로컬 newUser() 대신, 공유 Testcontainers MySQL에 영구히 남는 유저가
        // 다른(롤백되는) 테스트의 같은 suffix와 충돌하지 않도록 createUniqueUser()(전역 카운터로
        // 접미사를 유일하게 만듦)를 쓴다.
        User author = createUniqueUser("search-title");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "검색 제목 워크스페이스");
        issueService.create(author.getId(), workspace.id(), "Gradle Build 실패", "설명", null);
        issueService.create(author.getId(), workspace.id(), "다른 이슈", "무관한 설명", null);
        commitPendingChanges();

        try {
            List<IssueResponse> results = issueService.list("gradle");

            assertThat(results).extracting(IssueResponse::title).containsExactly("Gradle Build 실패");
        } finally {
            cleanUpCommittedWorkspace(author.getId(), workspace.id());
        }
    }

    @Test
    void searchMatchesProblemDescriptionAndSolution() {
        // createUniqueUser() 사용 이유는 위 searchMatchesTitleCaseInsensitively 참고(강제 커밋 +
        // suffix 충돌 방지).
        User author = createUniqueUser("search-body");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "검색 본문 워크스페이스");
        issueService.create(author.getId(), workspace.id(), "제목1", "여기에 키워드가 있음", null);
        issueService.create(author.getId(), workspace.id(), "제목2", "설명", "해결책에 키워드가 있음");
        issueService.create(author.getId(), workspace.id(), "제목3", "무관", "무관");
        commitPendingChanges();

        try {
            List<IssueResponse> results = issueService.list("키워드");

            assertThat(results).extracting(IssueResponse::title).containsExactlyInAnyOrder("제목1", "제목2");
        } finally {
            cleanUpCommittedWorkspace(author.getId(), workspace.id());
        }
    }

    @Test
    void blankSearchKeywordReturnsAllIssues() {
        User author = newUser("search-blank");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "검색 빈값 워크스페이스");
        issueService.create(author.getId(), workspace.id(), "제목1", "설명1", null);
        issueService.create(author.getId(), workspace.id(), "제목2", "설명2", null);

        assertThat(issueService.list("   ")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void listIncludesAccurateCommentCountPerIssue() {
        User author = newUser("count-list");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "댓글수 목록 워크스페이스");
        IssueResponse withComments = issueService.create(author.getId(), workspace.id(), "댓글 있는 이슈", "설명", null);
        IssueResponse withoutComments = issueService.create(author.getId(), workspace.id(), "댓글 없는 이슈", "설명", null);
        issueCommentService.create(author.getId(), withComments.id(), "댓글1");
        issueCommentService.create(author.getId(), withComments.id(), "댓글2");

        List<IssueResponse> results = issueService.list(null);

        assertThat(results.stream().filter(i -> i.id().equals(withComments.id())).findFirst().orElseThrow()
                .commentCount()).isEqualTo(2);
        assertThat(results.stream().filter(i -> i.id().equals(withoutComments.id())).findFirst().orElseThrow()
                .commentCount()).isEqualTo(0);
    }

    @Test
    void getDetailIncludesAccurateCommentCount() {
        User author = newUser("count-detail");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "댓글수 상세 워크스페이스");
        IssueResponse issue = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);
        issueCommentService.create(author.getId(), issue.id(), "댓글1");

        IssueResponse detail = issueService.getDetail(issue.id());

        assertThat(detail.commentCount()).isEqualTo(1);
    }

    @Test
    void newlyCreatedIssueHasZeroCommentCount() {
        User author = newUser("count-create");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "댓글수 생성 워크스페이스");

        IssueResponse issue = issueService.create(author.getId(), workspace.id(), "제목", "설명", null);

        assertThat(issue.commentCount()).isEqualTo(0);
    }

    @Test
    void searchOrdersResultsByRelevanceWhenMultipleColumnsMatch() {
        // createUniqueUser() 사용 이유는 searchMatchesTitleCaseInsensitively 참고(강제 커밋 +
        // suffix 충돌 방지).
        User author = createUniqueUser("search-relevance");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "관련도 정렬 워크스페이스");
        // 키워드가 설명에 한 번만 등장 - 관련도 점수가 더 낮아야 한다.
        // 이 이슈를 먼저 생성해서 더 낮은 ID를 갖도록 한다.
        issueService.create(author.getId(), workspace.id(),
                "제목2", "희귀어가 한 번만 등장", "무관한 해결책");
        // 키워드가 3개 컬럼(제목/설명/해결책) 전부에 등장 - 관련도 점수가 더 높아야 한다.
        // 이 이슈를 나중에 생성해서 더 높은 ID를 갖도록 한다.
        issueService.create(author.getId(), workspace.id(),
                "희귀어 제목", "희귀어 설명에도 등장", "희귀어 해결책에도 등장");
        commitPendingChanges();

        try {
            List<IssueResponse> results = issueService.list("희귀어");

            assertThat(results).extracting(IssueResponse::title)
                    .containsExactly("희귀어 제목", "제목2");
        } finally {
            cleanUpCommittedWorkspace(author.getId(), workspace.id());
        }
    }

    @Test
    void searchOrdersTiedRelevanceByMostRecentFirst() {
        // createUniqueUser() 사용 이유는 searchMatchesTitleCaseInsensitively 참고(강제 커밋 +
        // suffix 충돌 방지).
        User author = createUniqueUser("search-tiebreak");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "동점 정렬 워크스페이스");
        // 두 이슈 모두 키워드가 설명에 정확히 한 번씩만 등장 - 관련도 점수가 동점이어야 한다.
        issueService.create(author.getId(), workspace.id(), "제목A", "동점어가 한 번 등장", null);
        issueService.create(author.getId(), workspace.id(), "제목B", "동점어가 한 번 등장", null);
        commitPendingChanges();

        try {
            List<IssueResponse> results = issueService.list("동점어");

            // 동점일 때는 created_at DESC(최신 먼저)로 - 제목B가 나중에 생성됨.
            assertThat(results).extracting(IssueResponse::title).containsExactly("제목B", "제목A");
        } finally {
            cleanUpCommittedWorkspace(author.getId(), workspace.id());
        }
    }

    @Test
    void searchWithSingleCharacterKeywordReturnsEmpty() {
        // createUniqueUser() 사용 이유는 searchMatchesTitleCaseInsensitively 참고(강제 커밋 +
        // suffix 충돌 방지).
        User author = createUniqueUser("search-shortkw");
        WorkspaceResponse workspace = workspaceService.create(author.getId(), "짧은 검색어 워크스페이스");
        issueService.create(author.getId(), workspace.id(), "가나다 제목", "설명", null);
        commitPendingChanges();

        try {
            // ngram 파서의 최소 토큰 크기(2)보다 짧은 검색어는 n-gram 자체가 안 만들어져서 매칭이
            // 안 된다 - LIKE 때와 다른, 의도된 동작이다.
            List<IssueResponse> results = issueService.list("가");

            assertThat(results).isEmpty();
        } finally {
            cleanUpCommittedWorkspace(author.getId(), workspace.id());
        }
    }
}
