import ws from 'k6/ws';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { signAccessToken } from './lib/auth.js';
import { buildFrame, parseFrames } from './lib/stomp-ws.js';

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const USER_IDS = [90001, 90002, 90003, 90004, 90005, 90006, 90007, 90008, 90009, 90010];
const TASK_ID = 90076;
const HOLD_MS = 8000;

// SUBSCRIBE 전송 후 이 태스크의 presence 토픽으로 브로드캐스트가 도착하기까지 걸린 시간.
// 동시입장 스톰 중 뷰어 수가 늘어날수록(핸들러가 매번 전체 뷰어 목록을 다시 읽어 재브로드캐스트하므로)
// 이 값이 어떻게 변하는지가 이번 테스트의 핵심 관찰 대상.
const presenceBroadcastLatency = new Trend('presence_broadcast_latency_ms');
const stompErrorFrames = new Counter('stomp_error_frames');

export default function () {
    const userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];
    const token = signAccessToken(userId, JWT_SECRET);
    let subscribedAt = null;

    const res = ws.connect(WS_URL, {}, function (socket) {
        socket.on('open', () => {
            socket.send(buildFrame('CONNECT', {
                'accept-version': '1.2',
                'heart-beat': '0,0',
                Authorization: `Bearer ${token}`,
            }));
        });

        socket.on('message', (data) => {
            for (const frame of parseFrames(data)) {
                if (frame.command === 'CONNECTED') {
                    subscribedAt = Date.now();
                    socket.send(buildFrame('SUBSCRIBE', {
                        id: 'sub-0',
                        destination: `/topic/tasks/${TASK_ID}/presence`,
                    }));
                } else if (frame.command === 'MESSAGE' && subscribedAt !== null) {
                    presenceBroadcastLatency.add(Date.now() - subscribedAt);
                } else if (frame.command === 'ERROR') {
                    stompErrorFrames.add(1);
                }
            }
        });

        socket.setTimeout(() => {
            socket.send(buildFrame('DISCONNECT', {}));
            socket.close();
        }, HOLD_MS);
    });

    check(res, { 'ws handshake status is 101': (r) => r && r.status === 101 });
}
