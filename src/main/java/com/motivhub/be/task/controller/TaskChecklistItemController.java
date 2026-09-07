package com.motivhub.be.task.controller;

import com.motivhub.be.task.dto.TaskChecklistItemCreateRequest;
import com.motivhub.be.task.dto.TaskChecklistItemResponse;
import com.motivhub.be.task.dto.TaskChecklistItemUpdateRequest;
import com.motivhub.be.task.service.TaskChecklistItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskChecklistItemController {

    private final TaskChecklistItemService taskChecklistItemService;

    public TaskChecklistItemController(TaskChecklistItemService taskChecklistItemService) {
        this.taskChecklistItemService = taskChecklistItemService;
    }

    @PostMapping("/api/tasks/{taskId}/checklist-items")
    public ResponseEntity<TaskChecklistItemResponse> create(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId,
            @Valid @RequestBody TaskChecklistItemCreateRequest request) {
        return ResponseEntity.ok(taskChecklistItemService.create(userId, taskId, request.content()));
    }

    @PatchMapping("/api/tasks/{taskId}/checklist-items/{itemId}")
    public ResponseEntity<TaskChecklistItemResponse> update(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId, @PathVariable Long itemId,
            @Valid @RequestBody TaskChecklistItemUpdateRequest request) {
        return ResponseEntity.ok(
                taskChecklistItemService.update(userId, taskId, itemId, request.content(), request.isDone()));
    }

    @DeleteMapping("/api/tasks/{taskId}/checklist-items/{itemId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long taskId, @PathVariable Long itemId) {
        taskChecklistItemService.delete(userId, taskId, itemId);
        return ResponseEntity.noContent().build();
    }
}
