import http from 'k6/http';
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];

export const options = {
    stages: [
        { duration: '1m', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 0 },
    ],
};

function base64url(obj) {
    return encoding.b64encode(JSON.stringify(obj), 'rawurl');
}

function signAccessToken(userId, secret) {
    const header = { alg: 'HS256', typ: 'JWT' };
    const nowSeconds = Math.floor(Date.now() / 1000);
    const payload = {
        sub: String(userId),
        typ: 'access',
        iat: nowSeconds,
        exp: nowSeconds + 3600,
    };
    const signingInput = `${base64url(header)}.${base64url(payload)}`;
    const signature = crypto.hmac('sha256', secret, signingInput, 'base64rawurl');
    return `${signingInput}.${signature}`;
}

export function setup() {
    const tokens = USER_IDS.map((id) => signAccessToken(id, JWT_SECRET));
    return { tokens };
}

export default function (data) {
    const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
    const headers = { Authorization: `Bearer ${token}` };

    const meRes = http.get(`${BASE_URL}/api/users/me`, { headers });
    check(meRes, { 'GET /api/users/me returns 200': (r) => r.status === 200 });

    const myPageRes = http.get(`${BASE_URL}/api/users/me/mypage`, { headers });
    check(myPageRes, { 'GET /api/users/me/mypage returns 200': (r) => r.status === 200 });

    sleep(1);
}