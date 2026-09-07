package com.motivhub.be.task.domain;

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
@Table(name = "task_checklist_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "is_done", nullable = false)
    private boolean done;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private TaskChecklistItem(Task task, String content, int orderIndex) {
        this.task = task;
        this.content = content;
        this.done = false;
        this.orderIndex = orderIndex;
        this.createdAt = LocalDateTime.now();
    }

    public static TaskChecklistItem create(Task task, String content, int orderIndex) {
        return new TaskChecklistItem(task, content, orderIndex);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void markDone(boolean done) {
        this.done = done;
    }
}
