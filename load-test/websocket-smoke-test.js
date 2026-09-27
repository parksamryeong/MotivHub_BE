import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { signAccessToken } from './lib/auth.js';
import { buildFrame, parseFrames } from './lib/stomp-ws.js';

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/websocket';
const JWT_SECRET = __ENV.JWT_SECRET || 'k6loadtestdevsecretexactly32byte';
const TASK_ID = 90076;

export default function () {
    const token = signAccessToken(90001, JWT_SECRET);
    let connected = false;
    let subscribed = false;
    let receivedMessage = false;

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
                    connected = true;
                    socket.send(buildFrame('SUBSCRIBE', {
                        id: 'sub-0',
                        destination: `/topic/tasks/${TASK_ID}/presence`,
                    }));
                    subscribed = true;
                } else if (frame.command === 'MESSAGE') {
                    receivedMessage = true;
                } else if (frame.command === 'ERROR') {
                    console.error(`STOMP ERROR frame: ${JSON.stringify(frame.headers)} body=${frame.body}`);
                }
            }
        });

        socket.on('error', (e) => console.error(`WS error: ${e.error()}`));

        socket.setTimeout(() => {
            socket.send(buildFrame('DISCONNECT', {}));
            socket.close();
        }, 3000);
    });

    check(res, { 'ws handshake status is 101': (r) => r && r.status === 101 });
    check(null, { 'STOMP CONNECTED received': () => connected });
    check(null, { 'SUBSCRIBE sent': () => subscribed });
    check(null, { '프레즌스 MESSAGE 수신 (자기 자신의 join 브로드캐스트)': () => receivedMessage });
    sleep(1);
}
