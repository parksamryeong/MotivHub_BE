CREATE TABLE workspace_invite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    token VARCHAR(36) NOT NULL,
    email VARCHAR(255) NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    CONSTRAINT fk_workspace_invite_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT uk_workspace_invite_token UNIQUE (token)
);
