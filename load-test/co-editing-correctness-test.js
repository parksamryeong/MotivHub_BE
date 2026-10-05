import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { signAccessToken } from './lib/auth.js';
import { buildFrame, parseFrames } from './lib/stomp-ws.js';

// 이 테스트는 실시간 공동편집(CRDT 릴레이·버퍼링·자동저장) 경로를 검증한다. 서버는 CRDT를 파싱하지
// 않고 타이핑 업데이트를 그대로 릴레이·버퍼링만 하므로(relay-only), 여기서 보내는 "타이핑 업데이트"는
// 실제 Yjs 바이너리가 아니라 임의의 문자열이면 충분하다(TaskLiveCoEditingEndToEndTest와 동일한 방식).
//
// VU 역할 분담: VU1은 옵저버(구독만, 아무것도 안 보냄 - 유실 여부 측정 기준), VU2는 제출자(타이핑 +
// 저장 요청 신호를 받으면 스냅샷 전송 + REST로 저장 결과 검증), VU3 이상은 일반 편집자(타이핑만).
// -u N --iterations N (N = 편집자 수 + 2)로 실행해서 전원이 한 번에 동시 편집하는 단일 버스트를 만든다.

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const HTTP_URL = __ENV.HTTP_URL || 'http://localhost:8080';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const RUN_ID = __ENV.RUN_ID || String(Date.now());
const TASK_ID = 90001;
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const UPDATES_PER_EDITOR = 5;
const UPDATE_INTERVAL_MS = 200;
const FINAL_CONTENT = `FINAL-${RUN_ID}`;
const OBSERVER_HOLD_MS = 10000;
const SAFETY_CLOSE_MS = 8000;

const editsSent = new Counter('edits_sent_total');
const editsReceivedByObserver = new Counter('edits_received_by_observer_total');
const editRelayLatency = new Trend('edit_relay_latency_ms');
const autosaveSignalLatency = new Trend('autosave_signal_latency_ms');

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

function subscribeEditsFrame(subId) {
    return buildFrame('SUBSCRIBE', { id: subId, destination: `/topic/tasks/${TASK_ID}/note/edits` });
}

export default function () {
    const vu = __VU;
    if (vu === 1) {
        runObserver();
    } else if (vu === 2) {
        runSubmitter();
    } else {
        runEditor(vu);
    }
}

function runObserver() {
    const userId = USER_IDS[0];
    ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(connectHeaders(userId)));
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(subscribeEditsFrame('sub-edits'));
                } else if (frame.command === 'MESSAGE') {
                    editsReceivedByObserver.add(1);
                }
            }
        });
        socket.setTimeout(() => socket.close(), OBSERVER_HOLD_MS);
    });
}

function runEditor(vu) {
    const userId = userIdForVu(vu);
    let sentCount = 0;
    let lastSendAt = null;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(connectHeaders(userId)));
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(subscribeEditsFrame('sub-edits'));
                    socket.setTimeout(() => startTyping(), 300);
                } else if (frame.command === 'MESSAGE' && lastSendAt !== null) {
                    editRelayLatency.add(Date.now() - lastSendAt);
                    lastSendAt = null;
                }
            }
        });

        function startTyping() {
            // k6 2.2.0의 k6/ws Socket 객체에는 setInterval은 있지만 clearInterval이 없다(실제
            // 환경에서 "Object has no member 'clearInterval'"로 VU가 죽는 것을 확인) — 동일한
            // 타이밍/횟수를 유지하면서 자체적으로 멈추는 setTimeout 재귀 체인으로 대체한다.
            function tick() {
                sentCount += 1;
                lastSendAt = Date.now();
                socket.send(buildFrame(
                    'SEND',
                    { destination: `/app/tasks/${TASK_ID}/note/edits`, 'content-type': 'application/json' },
                    JSON.stringify({ update: `vu${vu}-edit${sentCount}` }),
                ));
                editsSent.add(1);
                if (sentCount < UPDATES_PER_EDITOR) {
                    socket.setTimeout(tick, UPDATE_INTERVAL_MS);
                }
            }
            socket.setTimeout(tick, UPDATE_INTERVAL_MS);
        }

        socket.setTimeout(() => socket.close(), SAFETY_CLOSE_MS);
    });

    check(res, { 'editor ws handshake status is 101': (r) => r && r.status === 101 });
}

function runSubmitter() {
    const userId = userIdForVu(2);
    let sentCount = 0;
    let lastSendAt = null;
    let typingDoneAt = null;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => socket.send(connectHeaders(userId)));
        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    socket.send(subscribeEditsFrame('sub-edits'));
                    socket.send(buildFrame('SUBSCRIBE', {
                        id: 'sub-save-request',
                        destination: `/topic/tasks/${TASK_ID}/note/save-request`,
                    }));
                    socket.setTimeout(() => startTyping(), 300);
                } else if (frame.command === 'MESSAGE') {
                    if (frame.headers['destination'] === `/topic/tasks/${TASK_ID}/note/save-request`) {
                        if (typingDoneAt !== null) {
                            autosaveSignalLatency.add(Date.now() - typingDoneAt);
                        }
                        sendSnapshot();
                    } else if (lastSendAt !== null) {
                        editRelayLatency.add(Date.now() - lastSendAt);
                        lastSendAt = null;
                    }
                }
            }
        });

        function startTyping() {
            // runEditor와 동일한 이유로 setInterval/clearInterval 대신 setTimeout 재귀 체인을 쓴다
            // (이 k6 빌드의 k6/ws Socket에는 clearInterval이 없음 - 실제 실행으로 확인).
            function tick() {
                sentCount += 1;
                lastSendAt = Date.now();
                socket.send(buildFrame(
                    'SEND',
                    { destination: `/app/tasks/${TASK_ID}/note/edits`, 'content-type': 'application/json' },
                    JSON.stringify({ update: `vu2-edit${sentCount}` }),
                ));
                editsSent.add(1);
                if (sentCount < UPDATES_PER_EDITOR) {
                    socket.setTimeout(tick, UPDATE_INTERVAL_MS);
                } else {
                    typingDoneAt = Date.now();
                }
            }
            socket.setTimeout(tick, UPDATE_INTERVAL_MS);
        }

        function sendSnapshot() {
            socket.send(buildFrame(
                'SEND',
                { destination: `/app/tasks/${TASK_ID}/note/snapshot`, 'content-type': 'application/json' },
                JSON.stringify({ content: FINAL_CONTENT, yjsState: null }),
            ));
            // 전송 프레임이 플러시될 시간을 아주 짧게 준 뒤 닫는다 - 안전망 타임아웃(8초)까지 기다리지
            // 않고 바로 다음 단계(REST 검증)로 넘어가기 위함.
            socket.setTimeout(() => socket.close(), 200);
        }

        socket.setTimeout(() => socket.close(), SAFETY_CLOSE_MS);
    });

    check(res, { 'submitter ws handshake status is 101': (r) => r && r.status === 101 });

    sleep(1); // 스냅샷이 별도 트랜잭션에서 커밋될 시간 (기존 TaskLiveCoEditingEndToEndTest와 동일한 이유)

    const token = signAccessToken(userId, JWT_SECRET);
    const getRes = http.get(`${HTTP_URL}/api/tasks/${TASK_ID}/note`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    check(getRes, {
        'note content persisted correctly': (r) => {
            if (r.status !== 200) return false;
            const body = JSON.parse(r.body);
            return body.content === FINAL_CONTENT;
        },
    });
}
