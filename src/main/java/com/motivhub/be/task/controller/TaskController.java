package com.motivhub.be.task.controller;

import com.motivhub.be.task.dto.TaskAssigneeRequest;
import com.motivhub.be.task.dto.TaskContentUpdateRequest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskPeriodUpdateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.dto.TaskStatusUpdateRequest;
import com.motivhub.be.task.service.TaskService;
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
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/workspaces/{workspaceId}/tasks")
    public ResponseEntity<TaskResponse> create(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId,
            @Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(taskService.create(userId, workspaceId, request));
    }

    @GetMapping("/api/workspaces/{workspaceId}/tasks")
    public ResponseEntity<List<TaskResponse>> listByWorkspace(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(taskService.listByWorkspace(userId, workspaceId));
    }

    @GetMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> getDetail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.getDetail(userId, id));
    }

    @PatchMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> updateContent(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody TaskContentUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateContent(userId, id, request.name(), request.description()));
    }

    @PatchMapping("/api/tasks/{id}/period")
    public ResponseEntity<TaskResponse> updatePeriod(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody TaskPeriodUpdateRequest request) {
        return ResponseEntity.ok(taskService.updatePeriod(userId, id, request.startDate(), request.dueDate()));
    }

    @PatchMapping("/api/tasks/{id}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskService.changeStatus(userId, id, request.status()));
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        taskService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/tasks/{id}/assignees")
    public ResponseEntity<TaskResponse> addAssignee(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody TaskAssigneeRequest request) {
        return ResponseEntity.ok(taskService.addAssignee(userId, id, request.userId()));
    }

    @DeleteMapping("/api/tasks/{id}/assignees/{targetUserId}")
    public ResponseEntity<TaskResponse> removeAssignee(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long targetUserId) {
        return ResponseEntity.ok(taskService.removeAssignee(userId, id, targetUserId));
    }
}
