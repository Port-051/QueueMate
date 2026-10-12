# QueueMate — 앱 · 랜딩 · 백엔드

조건 기반 게임 팀원 매칭 서비스. 앱 프런트, 랜딩과 세 백엔드를 함께 관리한다.

| 폴더 | 무엇 | 가져온 커밋 |
|---|---|---|
| `frontend/` | 웹 프런트(React · Vite) | `acd8ab0` |
| `platform/` | API 서버 — 계정 · 소셜 로그인 · 게임 프로필 · 모집 게시판 · 방 · 친구/차단/신고 (Spring Boot, 8082) | `26d5925` |
| `matching/` | 매칭 엔진 — 요청 · 배정 · 제안 · 확정 (Spring Boot + Redis Lua, 8080) · 결정 로그 `matching/docs/11_DECISION_LOG.md` | `93131a2` |
| `notification/` | 알림 배달 — SSE (Spring Boot, 8081) | `4043112` |

각 폴더의 `START_HERE.md` → `CLAUDE.md` 순으로 읽는다. platform 의 계약은 `platform/contracts/platform-api.md` 다.

앱(`app.queue-mate.com`)과 랜딩(`queue-mate.com`)은 `main`의 CI 검증을 통과한 빌드를 배포한다. `landing/`은 정적 랜딩 소스이며, 두 사이트의 공통 로고 원본은 `frontend/public/brand/queuemate-mark.svg`다. 인증 설정과 배포·검증·복구 절차는 [배포 운영 문서](infra/deploy/README.md)를 참고한다.
