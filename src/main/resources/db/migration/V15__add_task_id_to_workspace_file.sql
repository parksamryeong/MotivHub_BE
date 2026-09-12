ALTER TABLE workspace_file ADD COLUMN task_id BIGINT NULL;
ALTER TABLE workspace_file ADD CONSTRAINT fk_workspace_file_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE SET NULL;
