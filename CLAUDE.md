# CLAUDE.md — frontend 규칙 (Non-Negotiable)

작업 전에 루트 `CLAUDE.md` → 루트 `START_HERE.md` → 이 폴더의 `START_HERE.md`(지금 상태 · 통합 계획 · API 대조표 · 미정) → 이 파일 → 계약 순으로 읽어라.
계약은 **옆 폴더에 있다** — `platform/contracts/platform-api.md`(계정 · 소셜 로그인 · 게임 프로필 · 모집 글 · 방 · 자동 매칭 파티의 방 · 친구 · 차단 · 신고 · 알림) · `matching/contracts/openapi.yaml` · `events.md`(매칭 요청 · 제안 · `MATCH_*` 알림) · `notification/CLAUDE.md`(SSE). 기준일은 2026-09-28 이다.

---

## 1. 이 폴더가 무엇인가

- QueueMate 의 **브라우저 앱**이다. React 18 · react-router 6 · TypeScript · Vite 5 · npm. 개발 서버 **5173**(platform · matching · notification 의 `ALLOWED_ORIGINS` 기본값에 들어 있는 출처다).
- **원본은 `Port-051/QueueMate` 브랜치 `codex/room-card-board`, 커밋 `7ee7177`(2026-09-28)의 `frontend/` 다.** 2026-09-28 에 그대로 가져왔다(커밋 `14946a9`). `main`(f860ada) 보다 90커밋 앞이고 그 차이의 거의 전부가 **방 카드 보드**(`src/rooms/*`)다 — 무엇이 다른지는 `START_HERE.md` §4.
  원본 README 는 `docs/UPSTREAM_README.md` 다. **그 파일과 원본 코드는 원본 백엔드(Spring 하나 · 8080 · bearer 토큰 · `/ws` WebSocket · 이메일 가입)를 전제한다 — 이 작업 공간의 백엔드가 아니다.** 근거로 쓰지 않는다.
- **git 고아 브랜치 `frontend`** 의 worktree 다(본 저장소 디렉터리는 `matching/.git`). `matching` · `platform` · `notification` 과 이력을 공유하지 않는다.
  옛 시도(`main` f860ada 기반, 2026-09-28 오전)는 **버렸다 — 브랜치도 지웠다.** 2 · 3단계의 내용은 이 브랜치 위에서 **새로** 한다(`START_HERE.md` §2).
- 백엔드는 **세 앱**이다(루트 `START_HERE.md` §2) — `platform` 8082(계정 · 소셜 로그인 · 토큰 · 게임 프로필 · 게시판 · 방 · 친구 · 차단 · 신고) · `matching` 8080(매칭 요청 · 제안) · `notification` 8081(SSE). **프런트는 이 세 앱의 계약을 따르는 쪽이다 — 계약을 정하는 쪽이 아니다.**

## 2. 백엔드와의 약속 — 프런트가 지킬 것

