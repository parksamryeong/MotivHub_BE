package com.motivhub.be.issue.service;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.exception.IssueForbiddenException;
import com.motivhub.be.issue.exception.IssueNotFoundException;
import com.motivhub.be.issue.repository.IssueCommentCount;
import com.motivhub.be.issue.repository.IssueCommentRepository;
import com.motivhub.be.issue.repository.IssueRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssueService {

    private final IssueRepository issueRepository;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;
    private final IssueCommentRepository issueCommentRepository;

    public IssueService(IssueRepository issueRepository, WorkspaceService workspaceService,
                         UserRepository userRepository, IssueCommentRepository issueCommentRepository) {
        this.issueRepository = issueRepository;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
        this.issueCommentRepository = issueCommentRepository;
    }

    @Transactional
    public IssueResponse create(Long userId, Long workspaceId, String title, String problemDescription,
                                 String solution) {
        Workspace workspace = workspaceService.getMembership(workspaceId, userId).getWorkspace();
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Issue issue = issueRepository.save(Issue.create(workspace, title, problemDescription, solution, author));
        return IssueResponse.from(issue);
    }

    public List<IssueResponse> list(String keyword) {
        List<Issue> issues = (keyword == null || keyword.isBlank())
                ? issueRepository.findAllOrderByCreatedAtDesc()
                : issueRepository.searchByKeyword(keyword.trim());
        Map<Long, Long> commentCounts = commentCountByIssueId(issues.stream().map(Issue::getId).toList());
        return issues.stream()
                .map(issue -> IssueResponse.from(issue, commentCounts.getOrDefault(issue.getId(), 0L)))
                .toList();
    }

    public IssueResponse getDetail(Long issueId) {
        Issue issue = issueRepository.findByIdFetchAuthorAndWorkspace(issueId)
                .orElseThrow(() -> new IssueNotFoundException("이슈를 찾을 수 없습니다."));
        return IssueResponse.from(issue, issueCommentRepository.countByIssueId(issueId));
    }

    // 이슈마다 개별 쿼리를 날리지 않고, 한 번의 집계 쿼리로 전부 가져온 뒤 메모리에서 조합한다(N+1 방지).
    private Map<Long, Long> commentCountByIssueId(List<Long> issueIds) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (IssueCommentCount row : issueCommentRepository.countByIssueIdsGroupByIssue(issueIds)) {
            result.put(row.issueId(), row.count());
        }
        return result;
    }

    public Issue getIssue(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException("이슈를 찾을 수 없습니다."));
    }

    @Transactional
    public IssueResponse update(Long userId, Long issueId, String title, String problemDescription,
                                 String solution) {
        Issue issue = getIssue(issueId);
        if (!issue.isAuthoredBy(userId)) {
            throw new IssueForbiddenException("작성자 본인만 수정할 수 있습니다.");
        }
        issue.update(title, problemDescription, solution);
        return IssueResponse.from(issue, issueCommentRepository.countByIssueId(issueId));
    }

    @Transactional
    public void delete(Long userId, Long issueId) {
        Issue issue = getIssue(issueId);
        if (!issue.isAuthoredBy(userId)) {
            throw new IssueForbiddenException("작성자 본인만 삭제할 수 있습니다.");
        }
        issueCommentRepository.deleteByIssueId(issueId);
        issueRepository.delete(issue);
    }
}
