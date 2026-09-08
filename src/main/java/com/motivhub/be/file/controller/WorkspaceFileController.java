package com.motivhub.be.file.controller;

import com.motivhub.be.file.dto.FilePresignRequest;
import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.service.WorkspaceFileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}
