package com.motivhub.be.issue.service;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.issue.domain.IssueComment;
import com.motivhub.be.issue.dto.IssueCommentResponse;
import com.motivhub.be.issue.exception.IssueCommentForbiddenException;
import com.motivhub.be.issue.exception.IssueCommentNotFoundException;
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

    @Transactional
    public IssueCommentResponse update(Long userId, Long issueId, Long commentId, String content) {
        IssueComment comment = findComment(issueId, commentId);
        if (!comment.isAuthoredBy(userId)) {
            throw new IssueCommentForbiddenException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }
        comment.updateContent(content);
        return IssueCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long userId, Long issueId, Long commentId) {
        IssueComment comment = findComment(issueId, commentId);
        if (!comment.isAuthoredBy(userId)) {
            throw new IssueCommentForbiddenException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
        issueCommentRepository.delete(comment);
    }

    private IssueComment findComment(Long issueId, Long commentId) {
        return issueCommentRepository.findById(commentId)
                .filter(comment -> comment.getIssue().getId().equals(issueId))
                .orElseThrow(() -> new IssueCommentNotFoundException("댓글을 찾을 수 없습니다."));
    }
}
