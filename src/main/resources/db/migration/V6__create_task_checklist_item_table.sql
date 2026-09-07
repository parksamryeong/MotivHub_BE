CREATE TABLE task_checklist_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    content VARCHAR(200) NOT NULL,
    is_done BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_task_checklist_item_task FOREIGN KEY (task_id) REFERENCES task(id)
);
