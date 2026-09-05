# 워크스페이스 & 태스크

칸반보드 형태의 프로젝트 관리 기능. 워크스페이스(팀/개인 공간) 하나에 태스크(칸반 카드) 여러 개가 속한다.
개인용/팀용을 따로 나누지 않고 통합 — 멤버가 1명이면 개인용, 여러 명이면 팀용으로 자연스럽게 동작한다.

## 구조

백엔드만 구현됨(프론트 미구현). `auth`, `user` 옆에 신설된 두 패키지:

```
com.motivhub.be
├── workspace/
│   ├── controller/     (WorkspaceController, WorkspaceInviteController)
│   ├── service/        (WorkspaceService, WorkspaceInviteService, WorkspaceInviteMailService)
│   ├── repository/     (WorkspaceRepository, WorkspaceMemberRepository, WorkspaceInviteRepository)
│   ├── domain/         (Workspace, WorkspaceMember, WorkspaceRole, WorkspaceInvite)
│   ├── dto/
│   └── exception/
├── task/
│   ├── controller/     (TaskController, TaskCommentController)
│   ├── service/        (TaskService, TaskCommentService, TaskExpirationScheduler)
│   ├── repository/     (TaskRepository, TaskAssigneeRepository, TaskCommentRepository)
│   ├── domain/         (Task, TaskStatus, TaskAssignee, TaskComment)
│   ├── dto/
│   └── exception/
```

`task` 패키지는 권한 판단(멤버 여부, OWNER 여부)을 위해 `workspace.service.WorkspaceService`의 public 메서드
(`getWorkspace`, `getMembership`, `requireOwner`)를 그대로 재사용한다 — `auth`↔`user`가 `RefreshTokenService`를
공유하는 것과 같은 패턴.

## 워크스페이스

### 역할과 권한
- **OWNER / MEMBER** 2단계만 (ADMIN 같은 중간 역할 없음)
- 워크스페이스 삭제: OWNER, 멤버가 있어도 이전 없이 가능(강한 확인은 프론트 책임)
- 나가기: MEMBER는 자유, OWNER는 혼자면 자유(=삭제와 동일), 다른 멤버가 있으면 오너십 이전 후에만 가능
- 멤버 추방/오너십 이전: OWNER만

### 초대
- **링크형**(누구나 재사용 가능) / **이메일형**(Gmail SMTP 발송) 둘 다 지원, 하나의 `WorkspaceInvite` 엔티티로 통합(`email` 필드가 null이면 링크형)
- 둘 다 **1회용 아님** — 만료(7일) 또는 OWNER의 명시적 무효화 전까지 여러 명이 같은 토큰으로 계속 참여 가능
- 수락 시 **토큰 소유만 확인**, 이메일 주소와 실제 로그인 계정의 이메일을 매칭하지 않음(어떤 소셜 로그인으로 수락하든 무관)

## 태스크 (칸반보드)

### 상태
`WAITING` → `IN_PROGRESS` → `DONE` (자유롭게 이동), 그리고 `EXPIRED`(만료):
- `TaskExpirationScheduler`가 매일 자정 `WAITING`/`IN_PROGRESS`이면서 마감일 지난 태스크를 자동으로 `EXPIRED`로 전환 (`DONE`은 안 건드림)
- `EXPIRED`는 직접 상태변경으로 진입/탈출 불가 — OWNER가 `updatePeriod`로 마감일을 오늘 이후로 연장하면 자동으로 `WAITING`으로 복귀

### 권한 매트릭스

| 액션 | 권한 |
|---|---|
| 태스크 생성 | 멤버 누구나 |
| 이름/설명 수정, 상태 변경, 담당자 추가/제거 | 담당자(들) + OWNER |
| 기간(시작일/마감일) 수정 | **OWNER 전용** — 멤버가 임의로 마감일을 늘려 책임 추적을 무디게 하는 것 방지 |
| 삭제 | 만든 사람 + OWNER (단, `EXPIRED` 상태면 OWNER만) |
| 댓글 | 멤버 누구나 (가장 개방적인 액션) |

담당자는 한 태스크에 여러 명 지정 가능(`TaskAssignee` 다대다).

## 알려진 이슈 / 이력

구현 중 발견되고 고쳐진 버그들(IDOR, FK cascade, soft-delete 필터링 누락 등)은 `docs/troubleshooting.md` 참고.

## 관련 문서

- 설계: `docs/superpowers/specs/2026-09-02-workspace-task-design.md` (로컬 전용, 커밋 안 됨)
- 비범위(다음 서브프로젝트에서 다룸): 트러블슈팅 게시판, 대시보드 UI, 파일 업로드, 랭킹, 워크스페이스 내 다중 보드
