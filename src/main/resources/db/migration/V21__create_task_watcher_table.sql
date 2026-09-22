CREATE TABLE task_watcher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_task_watcher_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_watcher_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT uk_task_watcher_task_user UNIQUE (task_id, user_id)
);
