package com.motivhub.be.issue.service;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.exception.IssueForbiddenException;
import com.motivhub.be.issue.exception.IssueNotFoundException;
import com.motivhub.be.issue.repository.IssueCommentRepository;
import com.motivhub.be.issue.repository.IssueRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
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

    public List<IssueResponse> list() {
        return issueRepository.findAllOrderByCreatedAtDesc().stream()
                .map(IssueResponse::from)
                .toList();
    }

    public IssueResponse getDetail(Long issueId) {
        Issue issue = issueRepository.findByIdFetchAuthorAndWorkspace(issueId)
                .orElseThrow(() -> new IssueNotFoundException("이슈를 찾을 수 없습니다."));
        return IssueResponse.from(issue);
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
        return IssueResponse.from(issue);
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
