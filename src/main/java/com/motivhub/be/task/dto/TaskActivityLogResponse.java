package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.domain.TaskActivityLog;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record TaskActivityLogResponse(
        Long id, UserSummary actor, TaskActivityAction action,
        String field, String oldValue, String newValue, LocalDateTime createdAt) {

    public static TaskActivityLogResponse from(TaskActivityLog log) {
        return new TaskActivityLogResponse(
                log.getId(), UserSummary.from(log.getActor()), log.getAction(),
                log.getField(), log.getOldValue(), log.getNewValue(), log.getCreatedAt());
    }
}
