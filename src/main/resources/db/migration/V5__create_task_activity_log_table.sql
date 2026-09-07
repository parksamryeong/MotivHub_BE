CREATE TABLE task_activity_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    field VARCHAR(30) NULL,
    old_value VARCHAR(500) NULL,
    new_value VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_task_activity_log_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_activity_log_actor FOREIGN KEY (actor_id) REFERENCES user(id)
);
