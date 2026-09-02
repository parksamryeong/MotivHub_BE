package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskComment;
import java.time.LocalDateTime;

public record TaskCommentResponse(Long id, Long authorId, String content, LocalDateTime createdAt) {
    public static TaskCommentResponse from(TaskComment comment) {
        return new TaskCommentResponse(
                comment.getId(), comment.getAuthor().getId(), comment.getContent(), comment.getCreatedAt());
    }
}
