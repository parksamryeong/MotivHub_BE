package com.motivhub.be.file.dto;

import com.motivhub.be.file.domain.WorkspaceFile;
import com.motivhub.be.user.dto.UserSummary;
import java.time.LocalDateTime;

public record WorkspaceFileResponse(
        Long id, String fileName, long fileSize, String contentType, UserSummary uploadedBy,
        LocalDateTime createdAt) {

    public static WorkspaceFileResponse from(WorkspaceFile file) {
        return new WorkspaceFileResponse(
                file.getId(), file.getFileName(), file.getFileSize(), file.getContentType(),
                UserSummary.from(file.getUploadedBy()), file.getCreatedAt());
    }
}
