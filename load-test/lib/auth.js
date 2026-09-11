import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

function base64url(obj) {
    return encoding.b64encode(JSON.stringify(obj), 'rawurl');
}

// The app's JwtProvider calls Keys.hmacShaKeyFor(secret.getBytes()), which picks the
// signing algorithm from the secret's byte length: 32-47 bytes -> HS256, 48-63 -> HS384,
// 64+ -> HS512. This function always signs as HS256, so pass a 32-47 byte secret that
// matches the app's JWT_SECRET byte-for-byte (both value and length).
export function signAccessToken(userId, secret) {
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
