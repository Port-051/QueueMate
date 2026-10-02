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
npm run e2e          # 실제 백엔드 e2e(e2e/ 01~11) — Windows 사본에서만 돌린다. WSL 에서 돌리지 않는다(START_HERE.md §6 · 루트 START_HERE.md §10.4)
```

mock 서버 · `dev:mock` · `dev:rooms` 는 2단계(2026-09-28)에 지웠다 — 화면을 보려면 백엔드를 띄운다. Playwright e2e 는 2026-09-30 에 mock 이 아니라 **실제 백엔드 셋에 붙는 시나리오 e2e**(`e2e/*.spec.ts` · `CLAUDE.md` §3-21)로 돌아왔다 — e2e 는 서버를 띄우지 않으니 백엔드 셋(platform 은 `DEV_LOGIN_ENABLED=true`) · 5173 을 먼저 띄워 둔다. `build` · `typecheck` 는 2 · 3 · 4 · 5단계 커밋마다 이 자리(OneDrive 아래 WSL)에서 통과했다. **dev 서버는 띄웠으면 반드시 내린다**(`ss -ltnp | grep :5173` → `kill <PID>`. `pkill -f` 금지).

## 지금 어디까지

**1단계(가져오기 · 빌드) · 2단계(인증 · 전송) · 3단계(게임 계정 · 매칭) · 4단계(게시판 · 방) · 5단계(소셜) 끝 — 통합 다섯 단계가 다 됐다(2026-09-29).** 쿠키 `qm_access` 인증 · 소셜 전용 로그인(`/login` — 카카오 · Discord · Google 버튼 셋 + 최근 사용 배지 · dev 서버에서만 개발용 로그인(`TEMP-DEV-LOGIN`) — 2026-09-29 · `/signup/social` · `/settings`) · 프록시 셋 · SSE `GET /api/v1/events` · 게임 설정 정적 상수(`src/domain/gameCatalog.ts` — 원본은 `matching/seed/gameconfig.redis`) · 게임 계정(`PUT /users/me/game-accounts/{game}` · 온보딩 · 내 정보) · 빠른매치(화면 이름 — `CLAUDE.md` §3-33 · 필터 줄 끝 [빠른매치] 창의 "빠른매치 시작" = `POST /posts/auto-join` → 404 면 `POST /match-requests` · heartbeat · 제안 · `MATCH_CONFIRMED` → `POST /match-parties/{partyId}/room` · 상태 조회 `GET /match-requests` 는 알림 · SSE 재연결 · 탭 복귀 때만 — 폴링 없음, §3-36)· **게시판 = 방 카드 보드**(`GET /posts?game=` · `POST /posts` · 입장 `POST /rooms/{roomId}/members` · `BOARD_CHANGED` 는 처음 온 신호부터 1.5초 ± 0.3초로 묶기) · **내 방**(`GET /rooms/me` · heartbeat 1분 · `ROOM_*` — `state/RoomSessionContext.tsx`) · **방 화면 `/app/party/:roomId`**(게시판 방 · 빠른매치 방 같은 화면 — 멤버 · 나가기 · 강퇴 · 확정 · 글 지우기 · WebRTC 음성/채팅 — 글 고치기(`PATCH /posts/{postId}`)는 없어졌다 · 보내면 405 · 2026-10-01 소유자 결정 — platform P-45)· **친구 · 차단 · 신고 · 최근 함께한 사람**(`state/SocialContext.tsx` · 왼쪽 레일 "친구" 의 친구 화면 `/app/friends?tab=` · `ReportModal` · `FRIEND_*` 로 재조회 · 사람 검색 없음 — 사용자 번호를 넣는다)이 우리 백엔드 모양이다. 예약 · 듀오 제안 · `LegacyRecruitmentHome` 은 대응물 없이 남아 있다(라우트 밖 · 연결하지 않음 — DM · 알림함 · 아바타 업로드는 2026-10-02 에 지웠다, `CLAUDE.md` §3-39 · 42). **3 · 4 · 5단계의 흐름은 2026-09-30 에 실제 백엔드 e2e 가 처음 돌렸다**(`START_HERE.md` §1 "확인") — 남은 것은 `START_HERE.md` §5. 무엇을 어떤 순서로 바꾸는지는 `START_HERE.md` §2, 경로 하나하나의 대조는 `START_HERE.md` §3 이다. 실제 소셜 로그인은 카카오 · 디스코드 · Google 앱 키가 없어 아직 브라우저에서 못 해 본다 — 그동안은 개발용 로그인(백엔드 `DEV_LOGIN_ENABLED=true` · `START_HERE.md` §6)으로 들어간다(백엔드 쪽은 platform `DevLoginController` 에 있다 — e2e 도 이 길로 사람을 만든다).
