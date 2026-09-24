# START_HERE — platform 을 처음 여는 세션과 사람을 위한 안내

> **이 파일은 일이 진행되면 갱신한다.** 단계가 끝나면 §1 "지금 어디까지 됐나"와 §3 의 표를 고쳐라. 정해진 미정 사항은 §3 · §4 에서 지우고
> `CLAUDE.md` §7 에서도 뺀다(해당 절로 옮긴다).

> **지위(2026-09-21) — 먼저 읽어라.** 이날 빈 뼈대에서 1 · 2 · 3 · 5단계와 7단계의 일부(친구 · 신고)가 구현됐다. 소유자가 "네가 platform 을 만들어 봐라"고 맡겼고, 문서가 "미정 — 임의로 정해 구현하지 마라"로 묶어 둔 것들을
> **Claude 가 정해 구현했다. 소유자는 아직 항목별로 검토하지 않았다.** 그렇게 정한 것의 원본은 **`contracts/platform-api.md`**(머리의 "지위" 문단 · 맨 아래 "원본에 올려야 할 것" P-1~P-16 — P-11 ~ P-16 은 소유자가 직접 정한 것이다)이고,
> **docs/11 에 D-항목이 하나도 없다.** 소유자가 직접 정한 것은 이 파일에서 "소유자 확정" · "소유자 지시"라고 따로 적었다. **남은 미정(§4)에는 "묻고 정한다"가 그대로 걸린다.**

**먼저 읽을 것 — 이 순서로.**

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **이 파일** | 이 서비스가 어디까지 됐고, 무엇을 어떤 순서로 만들며, **다음에 닿기 전에 무엇을 물어야** 하는지, 소유자가 무엇을 검토해야 하는지 |
| 2 | `CLAUDE.md` | **규칙.** 하는 일 / 안 하는 일(§2), 계약(§3), 설계 규칙(§5 · §5.1), 미정 사항(§7 · §7.1 · §7.2), 일하는 방식과 운영 규칙(§9) |
| 3 | `README.md` | 이 서비스가 하는 일의 짧은 소개와 "만드는 순서"의 원본 |
| 4 | **`contracts/platform-api.md`** | **이 폴더에서 정한 계약** — 경로 · 요청/응답 · 에러 코드 · access 토큰 · 입장권 · 알림. 코드를 고치기 전에 읽는다. 경로나 스키마를 바꾸면 같이 고친다 |
| 5 | `docs/ROOM_CONTRACT.md` | 이 앱이 읽는 `room` 의 Redis 키와, `room` 계약 가운데 이 앱에 걸리는 부분. **`party` 패키지를 만지기 전에** 읽는다 |
| 필요할 때 | 옆 폴더의 문서 (§5 문서 지도) | 전체 그림 · 결정의 이유 · 알림 봉투 · 로컬 환경 함정 |

> **출처 표기.** `docs/11 D-…` · `contracts/events.md` · `HANDOFF.md` 처럼 폴더 이름 없이 적은 것은 옆 폴더 `matching` 기준이다(`CLAUDE.md` 머리와 같다). **`contracts/platform-api.md` 만은 이 폴더의 것이다.**
> **출처가 안 붙은 사실은 정해지지 않은 것이다.**

---

## 1. 지금 어디까지 됐나 (2026-09-21)

**platform 은 1 · 2 · 3 · 5단계와 7단계의 일부가 됐다.** 빈 뼈대에서 하루 만에 계정과 인증 · 소셜 로그인 · 게임 프로필 · 차단 · 파티 모집 게시판(글 · 목록 · 입장권 · 게시판 신호) · 방장 확정의 기록 · 친구 · 신고가 들어왔다.
**막힌 것은 6단계(SQS)와 거기에 매인 것들이다. 2026-09-23 소유자 결정 셋 — refresh 토큰 · LoL 전적 동기화(Riot API) · 게시판 목록의 페이지 나누기(커서 방식) — 과 2026-09-24 소유자 결정 하나 — `mode` · `tier` 를 gameconfig 에서 검증하는 것 — 이 붙었다**(바로 아래). **아직 없는 것은 VALORANT · PUBG 의 전적과 `verified` 를 켜는 길이다**(§4 C). 정한 것의 지위는 머리의 "지위" 를 본다.

> **2026-09-22 소유자 결정 둘 — 코드 · 마이그레이션 · 계약이 전부 이 모양이다.**
> ① **모든 테이블의 PK 가 `bigint GENERATED ALWAYS AS IDENTITY` 가 됐고, 사용자의 식별자가 둘로 갈렸다** — **`userId` 는 사용자 번호**(`account.users.id`)이고 가입 · 로그인에 쓰는 **로그인 아이디는 `loginId`** 다(중복은 409 `LOGIN_ID_TAKEN`).
> **2026-09-19 의 "사용자 id 는 가입할 때 정한 로그인 아이디(문자열)"를 개정하는 것이다**(docs/11 D-4 와 얽힌다 — `CLAUDE.md` §3.5 · `contracts/platform-api.md` P-11). **`matching` 의 `block/Block.java` 를 `Long` 으로 바꿔야 한다(아직 안 바꿨다 — 그 폴더의 일이다).**
> ② **스키마별 DB 롤은 두지 않는다**(아래 2단계 · §4 D). **둘 다 docs/11 에 D-항목이 없다.**

> **2026-09-23 소유자 결정 — refresh 토큰을 붙였다.** access 수명이 `PT24H`(`TEMP-NO-REFRESH`)에서 **`PT15M`** 으로 줄고, 불투명 UUID 의 refresh(**`P7D` — 7일**)가 그것을 이어 준다.
> 재발급은 **`POST /api/v1/auth/refresh`**(본문 없이 쿠키 `qm_refresh` 로만)이고 **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN`** 이다. 로그아웃이 Redis 의 그 줄과 쿠키 둘을 지운다.
> **`TEMP-NO-REFRESH` 표식이 `backend/src` 에 0건이 됐다.** 경로 · 쿠키 이름 · 401 을 하나로 합친 것 · Redis 가 죽었을 때의 갈림은 **Claude 가 정했다**(`CLAUDE.md` §5.1 (라) · (마) · `contracts/platform-api.md` "refresh 토큰" · P-15. **docs/11 에 D-항목이 없다**).

> **2026-09-23 소유자 결정 — LoL 의 전적을 Riot API 에서 긁는다**(P-13). **긁는 시점은 둘이다** — 게임 계정을 연결할 때(`PUT …/game-accounts/{game}`)와 **모집 글을 쓸 때**(`POST /posts` — 방장의 그 게임 계정. `synced_at` 이 `platform.riot.freshness` 기본 30분 안이면 건너뛴다).
> 둘 다 **커밋된 뒤에 비동기로** 돌고(전용 풀 — Riot 을 20여 회 부르는 동안 응답을 붙잡을 수 없다) **실패해도 본 요청은 성공이다**(기존 `stats` 줄을 지우지 않는다). **`external_id` 에 `puuid` 를 적지만 `verified` 는 켜지 않는다.** **평점은 넣지 않는다**(OP.GG 가 스스로 계산한 값이라 Riot API 에 없다 — 소유자가 그날 다시 확인했다).
> **`RIOT_API_KEY` 가 없으면 긁는 일 자체를 하지 않는다** — 기동은 정상이고 `stats` 가 `null` 로 남는다. Redis 락 `qm:riot:sync:{gameAccountId}`(`SET … NX EX 60` · 못 잡으면 건너뛴다 · **Redis 가 죽으면 락 없이 진행한다**) · 게임별 구현을 인터페이스(`account.stats.GameStatsProvider`) 뒤에 둔 것 · 429 를 재시도하지 않는 것은 **Claude 가 정했다**
> (`CLAUDE.md` §7 "게임 계정 연동" · `contracts/platform-api.md` "전적을 긁는 것" · P-13. **docs/11 에 D-항목이 없다**). **VALORANT · PUBG 는 구현이 없어 그 둘의 `stats` 는 늘 `null` 이다**(§4 C).

> **2026-09-23 소유자 결정 — 게시판 목록을 커서로 나눈다**(P-14). **`limit` 은 없으면 20 · 최대 100** 이고 벗어나면 **400 `VALIDATION_FAILED`** 다 — **상한으로 잘라 주지 않는다.** `cursor` 는 불투명한 문자열이고 응답의 **`nextCursor`** 로 이어 받는다(더 볼 것이 없으면 `null`).
> **`BOARD_CHANGED` 를 받은 프런트는 커서를 쓰지 않는다 — 펼친 만큼을 `limit` 으로 맨 위부터 다시 받는다.** 커서의 속(정렬 셋을 base64url 로 · 서명하지 않는다) · 차단으로 모자라면 최대 3번 더 읽어 채우는 것(`platform.board.max-refills`) · `nextCursor` 를 마지막으로 **읽은** 줄로 잡은 것은 **Claude 가 정했다.**
> **대가 하나 — 만료 · 확정 옮겨 적기가 읽은 글에만 걸린다**(목록 깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다. 입장권 발급도 같은 판단을 하므로 "목록에 안 보이지만 들어갈 수 있는 죽은 방" 은 생기지 않는다)
> (`CLAUDE.md` §7.1 · `contracts/platform-api.md` "목록의 페이지 나누기" · P-14. **docs/11 에 D-항목이 없다**).

> **2026-09-24 소유자 결정 — 이 앱이 `qm:gameconfig:*` 를 읽어 `mode` 와 `tier` 를 검증한다**(P-16). 값의 원본은 **`matching/seed/gameconfig.redis`** 이고 **읽는 키는 둘뿐이다** — 모드별 설정 HASH 를 `EXISTS`, 티어 사다리 ZSET 을 `ZSCORE` 로 본다. **쓰지 않고, 내용도 `:tier-range:` 도 읽지 않으며 seed 를 심지도 않는다.**
> **Redis 를 못 읽으면 통과시킨다(fail-open)** — 검증만 건너뛰고 글 쓰기 · 게임 계정 연결은 성공한다(WARN 한 줄. 목록 조회가 이미 fail-open 인 것과 결을 맞춘 것이다). **`mode` 가 필수가 됐다** — 옛날에는 "30자까지의 자유 문자열(없어도 된다)"이었고, **고치기(`PATCH`)에서 빈 문자열로 모드를 비우는 길이 없어졌다**(빈 문자열은 400. `description` 은 그대로 비운다). `tier` 는 여전히 없어도 되고 값이 있을 때만 본다.
> **이것은 `CLAUDE.md` §2 · §11 의 "매칭 Redis 키(… `qm:gameconfig:*` …) 접근 — 예외가 없다"와 docs/11 #15 를 개정한다** — gameconfig 는 `matching` 이 쓰는 상태가 아니라 **운영자가 배포 때 심는 공유 설정**이고(seed 머리가 "앱은 부팅 시 설정을 밀어넣지 않고 Redis 에서 읽기만 한다"고 적었다) `matching` 도 읽는 쪽이다.
> **나머지 넷(`qm:party:*` · `qm:user:*` · `qm:proposal:*` · `qm:lock:*`)의 금지는 그대로다.** 클래스의 자리(`common/gameconfig/GameConfigKeys` · `GameConfigReader`) · 에러의 글귀(400 `VALIDATION_FAILED`) · 안 심긴 gameconfig 도 통과시키는 것 · `PATCH` 에서 모드 검증이 방장 · 상태 검사보다 먼저인 것은 **Claude 가 정했다**
> (`CLAUDE.md` §3.6 · `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16. **docs/11 에 D-항목이 없다**).

