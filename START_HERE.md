# START_HERE — frontend 지금 상태 · 통합 계획 · API 대조

규칙은 `CLAUDE.md`, 전체 그림은 루트 `START_HERE.md`. 이 파일은 **어디까지 됐고 무엇을 어떤 순서로 바꾸는지**만 담는다. 기준일은 2026-09-28 이다.

---

## 1. 지금 상태

| 무엇 | 상태 |
|---|---|
| 원본 | `Port-051/QueueMate` **`codex/room-card-board` 7ee7177**(2026-09-28)의 `frontend/` · 277 파일. `main`(f860ada) 과의 merge-base 는 f860ada — 즉 **main 전부 + 방 카드 보드 90커밋**이다(§4). 커밋 `14946a9` |
| 가져오며 한 것 | 원본 README → `docs/UPSTREAM_README.md`. `.gitignore` 에 `node_modules/ dist/ .vite/ *.log .env*(.env.example 예외) .DS_Store` 보탬. `node_modules` · `dist` 제외. 원본의 `contracts/`(`rooms.openapi.yaml` · `openapi.yaml` · `events.md`)는 **가져오지 않았다** — 우리 계약이 아니다(§4 에 방 API 모양만 요약) |
| 확인 | `npm ci`(75 packages · 8초) · `npm run build`(tsc -b + vite build · 3.6초) · `npm run typecheck` **전부 통과**(2026-09-28, OneDrive 아래 WSL). dev 서버는 띄우지 않았다 |
| 코드 | **원본 그대로다 — 우리 백엔드와 통하지 않는다.** 토큰 `localStorage['qm.tokens']` + `Authorization: Bearer`, 이메일 가입 · 로그인, `/ws` WebSocket, 프록시 `/api` → 8080 하나, `VITE_API_MODE` 기본이 **mock**(브라우저 안 가짜 서버) |
| 단계 | **1단계(가져오기 · 빌드) ✅.** 2단계부터는 이 브랜치 위에서 새로 한다(§2) |

### 원본 프런트의 모양(가져온 그대로 — 조사 2026-09-28)

- 라우트(`src/App.tsx`) — 공개 `/`(랜딩) · `/login` · `/signup` · `/auth/callback`(OAuth code 교환) · 인증 뒤 `/onboarding`(게임 계정 연결) · `/app`(`AppShell` — `RequireAuth` > `RequireOnboarding` > `RequireGameCatalog`) 아래 `home` · `match` · `match/waiting/:requestId` · `reservations` · `reservations/new`(넷은 홈으로 redirect) · `proposals/:proposalId` · `party` · `party/:partyId` · `messages` · `friends` · `recent` · `me` · `settings`.
- 홈(`src/pages/HomePage.tsx` 9줄) — `USE_MOCK && VITE_HOME_LAYOUT !== 'legacy' || VITE_HOME_LAYOUT === 'rooms'` 이면 **`RoomBoardHome`(방 카드 보드)**, 아니면 **`LegacyRecruitmentHome`**(main 의 옛 `HomePage` 를 이름만 바꾼 것 — 모집 게시판 `/recruitments/*` · 매칭 컴포저 · 제안 · 예약). **real 모드의 기본 홈은 legacy 다.**
- HTTP(`src/api/http.ts` · `src/config.ts`) — `API_BASE = VITE_API_BASE || '/api/v1'`, 401 이면 `POST /auth/refresh {refreshToken}` 한 번(single-flight) 뒤 재시도, 실패하면 토큰을 지우고 `AuthContext` 가 로그아웃. mock 모드면 `src/mocks/server`(in-memory · `[method, regex, handler]` 표 · MSW 아님)로 간다.
- 실시간(`src/api/ws.ts`) — `ws(s)://<host>/ws`, 서브프로토콜 `['queuemate.v1', 'bearer.<token>']`, 봉투 `{type, eventId, occurredAt, payload}`, 지수 백오프 15초 상한, 연결 직후 `SESSION_SNAPSHOT`. 받는 type 14개(§3 끝).
- 게임 설정(`src/domain/gameConfig.ts`) — 모드는 하드코딩하지 않고 `GET /games` · `GET /games/{g}/match-schema` 에서 받는다(`RequireGameCatalog`). 라벨만 상수(`SOLO_DUO_RANKED` · `NORMAL_DRAFT` · `SWIFTPLAY` · `ARAM` · `FLEX_RANKED` · `COMPETITIVE` · `UNRATED` · `DUO` · `SQUAD`). 티어는 `src/domain/recruitment.ts` `tiers(game)`(디비전 없는 이름 — `GOLD`). 핵심 조건 값 LoL `TOP/JUNGLE/MID/ADC/SUPPORT/ANY` · VALORANT 4역할군 · **PUBG `PLAY_STYLE`(`AGGRESSIVE/BALANCED/SURVIVAL`)**. 음성 `REQUIRED/OPTIONAL/NO_VOICE`.
- 프런트 안에서만 사는 것(localStorage) — DM(`qm:direct-messages:*`) · 알림함(`qm:notifications:*`) · 듀오 제안(메모리 · mock 전용) · 자기소개(`domain/introduction.ts`) · 방 카드 보드의 mock 방(`qm:room-board:v1:<userId>`).
- 테스트 — `e2e/` 35 spec + `helpers.ts`(전부 **mock 모드** 전제 · 데모 계정) · `e2e-live/shared-rooms.spec.ts`(원본 백엔드 파일럿 전제) · `tests/*.spec.ts` 5개(Playwright 러너를 브라우저 없이 단위 테스트로 — rooms 의 순수 함수).

---

## 2. 통합 계획 — 5단계

옛 시도(`main` 기반)와 같은 나눔이다. **2 · 3단계는 이 브랜치 위에서 새로 한다**(옛 커밋을 옮겨 오지 않는다 — 브랜치를 지웠다). 단계마다 `npm run build` · `npm run typecheck` 통과 → 커밋. 확인은 그 둘만이다(소유자 결정 — 실제 소셜 로그인은 앱 키가 없어 못 해 본다).

