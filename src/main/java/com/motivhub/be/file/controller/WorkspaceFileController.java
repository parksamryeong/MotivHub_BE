package com.motivhub.be.file.controller;

import com.motivhub.be.file.dto.FilePresignRequest;
import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.dto.WorkspaceFileConfirmRequest;
import com.motivhub.be.file.dto.WorkspaceFileResponse;
import com.motivhub.be.file.service.WorkspaceFileService;
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
public class WorkspaceFileController {

    private final WorkspaceFileService workspaceFileService;

    public WorkspaceFileController(WorkspaceFileService workspaceFileService) {
        this.workspaceFileService = workspaceFileService;
    }

    @PostMapping("/api/workspaces/{workspaceId}/files/presign")
    public ResponseEntity<FilePresignResponse> presign(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId,
            @Valid @RequestBody FilePresignRequest request) {
        return ResponseEntity.ok(workspaceFileService.presign(
                userId, workspaceId, request.fileName(), request.contentType(), request.fileSize()));
    }

    @PostMapping("/api/workspaces/{workspaceId}/files")
    public ResponseEntity<WorkspaceFileResponse> confirm(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceFileConfirmRequest request) {
        return ResponseEntity.ok(workspaceFileService.confirm(
                userId, workspaceId, request.fileKey(), request.fileName(), request.fileSize(),
                request.contentType()));
    }

    @GetMapping("/api/workspaces/{workspaceId}/files")
    public ResponseEntity<List<WorkspaceFileResponse>> list(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceFileService.list(userId, workspaceId));
    }
}
