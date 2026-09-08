package com.motivhub.be.issue.service;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.exception.IssueNotFoundException;
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

    public IssueService(IssueRepository issueRepository, WorkspaceService workspaceService,
                         UserRepository userRepository) {
        this.issueRepository = issueRepository;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
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
}
