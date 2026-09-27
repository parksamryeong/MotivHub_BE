import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const WORKSPACE_IDS = [90001, 90002, 90003];

// No `options.stages` here either — see mixed-read-load-test.js for why. VU/duration are
// controlled via `-u`/`-d` CLI flags.

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

function randomFrom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

export default function (data) {
    const token = randomFrom(data.tokens);
    const headers = { Authorization: `Bearer ${token}` };
    const roll = Math.random();

    let res;
    let label;
    if (roll < 0.3) {
        label = 'GET /api/tasks/mine (필터 없음)';
        res = http.get(`${BASE_URL}/api/tasks/mine`, { headers, tags: { name: label } });
    } else if (roll < 0.5) {
        // 시드 데이터의 태스크 75개는 전부 IN_PROGRESS라 실제로는 빈 배열이 돌아온다 -
        // 완료 태스크가 없는 채로도 쿼리 자체(정렬/50건 제한 포함)가 에러 없이 도는지는 확인 가능하지만,
        // 정렬 로직에 실제 부하를 주는 측정은 아니라는 점을 결과 해석 시 감안할 것.
        label = 'GET /api/tasks/mine?status=DONE';
        res = http.get(`${BASE_URL}/api/tasks/mine?status=DONE`, { headers, tags: { name: label } });
    } else if (roll < 0.7) {
        // 시드 태스크 이름이 전부 "k6 load task N"이라 'task'로 검색하면 워크스페이스당 25개가
        // 전부 매치된다 - LIKE 검색 경로에 실제 결과 집합을 태워보기 위한 값.
        label = 'GET /api/tasks/mine?q=task';
        res = http.get(`${BASE_URL}/api/tasks/mine?q=task`, { headers, tags: { name: label } });
    } else if (roll < 0.85) {
        label = 'GET /api/dashboard/stats (전체 워크스페이스)';
        res = http.get(`${BASE_URL}/api/dashboard/stats`, { headers, tags: { name: label } });
    } else {
        const workspaceId = randomFrom(WORKSPACE_IDS);
        label = 'GET /api/dashboard/stats?workspaceId={id}';
        res = http.get(`${BASE_URL}/api/dashboard/stats?workspaceId=${workspaceId}`, { headers, tags: { name: label } });
    }

    check(res, { [`${label} returns 200`]: (r) => r.status === 200 });
    sleep(1);
}
