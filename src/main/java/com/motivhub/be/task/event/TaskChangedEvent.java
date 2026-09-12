package com.motivhub.be.task.event;

public record TaskChangedEvent(Long taskId, Long workspaceId, TaskChangeType changeType) {
}
