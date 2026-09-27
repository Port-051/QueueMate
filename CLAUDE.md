# CLAUDE.md — matching 엔진 규칙 (Non-Negotiable)

작업 전에 이 파일과 `START_HERE.md`, 그리고 `docs/`를 읽어라.

> **이어서 하는 작업이 있다 — `HANDOFF.md` 를 먼저 읽어라.** 지금 상태, 사용자에게 먼저 물어야
> 할 것, 다음 할 일, 테스트 환경 함정이 거기 있다. 이 파일에는 규칙만 둔다.

이 저장소는 QueueMate의 **`app:matching` 배포 단위 하나**다.
전체 시스템 규칙은 queueMate 본 저장소의 `CLAUDE.md`에 있고, 이 파일은 그중
**매칭 엔진에 걸리는 부분만** 옮긴 것이다. 프런트엔드 / 소셜 / 파티 REST /
예약 REST / 인증 규칙은 여기 없다 — 그건 이 저장소의 책임이 아니다.
**단, 인증의 "검증"은 이 앱도 한다** — 2026-09-27 에 임시 식별 `?userId=` 를 `app:platform` 이 발급하는 쿠키 `qm_access`
(RS256 JWT)의 검증으로 바꿨다(docs/11 D-24 의 적용 — §3 "인증"). 발급 · 서명 · refresh 는 여전히 `app:platform` 의 일이다.

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다.
자동 매칭이 **기본 경로**이고, 파티 모집 게시판이 그 옆의 **두 번째 경로**다 (docs/11 D-11).

반드시 지킨다:
- 지원 게임은 **LoL, VALORANT, PUBG 셋뿐**이다. 넷째 게임을 추가하지 않는다.
- **상대팀/VS/대전 상대를 만들거나 보여주지 않는다.** proposal 하나는 언제나
  **하나의 party**를 뜻한다. 두 팀을 만들어 붙이는 코드는 이 제품이 아니다.
