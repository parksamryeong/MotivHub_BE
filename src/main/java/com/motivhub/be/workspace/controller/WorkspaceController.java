package com.motivhub.be.workspace.controller;

import com.motivhub.be.workspace.dto.TransferOwnershipRequest;
import com.motivhub.be.workspace.dto.WorkspaceCreateRequest;
import com.motivhub.be.workspace.dto.WorkspaceDetailResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.dto.WorkspaceUpdateRequest;
import com.motivhub.be.workspace.service.WorkspaceService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.ok(workspaceService.create(userId, request.name()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> updateName(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody WorkspaceUpdateRequest request) {
        return ResponseEntity.ok(workspaceService.updateName(userId, id, request.name()));
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> listMine(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(workspaceService.listMine(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(workspaceService.getDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        workspaceService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        workspaceService.leave(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody TransferOwnershipRequest request) {
        workspaceService.transferOwnership(userId, id, request.newOwnerUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    public ResponseEntity<Void> kick(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long targetUserId) {
        workspaceService.kick(userId, id, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
