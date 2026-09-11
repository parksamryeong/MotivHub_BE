import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const WORKSPACE_IDS = [90001, 90002, 90003];
const TASKS_PER_WORKSPACE = 25;
const ISSUE_IDS = Array.from({ length: 25 }, (_, i) => 90001 + i);

// No `options.stages` here on purpose — VU count and duration are controlled via the
// `-u`/`-d` CLI flags at run time, so the same script can be run once per VU level and
// produce one independent --summary-export per level. See the plan for why.

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

function randomFrom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

// A random *normal* task belonging to workspaceId. Task IDs are laid out contiguously
// per workspace by the seed script (e.g. workspace 90001 -> tasks 90001-90025), and the
// extreme-case task (90076) falls outside every workspace's normal range, so no explicit
// exclusion is needed here.
function randomTaskIdForWorkspace(workspaceId) {
    const workspaceIndex = WORKSPACE_IDS.indexOf(workspaceId);
    const base = 90001 + workspaceIndex * TASKS_PER_WORKSPACE;
    return base + Math.floor(Math.random() * TASKS_PER_WORKSPACE);
}

export default function (data) {
    const token = randomFrom(data.tokens);
    const headers = { Authorization: `Bearer ${token}` };
    const workspaceId = randomFrom(WORKSPACE_IDS);
    const roll = Math.random();

    let res;
    let label;
    if (roll < 0.4) {
        label = 'GET /api/workspaces/{id}/tasks';
        res = http.get(`${BASE_URL}/api/workspaces/${workspaceId}/tasks`, { headers, tags: { name: label } });
    } else if (roll < 0.7) {
        const taskId = randomTaskIdForWorkspace(workspaceId);
        label = 'GET /api/tasks/{id} (normal)';
        res = http.get(`${BASE_URL}/api/tasks/${taskId}`, { headers, tags: { name: label } });
    } else if (roll < 0.85) {
        label = 'GET /api/workspaces/{id}/files';
        res = http.get(`${BASE_URL}/api/workspaces/${workspaceId}/files`, { headers, tags: { name: label } });
    } else if (roll < 0.925) {
        label = 'GET /api/issues';
        res = http.get(`${BASE_URL}/api/issues`, { headers, tags: { name: label } });
    } else {
        const issueId = randomFrom(ISSUE_IDS);
        label = 'GET /api/issues/{id}';
        res = http.get(`${BASE_URL}/api/issues/${issueId}`, { headers, tags: { name: label } });
    }

    check(res, { [`${label} returns 200`]: (r) => r.status === 200 });
    sleep(1);
}