- 공개 사용자 탐색, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다.
  - **예외 하나 — 파티 모집 게시판은 허용된다** (docs/11 D-11, #14 개정). 모집 글을 올리면 그것이 곧
    파티방이고, 글을 누른 사용자는 그 방에 들어와(둘러보러 온 상태이지 파티원이 아니다) 음성으로 바로
    말을 건다. **글 · 목록 · 방장 확정과 방 안의 일(입장 · 나가기 · 강퇴 · 정원 · 시그널)이 전부 `app:platform` 의
    일이다 — 방 안의 일은 그 앱의 `room` 패키지다** (docs/11 D-33. 2026-09-19 에 D-16 이 방 안의 일을 별도 서비스
    `app:room` 으로 뗐다가 2026-09-25 에 `app:platform` 에 합쳤다). **이 저장소(매칭 엔진)에 게시판 코드를 넣지 않는다.**
  - **한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다** (D-11, INV-2 의 취지와 같다).
    **키 둘로 지킨다** (docs/11 D-19 — D-11 16번의 "활성 요청 키 하나"를 개정했다). `app:matching` 은 활성 요청 키
    `qm:user:active-request:{userId}`(HASH)를, `app:platform`(의 `room` 패키지 — 2026-09-25 까지는 `app:room` 이었다, D-33)은 **입장 표시 키 `qm:user:active-room:{userId}`**
    (STRING, 값은 `roomId`)를 쓴다. **각자 자기 키만 쓰고 지우고, 상대 키는 `EXISTS` 로 있는지만 본다**(값을
    읽지 않고, 쓰지도 지우지도 `EXPIRE` 를 걸지도 않는다).
    - 매칭 대기 중이면 방에 못 들어간다 — `app:platform` 의 입장 Lua(그리고 방을 같이 만드는 **글 쓰기**의 Lua — D-33)가 활성 요청 키를 보고 거절한다.
    - 방에 있으면 매칭을 못 돌린다 — `redis/shared/claim-request.lua` 가 `KEYS[2]` 로 입장 표시 키를 받아
      있으면 `-1` 을 돌려주고, `MatchRequestService#join()` 이 `JoinResult.Status.IN_ROOM` 으로,
      `MatchingController` 가 **409 `IN_ROOM`** 으로 바꾼다(`ALREADY_QUEUED` 와 구분된다).
    - **활성 요청 키를 쓰는 앱은 `app:matching` 하나다.** `app:platform`(옛 `app:room` 을 합친 것 — D-33)은 쓰지 않는다. 그래서
      방에 있는 사용자는 활성 요청이 없고, 상태 조회는 `IDLE`, 취소는 `NOT_FOUND` 로 고치지 않아도 옳게 답한다.
      **이 저장소가 입장 표시 키에 쓰거나 지우거나 값을 읽는 코드를 넣지 않는다** — 보는 자리는
      `claim-request.lua` 하나다.
    - 입장 표시 키 접두사의 **원본은 `../platform` 의 `room/redisKeys/RoomKeys.java` `ACTIVE_ROOM_PREFIX`**(2026-09-25 까지는 `../room` 의 같은 이름 파일이었다 — D-33)이고
      `redisKeys/SharedKeys.java` `ACTIVE_ROOM_PREFIX` 가 따라 적는다. 혼자 바꾸거나 오타를 내면 컴파일도
      테스트도 통과한 채로 방에 있는 사람의 매칭 요청을 받게 된다. 약속은 이름뿐이다 — 자료형 · 값 · 수명은
      `app:platform` 이 혼자 정한다(D-21 로 방 키와 같은 수명 규칙이다 — `../platform/contracts/platform-api.md` "방" 참조).
    - 확정된 사용자는 활성 요청이 `status=PARTY` 로 남으므로 **그대로는 방 입장도 거절된다.** 자동 매칭으로
      확정된 파티의 방 입장과 같이 풀어야 하고 **미정**이다 (D-19 "아직 미정").
  - 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이다. 사람을 검색하고 둘러보는
    공개 사용자 탐색은 여전히 금지다.
  - "공개 채팅방"은 **파티 모집과 무관한 잡담용 공개방**을 뜻한다. 모집 글에 딸린 방은 그 예외다.
- 프리미엄/과금 기능을 구현하지 않는다.
- 차단 관계의 사용자는 **어떤 매칭에서도 같은 파티가 될 수 없다** (INV-6).

## 2. 사용자 매칭 조건 — 게임당 정확히 4개

조건을 임의로 추가하지 않는다. 조건 하나가 늘 때마다 후보 풀이 곱셈으로 쪼개진다.

| | LoL | VALORANT | PUBG |
|---|---|---|---|
| 1 | 게임 모드 | 게임 모드 | 게임 모드 |
| 2 | 희망 포지션 | 선호 역할군 | 플랫폼 (스팀 / 카카오) |
| 3 | 음성 사용 | 음성 사용 | 음성 사용 |
| 4 | 플레이 목적 | 플레이 목적 | 플레이 목적 |

2번 줄이 게임마다 이름만 다른 **핵심 조건(keyValue)** 이다. 코드에서는
`KeyConditionType`(`POSITION` / `ROLE` / `PLATFORM`) + `String value`로 통일해 다룬다.
PUBG 2번은 원래 플레이 스타일이었으나 **플랫폼으로 교체했다** — 스팀과 카카오는 서버가 분리돼
서로 파티를 맺을 수 없으므로 조건에 없으면 게임에 같이 못 들어가는 파티가 생긴다. 조건 개수는
그대로 4개다(추가가 아니라 교체). 근거는 `domain/condition/KeyConditionType.java` 클래스 주석.

예약 매칭에만 붙는 추가 조건 (이 저장소 범위 밖, `app:reservation` — Lambda, docs/11 D-15. 예전 서술의 `app:platform` 예약 REST + `app:reservation-batch` 를 대체한다):
- 플레이 가능한 시간: 30분 단위 start/end
- 플레이할 양: `ONE_GAME` / `TWO_PLUS`

**새 조건은 `docs/12_ELBOW_CONDITION_SELECTION.md` 절차를 거치기 전에는 추가 금지.**

### 조건 enum의 현재 값 (코드가 원본)

| 개념 | 코드 | 값 |
|---|---|---|
| 게임 | `domain/GameKey.java` | `LOL, VALORANT, PUBG` |
| 조건 타입 | `domain/condition/KeyConditionType.java` | `POSITION, ROLE, PLATFORM` |
| 음성 | `domain/condition/VoicePreference.java` | **`REQUIRED, NO_VOICE`** — `OPTIONAL`은 **제거됐다** |
| 목적 | `domain/condition/PlayPurpose.java` | `RANK_UP, NORMAL, FUN` |
| LoL 포지션 | `domain/condition/lol/LolPosition.java` | `TOP, JUNGLE, MID, ADC, SUPPORT, NONE` |
| LoL 티어 | **자바에 없다.** Redis ZSET `qm:gameconfig:LOL:tier` (`seed/gameconfig.redis`) | `UNRANKED`(score 0), `IRON_4` … `CHALLENGER`(score 31) — 단(division)까지 **32개**. **조건이 아니다, 아래 참고** |

> **티어는 다섯 번째 조건이 아니다.** `tier`는 사용자가 고르는 조건이 아니라
> docs/02 §6의 **derived 조건**(연동 계정에서 가져오는 값)이자 같은 문서 §3이
> `rank eligibility = hard`라고 선언한 **자격 조건**이다. 랭크 모드는 티어 차이가 크면
> 게임에서 큐를 같이 돌 수 없으므로, 티어를 안 보면 애초에 같이 들어갈 수 없는 파티가
> 만들어진다. 그래서 조건 4개 규칙을 어기는 것이 아니고 docs/12 절차 대상도 아니다.
> 무엇을 볼지는 gameconfig의 `tierRule`(**`NONE` / `EXIST` 둘뿐이다**)이 정한다
> (`docs/GAME_CONFIG.md`, `seed/gameconfig.redis`). 지금은 사용자 자기신고이고,
> 라이엇 계정 연동이 붙으면 요청 바디에서 사라진다. (`userId` 는 2026-09-27 에 먼저 바디에서 빠졌다 —
> access 토큰의 `sub` 가 대신한다. §3 "인증")

> **티어 값의 원본은 자바가 아니라 Redis다.** `domain/lol/LolTier.java` enum은 **삭제됐고**
> `grep -rn LolTier backend/src`는 0건이다 — 자바에 티어 이름을 아는 코드가 한 줄도 없다.
> 사다리는 ZSET `qm:gameconfig:LOL:tier`이고 score가 단계 번호다(`0 UNRANKED`, `1 IRON_4`
> … `31 CHALLENGER`). 뺀 이유는 gameconfig를 데이터로 뺀 것과 같다 — 단(division)을
> 넣거나 라이엇이 티어를 추가할 때마다 재배포하지 않기 위해서다. 실제로 그 일이 한 번
> 일어났다: `GOLD` 하나가 `GOLD_4`~`GOLD_1`로 쪼개졌다(라이엇 실제 규칙이 **단 단위**라
> 티어로 뭉개면 표현이 안 된다). 단이 있는 티어는 `IRON`~`DIAMOND` 7개이고 **숫자가 클수록
> 낮다**(골드4 → 골드1 → 플래티넘4). `UNRANKED` / `MASTER` / `GRANDMASTER` / `CHALLENGER`는
> 단이 없다. 값을 고치려면 자바가 아니라 `seed/gameconfig.redis`를 고쳐라.

> `VoicePreference.OPTIONAL` 제거는 되돌리지 마라. 이유는 `domain/condition/VoicePreference.java`의
> 클래스 주석에 있다 — "매칭 전에 답이 정해지지 않는 조건은 조건이 아니다."
> `contracts/openapi.yaml`은 아직 `OPTIONAL`을 남기고 있다. **코드가 맞고 계약이 낡았다**
> (`contracts/README.md` 불일치 표 참고).

## 3. 아키텍처 제약

- Java Spring Boot / Gradle. 이 저장소는 **단일 모듈**이다 (`backend/settings.gradle`의
  `rootProject.name = 'matching'`). queueMate 본 저장소의 멀티모듈로 되돌리지 마라.
- **스프링/Gradle 프로젝트는 `backend/` 아래에 있다.** `src/` · `build.gradle` ·
  `settings.gradle` · `gradle/` · `gradlew` 가 전부 거기에 있고, 루트에는 `contracts/`
  `db-design/` `docs/` `load-test/` `redis-ha-lab/` `seed/` 와 문서만 남는다.
  **빌드·테스트는 `backend/` 안에서 돌린다** (`cd backend && ./gradlew ...`).
  아래 표가 `service/...` `redis/...` 처럼 짧게 적은 경로는 `backend/src/main/`
  아래의 패키지 상대 표기다.
- **Redis 키 문자열은 `redisKeys/SharedKeys.java` 한 곳에서 나온다.** 자바 쪽 단일 출처다 —
  `qm:party:` · `qm:party:open:` · `:needs` · `qm:party:needs-roles:` · `qm:proposal:accepts:` ·
  `qm:proposal:pending` · `qm:user:active-request:` · `qm:user:active-room:` · `qm:pubsub:push:` ·
  `qm:gameconfig:` · `:tier` · `:tier-range:` · `qm:lock:pool:`. **이 가운데 `qm:user:active-room:` 만 이 앱이
  정하는 값이 아니다** — `app:platform` 의 입장 표시 키이고 원본은 `../platform` 의 `room/redisKeys/RoomKeys.ACTIVE_ROOM_PREFIX` 다
  (docs/11 D-19 · D-33 — 2026-09-25 까지는 `app:room` 의 것이었다. 이 앱은 `EXISTS` 로 보기만 한다).
  **`qm:gameconfig:` 는 이 앱이 정하지만 `app:platform` 도 읽는다**(docs/11 D-29, 2026-09-24 — `qm:gameconfig:{GAME}:{MODE}` 의
  `EXISTS` 와 `:tier` 의 `ZSCORE` 로 모집 글의 `mode` · 게임 계정의 `tier` 를 검증한다. 쓰지 않고, 못 읽으면 통과시킨다).
  **그래서 이 접두사나 `seed/gameconfig.redis` 의 키 모양을 바꾸면 `app:platform` 의 검증이 조용히 꺼진다 — 같이 바꾼다.** 같은 접두사를 여러 클래스가 각자 적고 있으면
  **한쪽만 고쳐도 컴파일은 통과하고**, 그때부터 서로 다른 키를 만들어 아무도 못 찾는 데이터가
  조용히 쌓인다. 게임별 `Lol/Pubg/ValorantPartyKeys`는 없어지지 않았다 — 조각만 `SharedKeys`에
  두고 **게임 이름과 조건을 엮어 needs 색인을 조립하는 일은 그쪽 몫**이다.
  **같은 문자열이 Lua 안에도 리터럴로 있다.** 컴파일러가 맞춰 주지 않는 짝이라, 값을 고치면
  어느 스크립트를 같이 고쳐야 하는지가 `SharedKeys`의 클래스 주석에 목록으로 있다. 그것부터 읽어라.
- **진행 중인 실시간 매칭 상태의 source of truth는 Redis다.**
  매칭 요청 / 아직 안 찬 파티 / 진행 중 proposal / 수락 집계 — 전부 Redis에만 둔다.
  - **`match_requests` 테이블을 만들지 않는다** (docs/11 #27).
  - PostgreSQL은 **확정된 것**만 안다. 시도했다 실패한 요청은 DB를 치지 않는다.
  - DB 의존성은 **차단 조회 하나 때문에만** 있다 (JPA + H2/PostgreSQL 드라이버).
    Flyway는 아직 없고 스키마도 없다. 매칭 상태를 DB로 옮기는 용도로 쓰지 마라.
- `app:matching`이 DB를 치는 유일한 지점은 INV-6 검증의 **`blocks`** 동기 SELECT
  하나다 (아직 미구현). **테이블은 `public.blocks` 이고 `blocker_id` · `blocked_id` 는 bigint(사용자 번호)다.**
  **이 앱이 읽는 테이블은 이것 하나이고 늘리지 않는다** — 권한이 아니라 약속으로 지킨다 (docs/11 D-1 · D-34).
  - (옛 서술 — 낡았다) "스키마는 앱별로 나누고 크로스 스키마 JOIN을 금지하되, 이 테이블 하나만 예외로 `matching`
    롤에 SELECT 권한을 준다(`social.blocks`)". **2026-09-22 에 스키마별 DB 롤을 두지 않기로 했고, 2026-09-26 에
    `app:platform` 이 스키마를 `public` 하나로 합치고 JOIN · FK 를 허용했다** (docs/11 D-34 가 #17 · D-1 을 개정).
    **두 칸이 `varchar` 에서 bigint 가 된 것은 2026-09-22** (docs/11 D-25 가 D-4 를 개정).
  - **그래서 `block/Block.java` 를 고쳐야 했다 — 2026-09-26 에 고쳤다(`Long` · `@Table(name = "blocks")`)**(`HANDOFF.md` §0-4 (가)): `@Table(schema = "social", …)` 의
    `schema` 를 빼고 두 칸을 `Long` 으로. 그 전까지는 운영 DB 에 붙으면 차단 조회가 깨진다.
  - 뷰(`shared_read.blocked_pairs`)를 두는 원안은 폐기했다 — 층을 하나 더 만드는 값보다
    단순함이 크다고 판단했다.
- **Redis 장애 시 fail-closed 한다.** 중복 매칭을 감수하는 fallback을 만들지 마라 (INV-10).
- Kafka/RabbitMQ 추가 금지. 앱 간 도메인 이벤트는 outbox → **SQS FIFO**다.
  `app:matching`은 `ProposalConfirmed.fifo` 발행을 맡는다. **소비하는 큐는 없다** — `BlockChanged.fifo`는
  폐기됐고(docs/11 D-12) `PartyClosed.fifo`는 `app:platform`만 읽는다(docs/11 D-13).
- **사용자 알림을 이 앱이 직접 보내지 않는다.** Redis Pub/Sub에 publish 까지만 하고
  SSE 배달은 `app:realtime`이 한다. 이 저장소에 `SseEmitter`나 WebSocket을 넣지 마라.
  publish 쪽은 **구현돼 있다** — `notification/PushPublisher.java`가 채널
  `qm:pubsub:push:{userId}`에 `{type, eventId, occurredAt, payload}` JSON을 보낸다.
  종류는 `notification/PushEventType.java` 5종이고 **5종 모두 발행된다** —
  `MATCH_QUEUE_UPDATED` / `MATCH_PROPOSAL_CREATED`(배정, `rule/lol/*Assigner.java`) ·
  `MATCH_CANCELLED`(취소, `rule/lol/LolPartyLeaver.java`) ·
  `MATCH_PROPOSAL_EXPIRED`(만료, `service/ProposalExpiryService.java` — 무응답자와 수락자를
  가리지 않고 그 제안에 있던 전원에게, payload `{partyId}`) ·
  `MATCH_CONFIRMED`(확정, `service/ProposalService.java#accept()` 가
  `proposal/cleanup-confirmed.lua`가 돌려준 파티원 전원에게, payload `{partyId}`).
  `MATCH_CONFIRMED`는 **확정을 만든 그 한 번의 호출에서만** 나간다 — 이미 확정된 제안에
  수락이 또 오면 `accept-proposal.lua`가 `CONFIRMED`가 아니라 `ALREADY_RESPONDED`를
  돌려주기 때문이다(그래서 같은 알림이 두 번 나가지 않는다).
  `publish()`는 **어떤 예외도 밖으로 내보내지 않는다** — 알림 실패로 이미 성립한 매칭을
  503으로 뒤집지 않기 위해서다. 그 대가로 발행이 틀려도 조용하니 `PushNotificationTest`를 돌려라.
- 게임 모드 설정(`gameconfig`)은 **Redis에서 읽기만 한다.** 앱이 부팅 시 밀어넣지 않는다.
  밀어넣으면 모드 추가마다 재배포가 필요해져 설정을 데이터로 뺀 의미가 사라진다
  (`seed/gameconfig.redis`, `docs/GAME_CONFIG.md`).
- **인증 — 쿠키 `qm_access` 의 access 토큰(RS256 JWT)을 `app:platform` 의 공개 키로 검증만 한다** (2026-09-27 소유자 지시 · docs/11 D-24 의 적용).
  그 전에는 JWT 가 없어 `userId` 를 요청 바디(`POST /match-requests`)와 쿼리 파라미터(`?userId=` — 조회 · 취소 · 수락 · 거절)로 받는
  **임시 식별**이었고, 남의 번호로 요청 · 취소 · 수락을 할 수 있었다. **지금 "나"는 토큰의 `sub`(사용자 번호의 십진 문자열 `"42"`)다** —
  `@CurrentUserId String userId`(`common/security/`). 엔진 안(Redis 키 · Lua · 알림 채널)은 `userId` 를 전처럼 문자열로 다루므로 바뀐 것이 없다.
  - 검증 — 서명(RS256) · `exp` · `iss == queuemate-platform` · **`token_use == access`** · **`sub` 가 `^[0-9]{1,19}$`**. 하나라도 어긋나면
    401 `{"code":"UNAUTHENTICATED", …}` 이고 이유는 가르지 않는다. 값의 원본은 `../platform` 의 `common/security/TokenClaims` 다 —
    `common/security/TokenClaims` 에 **똑같이 베꼈다. 따로 바꾸지 마라.** `Authorization` 헤더는 받지 않는다.
  - 공개 키 — 환경변수 **`JWT_PUBLIC_KEY`**(X.509 PEM, 운영은 Secrets Manager) 또는 **`JWT_PUBLIC_KEY_FILE`**(기본값
    `../../platform/backend/.dev-keys/public.pem` — `backend/` 에서 띄운다는 전제의 로컬 개발용. `platform` 이 처음 뜰 때 만든다).
    **둘 다 없으면 기동하지 않는다.** 개인 키 · 서명 · refresh · JWKS 는 이 앱에 없다.
  - 인증 없이 열린 것은 `/actuator/**` 뿐이다(노출 목록은 `management.endpoints` 가 정한다). 나머지는 전부 인증이다.
  - **CSRF — 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)의 `Origin` 을 허용 목록(`ALLOWED_ORIGINS`, 기본값 `http://localhost:5173,http://localhost:3000`
    — `platform` 과 같다)과 대조한다**(`common/web/OriginCheckFilter` — 403 `ORIGIN_NOT_ALLOWED`). `Origin` 이 없는 요청(curl · 서버 사이)은 통과한다.
    CSRF 토큰 · CORS 설정은 두지 않는다. **전제 — 상태를 바꾸는 GET 을 만들지 않는다.**
  - 테스트는 JVM 마다 임시 키 쌍을 만들어 공개 키를 모든 컨텍스트에 넣는다(`src/test/…/common/security/TestJwt` ·
    `TestJwtKeyInitializer` — `META-INF/spring.factories`). 토큰은 `TestJwt.cookie("42")` 로 찍는다.
- 배포 기준은 **Stage 2(ECS Fargate)** 다. Stage 1(단일 EC2 + Docker Compose)은 적용하지 않는다 (docs/11 D-18).
  **k8s/HPA/sticky session을 전제한 구현 금지**는 그대로다.

## 4. 불변식 (INV) — 매칭 해당분과 그것이 지켜지는 자리

깨는 구현은 완료가 아니다. `<repo>` = 이 저장소 루트.

| INV | 내용 | 어디서 지켜지나 | 상태 |
|---|---|---|---|
| **INV-1** | 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다 | `backend/src/main/resources/redis/shared/claim-request.lua` — `EXISTS` + `HSET`을 한 원자 실행으로 묶고 마지막에 `EXPIRE 60`을 건다(선점만 하고 배정 전에 죽으면 그 사용자가 영영 막히는 것을 막는 안전장치). 호출은 `service/MatchRequestService.java#join()`. 실패 시(반환 `0`) `controller/MatchingController.java`가 `409 ALREADY_QUEUED`. 배정에 성공한 스크립트가 `PERSIST`로 그 만료를 뗀다. **같은 스크립트가 INV-1 과 별개로 "방에 있으면 매칭을 못 돌린다"(docs/11 D-11 15번 · D-19)도 지킨다** — `KEYS[2]`로 `app:platform`(옛 `app:room` — D-33)의 입장 표시 키 `qm:user:active-room:{userId}` 를 받아 `EXISTS` 로 보고, 있으면 아무것도 쓰지 않고 `-1` 을 돌려준다. `join()` 이 그것을 `dto/JoinResult.java` 의 `Status.IN_ROOM` 으로 바꾸고 컨트롤러가 `409 IN_ROOM` 으로 내보낸다 | **구현·테스트됨** |
| **INV-2** | 한 사용자는 동시에 하나의 활성 proposal에만 속한다 | 수락/거절이 붙은 뒤에도 **여전히 "한 사용자는 한 파티에만"으로 근사된다.** 근사가 성립하는 이유는 **proposal이 곧 party**이기 때문이다 — `proposalId = partyId`이고 제안 상태(`status`/`expiresAt`)를 별도 레코드가 아니라 파티 HASH에 얹는다(`service/ProposalService.java` 클래스 주석). 그 한 파티를 지키는 것은 배정 스크립트 4개(`redis/lol/create-or-check-party-untiered.lua` · `create-or-check-party-tiered.lua` · `join-party.lua` · `join-party-tiered.lua`)가 전부 `qm:user:active-request:{userId}` HASH의 `partyId` 필드 **하나만** 쓰는 것이다. 그 필드를 지우는 것은 `redis/lol/leave-party.lua` 하나이고, 거절 시에는 `ProposalService#decline()`이 스크립트 뒤에 `MatchCancelService#cancel()`을 불러 거절한 본인만 큐에서 뺀다(수락해 놓고 기다리던 나머지는 남긴다). **확정되면 `redis/proposal/cleanup-confirmed.lua`가 파티원의 활성 요청을 지우지 않고 `status='PARTY'`를 찍는다** — 지우면 그 순간 새 매칭을 걸 수 있어 한 사람이 두 파티에 속하기 때문이다. 그래서 확정 뒤에도 INV-1 선점이 그대로 유지되고, 그 사용자는 그 파티 하나에 묶인 채다. 파티 HASH도 남긴다(상태 조회와 수락 재전송이 읽는다). **이 상태를 푸는 것은 파티를 닫는 `app:platform`이고 아직 없다** — 확정된 사용자는 큐에 다시 들어올 방법이 없다. 이 앱이 `PartyClosed`를 소비해 푸는 길은 닫혔고(docs/11 D-13 — 그 큐는 `app:platform`만 읽는다) 누가 어떻게 푸는지는 미정이다(`HANDOFF.md` ①, docs/11 D-19 "아직 미정" — `app:room`(지금은 `app:platform` 의 `room` 패키지 — D-33)이 이 키를 지우는 길은 D-19 로 없어졌다) | **부분 (파티 단위 근사)** |
| **INV-3** | 파티 인원은 mode의 target party size를 넘지 않는다 | `redis/lol/join-party.lua` / `join-party-tiered.lua` — 참가자를 `HSET` 한 뒤 `member:` 필드를 **세어** `size >= target`이면 그 파티를 **모든 needs 색인(티어 모드는 파티의 `tierLo`~`tierHi` 칸 전부)에서 제거**한다. 인원 카운터 필드는 두지 않는다 — Lua는 롤백이 없어 페일오버 뒤 재시도가 `HINCRBY`를 두 번 더하면 실제 멤버 수와 어긋나지만, `HSET` + 세기는 몇 번 해도 같기 때문이다. **주의: 세는 자리에는 target 확인 분기가 없다.** 초과를 막는 것은 ① 후보가 needs 색인(=아직 안 찬 파티)에서만 나온다는 것과 ② 후보 선택부터 합류까지가 `redisLock/PoolLock.java`의 후보 풀 락 안에 있다는 것, 두 겹이다. 그 락을 건너뛰는 호출부를 만들면 INV-3이 깨진다 | **구현·테스트됨** |
| **INV-4** | proposal의 모든 참가자가 accept하기 전에는 party 확정 금지 | `redis/proposal/accept-proposal.lua` — **수락자 SET `qm:proposal:accepts:{partyId}`를 `SCARD`로 세어 파티 HASH의 `target`과 비교하고, `count >= target`일 때만 `HSET status 'CONFIRMED'`** 한 뒤 `CONFIRMED`를 돌려준다. 세기와 확정이 한 스크립트 안이라 마지막 두 명이 동시에 눌러도 둘 다 "내가 마지막"이 될 수 없다. `target`을 못 읽으면 확정하지 않고 수락만 기록한다(fail-closed). 쓰기가 `SADD`/`HSET`뿐이고 `SADD` 반환값으로 early return 하지 않아 **재시도해도 답이 같다**(카운터 대신 집합을 쓰는 이유 — 중간에 죽어도 다음 호출이 다시 세어 확정한다). `decline-proposal.lua`가 `DEL acceptsKey`까지 하는 것도 INV-4를 위해서다 — 옛 수락을 남기면 다시 찬 파티가 한 명만 눌러도 `SCARD`가 `target`에 닿는다. 제안이 열리는 자리는 `rule/lol/LolUntieredAssigner.java#joinParty()` / `rule/lol/LolTieredAssigner.java#joinParty()`의 `JOINED_AND_FULL`(Lua 반환 **`2`**) 분기이고, 거기서 `MATCH_PROPOSAL_CREATED` 알림을 파티 전원에게 발행한다. `status='PENDING'` + `expiresAt`을 쓰는 것은 `join-party.lua` / `join-party-tiered.lua`의 `HSETNX`다. 확정 뒤에는 `ProposalService#accept()`가 `redis/proposal/cleanup-confirmed.lua`를 불러 파티원의 활성 요청에 `status='PARTY'`를 찍고(INV-2 참고) 수락자 SET에 TTL(`queuemate.proposal.confirmed-retention-seconds`, 기본 60초)을 건 뒤, 그 스크립트가 돌려준 파티원 전원에게 `MATCH_CONFIRMED`를 발행한다. 그 스크립트도 `status == 'CONFIRMED'`일 때만 도는 멱등 연산이다. **아직 없는 것**: `matching.outbox` 기록과 `ProposalConfirmed.fifo` 발행 — 그래서 파티가 DB에 만들어지지 않는다. 확정된 파티를 푸는 자리도 없다 — `PartyClosed` 소비는 이 앱의 일이 아니고(docs/11 D-13) 푸는 주체는 미정이다 | **구현·테스트됨 (outbox 발행은 없음)** |
| **INV-5** | expired/declined/cancelled proposal은 다시 confirm될 수 없다 | 네 갈래가 **전부 막혀 있다.** ① **declined — 막힘.** `redis/proposal/decline-proposal.lua`는 `status`를 `'DECLINED'`로 **바꾸지 않고 `HDEL status, expiresAt` + `DEL acceptsKey`로 지운다.** 남겨 두면 그 파티가 다시 찼을 때 `join-party*.lua`의 `HSETNX status 'PENDING'`이 0을 돌려주어 아무도 확정시킬 수 없는 **좀비 파티**가 되기 때문이다(그 파일 머리말). 그래서 거절된 제안에 들어온 수락은 `accept-proposal.lua` 1번에서 `NOT_FOUND`로 걸린다 — `status == 'DECLINED'` 분기는 현재 **도달하지 않는 방어 코드**다. ② **confirmed — 되돌릴 수 없음.** 두 스크립트 모두 쓰기 전에 `HGET status`를 먼저 보고, `CONFIRMED`면 수락은 **`ALREADY_RESPONDED`**(확정 알림이 두 번 나가지 않게 `CONFIRMED`와 값을 갈라 놓았다. 컨트롤러는 이것도 204다), 거절은 `CONFIRMED`(=깨지 못함, 409)를 돌려준다. 페일오버 재실행 대비도 있다 — `join-party*.lua`가 `HSET`이 아니라 `HSETNX`로 `PENDING`을 써서 확정된 제안이 `PENDING`으로 되돌아가지 않는다. ③ **expired — 막힘.** `expiresAt`(= now + `queuemate.proposal.ttl-seconds`, 기본 20초)을 읽는 주체가 생겼다. 정원이 찰 때 합류 스크립트가 `HSETNX status 'PENDING'` **성공 분기 안에서** `qm:proposal:pending` ZSET에 `ZADD`(member = partyId, score = `expiresAt`)까지 하고, `service/ProposalSweeper.java`가 `queuemate.sweep.interval-ms`(기본 1초)마다 시한이 지난 것을 한 회차 100건씩 꺼내 `service/ProposalExpiryService.java`에 넘긴다. `redis/proposal/expiry-proposal.lua`는 `status == 'PENDING'`일 때만 `HDEL status, expiresAt` + `DEL acceptsKey` + `ZREM`을 하므로 **그사이 확정된 제안을 만료가 뒤집지 못한다**(PENDING이 아니면 pending 목록에서만 빼고 빈 목록을 돌려준다). 정책은 **수락하지 않은 사람만 큐에서 빼는 것**이다 — 수락한 사람은 파티에 남아 다시 기다리고, 옛 수락 기록이 지워지므로 빈자리가 채워져 제안이 새로 열리면 다시 눌러야 한다. **그 창도 닫혔다**: `accept-proposal.lua`가 `ARGV[3] = now`로 현재 시각을 받아 `expiresAt <= now`면 수락을 기록하지 않고 **`NOT_FOUND`**를 돌려준다. 그래서 시한이 지나고 스위퍼가 그 파티를 꺼내기 전(주기만큼)에 도착한 수락도 확정되지 않는다. `NOT_FOUND`인 이유는 클라이언트가 갈 곳이 스위퍼가 이미 걷어간 뒤와 같아서다(대기 화면 복귀) — 상태 값을 하나 더 만들면 같은 상황을 두 갈래로 다뤄야 한다. **흔적을 지우는 것은 여전히 스위퍼 몫이다** — 여기서 지우면 이 스크립트가 수락 집계 말고 다른 일까지 하게 되고 만료 알림도 못 나간다. ④ **cancelled — 막힘.** `redis/{game}/leave-party.lua`가 `member:` 필드를 지우기 **전에** `HDEL status, expiresAt` + `DEL qm:proposal:accepts:{partyId}` + `ZREM qm:proposal:pending`을 한다(커밋 `3d3efaf`). 그래서 `PENDING` 제안 도중 한 명이 취소하면 제안 자체가 깨지고, 취소자의 옛 수락이 남아 새로 합류한 사람의 수락으로 `SCARD`가 `target`에 닿는 일이 없다(거절 경로는 `decline-proposal.lua`가 먼저 지우고 그 뒤에 취소한다). 응답 갈래는 `domain/ProposalResult.java` 한 enum이 맡는다(`AcceptResult`/`DeclineResult`는 없어졌다). 쓰는 코드가 없던 `domain/ProposalStatus.java` / `domain/AcceptanceStatus.java`는 **삭제됐다** — 상태는 Redis의 문자열이다 | **구현·테스트됨** |
| **INV-6** | block 관계 사용자는 같은 proposal/party에 들어갈 수 없다 | **미구현.** 두 겹으로 설계했는데 아랫단만 있다. ① **선필터(코드 있음)** — `rule/lol/LolCandidateRule.java#canJoin()`이 락을 잡기 전에 `block/BlockRepository.java#findBlockedUserIds()`를 실제로 부르고, Lua가 돌려준 후보 파티 멤버 목록을 `rule/ScriptSupport.java#blockedWith()`로 거른다(상한 `MAX_CANDIDATE_SCAN = 20`, 전부 차단이면 새 파티를 만든다). Redis 선필터(`qm:block:{userId}`)가 아니라 **DB 조회**다 (docs/11 D-2. 그 선필터와 `BlockChanged.fifo`는 D-12로 폐기됐다). ② **확정 직전 최종 검증(없음)** — `blocks` 동기 SELECT (docs/11 D-1. 옛 이름 `social.blocks` — 2026-09-26 에 `public.blocks` 가 됐다, D-34). 확정 단계가 없으므로 이것도 없다. **그리고 ①은 스키마가 없어 실제로는 실패한다** — Flyway가 없고 `application.yaml`이 `ddl-auto: none`이라 기본 실행(H2)에 그 테이블이 없다(**그리고 운영 DB 에 붙어도 `Block.java` 가 옛 모양 — `schema = "social"` · `String` — 이라 깨진다**, docs/11 D-25 · D-34). 배정은 `@Async` 안이라 요청은 201로 나가고 배정만 조용히 실패한다. 테스트만 `ConcurrencyTestSupport`의 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql`로 테이블을 만들어 통과한다. **차단 검증 없이 배포하지 않는다** (docs/11 #30) | **미구현 (선필터 코드만, 스키마 없음)** |
| **INV-7** | 동일 사용자의 PartyMember 중복 금지 | 배정 스크립트 4개가 참가자를 `member:{userId} = keyValue` **HASH 필드**로 쓴다 — 새로 만들 때는 `create-or-check-party-untiered.lua` / `create-or-check-party-tiered.lua`의 `HSET`, 합류할 때는 `join-party.lua` / `join-party-tiered.lua`의 `HSET`. 같은 userId면 필드가 하나뿐이라 구조적으로 중복이 불가능하다. 앞단에서 INV-1이 이미 두 번째 요청을 막는다 | **구현됨** |
| **INV-8** | 게임별 hard rule 위반 파티 생성 금지 | 두 겹이다. ① 값 검증 — `validation/lol/LolConditionValidator.java`가 modeKey 존재 여부, `positionUniqueness`에 맞는 포지션 값, 그리고 `tierRule`(**`NONE` / `EXIST`**)에 맞는 티어 값까지 확인한다. `EXIST`면 `qm:gameconfig:LOL:tier-range:{modeKey}` 표를 읽어 **줄이 없는 티어와 `SOLO_ONLY` 티어를 거른다**(표가 없는 모드는 그 모드 요청이 전부 400이다 — fail-closed다). `WINDOW`/`TABLE`과 `maxTierGap`은 없앴다. 폭으로 거를지 표로 거를지를 설정에 또 적으면 설정이 데이터와 어긋날 수 있었기 때문이다 — `WINDOW`라고 적어 놓고 `maxTierGap`을 빠뜨리면 폭이 0이 되어 자기 티어하고만 매칭되는데 **에러가 안 났다**. 지금은 "표가 있으면 그 표대로"가 전부다 (`seed/gameconfig.redis`). ② 구조적 분리 — 조건이 **Redis 키 이름**에 들어가므로(`qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}`, 티어 모드는 뒤에 `:{tier}`가 더 붙어 (포지션 x 티어) 격자가 된다. 그 접미사는 **Lua가 스스로 붙인다** — 자바는 티어 없는 needs 키만 넘긴다) 조건이 다르면 애초에 같은 색인에 없다. 포지션 중복 금지는 Lua의 `unique` 분기가 처리. **PUBG 는 중복 금지가 없다** — 핵심 조건이 플랫폼이라 같은 값이 여럿 겹쳐도 되고, 대신 스팀과 카카오가 색인 자체로 갈린다 | **세 게임 모두 구현·테스트됨** (LoL · VALORANT · PUBG 각각 동시성 테스트가 있다) |
| INV-9 | 시간이 겹치는 활성 예약 중복 등록 금지 | **이 저장소 범위 밖.** `app:reservation`(Lambda)의 예약 REST가 검증한다 (docs/11 #24를 D-15가 개정 — 예전에는 `app:platform`이었다) | 해당 없음 |
| **INV-10** | Redis 장애 시 중복 매칭을 감수하는 fallback 금지. 새 매칭을 fail-closed 한다 | `common/error/GlobalExceptionHandler.java#handleRedisFailure()` — `DataAccessException`을 `503 MATCHING_UNAVAILABLE` + `Retry-After: 5`로 바꾼다. 이미 성립한 파티는 건드리지 않고 새 요청만 거절한다 | **구현됨** |

### 불변식 회귀 테스트

| 테스트 | 지키는 것 |
|---|---|
| `backend/src/test/java/.../concurrency/ActiveRequestConcurrencyTest.java` | INV-1 (같은 사용자 동시 100회 → 1건만 성공 / 두 번째 요청은 `ALREADY_QUEUED` / 다른 사용자 100명 → 전원 성공). 더해서 **docs/11 D-19** — 입장 표시 키가 있으면 `IN_ROOM` 이고 활성 요청 키가 생기지 않으며 남의 키를 건드리지 않는다 / 입장 표시 키가 사라지면 다시 접수된다 |
| `backend/src/test/java/.../concurrency/NaiveVsLuaComparisonTest.java` | INV-1 (순진한 `EXISTS`-후-`HSET`은 깨지고 Lua는 중복 0건임을 대조로 보인다) |
| `backend/src/test/java/.../concurrency/PartyJoinConcurrencyTest.java` | INV-3 (정원 초과 없음), INV-8 (같은 포지션 2명 없음), INV-2 근사 (한 사용자 = 한 파티) |
| `backend/src/test/java/.../concurrency/ValorantPartyJoinConcurrencyTest.java` | 위와 같은 것을 VALORANT 경로로 (일반전·경쟁전 각각). 더해서 INV-8 의 "파티 최고 티어 <= 한계(파티 최저 티어)" 와 **INV-5 cancelled** (제안 도중 취소로 빠져도 파티가 다시 차면 새 제안이 열리고 옛 수락이 남지 않는다) |
| `backend/src/test/java/.../concurrency/PubgPartyJoinConcurrencyTest.java` | 위와 같은 것을 PUBG 경로로 (일반전·랭크 각각). **PUBG 고유 두 가지**를 더 본다 — 핵심 조건이 플랫폼이라 **같은 값이 겹쳐도 되고**(LoL 포지션·VALORANT 역할군의 중복 금지가 여기엔 없다), 반대로 **스팀과 카카오는 한 파티에 섞이지 않는다**(INV-8 구조적 분리). 랭크는 파티 폭(최고-최저)이 10 을 넘지 않고 전원이 `tierLo`~`tierHi` 안인지, 정원이 차서 색인에서 빠진 파티가 한 명 취소로 원래 티어 칸 **전부**에 되돌아오는지까지 본다. **INV-5 cancelled** 도 VALORANT 와 같이 본다 |
| `backend/src/test/java/.../proposal/ProposalIdempotencyTest.java` | INV-4 (전원 수락 전 확정 없음 / 재시도가 수락자 수를 부풀리지 않음), INV-5 (확정은 거절로 뒤집히지 않음, 거절된 제안은 확정 경로에 못 들어옴, 페일오버 재실행이 `CONFIRMED`를 `PENDING`으로 되돌리지 않음) |

**동시성이 걸린 코드를 고쳤으면 위 표의 `concurrency/*` 5개를 반드시 다시 돌려라** (27건).

불변식 회귀는 아니지만 같은 Redis(DB 15)를 쓰는 테스트가 하나 더 있다 —
`backend/src/test/java/.../notification/PushNotificationTest.java` (6건). `qm:pubsub:push:*`를
직접 구독해서 알림이 실제로 나갔는지 본다. `PushPublisher`가 예외를 밖으로 내보내지 않으므로
(의도된 설계다) 발행 코드가 틀려도 호출부는 조용히 지나간다 — 구독 말고는 검증할 방법이 없다.
**알림 발행 코드를 고쳤으면 이것도 돌려라.**

표의 `ProposalIdempotencyTest`(11건)는 **단일 스레드**다. 동시성이 아니라 **멱등성**으로
INV-4/5를 지킨다 — 같은 수락을 두 번 보내도 답이 같은가, 거절은 왜 멱등이 아닌가
(첫 거절만 `DECLINED`, 재시도는 `NOT_FOUND`), 확정된 제안이 페일오버 재실행으로
`PENDING`으로 되돌아가지 않는가. `proposal/*.lua`나 `join-party*.lua`의 `HSETNX` 분기를
고쳤으면 이것도 돌려라.

### Lua 스크립트 목록 (`backend/src/main/resources/redis/`, 20개)

아래 표는 `shared` · `lol` · `proposal` 10개다. `pubg/` 5개와 `valorant/` 5개는
그 아래 "Lua 스크립트는 게임별로 나눈다" 절에 있다.

| 파일 | 하는 일 | 반환 코드 |
|---|---|---|
| `shared/claim-request.lua` | 활성 요청 선점 (INV-1). `EXISTS` + `HSET` + `EXPIRE 60`. `KEYS[1]` 이 활성 요청 키, **`KEYS[2]` 가 `app:platform`(옛 `app:room` — D-33)의 입장 표시 키**다 — `KEYS[2]` 는 `EXISTS` 로 보기만 하고 쓰지도 지우지도 값을 읽지도 않는다 (docs/11 D-19) | `1` 선점 / `0` 이미 있음(409 `ALREADY_QUEUED`) / **`-1` 게시판 방에 들어가 있음**(409 `IN_ROOM`) |
| `lol/create-or-check-party-untiered.lua` | 후보 파티 찾기. 없으면 새로 만들고 들어간다 | `1` 새로 만듦 / `2` 후보 찾음(멤버 목록 반환) / `-1` 설정과 안 맞는 값 / `-2` claim 만료 |
| `lol/create-or-check-party-tiered.lua` | 위의 (포지션 x 티어) 격자판. tier-range 표와 티어 사다리를 **Lua가 직접 읽어** 이 파티가 받아들일 범위를 정하고 `tierLo`/`tierHi`(사다리 순번, `ZRANK` 값 **그대로**라 0부터다)에 적는다 | 같음. `-1`에 "tier-range 표에 내 티어 줄이 없다 / `SOLO_ONLY`다 / 사다리에 없는 티어다"가 포함된다 |
| `lol/join-party.lua` | 이미 찾아 둔 파티에 합류. 정원이 차면 `HSETNX status 'PENDING'` + `expiresAt`을 쓰고 **그 성공 분기 안에서** `qm:proposal:pending`에 `ZADD` 한다 | `1` 합류 / **`2` 합류했고 정원이 찼다** / `-1` / `-2` |
| `lol/join-party-tiered.lua` | 위의 격자판. **자기 tier-range를 다시 읽지 않는다** — 파티의 `tierLo`/`tierHi`를 읽어 뺄 칸을 정한다 | 같음 |
| `lol/leave-party.lua` | 취소. 티어 유/무 한 벌로 처리(티어를 안 보는 모드는 티어 이름 자리에 `"NONE"`을 넘겨 접미사를 빈 문자열로 접는다). **멤버를 빼기 전에 제안 흔적(`status`/`expiresAt`/수락자 SET/pending)을 먼저 지운다** (INV-5 cancelled) | `1` 취소(파티 남음) / `2` 취소(파티 없음) / `0` 활성 요청 없음 / `-1` requestId 불일치 |
| `proposal/accept-proposal.lua` | 제안 수락 집계. 전원이 차면 확정하고 `qm:proposal:pending`에서 뺀다 (INV-4). `ARGV[2]`로 partyId를 받는다 | 문자열. `ACCEPTED` / `CONFIRMED` / `ALREADY_RESPONDED`(**이미 확정된 제안에 또 온 수락** — 확정 알림이 두 번 나가지 않게 `CONFIRMED`와 갈라 놓은 값이다. 컨트롤러는 이것도 204로 받는다) / `NOT_FOUND` / `NOT_A_MEMBER` / `DECLINED`(도달하지 않는 방어 코드) |
| `proposal/decline-proposal.lua` | 제안 거절. 파티 HASH의 제안 흔적(`status`/`expiresAt`)과 수락자 집합을 지우고 `qm:proposal:pending`에서 뺀다. `ARGV[2]`로 partyId를 받는다 | 문자열. `DECLINED` / `ALREADY_RESPONDED` / `CONFIRMED` / `NOT_FOUND` / `NOT_A_MEMBER` |
| `proposal/expiry-proposal.lua` | 시한이 지난 제안 하나를 깬다 (INV-5 expired). `status == 'PENDING'`일 때만 `HDEL status, expiresAt` + `DEL` 수락자 SET + `ZREM` | 목록 2개 — `{무응답자 {userId, requestId} 쌍 목록, 수락한 userId 목록}`. `PENDING`이 아니면 pending에서만 빼고 **빈 목록** |
| `proposal/cleanup-confirmed.lua` | 확정 직후 뒷정리. `status == 'CONFIRMED'`일 때만 파티원 활성 요청에 `status='PARTY'`를 찍고 수락자 SET에 TTL을 건다. 활성 요청과 파티 HASH는 **지우지 않는다** (INV-2) | 파티원 userId 목록. 확정된 제안이 아니면 빈 목록 |

**`qm:proposal:pending` ZSET의 수명.** 진행 중인 제안 목록이다 — member = partyId,
score = `expiresAt`. **넣는 자리는 하나**다: 합류 스크립트 6개(`{lol,pubg,valorant}/join-party.lua` ·
`join-party-tiered.lua`)의 `HSETNX status 'PENDING'` **성공 분기 안**. 분기 밖에 두면 재시도가
score를 미래로 밀어 파티 HASH의 `expiresAt`과 어긋난다. **빼는 자리는 제안이 끝나는 모든
곳**이다 — `accept-proposal.lua`(확정 분기) · `decline-proposal.lua` ·
`{lol,pubg,valorant}/leave-party.lua`(파티가 남는 자리와 지워지는 자리 둘 다) ·
`expiry-proposal.lua`. 한 군데라도 빠뜨리면 스위퍼가 이미 끝난 제안을 주기마다 영원히 다시
꺼내고, 최악의 경우 아무 잘못 없이 기다리던 사람을 큐에서 뺀다. 읽는 쪽은
`ProposalSweeper#sweep()`의 `ZRANGEBYSCORE 0 now LIMIT 0 100` 하나다.

**티어 격자를 KEYS로 통째로 넘기지 않는다.** 예전에는 자바가 (포지션 x 티어) 격자를 평평하게
펴서 KEYS로 전부 넘기고 Lua가 `KEYS[2 + (p-1)*T + t]`로 칸을 찾았다. 단(division)이 들어가
칸이 (포지션 6 x 티어 32) = **192개**가 되면서 그 방식을 버렸다. 지금은 **티어 접미사가 없는
needs 키**를 넘기고 Lua가 `':' .. 티어이름`을 붙여 조립한다 — KEYS 개수가 `4 + 포지션 개수`로
고정된다 (`leave-party.lua`는 `3 + keyValue 개수`).

**`tierRule` 해석은 자바가 아니라 Lua에 있다.** `LolTieredAssigner`가 표를 읽어 `[최저, 최고]`로
환산해 넘기던 방식은 없어졌다. Lua가 tier-range 표(`KEYS[3]`)와 티어 사다리(`KEYS[4]`)를 직접
읽는다. 그래서 **티어 사다리 중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi`가
엉뚱한 칸을 가리킨다** — 사다리는 큐가 비어 있을 때 바꿔라.

**`tierLo`/`tierHi`는 `ZRANK` 값 그대로다 — 0부터 센다.** 예전에는 `ZRANK + 1`로 적고 읽는
쪽에서 1을 뺐는데, 더하고 빼는 자리가 둘로 갈려 한쪽만 고치면 칸이 어긋났다. 지금은 쓰는 쪽이
`ZRANK`를 그대로 적고 읽는 쪽이 `ZRANGE lo hi`에 그대로 넘긴다. 티어를 안 보는 모드의 자리
채움 값도 `1/1`이 아니라 **`0/0`**이다.

**찾기와 합류가 두 스크립트로 나뉘어 있다.** 그 사이에 차단 검증(자바)이 끼기 때문이다.
두 호출 사이의 틈은 Lua가 아니라 `redisLock/PoolLock.java`의 후보 풀 락(Redisson `RLock`,
키는 `qm:lock:pool:` + `LolPartyKeys#poolKey()` = needs 키에서 keyValue만 뺀 조합)이 막는다.
락 키에 keyValue를 넣지 마라 — 이유는 그 클래스 주석에 있다. Redisson은 `qm:lock:*`만
만지고 데이터는 계속 `StringRedisTemplate` + Lua가 다룬다 (`config/redis/RedissonConfig.java`).

### 원자성 규칙

> **`GET → 애플리케이션 판단 → SET`으로 불변식을 지키지 마라.**

확인과 쓰기 사이에 다른 요청이 끼어들면 불변식이 깨진다. INV-1/2/3/4/5/7은 전부
**Lua 스크립트 한 덩어리**로 처리한다. 새 불변식을 추가할 때도 같은 방식을 따른다.
Lua 안에 **후보를 순회하는 루프를 넣지 마라** — 쓰기를 한 스크립트는 `SCRIPT KILL`이
안 되고 `SHUTDOWN NOSAVE`만 남는다 (docs/11 #33).

### Lua 스크립트는 게임별로 나눈다 (docs/11 D-7)

```
backend/src/main/resources/redis/
├── shared/
│   └── claim-request.lua      게임을 보지 않는다. INV-1 은 사용자 단위다
├── lol/                       배정·취소 5개
├── proposal/                  수락·거절·만료·확정 정리 4개. 게임을 보지 않는다 —
│                              제안은 파티 HASH 위에서만 돌아가고 조건을 읽지 않는다
├── pubg/                      배정·취소 5개. 색인이 (핵심조건 x 티어) 격자가 아니라
│                              **티어 한 줄**이다 — 플랫폼은 한 파티에 겹쳐도 되기 때문이다
└── valorant/                  배정·취소 5개. 합류할 때마다 파티 티어 범위를 좁힌다
                               (아래 "티어 범위를 다루는 방식" 참고)
```

**한 게임의 수정이 다른 게임 스크립트에 닿지 않게 한다.** 게임을 추가할 때 기존 게임
스크립트를 고쳐 쓰지 말고 그 게임 디렉터리에 자기 것을 둔다.

**티어 범위를 다루는 방식이 게임마다 다르다.** LoL과 PUBG는 파티가 받아들일 범위를 **만든
사람 기준으로 한 번** 정하고 그 뒤 바꾸지 않는다. **VALORANT는 합류할 때마다 좁힌다** —
`valorant/join-party-tiered.lua`가 파티 범위를 "지금 범위 ∩ 들어온 사람의 tier-range 줄"로
바꿔, 옛 범위 칸 전부에서 파티를 빼고 **아직 빈 역할군만** 새 범위 칸에 다시 올린다(정렬값은
지금 시각이 아니라 파티의 `createdAt`이다 — now를 쓰면 이 파티만 색인에서 가장 새 것으로 밀린다).
발로란트 규칙이 "파티 최고 티어 <= 한계(파티 최저 티어)"라 3인 파티는 방장 줄 하나로 표현되지
않기 때문이다. 그 대신 후보로 잡힌 파티는 곧 같이 갈 수 있는 파티라, 찾기 쪽에 거르는 분기가 없다.

그래서 VALORANT에만 딸린 것이 둘 있다.
- `qm:party:needs-roles:{partyId}` SET — 아직 비어 있는 역할군. create가 `SADD`, join이
  합류 때 `SREM`, 정원이 차면 `DEL`. 범위를 좁힌 뒤 어느 칸을 다시 만들지가 이 목록이다.
  **취소도 이 키를 되돌린다** — `valorant/leave-party.lua`가 빠진 사람의 역할군을 `SADD` 한다.
- 파티 HASH의 `minTier`/`maxTier` — 지금까지 들어온 사람의 최저·최고 순번(`ZRANK`, 0부터).
  `join-party-tiered.lua`가 읽어 범위를 좁히고, `leave-party.lua`가 남은 사람 기준으로 다시 적는다.

**KEYS 배치도 LoL과 다르다.** `KEYS[5]`가 역할군·티어가 없는 **밑동** needs 키이고
`KEYS[6..]`이 역할군별 needs 키다. 칸은 `KEYS[5 + p] .. ':' .. 티어이름`으로 조립하고,
밑동은 `needs-roles`에서 받은 역할군 이름으로 칸을 다시 만들 때 쓴다.

**게임 패키지(`rule/{game}` · `config/redis/{game}` 등) 안의 클래스는 `Lol*` / `Pubg*`, 스크립트 빈은
`lol*` / `pubg*` 접두사를 붙인다.** 같은 타입(`RedisScript<List>`) 빈이 여럿이라 Spring은 주입 필드
이름 = 빈 이름으로 고르므로, 이름이 겹치면 기동이 실패하거나 **에러 없이 다른 게임 스크립트가 주입된다.**

**Redis 설정은 `config/redis/`에 둔다** — 게임 무관(`RedisConfig` · `RedissonConfig`)은 바로 아래,
게임별 스크립트 빈은 `config/redis/{game}/`(`LolRedisConfig`). 락 코드는 `redisLock/`(`PoolLock`),
게임 공통 스크립트 결과 읽기는 `rule/ScriptSupport` — 이걸 쓰려면 게임 Lua가 `member:{userId}` 필드와
`{code, ...}` 반환 약속을 지켜야 한다.

`claim-request.lua`만 `shared/`에 남는다. 게임을 구분하지 않을 뿐 아니라, 나누면
배그를 하다 롤 큐를 또 잡을 수 있게 되어 **INV-1이 오히려 깨진다.**

**나눈 대가가 있다. 게임마다 불변식 동시성 테스트가 있어야 한다.** 스크립트가 한 벌일
때는 한 벌의 테스트가 전부를 지켰지만, 나뉜 뒤로는 테스트 없는 게임 스크립트가 **아무도
실행하지 않는 코드**가 된다. 그러면 잘못된 수정이 그 게임에서만 조용히 깨진 채 배포된다 —
나눠서 막으려던 일이 그렇게 일어난다. **이제 세 게임 모두 테스트가 있다** — **LoL 경로**
(동시성 3종 + 알림 + 제안 멱등성) · **VALORANT 경로**(`ValorantPartyJoinConcurrencyTest`, 8건) ·
**PUBG 경로**(`PubgPartyJoinConcurrencyTest`, 9건). 게임을 추가하면 그 게임의 동시성 테스트도
같이 만들어라 — 그것이 나눈 대가다.

**가드·TTL·반환 코드처럼 게임과 무관한 변경은 모든 게임 디렉터리에 같이 넣어야 한다.**
2026-09-08 의 `EXISTS` 가드 + `PERSIST` 는 스크립트 4개에 같은 내용을 넣은 작업이었다.
그런 변경을 할 때는 전 디렉터리를 훑었는지 확인해라.

## 5. Contract first

- 이 저장소가 노출하는 계약만 `contracts/`에 발췌해 두었다
  (`contracts/README.md` / `contracts/openapi.yaml` / `contracts/events.md`).
- **원본 계약은 queueMate 본 저장소의 `contracts/`다.** 소유 영역 밖 계약을 임의로
  바꾸지 마라. 바꿔야 하면 본 저장소에서 contract 변경 커밋을 먼저 만든다.
- 현재 코드와 계약이 어긋난 지점은 `contracts/README.md`에 표로 정리돼 있다.
  **코드를 계약에 맞추기 전에 그 표를 읽어라.** 일부는 코드가 맞고 계약이 낡았다.

## 6. Definition of done

기능 완료 조건:
1. happy path 구현
2. 실패 / 중복 / timeout 처리
3. 테스트 추가
4. 로그 / metric 포인트 추가
5. API contract 불일치 없음 (또는 `contracts/README.md`에 불일치를 기록)
6. 해당 invariant 검증

## 7. 작업 방식 — 서브 에이전트로 처리한다

작업 지시를 받으면 **기본적으로 서브 에이전트를 띄워서 처리한다.** 직접 파고들지 않는다.

- 조사, 코드 탐색, 문서 정리, 여러 파일에 걸친 변경은 전부 서브 에이전트에 맡긴다
- 서브 에이전트는 이 대화를 모른다. **필요한 맥락을 프롬프트에 다 적어 준다** —
  읽어야 할 파일, 건드리면 안 되는 파일, 지금까지 정해진 결정
- 서로 겹치지 않는 일이면 **여러 개를 한 번에 띄운다**
- 돌아온 결과는 그대로 옮기지 말고 **직접 확인한 뒤** 요약해서 보고한다

예외는 하나다 — 사용자가 **묻기만 한 것**(설명, 확인, 의견)은 서브 에이전트 없이 바로 답한다.

### 검증할 때 무엇을 돌리나

**`MatchingApplicationTests`를 습관적으로 돌리지 마라.**

```bash
cd backend                                                      # 여기서 돌린다
./gradlew test --tests 'com.queuemate.matching.concurrency.*'   # 기본
./gradlew test                                                  # 설정/의존성을 건드렸을 때만
```

측정값이다 — 전체 3분 중 `MatchingApplicationTests` 하나가 **168초**를 쓴다.
그중 실제 테스트는 3.7초이고 나머지는 Spring 컨텍스트 기동이다. 이 저장소가
`/mnt/c`(윈도우 파일시스템)에 있어 클래스패스 스캔이 특히 느리다.

그 테스트가 보는 것은 "앱이 뜨는가" 하나뿐이므로, `backend/build.gradle` /
`backend/src/main/resources/application.yaml` / 빈 등록을 건드렸을 때만 의미가 있다. 불변식을 지키는 것은
동시성 테스트 3종이고 그건 20초면 끝난다.

`bootRun`으로 앱을 띄웠으면 **반드시 종료해라.** 안 죽이면 포트 8080이 물려
다음 검증이 실패한다 (실제로 3시간 물려 있던 적이 있다).

## 8. Commit convention

AngularJS commit convention. 형식: `type(scope): subject`

- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: 이 저장소에서 쓰는 것은 `matching` `gameconfig` `common` `contracts` `docs` `infra`
- subject: **한글**, 50자 이내, 끝에 마침표 없음
- body: **한글**로 무엇을/왜. 어떻게는 코드가 말한다. 3줄 이내
- type/scope 키워드만 영어를 유지한다
- footer: `BREAKING CHANGE: <설명>`, revert는 `revert: <원 subject>` + 원 commit hash

규칙:
- shared 파일(`contracts/**`, `docs/**`, `CLAUDE.md`) 변경과 feature 구현을 **한 커밋에
  섞지 않는다.**
- 하나의 커밋은 하나의 목적만 담는다. 되돌릴 이유가 다르면 커밋도 다르다.
- type이 다르면 나눈다. 의존성/설정(`build`)과 구현(`feat`)을 섞지 않는다.
- 기능과 그 기능의 테스트는 §6상 한 덩어리이므로 같은 커밋에 담는다.
- 각 커밋 시점에서 빌드가 통과해야 한다.

## 9. 이 저장소에서 하지 말 것 (요약)

- `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 추가 — `app:realtime`의 일이다
- 예약 REST(`/api/v1/reservations`) 서빙 — `app:reservation`(Lambda)의 일이다 (docs/11 D-15)
- 파티를 DB에 만드는 코드 — `app:platform`이 `ProposalConfirmed.fifo`를 소비해서 한다
- `match_requests` 테이블 — docs/11 #27이 금지한다
- Redis 장애 시 우회 매칭 경로 — INV-10이 금지한다
- 매칭 조건 5번째 추가 — docs/12 절차 없이는 금지한다
- 앱 부팅 시 gameconfig를 Redis에 밀어넣기 — 설정은 `seed/gameconfig.redis`가 원본이다
- `?userId=` · 요청 바디의 `userId` 로 사용자를 받기 — 2026-09-27 에 없앴다. "나"는 access 토큰의 `sub` 다(§3 "인증").
  쿠키가 없으면 `userId` 파라미터를 받는 개발용 스위치도 두지 않는다
- 개인 키 · 토큰 서명 · refresh · JWKS 엔드포인트 · jjwt 등 다른 JWT 라이브러리 · Spring `oauth2-client` 들이기,
  `common/security/TokenClaims` 의 값을 `app:platform` 과 따로 바꾸기, `token_use` 를 안 보고 토큰 받기, 상태를 바꾸는 GET
