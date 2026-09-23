CREATE TABLE notification_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_notification_setting_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT uk_notification_setting_user_type UNIQUE (user_id, type)
);
