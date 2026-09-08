package com.motivhub.be.issue.dto;

import com.motivhub.be.issue.domain.Issue;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record IssueResponse(
        Long id, Long workspaceId, String workspaceName, String title, String problemDescription,
        String solution, UserSummary author, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static IssueResponse from(Issue issue) {
        return new IssueResponse(
                issue.getId(), issue.getWorkspace().getId(), issue.getWorkspace().getName(), issue.getTitle(),
                issue.getProblemDescription(), issue.getSolution(), UserSummary.from(issue.getAuthor()),
                issue.getCreatedAt(), issue.getUpdatedAt());
    }
}
