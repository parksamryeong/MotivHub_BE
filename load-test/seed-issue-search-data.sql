-- 이슈 검색(LIKE 풀스캔) 부하테스트 전용 대량 시드. seed-read-heavy-data.sql과 완전히 분리된 별도
-- 파일이다 - 여기서 추가하는 이슈 1만 건이 기존 mixed-read-load-test.js의 "검색어 없는 이슈 목록
-- 조회" 경로(GET /api/issues도 페이지네이션 없이 전체 반환)까지 덩달아 느리게 만들어 원인을 섞으면
-- 안 되기 때문이다. 이 부하테스트를 할 때만 실행한다.

-- 기본 cte_max_recursion_depth(MySQL 8 기본값 1000)보다 많은 행을 재귀 CTE로 만들어야 해서
-- 세션 단위로 올린다.
SET SESSION cte_max_recursion_depth = 11000;

DELETE FROM issue WHERE id BETWEEN 91001 AND 101000;

-- 이슈 10,000개(id 91001~101000). 400개 중 1개꼴(n % 400 = 0, 총 25건)에만 눈에 띄는 테스트 전용
-- 키워드를 심어서 "매칭은 적지만 테이블 전체를 훑어야 하는" 검색 시나리오를 만든다. 나머지 9,975건은
-- 매칭되지 않는 일반 더미 본문(현실적인 길이를 흉내내기 위해 ~300자로 채움).
INSERT INTO issue (id, workspace_id, title, problem_description, solution, author_id, created_at, updated_at)
SELECT
    91001 + n,
    90001 + (n % 3),
    CONCAT('Search load issue ', n + 1),
    CASE
        WHEN n % 400 = 0 THEN CONCAT(
            REPEAT('이것은 부하테스트용 더미 설명입니다. ', 8),
            'LOADTESTKEYWORD',
            REPEAT(' 추가 더미 텍스트로 길이를 채웁니다.', 4))
        ELSE REPEAT('이것은 부하테스트용 더미 설명입니다. 실제 이슈 본문과 비슷한 길이를 흉내냅니다. ', 8)
    END,
    NULL,
    90001 + (n % 10),
    NOW(), NOW()
FROM (
    WITH RECURSIVE seq AS (
        SELECT 0 AS n
        UNION ALL
        SELECT n + 1 FROM seq WHERE n < 9999
    )
    SELECT * FROM seq
) AS seq;
