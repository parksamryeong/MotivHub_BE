package com.motivhub.be.file.controller;

import com.motivhub.be.file.dto.FileDownloadResponse;
import com.motivhub.be.file.dto.FilePresignRequest;
import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.dto.WorkspaceFileCategoryUpdateRequest;
import com.motivhub.be.file.dto.WorkspaceFileConfirmRequest;
import com.motivhub.be.file.dto.WorkspaceFileResponse;
import com.motivhub.be.file.service.WorkspaceFileService;
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
                request.contentType(), request.category()));
    }

    @GetMapping("/api/workspaces/{workspaceId}/files")
    public ResponseEntity<List<WorkspaceFileResponse>> list(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceFileService.list(userId, workspaceId));
    }

    @GetMapping("/api/workspaces/{workspaceId}/files/{fileId}/download")
    public ResponseEntity<FileDownloadResponse> download(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId, @PathVariable Long fileId) {
        return ResponseEntity.ok(workspaceFileService.getDownloadUrl(userId, workspaceId, fileId));
    }

    @PatchMapping("/api/workspaces/{workspaceId}/files/{fileId}")
    public ResponseEntity<WorkspaceFileResponse> updateCategory(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId, @PathVariable Long fileId,
            @Valid @RequestBody WorkspaceFileCategoryUpdateRequest request) {
        return ResponseEntity.ok(workspaceFileService.updateCategory(userId, workspaceId, fileId, request.category()));
    }

    @DeleteMapping("/api/workspaces/{workspaceId}/files/{fileId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Long userId, @PathVariable Long workspaceId, @PathVariable Long fileId) {
        workspaceFileService.delete(userId, workspaceId, fileId);
        return ResponseEntity.noContent().build();
    }
}
