# 인증 (로그인/세션)

소셜 로그인(구글/깃허브/카카오/네이버) 기반 인증. 이메일/비밀번호 로그인은 없음 — 최초 로그인 시 자동 가입.
백엔드가 OAuth 처리를 전담하고, 프론트는 자체 발급 JWT(access/refresh)만 다룬다.
여러 기기(노트북/폰 등)에서 동시에 로그인 상태를 유지할 수 있다(기기별 독립 세션).

## 구조

### 프론트엔드 (`MotivHub_FE`)

```
src/
├── features/auth/
│   ├── LoginPage.tsx               # 소셜 로그인 버튼 4개
│   ├── OAuthCallbackPage.tsx       # code→토큰 교환 처리 (콜백 랜딩)
│   └── OnboardingNicknamePage.tsx  # 최초 로그인 시 닉네임 설정 강제
├── api/
│   ├── auth.ts     # exchangeCode, logout, oauthAuthorizeUrl
│   ├── client.ts   # axios 인스턴스 + 인터셉터(토큰 첨부, 401 자동 refresh)
│   ├── types.ts    # AuthTokens, UserProfile, OAuthProvider 타입
│   └── user.ts     # fetchMe, updateNickname 등
├── stores/authStore.ts   # zustand — accessToken(메모리)/refreshToken(localStorage)
├── routes/
│   ├── router.tsx         # 라우트 정의
│   └── ProtectedRoute.tsx # 미인증→/login, 닉네임 미설정→/onboarding
├── App.tsx    # 앱 부팅 시 silent refresh (StrictMode 중복실행 가드 있음)
└── main.tsx
```

### 백엔드 (`MotivHub_BE`)

```
com.motivhub.be
├── auth/
│   ├── config/SecurityConfig.java        # 인증 규칙, CORS, public 경로
│   ├── config/RedisConfig.java
│   ├── oauth/
│   │   ├── CustomOAuth2UserService.java  # 로그인 성공 후 유저 조회/가입
│   │   ├── OAuth2UserInfoFactory.java    # provider별 응답 정규화
│   │   ├── {Google,Github,Kakao,Naver}UserInfo.java
│   │   └── CustomOAuth2User.java         # Spring Security principal
│   ├── handler/
│   │   ├── OAuth2SuccessHandler.java     # 토큰 발급, deviceId 생성, 리다이렉트
│   │   └── OAuth2FailureHandler.java
│   ├── jwt/
│   │   ├── JwtProvider.java              # 토큰 생성/검증, did(deviceId) claim
│   │   └── JwtAuthenticationFilter.java  # 매 요청마다 Authorization 헤더 검증
│   ├── service/
│   │   ├── AuthService.java              # exchange/refresh/logout 비즈니스 로직
│   │   ├── TempAuthCodeService.java      # 1회용 code (Redis, 60초 TTL)
│   │   └── RefreshTokenService.java      # 기기별 refresh token 저장 (Redis)
│   ├── controller/AuthController.java    # /api/auth/{exchange,refresh,logout}
│   ├── dto/{TokenPair, ExchangeRequest, RefreshRequest}.java
│   └── exception/                        # Invalid*Exception, LogoutForbiddenException
├── user/
│   ├── domain/{User, SocialProvider, UserStatus}.java
│   ├── service/
│   │   ├── UserRegistrationService.java  # (provider,providerId)로 신규가입/재활성화
│   │   ├── UserService.java              # 프로필, 닉네임, 탈퇴
│   │   ├── NicknameValidator.java
│   │   └── RandomNicknameGenerator.java
│   ├── repository/UserRepository.java
│   └── controller/UserController.java    # /api/users/*
└── global/exception/{GlobalExceptionHandler, ErrorResponse}.java
```

## 전체 흐름

### ① 로그인 시작
`LoginPage.tsx` 버튼 클릭 → `window.location.href = "{API}/oauth2/authorization/{provider}"` — API 호출 없이 브라우저를 BE로 통째로 이동. 이후는 Spring Security OAuth2 Client가 자동 처리: provider 로그인 화면 → 동의 → `/login/oauth2/code/{provider}` 콜백 → 토큰 교환.

### ② 유저 확정 (`CustomOAuth2UserService`)
- `OAuth2UserInfoFactory`가 provider별로 다른 응답 필드를 공통 인터페이스(`OAuth2UserInfo`)로 정규화
- `UserRegistrationService.resolveUser()`: `(provider, providerId)` 복합키로 조회 → 없으면 신규가입(랜덤 닉네임, `nicknameConfigured=false`), 탈퇴 상태면 재활성화, 있으면 그대로
- `CustomOAuth2User(userId, attributes)`로 감싸서 Security 컨텍스트에 올림

