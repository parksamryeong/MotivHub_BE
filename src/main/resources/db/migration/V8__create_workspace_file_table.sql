CREATE TABLE workspace_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    file_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_workspace_file_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_workspace_file_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES user(id),
    CONSTRAINT uk_workspace_file_file_key UNIQUE (file_key)
);
