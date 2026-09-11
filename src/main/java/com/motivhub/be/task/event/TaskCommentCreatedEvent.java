package com.motivhub.be.task.event;

public record TaskCommentCreatedEvent(Long taskId, Long authorId, String authorNickname) {
}
