package com.motivhub.be.file.repository;

import com.motivhub.be.file.domain.WorkspaceFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFile, Long> {

    @Query("SELECT wf FROM WorkspaceFile wf JOIN FETCH wf.uploadedBy WHERE wf.workspace.id = :workspaceId "
            + "AND wf.task IS NULL ORDER BY wf.createdAt DESC, wf.id DESC")
    List<WorkspaceFile> findByWorkspaceIdAndTaskIsNullOrderByCreatedAtDesc(@Param("workspaceId") Long workspaceId);

    @Query("SELECT wf FROM WorkspaceFile wf JOIN FETCH wf.uploadedBy WHERE wf.task.id = :taskId "
            + "ORDER BY wf.createdAt DESC, wf.id DESC")
    List<WorkspaceFile> findByTaskIdOrderByCreatedAtDesc(@Param("taskId") Long taskId);

    // 태스크 삭제 시 TaskService.delete()에서 명시적으로 호출 - DB의 ON DELETE SET NULL만 믿으면
    // 같은 트랜잭션 안에 이미 로딩된 WorkspaceFile 엔티티가 있을 때 Hibernate가
    // TransientPropertyValueException을 던진다(ORM이 DB 레벨 캐스케이드를 모르기 때문). 벌크 업데이트로
    // 명시적으로 끊어준다. flushAutomatically 없이 clearAutomatically만 켜면, 같은 트랜잭션에서 이
    // 호출보다 먼저 실행된 다른 deleteByTaskId(댓글/체크리스트/활동로그)들이 아직 플러시되지 않은 채
    // 영속성 컨텍스트에만 예약돼 있다가 clear()로 통째로 유실되는 버그가 생긴다(직접 겪음) - 반드시 둘 다
    // 켜서, 먼저 대기 중인 변경을 DB에 반영한 뒤에 이 벌크 UPDATE를 실행하고, 그 다음 컨텍스트를 비운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WorkspaceFile wf SET wf.task = null WHERE wf.task.id = :taskId")
    void clearTaskId(@Param("taskId") Long taskId);

    boolean existsByFileKey(String fileKey);
}
