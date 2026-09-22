package com.motivhub.be.task.repository;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t JOIN FETCH t.createdBy WHERE t.workspace.id = :workspaceId")
    List<Task> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    List<Task> findByStatusInAndDueDateBefore(List<TaskStatus> statuses, LocalDate date);

    List<Task> findByStatusInAndDueDate(List<TaskStatus> statuses, LocalDate dueDate);

    @Query("SELECT new com.motivhub.be.task.repository.TaskStatusCount(t.workspace.id, t.status, COUNT(t)) "
            + "FROM Task t WHERE t.workspace.id IN :workspaceIds GROUP BY t.workspace.id, t.status")
    List<TaskStatusCount> countByWorkspaceIdsGroupByStatus(@Param("workspaceIds") List<Long> workspaceIds);

    @Query("SELECT t FROM Task t JOIN FETCH t.workspace w "
            + "WHERE t.status <> :excludedStatus AND w.deletedAt IS NULL "
            + "AND EXISTS (SELECT 1 FROM TaskAssignee ta WHERE ta.task = t AND ta.user.id = :userId) "
            + "AND EXISTS (SELECT 1 FROM WorkspaceMember wm WHERE wm.workspace = w AND wm.user.id = :userId) "
            + "AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY t.dueDate ASC, "
            + "CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END ASC, "
            + "t.id ASC")
    List<Task> findAssignedToUserExcludingStatus(
            @Param("userId") Long userId, @Param("excludedStatus") TaskStatus excludedStatus, @Param("q") String q);

    @Query("SELECT t FROM Task t JOIN FETCH t.workspace w "
            + "WHERE t.status = :status AND w.deletedAt IS NULL "
            + "AND EXISTS (SELECT 1 FROM TaskAssignee ta WHERE ta.task = t AND ta.user.id = :userId) "
            + "AND EXISTS (SELECT 1 FROM WorkspaceMember wm WHERE wm.workspace = w AND wm.user.id = :userId) "
            + "AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY t.dueDate ASC, "
            + "CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 END ASC, "
            + "t.id ASC")
    List<Task> findAssignedToUserByStatus(
            @Param("userId") Long userId, @Param("status") TaskStatus status, @Param("q") String q);

    @Query("SELECT t FROM Task t JOIN FETCH t.workspace w "
            + "WHERE t.status = 'DONE' AND w.deletedAt IS NULL "
            + "AND EXISTS (SELECT 1 FROM TaskAssignee ta WHERE ta.task = t AND ta.user.id = :userId) "
            + "AND EXISTS (SELECT 1 FROM WorkspaceMember wm WHERE wm.workspace = w AND wm.user.id = :userId) "
            + "AND (:q IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "ORDER BY t.completedAt DESC, t.id DESC")
    List<Task> findCompletedTasksForUser(@Param("userId") Long userId, @Param("q") String q, Pageable pageable);
}
