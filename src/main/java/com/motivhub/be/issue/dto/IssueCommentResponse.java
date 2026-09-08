package com.motivhub.be.issue.dto;

import com.motivhub.be.issue.domain.IssueComment;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record IssueCommentResponse(Long id, UserSummary author, String content, LocalDateTime createdAt) {
    public static IssueCommentResponse from(IssueComment comment) {
        return new IssueCommentResponse(
                comment.getId(), UserSummary.from(comment.getAuthor()), comment.getContent(), comment.getCreatedAt());
    }
}
