package com.motivhub.be.issue.repository;

import com.motivhub.be.issue.domain.IssueComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    @Query("SELECT ic FROM IssueComment ic JOIN FETCH ic.author WHERE ic.issue.id = :issueId "
            + "ORDER BY ic.createdAt ASC, ic.id ASC")
    List<IssueComment> findByIssueIdOrderByCreatedAtAsc(@Param("issueId") Long issueId);

    void deleteByIssueId(Long issueId);
}
