CREATE TABLE task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NULL,
    start_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_task_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_task_created_by FOREIGN KEY (created_by) REFERENCES user(id)
);

CREATE TABLE task_assignee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_task_assignee_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_assignee_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT uk_task_assignee_task_user UNIQUE (task_id, user_id)
);

CREATE TABLE task_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_task_comment_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_comment_author FOREIGN KEY (author_id) REFERENCES user(id)
);
