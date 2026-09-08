package com.motivhub.be.file.repository;

import com.motivhub.be.file.domain.WorkspaceFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFile, Long> {

    @Query("SELECT wf FROM WorkspaceFile wf JOIN FETCH wf.uploadedBy WHERE wf.workspace.id = :workspaceId "
            + "ORDER BY wf.createdAt DESC, wf.id DESC")
    List<WorkspaceFile> findByWorkspaceIdOrderByCreatedAtDesc(@Param("workspaceId") Long workspaceId);

    boolean existsByFileKey(String fileKey);
}
