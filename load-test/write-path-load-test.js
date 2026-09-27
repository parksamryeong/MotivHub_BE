import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const WORKSPACE_IDS = [90001, 90002, 90003];
const TASKS_PER_WORKSPACE = 25;
const STATUSES = ['WAITING', 'IN_PROGRESS'];

// No `options.stages` here either — see mixed-read-load-test.js for why. VU/duration are
// controlled via `-u`/`-d` CLI flags.
//
// 주의: 이 시나리오는 읽기 전용이 아니다. "태스크 생성" 분기는 매 호출마다 실제로 새 row를 만들어서
// DB에 누적된다(로컬 전용 - seed-read-heavy-data.sql의 예약 ID 범위 밖 auto-increment ID를 쓰므로
// 충돌은 없음). "댓글 작성" 분기도 마찬가지로 comment 테이블에 누적된다. VU를 반복 실행하면
// 실행할수록 테이블 크기가 커지므로, 나중 실행일수록 약간 불리한 조건이 될 수 있다는 점을 결과
// 해석 시 감안할 것 - 순수하게 "같은 데이터량에서 VU만 다르게"가 아니다.

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

function randomFrom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function randomTaskIdForWorkspace(workspaceId) {
    const workspaceIndex = WORKSPACE_IDS.indexOf(workspaceId);
    const base = 90001 + workspaceIndex * TASKS_PER_WORKSPACE;
    return base + Math.floor(Math.random() * TASKS_PER_WORKSPACE);
}

// seed-read-heavy-data.sql의 담당자 배정 규칙(태스크 id 90001+n -> 담당자 90001+(n%10)과
// 90001+((n+1)%10))과 정확히 일치해야 한다 - TaskService.changeStatus는 담당자/소유자만 허용해서
// (TaskAccessPolicy.requireEditPermission), 아무 유저나 토큰으로 호출하면 대부분 403이 난다.
function assigneeTokenForTask(tokens, taskId) {
    const n = taskId - 90001;
    const assigneeUserId = 90001 + (Math.random() < 0.5 ? n % 10 : (n + 1) % 10);
    return tokens[assigneeUserId - 90001];
}

export default function (data) {
    const workspaceId = randomFrom(WORKSPACE_IDS);
    const taskId = randomTaskIdForWorkspace(workspaceId);
    const roll = Math.random();

    let res;
    let label;
    if (roll < 0.25) {
        label = 'POST /api/workspaces/{id}/tasks (생성)';
        const headers = { Authorization: `Bearer ${randomFrom(data.tokens)}`, 'Content-Type': 'application/json' };
        const body = JSON.stringify({
            name: `k6 write-load task ${Date.now()}-${Math.floor(Math.random() * 100000)}`,
            description: 'k6 write-path load test로 생성된 태스크',
            startDate: new Date().toISOString().slice(0, 10),
            dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
            assigneeIds: [randomFrom(USER_IDS)],
            priority: 'MEDIUM',
        });
        res = http.post(`${BASE_URL}/api/workspaces/${workspaceId}/tasks`, body, { headers, tags: { name: label } });
    } else if (roll < 0.65) {
        label = 'PATCH /api/tasks/{id}/status (상태변경)';
        const headers = {
            Authorization: `Bearer ${assigneeTokenForTask(data.tokens, taskId)}`,
            'Content-Type': 'application/json',
        };
        const body = JSON.stringify({ status: randomFrom(STATUSES) });
        res = http.patch(`${BASE_URL}/api/tasks/${taskId}/status`, body, { headers, tags: { name: label } });
    } else {
        label = 'POST /api/tasks/{id}/comments (댓글작성)';
        const headers = { Authorization: `Bearer ${randomFrom(data.tokens)}`, 'Content-Type': 'application/json' };
        const body = JSON.stringify({ content: `k6 write-load comment ${Date.now()}` });
        res = http.post(`${BASE_URL}/api/tasks/${taskId}/comments`, body, { headers, tags: { name: label } });
    }

    check(res, { [`${label} returns 200`]: (r) => r.status === 200 });
    sleep(1);
}
