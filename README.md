# MotivHub_BE

팀 단위 워크스페이스에서 태스크를 관리하고, 실시간 공동 편집·알림으로 함께 일하는 흐름을 지원하는 협업 툴의 백엔드입니다.

## 주요 기능

- **인증**: 소셜 로그인(구글/깃허브/카카오) + 이메일/비밀번호 로그인·회원가입(6자리 인증코드 방식), JWT 기반 멀티 디바이스 세션
- **워크스페이스**: 팀 생성/초대/멤버 관리, 권한(소유자/멤버) 기반 접근 제어
- **태스크 관리**: 상태·우선순위·기간 관리, 체크리스트, 댓글, 담당자·감시자 지정, 활동 로그
- **실시간 공동 편집**: Yjs(CRDT) 기반 태스크 노트 동시 편집, WebSocket(STOMP)으로 보드 변경사항·접속자 현황(프레즌스) 실시간 동기화
- **알림**: 담당자 지정/댓글/마감 임박 등 이벤트 기반 알림, 대량 발송 시 병목을 피하기 위한 비동기 팬아웃 처리
- **이슈 게시판**, **워크스페이스 파일 공유**(S3 호환 스토리지)
- **부하테스트 기반 성능 최적화**: k6로 부하를 주며 Grafana/Prometheus로 실시간 관측, 실측 기반으로 병목을 찾아 개선

## 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Spring Boot, Spring Security(OAuth2/JWT), Spring Data JPA, MySQL, Redis, Flyway |
| Infra | Docker Compose, Nginx, AWS S3(LocalStack), Prometheus, Grafana |
| Test | JUnit 5, Testcontainers, k6 |

## 아키텍처

```
클라이언트 ── Nginx(리버스 프록시, WebSocket 포함)
                    │
              Spring Boot
              ├── MySQL   (영속 데이터)
              ├── Redis   (세션/캐시/실시간 편집 버퍼)
              └── S3      (파일 저장)
```

## 프로젝트 구조

```
com.motivhub.be
├── auth          # 인증/인가 — OAuth2, JWT, 이메일 회원가입·로그인
├── user          # 유저 프로필, 닉네임, 탈퇴
├── workspace     # 워크스페이스, 멤버, 초대
├── task          # 태스크, 체크리스트, 댓글, 활동 로그
├── issue         # 이슈 게시판
├── file          # 워크스페이스 파일 공유
├── notification  # 알림 생성·조회·설정
├── realtime      # WebSocket(STOMP), 실시간 공동편집, 프레즌스
└── global        # 공통 설정, 예외 처리
```

## API 문서

로컬 실행 후 `http://localhost:8080/swagger-ui.html`에서 확인할 수 있습니다.

## 트러블슈팅

개발 중 겪은 문제와 원인·해결 과정은 [`docs/troubleshooting.md`](docs/troubleshooting.md)에 기록되어 있습니다.

---

## 로컬 실행

### 1. 환경 변수 설정

앱이 기동하려면 아래 환경 변수가 필요합니다. Spring Security가 기동 시점에 OAuth2 클라이언트 설정 값을 검증하므로, 로컬 개발에서는 실제 값이 아니어도 비어 있지 않은 임의의 문자열이면 됩니다.

