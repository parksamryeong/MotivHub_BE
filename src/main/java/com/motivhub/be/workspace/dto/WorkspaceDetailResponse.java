package com.motivhub.be.workspace.dto;

import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import java.time.LocalDateTime;
import java.util.List;

public record WorkspaceDetailResponse(
        Long id, String name, WorkspaceRole myRole, LocalDateTime createdAt, List<MemberSummary> members) {

    public static WorkspaceDetailResponse of(Workspace workspace, WorkspaceRole myRole, List<WorkspaceMember> members) {
        return new WorkspaceDetailResponse(
                workspace.getId(), workspace.getName(), myRole, workspace.getCreatedAt(),
                members.stream().map(MemberSummary::from).toList());
    }
}
