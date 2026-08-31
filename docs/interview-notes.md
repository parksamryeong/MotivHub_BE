# 개발 회고 노트 (면접 대비)

개발하면서 발견한 문제와 해결 과정을 기록한다. 사소한 것은 넣지 않고, 면접에서 "문제 → 원인 → 해결 → 결과"로 설명할 만한 것만 남긴다.

형식:

## [날짜] 문제 제목

- **상황**: 무엇을 하다가 발견했는지
- **원인**: 왜 그런 문제가 생겼는지
- **해결**: 어떻게 고쳤는지
- **결과**: 수치 변화가 있다면 (TPS, 응답시간, 에러율 등)

---

## [2026-08-27] 첫 부하테스트 — 보호 API 기준선 확보

- **상황**: Docker Compose + Prometheus + Grafana로 관측 인프라를 구축한 뒤, k6로 `GET /api/users/me`, `GET /api/users/me/mypage`에 ramping 부하(0→50명→0, 3분)를 줘서 실제로 지표가 움직이는지, 그리고 지금 시점 성능이 어느 정도인지 확인했다.
- **원인**: (문제 상황 아님 — 최초 기준선 측정)
- **해결**: (해당 없음)
- **결과**:
  - 총 11,864 요청, 실패 0건 (성공률 100%)
  - 처리량 약 65.6 req/s (VU 50명 유지 구간)
  - 응답시간: 평균 10ms, p95 13.83ms, 최대 217.43ms
  - Grafana 대시보드에서 TPS/JVM 힙 패널이 부하 구간과 정확히 같은 모양(상승→유지→하강)으로 실시간 반영되는 것 확인
  - 현재 규모(조회 API 2개, 50 VU)에서는 병목이 전혀 보이지 않음 — 이후 기능이 늘어나거나 VU를 더 올렸을 때 이 수치와 비교해 성능 저하 여부를 판단할 기준선으로 사용

---

## [2026-08-30] 프론트엔드 토큰 저장 전략 결정

- **상황**: 프론트(MotivHub_FE)에서 로그인 플로우를 붙이면서, `/api/auth/exchange`·`/api/auth/refresh`로 받은 accessToken/refreshToken을 어디에 저장할지 결정해야 했다. 후보는 (1) 둘 다 localStorage (2) accessToken은 메모리, refreshToken은 localStorage (3) refreshToken을 httpOnly 쿠키로.
- **원인**: (문제 상황 아님 — 설계 결정)
- **해결**:
  - **최종 선택은 (2) accessToken 메모리 보관 + refreshToken localStorage.** XSS가 발생해도 메모리에 있는 accessToken은 새로고침 시 사라지므로 탈취 후 악용 가능한 시간 창이 짧아진다. 대신 앱 진입 시 refresh 1회로 accessToken을 재발급받는 구조가 필요하다.
  - **(3) httpOnly 쿠키 방식은 채택하지 않음.** 이유는 보안이 아니라 백엔드 제약: 현재 `SecurityConfig`의 CORS 설정이 `allowCredentials: false`(쿠키 미사용, 토큰을 body/헤더로만 주고받는 구조)라서, 쿠키 기반으로 가려면 백엔드의 CORS·쿠키 발급(SameSite/Secure 속성 포함) 로직을 같이 바꿔야 한다. 지금 범위에서는 프론트 단독 변경만으로 완결되는 (2)를 택했다.
- **결과**: (수치 변화 없음 — 설계 결정 기록)
  - 트레이드오프 요약: (1) 둘 다 localStorage는 구현이 가장 단순하지만 XSS 시 refreshToken까지 영구 탈취되는 위험이 가장 크다. (2)는 구현 복잡도가 조금 늘지만(메모리 상태 관리 + 앱 진입 시 silent refresh) XSS 노출 창을 줄인다. (3) httpOnly 쿠키가 이론적으로 가장 안전하지만 지금 아키텍처(백엔드가 토큰을 body로 응답, CORS `allowCredentials: false`)와 맞지 않아 채택하지 않았다 — 향후 진짜 프로덕션으로 갈 때 재검토 대상.

---