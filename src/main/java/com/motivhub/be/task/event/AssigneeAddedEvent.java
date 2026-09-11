package com.motivhub.be.task.event;

public record AssigneeAddedEvent(Long taskId, Long newAssigneeUserId) {
}
