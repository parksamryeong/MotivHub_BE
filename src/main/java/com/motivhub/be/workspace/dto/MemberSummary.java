package com.motivhub.be.workspace.dto;

import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import java.time.LocalDateTime;

public record MemberSummary(UserSummary user, WorkspaceRole role, LocalDateTime joinedAt) {
    public static MemberSummary from(WorkspaceMember member) {
        return new MemberSummary(UserSummary.from(member.getUser()), member.getRole(), member.getJoinedAt());
    }
}
