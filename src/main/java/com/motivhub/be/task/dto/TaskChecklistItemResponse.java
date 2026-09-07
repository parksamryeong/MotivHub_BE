package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskChecklistItem;
import java.time.LocalDateTime;

public record TaskChecklistItemResponse(
        Long id, String content, boolean isDone, int orderIndex, LocalDateTime createdAt) {

    public static TaskChecklistItemResponse from(TaskChecklistItem item) {
        return new TaskChecklistItemResponse(
                item.getId(), item.getContent(), item.isDone(), item.getOrderIndex(), item.getCreatedAt());
    }
}
