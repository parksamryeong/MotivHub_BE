package com.motivhub.be.workspace.domain;

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
@Table(name = "workspace_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(length = 255)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    private WorkspaceInvite(Workspace workspace, String token, String email, LocalDateTime expiresAt) {
        this.workspace = workspace;
        this.token = token;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public static WorkspaceInvite create(Workspace workspace, String token, String email, LocalDateTime expiresAt) {
        return new WorkspaceInvite(workspace, token, email, expiresAt);
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }
}
