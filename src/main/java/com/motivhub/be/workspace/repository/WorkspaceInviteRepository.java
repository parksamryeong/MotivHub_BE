package com.motivhub.be.workspace.repository;

import com.motivhub.be.workspace.domain.WorkspaceInvite;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    Optional<WorkspaceInvite> findByToken(String token);
    List<WorkspaceInvite> findByWorkspaceIdAndRevokedAtIsNullAndExpiresAtAfter(Long workspaceId, LocalDateTime now);
}
