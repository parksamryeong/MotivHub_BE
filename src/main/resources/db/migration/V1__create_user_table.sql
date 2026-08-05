CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    nickname VARCHAR(30) NOT NULL,
    nickname_configured BOOLEAN NOT NULL DEFAULT FALSE,
    profile_image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    CONSTRAINT uk_user_provider_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT uk_user_nickname UNIQUE (nickname)
);
