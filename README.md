# QueueMate — 프런트 + 백엔드 셋 (통합 브랜치)

조건 기반 게임 팀원 매칭 서비스. 이 브랜치는 네 배포 단위를 폴더 넷으로 모은 것이다(각 폴더의 커밋 이력이 그대로 이어져 있다).

| 폴더 | 무엇 | 가져온 커밋 |
|---|---|---|
| `frontend/` | 웹 프런트(React · Vite) | `acd8ab0` |
| `platform/` | API 서버 — 계정 · 소셜 로그인 · 게임 프로필 · 모집 게시판 · 방 · 친구/차단/신고 (Spring Boot, 8082) | `26d5925` |
| `matching/` | 매칭 엔진 — 요청 · 배정 · 제안 · 확정 (Spring Boot + Redis Lua, 8080) · 결정 로그 `matching/docs/11_DECISION_LOG.md` | `93131a2` |
| `notification/` | 알림 배달 — SSE (Spring Boot, 8081) | `4043112` |

각 폴더의 `START_HERE.md` → `CLAUDE.md` 순으로 읽는다. platform 의 계약은 `platform/contracts/platform-api.md` 다.
