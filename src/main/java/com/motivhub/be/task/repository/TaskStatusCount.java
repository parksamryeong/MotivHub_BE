package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskStatus;

public record TaskStatusCount(Long workspaceId, TaskStatus status, Long count) {
}
