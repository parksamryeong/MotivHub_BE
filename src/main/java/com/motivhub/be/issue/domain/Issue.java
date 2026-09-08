package com.motivhub.be.issue.domain;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.domain.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "problem_description", nullable = false, length = 2000)
    private String problemDescription;

    @Column(length = 2000)
    private String solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Issue(Workspace workspace, String title, String problemDescription, String solution, User author) {
        this.workspace = workspace;
        this.title = title;
        this.problemDescription = problemDescription;
        this.solution = normalizeSolution(solution);
        this.author = author;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Issue create(Workspace workspace, String title, String problemDescription, String solution,
                                User author) {
        return new Issue(workspace, title, problemDescription, solution, author);
    }

    public void update(String title, String problemDescription, String solution) {
        if (title != null) {
            this.title = title;
        }
        if (problemDescription != null) {
            this.problemDescription = problemDescription;
        }
        if (solution != null) {
            this.solution = normalizeSolution(solution);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAuthoredBy(Long userId) {
        return this.author.getId().equals(userId);
    }

    private static String normalizeSolution(String solution) {
        return (solution == null || solution.isBlank()) ? null : solution;
    }
}
