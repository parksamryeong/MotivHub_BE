package com.motivhub.be.task.controller;

import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.task.dto.TaskCommentCreateRequest;
import com.motivhub.be.task.dto.TaskCommentPromoteToIssueRequest;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.dto.TaskCommentUpdateRequest;
import com.motivhub.be.task.service.TaskCommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @PatchMapping("/api/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<TaskCommentResponse> update(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId, @PathVariable Long commentId,
            @Valid @RequestBody TaskCommentUpdateRequest request) {
        return ResponseEntity.ok(taskCommentService.update(userId, taskId, commentId, request.content()));
    }

    @DeleteMapping("/api/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId, @PathVariable Long commentId) {
        taskCommentService.delete(userId, taskId, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tasks/{taskId}/comments/{commentId}/promote-to-issue")
    public ResponseEntity<IssueResponse> promoteToIssue(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId, @PathVariable Long commentId,
            @Valid @RequestBody TaskCommentPromoteToIssueRequest request) {
        return ResponseEntity.ok(taskCommentService.promoteToIssue(userId, taskId, commentId, request.title()));
    }
}
