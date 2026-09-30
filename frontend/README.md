# QueueMate 프런트 (`frontend/` · 브랜치 `frontend`)

QueueMate 의 **브라우저 앱**이다. React 18 · react-router 6 · TypeScript · Vite 5 · npm. 개발 서버 **5173**.

- **원본** — `Port-051/QueueMate` 브랜치 **`codex/room-card-board`**(커밋 `7ee7177`, 2026-09-28)의 `frontend/` 를 2026-09-28 에 그대로 가져왔다(`main` f860ada 보다 90커밋 앞 — **방 카드 보드**가 더해진 갈래다). 원본의 README 는 `docs/UPSTREAM_README.md` 에 있다 — 그 파일은 **원본 백엔드**(8080 하나 · bearer 토큰 · WebSocket)를 전제로 쓰였고 **이 작업 공간의 백엔드와 다르다.**
- **이 폴더의 규칙은 `CLAUDE.md`, 지금 상태와 계획은 `START_HERE.md`.** 백엔드 계약은 옆 폴더 — `platform/contracts/platform-api.md` · `matching/contracts/openapi.yaml` · `events.md`.
- 이 폴더는 git **고아 브랜치 `frontend`** 의 worktree 다(`matching/.git` 이 본 저장소). 커밋은 WSL 에서, 경로를 명시해서 한다(루트 `CLAUDE.md` §4).

## 명령

```bash
npm ci
npm run dev          # 5173 — 프록시 셋: /api/v1/events → 8081, /api/v1/match-requests · /api/v1/proposals → 8080, 나머지 /api → 8082 (백엔드 셋을 먼저 띄운다 — 루트 START_HERE.md §6)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
```

mock 서버 · Playwright e2e · `dev:mock` · `dev:rooms` 는 2단계(2026-09-28)에 지웠다 — 화면을 보려면 백엔드를 띄운다. `build` · `typecheck` 는 2 · 3 · 4 · 5단계 커밋마다 이 자리(OneDrive 아래 WSL)에서 통과했다. **dev 서버는 띄웠으면 반드시 내린다**(`ss -ltnp | grep :5173` → `kill <PID>`. `pkill -f` 금지).

## 지금 어디까지

**1단계(가져오기 · 빌드) · 2단계(인증 · 전송) · 3단계(게임 계정 · 매칭) · 4단계(게시판 · 방) · 5단계(소셜) 끝 — 통합 다섯 단계가 다 됐다(2026-09-29).** 쿠키 `qm_access` 인증 · 소셜 전용 로그인(`/login` — 카카오 · Discord · Google 버튼 셋 + 최근 사용 배지 · dev 서버에서만 개발용 로그인(`TEMP-DEV-LOGIN`) — 2026-09-29 · `/signup/social` · `/settings`) · 프록시 셋 · SSE `GET /api/v1/events` · 게임 설정 정적 상수(`src/domain/gameCatalog.ts` — 원본은 `matching/seed/gameconfig.redis`) · 게임 계정(`PUT /users/me/game-accounts/{game}` · 온보딩 · 내 정보) · 매칭("자동 매칭 시작"(2026-09-29 까지 "매칭 시작") = `POST /posts/auto-join` → 404 면 `POST /match-requests` · `GET /match-requests` 폴링 · heartbeat · 제안 · `MATCH_CONFIRMED` → `POST /match-parties/{partyId}/room`)· **게시판 = 방 카드 보드**(`GET /posts?game=` · `POST /posts` · 입장 `POST /rooms/{roomId}/members` · `BOARD_CHANGED` 2.5초 묶기) · **내 방**(`GET /rooms/me` · heartbeat 1분 · `ROOM_*` — `state/RoomSessionContext.tsx`) · **방 화면 `/app/party/:roomId`**(게시판 방 · 자동 매칭 방 같은 화면 — 멤버 · 나가기 · 강퇴 · 확정 · 글 고치기/지우기 · WebRTC 음성/채팅)· **친구 · 차단 · 신고 · 최근 함께한 사람**(`state/SocialContext.tsx` · 메시지 화면의 친구 관리 패널 `?manage=` · `ReportModal` · `FRIEND_*` 로 재조회 · 사람 검색 없음 — 사용자 번호를 넣는다)이 우리 백엔드 모양이다. 예약 · DM(대화는 localStorage) · 듀오 제안 · 아바타 · 알림함 · `LegacyRecruitmentHome` 은 대응물 없이 남아 있다(라우트 밖 · 연결하지 않음). **실제로 띄워 본 흐름은 아직 없다** — 남은 것은 `START_HERE.md` §5. 무엇을 어떤 순서로 바꾸는지는 `START_HERE.md` §2, 경로 하나하나의 대조는 `START_HERE.md` §3 이다. 실제 소셜 로그인은 카카오 · 디스코드 · Google 앱 키가 없어 아직 브라우저에서 못 해 본다 — 그동안은 개발용 로그인(백엔드 `DEV_LOGIN_ENABLED=true` · `START_HERE.md` §6)으로 들어간다(백엔드 쪽은 2026-09-29 기준 아직 없다).