### ③ 토큰 발급 (`OAuth2SuccessHandler`)
- `deviceId = UUID.randomUUID()` — 로그인마다 새로 생성 (멀티 디바이스의 핵심)
- `accessToken`(1시간, `typ=access`), `refreshToken`(2주, `typ=refresh` + `did={deviceId}` claim) 발급
- Redis에 `refresh-token:{userId}:{deviceId}` 키로 저장
- 1회용 `code` 발급(Redis, 60초 TTL) → `{frontendUrl}/oauth/callback?code={code}`로 리다이렉트 (토큰을 URL에 직접 노출 안 시키려고 이 code 브릿지를 거침)

### ④ 콜백 처리 (`OAuthCallbackPage.tsx`)
`hasRun` ref로 StrictMode 중복 실행 방지한 뒤:
- `POST /api/auth/exchange {code}` → BE가 Redis에서 code를 조회+즉시삭제(1회성)하고 토큰 쌍 반환
- `accessToken`은 zustand 메모리에만, `refreshToken`은 zustand persist로 localStorage에 저장
- `GET /api/users/me`로 유저 정보 조회 → `nicknameConfigured` 여부로 `/onboarding/nickname` 또는 `/mypage`로 라우팅

### ⑤ 인증된 요청 (매 API 호출)
- `apiClient`(axios) 요청 인터셉터가 `Authorization: Bearer {accessToken}` 자동 첨부
- BE `JwtAuthenticationFilter`가 헤더 검증 후 `SecurityContext`에 `userId`(Long) 세팅 → 컨트롤러는 `@AuthenticationPrincipal Long userId`로 바로 받음
- 401 받으면 응답 인터셉터가 자동으로 refresh 1회 시도(동시 요청은 `refreshPromise`로 중복 방지) 후 원래 요청 재시도, 그래도 실패하면 로그아웃 처리

### ⑥ 새로고침/앱 재진입 (`App.tsx`)
`hasRun` ref 가드 있는 부팅 `useEffect`:
- localStorage의 `refreshToken`으로 `POST /api/auth/refresh` 선제 호출
- BE `AuthService.refresh()`: 토큰에서 userId+deviceId 추출·검증 → Redis `refresh-token:{userId}:{deviceId}` 값과 일치 확인 → 새 토큰 발급(**같은 deviceId 유지**, 값만 rotate) → 저장 후 유저 정보 재조회

### ⑦ 로그아웃
`POST /api/auth/logout {refreshToken}` (액세스 토큰 헤더도 필요, 보호된 엔드포인트):
- BE: 토큰 만료/조작 시 그냥 204(이미 로그아웃 취급) → **소유권 확인이 먼저**(다른 사람 토큰이면 403) → deviceId 없으면 204 → **이 기기의 Redis 키만 삭제**
- FE: API 결과와 무관하게 `finally`에서 로컬 상태 지우고 `/login`으로 이동

### ⑧ 회원탈퇴
`DELETE /api/users/me` → soft delete(`status=WITHDRAWN`, 개인정보 마스킹) + `RefreshTokenService.deleteAll(userId)`로 **그 유저의 모든 기기 세션을 한 번에 삭제**

## 멀티 디바이스가 실제로 되는 원리

- Redis 키가 `refresh-token:{userId}:{deviceId}` 조합이라 기기마다 완전히 독립적인 슬롯
- 기기A 로그아웃 → 기기A의 키만 삭제, 기기B는 안 건드림
- 기기A가 refresh할 때마다 **같은 deviceId를 유지**하면서 값만 rotate → 그 기기의 세션이 계속 이어짐
- 탈퇴만 예외적으로 `deleteAll`로 전체 기기를 한 번에 정리(계정 자체가 없어지는 거니까)

## 보안 설계 요약

| 설계 | 이유 |
|---|---|
| access token은 메모리, refresh token은 localStorage | XSS 발생해도 새로고침하면 accessToken은 날아감 — 탈취 후 악용 가능 시간 축소 |
| code 브릿지(60초 1회용) | JWT를 URL 쿼리스트링에 그대로 노출하면 브라우저 히스토리/리퍼러/서버 로그에 남음 |
| refresh token rotation | 매 refresh마다 값이 바뀌어서, 탈취된 옛 토큰은 자동으로 무효화됨 |
| logout에서 소유권 체크 먼저 | 다른 사람의 refresh token으로 로그아웃 시도하는 걸 막음 (403) |
| deviceId를 refresh token에만 심음 | access token 포맷/검증 경로는 전혀 안 건드림 (블라스트 반경 최소화) |
| App.tsx/OAuthCallbackPage `hasRun` 가드 | React 18 StrictMode의 effect 이중 실행이 refresh rotation과 겹쳐서 생기는 레이스 컨디션 방지 |

## 관련 문서

- 최초 설계: `docs/superpowers/specs/2026-08-05-auth-design.md` (로컬 전용, 커밋 안 됨)
- 멀티 디바이스 설계: `docs/superpowers/specs/2026-09-01-multi-device-login-design.md` (로컬 전용, 커밋 안 됨)
- 발견된 버그/개선 이력: `docs/troubleshooting.md`
