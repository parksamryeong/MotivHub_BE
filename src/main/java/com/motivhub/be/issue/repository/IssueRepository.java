package com.motivhub.be.issue.repository;

import com.motivhub.be.issue.domain.Issue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace "
            + "WHERE i.workspace.deletedAt IS NULL ORDER BY i.createdAt DESC, i.id DESC")
    List<Issue> findAllOrderByCreatedAtDesc();

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace "
            + "WHERE i.id = :id AND i.workspace.deletedAt IS NULL")
    Optional<Issue> findByIdFetchAuthorAndWorkspace(@Param("id") Long id);

    // JPQL은 MySQL의 MATCH()/AGAINST()를 표현할 수 없어 네이티브 쿼리로 작성한다. 이 쿼리는
    // "관련도순으로 정렬된 ID 목록"만 가져온다 - author/workspace까지 한 번에 당겨오는 건
    // findByIdInFetchAuthorAndWorkspace()가 담당한다(네이티브 쿼리는 JOIN FETCH 같은 엔티티
    // 그래프 기능을 쓸 수 없어 2단계로 나눔).
    @Query(value = "SELECT i.id FROM issue i "
            + "JOIN workspace w ON w.id = i.workspace_id "
            + "WHERE w.deleted_at IS NULL "
            + "AND MATCH(i.title, i.problem_description, i.solution) AGAINST (:keyword IN NATURAL LANGUAGE MODE) "
            + "ORDER BY MATCH(i.title, i.problem_description, i.solution) AGAINST (:keyword IN NATURAL LANGUAGE MODE) DESC, "
            + "i.created_at DESC, i.id DESC",
            nativeQuery = true)
    List<Long> searchIdsByKeywordOrderByRelevance(@Param("keyword") String keyword);

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace WHERE i.id IN :ids")
    List<Issue> findByIdInFetchAuthorAndWorkspace(@Param("ids") List<Long> ids);

    // "내가 작성한 이슈" 탭 전용 - author_id로 먼저 좁혀지는 조회라 전체 게시판과 달리 풀스캔
    // 위험이 없으므로 FULLTEXT 대신 단순 LIKE로 충분하다. ASC/DESC를 JPQL 하나로 파라미터화할
    // 수 없어 정렬 방향별로 메서드를 나눈다(이 레포의 다른 쿼리들과 같은 스타일).
    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace w "
            + "WHERE i.author.id = :authorId AND w.deletedAt IS NULL "
            + "AND (:workspaceId IS NULL OR w.id = :workspaceId) "
            + "AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(i.problemDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(i.solution) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY i.createdAt DESC, i.id DESC")
    List<Issue> findMineOrderByCreatedAtDesc(
            @Param("authorId") Long authorId, @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword);

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace w "
            + "WHERE i.author.id = :authorId AND w.deletedAt IS NULL "
            + "AND (:workspaceId IS NULL OR w.id = :workspaceId) "
            + "AND (:keyword IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(i.problemDescription) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(i.solution) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY i.createdAt ASC, i.id ASC")
    List<Issue> findMineOrderByCreatedAtAsc(
            @Param("authorId") Long authorId, @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword);
}
