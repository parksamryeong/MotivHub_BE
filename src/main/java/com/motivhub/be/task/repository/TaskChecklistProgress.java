package com.motivhub.be.task.repository;

public record TaskChecklistProgress(Long taskId, Long total, Long completed) {
}
