CREATE TABLE workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL
);

CREATE TABLE workspace_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at DATETIME NOT NULL,
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_workspace_member_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT uk_workspace_member_workspace_user UNIQUE (workspace_id, user_id)
);
