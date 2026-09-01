package com.motivhub.be.workspace.dto;

import com.motivhub.be.workspace.domain.WorkspaceInvite;
import java.time.LocalDateTime;

public record WorkspaceInviteResponse(
        Long id,
        String token,
        String email,
        LocalDateTime expiresAt) {

    public static WorkspaceInviteResponse from(WorkspaceInvite invite) {
        return new WorkspaceInviteResponse(invite.getId(), invite.getToken(), invite.getEmail(), invite.getExpiresAt());
    }
}
