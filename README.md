# MotivHub_BE

## 로컬 실행

### 1. 환경 변수 설정

앱이 기동하려면 아래 환경 변수가 필요합니다. Spring Security가 기동 시점에 OAuth2 클라이언트 설정 값을 검증하므로, 로컬 개발에서는 실제 값이 아니어도 비어 있지 않은 임의의 문자열이면 됩니다.

- `JWT_SECRET`: 32자 이상의 임의 문자열
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`
- `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`

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
NAVER_CLIENT_ID=dummy NAVER_CLIENT_SECRET=dummy \
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