| 순서 | 만들 것 | `README.md` 의 단계 | 상태 |
|---|---|---|---|
| 0 | 뼈대 — Spring Boot 4.1.1 · Java 21 · PostgreSQL + Flyway · Redis · 헬스 엔드포인트 | (1단계의 "기반" 가운데 일부) | ✅ 2026-09-21 |
| 1 | 계정과 인증 — 가입 · 로그인 · 로그아웃 · `users/me` · 닉네임 변경 · 게임 계정(자기신고) · RS256 JWT 쿠키 · `Origin` 검사 · 로그인 실패 제한. 스키마 `account` | 1 | ✅ 2026-09-21. **식별자가 둘로 갈렸다**(2026-09-22 — `users.id`(사용자 번호) · `login_id`. 가입 · 로그인 본문은 `loginId` 이고 JWT 의 `sub` 는 사용자 번호의 문자열이다). **refresh 토큰이 붙었다**(2026-09-23 소유자 결정 — access 15분 · refresh 7일 · 재발급 `POST /api/v1/auth/refresh`. `TEMP-NO-REFRESH` 가 없어졌다). **게임 계정의 `tier` 를 gameconfig 의 티어 사다리로 검증한다**(2026-09-24 소유자 결정 — 값이 있을 때만 본다. Redis 를 못 읽으면 통과시킨다) |
| 1+ | **소셜 로그인(카카오 · 디스코드)** — 소유자 지시. 인가 코드 흐름을 `RestClient` 로 직접(세션 없음). 처음 온 사람은 아이디 · 닉네임을 정한다 | (순서에 없던 것) | ✅ 2026-09-21. **가짜 제공자로만 테스트했다 — 실제 키로는 붙여 보지 않았다** |
| 1+ | **게임 프로필 · 전적 스냅숏**(`account.game_account_stats`) — 목록의 한 줄이 게임마다 다른 정보를 보여 주려는 것(소유자 지시) | (순서에 없던 것) | 🟡 **LoL 은 채워진다**(2026-09-23 소유자 결정 — Riot API. 게임 계정 연결 · 글 쓰기 때 커밋 뒤 비동기. P-13). **VALORANT · PUBG 는 구현이 없어 `stats` 가 늘 `null` 이고, `verified` 를 켜는 길도 아직 없다**(§4 C) |
| 2 | 차단 — `social.blocks`(`matching` 이 읽는 모양) · API 셋. **스키마별 DB 롤은 두지 않는다**(2026-09-22 소유자 결정 — `qm_matching` 롤과 GRANT 를 뺐다. `CLAUDE.md` §3.5) | 2 | ✅ 2026-09-21. **`blocker_id` · `blocked_id` 가 bigint 가 됐다**(2026-09-22) — **`matching` 의 `Block.java` 를 `Long` 으로 바꿔야 한다. 아직 안 바꿨다 — 그 폴더의 일이고, 바꾸기 전까지 `matching` 은 이 테이블을 읽다가 깨진다** |
| 3 | 모집 글 · 목록(방 안 사람 카드 · 게임 프로필 · 차단 거르기) · 입장권 발급 · 게시판 채널 신호 발행 | 3 | ✅ 2026-09-21 (이 앱의 몫). **목록의 페이지 나누기가 붙었다**(2026-09-23 소유자 결정 — 커서 방식 · `limit` 기본 20 · 최대 100. P-14). **글의 `mode` 가 필수가 되고 gameconfig 의 모드 이름으로 좁혀졌다**(2026-09-24 소유자 결정 — P-16. 고치기에서 빈 문자열로 비우는 길이 없어졌다). **`room` 의 입장권 검증은 아직 없다** — `room` 폴더의 일이다 |
| 4 | (이 앱의 몫이 없다 — 시그널 `POST` 는 `room`, 음성은 프런트) | 4 | — (`room` 의 시그널 `POST` 는 돼 있다) |
| 5 | 방장 확정의 기록 — 글을 "확정"으로 · 파티원 기록. 길 둘(`POST …/confirm` + 목록이 발견) | 5 | ✅ 2026-09-21 (`CLAUDE.md` §7.2 (가)의 방향을 받았다) |
| 6 | 자동 매칭 파티 — `ProposalConfirmed.fifo` 소비(SQS · outbox) | 6 | ⛔ **막혔다.** `matching` 쪽 발행이 없고 SQS 배선 · 메시지 본문이 미정이다. `party.outbox` 도 만들지 않았다 |
| 7 | 친구 · 신고 · 최근 함께한 사람(읽기) · 알림 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` | 7 | 🟡 **친구 · 신고 · 최근 함께한 사람(읽기) · 알림 둘이 들어왔다**(2026-09-21 — `social/V6__friends_reports_recent_players.sql` · `social` 패키지 · `common/push/`. 계약은 `contracts/platform-api.md` "친구 · 신고 · 최근 함께한 사람"). **구현한 세션이 `./gradlew cleanTest build` 로 전부 통과시켰다**(2026-09-21 — 아래). **`PartyClosed.fifo` 는 6단계와 같이 막혀 있다 — 최근 함께한 사람을 채우는 주체가 없다**(읽으면 늘 빈 목록이다) |

- **테스트가 164건이고 전부 통과한다**(2026-09-24 — **`mode` · `tier` 의 gameconfig 검증을 붙인 뒤** `./gradlew cleanTest test` 를 돌려 확인했다. tests **162 → 164** · failures 0 · errors 0 · **skipped 0**. 늘어난 둘은 `common/gameconfig/GameConfigReaderTest` 다. 테스트용 PostgreSQL 5433 · Redis 6380. 그 앞의 162건은 같은 날 refresh 토큰을 붙인 뒤에 센 것이다 — 155 → 162).
  **2026-09-22 에는 앱을 띄워 curl 로도 끝까지 확인했다** — 가입(`loginId`) → 409 `LOGIN_ID_TAKEN` → 로그인 → JWT 의 `sub` 가 사용자 번호(`"517"`) → 게임 계정 → 글 쓰기(`postId` 가 숫자) → `room` 인 척 방 키를 넣고 목록(인원 · 카드 · `filledPositions` · **승/패가 있는 전적과 없는 전적 둘 다**) → 입장권(클레임은 문자열 · 본문은 숫자) → 차단하면 목록에서 빠지고 입장권이 404 → 경로의 숫자가 아닌 id 는 400 · 본문의 것은 404 → 글을 고치고 지울 때 `qm:pubsub:board` 에 `BOARD_CHANGED` 둘 → SIGTERM 에 곱게 내려갔다.
  **건너뛴 것을 통과로 읽지 마라**(5432 · 6379 면 전부 건너뛴다 — §6).
- **테스트가 확인하는 것**(파일 이름이 곧 목록이다 — `backend/src/test/java/com/queuemate/platform/`) —
  - `account/` — 마이그레이션의 제약(`AccountMigrationTest`), 가입 · 로그인 · 로그아웃과 **중복 가입을 DB 제약이 막는 것**(`AuthApiTest`), `users/me` · 닉네임 · 게임 계정(`UserApiTest`), 게임 프로필과 전적 스냅숏을 읽는 쪽(`GameProfileTest`), 로그인 실패 제한(`LoginThrottleTest`), **재발급과 rotation · 실패가 전부 같은 401 인 것 · 로그아웃이 Redis 의 refresh 를 지우는 것 · Redis 가 죽으면 로그인은 되고 재발급은 401 인 것**(`RefreshTokenApiTest` — 2026-09-23), 소셜 로그인 — **가짜 제공자 서버**(`oauth/FakeOAuthProvider`)로 시작 · 콜백 · 처음 온 사람의 가입 · 설정이 없을 때(`oauth/*`), **LoL 전적 동기화 — 가짜 Riot 서버**(`stats/FakeRiotApi`)로 긁는 것 · 솔로랭크가 없을 때 · 연승 · 글 쓰기의 신선도 · Riot 이 실패해도 옛 전적을 그대로 두는 것 · 태그 없는 닉네임 · **LoL 만 긁는 것** · 락이 동시 갱신을 건너뛰게 하는 것(`stats/GameStatsSyncTest` — 2026-09-23)과 **키가 없으면 아무것도 하지 않는 것**(`stats/GameStatsNotConfiguredTest`).
  - `common/` — 쿠키의 토큰 검증과 **`token_use` 가 다른 토큰을 거절하는 것**(`AuthenticationTest`), 키를 읽고 만드는 것(`JwtKeysTest`), **다른 출처의 `Origin` 을 단 POST 가 거절되는 것**(`OriginCheckTest`), **gameconfig 에서 `mode` · `tier` 가 있는 값인지 보는 것과 Redis 를 못 읽거나 gameconfig 가 안 심겼을 때 통과시키는 것(fail-open)**(`gameconfig/GameConfigReaderTest` — 2026-09-24).
  - `social/` — 차단 API(`BlockApiTest`), **`social.blocks` 의 컬럼 이름 · 자료형과 제약이 약속대로 있는 것**(`BlockMigrationTest` — PostgreSQL 에서만 볼 수 있다. 롤 · GRANT 는 보지 않는다 — 스키마별 DB 롤을 두지 않는다.
    **두 칸이 bigint 가 됐으므로 `matching` 의 `Block.java` 쪽을 `Long` 으로 맞춰야 한다** — 그 폴더의 일이다). 친구 요청 · 수락 · 거절 · 거두기 · 목록 · 끊기(`FriendApiTest`), **상대의 채널을 구독해서 알림을 확인하는 것**(`FriendPushTest`)과 Redis 가 죽어도 본 작업이 되는 것(`FriendPushRedisDownTest`), 신고(`ReportApiTest`), 최근 함께한 사람의 읽기(`RecentPlayerApiTest`), 제약(`SocialMigrationTest`).
  - `party/` — 글 쓰기 · 고치기 · 지우기(`PostApiTest`), **목록 · 카드 · 차단 거르기 · `room_seen_at` · 만료 · 입장권 · 방장 확정의 길 둘**(`PostBoardTest`), **페이지 나누기 — `limit` 의 기본값 · 상한(벗어나면 400) · 커서로 다음 장 · 1페이지를 본 뒤 새 글이 올라와도 2페이지에 중복 · 누락이 없는 것(`offset` 이면 깨진다) · 차단으로 모자라면 채우는 것과 그 상한 · 깨진 커서가 400 인 것 · SQL 문장 수가 페이지 크기에 비례해 늘지 않는 것**(`PostPagingTest` — 2026-09-23), **Redis 를 못 읽을 때 만료 판정을 하지 않는 것**(`PostServiceRedisDownTest`), **게시판 채널을 구독해서 신호를 확인하는 것**(`BoardSignalTest`), 제약(`PartyMigrationTest`).
- **`room` 을 실제로 같이 띄워 확인한 기록은 없다.** 테스트는 **`room` 인 척 방 키를 직접 넣는다**(`party/PostTestSupport` — 키 이름을 main 의 상수에서 가져오지 않고 글자로 적었다. 상수에 오타가 나면 테스트가 깨지게 하려는 것이다).
  `room` · `notification` 과 같은 Redis 에 띄워 글 쓰기 → 방 만들기 → 목록 → SSE 로 신호가 오는 것까지 보는 것은 **아직 남은 확인이다**(§6 에 방법이 있다).
- **그 밖에 정한 것**(전부 Claude 가 정했다 — 포트만 소유자 확정) — 패키지는 도메인 먼저(`common` · `account` · `social` · `party` — `backend/…/platform/package-info.java`), 에러 본문은 `matching` · `room` 과 같은 `{code, message, details: [문자열]}`,
  **readiness 에 `db` 를 넣었고 Redis 는 넣지 않았다**, **포트 8082 확정**, Hibernate 의 제약 위반 WARN 로거를 껐다(중복을 DB 제약 위반으로 아는 것이 정상 경로라서다 — `application.yaml` 의 주석), 마이그레이션의 **버전 번호는 스키마 폴더 사이에서 하나의 순서**다(V1 baseline · `account/` V2 · V3 · `social/` V4 · `party/` V5 · `social/` V6),
  개발용 키는 `backend/.dev-keys/`(gitignore — 옆 서비스는 그 `public.pem` 으로 검증한다. §2).
- **넣지 않은 것** — AWS SDK(SQS)와 outbox 테이블(6단계 — `build.gradle` 에 넣을 자리만 주석으로 남겼다), **VALORANT · PUBG 의 전적 클라이언트**(LoL 의 Riot 클라이언트는 들어왔다 — `account/stats/RiotApiClient`. 나머지 둘은 `GameStatsProvider` 자리만 있다), Spring 의 `oauth2-client`(세션을 쓴다 — 쓰지 않기로 했다), jjwt, **H2**(PostgreSQL 의 제약을 그대로 재현하지 못한다 — `CLAUDE.md` §5 · docs/11 D-3).
  이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기, 소셜 가입자의 비밀번호 만들기, 신고의 처리 화면 · 제재도 없다(`contracts/platform-api.md`).
- **뼈대의 기준은 Spring Initializr 다.** `start.spring.io` 에 Boot 4.1.1 · Java 21 · Gradle 로 `web, validation, data-jpa, postgresql, flyway, data-redis, actuator, lombok` 을 넣어 받은 것에서 시작했다(wrapper 는 Gradle 9.7.1 — `room/backend` 와 같은 파일).
  그 뒤 `spring-boot-starter-security` · `spring-boot-starter-security-oauth2-resource-server`(Boot 4 에서 바뀐 이름이다)와 그 테스트 스타터를 더했다. `application.properties` 대신 `application.yaml`, `.gitignore` 는 폴더 루트의 것을 쓴다(`room` 과 같다).
- **헬스 주소가 `room` 과 다르다.** `CLAUDE.md` §5 의 `/health/live` · `/health/ready` 를 맞추려고 actuator 를 루트(`management.endpoints.web.base-path: /`)에 두고 health 그룹 `live` · `ready` 를 만들었다.
  그래서 이 앱에는 `/actuator/health` 가 **없다**. `/health` · `/health/liveness` · `/health/readiness` 도 답한다(`/info` 도 열려 있다). `room` 은 기본값이라 `/actuator/health/liveness` 다. **`/health/ready` 는 이제 DB 를 본다.**
  (health 그룹의 `additional-path` 로 하려 했으나 한 마디짜리 주소(`/livez` 같은)만 받아 기동이 실패했다 — 뼈대를 만들 때 실행해서 본 것이다.)

**시스템 전체 — 무엇이 돼 있고, 무엇이 platform 을 기다렸으며, 이제 무엇을 받아 갈 수 있는가**

| 서비스 | 상태 (2026-09-21) | platform 을 기다리던 것 → 지금 |
|---|---|---|
| `room` | **방 안의 기능이 전부 됐다.** 요청 아홉 가지(방 만들기 · 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 방 안 사람 목록 · 내 방 찾기 · 시그널) · 알림 · 확정한 방의 방장 넘기기 · 게시판 채널 신호 발행. 테스트 120건 (docs/11 D-21 · D-23 · `../room/START_HERE.md` §1) | **인증과 입장권 → 이 앱이 줄 것은 다 있다**(access 토큰 · 입장권 · 공개 키 — §2). **`room` 쪽의 검증은 아직 없다** — 지금도 요청의 `userId` 를 그대로 믿고 누구나 아무 `roomId` 로 방을 만든다(`TEMP-NO-PLATFORM`). 글과 목록이 생겼으니 **방을 찾아 들어갈 화면의 백엔드는 있다** |
| `notification` | 개인 알림(SSE)과 **게시판 채널 구독**(기동 때 `qm:pubsub:board` 하나를 구독해 모든 연결로 전달 — D-22)이 됐다. 인증만 없다(`?userId=`) | **access 토큰의 발급 → 됐다.** 검증을 붙이는 것은 `notification` 폴더의 일이다(`CLAUDE.md` §5.1 (가) · (바) — SSE 는 연결할 때만 검증한다). **이 앱의 게시판 신호 발행 → 됐다**(글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정될 때). `notification` 을 거쳐 SSE 로 도착하는 것까지는 아직 보지 않았다 |
| `matching` | 매칭 엔진은 됐다(세 게임 · 제안 · 수락 · 확정 · 만료 · 상태 조회 · 409 `IN_ROOM`). **outbox · SQS · DB 스키마는 없다**(`HANDOFF.md` §0) | ① `social.blocks` 테이블 → **생겼다**(`social/V4__social_blocks.sql`. **스키마별 DB 롤은 두지 않는다**(2026-09-22 소유자 결정) — `matching` 은 별도 롤 없이 같은 방식으로 붙어 읽는다. `matching` 을 같은 DB 에 붙여 INV-6 이 실제 데이터로 도는 것은 **아직 보지 않았다**) ② `ProposalConfirmed.fifo` 를 받아 줄 소비자(③) → **없다**(6단계 — 막혔다) ③ `status=PARTY` 를 누가 푸는가(① — 검토한 방향은 `CLAUDE.md` §7.2 (라)) → **그대로 미정** ④ 인증 → 토큰은 있다. 검증은 `matching` 폴더의 일이다 |
| `app:reservation`(Lambda) | 이 컴퓨터에 폴더가 없다 (docs/11 D-15) | `reservation` 스키마의 마이그레이션을 누가 실행하는가(`CLAUDE.md` §7 — 미정. 정해지기 전에 이 앱에 넣지 않는다) |
| 프런트 | 이 컴퓨터에 없다 (queueMate 본 저장소 `feature/frontend`) | 계약 → **이 폴더에서 정한 것이 `contracts/platform-api.md` 에 있다.** 원본에 platform 엔드포인트가 이미 있으면 그쪽과 맞춰야 한다(P-1 — 이 컴퓨터에서는 볼 수 없었다). |

## 2. 옆 서비스의 임시 처리(`TEMP-NO-PLATFORM`)와, 이제 이 앱에서 받아 갈 수 있는 것

순서대로라면 platform 의 1~2단계(계정 · 차단)가 먼저였지만 **`room` 을 먼저 만들었다.** 그래서 `room` 은 platform 이 줄 것을 비워 둔 채 돈다 — **이 앱이 생긴 지금도 그대로다. 채우는 것은 `room` 폴더의 일이다.**
아래 표의 앞 두 칸은 `../room/START_HERE.md` §2 를 옮겨 적은 것이다(원본이 바뀌면 그쪽이 우선한다).

| platform 이 있으면 | 없는 채로 만든 지금 (`room`) | platform 이 주는 것 (2026-09-21 — 전부 있다) |
|---|---|---|
| 사용자는 **쿠키의 access 토큰**에서 꺼낸다 (docs/11 D-14) | 요청이 보낸 **`userId` 쿼리 파라미터를 그대로 믿는다**(`?userId=`) — 누구나 아무 `userId` 로 입장하고 강퇴할 수 있다 | **access 토큰.** 쿠키 `qm_access` 의 JWT(RS256). 공개 키 PEM 을 환경변수로 받아 `NimbusJwtDecoder.withPublicKey()` 로 검증하고 토큰은 쿠키에서 꺼낸다(`CLAUDE.md` §5.1 (가) · (아)). `userId` 를 받는 자리를 전부 바꾸는 것은 `room` 폴더의 일이다 |
| 입장할 때 **입장권의 서명을 검증**한다. 방 식별자 · 방장이 누구인지도 입장권이 말해 준다 (D-16) | **입장권 검증 자리가 비어 있고 `roomId` 만 받는다** | **입장권.** `POST /api/v1/posts/{postId}/ticket` 이 내준다. 같은 키의 JWT 이고 수명은 60초다(아래) |
| 글이 **모집 중인지 · 차단 관계가 아닌지**는 platform 이 확인하고 입장권을 내준다 | **확인하지 않는다.** `room` 은 원래 글의 상태도 차단 관계도 모른다 | 입장권을 내줄 때 이 앱이 확인한다(모집 중 · 방 안의 **전원**과 차단 대조). 입장권 검증이 붙으면 저절로 지켜진다 |
| 방장은 **글을 쓴 사람**이다 — 입장권이 "이 방의 방장은 이 사람"이라고 말해 준다 | **"방 만들기"(`POST /api/v1/rooms/{roomId}`)를 부른 사람이 방장이다. 누구나 아무 `roomId` 로 만든다** | 입장권의 **`host_id`**. 방장도 같은 요청으로 입장권을 받는다(`sub` = `host_id`). 방 만들기가 그대로 남는지 방장의 첫 입장에 합쳐지는지는 `room` 쪽에서 다시 정한다(`../room/CLAUDE.md` §7) |

**옆 서비스가 받아 가는 것 — 원본은 `contracts/platform-api.md` "access 토큰" · "입장권"이다**(쿠키 이름 · `iss` · 키 · 환경변수 이름 · `token_use` 는 소유자 확정, 입장권의 형식은 Claude 가 정했다 — P-2 · P-3).

| 무엇 | 값 |
|---|---|
| 공개 키 | 로컬 — **`backend/.dev-keys/public.pem`**(X.509 PEM. 이 앱을 키 없이 한 번 띄우면 생긴다. git 에 없다). 운영 — 환경변수로 넣는다(이 앱의 이름은 `JWT_PUBLIC_KEY` 다. 옆 서비스가 같은 이름을 쓸지는 그 폴더에서 정한다). JWKS 엔드포인트는 없다 |
| 공통 검증 | 서명(RS256 · 헤더에 `kid`) · **`iss` = `queuemate-platform`** · `exp` · **그리고 `token_use` 가 기대한 값인지** |
| access 토큰 | 쿠키 **`qm_access`**. 클레임 `iss` · **`sub`(= `userId` — 사용자 번호를 십진 문자열로 찍은 것이다. `"42"`. 로그인 아이디가 아니다, 2026-09-22)** · `iat` · `exp` · `jti` · **`token_use` = `access`**. 수명은 **15분**이다(2026-09-23 소유자 결정으로 24시간에서 줄었다 — 이어 주는 것은 refresh 다. **옆 서비스는 refresh 를 받을 일이 없다** — 쿠키 `qm_refresh` 는 `Path=/api/v1/auth/refresh` 라 이 앱의 그 요청에만 실려 간다) |
| 입장권 | 응답 본문의 `ticket`(쿠키가 아니다 — 브라우저가 `room` 에 어떻게 실어 보낼지는 `room` 의 계약에서 정한다). 클레임 `iss` · `iat` · `jti` · `sub`(입장하려는 사람) · **`token_use` = `room_ticket`** · **`room_id`**(글의 id = `roomId`. 주소의 `{roomId}` 와 같은지 본다) · **`host_id`**(글을 쓴 사람) · `exp`(발급 후 60초). **`sub` · `room_id` · `host_id` 는 전부 숫자를 십진 문자열로 찍은 것이다**(응답 본문의 `roomId` · `hostId` 는 숫자다) |

- **`sub` 는 사용자 번호의 십진 문자열이다**(2026-09-22 소유자 결정). 검증하는 쪽은 서명 · `iss` · `exp` · `token_use` 에 더해 **`sub` 가 `^[0-9]{1,19}$` 인지도 본다**(이 앱의 `common/security/TokenClaims.SUBJECT_PATTERN` · `JwtConfig#jwtDecoder` 가 그 본보기다. 그 검사 자체는 Claude 가 정했다).
  **옆 서비스가 `userId` 를 문자열로 받아 쓰는 자리(요청 파라미터 · Redis 채널 · 방 키 · 멤버 SET)는 `"42"` 가 들어가도 코드를 바꿀 것이 없다.** 바꿔야 하는 것은 하나다 — **`matching` 의 `block/Block.java`**(`social.blocks` 의 두 칸이 bigint 가 됐다. `Long` 으로 — 아직 안 바꿨고 그 폴더의 일이다).
- **`token_use` 를 반드시 본다.** access 토큰과 입장권을 **같은 키로 서명한다** — 서명 · `iss` · `exp` 만 보면 입장권을 access 토큰으로(또는 그 반대로) 쓸 수 있다. `sub` 가 둘 다 `userId` 라서 그대로 통한다.
  JOSE 헤더의 `typ` 으로 가르지 않은 이유는 Spring Security 의 기본 디코더가 `typ` 이 `JWT` 가 아니면 거절하기 때문이다(`contracts/platform-api.md`). 소셜 가입 대기 토큰(`token_use` = `social_signup`)도 같은 키다 — 옆 서비스는 받을 일이 없다.
- **`roomId` 는 글의 id 다**(P-4. **bigint identity** — 2026-09-22 전에는 UUID 였다. `room` 의 주소와 방 키에는 **숫자를 십진 문자열로** 쓴다 — `qm:room:123:host`).
  그동안 멤버 SET 에 아무 문자열이나 들어올 수 있다 — **사용자 번호로 팔 수 없는 값(숫자가 아닌 것)은 이 앱이 방 키를 읽는 자리에서 건너뛴다**(카드 · 파티원 · `memberCount` 어디에도 들지 않는다). **숫자이지만 가입하지 않은 번호는** 카드에 `nickname: null` 로 남고 파티원으로도 기록된다.
- **그 자리는 `room` 의 코드에 표시돼 있다.** `grep -rn "TEMP-NO-PLATFORM" ../room/backend/src` — 2026-09-21 에 돌려 보니 11줄이다.
  **채우는 것은 `room` 폴더에서 한다** — 여기서 옆 폴더의 파일을 고치지 않는다(`CLAUDE.md` §9).
- `matching` · `notification` 도 같은 방식이다 — 요청의 `userId` 파라미터를 그대로 믿는다. 그쪽에는 `TEMP-NO-PLATFORM` 표식이 없다.
- **전환의 시점과 순서는 정해져 있다**(2026-09-21 소유자 확정 — `CLAUDE.md` §5.1 (아)). 로그인이 도는 것을 본 뒤(**이제 돈다**), 서비스별로 따로 옮긴다. 순서는 **`room` → `notification` → `matching`.** **아직 하나도 옮기지 않았다.**
  각 서비스에 "쿠키가 없으면 `userId` 파라미터를 받는" 개발용 스위치를 잠깐 남겨도 된다 — 임시 처리로 표시하고 운영에서는 끈다. `Origin` 검사(§5.1 (다))도 세 서비스에 걸리는 결정이다 — 언제 넣는지는 정하지 않았다(설정의 이름은 이 앱이 `ALLOWED_ORIGINS` 로 정했다). **바꾸는 작업은 각 폴더에서 한다.**
- **이 앱에는 임시 처리 표식이 하나도 없다** — 2026-09-23 에 refresh 를 붙이며 `TEMP-NO-REFRESH` 를 걷었다(`grep -rn "TEMP-NO-REFRESH" backend/src` 가 **0건**이다). 임시 처리를 다시 넣게 되면 같은 방식으로 표시한다 — 표식 문구를 담은 주석을 달아 검색 한 번으로 전부 찾을 수 있게 한다. 표시 없는 임시 처리는 구멍으로 남는다.

## 3. 만드는 순서

원본은 `README.md` "만드는 순서"다. 여기는 그 표에 **단계마다 끝났는지 확인한 것**과, 남은 단계의 **먼저 물을 것**을 붙인 것이다.
**한 번에 한 단계만 한다.** 순서를 바꾸려면 먼저 묻는다(`CLAUDE.md` §9).

- 끝난 단계(✅)의 "정한 것"은 **`contracts/platform-api.md` 가 원본이다** — 소유자 확정이라고 적지 않은 것은 Claude 가 정했고 소유자가 항목별로 검토하지 않았다(§4 "소유자가 검토해야 하는 것").
- 남은 단계의 "먼저 물을 것"은 `CLAUDE.md` §7 · §7.1 · §7.2 에서 그 단계에 걸리는 것이다 — **정하지 않고 구현하지 않는다.** 정한 것은 `contracts/platform-api.md` 에 적는다(`CLAUDE.md` §3.1).
- 검증은 **PostgreSQL 에서만** 의미가 있다 — H2 는 제약을 그대로 재현하지 못한다(`CLAUDE.md` §5).

| 단계 | 만드는 것 | 정한 것 / 이미 정해져 있는 것 | 확인한 것 (✅) / **먼저 물을 것** (남은 단계) |
|---|---|---|---|
| **0** ✅ | 뼈대 | `CLAUDE.md` §4 | 뼈대를 만들 때 실행해서 봤다 — `./gradlew build`, `bootRun` 으로 `/health/live` · `/health/ready` 200, Flyway 기록 테이블, SIGTERM 에 graceful shutdown |
| **1** ✅ | 기반(스키마 `account` · Flyway) + 계정(가입 · 로그인 · 로그아웃 · `users/me` · 닉네임 변경 · 게임 계정) · JWT 발급 · Spring Security(`oauth2-resource-server`) · `Origin` 검사 · 로그인 실패 제한 | **소유자 확정** — 인증 세부 여덟 가지와 그 남은 것(`CLAUDE.md` §5.1 — RS256 · 쿠키 `qm_access` · `iss` = `queuemate-platform` · RSA 2048 · `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`/`JWT_KEY_ID` · `ALLOWED_ORIGINS` · `ACCESS_TOKEN_TTL` · `token_use`), **닉네임은 대소문자를 구별한다**, 로그인 실패 제한은 사례(OWASP · NIST)를 찾아 정하라고 맡겼다. **소유자 결정(2026-09-23)** — **refresh 토큰을 붙인다**(access `ACCESS_TOKEN_TTL` 기본 `PT15M` · refresh `REFRESH_TOKEN_TTL` 기본 `P7D`(7일). `TEMP-NO-REFRESH` 가 없어졌다 — P-15). **소유자 결정(2026-09-22)** — **모든 PK 는 `bigint identity` 이고 `userId`(사용자 번호)와 `loginId`(로그인 아이디)를 가른다**(2026-09-19 의 결정 · docs/11 D-4 를 개정한다. JWT 의 `sub` 가 숫자 문자열이 되고 중복 아이디는 409 `LOGIN_ID_TAKEN` 이다 — P-11). **Claude 가 정했다** — 경로와 에러 코드, 로그인 아이디 `^[a-z0-9_]{4,20}$`, 본문의 id 를 문자열로 받아 파는 것(숫자가 아니면 404 `USER_NOT_FOUND`)과 `sub` 가 숫자인지 보는 검증, 비밀번호 8~72자 · 72바이트 · `{bcrypt}`, 닉네임 2~16자 · 유일, 테이블 컬럼(`account/V2`), 로그인 실패 제한의 수치(15분 안에 5번 → 1분부터 두 배씩 최대 15분 · 영구 잠금 없음 · Redis 가 죽으면 통과), 에러 본문 · 패키지 · readiness(§1) | `AuthApiTest` · `UserApiTest` · `AccountMigrationTest` · `AuthenticationTest` · `JwtKeysTest` · `OriginCheckTest` · `LoginThrottleTest` · `RefreshTokenApiTest`(§1). **그 토큰을 `room` · `notification` · `matching` 이 공개 키로 검증할 수 있는지는 그 폴더에서 본다 — 아직 안 봤다**(§2). **refresh 도 됐다**(2026-09-23 소유자 결정 — access 15분 · refresh 7일. 남은 것은 §4 A). **게임 계정의 `tier` 검증이 붙었다**(2026-09-24 소유자 결정 — gameconfig 의 티어 사다리. `GameConfigReaderTest` · `UserApiTest`) |
| **1+** ✅ | **소셜 로그인 — 카카오 · 디스코드**(순서에 없던 것. 2026-09-21 소유자 지시) | **소유자 지시** — 넣는다. **Claude 가 정했다** — 로그인 아이디는 여전히 가입할 때 정하는 것이라 **처음 온 사람은 로그인 아이디 · 닉네임을 정하는 한 단계**를 거친다(제공자의 회원 번호는 사용자 번호가 되지 않는다), Spring 의 `oauth2-client` 를 쓰지 않고 `RestClient` 로 직접(세션 없음), `state` 는 쿠키 `qm_oauth_state`, 가입 대기는 JWT 쿠키 `qm_social_signup`(`token_use` = `social_signup`), 제공자에게서 회원 번호와 닉네임만 받는다(`contracts/platform-api.md` "소셜 로그인" · P-7) | `SocialLoginApiTest` · `OAuthProviderSpecTest` · `OAuthNotConfiguredTest` — **가짜 제공자 서버로만.** **실제 카카오 · 디스코드 키로는 붙여 보지 않았다** — 앱 등록과 Redirect URI 등록은 소유자가 해야 한다(§4 F) |
| **1+** 🟡 | **게임 프로필 · 전적 스냅숏 · LoL 전적 동기화**(순서에 없던 것. 목록의 한 줄이 게임마다 다른 정보를 보여 주려는 것 — 소유자 지시) | **소유자 결정(2026-09-23)** — **LoL 의 전적을 Riot API 에서 긁는다**(긁는 시점 둘 — 게임 계정 연결 · 모집 글 쓰기 · 신선도 30분 · 비동기이고 실패해도 본 요청은 성공 · `verified` 는 켜지 않는다 · **평점은 넣지 않는다** — P-13). **Claude 가 정했다** — 게임 프로필의 모양(`game` · `gameNickname` · `verified` · `tier` · `mainPosition` · `server` · `stats`), `stats` 의 모양과 게임별 `detail`, `winRate` · `kda` 는 이 앱이 계산한다, 목록을 그릴 때 게임사 API 를 부르지 않는다(스냅숏만 읽는다), Redis 락 `qm:riot:sync:{gameAccountId}` · 게임별 구현을 `GameStatsProvider` 뒤에 둔 것 · 429 를 재시도하지 않는 것(`contracts/platform-api.md` "게임 프로필" · "전적을 긁는 것" · P-8 · P-13. 테이블은 `account/V3`) | `GameProfileTest`(읽는 쪽) · `GameStatsSyncTest` · `GameStatsNotConfiguredTest` — **가짜 Riot 서버로만. 실제 키로는 붙여 보지 않았다.** **먼저 물을 것(§4 C)** — VALORANT · PUBG 의 전적, `verified` 를 켜는 법, 운영의 API 키(Riot 의 승인 · VALORANT 는 별도 승인) |
| **2** ✅ | 차단 — `social.blocks` · API 셋 | **이미 정해져 있던 것** — 테이블의 모양(`CLAUDE.md` §3.5 · docs/11 D-4), `BlockChanged.fifo` 는 만들지 않는다(D-12). **소유자 결정(2026-09-22)** — **스키마별 DB 롤은 두지 않는다.** 앱 하나가 롤 하나로 붙고 `matching` 은 별도 롤 없이 `social.blocks` 를 읽는다(`qm_matching` 롤과 D-1 의 GRANT 를 뺐다 — docs/11 #17 · D-1 을 개정하는 것이라 D-항목으로 남겨야 한다). **Claude 가 정했다** — 경로와 에러 코드, 내가 차단한 사람만 보여 준다, 차단은 친구 관계 · 이미 같은 방에 있는 상태를 건드리지 않는다(`contracts/platform-api.md` "차단") | `BlockApiTest` · `BlockMigrationTest`(UNIQUE 가 두 번 차단을 막는다 · 컬럼이 `matching` 의 `Block.java` 와 맞는다). **`matching` 을 같은 DB 에 붙여 차단 관계인 두 사람이 한 파티가 안 되는지는 아직 안 봤다.** **먼저 물을 것(§4 D)** — 운영에서 앱이 붙는 DB 계정 |
| **3** ✅ | 모집 글 쓰기 · 고치기 · 지우기, 게시판 목록(인원 · **방 안 사람 카드(게임 프로필 전체)** · `filledPositions` · **차단 거르기**), **입장권 발급**, 게시판 채널 신호 발행 | **이미 정해져 있던 것** — 방 키와 읽는 법(`CLAUDE.md` §3.3), 차단은 방 안의 누구와든(D-20), 게시판 채널(D-22). **소유자 지시** — 목록의 한 줄이 게임마다 다른 정보를 보여 주고 방 안 전원을 그 자리에서 다 보여 준다 · 게시판은 실시간으로 바뀌어야 한다. **Claude 가 정했다** — **`roomId` = 글의 id**(2026-09-22 소유자 결정으로 **bigint identity** 가 됐다 — 정할 때는 UUID 였다), **입장권의 형식**(60초 · `room_id` · `host_id` — 셋 다 숫자를 문자열로 찍는다), **`room_seen_at` 으로 "아직 안 만들어진 방"을 가른다**(본 적 없는 글은 10분 뒤에만 만료), 만료 · 확정 글은 10분 동안 목록에 남는다, 만석은 `full: true`, 글의 내용(`voice` · `purpose` · `conditions` · `wantedPositions`)과 정렬, 모집 중인 글은 한 사람에 하나, 숨겨진 글은 404, **Redis 를 못 읽으면 만료 판정을 하지 않고 입장권은 503**(`contracts/platform-api.md` "모집 글 · 목록 · 입장권" · "입장권" · P-3 ~ P-5 · P-8). **소유자 결정(2026-09-23)** — **목록을 커서로 나눈다**(`limit` 기본 20 · 최대 100 · 벗어나면 400 이고 잘라 주지 않는다 · `nextCursor` 로 이어 받는다 · 신호를 받으면 커서 없이 펼친 만큼 다시 받는다 — P-14). 커서의 속 · 차단으로 모자랄 때 채우기(최대 3번) · `nextCursor` 를 마지막으로 **읽은** 줄로 잡은 것은 **Claude 가 정했다** | `PostApiTest` · `PostBoardTest` · `PostPagingTest` · `PostServiceRedisDownTest` · `BoardSignalTest`(구독해서 확인한다) · `PartyMigrationTest` · **`GameConfigReaderTest`**(2026-09-24 — 글의 `mode` 가 gameconfig 에 있는 모드여야 하고, Redis 를 못 읽으면 통과시킨다). **테스트는 `room` 인 척 방 키를 직접 넣었다 — `room` · `notification` 을 같은 Redis 에 띄워 끝에서 끝까지 본 적은 없다**(§6 에 방법). 이 앱이 방 키에 쓰지 않는 것은 코드로 보인다(`party/room/` 에 쓰는 명령이 없다) — `MONITOR` 로 본 기록은 없다 |
| **4** | (이 앱의 몫이 없다) | 시그널 `POST` 와 `WEBRTC_SIGNAL` 은 `room` 의 일이고 돼 있다(D-16 · D-21). 음성은 프런트 | 브라우저 두 개로 음성 통화가 된다(프런트가 있어야 볼 수 있다) |
| **5** ✅ | 방장 확정의 기록 — 글을 "확정"으로, 파티원을 DB 에 | **이미 정해져 있던 것** — 확정 요청은 `room` 이 받고 이 앱은 확정 표시 키와 멤버 SET 을 읽는다 · 전원 · 되돌릴 수 없다 · `room` 의 키에 쓰지 않는다(D-21) · 확정한 방은 방장이 바뀔 수 있다(D-23). **Claude 가 정했다** — **`CLAUDE.md` §7.2 (가)의 길 둘을 받았다**(① 브라우저가 `room` 의 확정 뒤에 `POST …/confirm` ② 목록 · 단건 · 입장권이 발견), **멱등은 `parties` 의 `UNIQUE (post_id)` 가 지킨다**(2026-09-22 — PK 가 `roomId` 였던 때는 PK 가 지켰다), 읽는 시점의 멤버 SET 이 확정 순간과 다를 수 있는 것은 감수한다, **확정된 글은 방장 키가 없어도 만료시키지 않는다**(`contracts/platform-api.md` "방장 확정의 기록" · P-6) | `PostBoardTest`. **남은 물음** — 확정된 방의 기능(Ready 등), "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가, 둘 다 빠지고 방이 사라지는 구멍을 막을지(`CLAUDE.md` §7.1) |
| **6** ⛔ | 자동 매칭 파티 — `ProposalConfirmed.fifo` 소비 | outbox + SQS FIFO · at-least-once · **소비는 멱등** · DLQ + `maxReceiveCount` · `MessageGroupId` 는 `proposalId`. Kafka 등으로 바꾸지 않는다(docs/11 #21 · `CLAUDE.md` §3.4). `party.parties` 에 `source` = `MATCH` 의 자리만 있다 | **먼저 물을 것(§4 B)** — **`matching` 이 UUID 문자열로 내려 주는 `partyId` 를 어디에 두는가**(`parties.id` 가 bigint 다 — 2026-09-22 로 생긴 미정이다). 메시지 본문. 파티 id 를 `proposalId` 와 같게 둘지. AWS SDK 를 언제 들일지 · 로컬에서 SQS 를 무엇으로 흉내 낼지. **자동 매칭 파티의 방과 입장권을 누가 발급하는가 · `status=PARTY` 해제 — 검토한 방향이 있다, 정하지 않았다(`CLAUDE.md` §7.2 (나) · (다) · (라))**. `matching` 쪽 발행(`HANDOFF.md` §0-1 ③)이 같이 필요하다. 끝났는지 확인 — 같은 메시지를 **두 번** 넣어도 파티가 하나인지, 실패한 메시지가 DLQ 로 가는지 |
| **7** 🟡 | 친구 · 신고 · 최근 함께한 사람(`PartyClosed.fifo` 발행 + 소비) | `PartyClosed.fifo` 의 소비자는 이 앱 하나(D-13). 신고는 접수만 받는 최소 형태(`README.md`). **Claude 가 정했다** — 친구 요청 · 친구 · 신고 · 최근 함께한 사람(읽기)의 경로, 알림 **`FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`**, 대기 중 요청은 같은 방향에 하나(DB), 아이디를 정확히 알아야 요청을 보낸다(검색 API 없음 — 공개 사용자 탐색 금지)(`contracts/platform-api.md` "친구 · 신고 · 최근 함께한 사람" · P-9) | `FriendApiTest` · `FriendPushTest` · `FriendPushRedisDownTest` · `ReportApiTest` · `RecentPlayerApiTest` · `SocialMigrationTest` — 통과 여부는 §1 의 표 7 을 본다. **먼저 물을 것** — `PARTY_*` 알림의 이름과 `payload`, **파티가 "닫혔다"를 무엇으로 판단하는가**(이 폴더의 문서와 docs/11 에서 찾지 못했다 — 이것이 없어 최근 함께한 사람을 채우지 못한다), `PartyClosed.fifo` 는 6단계의 SQS 배선에 매여 있다 |

## 4. 다음에 닿기 전에 물어야 하는 것

**문서에 "미정"으로 남은 것만 모았다. 여기 적힌 선택지는 문서에 나온 것이고 추천이 아니다 — 임의로 정해 구현하지 않는다.** 2026-09-21 에 소유자가 맡겨 Claude 가 정해 구현한 것이 있다고 해서 **남은 것도 그렇게 해도 된다는 뜻이 아니다.**
정해지면 `CLAUDE.md` §7 에서 빼고 해당 절로 옮기며, `contracts/platform-api.md` 에 적고, `matching` 폴더에서 docs/11 에 D-항목으로 남겨야 한다고 알린다.

**A. refresh 토큰에 남은 것 — 도입은 됐다** (`CLAUDE.md` §5.1 (라) · (마) · §7)

**2026-09-23 소유자 결정으로 붙었고 구현됐다** — access `PT15M` · refresh `P7D`(7일) · 불투명 UUID 를 Redis `qm:auth:refresh:{uuid}` → 사용자 번호에 두고 · rotation 은 `GETDEL` 한 번 · **`POST /api/v1/auth/refresh`**(본문 없이 쿠키 `qm_refresh` 로만. `Path` 는 그 경로 하나다) ·
실패는 전부 같은 401 `INVALID_REFRESH_TOKEN`(그때도 refresh 쿠키를 지운다) · 로그아웃이 Redis 의 줄과 쿠키 둘을 지운다 · 소셜 로그인 · 소셜 가입도 같은 쿠키 둘을 받는다(`common/security/SessionCookies` · `RefreshTokenApiTest` · `contracts/platform-api.md` "refresh 토큰" · P-15). **남은 것 —**

1. **한 사용자의 refresh 를 한꺼번에 끊는 길이 없다** — 사용자별 토큰 집합을 두지 않았고 `KEYS`/`SCAN` 은 쓰지 않는다. 그래서 **비밀번호를 바꾸거나 계정이 털렸을 때 모든 기기를 로그아웃시킬 수 없다.** 둘지부터가 미정이다(access denylist 를 두지 않는 것과 같이 본다 — `CLAUDE.md` §5.1 (라)).
2. **프런트의 재발급 흐름** — access 가 만료되기 전에 프런트가 불러야 하고 **서버 쪽 장치는 없다**(SSE 쪽은 §5.1 (바)). 프런트가 이 컴퓨터에 없어 **맞춰 본 적이 없다.**
3. **Redis 가 죽은 동안 로그인한 사람은 refresh 가 없다** — access 15분이 끝나면 다시 로그인한다. 로그아웃도 Redis 장애 때는 그 refresh 를 못 지운다(최대 7일 남는다). **감수하는 것으로 두었다**(`contracts/platform-api.md` "refresh 토큰").

**B. SQS 배선 · 메시지 본문 · `status=PARTY` 해제 — 6단계와 `PartyClosed.fifo`** (`CLAUDE.md` §3.4 · §7 · §7.2 (나) · (다) · (라))

0. **`matching` 의 `partyId`(UUID 문자열)를 `party.parties` 의 어디에 두는가** — `parties.id` 는 bigint identity 다(2026-09-22 소유자 결정). 새 칸에 담을지 · 다른 값을 쓸지 **정해지지 않았다.** 계약이 적은 "자동 매칭 파티는 `roomId = partyId` 다" 한 줄도 여기에 걸린다.
1. `ProposalConfirmed` 의 **메시지 본문**, 파티 id 를 `proposalId` 와 같게 둘지(`matching` 은 클라이언트에 `partyId` = `proposalId` 를 이미 내려 준다). `matching` 쪽 발행이 없다(`HANDOFF.md` §0-1 ③) — **`matching` 폴더의 일과 같이 가야 한다.**
2. **AWS SDK 를 언제 들이는가, 로컬에서 SQS 를 무엇으로 흉내 내는가.** `party.outbox` · `social.outbox` 테이블과 relay 도 이때 만든다.
3. **자동 매칭 파티의 방과 입장권**(§7.2 (다)) — 검토한 방향은 "파티 입장권은 `matching` 이 발급한다"였다. **입장권은 그 뒤에 이 앱의 개인 키로 서명하는 것으로 정해졌다** — `matching` 이 발급하게 되면 `CLAUDE.md` §5.1 (가)의 "서명은 이 앱만 한다"와 부딪힌다. 계약이 적은 "자동 매칭 파티는 `roomId = partyId` 다" 한 줄도 이 미정 위에 서 있다.
4. **`status=PARTY` 해제**(§7.2 (라)) — 검토한 방향은 "아무도 지우지 않는다(짧은 수명 + 입장 표시 키가 이어받는다)"였다. **정하지 않았다.**
5. **파티가 "닫혔다"를 무엇으로 판단하는가** — 문서에서 찾지 못했다. 이것이 정해져야 `PartyClosed.fifo` 와 **최근 함께한 사람**이 채워진다(지금은 읽는 쪽만 있고 늘 빈 목록이다). 게시판으로 확정된 파티도 최근 함께한 사람의 기준이 되는가(`CLAUDE.md` §7.1).
6. `PARTY_*` 알림의 이름과 `payload`, 확정된 파티를 조회하는 경로(`parties`).

**C. 게임사 API 연동 — 남은 것은 VALORANT · PUBG 와 `verified` 다** (`CLAUDE.md` §7 "게임 계정 연동" · `contracts/platform-api.md` "게임 프로필" · "전적을 긁는 것")

**LoL 은 2026-09-23 소유자 결정으로 붙었고 구현됐다**(P-13) — 긁는 시점 둘(게임 계정 연결 · 모집 글 쓰기 · 신선도 `platform.riot.freshness` 기본 30분) · 커밋 뒤 **비동기**(전용 풀) · **실패해도 본 요청은 성공**이고 기존 `stats` 를 지우지 않는다 · `external_id` 에 `puuid` · Redis 락 `qm:riot:sync:{gameAccountId}` ·
`RIOT_API_KEY` 가 없으면 긁지 않는다(기동은 정상) · 부르는 것은 `account-v1` · `summoner-v4` · `league-v4` · `match-v5` (`GameStatsSyncTest` — **가짜 Riot 서버로만**). **남은 것 —**

1. **운영의 API 키.** Riot 의 승인이 필요하고 **키는 소유자가 받아야 한다**(§4 F). 지금까지 **실제 키로는 붙여 보지 않았다** — 개발용 키의 한도(2분당 100회) 때문에 `match-count` 를 20 으로 두었고, 운영에서 그 값과 타임아웃을 얼마로 둘지 정해진 적이 없다.
2. **VALORANT · PUBG 의 전적** — 구현이 없어 그 둘의 `stats` 는 늘 `null` 이다(게임별 구현은 `account.stats.GameStatsProvider` 뒤에 있어 구현 하나를 더하면 된다). **VALORANT 의 전적 API 는 Riot 의 별도 승인이고** PUBG 는 다른 API 다. 무엇을 채우는가(그 테이블의 비는 칸 · `detail`)도 같이 정해야 한다.
3. **`verified` 를 켜는 법** — Riot(RSO) 인증. **지금은 켜는 길이 없다**(`puuid` 를 알아낸 것은 본인 확인이 아니다). 인증하지 않은 자기신고 계정을 목록에 어떻게 보여 주는가.
4. OP.GG 의 "MVP · Ace" 배지와 평점은 Riot API 에 없어 넣지 않았다 — **평점은 소유자가 2026-09-23 에 넣지 않는 것으로 다시 확인했다.** 배지를 그대로 둘지.

**D. 운영의 DB 롤** (`CLAUDE.md` §3.5 · §7)

**스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정).** 앱 하나가 롤 하나로 붙는다. `matching` 은 별도 롤 없이 `social.blocks` 를 읽는다 — `social/V4` 의 `CREATE ROLE qm_matching` 과 GRANT 를 뺐고, `CREATEROLE` 문제도 같이 없어졌다.
스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다. docs/11 #17 의 "스키마별 DB 롤" 대목과 D-1 의 GRANT 를 개정하는 것이다 — `matching` 폴더에서 D-항목으로 남겨야 한다(아직 안 남겼다). 남은 것 —

1. 이 앱이 운영에서 붙는 DB 계정의 이름과 권한(로컬은 `postgres` 슈퍼유저다 — `DB_USER` · `DB_PASSWORD`), 마이그레이션 계정과 앱 계정을 나눌지. `matching` 이 붙는 계정은 `matching` 폴더의 일이다.
2. Flyway 기록 테이블의 자리(기본값 `public`), JSON 로그의 형식(운영에서 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` 로 켜게 해 두었다 — 형식은 정해진 적이 없다).

**E. 옆 서비스의 전환** (`CLAUDE.md` §5.1 (아) · §2)

1. 순서는 정해져 있다 — **`room` → `notification` → `matching`.** 각 폴더의 일이다. **언제 시작하는가**, 개발용 스위치(`?userId=` 를 받는)를 남길지.
2. `room` 이 입장권을 어디에 실어 받는가(헤더 · 본문 · 쿼리), 방 만들기가 방장의 첫 입장에 합쳐지는가(`../room/CLAUDE.md` §7) — `room` 의 계약에서 정하고 이 폴더의 `docs/ROOM_CONTRACT.md` 를 다시 떠 온다.
3. `Origin` 검사를 세 서비스에 언제 넣는가. 공개 키를 담는 환경변수의 이름을 네 서비스가 같게 쓸지.
4. 게시판 채널 이름의 **원본 상수를 어느 서비스에 둘지** — 지금 세 앱이 각자 적어 두었다(세 값이 같아야 한다 — `CLAUDE.md` §3.2). 프런트가 재요청을 묶는 간격.

**F. 소유자가 직접 해야 하는 것**

1. **카카오 · 디스코드의 앱 등록 · 키 · Redirect URI 등록.** Redirect URI 는 `OAUTH_REDIRECT_BASE_URL` + `/api/v1/auth/oauth/{provider}/callback` 이다(로컬 기본값이면 `http://localhost:8082/api/v1/auth/oauth/kakao/callback`). 키를 환경변수(`KAKAO_CLIENT_ID` · `DISCORD_CLIENT_ID` · `DISCORD_CLIENT_SECRET` …)로 넣고 **실제로 한 번 붙여 본다** — 가짜 제공자로만 테스트했다.
2. **docs/11 결정 로그** — 2026-09-21 에 정한 것이 하나도 안 올라갔다. `matching` 폴더에서 해야 한다(인증 세부 §5.1 은 #16 을 개정한다).
3. 계약 원본(queueMate 본 저장소 `feature/frontend` 의 `contracts/`)을 받아 올 수 있는가 — 거기에 platform 엔드포인트가 이미 있으면 `contracts/platform-api.md` 와 맞춰야 한다(P-1).

### 소유자가 검토해야 하는 것 — `contracts/platform-api.md` 의 P-1 ~ P-16

**P-1 ~ P-10 은 Claude 가 정해 구현했고 소유자가 항목별로 검토하지 않은 것 전부다.** 원본은 그 파일 맨 아래 "원본에 올려야 할 것" 표다 — 여기는 가리키기만 한다. 뒤집으면 코드 · 계약 · `CLAUDE.md` 를 같이 고친다.
**P-11 ~ P-16 은 소유자가 직접 정한 것이라 검토가 아니라 뒤처리가 남았다**(docs/11 에 올리기 · `matching` 의 `Block.java`). 아래 표는 **P-11 · P-13 · P-14 · P-15 · P-16 을** 옮겨 적었다 — P-12(전적 스냅숏을 한 테이블에 담는 것)는 그 파일의 표를 본다.

| # | 무엇 | 특히 볼 것 |
|---|---|---|
| P-1 | 엔드포인트 전부(경로 · 스키마 · 에러 코드) | 원본 `openapi.yaml` 과 맞는가. 자원 이름 `posts` · `friend-requests` 는 새로 지은 것이다 |
| P-2 | access 토큰의 클레임 · `token_use` · 쿠키 이름 `qm_access` | 소유자 확정이다 — docs/11 에 올리는 것이 남았다 |
| P-3 | 입장권의 형식 | 수명 60초 · `room_id` · `host_id`. `room` 의 계약에도 걸린다 |
| P-4 | `roomId` = 글의 id(**숫자** — P-11 로 UUID 에서 바뀌었다) | 어느 문서에도 없던 것이다(예전 이 절의 E-2) |
| P-5 | `room_seen_at` 으로 "아직 안 만들어진 방"을 가르는 법, 만료 · 확정 글의 10분 보존 | 본 적 없는 글은 10분 뒤에만 만료된다 — 방 만들기를 끝내 안 부른 글이 10분 동안 목록에 남는다. **목록 조회(GET)가 글의 상태를 옮겨 적는다**(`CLAUDE.md` §3.3) |
| P-6 | 방장 확정을 길 둘로 기록하는 것 | `confirm` 을 로그인한 누구나 부를 수 있다(읽은 것만 기록한다). 멤버 SET 이 확정 순간과 다를 수 있는 것을 감수한다 |
| P-7 | 소셜 로그인과 "처음 오면 아이디를 정한다" | docs/00 의 계정 정의에 걸린다. 본 저장소의 v2 설계(Discord 로그인)와 다른 모양이다. 프런트의 경로(`/signup/social` 등)를 맞춘 적이 없다 |
| P-8 | 게임 프로필 · 전적 스냅숏 · 글의 `voice` · `purpose` · `conditions` | 세 게임 모두 가로 한 줄이고 펼치지 않는다(소유자 지시). **LoL 의 전적을 가져오는 법은 정해졌다**(P-13) — VALORANT · PUBG 는 미정이다 |
| P-9 | 친구 · 신고 · 최근 함께한 사람의 경로와 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` | 원본 `events.md` 의 `FRIEND_*` 이름과 맞는가. 차단 관계면 친구 요청이 404 `USER_NOT_FOUND` 다 |
| P-10 | 로그인 실패 제한(429 `TOO_MANY_LOGIN_ATTEMPTS`) | 수치(15분 · 5번 · 1분부터 두 배 · 최대 15분)와 "Redis 가 죽으면 통과". 세는 열쇠만 **로그인 아이디**다 |
| **P-11** | **모든 PK 는 `bigint identity` · `userId` 는 사용자 번호 · 로그인 아이디는 `loginId` 로 따로**(2026-09-22 **소유자 결정** — 2026-09-19 의 결정과 docs/11 D-4 를 개정한다) | **검토가 아니라 뒤처리다** — ① docs/11 에 D-항목으로 남기기 ② **`matching` 의 `block/Block.java` 를 `Long` 으로**(아직 안 바꿨다 — 그 폴더의 일이다) ③ 자동 매칭 파티의 `partyId`(UUID)를 `parties`(bigint) 어디에 둘지는 **미정**(§4 B) |
| **P-13** | **LoL 전적 동기화**(2026-09-23 **소유자 결정** — Riot API · 긁는 시점 둘(게임 계정 연결 · 모집 글 쓰기) · 신선도 30분 · 비동기이고 실패해도 본 요청은 성공 · `external_id` 는 `puuid` · **`verified` 는 켜지 않는다** · **평점은 넣지 않는다**) | **검토가 아니라 뒤처리다** — docs/11 에 D-항목으로 남기기. **남은 것** — VALORANT · PUBG 의 전적 · `verified` 를 켜는 법 · 운영의 API 키(§4 C). Redis 락 키 `qm:riot:sync:{gameAccountId}` 가 늘었다(이 앱의 접두사다) · 429 를 재시도하지 않는 것 · 게임별 구현을 인터페이스 뒤에 둔 것은 Claude 가 정했다 |
| **P-14** | **게시판 목록의 페이지 나누기(커서 방식)**(2026-09-23 **소유자 결정** — `limit` 기본 20 · 최대 100 · 벗어나면 400 이고 **잘라 주지 않는다** · `cursor` 는 불투명 · `nextCursor` 로 이어 받는다 · 신호를 받으면 커서 없이 펼친 만큼 다시 받는다) | **검토가 아니라 뒤처리다** — docs/11 에 D-항목으로 남기기. **특히 볼 것** — **만료 · 확정 옮겨 적기가 읽은 글에만 걸리게 됐다**(목록 깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다 — 받아들인 대가다). 커서의 속(정렬 셋 · base64url · 서명하지 않는다) · 차단으로 모자랄 때의 채우기(최대 3번) · `nextCursor` 를 마지막으로 **읽은** 줄로 잡은 것은 Claude 가 정했다 |
| **P-15** | **refresh 토큰**(2026-09-23 **소유자 결정** — 불투명 UUID · Redis `qm:auth:refresh:{uuid}` · `P7D` · 쿠키 `qm_refresh` · `POST /api/v1/auth/refresh` · rotation · access 가 `PT15M` 으로 줄었다) | **검토가 아니라 뒤처리다** — docs/11 에 D-항목으로 남기기(#16 의 access denylist 개정과 같은 묶음이다). **옆 서비스에는 걸리지 않는다.** 경로 · 쿠키 이름 · 실패를 401 하나로 합친 것 · Redis 가 죽었을 때의 갈림은 Claude 가 정했다. **남은 것** — 모든 기기 로그아웃 · 프런트의 재발급 흐름(§4 A) |
| **P-16** | **`mode` 와 `tier` 를 gameconfig(Redis)에서 읽어 검증하는 것**(2026-09-24 **소유자 결정** — 읽는 키 둘(`qm:gameconfig:{GAME}:{MODE}` 의 `EXISTS` · `:tier` 의 `ZSCORE`) · **Redis 를 못 읽으면 통과시킨다(fail-open)** · **`mode` 가 필수가 됐다** · `tier` 는 값이 있을 때만 본다) | **검토가 아니라 뒤처리다** — docs/11 에 D-항목으로 남기기. **`CLAUDE.md` §2 · §11 의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15 를 개정한다**(`CLAUDE.md` §3.6). **특히 볼 것** — 고치기에서 모드를 비우는 길이 없어진 것, 접두사가 어긋나면 fail-open 때문에 **검증이 조용히 꺼지는 것.** 클래스의 자리 · 에러의 글귀 · 안 심긴 gameconfig 도 통과시키는 것 · `tier` 의 `@Pattern` 을 남긴 것 · `PATCH` 에서 모드 검증이 방장 · 상태 검사보다 먼저인 것은 Claude 가 정했다. **남은 것** — `party.recruit_posts.mode` 를 `NOT NULL` 로 조일지 · 옛 글의 빈 `mode` |

그 표에 없지만 같이 볼 것 — 패키지를 나누는 법 · readiness 에 `db` 만 넣은 것 · Hibernate 의 제약 위반 WARN 로거를 끈 것. **소유자가 정한 것이지만 docs/11 에 올려야 하는 것 여섯**(`matching` 폴더에서 D-항목으로 남겨야 한다) — 스키마별 DB 롤을 두지 않는 것(2026-09-22. docs/11 #17 · D-1 을 개정한다. §1 · D) · **식별자**(2026-09-22. P-11 — 2026-09-19 의 결정 · D-4 를 개정한다. §1) · **refresh 토큰**(2026-09-23. P-15 — `CLAUDE.md` §5.1 (라) · (마)가 설계로만 적어 둔 것이 구현됐다. #16 의 access denylist 개정과 같은 묶음이다. §1 · §4 A) · **LoL 전적 동기화**(2026-09-23. P-13 — `CLAUDE.md` §7 의 "전적을 가져오는 주기와 방법" 미정을 LoL 에 대해 닫는다. §1 · §4 C) · **게시판 목록의 페이지 나누기**(2026-09-23. P-14 — `CLAUDE.md` §7.1 의 "페이지를 나눌지" 미정을 닫는다. §1) · **gameconfig 를 읽어 `mode` · `tier` 를 검증하는 것**(2026-09-24. P-16 — **`CLAUDE.md` §2 · §11 의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15 를 개정한다.** `CLAUDE.md` §3.6 · §7 "게임 계정 연동" 의 "이 앱이 검증하지 않는다" 미정을 닫는다. §1).

## 5. 문서 지도

**이 폴더**

| 파일 | 무엇인가 | 언제 읽나 |
|---|---|---|
| `START_HERE.md` | 이 파일. 진행 상황, 옆 서비스의 임시 처리와 이 앱이 주는 것, 만드는 순서, 다음에 닿기 전에 물을 것 · 소유자가 검토해야 하는 것 | 세션을 시작할 때마다. **단계가 끝나면 고친다** |
| `CLAUDE.md` | 규칙 — 경계, 계약, 설계 규칙, 미정 사항(§7 · §7.1 · §7.2), 작업 방식 · 운영 규칙, 커밋 규칙 | 작업 전에 |
| `README.md` | 하는 일 / 하지 않는 일의 짧은 소개, "만드는 순서"의 원본, "아직 정할 것" 요약 | 처음 한 번 |
| **`contracts/platform-api.md`** | **이 폴더에서 정한 계약** — 공통(에러 본문 · 인증 · `Origin`) · access 토큰 · 계정 · 게임 프로필 · 소셜 로그인 · 차단 · 모집 글/목록/입장권 · 방장 확정의 기록 · 친구/신고/최근 함께한 사람 · 이 앱이 내는 알림 · **refresh 토큰** · **gameconfig 를 읽는 것** · **"원본에 올려야 할 것"(P-1~P-16).** Claude 가 정했고 소유자가 항목별로 검토하지 않았다(P-11 ~ P-16 은 소유자가 직접 정한 것이다 — 머리의 "지위") | 코드를 고치기 전에. **경로 · 스키마 · 에러 코드 · 클레임을 바꾸면 같이 고친다** |
| `docs/ROOM_CONTRACT.md` | `room` 계약의 발췌 사본 — 이 앱이 읽는 Redis 키, 방 만들기 · 입장 · 방장 확정 · 접속 확인, `room` 이 내는 알림, 게시판 채널 신호. 머리 절만 이 폴더에서 쓴 글이다 | 3 · 5단계에 들어갈 때. **낡는다** — 머리의 확인 명령 둘을 돌린다(2026-09-21 에 다시 돌렸고 출력이 비어 있었다 — 사본 머리에 적힌 커밋 `0c6d9b9` 그대로다) |
| `backend/` | Spring Boot 앱. 패키지를 나누는 법은 `backend/src/main/java/com/queuemate/platform/package-info.java` 가 말한다(`common` · `account` · `social` · `party`). **테이블의 원본은 `backend/src/main/resources/db/migration/`**, 설정과 환경변수의 목록은 `application.yaml` 이다 | 코드를 만질 때 |
| (밖에 있다) ERD | <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq>(어긋나면 마이그레이션이 맞다) | 테이블을 만질 때 |

**옆 폴더 (이 컴퓨터에 있을 때. 읽기만 한다 — 절대 경로는 `CLAUDE.md` §10)**

| 무엇 | 경로 |
|---|---|
| **전체 그림** — 서비스 다섯 개, 잇는 것, 한 번이 흘러가는 순서, 결정 한눈에(2026-09-20 기준이라 그 뒤의 것은 없다) | `../room/docs/PROJECT_OVERVIEW.md` |
| 결정 로그 원본 — 이 앱에 걸리는 항목은 `CLAUDE.md` §10 의 그 행에 있다. **파일 머리의 "낡은 항목 주의"로 걸러 읽는다** | `../matching/docs/11_DECISION_LOG.md` |
| 알림 계약(봉투 · 발행 주체 · SQS FIFO · 계약 구멍) / 봉투를 만드는 코드의 본보기 · 채널 접두사 원본 | `../matching/contracts/events.md` / `../matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `…/redisKeys/SharedKeys.java` |
| `matching` 이 읽는 `social.blocks` 의 모양 | `../matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `../matching/backend/src/test/resources/schema.sql` |
| `matching` 이 platform 을 기다리는 일(① 파티 풀기 ② 차단 스키마 ③ outbox) | `../matching/HANDOFF.md` §0-1 |
| `room` 의 계약 원본 · 방 키 상수의 원본 · 임시 처리의 원본 | `../room/contracts/room-api.md` · `../room/backend/src/main/java/com/queuemate/room/redisKeys/RoomKeys.java` · `../room/START_HERE.md` §2 |
| **2026-09-21 에 검토한 방향의 원문**(`room` 의 눈) | `../room/CLAUDE.md` §7 "`status=PARTY` 해제" 행 |
| 로컬 환경 함정(띄우고 죽이기 · IntelliJ · worktree · 테스트) | `../room/docs/NOTIFICATION_LESSONS.md` |
| 왜 PostgreSQL 인가 · 스키마 배치 · outbox | `../matching/docs/WHY_POSTGRESQL.md` |

## 6. 로컬에서 띄우는 법

**5432 · 6379 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 건드리지 않는다**(`CLAUDE.md` §9). 테스트용을 다른 포트로 따로 띄우고, 끝나면 내린다.

```bash
# 테스트용 PostgreSQL(5433) · Redis(6380). --rm 이라 멈추면 지워진다 — DB 가 매번 빈 채로 시작한다
docker.exe run -d --rm --name qm-platform-test-pg -p 5433:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=queuemate postgres:16-alpine
docker.exe run -d --rm --name qm-platform-test-redis -p 6380:6379 redis:7-alpine

cd backend && ./gradlew build        # 테스트 포함. 기본값이 5433 · 6380 이라 환경변수가 필요 없다. /mnt/c 아래라 느리다
cd backend && ./gradlew bootRun      # 앱은 8082. JWT 키가 없으면 backend/.dev-keys/ 에 개발용 키를 만든다(경고 로그 — 정상이다)
curl -s localhost:8082/health/live ; curl -s localhost:8082/health/ready      # ready 는 DB 를 본다

docker.exe exec qm-platform-test-pg psql -U postgres -d queuemate -c '\dn'      # psql · redis-cli 는 컨테이너 것을 빌려 쓴다
docker.exe exec qm-platform-test-redis redis-cli KEYS 'qm:*'

ss -ltnp | grep :8082      # → kill <PID> → ./gradlew --stop → docker.exe stop qm-platform-test-pg qm-platform-test-redis
                           #   (띄운 것은 반드시 내린다. pkill -f 금지. 마지막에 docker.exe ps 와 ss -ltn 으로 남은 것이 없는지 본다)
```

- **컨테이너를 안 띄우고 `./gradlew build` 를 돌리면 테스트가 실패한다**(접속 거부) — 건너뛰지 않는다. 앱이 뜨려면 Flyway 가 DB 에 붙어야 하기 때문이다.
  **건너뛰는 경우는 하나다** — `DB_PORT=5432` 이거나 `REDIS_PORT=6379` 일 때(남의 DB 에 Flyway 를 돌리지 않으려는 것이다. 테스트 클래스를 통째로 건너뛴다 — `ApiTestSupport` · `PlatformApplicationTests`). **건너뛴 것을 통과로 읽지 마라.**
- `bootRun` 을 `kill` 로 내리면 Gradle 이 `BUILD FAILED`(exit value 143)를 찍는다 — SIGTERM 으로 끝났다는 뜻이고 정상이다. 앱 로그에 `Graceful shutdown complete` 가 있으면 곱게 내려간 것이다.
- `room` 을 같은 Redis 에 붙여 같이 띄우려면 `room/backend` 에서 `REDIS_PORT=6380 ./gradlew bootRun`(8083). `notification` 은 8081, `matching` 은 8080 이다.
- **`bootRun` 으로 손으로 해 보려면 gameconfig 를 먼저 심는다**(2026-09-24 — `mode` · `tier` 검증이 그 키를 읽는다. **심지 않으면 fail-open 으로 검증이 통째로 꺼진다** — 아무 모드나 통과한다. `CLAUDE.md` §3.6).
  값의 원본은 옆 폴더의 **`../matching/seed/gameconfig.redis`** 이고 **그 파일을 `redis-cli` 에 부으면 된다** — `matching` 의 문서가 적은 형태는 `docker exec -i <컨테이너> redis-cli < seed/gameconfig.redis` 다(`../matching/START_HERE.md` · `../matching/docs/GAME_CONFIG.md`).
  **어떻게 심는지는 `matching` 폴더의 일이다** — 이 앱은 심지 않고 읽기만 한다. (**이 문서를 고친 세션은 이 명령을 실행해 보지 않았다.**)
  **테스트는 심지 않아도 된다** — `ApiTestSupport` 가 **없는 키만** 스스로 심고 끝나면 그것만 지운다(있던 키는 건드리지 않는다).
- **테스트와 `bootRun` 은 같은 DB · 같은 Redis 를 쓴다.** 테스트는 자기가 만든 사용자(로그인 아이디가 `t_…`)와 그에 딸린 줄 · 키만 지운다(`ApiTestSupport`) — 손으로 넣은 데이터는 건드리지 않는다. 컨테이너가 `--rm` 이라 멈추면 전부 사라진다.
- Claude 가 파일을 고친 뒤에는 IntelliJ 에서 `Ctrl+Alt+Y`. 그 밖의 함정은 `CLAUDE.md` §9 "운영 규칙"과 `../room/docs/NOTIFICATION_LESSONS.md`.

### 손으로 해 보기 — 가입 → 로그인 → 글 쓰기 → 목록

경로와 본문은 `contracts/platform-api.md` 그대로다. **이 보기는 계약과 코드를 보고 적었다 — 이 문서를 고친 세션이 실행해 보지는 않았다**(그때 다른 작업이 `backend/` 에서 진행 중이었다). 어긋나면 계약과 코드가 맞다.
`curl` 은 `Origin` 헤더를 달지 않으므로 `Origin` 검사를 그냥 통과한다. 쿠키(`qm_access`)는 `-c` 로 받아 `-b` 로 보낸다.

```bash
B=localhost:8082
J='Content-Type: application/json'

# 가입 → 로그인 (로그인 아이디는 ^[a-z0-9_]{4,20}$ · 비밀번호 8~72자 · 닉네임 2~16자)
# 응답은 {userId, loginId, nickname} 이다 — userId 는 DB 가 매긴 사용자 번호다(숫자). 아래에서 쓴다
curl -s -X POST $B/api/v1/auth/signup -H "$J" -d '{"loginId":"host_01","password":"password1234","nickname":"방장"}'
curl -s -X POST $B/api/v1/auth/login  -H "$J" -d '{"loginId":"host_01","password":"password1234"}' -c host.cookie
curl -s $B/api/v1/users/me -b host.cookie                     # 쿠키 없이 부르면 401 UNAUTHENTICATED
HOST=<위 응답의 userId — 숫자다>                               # 차단 · 친구 요청의 본문과 URL 에도 이 번호를 쓴다

# 게임 계정(자기신고) — 목록의 카드와 filledPositions 의 출처다. tier 는 그 게임의 티어 사다리에 있는 이름이어야 한다(2026-09-24 — LoL 의 EMERALD_4 는 사다리에 있다).
# RIOT_API_KEY 가 있으면 이 직후에 LoL 전적을 비동기로 긁는다(응답의 stats 는 아직 예전 값이다 — 잠시 뒤 다시 조회하면 채워져 있다).
# 키가 없으면 stats 는 계속 null 이다. VALORANT · PUBG 는 키가 있어도 늘 null 이다
curl -s -X PUT $B/api/v1/users/me/game-accounts/LOL -H "$J" -b host.cookie \
     -d '{"gameNickname":"달콤한 인생#KR7","tier":"EMERALD_4","mainPosition":"MID","server":null}'

# 글 쓰기 — 응답의 postId(숫자)가 곧 roomId 다. LOL · VALORANT 의 conditions 는 {} 다 (PUBG 는 {"perspective":"TPP"})
# mode 는 필수이고 그 게임의 gameconfig 에 있는 이름이어야 한다(2026-09-24) — LoL 은 RANKED_SOLO · RANKED_FLEX_5 · ARAM_2 …,
# VALORANT 는 COMPETITIVE_DUO …, PUBG 는 RANKED_DUO_TPP … (원본은 ../matching/seed/gameconfig.redis). 없는 모드는 400 VALIDATION_FAILED 다
curl -s -X POST $B/api/v1/posts -H "$J" -b host.cookie \
     -d '{"game":"LOL","mode":"RANKED_SOLO","title":"미드 서폿 구해요","description":"즐겜","voice":"REQUIRED","purpose":"RANK_UP","conditions":{},"wantedPositions":["MID","SUPPORT"]}'
ID=<위 응답의 postId>

curl -s "$B/api/v1/posts?game=LOL" -b host.cookie             # 방이 아직 없다 — memberCount 0, members [], host 는 채워져 있다
# 목록은 커서로 나뉜다 — limit 은 없으면 20 · 최대 100(벗어나면 400 이고 잘라 주지 않는다), 다음 장은 응답의 nextCursor 로 받는다
curl -s "$B/api/v1/posts?game=LOL&limit=5" -b host.cookie      # nextCursor 가 있으면 &cursor=<그 값> 으로 이어 받는다
curl -s -X POST $B/api/v1/posts/${ID}/ticket -b host.cookie   # 입장권(JWT · 60초). jwt.io 등에서 token_use = room_ticket 을 본다
```

### `room` 인 척 방 키를 넣어 보기 — `room` 을 안 띄우고 목록을 확인하는 법

**이 앱은 방 키에 쓰지 않는다 — 아래는 사람이 `redis-cli` 로 `room` 의 자리를 대신하는 것이다**(테스트의 `party/PostTestSupport` 가 하는 일과 같다). 키의 약속은 `docs/ROOM_CONTRACT.md` "Redis 키"다.
**zsh 에서는 변수를 중괄호로 감싼다** — `qm:room:$ID:host` 라고 쓰면 `$ID:h` 가 특수 문법으로 해석돼 문자열이 깨진다(`CLAUDE.md` §9).

```bash
R() { docker.exe exec qm-platform-test-redis redis-cli "$@" | tr -d '\r'; }

# 방 만들기 흉내 — 방장 키(STRING, 값은 방장의 userId)와 멤버 SET. 값은 전부 사용자 번호의 십진 문자열이다. 수명은 room 과 같은 600초
# (숫자가 아닌 값을 넣으면 이 앱이 건너뛴다 — 카드에도 memberCount 에도 들지 않는다)
R SET  "qm:room:${ID}:host" "${HOST}" EX 600
R SADD "qm:room:${ID}:members" "${HOST}" 999999     # 999999 는 가입하지 않은 번호다
R EXPIRE "qm:room:${ID}:members" 600

curl -s "$B/api/v1/posts?game=LOL" -b host.cookie   # memberCount 2 · members 에 카드 둘(가입하지 않은 999999 는 nickname null) · 이때 글에 room_seen_at 이 적힌다

# 방장 확정 흉내 — 확정 표시 키. 그 뒤 길 ①(confirm) 을 부르거나, 목록을 다시 받으면(길 ②) 글이 CONFIRMED 가 된다
R SET "qm:room:${ID}:confirmed" "${ID}" EX 600
curl -s -X POST $B/api/v1/posts/${ID}/confirm -b host.cookie

# 방이 사라진 것 흉내 (확정하지 않은 글에서) — 방장 키를 지우고 목록을 다시 받으면 글이 EXPIRED 가 된다
# R DEL "qm:room:${ID}:host" "qm:room:${ID}:members"
```

### 게시판 신호를 보기 — `qm:pubsub:board`

```bash
# 다른 터미널에서 구독해 둔다 (Ctrl+C 로 끝낸다)
docker.exe exec -it qm-platform-test-redis redis-cli SUBSCRIBE qm:pubsub:board
```

글을 쓰거나 · 고치거나 · 지우거나(만료) · 확정하면 `{"type":"BOARD_CHANGED","eventId":"…","occurredAt":"…","payload":{}}` 한 줄이 온다. **데이터는 실려 있지 않다** — 받은 쪽이 목록을 다시 요청한다(`CLAUDE.md` §3.2).
목록 조회가 글을 만료 · 확정으로 옮겨 적을 때도 나간다. `notification`(8081)을 같은 Redis 에 띄우면 SSE 로 오는 것까지 볼 수 있다 — `PUBSUB NUMSUB qm:pubsub:board` 가 1 이 된 뒤에 움직인다(`CLAUDE.md` §9).
