package com.motivhub.be.task.domain;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.domain.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Task(Workspace workspace, String name, String description,
                  LocalDate startDate, LocalDate dueDate, User createdBy) {
        this.workspace = workspace;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.status = TaskStatus.WAITING;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public static Task create(Workspace workspace, String name, String description,
                               LocalDate startDate, LocalDate dueDate, User createdBy) {
        return new Task(workspace, name, description, startDate, dueDate, createdBy);
    }

    public void updateContent(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void updatePeriod(LocalDate startDate, LocalDate dueDate) {
        this.startDate = startDate;
        this.dueDate = dueDate;
        if (this.status == TaskStatus.EXPIRED && !dueDate.isBefore(LocalDate.now())) {
            this.status = TaskStatus.WAITING;
        }
    }

    public void changeStatus(TaskStatus newStatus) {
        this.status = newStatus;
    }

    public void expire() {
        this.status = TaskStatus.EXPIRED;
    }

    public boolean isCreatedBy(Long userId) {
        return this.createdBy.getId().equals(userId);
    }
}
