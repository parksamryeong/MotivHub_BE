ALTER TABLE notification ADD INDEX idx_notification_recipient_created (recipient_id, created_at);
ALTER TABLE notification ADD INDEX idx_notification_recipient_read (recipient_id, is_read);
ALTER TABLE notification DROP INDEX idx_notification_recipient_read_created;
