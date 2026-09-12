CREATE TABLE task_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    content LONGTEXT,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_task_note_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_note_updated_by FOREIGN KEY (updated_by) REFERENCES user(id),
    CONSTRAINT uk_task_note_task_id UNIQUE (task_id)
);
