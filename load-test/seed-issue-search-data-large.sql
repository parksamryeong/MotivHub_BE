-- 이슈 검색 부하테스트 - 대규모(50만 건) 버전. InnoDB 기본 버퍼풀(128MB)을 확실히 넘어서는
-- 데이터 규모(설명 텍스트만 약 550MB)를 만들어서, 메모리에 다 못 올라가고 디스크 I/O가 끼어드는
-- 상황에서도 LIKE 풀스캔이 괜찮은지 확인하기 위한 목적이다. 기존 1만 건짜리
-- seed-issue-search-data.sql(빠른 회귀 체크용)과는 완전히 별개 파일이고, 키워드도 겹치지 않게
-- 다르게 둬서 두 시드가 동시에 들어있어도 서로의 매칭 건수를 오염시키지 않는다.
--
-- 50만 건을 단순 재귀 CTE로 만들면 재귀를 50만 번 돌려야 해서 느리다 - 대신 작은 수열 2개
-- (1000개 x 500개)를 교차곱(CROSS JOIN)해서 한 번에 50만 조합을 만드는 방식을 쓴다. 재귀는
-- 1000번만 돌면 되고 나머지는 세트 단위 연산이라 훨씬 빠르다.
SET SESSION cte_max_recursion_depth = 2000;

DELETE FROM issue WHERE id BETWEEN 200001 AND 700000;

-- 이슈 500,000개(id 200001~700000). 400개 중 1개꼴(n % 400 = 0, 총 1,250건)에만 이 대규모
-- 테스트 전용 키워드를 심는다. 나머지 498,750건은 매칭되지 않는 일반 더미 본문.
INSERT INTO issue (id, workspace_id, title, problem_description, solution, author_id, created_at, updated_at)
SELECT
    200001 + n,
    90001 + (n % 3),
    CONCAT('Search load issue (large) ', n + 1),
    CASE
        WHEN n % 400 = 0 THEN CONCAT(
            REPEAT('이것은 대규모 부하테스트용 더미 설명입니다. ', 8),
            'BOTTLENECKPROBELARGE',
            REPEAT(' 추가 더미 텍스트로 길이를 채웁니다.', 4))
        ELSE REPEAT('이것은 대규모 부하테스트용 더미 설명입니다. 실제 이슈 본문과 비슷한 길이를 흉내냅니다. ', 8)
    END,
    NULL,
    90001 + (n % 10),
    NOW(), NOW()
FROM (
    WITH RECURSIVE seq_a AS (
        SELECT 0 AS a
        UNION ALL
        SELECT a + 1 FROM seq_a WHERE a < 999
    ),
    seq_b AS (
        SELECT 0 AS b
        UNION ALL
        SELECT b + 1 FROM seq_b WHERE b < 499
    )
    SELECT (a.a * 500 + b.b) AS n FROM seq_a a CROSS JOIN seq_b b
) AS seq;
