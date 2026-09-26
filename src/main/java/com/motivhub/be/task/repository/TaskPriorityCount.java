package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskPriority;

public record TaskPriorityCount(Long workspaceId, TaskPriority priority, Long count) {
}
