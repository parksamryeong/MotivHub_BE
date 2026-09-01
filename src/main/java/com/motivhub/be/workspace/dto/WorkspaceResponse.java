package com.motivhub.be.workspace.dto;

import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import java.time.LocalDateTime;

public record WorkspaceResponse(
        Long id,
        String name,
        WorkspaceRole myRole,
        LocalDateTime createdAt) {

    public static WorkspaceResponse of(Workspace workspace, WorkspaceRole myRole) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), myRole, workspace.getCreatedAt());
    }
}
