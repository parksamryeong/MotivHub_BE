INSERT INTO user (id, provider, provider_id, email, nickname, nickname_configured, profile_image_url, status, created_at)
VALUES
    (90001, 'GITHUB', 'k6-loadtest-1', 'k6-loadtest-1@test.local', 'k6user1', TRUE, NULL, 'ACTIVE', NOW()),
    (90002, 'GITHUB', 'k6-loadtest-2', 'k6-loadtest-2@test.local', 'k6user2', TRUE, NULL, 'ACTIVE', NOW()),
    (90003, 'GITHUB', 'k6-loadtest-3', 'k6-loadtest-3@test.local', 'k6user3', TRUE, NULL, 'ACTIVE', NOW()),
    (90004, 'GITHUB', 'k6-loadtest-4', 'k6-loadtest-4@test.local', 'k6user4', TRUE, NULL, 'ACTIVE', NOW()),
    (90005, 'GITHUB', 'k6-loadtest-5', 'k6-loadtest-5@test.local', 'k6user5', TRUE, NULL, 'ACTIVE', NOW()),
    (90006, 'GITHUB', 'k6-loadtest-6', 'k6-loadtest-6@test.local', 'k6user6', TRUE, NULL, 'ACTIVE', NOW()),
    (90007, 'GITHUB', 'k6-loadtest-7', 'k6-loadtest-7@test.local', 'k6user7', TRUE, NULL, 'ACTIVE', NOW()),
    (90008, 'GITHUB', 'k6-loadtest-8', 'k6-loadtest-8@test.local', 'k6user8', TRUE, NULL, 'ACTIVE', NOW()),
    (90009, 'GITHUB', 'k6-loadtest-9', 'k6-loadtest-9@test.local', 'k6user9', TRUE, NULL, 'ACTIVE', NOW()),
    (90010, 'GITHUB', 'k6-loadtest-10', 'k6-loadtest-10@test.local', 'k6user10', TRUE, NULL, 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE status = 'ACTIVE', deleted_at = NULL;
