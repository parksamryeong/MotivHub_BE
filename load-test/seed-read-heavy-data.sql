-- 로컬 개발 DB 전용. 90001~90076 범위를 무조건 DELETE하므로 절대 운영/공용 DB에 실행하지 말 것.

-- 재실행 가능하도록 이번 시드가 만드는 범위를 먼저 정리(자식 → 부모 순서로 삭제)
DELETE FROM task_activity_log WHERE task_id BETWEEN 90001 AND 90076;
DELETE FROM task_comment WHERE task_id BETWEEN 90001 AND 90076;
DELETE FROM task_checklist_item WHERE task_id BETWEEN 90001 AND 90076;
DELETE FROM task_assignee WHERE task_id BETWEEN 90001 AND 90076;
DELETE FROM task WHERE id BETWEEN 90001 AND 90076;
DELETE FROM issue_comment WHERE issue_id BETWEEN 90001 AND 90025;
DELETE FROM issue WHERE id BETWEEN 90001 AND 90025;
DELETE FROM workspace_file WHERE workspace_id BETWEEN 90001 AND 90003;
DELETE FROM workspace_member WHERE workspace_id BETWEEN 90001 AND 90003;
DELETE FROM workspace_invite WHERE workspace_id BETWEEN 90001 AND 90003;
DELETE FROM workspace WHERE id BETWEEN 90001 AND 90003;

-- 워크스페이스 3개
INSERT INTO workspace (id, name, created_at, deleted_at) VALUES
    (90001, 'k6-load-workspace-1', NOW(), NULL),
    (90002, 'k6-load-workspace-2', NOW(), NULL),
    (90003, 'k6-load-workspace-3', NOW(), NULL);

-- 워크스페이스 멤버: 10명 유저 전원이 3개 워크스페이스 모두의 멤버. 워크스페이스 9000N의 OWNER는 유저 9000N
INSERT INTO workspace_member (workspace_id, user_id, role, joined_at)
SELECT w.id, u.id, IF(u.id = w.id, 'OWNER', 'MEMBER'), NOW()
FROM (SELECT 90001 AS id UNION ALL SELECT 90002 UNION ALL SELECT 90003) w
CROSS JOIN (
    SELECT 90001 AS id UNION ALL SELECT 90002 UNION ALL SELECT 90003 UNION ALL SELECT 90004
    UNION ALL SELECT 90005 UNION ALL SELECT 90006 UNION ALL SELECT 90007 UNION ALL SELECT 90008
    UNION ALL SELECT 90009 UNION ALL SELECT 90010
) u;

-- 일반 태스크 75개 (워크스페이스당 25개), id 90001~90075
INSERT INTO task (id, workspace_id, name, description, start_date, due_date, status, created_by, created_at)
SELECT
    90001 + n,
    90001 + FLOOR(n / 25),
    CONCAT('k6 load task ', n + 1),
    CONCAT('Seed task ', n + 1, ' for read-heavy load testing'),
    CURDATE(),
    CURDATE() + INTERVAL 30 DAY,
    'IN_PROGRESS',
    90001 + (n % 10),
    NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 74
    )
    SELECT * FROM seq
) AS seq;

-- 극단 케이스 태스크 1개 (워크스페이스 90001 소속, id 90076)
INSERT INTO task (id, workspace_id, name, description, start_date, due_date, status, created_by, created_at)
VALUES (
    90076, 90001, 'k6 load extreme task',
    'Extreme case task with 300 checklist items / comments / activity logs for the stress scenario',
    CURDATE(), CURDATE() + INTERVAL 30 DAY, 'IN_PROGRESS', 90001, NOW()
);

-- 일반 태스크 담당자 2명씩 (생성자 본인 + 다음 유저)
INSERT INTO task_assignee (task_id, user_id)
SELECT 90001 + n, 90001 + (n % 10) FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 74
    )
    SELECT * FROM seq
) AS seq
UNION ALL
SELECT 90001 + n, 90001 + ((n + 1) % 10) FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 74
    )
    SELECT * FROM seq
) AS seq;

-- 극단 케이스 태스크 담당자 1명
INSERT INTO task_assignee (task_id, user_id) VALUES (90076, 90001);

-- 일반 태스크당 댓글 3개
INSERT INTO task_comment (task_id, author_id, content, created_at, updated_at)
SELECT
    90001 + seq.n,
    90001 + ((seq.n + c.n) % 10),
    CONCAT('Seed comment ', c.n + 1, ' on task ', seq.n + 1),
    NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 74
    )
    SELECT * FROM seq
) AS seq
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) AS c;

-- 일반 태스크당 체크리스트 항목 4개
INSERT INTO task_checklist_item (task_id, content, is_done, order_index, created_at)
SELECT
    90001 + seq.n,
    CONCAT('Seed checklist item ', c.n + 1),
    (c.n = 0),
    c.n,
    NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 74
    )
    SELECT * FROM seq
) AS seq
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) AS c;

-- 워크스페이스 파일 45개 (워크스페이스당 15개)
INSERT INTO workspace_file (workspace_id, file_key, file_name, file_size, content_type, uploaded_by, created_at, category)
SELECT
    90001 + FLOOR(n / 15),
    CONCAT('workspaces/', 90001 + FLOOR(n / 15), '/files/seed-', n, '.pdf'),
    CONCAT('seed-file-', n + 1, '.pdf'),
    102400,
    'application/pdf',
    90001 + (n % 10),
    NOW(),
    NULL
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 44
    )
    SELECT * FROM seq
) AS seq;

-- 플랫폼 이슈 25개
INSERT INTO issue (id, workspace_id, title, problem_description, solution, author_id, created_at, updated_at)
SELECT
    90001 + n,
    90001 + (n % 3),
    CONCAT('Seed issue ', n + 1),
    CONCAT('Seed problem description for issue ', n + 1),
    NULL,
    90001 + (n % 10),
    NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 24
    )
    SELECT * FROM seq
) AS seq;

-- 이슈당 댓글 3개
INSERT INTO issue_comment (issue_id, author_id, content, created_at)
SELECT
    90001 + seq.n,
    90001 + ((seq.n + c.n) % 10),
    CONCAT('Seed comment ', c.n + 1, ' on issue ', seq.n + 1),
    NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 24
    )
    SELECT * FROM seq
) AS seq
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) AS c;

-- 극단 케이스: 체크리스트 300개
INSERT INTO task_checklist_item (task_id, content, is_done, order_index, created_at)
SELECT 90076, CONCAT('Extreme checklist item ', n + 1), FALSE, n, NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 299
    )
    SELECT * FROM seq
) AS seq;

-- 극단 케이스: 댓글 300개
INSERT INTO task_comment (task_id, author_id, content, created_at, updated_at)
SELECT 90076, 90001 + (n % 10), CONCAT('Extreme comment ', n + 1), NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 299
    )
    SELECT * FROM seq
) AS seq;

-- 극단 케이스: 활동 로그 300건 (서비스 로직 우회, CREATE 1건 + CHANGE_STATUS 스냅샷 299건)
INSERT INTO task_activity_log (task_id, actor_id, action, field, old_value, new_value, created_at)
SELECT
    90076,
    90001,
    IF(n = 0, 'CREATE', 'CHANGE_STATUS'),
    IF(n = 0, NULL, 'status'),
    IF(n = 0, NULL, 'WAITING'),
    IF(n = 0, NULL, 'IN_PROGRESS'),
    NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 299
    )
    SELECT * FROM seq
) AS seq;
