package com.motivhub.be.workspace.repository;

import com.motivhub.be.workspace.domain.WorkspaceMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    List<WorkspaceMember> findByWorkspaceId(Long workspaceId);
    long countByWorkspaceId(Long workspaceId);

    @Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.workspace WHERE wm.user.id = :userId")
    List<WorkspaceMember> findByUserIdFetchWorkspace(@Param("userId") Long userId);
}
