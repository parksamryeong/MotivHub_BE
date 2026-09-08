package com.motivhub.be.issue.repository;

import com.motivhub.be.issue.domain.Issue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace ORDER BY i.createdAt DESC, i.id DESC")
    List<Issue> findAllOrderByCreatedAtDesc();

    @Query("SELECT i FROM Issue i JOIN FETCH i.author JOIN FETCH i.workspace WHERE i.id = :id")
    Optional<Issue> findByIdFetchAuthorAndWorkspace(@Param("id") Long id);
}