| 단계 | 무엇 | 바꾸는 자리(원본 기준) | 계약 |
|---|---|---|---|
| **1** ✅ | 가져오기 · 빌드 · 문서 셋 | — | — |
| **2** | **인증 · 전송** — ① 쿠키 인증: `Authorization` 헤더 · `qm.tokens` · `TokenResponse` 를 지우고 `fetch` 는 같은 출처(프록시)라 쿠키가 저절로 붙는다. 401 → `POST /api/v1/auth/refresh`(본문 없음) 한 번 → 재시도, 실패면 로그아웃 상태(single-flight 는 남긴다). 만료 전 선제 재발급은 미정(§5) ② **소셜 전용 로그인**: `/login` 은 카카오 · 디스코드 버튼 둘(`window.location.assign('/api/v1/auth/oauth/KAKAO/start')`) · `/signup` 과 `/auth/callback` 은 없어지고 **`/signup/social`**(`GET /auth/social/pending` → 닉네임 하나 → `POST /auth/social/signup {nickname}`) · **`/login?error=OAUTH_FAILED`** · **`/settings?linked=…` · `/settings?error=SOCIAL_ALREADY_LINKED\|PROVIDER_ALREADY_LINKED`** 라우트를 만든다. 로그아웃 `POST /auth/logout`(본문 없음). `GET /users/me` 는 `{userId, nickname, createdAt, socialProviders, gameAccounts}` ③ **프록시 셋**(`vite.config.ts`): `/api/v1/match-requests` · `/api/v1/proposals` → 8080, `/api/v1/events` → 8081, `/api` → 8082. `/ws` 프록시 삭제 ④ **SSE**: `src/api/ws.ts` → `EventSource('/api/v1/events')`, `onmessage` 로 봉투, `heartbeat` 는 이름 있는 이벤트(감시 60초쯤), `retry:` 는 서버가 준다, 401 로 닫히면(`readyState === CLOSED`) 재발급 → 새 `EventSource`. 받는 type 목록을 우리 것으로(§3 끝) ⑤ **게임 설정 정적 상수**: `GET /games` · `match-schema` · `RequireGameCatalog` 를 없애고 seed 사본 상수로(모드 키 · 정원 · `tierRule` · 티어 사다리 · 포지션 값 — 자리와 모양은 §5) ⑥ **mock · e2e 삭제**: `src/mocks/*` · `USE_MOCK`/`API_MODE` 분기 · `e2e/` · `e2e-live/` · `playwright*.config.ts` · `dev:mock*` · `dev:rooms` · `VITE_ROOM_DEMO`. `tests/` 는 §5 | `src/api/http.ts` · `config.ts` · `ws.ts` · `types.ts` · `state/AuthContext.tsx` · `pages/AuthPage.tsx` · `AuthCallbackPage.tsx` · `components/SocialLoginButtons.tsx` · `RequireGameCatalog.tsx` · `domain/gameConfig.ts` · `mocks/*` · `vite.config.ts` · `package.json` · `App.tsx` | `platform-api.md` "공통" · "access 토큰" · "refresh 토큰" · "소셜 로그인" · "잇기 · 끊기" · `notification/CLAUDE.md` §5 · `matching/seed/gameconfig.redis` |
| **3** | **게임 계정 · 매칭** — ① 게임 계정: `POST /users/me/game-accounts {game, externalGameId, region}` → **`PUT /users/me/game-accounts/{LOL\|VALORANT\|PUBG}`**(LOL `{gameNickname, mainPosition}` — 티어는 Riot 이 채운다 · VALORANT `{gameNickname, tier, mainPosition}` · PUBG `{gameNickname, tier, server}`), 응답은 **게임 프로필**(`tier` · `mainPosition` · `server` · `verified` · `stats` · `syncedAt`). `DELETE …/{id}` → `DELETE …/{game}`. 목록은 `GET /users/me` 의 `gameAccounts`(따로 `GET …/game-accounts` 없음). 전적 갱신 `POST …/{game}/refresh`(429 `TOO_MANY_STATS_REFRESHES` + `Retry-After`). **온보딩 = 게임 계정 화면으로 전환**(소유자 결정 — 없으면 보내되 막지 않는다) ② 매칭: "매칭 시작" → 먼저 **`POST /api/v1/posts/auto-join`**(platform · 같은 본문) → 404 `NO_MATCHING_POST` 면 **`POST /api/v1/match-requests`**(matching). 본문 `{game, modeKey, tier?, keyCondition{type: POSITION\|ROLE\|PLATFORM, value}, voicePreference: REQUIRED\|NO_VOICE, playPurpose}` — PUBG 는 `PLATFORM: STEAM\|KAKAO`(원본의 `PLAY_STYLE` 아님), `OPTIONAL` 없음, `tier` 는 `EXIST` 모드에 필수(`GOLD_4` 꼴). 응답 `MatchRequestView {status: IDLE\|QUEUED\|PROPOSED\|MATCHED, requestId, queuedAt(epoch ms), partyId, target, memberCount, expiresAt, isAccepted}`. 상태는 **`GET /match-requests`**(경로 변수 없음 · 늘 200), 취소 `DELETE /match-requests/{requestId}`, **`POST /match-requests/heartbeat` 30초마다**(90초 끊기면 서버가 취소). 제안 `POST /proposals/{partyId}/accept` · `/decline` — **204 · 본문 없음**(`GET /proposals/{id}` 없음 — 제안 화면은 `MATCH_PROPOSAL_CREATED {memberNumber, target, partyId}` + 상태 조회로). `MATCH_CONFIRMED {partyId}` → **`POST /api/v1/match-parties/{partyId}/room`** → `{roomId}`(= partyId) → 방 화면. 원본의 `GET /parties/{id}` · `/ready` · `/leave` · `PARTY_*` 는 대응물이 없다(남긴다 — §5) | `src/api/client.ts` · `types.ts` · `state/MatchContext.tsx` · `PartySessionContext.tsx` · `pages/OnboardingPage.tsx` · `MyInfoPage.tsx` · `ProposalPage.tsx` · `PartyRoomPage.tsx` · `components/MatchComposer.tsx` · `ConditionForm.tsx` · `ActiveMatchCard.tsx` · `MatchProgress.tsx` | `platform-api.md` "계정" · "게임 프로필" · "전적을 긁는 것" · "자동 매칭이 게시판 방에 먼저 합류하는 길" · "자동 매칭 파티의 방" · `matching/contracts/openapi.yaml` · `README.md` #4 · #5(이름이 열려 있다) · `events.md` |
| **4** | **게시판 · 방** — 홈을 우리 게시판에 맞춘다. 목록 **`GET /posts?game=LOL&limit=20&cursor=`**(`game` 필수 · `id` 내림차순 · `nextCursor` 숫자 · 끝난 글도 `status` 로 남는다), 글 쓰기 **`POST /posts {game, mode, title, description, voice, conditions, wantedPositions}`**(방이 같이 생긴다 · 409 `ALREADY_RECRUITING` · `ALREADY_QUEUED` · `IN_OTHER_ROOM`), 고치기 `PATCH`(방에 남이 있으면 409 `ROOM_HAS_OTHER_MEMBERS`) · 지우기 `DELETE`(만료로). 입장 **`POST /rooms/{roomId}/members`**(201/200 · 404 `POST_NOT_FOUND` · 409 `POST_NOT_RECRUITING` · `ROOM_FULL` · `ROOM_CONFIRMED` · `IN_OTHER_ROOM` · `ALREADY_QUEUED`), 나가기 `DELETE …/members/me`(늘 204), 강퇴 `DELETE …/members/{userId}`, 확정 `POST …/confirm`(되돌릴 수 없다 — 한 번 더 확인 · 2명 이상), 접속 확인 **`POST …/heartbeat` 1분마다**(403/404 면 방 화면 닫기), `GET …/members`(방 안 사람만 · 문자열 id · `hostId` 로 승계 감지), `GET /rooms/me`(새로 열었을 때), 시그널 `POST …/signals {toUserId, signal}` ↔ `WEBRTC_SIGNAL`. `BOARD_CHANGED` 는 게시판 페이지일 때만 · 묶어서 · 커서 없이 맨 위부터 `limit` 으로 다시. **방 카드 보드(`src/rooms/*`)와 legacy 홈을 어떻게 할지는 이 단계 전에 묻는다**(§4 · §5) | `src/pages/HomePage.tsx` · `LegacyRecruitmentHome.tsx` · `src/rooms/*` · `src/api/recruitment.ts` · `state/useRecruitmentBoard.ts` · `components/Recruitment*.tsx` · `BoardFilters.tsx` · `PartyRoomPage.tsx` · `webrtc/WebRtcPartyClient.ts` | `platform-api.md` "모집 글 · 목록" · "방" 전부 · "게시판 채널 신호" |
| **5** | **소셜 필드** — 친구 `POST /friend-requests {userId}`(원본 `targetUserId`) · `GET /friend-requests?direction=RECEIVED\|SENT` → `{requests: [{requestId, requester, receiver, createdAt}]}` · accept/decline/`DELETE` · `GET /friends` → `{friends: [{userId, nickname, since}]}` · `DELETE /friends/{userId}`. 차단 `POST /blocks {userId}` · `GET /blocks` → `{blocks: […]}` · `DELETE /blocks/{userId}`. 신고 `POST /reports {targetUserId, reason: ABUSE\|CHEATING\|SPAM\|NO_SHOW\|OTHER, detail, contextId}` → 201 `{reportId, createdAt}`(원본 `description` · `partyId` · 다른 reason 이름). 최근 함께한 사람 `GET /recent-players` → `{players: [{userId, nickname, lastPartyId, lastPlayedAt}]}`(원본 `?limit=` · `avatarUrl` · `playCount` · `friend` 없음). 알림 `FRIEND_REQUEST_RECEIVED {requestId, fromUserId}` · `FRIEND_REQUEST_ACCEPTED {requestId, userId}` → 목록 재조회. **사람 검색은 없다** — 상대의 사용자 번호를 직접 넣는다(방 안 카드 · 친구 목록에서 온다) | `src/api/client.ts` · `state/SocialContext.tsx` · `pages/DirectMessagesPage.tsx` · `components/FriendManagementPanel.tsx` · `ReportModal.tsx` | `platform-api.md` "차단" · "친구 · 신고 · 최근 함께한 사람" · "이 앱이 내는 알림" |

