// STOMP 1.2 프레임을 텍스트로 조립/파싱하는 최소 헬퍼. k6에는 STOMP 클라이언트가 없어서
// k6/ws(순수 WebSocket)로 프로토콜을 직접 구사한다. 이 앱의 WebSocketConfig는
// registry.addEndpoint("/ws").withSockJS()로 등록돼 있지만, SockJS의 raw websocket 폴백
// 경로인 "{endpoint}/websocket"(여기서는 "/ws/websocket")로 붙으면 SockJS 프레이밍 없이
// STOMP 프레임이 그대로 오간다 - 브라우저의 SockJS 라이브러리를 흉내 낼 필요가 없다.
const NULL_BYTE = '\x00';

export function buildFrame(command, headers = {}, body = '') {
    const headerLines = Object.entries(headers)
        .map(([key, value]) => `${key}:${value}`)
        .join('\n');
    return `${command}\n${headerLines}\n\n${body}${NULL_BYTE}`;
}

// 하나의 WebSocket 메시지에 프레임이 여러 개 이어 붙어 올 수 있어 NULL_BYTE 기준으로 분리한다.
// CONNECT에서 heart-beat:0,0을 선언해 서버발 하트비트(빈 줄 하나)는 애초에 발생하지 않지만,
// 방어적으로 선행 개행은 그냥 잘라낸다.
export function parseFrames(raw) {
    return raw
        .split(NULL_BYTE)
        .map((chunk) => chunk.replace(/^\n+/, ''))
        .filter((chunk) => chunk.length > 0)
        .map(parseFrame);
}

function parseFrame(text) {
    const [headerPart, ...bodyParts] = text.split('\n\n');
    const lines = headerPart.split('\n');
    const command = lines[0];
    const headers = {};
    for (const line of lines.slice(1)) {
        const idx = line.indexOf(':');
        if (idx > -1) {
            headers[line.slice(0, idx)] = line.slice(idx + 1);
        }
    }
    return { command, headers, body: bodyParts.join('\n\n') };
}
