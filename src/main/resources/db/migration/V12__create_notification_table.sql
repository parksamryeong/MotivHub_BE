CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    read_at DATETIME NULL,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES user(id),
    INDEX idx_notification_recipient_read_created (recipient_id, is_read, created_at)
);
