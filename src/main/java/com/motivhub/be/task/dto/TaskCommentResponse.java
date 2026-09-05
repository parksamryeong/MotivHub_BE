package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskComment;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record TaskCommentResponse(Long id, UserSummary author, String content, LocalDateTime createdAt) {
    public static TaskCommentResponse from(TaskComment comment) {
        return new TaskCommentResponse(
                comment.getId(), UserSummary.from(comment.getAuthor()), comment.getContent(), comment.getCreatedAt());
    }
}
