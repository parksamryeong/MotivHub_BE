package com.motivhub.be.workspace.controller;

import com.motivhub.be.workspace.dto.WorkspaceInviteCreateRequest;
import com.motivhub.be.workspace.dto.WorkspaceInviteResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceInviteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkspaceInviteController {

    private final WorkspaceInviteService workspaceInviteService;

    public WorkspaceInviteController(WorkspaceInviteService workspaceInviteService) {
        this.workspaceInviteService = workspaceInviteService;
    }

    @PostMapping("/api/workspaces/{id}/invites")
    public ResponseEntity<WorkspaceInviteResponse> createInvite(
            @AuthenticationPrincipal Long userId, @PathVariable Long id,
            @Valid @RequestBody WorkspaceInviteCreateRequest request) {
        return ResponseEntity.ok(workspaceInviteService.createInvite(userId, id, request.email()));
    }

    @GetMapping("/api/workspaces/{id}/invites")
    public ResponseEntity<List<WorkspaceInviteResponse>> listInvites(
            @AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(workspaceInviteService.listInvites(userId, id));
    }

    @DeleteMapping("/api/workspaces/{id}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @AuthenticationPrincipal Long userId, @PathVariable Long id, @PathVariable Long inviteId) {
        workspaceInviteService.revokeInvite(userId, id, inviteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/invites/{token}/accept")
    public ResponseEntity<WorkspaceResponse> accept(
            @AuthenticationPrincipal Long userId, @PathVariable String token) {
        return ResponseEntity.ok(workspaceInviteService.accept(userId, token));
    }
}