- `JWT_SECRET`: 32자 이상의 임의 문자열
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`

### 2. 인프라 컨테이너 기동

```bash
docker compose up -d
```

MySQL, Redis, Prometheus, Grafana가 함께 기동됩니다. 기동 후 서비스 접속 정보는 다음과 같습니다.

- MySQL: `localhost:13306` (이 개발 환경에 이미 기본 포트(3306)를 쓰는 네이티브 MySQL이 있어 포트를 옮겼습니다)
- Redis: `localhost:6379`
- Prometheus UI: `localhost:9090`
- Grafana: `localhost:13000` (admin/admin) — 프런트엔드 개발 서버와의 기본 포트(3000) 충돌을 피하기 위해 옮겼습니다

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

`localhost:8080`에서 앱이 실행됩니다.

> `/actuator/health`, `/actuator/prometheus`는 로컬 개발 편의를 위해 인증 없이 노출되어 있습니다. 실제 배포 환경에는 그대로 가져가면 안 됩니다.

### 4. (선택) 로컬에서 파일함 기능 손으로 테스트하기

`docker compose up -d`로 함께 뜨는 LocalStack(`localhost:4566`)이 S3를 흉내낸다. 버킷을 한 번 만들어야 한다:

```bash
docker compose exec localstack awslocal s3 mb s3://motivhub-local
```

앱을 아래 환경변수로 기동하면 파일함 API가 LocalStack을 보게 된다(자격증명 값은 아무 문자열이어도 됨 —
LocalStack은 검증하지 않는다):

```bash
AWS_S3_ENDPOINT_OVERRIDE=http://localhost:4566 AWS_S3_BUCKET=motivhub-local \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test ./gradlew bootRun
```

### 부하테스트 (k6)

```bash
docker compose up -d
docker compose exec -T mysql mysql -uroot -proot motivhub < load-test/seed-users.sql
docker compose exec localstack awslocal s3 mb s3://motivhub-local
JWT_SECRET=k6loadtestdevsecretexactly32byte \
AWS_S3_ENDPOINT_OVERRIDE=http://localhost:4566 AWS_S3_BUCKET=motivhub-local \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
GOOGLE_CLIENT_ID=dummy GOOGLE_CLIENT_SECRET=dummy \
GITHUB_CLIENT_ID=dummy GITHUB_CLIENT_SECRET=dummy \
KAKAO_CLIENT_ID=dummy KAKAO_CLIENT_SECRET=dummy \
./gradlew bootRun
k6 run -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/protected-api-load-test.js
```

> `bootRun`은 위 "로컬 실행" 섹션의 필수 환경 변수(OAuth 클라이언트, `JWT_SECRET`)를 전부 요구하고, 파일함 기능 도입 이후로는 `AWS_S3_BUCKET`도 기동 시점에 `WorkspaceFileService`가 검증한다 — 빠뜨리면 `bootRun`이 즉시 실패한다.

> 위 "로컬 실행"의 `JWT_SECRET`은 32자 이상이면 되지만, 부하테스트에서는 반드시 이 값을 정확히 그대로 사용해야 한다. `Keys.hmacShaKeyFor()`가 시크릿 바이트 길이로 서명 알고리즘(HS256/384/512)을 정하기 때문에, k6와 앱이 다른 값을 쓰면 토큰이 401로 거부된다.

부하를 주는 동안 `http://localhost:13000`의 Grafana 대시보드에서 TPS/p95/JVM 힙/HikariCP 커넥션 변화를 관찰할 수 있다.

### 부하테스트 확장 — 읽기 위주 신규 기능 API

워크스페이스 태스크/파일함/이슈게시판까지 포함한 확장 시나리오. 위 "부하테스트(k6)"의 인프라 기동·시드
유저·앱 기동을 그대로 마친 뒤, 시드 데이터를 하나 더 넣고 두 시나리오를 실행한다.

```bash
docker compose exec -T mysql mysql -uroot -proot motivhub < load-test/seed-read-heavy-data.sql

# K6_WEB_DASHBOARD=true를 붙이면 실행 중 http://127.0.0.1:5665 에서 k6 자체 라이브 대시보드(TPS/응답시간/VU)를
# 볼 수 있다. http://localhost:13000(Grafana, admin/admin)과 나란히 열어두면 클라이언트/서버 양쪽을 동시에 관찰 가능.

mkdir -p load-test/results

# 시나리오 1: 일반 혼합 (태스크 목록/상세, 파일함 목록, 이슈 목록/상세) — VU 10 -> 20 -> 30, 레벨별 1분씩
K6_WEB_DASHBOARD=true k6 run -u 10 -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/mixed-read-load-test.js --summary-export=load-test/results/mixed-vu10.json
K6_WEB_DASHBOARD=true k6 run -u 20 -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/mixed-read-load-test.js --summary-export=load-test/results/mixed-vu20.json
K6_WEB_DASHBOARD=true k6 run -u 30 -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/mixed-read-load-test.js --summary-export=load-test/results/mixed-vu30.json

# 시나리오 2: 극단 케이스(체크리스트/댓글/활동로그 300개씩 달린 태스크 상세만) — VU 5 -> 10 -> 15
K6_WEB_DASHBOARD=true k6 run -u 5  -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/heavy-task-stress-test.js --summary-export=load-test/results/heavy-vu5.json
K6_WEB_DASHBOARD=true k6 run -u 10 -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/heavy-task-stress-test.js --summary-export=load-test/results/heavy-vu10.json
K6_WEB_DASHBOARD=true k6 run -u 15 -d 1m -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/heavy-task-stress-test.js --summary-export=load-test/results/heavy-vu15.json
```

레벨을 하나씩 올려가며 실패율/p95가 어떻게 변하는지 비교한다 — 실패율이 0%를 벗어나거나 p95가 전
단계 대비 2배 이상 뛰면 그 지점을 병목 시작점으로 본다. 발견 사항은 `docs/troubleshooting.md`에 기록한다.