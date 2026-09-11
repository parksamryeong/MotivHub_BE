import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const HEAVY_TASK_ID = 90076;

// No `options.stages` here either — see mixed-read-load-test.js for why. VU/duration are
// controlled via `-u`/`-d` CLI flags.

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

export default function (data) {
    const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
    const headers = { Authorization: `Bearer ${token}` };
    const res = http.get(`${BASE_URL}/api/tasks/${HEAVY_TASK_ID}`, { headers });
    check(res, { 'GET /api/tasks/{heavyId} returns 200': (r) => r.status === 200 });
    sleep(1);
}
