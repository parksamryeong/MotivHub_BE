package com.motivhub.be.task.controller;

import com.motivhub.be.task.dto.TaskNoteResponse;
import com.motivhub.be.task.dto.TaskNoteUpdateRequest;
import com.motivhub.be.task.service.TaskNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskNoteController {

    private final TaskNoteService taskNoteService;

    public TaskNoteController(TaskNoteService taskNoteService) {
        this.taskNoteService = taskNoteService;
    }

    @GetMapping("/api/tasks/{taskId}/note")
    public ResponseEntity<TaskNoteResponse> get(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskNoteService.get(userId, taskId));
    }

    @PatchMapping("/api/tasks/{taskId}/note")
    public ResponseEntity<TaskNoteResponse> upsert(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId,
            @Valid @RequestBody TaskNoteUpdateRequest request) {
        return ResponseEntity.ok(taskNoteService.upsert(userId, taskId, request.content()));
    }
}
