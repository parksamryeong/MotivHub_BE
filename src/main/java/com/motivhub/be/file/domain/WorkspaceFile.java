package com.motivhub.be.file.domain;

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
@Table(name = "workspace_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "file_key", nullable = false, unique = true, length = 500)
    private String fileKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(length = 50)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private WorkspaceFile(Workspace workspace, String fileKey, String fileName, long fileSize,
                           String contentType, String category, User uploadedBy) {
        this.workspace = workspace;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.category = category;
        this.uploadedBy = uploadedBy;
        this.createdAt = LocalDateTime.now();
    }

    public static WorkspaceFile create(Workspace workspace, String fileKey, String fileName, long fileSize,
                                        String contentType, String category, User uploadedBy) {
        return new WorkspaceFile(workspace, fileKey, fileName, fileSize, contentType, category, uploadedBy);
    }

    public boolean isUploadedBy(Long userId) {
        return this.uploadedBy.getId().equals(userId);
    }

    public void updateCategory(String category) {
        this.category = category;
    }
}
