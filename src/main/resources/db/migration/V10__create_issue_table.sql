CREATE TABLE issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    problem_description VARCHAR(2000) NOT NULL,
    solution VARCHAR(2000) NULL,
    author_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_issue_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_issue_author FOREIGN KEY (author_id) REFERENCES user(id)
);
