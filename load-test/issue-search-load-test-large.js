import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const SEARCH_KEYWORD = 'BOTTLENECKPROBELARGE';
const EXPECTED_MATCH_COUNT = 1250;

// issue-search-load-test.js(1만 건)의 대규모 버전 - 이슈 50만 건(seed-issue-search-data-large.sql)
// 규모에서 같은 쿼리를 같은 VU 레벨로 재측정한다. InnoDB 기본 버퍼풀(128MB)을 설명 텍스트만으로도
// 몇 배 넘어서는 규모라, 메모리에 다 못 올라가고 디스크 I/O가 끼어드는 상황에서 LIKE 풀스캔이
// 어떻게 되는지를 보려는 목적. 1만 건 테스트와 VU 레벨을 동일하게 맞춰서(10/20/30) 데이터 크기
// 변수 하나만 바뀐 통제된 비교가 되게 한다.
//
// 키워드가 기존 1만 건 시드의 LOADTESTKEYWORD와 겹치지 않는 이유: 두 시드가 동시에 DB에 들어있어도
// (seed-issue-search-data.sql을 지우지 않고 이것만 추가로 넣은 경우) 서로의 매칭 건수를 오염시키지
// 않기 위함이다.

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

export default function (data) {
    const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
    const headers = { Authorization: `Bearer ${token}` };
    const label = 'GET /api/issues?q=(50만건 중 1250건 매칭)';
    const res = http.get(`${BASE_URL}/api/issues?q=${SEARCH_KEYWORD}`, { headers, tags: { name: label } });

    check(res, {
        [`${label} returns 200`]: (r) => r.status === 200,
        [`${label} matches exactly ${EXPECTED_MATCH_COUNT} issues`]: (r) => {
            if (r.status !== 200) return false;
            const body = JSON.parse(r.body);
            return Array.isArray(body) && body.length === EXPECTED_MATCH_COUNT;
        },
    });
    sleep(1);
}
