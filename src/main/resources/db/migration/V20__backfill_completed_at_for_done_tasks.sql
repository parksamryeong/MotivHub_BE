UPDATE task SET completed_at = created_at WHERE status = 'DONE' AND completed_at IS NULL;
