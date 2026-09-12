package com.motivhub.be.task.dto;

import com.motivhub.be.task.domain.TaskNote;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record TaskNoteResponse(Long taskId, String content, UserSummary updatedBy, LocalDateTime updatedAt) {

    public static TaskNoteResponse from(TaskNote note) {
        return new TaskNoteResponse(note.getTask().getId(), note.getContent(),
                UserSummary.from(note.getUpdatedBy()), note.getUpdatedAt());
    }

    public static TaskNoteResponse empty(Long taskId) {
        return new TaskNoteResponse(taskId, null, null, null);
    }
}
