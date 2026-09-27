import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';
import { buildFrame, parseFrames } from './lib/stomp-ws.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const WORKSPACE_ID = 90001;
const TASK_ID = 90076;
const DURATION = __ENV.DURATION || '40s';
const SUBSCRIBER_COUNT = Number(__ENV.SUBSCRIBERS || 0);

// SUBSCRIBERS=0이면 writer만 돌려서 베이스라인을 잰다 - k6 executor는 vus:0을 허용하지 않으므로
// subscribers 시나리오 자체를 아예 options에서 뺀다.
const scenarios = {
    writer: {
        executor: 'constant-vus',
        exec: 'writerVU',
        vus: 1,
        duration: DURATION,
    },
};
if (SUBSCRIBER_COUNT > 0) {
    scenarios.subscribers = {
        executor: 'constant-vus',
        exec: 'subscriberVU',
        vus: SUBSCRIBER_COUNT,
        duration: DURATION,
    };
}
export const options = { scenarios };

// 유휴 구독자: 워크스페이스 보드 토픽을 구독만 하고 테스트 기간 내내 대기한다(팬아웃 대상 수를 늘리는 역할).
export function subscriberVU() {
    const userId = 90001 + (__VU % 10);
    const token = signAccessToken(userId, JWT_SECRET);
    let openedAt = null;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => {
            openedAt = Date.now();
            socket.send(buildFrame('CONNECT', {
                'accept-version': '1.2',
                'heart-beat': '0,0',
                Authorization: `Bearer ${token}`,
            }));
        });
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(buildFrame('SUBSCRIBE', {
                        id: 'sub-0',
                        destination: `/topic/workspaces/${WORKSPACE_ID}/tasks`,
                    }));
                } else if (frame.command === 'ERROR') {
                    console.error(`[VU${__VU}] STOMP ERROR: ${JSON.stringify(frame.headers)} body=${frame.body}`);
                }
            }
        });
        socket.on('error', (e) => console.error(`[VU${__VU}] ws error: ${e.error()}`));
        socket.on('close', () => {
            const heldMs = openedAt === null ? -1 : Date.now() - openedAt;
            if (heldMs >= 0 && heldMs < 30000) {
                console.error(`[VU${__VU}] closed early after ${heldMs}ms`);
            }
        });
        socket.setTimeout(() => socket.close(), 35000);
    });
    if (!res || res.status !== 101) {
        console.error(`[VU${__VU}] handshake failed: status=${res ? res.status : 'null'}`);
    }
}

// 이 VU 하나가 1초 간격으로 극단 케이스 태스크의 priority를 LOW/HIGH로 토글한다 - 매 호출이
// TaskChangedEvent -> TaskBoardBroadcaster.onTaskChanged(AFTER_COMMIT, 동기)를 트리거해서
// 워크스페이스 보드 구독자 전원에게 팬아웃된다. 이 REST 호출 자체의 지연이 관찰 대상.
const priorities = ['LOW', 'HIGH'];
let toggle = 0;

export function writerVU() {
    const token = signAccessToken(90001, JWT_SECRET);
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
    const priority = priorities[toggle % 2];
    toggle += 1;

    const res = http.patch(
        `${BASE_URL}/api/tasks/${TASK_ID}/priority`,
        JSON.stringify({ priority }),
        { headers, tags: { name: 'PATCH /api/tasks/{id}/priority (board fanout writer)' } });

    check(res, { 'priority patch returns 200': (r) => r.status === 200 });
    sleep(1);
}
