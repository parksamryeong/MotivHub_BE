import ws from 'k6/ws';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { signAccessToken } from './lib/auth.js';
import { buildFrame, parseFrames } from './lib/stomp-ws.js';

// 이 테스트는 태스크 공동편집의 awareness(커서 위치 등) 중계 채널을 검증한다. 서버는 페이로드를
// 전혀 해석하지 않고 그대로 릴레이만 하며(TaskEditRelayController.relayAwareness, Redis 버퍼링도
// 없음), 기존 편집 중계(/edits)와 달리 저장/스냅샷 개념 자체가 없다 - 그래서 이 테스트는 "유실 없이
// 전달되는가"만 검증한다.
//
// VU 역할 분담: VU1은 옵저버(구독만, 아무것도 안 보냄 - 유실 여부 측정 기준), VU2 이상은 커서
// 발신자(전송만). -u N --iterations N (N = 발신자 수 + 1)으로 실행해서 전원이 한 번에 동시 전송하는
// 단일 버스트를 만든다.
//
// 메시지 빈도는 기존 편집 테스트(UPDATES_PER_EDITOR=5, UPDATE_INTERVAL_MS=200)의 4배다
// (UPDATES_PER_SENDER=20, UPDATE_INTERVAL_MS=50) - 같은 1초짜리 버스트 구간이지만, 커서 위치는
// 타이핑보다 훨씬 자주 바뀔 수 있다는 현실적 근거로 메시지량만 늘렸다.

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const RUN_ID = __ENV.RUN_ID || String(Date.now());
const TASK_ID = 90001;
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const UPDATES_PER_SENDER = 20;
const UPDATE_INTERVAL_MS = 50;
const OBSERVER_HOLD_MS = 10000;
const SAFETY_CLOSE_MS = 8000;

const awarenessSent = new Counter('awareness_sent_total');
const awarenessReceivedByObserver = new Counter('awareness_received_by_observer_total');
const awarenessRelayLatency = new Trend('awareness_relay_latency_ms');

function userIdForVu(vu) {
    return USER_IDS[(vu - 1) % USER_IDS.length];
}

function connectHeaders(userId) {
    return buildFrame('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '0,0',
        Authorization: `Bearer ${signAccessToken(userId, JWT_SECRET)}`,
    });
}

function subscribeAwarenessFrame(subId) {
    return buildFrame('SUBSCRIBE', { id: subId, destination: `/topic/tasks/${TASK_ID}/note/awareness` });
}

export default function () {
    const vu = __VU;
    if (vu === 1) {
        runObserver();
    } else {
        runSender(vu);
    }
}

function runObserver() {
    const userId = USER_IDS[0];
    ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(connectHeaders(userId)));
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(subscribeAwarenessFrame('sub-awareness'));
                } else if (frame.command === 'MESSAGE') {
                    awarenessReceivedByObserver.add(1);
                }
            }
        });
        socket.setTimeout(() => socket.close(), OBSERVER_HOLD_MS);
    });
}

function runSender(vu) {
    const userId = userIdForVu(vu);
    let sentCount = 0;
    let lastSendAt = null;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(connectHeaders(userId)));
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(subscribeAwarenessFrame('sub-awareness'));
                    socket.setTimeout(() => startSending(), 300);
                } else if (frame.command === 'MESSAGE' && lastSendAt !== null) {
                    awarenessRelayLatency.add(Date.now() - lastSendAt);
                    lastSendAt = null;
                }
            }
        });

        function startSending() {
            // k6 2.2.0의 k6/ws Socket 객체에는 setInterval은 있지만 clearInterval이 없다(실제
            // 환경에서 "Object has no member 'clearInterval'"로 VU가 죽는 것을 이전 공동편집
            // 테스트에서 확인한 바 있음) - 동일한 타이밍/횟수를 유지하면서 자체적으로 멈추는
            // setTimeout 재귀 체인으로 작성한다.
            function tick() {
                sentCount += 1;
                lastSendAt = Date.now();
                socket.send(buildFrame(
                    'SEND',
                    { destination: `/app/tasks/${TASK_ID}/note/awareness`, 'content-type': 'application/json' },
                    JSON.stringify({ update: `vu${vu}-cursor${sentCount}-${RUN_ID}` }),
                ));
                awarenessSent.add(1);
                if (sentCount < UPDATES_PER_SENDER) {
                    socket.setTimeout(tick, UPDATE_INTERVAL_MS);
                }
            }
            socket.setTimeout(tick, UPDATE_INTERVAL_MS);
        }

        socket.setTimeout(() => socket.close(), SAFETY_CLOSE_MS);
    });

    check(res, { 'sender ws handshake status is 101': (r) => r && r.status === 101 });
}
