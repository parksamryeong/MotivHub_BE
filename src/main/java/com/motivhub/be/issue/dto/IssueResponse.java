package com.motivhub.be.issue.dto;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record IssueResponse(
        Long id, Long workspaceId, String workspaceName, String title, String problemDescription,
        String solution, UserSummary author, LocalDateTime createdAt, LocalDateTime updatedAt, long commentCount) {

    /**
     * 방금 생성된 이슈처럼 댓글 개수가 항상 0으로 확정된 경우 전용 — 그 외에는 아래
     * {@link #from(Issue, long)}로 실제 개수를 넘길 것.
     */
    public static IssueResponse from(Issue issue) {
        return from(issue, 0);
    }

    public static IssueResponse from(Issue issue, long commentCount) {
        return new IssueResponse(
                issue.getId(), issue.getWorkspace().getId(), issue.getWorkspace().getName(), issue.getTitle(),
                issue.getProblemDescription(), issue.getSolution(), UserSummary.from(issue.getAuthor()),
                issue.getCreatedAt(), issue.getUpdatedAt(), commentCount);
    }
}