**단계 사이에 안 바뀌는 것** — 랜딩 · 디자인 · `AppShell` · 게임 선택. **남기되 붙일 곳이 없는 것**(예약 · DM · 듀오 제안 · 아바타 · Ready/PLAYING · 알림함)은 §5.

---

## 3. API 대조표 — 원본 프런트가 부르는 경로 → 우리 백엔드

원본은 `src/api/client.ts` · `recruitment.ts` · `rooms/useRoomData.ts` 에서 `/api/v1` 아래를 부른다(2026-09-28 에 코드에서 뽑았다). "우리" 열의 근거는 `platform-api.md`(P) · `matching/contracts/openapi.yaml`(M) · `notification/CLAUDE.md`(N). **없음** = 백엔드에 대응물이 없다(만들어 달라고 하지 않는다 — `CLAUDE.md` §3-7 · §5).

| 영역 | 원본 프런트 | 우리 백엔드 | 앱 | 비고 |
|---|---|---|---|---|
| 가입 · 로그인 | `POST /auth/signup {email,password,nickname}` · `POST /auth/login` → `TokenResponse` | **없음** — 소셜만 | P | 2단계 |
| 소셜 | `GET /auth/oauth/providers` · `POST /auth/oauth/exchange {code}` · 라우트 `/auth/callback?code=` | `GET /auth/oauth/{KAKAO\|DISCORD}/start`(302) · 콜백은 백엔드 → `/` · `/signup/social` · `/login?error=OAUTH_FAILED` · `/settings?linked=` · `/settings?error=` | P | 프런트 라우트를 백엔드에 맞춘다(소유자 결정) |
| 소셜 가입 | — | `GET /auth/social/pending` → `{provider, suggestedNickname}` · `POST /auth/social/signup {nickname}` → 201 `{userId, nickname}` + 쿠키 | P | |
| 잇기 · 끊기 | — | 로그인한 채 `start` → 콜백이 잇는다 · `DELETE /users/me/social/{provider}`(409 `LAST_SOCIAL_IDENTITY`) | P | `users/me.socialProviders` 로 그린다 |
| 재발급 | `POST /auth/refresh {refreshToken}` → 토큰 둘 | `POST /auth/refresh`(쿠키 `qm_refresh` 만) → 200 `{userId, nickname}` + 쿠키 둘 · 401 `INVALID_REFRESH_TOKEN` | P | 프런트가 만료 전에 불러야 한다 |
| 로그아웃 | `POST /auth/logout {refreshToken}` | `POST /auth/logout`(본문 없음) → 204 + 쿠키 둘 제거 | P | |
| 내 정보 | `GET /users/me` → `{id, nickname, avatarUrl}` · `PATCH /users/me {nickname?, avatarUrl?}` · `POST /users/me/avatar`(multipart) | `GET /users/me` → `{userId, nickname, createdAt, socialProviders, gameAccounts}` · `PATCH /users/me {nickname}`(409 `NICKNAME_TAKEN`) · **아바타 없음** | P | |
| 게임 계정 | `GET /users/me/game-accounts` · `POST … {game, externalGameId, region}` · `DELETE …/{id}` · `GameAccountView{rankCode, flexRankCode, verifiedAt}` | 목록은 `users/me.gameAccounts` · **`PUT /users/me/game-accounts/{game}`**(게임마다 본문 다름) · `DELETE …/{game}` · `POST …/{game}/refresh` · 게임 프로필 `{game, gameNickname, verified, tier, mainPosition, server, stats, syncedAt}` | P | 3단계 |
| 게임 설정 | `GET /games` · `GET /games/{g}/match-schema` | **없음**(`GET /games` 는 계약에만 · 미구현) — 정적 상수(소유자 결정) | M | seed 사본 두 곳 |
| 자동 합류 | — | **`POST /posts/auto-join`**(매칭 본문과 같다) → 200 `{postId, roomId}` · 404 `NO_MATCHING_POST` → matching 으로 | P | "매칭 시작" 의 첫 호출 |
| 매칭 요청 | `POST /match-requests`(`MatchCondition`) → `{id, status, queuedAt, proposalId}` · `GET /match-requests/{id}` · `DELETE /match-requests/{id}` · `GET /match-requests/history` | `POST /match-requests`(`+tier`) → 201 `{status: QUEUED, requestId, queuedAt}` · **`GET /match-requests`**(변수 없음) · `DELETE /match-requests/{requestId}` · **`POST /match-requests/heartbeat`**(30초) · **history 없음** | M | 이름 `requestId`/`partyId`/epoch ms 는 원본 계약과 열려 있다(`README.md` #4 · #5) |
| 제안 | `GET /proposals/{id}` → `ProposalView{members[], expiresAt}` · `POST …/accept` → `ProposalView` · `POST …/decline` | **`GET` 없음** · `POST /proposals/{partyId}/accept` · `/decline` → **204**(403 `NOT_PROPOSAL_MEMBER` · 404 · 409 `PROPOSAL_CONFLICT`) | M | 제안 화면은 알림 + `GET /match-requests`(`PROPOSED` · `expiresAt` · `isAccepted`)로 |
| 파티(자동 매칭) | `GET /parties/{id}` · `POST …/ready {ready}` · `POST …/leave` · `PARTY_*` 이벤트 | **`POST /match-parties/{partyId}/room`** → 201/200 `{roomId}` · 그 뒤는 방의 요청 · **Ready/PLAYING · 파티 조회 없음**(P-31) | P | `MATCH_CONFIRMED {partyId}` 를 받으면 조작 없이 바로 부른다 |
| 예약 | `POST/GET /reservations` · `GET/PUT/DELETE …/{id}` · `RESERVATION_PROPOSAL_CREATED` | **없음**(`app:reservation` Lambda — 시작 안 함) | — | 남긴다 |
| 게시판(legacy 홈) | `POST /recruitments/search` · `GET /recruitments/mine` · `GET/PUT /recruitments/{id}` · `POST /recruitments` · `POST …/{id}/actions {action: CONFIRM\|BUMP\|PAUSE\|RESUME\|CLOSE\|LEAVE\|AUTO_ON\|…, version}` · `POST …/{id}/join {sourceId}` · `POST …/{id}/respond {applicantId, accept}` · `GET …/{id}/suggestions` · `POST /recruitments/impressions` · `RECRUITMENT_UPDATED` | `GET /posts?game=&limit=&cursor=` → `{posts, nextCursor}` · `GET /posts/{postId}` · `POST /posts` · `PATCH /posts/{postId}` · `DELETE /posts/{postId}` · 입장은 방(아래) · **신청 · 승인 · BUMP · PAUSE · 추천 · 노출 기록 없음** · 신호는 `BOARD_CHANGED {}` | P | 4단계. 원본의 mock 이 `/match-requests` 위에 흉내 낸 것이라 서버에 없던 경로다 |
| 방(codex 방 카드 보드) | `GET /rooms` → `GameRoom[]`(메시지 포함) · `POST /rooms {requestId, input: Settings, profile}` · `POST /rooms/{id}/join {profile, role?, fromRoomId?}` · `POST /rooms/{id}/actions {action: LEAVE\|KICK\|CONFIRM\|REOPEN, memberId?}` · `POST /rooms/{id}/messages {clientMessageId, text}` · `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` | 목록은 **글**(`GET /posts?game=`) · 만들기는 **글 쓰기** · `POST /rooms/{roomId}/members`(본문 없음) · `DELETE …/members/me` · `DELETE …/members/{userId}` · `POST …/confirm`(되돌릴 수 없음) · `POST …/heartbeat` · `GET …/members` · `GET /rooms/me` · `POST …/signals` · **메시지 · REOPEN 없음** · 알림 `ROOM_*` 5종 + `BOARD_CHANGED` | P | §4 표 |
| 친구 | `GET /friends` → `FriendView[]` · `DELETE /friends/{userId}` · `GET /friend-requests?direction=` → `FriendRequestView[]{counterpartUserId…}` · `POST /friend-requests {targetUserId}` · `POST …/{id}/accept` · `/decline` · `DELETE …/{id}` | 같은 경로 — 본문 `{userId}` · 응답이 감싸여 있다(`{friends: […]}` · `{requests: […]}` · 한 줄 `{requestId, requester{userId,nickname}, receiver, createdAt}`) · accept → `{userId, nickname, since}` | P | 5단계 |
| 차단 | `GET /blocks` · `POST /blocks {targetUserId}` · `DELETE /blocks/{userId}` | 같은 경로 — `POST {userId}` → 201 `{userId, nickname, createdAt}` · `GET` → `{blocks: […]}` | P | |
| 신고 | `POST /reports {targetUserId, reason: ABUSIVE_LANGUAGE\|HARASSMENT\|CHEATING\|TROLLING_OR_AFK\|INAPPROPRIATE_PROFILE\|OTHER, description?, partyId?}` → 201 빈 본문 | `POST /reports {targetUserId, reason: ABUSE\|CHEATING\|SPAM\|NO_SHOW\|OTHER, detail, contextId}` → 201 `{reportId, createdAt}` | P | |
| 최근 함께한 사람 | `GET /recent-players?limit=20` → `{userId, nickname, avatarUrl, lastPlayedAt, playCount, friend}[]` | `GET /recent-players` → `{players: [{userId, nickname, lastPartyId, lastPlayedAt}]}`(50명) | P | |
| 실시간 | `WS /ws`(서브프로토콜 bearer) · `SESSION_SNAPSHOT` | **`GET /api/v1/events`**(SSE · 쿠키 · 파라미터 없음) · `heartbeat` 이름 있는 이벤트 · `retry:` | N | 2단계 |
| DM · 알림함 · 듀오 제안 | localStorage · 메모리 | **없음** | — | 남긴다 |

**이벤트 type 대조** — 원본이 받는 14개: `SESSION_SNAPSHOT` · `RECRUITMENT_UPDATED` · `MATCH_PROPOSAL_CREATED` · `MATCH_PROPOSAL_EXPIRED` · `MATCH_CONFIRMED` · `MATCH_CANCELLED` · `RESERVATION_PROPOSAL_CREATED` · `PARTY_MEMBER_LEFT` · `PARTY_READY_CHANGED` · `PARTY_PLAYING` · `PARTY_CLOSED` · `WEBRTC_SIGNAL` · `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED`.
우리가 보내는 것: `MATCH_QUEUE_UPDATED` · `MATCH_PROPOSAL_CREATED` · `MATCH_PROPOSAL_EXPIRED` · `MATCH_CONFIRMED` · `MATCH_CANCELLED`(matching) · `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` · `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · `WEBRTC_SIGNAL` · `BOARD_CHANGED`(platform). **겹치는 것은 `MATCH_*` 4개와 `WEBRTC_SIGNAL` 뿐이고, `WEBRTC_SIGNAL` 의 `payload` 도 다르다**(원본 `{partyId, targetUserId, signalType, data}` — 보내는 쪽도 WS 프레임 · 우리는 `POST /rooms/{roomId}/signals {toUserId, signal}` ↔ `{roomId, fromUserId, signal}`).

---

## 4. codex 브랜치가 `main` 과 다른 점 — 방 카드 보드 (조사 2026-09-28)

`git diff origin/main 7ee7177 -- frontend` 는 72 파일 · +4812/−333 이다. `src/rooms/` 26 파일이 전부 새 것이고, 나머지는 그것을 끼우는 변경이다. 원본 저장소 쪽에는 같은 브랜치에 백엔드 파일럿(`backend/.../party/room/*` · `V8__shared_room_pilot.sql` · `contracts/rooms.openapi.yaml` · `docs/22~25`)이 같이 있다 — **가져오지 않았다.**

### 4.1 무엇이 더해졌나

| 자리 | 내용 |
|---|---|
| `src/pages/HomePage.tsx` | 9줄로 줄었다 — `USE_MOCK && VITE_HOME_LAYOUT !== 'legacy' \|\| VITE_HOME_LAYOUT === 'rooms'` 면 `RoomBoardHome`, 아니면 `LegacyRecruitmentHome`. **mock 의 기본 홈이 방 카드 보드, real 의 기본 홈은 legacy** |
| `src/pages/LegacyRecruitmentHome.tsx` | main 의 옛 `HomePage` 를 **이름만 바꿔** 옮긴 것(diff 가 함수 이름 한 줄) |
| `src/rooms/RoomBoardHome.tsx` | 보드 페이지. 게임 · 모드 · 티어 범위 · 역할 · 음성 · 시작 시각(`ALL/NOW/LATER`) · 열린 방만 필터, `createdAt` 내림차순. 오른쪽 레일은 `RoomQuickConnect`(탐색 · 매칭) ↔ `RoomConversation`(방 채팅) 토글 |
| `RoomDeck.tsx` | 방 카드 하나 — 제목 · 시작 라벨/마감 · 멤버 타일(아바타 · 방장 왕관 · 티어 · 역할 · 승률 · KDA · 챔피언 3) · **빈 자리 버튼**(`vacantRoleOptions` → 자리 선택 모달 `RoomSeatJoin`) |
| `RoomConversation.tsx` | 방 채팅 패널 — 방장의 `모집 마감`/`마감 취소` 토글(`CONFIRM`/`REOPEN`) · 나가기 · 강퇴 · 메시지 로그(시스템 메시지 `authorId: null`) · 2000자 입력 · **자동 마감 UI**(10분 유휴 + 60초 경고 — 방장 화면의 1초 타이머가 `autoConfirm`) · 음성은 mock 만(real 은 "연결 준비 중") |
| `RoomQuickConnect.tsx` · `quickConnect.ts` | "매칭 시작" — **대기열 없이** 같은 게임 · 모드 · 열림 · 빈 자리 · 시작 시각 · 음성 · 티어 범위 · 역할이 맞는 방을 골라 **한 개씩 제안**(빈 자리 적은 순 → 최신). "방 만들기" → `RoomCreatePreview` → `create` |
| `RoomCreatePreview` · `RoomSeatJoin` · `RoomMemberProfile` · `RoomCapacityPicker` · `RoomStartTimePicker`(`RoomDatePicker` · `RoomHourPicker`) · `RoomVoice` | 모달 · 피커들. 정원은 모드별(`summary.ts` — LoL `SOLO_DUO_RANKED` 2 · `FLEX_RANKED` `[2,3,5]` · PUBG `DUO` 2 · `SQUAD` 4) |
| `useRoomData.ts` | `USE_MOCK ? useDemoRooms : useLiveRooms`. **live** — `GET /rooms` 를 `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` · `SESSION_SNAPSHOT` · 연결됨 · 5초 폴링(보이는 탭만) · focus · visibilitychange 마다 다시 받는다(in-flight 합치기). `create` → `POST /rooms {requestId(UUID · 멱등), input, profile{roles, bio}}` · `join` → `POST /rooms/{id}/join {profile, role?, fromRoomId?}` · `leave/kick/confirm/reopen` → `POST /rooms/{id}/actions {action, memberId?}` · `send` → `POST /rooms/{id}/messages {clientMessageId, text}`. `autoConfirm` · `extendRecruitment` 는 live 에서 **빈 함수** |
| `store.ts` | mock 방 저장소 — localStorage `qm:room-board:v1:<userId>`(사용자별 · 공유 안 됨) · 예시 방 seed · 같은 탭 `CustomEvent 'qm:room-board-changed'` · 다른 탭 `storage`. 규칙(한 사람 한 방 · 방장만 · 방장 승계 · 만석이면 `CONFIRMED` · 나가면 `REOPEN`)을 클라이언트가 흉내 낸다 |
| `types.ts` | `GameRoom {id(UUID), game, modeKey, type: REALTIME\|RESERVATION, title, ownerId, capacity, members: RoomMember[], desiredRoles, desiredTierRange?, voice, status: OPEN\|CONFIRMED, autoCloseAt?, createdAt(epoch ms), availableFrom(ISO\|null), messages: RoomMessage[]}` · `RoomMember {id, nickname, avatarUrl, tier, division, winRate, kda, roles, champions, bio, voice}` · `CreateRoomInput` |
| `schedule.ts` · `autoClose.ts` · `positions.ts` · `summary.ts` · `voice.ts` · `accountRank.ts` | 순수 함수 — 예약 시각(정시 · 미래만) · 자동 마감 단계(`waiting/warning/due`) · LoL 5인 풀 라인업 검사 · 방 요약(평균 티어 · 승률 · KDA) · 음성 두 값(`OPTIONAL` → `NO_VOICE`) · `rankCode` 파싱 |
| `src/api/types.ts` · `ws.ts` | `ServerEventType` 에 **`ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED`** 둘 추가(payload 는 읽지 않는다 — 전체 재조회 신호). 다른 변경 없음 |
| `src/domain/tierRange.ts` · `components/TierRangePicker.tsx` · `styles/tier-range.css` | 티어 범위 `{minTier, maxTier}` · 게임별 `TIER_ORDER` · `tierInRange` · `tierRangesOverlap` · 두 번 눌러 범위를 잡는 팝오버 피커 |
| `components/SingleRolePicker.tsx` · `SlidingSelector.tsx` · `SelfIntroductionFields.tsx` | 역할 하나 고르는 라디오 · 선택 하이라이트가 미끄러지는 컨테이너 · 자기소개 폼에 `binaryVoice` · `showTierRange` · `compact` · `singleRole` props(LoL 밖의 티어 · 챔피언 · 승률 · KDA 입력을 없앴다) |
| `state/useDuoPreview.ts` · `components/AppShell.tsx` | `enabled` 인자 — 방 카드 보드가 보일 때 mock 듀오 제안 폴링(2초)을 끈다 |
| `domain/gameConfig.ts` · `introduction.ts` · `mocks/contract.ts` | 라벨 `SOLO_DUO_RANKED: '2인 랭크'` · `FLEX_RANKED: '자유 랭크'` 추가 · 자기소개에 `desiredTierRange` · `roomCapacity` · mock 카탈로그에 `FLEX_RANKED`(5인) |
| `pages/AuthPage.tsx` · `MyInfoPage.tsx` | `VITE_ROOM_DEMO=true` 면 "데모 A/B 로 시작" 버튼(원본 파일럿 계정 · 비밀번호 로그인) · 아바타 파일 업로드(5MB · png/jpeg/webp) |
| `package.json` · Playwright | `dev:rooms`(**real 모드** + `VITE_HOME_LAYOUT=rooms` + `VITE_ROOM_DEMO=true` · 5174 · `--host 127.0.0.1`) · `playwright.config.ts` 를 두 프로젝트로(`chromium` 5194 legacy · `room-decks` 5195) · `playwright.rooms-live.config.ts`(5196 · 원본 백엔드 필요) · `e2e/room-decks.spec.ts`(1418줄 · mock) · `e2e-live/shared-rooms.spec.ts` · `tests/*.spec.ts` 5개 + `tests/playwright.config.ts`(브라우저 없는 단위 테스트) |

### 4.2 원본의 방 API(`contracts/rooms.openapi.yaml` — 우리 계약이 아니다) 한 줄 요약

방은 **모집과 별개의 자원**이고 `queuemate.rooms.enabled=true` 파일럿이다. `GET /rooms`(전부 · 내 방만 메시지 200개) · `POST /rooms {requestId, input: Settings{game, modeKey, type, title, capacity 2~5, desiredRoles, desiredTierRange, voice, availableFrom}, profile{roles, bio}}` → `Room{id, ownerId, status: OPEN\|CONFIRMED, autoCloseAt, members: Member[], messages}` · `POST /rooms/{id}/join {profile, role?, fromRoomId?}`(다른 방에서 **원자적으로 옮겨 오기**) · `POST /rooms/{id}/actions {action: LEAVE\|KICK\|CONFIRM\|REOPEN, memberId?}`(만석이면 자동 CONFIRM · 누가 나가면 자동 REOPEN · 빈 방은 삭제 · 방장 승계) · `POST /rooms/{id}/messages {clientMessageId, text ≤2000}`(멤버만 · 방과 함께 삭제). bearer 인증 · 시각은 epoch ms(`availableFrom` 만 ISO). 변경마다 `ROOMS_UPDATED`.

### 4.3 우리 계약과의 대조 — 방 카드 보드가 우리 백엔드에 어떻게 대응하나

| 방 카드 보드가 하는 것 | 원본 API | **우리 계약**(`platform-api.md`) | 맞나 |
|---|---|---|---|
| 방 목록을 그린다 | `GET /rooms`(세 게임 전부 · 멤버 프로필 · 메시지 포함) | **`GET /posts?game=`**(게임 필수 · 커서 · `posts[].members[].profile` 에 게임 프로필 전체 · `memberCount` · `capacity` 5 · `full` · `status: RECRUITING\|CONFIRMED\|EXPIRED`). 끝난 글도 남는다 | **대응된다** — 카드의 데이터(닉네임 · 티어 · 주 포지션 · LoL 전적)는 `profile` 에서. 단 게임을 하나 골라야 하고(보드의 게임 선택이 이미 있다), 티어 범위 · 시작 시각 · 승률/KDA(VALORANT · PUBG) 는 없다 |
| 방 만들기(`RoomCreatePreview`) | `POST /rooms {input, profile}` | **`POST /posts {game, mode, title, description, voice, conditions, wantedPositions}`** — 방이 같이 생기고 응답 `members` 에 방장이 있다. 409 `ALREADY_RECRUITING` · `IN_OTHER_ROOM` · `ALREADY_QUEUED` | **대응된다** — `desiredRoles` → `wantedPositions`, `title` · `voice` 그대로. **`capacity`(정원 선택) · `desiredTierRange` · `type/availableFrom`(예약) · `profile.bio` 는 칸이 없다**(정원은 5 고정 · 자동 합류의 정원은 모드의 `targetPartySize`) |
| 빈 자리에 참여(`RoomSeatJoin` — 역할 선택) | `POST /rooms/{id}/join {profile, role, fromRoomId}` | **`POST /rooms/{roomId}/members`**(본문 없음 · 201/200). 포지션은 **입장 때 고르지 않는다**(프로필의 주 포지션 — D-20 · P-18) · 다른 방에 있으면 409 `IN_OTHER_ROOM`(먼저 나가야 한다 — 옮겨 오기 없음) | **절반** — 입장은 되지만 자리(역할) 선택 · 방 이동은 없다. 자리 버튼은 "참여" 하나로 |
| 나가기 · 강퇴 | `POST /rooms/{id}/actions {LEAVE\|KICK}` | `DELETE /rooms/{roomId}/members/me` · `DELETE …/members/{userId}` | **대응된다** |
| 모집 마감 · 마감 취소 토글 | `actions {CONFIRM\|REOPEN}` · 만석 자동 CONFIRM · 이탈 자동 REOPEN | `POST /rooms/{roomId}/confirm` — **되돌릴 수 없다**(REOPEN 없음) · 2명 이상 · 만석이어도 자동 확정 없음(`full: true` 로 목록에 남는다) · 확정 뒤 새 입장 409 `ROOM_CONFIRMED` | **안 맞는다** — 토글이 아니라 한 번뿐인 버튼(확인 한 번 더). 만석 · 자동 마감 · 유휴 마감 없음 |
| 방 채팅(`RoomConversation`) | `POST /rooms/{id}/messages` · `ROOM_MESSAGES_UPDATED` · `GET /rooms` 의 `messages` | **없음** — 텍스트는 서버에 두지 않는다(#6 · D-9 — WebRTC DataChannel 브라우저 직결). 시그널만 `POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL` | **안 맞는다** — 살리려면 DataChannel 로 다시 짠다(미정) |
| 예약 시각(`RoomStartTimePicker` · `REALTIME/RESERVATION` · `availableFrom`) | `Settings.type` · `availableFrom` | **없음** — 글에 시각 칸 없음. 예약은 `app:reservation`(Lambda · 미착수) | **안 맞는다** |
| 자동 마감(`autoClose.ts` — 10분 유휴 + 60초) | 클라이언트 타이머(live 는 빈 함수) | **없음** — 방의 수명은 접속 확인(1분 · 600초)이고 방장이 사라지면 방이 없어지고 글이 만료된다 | **안 맞는다** — 우리 쪽 "만료" 는 다른 개념 |
| 티어 범위(`desiredTierRange` · 필터 · 카드 배지) | `Settings.desiredTierRange` · `Member.tier/division` | 글에 범위 칸 없음. 카드의 `profile.tier`(`GOLD_4` 꼴 — 디비전 포함). 자동 합류의 티어 판정은 서버가 gameconfig `tier-range` 로 | **절반** — 배지는 그릴 수 있고(`profile.tier`) 범위 선택은 자리가 없다 |
| 빠른 연결(`RoomQuickConnect` — 대기열 없이 한 방씩 제안) | 클라이언트가 `GET /rooms` 를 걸러 제안 | **`POST /posts/auto-join`**(서버가 조건 · 티어 범위 · 포지션 · 가장 오래된 방부터 골라 **바로 넣는다**) → 404 `NO_MATCHING_POST` 면 `POST /match-requests`(대기열) | **다르다** — 우리는 서버가 고르고 바로 입장이며, 없으면 대기열 매칭으로 이어진다(제안 화면 없음). "매칭 시작" 버튼은 이 흐름으로 |
| 실시간 갱신 | `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` · `SESSION_SNAPSHOT` · 5초 폴링 · focus | **`BOARD_CHANGED {}`**(모든 연결 · 게시판 페이지일 때만 · 묶어서 · 커서 없이 맨 위부터 `limit`) · 방 안은 `ROOM_MEMBER_ENTERED/LEFT/KICKED` · `ROOM_CLOSED` · `ROOM_CONFIRMED` + `GET /rooms/{roomId}/members` 재조회 | **대응된다** — `useLiveRooms` 의 "신호 → 전체 재조회" 구조가 그대로 `BOARD_CHANGED` 에 맞다. 5초 폴링은 지운다 |
| 방장 표시 · 승계 | `ownerId` | 목록의 `host` · `members[].host` · 방 안은 `GET …/members` 의 `hostId`(확정한 방은 바뀔 수 있다 — D-23) | **대응된다** |
| 자동 매칭 파티의 방 | (없음 — 파티는 별개) | `MATCH_CONFIRMED {partyId}` → `POST /match-parties/{partyId}/room` → `roomId = partyId`(UUID) · 처음부터 확정된 방 · 게시판에 안 보인다 | 보드와 무관 — 방 화면(`PartyRoomPage` 자리)이 받는다 |

**요약** — 카드 보드의 **목록 · 만들기 · 참여 · 나가기 · 강퇴 · 방장 · 실시간 갱신**은 우리 `posts` + `rooms/{roomId}/members` + `ROOM_*` + `BOARD_CHANGED` 로 옮길 수 있다. **채팅(`/messages`) · 예약 시각 · `REOPEN` · 자동 마감 · 정원 선택 · 티어 범위 · 자리 선택 · 방 이동**은 우리 백엔드에 없다 — 어떻게 할지는 4단계 전에 소유자가 정한다(§5).

---

## 5. 미정 — 다음에 닿기 전에 물을 것

`CLAUDE.md` §5 의 표가 원본이다. 순서대로 —

- **2단계 전** — 게임 · 모드 · 티어 상수의 자리 · 모양 · 한글 라벨(`CLAUDE.md` §5) · `tests/*.spec.ts` 를 남길지 · 만료 전 선제 재발급을 둘지(15분 access) · `/login` 화면에 카카오 · 디스코드 말고 무엇을 남길지(랜딩 그대로?).
- **3단계 전** — 제안 화면(`ProposalPage`)을 `GET /match-requests` 폴링 + 알림으로 어떻게 그릴지(`GET /proposals/{id}` 없음 · 멤버 목록 없음 · `isAccepted` 만) · `PartyRoomPage`(Ready/PLAYING)를 방 화면으로 바꿀지 남길지 · history 화면.
- **4단계 전** — **방 카드 보드 vs legacy 홈**(§4.3) · 채팅 · 예약 시각 · 자동 마감 · 티어 범위 · 자리 선택의 처지 · `BOARD_CHANGED` 묶기 간격 · 방 화면 경로(`roomId` 숫자 · UUID).
- **5단계 전** — 상대의 사용자 번호를 어디서 받아 친구 요청을 보내는지(방 안 카드 · 최근 함께한 사람) · DM · 알림함 화면의 처지.
- **언제든** — 계약과 코드가 다를 때(`matching/contracts/README.md` #4 · #5 처럼 열린 이름) 프런트는 **코드가 답하는 모양**을 따르고 그 사실을 여기 적는다. 백엔드를 고치자고 하지 않는다.

---

## 6. 로컬에서 띄우기

**지금(1단계)은 우리 백엔드와 통하지 않는다** — `npm run dev:mock`(5174 · 가짜 서버)으로 화면만 볼 수 있다. 2단계 뒤에는 —

```bash
# 백엔드 셋 + Redis 6380 + PostgreSQL 5433 + gameconfig seed — 루트 START_HERE.md §6 그대로 (platform 8082 → notification 8081 → matching 8080)
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/frontend" && npm run dev     # 5173 — 프록시 셋
# 끝나면
ss -ltnp | grep :5173   # → kill <PID>. pkill -f 금지
```

- 로그인은 **소셜만** — 카카오 · 디스코드 앱 키(`KAKAO_CLIENT_ID` 등)와 Redirect URI(`http://localhost:8082/api/v1/auth/oauth/KAKAO/callback`)를 소유자가 등록해야 브라우저에서 로그인이 된다. 그 전까지 확인은 `build` · `typecheck` 만이다(소유자 결정).
- platform 의 `FRONT_BASE_URL` 기본값이 `http://localhost:5173` 이라 콜백이 5173 으로 돌아온다. `ALLOWED_ORIGINS` 기본값에 5173 이 있어 `Origin` 검사도 통과한다.
- SSE 도착 확인은 `PUBSUB NUMSUB` 이 1 이 된 뒤에(루트 `START_HERE.md` §8).
