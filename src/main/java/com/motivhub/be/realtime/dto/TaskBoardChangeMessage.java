package com.motivhub.be.realtime.dto;

import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.event.TaskChangeType;

public record TaskBoardChangeMessage(TaskChangeType changeType, Long taskId, TaskResponse task) {
}
