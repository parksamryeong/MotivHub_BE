package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.TaskAssignee;
import com.motivhub.be.task.domain.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, Long> {

    @Query("SELECT ta FROM TaskAssignee ta JOIN FETCH ta.user WHERE ta.task.id = :taskId")
    List<TaskAssignee> findByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT ta FROM TaskAssignee ta JOIN FETCH ta.user WHERE ta.task.id IN :taskIds")
    List<TaskAssignee> findByTaskIdIn(@Param("taskIds") List<Long> taskIds);

    Optional<TaskAssignee> findByTaskIdAndUserId(Long taskId, Long userId);
    boolean existsByTaskIdAndUserId(Long taskId, Long userId);
    void deleteByTaskId(Long taskId);

    @Query("SELECT new com.motivhub.be.task.repository.MemberWorkloadCount("
            + "ta.user.id, ta.user.nickname, ta.user.profileImageUrl, COUNT(ta)) "
            + "FROM TaskAssignee ta "
            + "WHERE ta.task.workspace.id IN :workspaceIds AND ta.task.status IN :statuses "
            + "GROUP BY ta.user.id, ta.user.nickname, ta.user.profileImageUrl")
    List<MemberWorkloadCount> countByWorkspaceIdsAndTaskStatusInGroupByUser(
            @Param("workspaceIds") List<Long> workspaceIds, @Param("statuses") List<TaskStatus> statuses);
}