| 항목 | 우리 백엔드 | 원본 프런트(지금 코드) | 출처 |
|---|---|---|---|
| 인증 | **쿠키 `qm_access`**(RS256 JWT · 15분 · `HttpOnly`) + **`qm_refresh`**(불투명 UUID · 7일 · `Path=/api/v1/auth/refresh`). 프런트는 토큰을 **보지도 저장하지도 않는다.** 만료되면 `POST /api/v1/auth/refresh`(본문 없음 · 쿠키만) — 401 `INVALID_REFRESH_TOKEN` 이면 로그아웃 상태다 | `localStorage['qm.tokens']` + `Authorization: Bearer` · `POST /auth/refresh {refreshToken}` | `platform-api.md` "공통" · "access 토큰" · "refresh 토큰" · D-24 · D-26 |
| 가입 · 로그인 | **소셜만**(카카오 · 디스코드) — `GET /api/v1/auth/oauth/{KAKAO\|DISCORD}/start` 로 **브라우저 이동**, 콜백은 백엔드가 받고 `FRONT_BASE_URL` + `/`(로그인됨) · `/signup/social`(처음 온 사람 → `GET /auth/social/pending` · `POST /auth/social/signup {nickname}`) · `/login?error=OAUTH_FAILED` · `/settings?linked=…` · `/settings?error=…` 로 302. 이메일 · 비밀번호 · 직접 가입은 **없다** | `POST /auth/signup {email,password,nickname}` · `POST /auth/login` · `GET /auth/oauth/providers` · `POST /auth/oauth/exchange {code}` · 라우트 `/auth/callback` | `platform-api.md` "소셜 로그인" · "잇기 · 끊기" · D-35 · D-38 |
| 식별자 | `userId` 는 **숫자**(사용자 번호). 게시판 응답은 JSON 숫자, 방 응답 · 알림 `payload` 는 십진 **문자열**(`"42"`). 보여 주는 이름은 `nickname` 하나. **사람 검색 API 는 없다** | `UserProfile{id,nickname,avatarUrl}` — 아바타 업로드 `POST /users/me/avatar` 는 우리에 없다 | `platform-api.md` "공통" · D-25 |
| 실시간 | **SSE `GET /api/v1/events`**(notification · 쿠키로 연결할 때만 검증 · 쿼리 파라미터 없음). 봉투 `{type, eventId, occurredAt, payload}` 는 **이름 없는 이벤트**(`onmessage`) · `event: heartbeat` 는 20초 · `retry:` 1000~2000ms. 401 로 끊기면 `EventSource` 가 재접속을 멈추므로 **재발급 뒤 새로 만든다** | `/ws` WebSocket · 서브프로토콜 `bearer.<token>` · `SESSION_SNAPSHOT` | `notification/CLAUDE.md` §5 · §5.1 · `events.md` A-1~A-4 |
| 알림 종류 | `MATCH_QUEUE_UPDATED {memberNumber}` · `MATCH_PROPOSAL_CREATED {memberNumber,target,partyId}` · `MATCH_PROPOSAL_EXPIRED {partyId}` · `MATCH_CONFIRMED {partyId}` · `MATCH_CANCELLED {memberNumber}` · `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` · `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED {roomId, members}` · `WEBRTC_SIGNAL {roomId, fromUserId, signal}` · **`BOARD_CHANGED {}`**(모든 연결에 — 게시판 페이지를 보고 있을 때만 목록을 다시 받는다, 몇 초에 한 번으로 묶어서). **`PARTY_*` 는 없다**(D-44 · P-31). 알림은 "다시 조회하라" 는 신호다 — 데이터는 REST 로 | `SESSION_SNAPSHOT` · `RECRUITMENT_UPDATED` · `PARTY_*` 4종 · `RESERVATION_PROPOSAL_CREATED` · `ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` | 루트 `START_HERE.md` §4.4 · `platform-api.md` "알림" |
| 개발 프록시 | **경로별로 셋** — `/api/v1/match-requests` · `/api/v1/proposals` → 8080, `/api/v1/events` → 8081, 나머지 `/api` → 8082. 운영은 CloudFront 한 도메인 아래 ALB 가 경로로 나눈다 — **같은 출처**라 쿠키 · `Origin` 검사 · CORS 없음이 전부 이 전제 위에 있다. **서비스에 CORS 를 넣지 않는다** | `/api` → 8080 하나 · `/ws` → 8080 | `platform/CLAUDE.md` §5.1 (사) · 루트 `START_HERE.md` §4.3 |
| CSRF | `SameSite=Lax` + `Origin` 검사 — POST/PUT/PATCH/DELETE 의 `Origin` 이 허용 목록 밖이면 403 `ORIGIN_NOT_ALLOWED`. 브라우저가 붙이는 헤더라 프런트가 할 일은 **5173 에서 프록시로 부르는 것**뿐이다. CSRF 토큰은 없다 | — | D-24 |
| 에러 본문 | `{"code", "message", "details": [문자열]}` — **`code` 로 갈래를 정한다.** 401 `UNAUTHENTICATED` · 400 `VALIDATION_FAILED` · 503 `ROOM_STATE_UNAVAILABLE` · `MATCHING_UNAVAILABLE`(+`Retry-After: 5`) 등. 원본의 `ErrorCode` 목록은 우리 코드와 이름이 다르다 | `{code,message}` · `UNAUTHORIZED` 등 | `platform-api.md` "공통" · `openapi.yaml` `ErrorResponse` |
| 게임 설정 | 게임 `LOL` · `VALORANT` · `PUBG`. 모드 · 티어 사다리의 **원본은 `matching/seed/gameconfig.redis`** 이고 백엔드에 조회 API 가 없다(`GET /games` 는 계약에만 있고 미구현 — `contracts/README.md` #7). **프런트는 정적 상수로 둔다**(2026-09-28 소유자 결정 — 두 곳이다. 어긋나면 400 이 난다) | `GET /games` · `GET /games/{g}/match-schema` | §3 · `START_HERE.md` §3 |
| 게시판 · 방 | 글 `POST /api/v1/posts`(방이 같이 생긴다) · 목록 `GET /posts?game=`(필수 · 커서) · 입장 `POST /rooms/{roomId}/members` · 나가기 `DELETE …/members/me` · 강퇴 · 확정 `POST …/confirm` · 접속 확인 `POST …/heartbeat`(1분) · `GET /rooms/me` · 시그널 `POST …/signals`. 자동 매칭 파티의 방은 `POST /match-parties/{partyId}/room`. **텍스트 채팅은 서버에 없다**(WebRTC DataChannel — 브라우저 직결) | `/recruitments/*`(legacy 홈) · `/rooms` · `/rooms/{id}/join` · `/actions` · `/messages`(방 카드 보드) | `platform-api.md` "모집 글 · 목록" · "방" · `START_HERE.md` §4 |

## 3. 소유자 결정 (2026-09-28)

이미 정해진 것이다 — 다시 묻지 않고 따른다. 뒤집는 것은 소유자만 한다.

1. **프런트 통합은 `codex/room-card-board` 위에서 다시 시작한다.** `main` 기반 옛 시도는 버렸다(브랜치째 지웠다). 옛 시도가 했던 2 · 3단계(쿠키 인증 · 소셜 전용 로그인 · 프록시 셋 · SSE · 게임 설정 상수 · mock 삭제 · 게임 계정 · 매칭)는 **이 브랜치 위에서 새로 한다** — 옮겨 오지 않는다.
2. **콜백 경로는 프런트가 백엔드에 맞춘다** — `/signup/social` · `/login?error=OAUTH_FAILED` · `/settings?linked={PROVIDER}` · `/settings?error=…` 라우트를 프런트가 만든다. 백엔드의 `FRONT_BASE_URL` 기본값은 `http://localhost:5173` 이다. (원본의 `/auth/callback?code=` 는 없어진다.)
3. **게임 · 모드 · 티어 목록은 프런트의 정적 상수다.** 원본은 `matching/seed/gameconfig.redis` — **seed 와 프런트 두 곳**이고 모드를 더하면 둘 다 고친다. 백엔드에 조회 API 를 만들어 달라고 하지 않는다.
4. **mock 층과 e2e 는 지운다** — `src/mocks/*` · `VITE_API_MODE` 분기 · `e2e/*` · `e2e-live/*` · `playwright*.config.ts` · `dev:mock*` · `dev:rooms` 스크립트. 진짜 백엔드 셋을 띄워 본다(루트 `START_HERE.md` §6). `tests/*.spec.ts`(브라우저 없는 순수 함수 테스트 5개)를 남길지는 그때 묻는다(§5).
5. **온보딩은 게임 계정 화면으로의 전환이다** — 소셜 가입 뒤 게임 계정이 없으면 게임 계정 연결 화면(`PUT /api/v1/users/me/game-accounts/{game}`)으로 보낸다. 게임 계정이 없어도 쓸 수 있는 기능은 막지 않는다(백엔드가 강제하지 않는다).
6. **확인은 `npm run build` · `npm run typecheck` 만이다.** 브라우저에서 실제 소셜 로그인은 카카오 · 디스코드 앱 키가 없어 아직 못 해 본다(`platform/START_HERE.md` §4 F-1).
7. **백엔드에 대응물이 없는 화면은 일단 남긴다** — 예약(`/reservations`) · DM(`/messages`) · 듀오 제안 · 아바타 업로드 · Ready/PLAYING 파티 · 알림함 · **방 카드 보드의 채팅 · 예약 시간 · 자동 마감**. 지우지도 백엔드를 만들어 달라고도 하지 않는다 — 어떻게 할지는 미정(§5).

## 4. 하지 말 것

- **`matching/` · `platform/` · `notification/` 의 파일을 고치기.** 읽기만 한다. 계약이 안 맞으면 **묻는다**(그 폴더에서 따로 작업이 열린다). 프런트 사정으로 백엔드 경로 · 응답 · 에러 코드를 바꿔 달라는 요청은 소유자에게 근거와 함께 올린다.
- **경로 · 스키마 · 에러 코드 · 알림 `payload` 를 지어내기.** 계약에 없는 것은 미정이다 — `START_HERE.md` §5 로 보낸다.
- **토큰을 `localStorage` · 메모리에 두거나 `Authorization: Bearer` 로 보내기.** 쿠키가 전부다. `?userId=` · 본문 `userId` 를 되살리기(백엔드가 받지 않는다).
- **WebSocket(`/ws`) · `SESSION_SNAPSHOT` 을 전제하기.** 실시간은 SSE `GET /api/v1/events` 하나다. **`ROOMS_UPDATED` · `ROOM_MESSAGES_UPDATED` · `PARTY_*` · `RECRUITMENT_UPDATED` 를 기다리기** — 오지 않는다. 방의 변화는 `ROOM_*` 5종 + `BOARD_CHANGED`, 파티 성립은 `MATCH_CONFIRMED` 다.
- **원본의 방 API(`GET/POST /rooms` · `/rooms/{id}/join` · `/actions` · `/messages`)를 우리 백엔드에 부르기.** 우리 방은 글(`posts`)에서 시작하고 입장은 `POST /rooms/{roomId}/members` 다(대조표 — `START_HERE.md` §4). **텍스트 채팅 · 방 메시지를 서버에 두기** — 우리는 두지 않는다(D-9 · #6: 음성 · 텍스트는 WebRTC 직결).
- **`BOARD_CHANGED` 마다 즉시 · 무제한으로 목록을 다시 받기** — 묶는다(간격은 미정 · 몇 초). 신호를 받았을 때 커서를 쓰기(펼친 만큼을 `limit` 으로 맨 위부터 다시 받는다).
- **`GET /rooms/{roomId}/members` 로 목록을 그리기** — 방 안 사람만 볼 수 있다(403). 방 밖에서 방 안을 보는 창구는 게시판 목록이다.
- **`mode` · `tier` · 포지션의 값을 소문자 · 다른 이름으로 보내기** — 백엔드는 대문자 enum 그대로만 받는다(`LOL` · `RANKED_SOLO` · `GOLD_4` · `MID`). 원본의 `SOLO_DUO_RANKED` · `PLAY_STYLE` · `OPTIONAL` · 디비전 없는 티어 `GOLD`(우리 사다리는 `GOLD_4` 꼴이고 이름은 seed 의 것이어야 한다) 같은 옛 이름을 남기기.
- **게임 설정 조회 API · 사람 검색 API · 확정된 파티 조회(`parties`) · 아바타 업로드 · 예약 API 를 백엔드에 만들어 달라고 하기.** 미정이거나 두지 않기로 한 것이다(§5).
- **`dev` 서버를 띄워 놓고 안 내리기.** `pkill -f` 금지. `git add -A` · `git add .` 금지(IntelliJ 가 자동 스테이징한다). **푸시는 지시가 있을 때만.**
- 세 백엔드 폴더의 "하지 말 것"(루트 `CLAUDE.md` §6)은 프런트에도 그대로 걸린다 — 특히 상태를 바꾸는 GET 을 전제하지 않는다, 지원 게임 넷째 · 공개 사용자 탐색 · 길드 · 피드 · 프리미엄 화면을 만들지 않는다.

## 5. 미정 — 정하기 전에 임의로 구현하지 마라

| 항목 | 상황 |
|---|---|
| **방 카드 보드를 어떻게 살릴까** | codex 가 더한 `src/rooms/*` 는 원본 백엔드의 `/rooms` API(방 = 독립 자원 · 채팅 · 예약 시간 · `CONFIRM/REOPEN` 토글 · 자동 마감)를 전제한다. 우리 백엔드는 **글이 곧 방**이고 목록은 `GET /posts?game=`, 입장은 `POST /rooms/{roomId}/members`, 확정은 되돌릴 수 없다. **카드 보드를 우리 목록 위에 다시 그릴지, legacy 홈(`LegacyRecruitmentHome`)을 우리 게시판에 맞출지, 둘 다 남길지** — 4단계에 닿기 전에 묻는다. 대조표는 `START_HERE.md` §4 |
| 방 채팅(`RoomConversation`) | 우리 서버에 텍스트가 없다 — WebRTC DataChannel 로 브라우저끼리 주고받아야 한다(D-9). 화면을 남길지 · DataChannel 을 언제 만들지 |
| 예약 시간(`availableFrom` · `REALTIME/RESERVATION`) · 자동 마감(`autoClose`) · `REOPEN` | 우리 글에는 시각 칸이 없고 예약은 `app:reservation`(Lambda · 시작 안 함)의 일이다. 확정은 되돌릴 수 없다(`REOPEN` 없음). 자동 마감 · 유휴 시간은 우리 방에 없다(수명 600초 · 접속 확인만) |
| 티어 범위(`desiredTierRange` · `TierRangePicker`) | 글에 티어 범위 칸이 없다. 자동 합류(`POST /posts/auto-join`)의 티어 판정은 gameconfig `tier-range` 로 서버가 한다 — 화면에 범위 선택을 남길지 |
| 게임 · 모드 · 티어 상수의 자리와 모양 | seed 의 모드 키(LoL `RANKED_SOLO` · `RANKED_FLEX_2/3/5` · `ARAM_2~5` · `NORMAL_2~5`, VALORANT `COMPETITIVE_DUO/TRIO` · `UNRATED_DUO/TRIO`, PUBG `NORMAL_/RANKED_ × DUO/SQUAD × TPP/FPP`)와 사다리(LoL `IRON_4`…`CHALLENGER` 32 · VALORANT `IRON_1`…`RADIANT` 26 — **디비전 순서가 반대다** · PUBG 27)를 어느 파일에 어떤 모양으로 둘지 · 한글 라벨 · 정원(`targetPartySize`)을 같이 베낄지 |
| 대응물 없는 화면의 처지 | 예약 · DM · 듀오 제안 · 아바타 · Ready/PLAYING · 알림함 · `GET /match-requests/history`. 남긴 채 숨길지 · 지울지 |
| `tests/*.spec.ts`(브라우저 없는 단위 테스트 5개) | e2e 를 지울 때 같이 지울지(Playwright 의존을 없애려면 지워야 한다) |
| `BOARD_CHANGED` 재요청 묶기 간격 | 백엔드도 미정(`platform/CLAUDE.md` §3.2). 몇 초에 최대 1번 |
| 재발급 흐름 | access 가 15분이라 **프런트가 만료 전에 `POST /api/v1/auth/refresh` 를 불러야 한다**(서버 장치 없음). 401 마다 한 번 재발급 → 재시도(원본의 single-flight 구조를 쿠키 방식으로 옮기면 된다) · SSE 401 뒤 재발급 → `EventSource` 새로. **모든 기기 로그아웃은 백엔드에도 없다** |
| 라우트 이름 | `/signup/social` · `/login` · `/settings` 는 백엔드가 정한 경로다(따른다). 그 밖(`/app/home` · `/app/party/:id` …)은 프런트 마음이지만 **방 화면의 경로에 `roomId`(게시판은 숫자 · 자동 매칭은 UUID)를 쓴다** |
| WebRTC `signal` 의 모양 | 서버는 열어 보지 않는다 — `platform-api.md` "`signal` 의 권장 모양"(`kind: description \| candidate`)을 따른다. 원본의 `WebRtcSignalMessage{signalType: OFFER\|ANSWER\|ICE}` 를 옮길 때 그 모양으로 |

## 6. 작업 방식 · 커밋

- **서브 에이전트로 처리한다**(루트 `CLAUDE.md` §4) — 조사 · 여러 파일 변경은 맡기고, 프롬프트에 읽을 파일(이 문서 셋 · 해당 계약 절) · 건드리면 안 되는 것(세 백엔드 폴더) · 정해진 결정(§3)을 다 적는다. 묻기만 한 것은 바로 답한다.
- **단계 순서는 `START_HERE.md` §2 다.** 순서를 바꾸려면 먼저 묻는다. 단계마다 `npm run build` · `npm run typecheck` 가 통과해야 커밋한다.
- **커밋** — `type(scope): subject`. scope 는 **`frontend`**(문서만이면 `docs`). type 은 `feat fix docs style refactor perf test build ci chore revert`. subject 한글 50자 마침표 없음, body 한글 3줄 이내. 문서(`CLAUDE.md` · `START_HERE.md` · `README.md` · `docs/**`)와 코드는 한 커밋에 섞지 않는다. `git add` 는 경로를 명시한다. 마지막 줄 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **코드를 읽고 한 추측은 확인한 뒤에 말한다.** 백엔드가 어떻게 답하는지는 계약 → 그 폴더의 코드 순으로 확인하고, 실제로 띄워 본 것만 "확인했다" 고 적는다.

## 7. 환경 함정

- **OneDrive 아래다** — `npm ci` · 빌드가 느리고 **파일 쓰기가 가끔 "Error writing file" 로 실패한다**(OneDrive 잠금). 잠깐 뒤 다시 하거나 셸(`cat > 파일 <<'EOF'`)로 쓴다. `node_modules/` · `dist/` 는 `.gitignore` 에 있다.
- WSL(Ubuntu 22.04) · zsh · node v22 · npm 10. zsh 에서 변수는 `${x}` 로 감싼다(`$ID:h` 가 특수 문법이다). `docker.exe` 출력의 `\r`.
- **worktree 라 IntelliJ 에 Commit 탭이 없다 — 커밋은 WSL 에서.** Claude 가 파일을 고친 뒤에는 IntelliJ 에서 `Ctrl+Alt+Y`(디스크에서 다시 읽기).
- git 2.34.1 이라 `worktree add --orphan` 이 없다 — 이 브랜치는 `worktree add --detach` → `checkout --orphan` → `rm -r --cached` → `clean -fd` 로 만들었다.
- `dev` 서버는 `ss -ltnp | grep :5173` 로 PID 를 찾아 `kill <PID>`. 백엔드 셋을 띄우는 법과 6379 · 5432 · `queuemate-v2-*` 금지는 루트 `START_HERE.md` §6 · §8.

## 8. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 지금 상태 · 통합 계획 5단계 · API 대조표 · 방 카드 보드 대조 · 미정 | `START_HERE.md`(이 폴더) |
| 원본 README(원본 백엔드 전제 — 참고만) | `docs/UPSTREAM_README.md` |
| 전체 지도 · 앱 사이의 약속 · 로컬 띄우기 · 환경 함정 | 루트 `START_HERE.md` · 루트 `CLAUDE.md` |
| platform 계약 — 공통 · 토큰 · 소셜 로그인 · 게임 프로필 · 글 · 방 · 자동 매칭 파티의 방 · 자동 합류 · 친구 · 차단 · 신고 · 알림 · P-1~P-31 | `../platform/contracts/platform-api.md` |
| matching 계약 — 매칭 요청 · heartbeat · 제안 · `MatchRequestView` · 열린 이름 물음(#4 · #5) | `../matching/contracts/openapi.yaml` · `README.md` · `events.md` |
| SSE — 경로 · 인증 · heartbeat · `retry:` · 게시판 채널 | `../notification/CLAUDE.md` §5 · §7 |
| gameconfig 원본(모드 키 · 티어 사다리 · 정원) | `../matching/seed/gameconfig.redis` · `../matching/docs/GAME_CONFIG.md` |
| 결정 로그 D-1~D-44 | `../matching/docs/11_DECISION_LOG.md` |
| 원본 저장소의 방 API 계약(우리 계약이 아니다 — 방 카드 보드가 무엇을 기대하는지 볼 때만) | `Port-051/QueueMate` `codex/room-card-board` 의 `contracts/rooms.openapi.yaml`(이 폴더에 가져오지 않았다 — `START_HERE.md` §4 에 요약) |
