import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const SEARCH_KEYWORD = 'LOADTESTKEYWORD';
const EXPECTED_MATCH_COUNT = 25;

// 다른 heavy-task-stress-test.js와 같은 이유로 options.stages를 쓰지 않는다 - VU/기간은
// -u/-d CLI 플래그로 제어해서, 레벨마다 독립적인 --summary-export를 뜨게 한다.
//
// 이 시나리오는 이슈 검색(앞뒤 와일드카드 LIKE, 3개 컬럼 OR, 워크스페이스 스코프 없음) 경로 하나만
// 단독으로 때린다 - 다른 API와 섞으면 느려지는 원인이 이 쿼리 때문인지 혼합 트래픽 때문인지 구분이
// 안 된다(기존 heavy-task-stress-test.js와 같은 "원인 분리" 철학).

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

export default function (data) {
    const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
    const headers = { Authorization: `Bearer ${token}` };
    const label = 'GET /api/issues?q=(1만건 중 25건 매칭)';
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
