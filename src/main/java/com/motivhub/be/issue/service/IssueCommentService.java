package com.motivhub.be.issue.service;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.issue.domain.IssueComment;
import com.motivhub.be.issue.dto.IssueCommentResponse;
import com.motivhub.be.issue.repository.IssueCommentRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IssueCommentService {

    private final IssueCommentRepository issueCommentRepository;
    private final IssueService issueService;
    private final UserRepository userRepository;

    public IssueCommentService(IssueCommentRepository issueCommentRepository, IssueService issueService,
                                UserRepository userRepository) {
        this.issueCommentRepository = issueCommentRepository;
        this.issueService = issueService;
        this.userRepository = userRepository;
    }

    @Transactional
    public IssueCommentResponse create(Long userId, Long issueId, String content) {
        Issue issue = issueService.getIssue(issueId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        IssueComment comment = issueCommentRepository.save(IssueComment.create(issue, author, content));
        return IssueCommentResponse.from(comment);
    }

    public List<IssueCommentResponse> list(Long issueId) {
        issueService.getIssue(issueId);
        return issueCommentRepository.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
                .map(IssueCommentResponse::from)
                .toList();
    }
}
