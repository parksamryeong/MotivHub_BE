ALTER TABLE task_comment ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
UPDATE task_comment SET updated_at = created_at;
