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

### 부하테스트 (k6)

```bash
docker compose up -d
docker compose exec -T mysql mysql -uroot -proot motivhub < load-test/seed-users.sql
JWT_SECRET=k6loadtestdevsecretexactly32byte ./gradlew bootRun
k6 run -e JWT_SECRET=k6loadtestdevsecretexactly32byte load-test/protected-api-load-test.js
```

> 위 "로컬 실행"의 `JWT_SECRET`은 32자 이상이면 되지만, 부하테스트에서는 반드시 이 값을 정확히 그대로 사용해야 한다. `Keys.hmacShaKeyFor()`가 시크릿 바이트 길이로 서명 알고리즘(HS256/384/512)을 정하기 때문에, k6와 앱이 다른 값을 쓰면 토큰이 401로 거부된다.

부하를 주는 동안 `http://localhost:13000`의 Grafana 대시보드에서 TPS/p95/JVM 힙/HikariCP 커넥션 변화를 관찰할 수 있다.