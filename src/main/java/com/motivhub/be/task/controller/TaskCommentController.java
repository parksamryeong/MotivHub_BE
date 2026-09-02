package com.motivhub.be.task.controller;

import com.motivhub.be.task.dto.TaskCommentCreateRequest;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.service.TaskCommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskCommentController {

    private final TaskCommentService taskCommentService;

    public TaskCommentController(TaskCommentService taskCommentService) {
        this.taskCommentService = taskCommentService;
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<TaskCommentResponse> create(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId,
            @Valid @RequestBody TaskCommentCreateRequest request) {
        return ResponseEntity.ok(taskCommentService.create(userId, taskId, request.content()));
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<List<TaskCommentResponse>> list(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId) {
        return ResponseEntity.ok(taskCommentService.list(userId, taskId));
    }
}
