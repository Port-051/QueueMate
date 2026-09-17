# HANDOFF — 다음 세션 인계

**작성:** 2026-09-15 (화) 12:29 KST · **갱신:** 2026-09-17 (목)
**읽는 순서:** `CLAUDE.md` → `START_HERE.md` → **이 파일의 §0 부터**

이 파일은 "지금 어디까지 왔고 무엇이 열려 있는가"만 담는다. 규칙은 `CLAUDE.md`,
결정의 근거는 `docs/11_DECISION_LOG.md`(특히 **D-8**), 조사 원문과 출처는
`WORKLOG_2026-09-14.md` 에 있다. 여기서 다시 설명하지 않는다.

작업이 끝나면 이 파일을 갱신하거나, 전부 끝났으면 지워라.

---

## 0. 2026-09-17 — 남은 것 전부 (이 절만 읽고 이어갈 수 있다)

아래 §1~§5 는 그날그날의 기록이라 겹치는 곳이 있다. **겹치면 이 절이 우선한다.**

### 0-0. 방금 들어온 것 (문서가 "미구현"이라 적고 있던 것들)

| 무엇 | 어디 | 비고 |
|---|---|---|
| **매칭 요청 상태 조회** | `controller/MatchingController#getMatchRequest` · `service/MatchQueryService` | **경로가 계약과 다르다** — 계약 `GET /match-requests/{requestId}` vs 구현 `GET /match-requests?userId=`. 경로 변수가 없다. 활성 요청이 `qm:user:active-request:{userId}` 로 **사용자 단위** 저장이라(INV-1) requestId 는 찾는 열쇠가 아니고, 이 조회가 가장 필요한 순간(페이지 새로 열기)에 클라이언트는 requestId 를 잃은 상태다. **contract 변경이 필요한 사안이다**(CLAUDE.md §5, 아래 0-1 ⑦). JWT 가 붙으면 `/match-requests/me` 가 된다 |
| 응답 DTO 확장 | `dto/MatchRequestResponse` | **record 로 바뀌고 8필드**가 됐다 — `{status, requestId, queuedAt, partyId, target, memberCount, expiresAt, isAccepted}`. `@JsonInclude(NON_NULL)` 이라 그 갈래에서 뜻이 없는 칸은 응답에서 빠진다. 정적 팩토리 `idle`/`queued`/`proposed`/`matched` 로 만든다 |
| `MatchRequestStatus.IDLE` | `domain/MatchRequestStatus` | 갈래는 `IDLE`(활성 요청 없음) / `QUEUED` / `PROPOSED` / `MATCHED`. **`CANCELLED`·`EXPIRED` 는 enum 에만 있고 조회가 절대 돌려주지 않는다** — 취소·만료는 활성 요청 키를 지우므로 `IDLE` 과 구분되지 않는다 |
| `accept-proposal.lua` 가 시한을 본다 | `redis/proposal/accept-proposal.lua` | `ARGV[3] = now` 를 받아 `expiresAt <= now` 면 `NOT_FOUND`. **INV-5 expired 의 "스위퍼 주기만큼 남던 창"이 닫혔다.** 흔적 지우기는 여전히 스위퍼 몫이다(만료 알림이 거기서 나간다) |
| 도메인 패키지 정리 | `domain/condition/` 신설 | `KeyConditionType`·`VoicePreference`·`PlayPurpose` + `condition/lol/LolPosition` · `condition/valorant/ValorantRole` · `condition/pubg/`(빈 디렉터리 — PUBG 핵심 조건은 플랫폼 문자열이라 enum 이 없다). `GameKey` 는 조건이 아니라 갈래라 `domain/` 에 남았고 `ActiveRequest`·`CancelResult`·`MatchRequestStatus`·`ProposalResult` 도 남았다 |
| Redis 키 통합 | `redisKeys/SharedKeys` 신설 | 자바 쪽 키 문자열의 단일 출처. 게임별 `*PartyKeys` 는 남아서 조각을 조합한다. **Lua 안의 같은 문자열은 그대로다** — 어느 스크립트에 박혀 있는지가 `SharedKeys` 클래스 주석에 목록으로 있다 |
| 활성 요청에 `queuedAt` | `service/MatchRequestService#requestFields()` | 줄 선 시각(epoch millis). **Lua 가 아니라 자바가 필드로 넘긴다** |
| 취소 스크립트 정리 | `{lol,pubg,valorant}/leave-party.lua` | 중복 정리 2줄 제거. 동작 무변경 |
| **PUBG 동시성 테스트 9건** | `concurrency/PubgPartyJoinConcurrencyTest` | **이제 세 게임 모두 테스트가 있다.** 총 41건 — 동시성 24(LoL 7 + VALORANT 8 + PUBG 9) + 제안 멱등성 11 + 알림 6 |

### 0-1. 남은 것 — 우선순위 순

우선순위 근거는 "안 하면 무엇이 안 되는가"다.

#### ① 확정된 사용자를 파티에서 풀어 주는 경로가 없다 — **가장 급하다**

**안 하면: 사용자가 매칭을 평생 한 번만 할 수 있다.** 확정되면
`redis/proposal/cleanup-confirmed.lua` 가 활성 요청을 **지우지 않고** `status='PARTY'` 를 찍는다
(지우면 그 순간 새 매칭을 걸 수 있어 한 사람이 두 파티에 속한다 — INV-2). 그 표시를 푸는 주체가
**아무도 없다.** 그래서 확정된 사용자의 `qm:user:active-request:{userId}` 는 TTL 도 없이
(배정 때 `PERSIST` 로 뗐다) 영원히 남고, 이후 모든 매칭 요청이 409 `ALREADY_QUEUED` 다.

**왜 아직 없나** — 게임이 끝났는지를 이 앱은 모른다. 파티의 수명은 `app:platform` 것이다.

**후보 셋. 사용자와 아직 결론이 안 났다 — 이어받기 전에 물어라.**
1. `PartyClosed.fifo` 소비 — 계약대로다. 다만 `app:platform` 쪽이 먼저 있어야 하고 SQS 배선이
   딸린다(아래 ③과 같은 작업 덩어리).
2. "파티 나가기" API — 이 저장소만으로 닫을 수 있다. 다만 파티 수명을 이 앱이 아는 꼴이 되어
   CLAUDE.md §9("파티를 DB 에 만드는 코드 금지")의 경계와 어디까지 다른지 정해야 한다.
3. 긴 TTL 안전망 — `cleanup-confirmed.lua` 가 활성 요청에 넉넉한 `EXPIRE`(예: 한 게임 길이)를
   건다. 가장 싸고 혼자 할 수 있지만 **시간이 지나면 저절로 풀리는 것**이라 정답은 아니다.
   1·2 가 붙기 전까지의 임시방편으로는 쓸 만하다.

**건드릴 곳**: `redis/proposal/cleanup-confirmed.lua` · `service/ProposalService#confirmed()` ·
(1번이면) 새 SQS 소비자 패키지 · (2번이면) `MatchingController` + `MatchCancelService`.

#### ② INV-6 차단 검증 — **배포 차단 조건**

**안 하면: 배포할 수 없다.** CLAUDE.md §4 INV-6 과 docs/11 #30 이 "차단 검증 없이 배포하지
않는다"고 못 박았다. 게다가 **지금은 기본 실행에서 배정이 조용히 실패한다.**

- **선필터 코드는 있다** — `rule/lol/LolCandidateRule#canJoin()` 이 락을 잡기 전에
  `block/BlockRepository#findBlockedUserIds()` 를 실제로 부르고 `rule/ScriptSupport#blockedWith()`
  로 거른다.
- **스키마가 없다** — `backend/build.gradle` 에 **Flyway 의존성이 없고**(확인함)
  `application.yaml` 이 `ddl-auto: none` 이다. 기본 실행(H2)에 `social.blocks` 테이블이 없어
  그 조회가 터진다. 배정이 `@Async` 안이라 **요청은 201 로 나가고 배정만 조용히 실패한다.**
  테스트만 `ConcurrencyTestSupport` 의 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql`
  로 통과한다.
- **확정 직전 최종 검증이 없다** — docs/11 D-1 의 `social.blocks` 동기 SELECT.
  이제 확정 경로(`accept-proposal.lua` → `ProposalService#accept()`)가 있으므로 그 앞에 붙일 수 있다.
- **선필터가 LoL 에만 있다.** `rule/pubg` · `rule/valorant` 의 CandidateRule 도 같은 검사를
  해야 하는지 확인해라 — 안 하면 두 게임은 차단 관계가 그대로 한 파티가 된다.

**건드릴 곳**: `backend/build.gradle`(Flyway) · `backend/src/main/resources/db/migration/`(신설) ·
`application.yaml` · `block/BlockRepository` · `service/ProposalService` ·
`rule/{pubg,valorant}/*CandidateRule`.

#### ③ 확정 후속 처리의 나머지 — outbox → `ProposalConfirmed.fifo`

**안 하면: 확정돼도 파티가 DB 에 안 생긴다.** `app:platform` 이 파티를 만들 신호를 못 받는다.
Redis 쪽 뒷정리(`cleanup-confirmed.lua`)와 `MATCH_CONFIRMED` 알림까지는 붙었는데 거기서 끝난다.

- **AWS SDK 의존성부터 없다** (`backend/build.gradle` 확인함 — `outbox`/`sqs`/`ProposalConfirmed`
  로 grep 하면 자바 코드는 0건이고 주석만 나온다).
- `matching.outbox` 테이블도 없다(Flyway 가 없으므로 ② 와 같은 덩어리다).
- ① 을 `PartyClosed` 로 풀기로 하면 그 소비도 여기 딸린다.

**건드릴 곳**: `backend/build.gradle` · `service/ProposalService#confirmed()` ·
새 `outbox/` 패키지 · Flyway 마이그레이션.

#### ④ `BlockChanged.fifo` 소비 없음

계약(`contracts/events.md`)에는 있고 코드에는 없다. **급하지 않다** — ② 가 DB 직접 조회로
도는 한 필요 없다. Redis 선필터(`qm:block:{userId}`, docs/11 D-2)를 켜기로 할 때 필요해진다.

#### ⑤ 메트릭 0건

`CLAUDE.md` §6 Definition of done 4번이 요구하는데 `MeterRegistry` / `@Timed` / `Metrics.` 가
`backend/src/main` 에 **0건**이다. `spring-boot-starter-actuator` 는 이미 의존성에 있고
`/actuator/metrics` 도 열려 있어(`application.yaml`) JVM·HTTP 기본 지표는 나온다.
**없는 것은 매칭 고유 지표다** — 큐 대기 시간(이제 `queuedAt` 이 있으니 잴 수 있다), 배정까지
걸린 시간, 제안 수락률, 만료·취소 건수, 후보 풀 락 대기 시간.

**건드릴 곳**: `service/*` · `rule/*/*Assigner` · `redisLock/PoolLock` · `ProposalSweeper`.

#### ⑥ 부하 테스트가 안 돈다

티어가 필수가 된 뒤로 `load-test/` 가 그대로는 못 돈다. 두 군데가 어긋난다 (재확인함).
1. **요청 바디** — `load-test/match_latency.py` 의 `body()` 가 `modeKey: RANKED_SOLO` 를
   `tier` 없이 보낸다. 시드의 `RANKED_SOLO` 는 `tierRule EXIST` 라 validator 가 400 을 낸다.
   `prefill.py` · `measure.js` · `stock.js` · `throughput.js` · `netpath/postload.js` 도 같다.
2. **색인 키** — `prefill.py` · `run.sh` · `netpath/runpost.sh` · `runpost2.sh` 가 티어 접미사
   없는 needs 키를 `ZCARD` 하는데, 티어 모드는 Lua 가 `:{tier}` 를 붙이므로 그 키는 비어 있다.

`tierRule NONE` 인 모드(`NORMAL_2` 등)로 바꾸거나, 바디에 `tier` 를 싣고 키에 같은 접미사를
붙여 맞춰라. **성사 감지 자체는 이미 고쳐져 있다**(`32031a4`, `member:` 필드를 센다).

#### ⑦ 계약 정리 — 본 저장소 contract 변경이 선행

`contracts/openapi.yaml` 은 **원본의 발췌**라 여기서 고치지 않는다 (CLAUDE.md §5).
지금 어긋난 것은 `contracts/README.md` 의 불일치 표에 전부 적어 두었다. 큰 것만:
- `VoicePreference` 에 `OPTIONAL` 이 남아 있다 (#1, **코드가 맞다**)
- `KeyCondition.type` 이 `PLAY_STYLE` 이다 — 코드는 `PLATFORM` (#14, **코드가 맞다**)
- **상태 조회 경로** `/{requestId}` vs `?userId=` (#5, **코드가 맞다고 보고 그렇게 뒀다**)
- `MatchRequestView` 4필드 vs `MatchRequestResponse` 8필드 (#4 — 이름은 계약이, 필드 수는
  구현이 앞서 있다. `queuedAt` 은 타입도 다르다: 계약 `date-time` vs 구현 epoch millis)
- **`POST /match-requests` 의 201 본문이 JSON 이 아니라 문자열 `"CREATED"` 다** (#5-1,
  **계약이 맞다. 구현을 고쳐야 한다** — 아래 0-2 참고)
- `tier` 필드가 계약에 없다 (#13), `GET /games` 가 구현에 없다 (#7)

### 0-2. 코드에서 발견한 작은 것들 (문서로는 못 고친다)

이번에 문서만 고쳤으므로 `backend/src/**` 는 손대지 않았다. 다음에 코드를 만질 때 같이 처리해라.

| 무엇 | 어디 | 내용 |
|---|---|---|
| **접수 응답이 DTO 를 안 쓴다** | `MatchingController#createMatchRequest` | `.body("CREATED")` — 문자열이다. `MatchRequestResponse.queued(requestId, queuedAt)` 정적 팩토리가 이미 있는데 쓰이지 않는다. **클라이언트가 `requestId` 를 못 받아 취소(`DELETE /{requestId}`)를 부를 값을 잃는다**(조회로 되찾을 수는 있으나 왕복이 하나 는다). 계약도 여기서 `MatchRequestView` 를 돌려주게 돼 있다 |
| 안 쓰는 import | `MatchingController` 5번째 줄 | 위 변경으로 `domain.MatchRequestStatus` 가 안 쓰이게 됐다 |
| 낡은 주석 | `controller/ProposalController.java:64` | "알림을 놓쳤을 때의 복구는 조회로 한다 (`MatchingController#getMatchRequest` — **아직 미구현**)" — 구현됐다 |
| 낡은 주석 | `rule/CandidateRule.java:14` | 인터페이스 머리말이 "`VALORANT` — **미구현**" 이라고 적고 있다. `ValorantCandidateRule` 은 커밋돼 있다 |
| 낡은 주석 | `redis/pubg/create-or-check-party-untiered.lua:37` | "PUBG 취소 스크립트(`leave-party.lua`)가 **아직 없다**" — 있다 |
| 남은 TODO | `service/ProposalService.java:161` | 거절 뒤에 **남은 사람들에게 제안이 깨졌음을 알리는 것**이 없다. 지금은 거절한 본인만 큐에서 빠지고, 수락해 놓고 기다리던 사람들은 제안 화면에 갇힌 채 아무 알림도 못 받는다 (만료 경로는 `MATCH_PROPOSAL_EXPIRED` 를 전원에게 보내는데 거절 경로에는 그 대응이 없다). 위 ①~③ 보다 작지만 **사용자가 실제로 겪는 문제**다 |
| 낡은 주석 | `redis/lol/create-or-check-party-untiered.lua:19` · `join-party.lua:18` | `ARGV[3]` 주석의 "PUBG 플레이 스타일" — 지금 PUBG 조건은 `PLATFORM` 이고 이 스크립트는 LoL 전용이다. 같은 흔적이 `create-or-check-party-untiered.lua:36` · `join-party.lua:34` · `join-party.lua:111` 의 "칼바람이나 PUBG처럼" 에도 있다 (2026-09-17 줄 번호 재확인) |

### 0-3. 이번에 고친 문서

`contracts/README.md`(불일치 표 #4·#4-1·#5·#5-1, 501 항목 제거, "남은 구멍" → "메워진 구멍") ·
`CLAUDE.md`(INV-5 ③ 만료 창, INV-8 상태 열, 회귀 테스트 표에 PUBG 행, `ProposalIdempotencyTest`
경고 문단 삭제, `domain/condition/` 경로, `SharedKeys` 규칙 추가) · `START_HERE.md`(핵심 사실,
스모크 호출, 패키지 트리, Redis 키 표의 `queuedAt`, §4.1/§4.2/§4.3, §5 표) ·
`docs/11`(R 절) · `docs/02`(부록 D-4). **`docs/07_REDIS_DESIGN.md` 는 "원문 verbatim, 수정 금지"라
건드리지 않았다** — 그 문서가 `qm:user:active-request:{userId}` 를 아직 `STRING requestId` 로,
제안을 별도 `qm:proposal:{id}` 레코드로 적고 있는 것은 그래서다. 지금 구현은 HASH 이고
제안 상태는 파티 HASH 에 얹힌다. **실제 키 구조는 `START_HERE.md` 의 Redis 키 표를 봐라.**

---

## 1. 지금 상태

- **LoL 티어 재설계 완료** (docs/11 D-8). `LolTier` enum 삭제 → Redis ZSET
  `qm:gameconfig:LOL:tier`(단 포함 32개). `tierRule` 은 `NONE`/`EXIST` 둘. Lua 가 칸 키를 조립한다.
- **`KeyConditionType.PLAY_STYLE` → `PLATFORM`** (PUBG = `STEAM` / `KAKAO`).
- 쓰이지 않던 `AcceptanceStatus` / `ProposalStatus` 삭제.
- **PUBG 시드 들어감** (모드 8 / 티어 사다리 27 / 랭크 티어 범위 표 4). **모드 목록 SET 은 LoL·PUBG 모두 없앴다.**
- **PUBG validator 커밋됨** (`validation/pubg/PubgConditionValidator.java`). PUBG 배정 규칙은 사용자가 작성 중.
- **Lua 스크립트 빈 설정을 나눴다** (`02d654f`). 공통(claim·accept·decline + `readScript()`) / LoL 5개.
- **Redis 설정 클래스를 `config/` 에서 `redis/` 패키지로 옮겼다** (`b99525a`). `redis/RedisConfig` ·
  `redis/RedissonConfig`(게임 무관, `PoolLock` 옆) / `redis/lol/LolRedisConfig`(게임별). 빈 이름은 그대로다.
  `config/` 에는 `AsyncConfig` 하나만 남았다. **규칙: Redis 설정은 `redis/`, 게임 무관은 바로 아래, 게임별은 `redis/{game}/`.**
- **LoL 클래스와 스크립트 빈에 게임 접두사를 붙였다** (`bf4b0da`). `LolModeConfig` `LolPartyLeaver`
  `LolTieredAssigner` `LolUntieredAssigner` / 빈 `lolCreateOrCheckParty{Untiered,Tiered}Script`
  `lolJoinParty{Untiered,Tiered}Script` `lolLeavePartyScript`. 티어 합류 빈은 이름이
  `joinTieredPartyUntieredScript` 로 틀려 있던 것을 `lolJoinPartyTieredScript` 로 같이 고쳤다.
- **부하 테스트 성사 감지 수정** (`32031a4`). `match_latency.py` 가 없어진 `size` 필드 대신 파티 HASH 의
  `member:` 필드를 센다. **고치기만 했고 다시 돌리지는 않았다 — 지금은 그대로 못 돈다(§3-D).**
- **코드와 어긋난 주석 수정** (`b05e2eb`). `PoolLock` · `CandidateRule` · `lol/create-or-check-party-untiered.lua` ·
  `lol/join-party.lua` 주석만. 코드 줄은 그대로다.
- **`docs/GAME_CONFIG.md` 에 PUBG 설정 반영, 모드 목록 SET 서술 제거** (`822599e`).
- **GitHub**: private 저장소 `github.com/rlaehddus302/queuemate-matching` (`main`).

### 2026-09-16 갱신 — 아래 §1 본문 중 낡은 것

이 절이 우선한다. 본문은 2026-09-15 기준이라 그대로 두었다.

- **PUBG 배정이 전부 커밋됐다.** `rule/pubg/` 6개 · `config/redis/pubg/PubgRedisConfig.java` ·
  `redis/pubg/*.lua` 5개. 아래 "작업 트리에 커밋 안 된 것"은 해소됐다.
- **`tierLo`/`tierHi` 가 `ZRANK` 값 그대로(0부터)가 됐다** (`6fe3f99`). `+ 1` 로 적고 읽을 때
  1 을 빼던 것을 없앴고, 티어를 안 보는 모드의 자리 채움 값도 `1/1` → **`0/0`** 이다.
  LoL·PUBG 스크립트 8개를 같이 고쳤다 (docs/11 Q-1).
- **VALORANT 가 전부 들어왔다** (`79a9c02` 티어 Lua 2개 → `7559b89` 배정·취소 →
  `c4c946c` 시드 → `b256769` 동시성 테스트). Lua 5개 · `rule/valorant` 6개 ·
  `ValorantRedisConfig` · 시드(모드 4 / 사다리 26 / tier-range 표 2) ·
  `ValorantPartyJoinConcurrencyTest`. 색인은 (역할군 x 티어)이고 **합류마다 파티 티어 범위를
  좁힌다** — LoL·PUBG 의 "범위는 만든 사람 기준으로 한 번 정해진다"가 발로란트에는 해당하지 않는다.
  곁딸린 키 `qm:party:needs-roles:{partyId}` SET 과 파티 HASH 의 `minTier`/`maxTier` 가 같이 생겼다.
- 테스트는 이제 LoL 과 VALORANT 경로를 탄다. ~~PUBG 스크립트를 도는 테스트는 아직 없다.~~
  **(2026-09-17 해소 — `PubgPartyJoinConcurrencyTest` 9건. §0-0)**
- **취소가 제안의 흔적을 지운다** (`3d3efaf`). `{lol,pubg,valorant}/leave-party.lua` 가 멤버를 빼기
  전에 `status`/`expiresAt` HDEL + 수락자 SET DEL 을 한다. INV-5 의 cancelled 구멍이 막혔다.
- **제안 만료를 구현했다** (`251453a`). 새 키 `qm:proposal:pending` ZSET(member = partyId,
  score = `expiresAt`) + `service/ProposalSweeper`(`@Scheduled`, `queuemate.sweep.interval-ms`,
  회차당 100건) + `service/ProposalExpiryService` + `redis/proposal/expiry-proposal.lua`.
  `MatchingApplication` 에 `@EnableScheduling` 을 붙였다(별도 `SchedulingConfig` 는 두지 않았다).
  **ZADD 는 합류 스크립트 6개의 `HSETNX status 'PENDING'` 성공 분기 안**이고, ZREM 은 제안이
  끝나는 모든 자리(확정·거절·취소·만료)에 있다. 정책은 **B안** — 만료되면 수락하지 않은 사람만
  큐에서 빼고, 수락한 사람은 파티에 남아 빈자리가 채워지면 **다시 눌러야 한다**(옛 수락 기록은
  지워진다). `MATCH_PROPOSAL_EXPIRED` 는 그 제안에 있던 전원에게 나간다.
  INV-5 의 expired 구멍이 막혔다. ~~다만 `accept-proposal.lua` 는 여전히 `expiresAt` 을 보지
  않으므로 시한 직후 스위퍼가 꺼내기 전까지(주기만큼)는 수락이 그대로 확정된다.~~
  **(2026-09-17 해소 — `ARGV[3] = now` 로 시한을 보고 `NOT_FOUND` 를 돌려준다. §0-0)**
- **확정 후속 처리의 절반이 들어왔다** (작업 트리, 아직 커밋 안 됨). 새 스크립트
  `redis/proposal/cleanup-confirmed.lua` 를 `ProposalService#accept()` 가 확정 직후 부른다 —
  파티원의 **활성 요청을 지우지 않고 `status='PARTY'` 를 찍고**(지우면 그 순간 새 매칭을 걸 수
  있어 INV-2 가 깨진다), 파티 HASH 는 남기고, 수락자 SET 에만 TTL
  (`queuemate.proposal.confirmed-retention-seconds`, 기본 60)을 건다. 돌려받은 파티원 전원에게
  `MATCH_CONFIRMED`(payload `{partyId}`)를 발행한다. 같은 작업 트리에서 `accept-proposal.lua` 가
  **이미 확정된 제안의 재수락에 `ALREADY_RESPONDED`** 를 돌려주도록 바뀌었고(확정 알림이 두 번
  나가지 않게 하려는 것이다), 컨트롤러는 수락 분기에서 `ACCEPTED`/`CONFIRMED`/`ALREADY_RESPONDED`
  를 **모두 204** 로 받는다(거절 분기의 `ALREADY_RESPONDED` 는 409 그대로다).
- ~~⚠️ `ProposalIdempotencyTest` 가 지금 작업 트리와 어긋난다.~~ **해소됐다** — 테스트가 새 값
  (`ALREADY_RESPONDED`)에 맞춰졌고 11건 전부 통과한다. 다만 **만료·확정 뒷정리를 덮는 테스트는
  아직 없다** (`MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED` 를 구독해 보는 `PushNotificationTest`
  항목 포함).
- **그래서 §3-B 는 대부분 해소됐다.** 그중 상태 조회와 PUBG 동시성 테스트도 2026-09-17 에
  들어왔다. **지금 남은 것은 §0-1 을 봐라** — `matching.outbox` + `ProposalConfirmed.fifo`,
  확정된 사용자를 푸는 경로, Flyway + `social.blocks`(INV-6) 다.

### 테스트 — 커밋 `8d7f094` 기준 24건 통과 (2026-09-15)

> **2026-09-17 기준은 41건이다** — 동시성 24(LoL 7 + VALORANT 8 + PUBG 9) + 제안 멱등성 11 +
> 알림 6. 아래 24건 기록은 2026-09-15 시점이라 그대로 둔다.

`concurrency.*` 7 + `PushNotificationTest` 6 + `ProposalIdempotencyTest` 11 = **24/24 통과.**
결과 로그에서 `ERR Error running script` / `RedisSystemException` / ERROR 로그도 0건이었다.

**설정 분리(`02d654f`)도 같은 24건으로 컨텍스트 기동까지 확인했다** (HEAD `31d23f7` + 설정 두 파일, PUBG 파일 제외).
**접두사 변경(`bf4b0da`)도 같은 24건 통과, 기동 오류 0건.**

그 전 커밋 중 자바를 바꾼 것은 `4c1c492`(PUBG validator 추가)다. 나머지(`af3b7ff` 시드 ·
`3198f74` `07b77be` `13dea28` 문서)는 자바를 건드리지 않았고, 테스트는 자기 시드를 직접 심으므로 영향이 없다.
**PUBG validator 는 그 24건에 포함되지 않았고, PUBG 를 검증하는 테스트는 아직 하나도 없다.**

### PUBG validator — 커밋됨, 규칙은 사용자가 작성 중

`validation/pubg/PubgConditionValidator.java` (파일명 오타 `Conditoin` 고침). 사용자 코드에 세 가지를 고쳐 커밋했다.
- 뒤집혀 있던 티어 분기(`EXIST` 모드가 `"NONE"` 만 통과시키던 것)
- **티어 없음 규약을 롤과 맞춤** — `NONE` 모드는 `tier` 가 **null** 이어야 통과. 클라이언트는 tier 를 안 싣는다
- `keyCondition` null 가드 + `type == PLATFORM` 확인

⚠️ **새 빈 등록인데 컨텍스트 기동 테스트(`MatchingApplicationTests`)는 안 돌렸다.** 의존성이
`StringRedisTemplate` 하나로 `LolConditionValidator` 와 같은 모양이라 위험은 낮지만, Redis 를 띄울 일이
생기면 같이 확인해라.

**아직 남은 문제 (사용자가 알고 있고 나중에 올린다고 했다):** validator 는 떴는데 PUBG `CandidateRule` 이
없다. PUBG 요청이 검증을 통과하면 `claim-request.lua` 가 활성 요청을 선점한 뒤 `MatchTrigger` 가 규칙을 못 찾아
`IllegalArgumentException("파티 배정 규칙이 없는 게임")` 으로 끝난다. `@Async` 라 201 로 나가고 그 사용자는
claim 의 `EXPIRE 60` 동안 다른 매칭을 못 잡는다.

**작업 트리에 커밋 안 된 것 — 건드리지 마라.**
- 사용자 작성 중: `rule/pubg/` 전체(`PubgCandidateRule` `PubgModeConfig` `PubgPartyKeys` `PubgScriptSupport`
  `PubgTieredAssigner` `PubgUntieredAssigner`), `resources/redis/pubg/create-or-check-party-{tiered,untiered}.lua`.
  `PubgPartyKeys` 는 IntelliJ 가 스테이징해 둔 상태(`AM`)다 — 커밋에 딸려 들어가지 않게 조심해라
- **Claude 가 만들었지만 일부러 안 올린 것:** `redis/pubg/PubgRedisConfig.java` — 위 PUBG Lua 두 개를 읽는 빈이다.
  Lua 가 커밋되지 않은 채 이것만 올리면 커밋된 코드로는 앱이 안 뜬다. **사용자가 PUBG Lua 를 올릴 때 같이 올려라.**

⚠️ **IntelliJ 가 새 파일을 git 에 자동으로 스테이징한다.** 실제로 위 Lua 두 개가 문서 커밋 `b785297` 에
딸려 올라갔다가 `e8932ad` 로 추적에서만 뺐다(원격 이력에는 남아 있다). 커밋은
**`git commit <파일경로>` 로 파일을 지정**하거나, 하기 직전에 `git diff --cached --stat` 으로 스테이징된 목록을 확인해라.

---

## 2. 사용자가 정한 것 (2026-09-15)

- **폴더는 아무것도 지우지 않는다.** `domain/lol/LolPosition` 은 쓰인다.
- **`domain/` 구조는 그대로 유지한다.** `domain/common/` 으로 옮기지 않는다.
- **PUBG 모드는 8개** (NORMAL/RANKED × DUO/SQUAD × TPP/FPP).

---

## 3. 다음 할 일

> **2026-09-17: 이 절은 대부분 끝났다.** A(PUBG)는 테스트까지 들어와 완료됐고, B 는 §0-1 ①·③ 만
> 남았다. C(INV-6)는 그대로 남아 §0-1 ② 다. D 는 아래 각 항목에 표시해 두었다.
> **지금 할 일의 최신 목록은 §0-1 이다.**

### A. PUBG 구현 (~~진행 중~~ **완료, 2026-09-17**)

~~**시드와 validator 는 들어갔다. 배정 규칙·Lua·테스트는 아직 없다.**~~
**전부 들어왔다** — 시드 · `PubgConditionValidator` · `rule/pubg/` 6개 · `redis/pubg/*.lua` 5개 ·
`PubgRedisConfig` · `PubgPartyJoinConcurrencyTest` 9건. 아래 표와 설계 근거는 기록으로 남긴다.
모드 목록 SET(`qm:gameconfig:modes:*`)은 사용자 결정으로 **없앴다** — 모드 존재는 모드 HASH 로 판단한다.

| 키 | 내용 |
|---|---|
| `qm:gameconfig:PUBG:{modeKey}` | `NORMAL_{DUO,SQUAD}_{TPP,FPP}` → `tierRule NONE` / `RANKED_{DUO,SQUAD}_{TPP,FPP}` → `EXIST`. 듀오 2, 스쿼드 4. **`positionUniqueness` 필드는 없다** — 포지션이 없어 중복 금지할 대상이 없다(LoL 과 다르다) |
| `qm:gameconfig:PUBG:tier` | ZSET 27개. `0 UNRANKED`, `1 BRONZE_4` … `24 DIAMOND_1`, `25 MASTER`, `26 SURVIVOR` (크리스탈 포함 6티어 × 4단) |
| `qm:gameconfig:PUBG:tier-range:{modeKey}` | **듀오 ±11 / 스쿼드 ±5.** `UNRANKED` 는 `SOLO_ONLY` |

**왜 11 인가** — 공식 규칙은 "파티 단계 차이 **최대 12단계**"(패치 38.1, 2025-10-14)인데
**12 를 포함하는지 확인하지 못했다**(38.1 이후 실측 제보 0건). 틀렸을 때 12 로 잡으면 게임에서
큐가 안 잡히는 파티가 생기고, 11 로 잡으면 딱 12칸 차이 파티만 놓친다 → **fail-closed 로 11.**
실측으로 12 가 확인되면 표만 넓혀라.

**왜 스쿼드는 절반인가** — 규칙은 "파티 최고와 최저의 차이"다. 우리 엔진은 만든 사람 범위 안이면
누구든 받으므로, 4인에 ±11 을 쓰면 파티 폭이 22 가 된다. ±5 면 폭이 최대 10. LoL 솔랭/자랭을
다르게 한 것과 같은 이유(2인은 대칭만, 3인 이상은 전이까지 필요).

**표는 손으로 쓰지 말고 생성 스크립트로 만들고**, 비대칭 0건 / 파티 폭을 기계로 검증해라.

남은 코드:
- **붙이는 자리는 이미 있다.** `MatchConditionValidator` · `MatchTrigger` · `MatchCancelService` 가 전부
  `supports(GameKey)` 로 게임별 구현을 고른다. PUBG 는 `CandidateRule` 구현체를 `@Component` 로 하나 두면 된다
- `rule/pubg/PubgPartyKeys` — `LolPartyKeys` 가 `qm:party:open:LOL:` 과 `qm:gameconfig:LOL:` 을 **박아 두었으므로**
  재사용할 수 없다. 색인 키는 `qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{STEAM|KAKAO}` (+ 랭크는 `:{티어}` 를 Lua 가 붙인다)
- `rule/pubg/PubgCandidateRule` + Assigner — **사용자가 작성 중이다.** 이어받기 전에 물어라
- **스크립트 빈 이름은 게임 접두사가 붙는다** — LoL 은 `lol*`(`redis/lol/LolRedisConfig`), PUBG 는 `pubg*`
  (`pubgCreateOrCheckPartyTieredScript` 등, `redis/pubg/PubgRedisConfig`). 같은 타입(`RedisScript<List>`) 빈이 여럿이라
  Spring 은 **주입 필드 이름 = 빈 이름**으로 고른다. PUBG Assigner 가 LoL 이름(`lolCreateOrCheckPartyTieredScript`)으로
  필드를 선언하면 **LoL 스크립트가 주입된다** — 컴파일도 기동도 통과하고 배그가 롤 Lua 로 돈다. 필드 이름을 반드시
  `pubg...` 로 맞춰라. 클래스 이름도 같은 이유로 `Pubg*` 다(같은 이름이면 빈 이름이 겹쳐 기동이 실패한다)
- `redis/pubg/*.lua` — **LoL 스크립트를 고쳐 쓰지 말고 자기 디렉터리에** (D-7)
- **PUBG 동시성 테스트** — 스크립트를 나눈 대가다(CLAUDE.md §4 "게임마다 테스트")

미확인: PUBG 에 배치 전(UNRANKED) 상태가 있는지 / 단이 없는 MASTER·SURVIVOR 를 몇 단계로 세는지 /
한국 서버 FPP 큐 유무(리전별로 패치마다 바뀜).

### B. 만료 처리 + 확정 후속 (INV-4/5 의 남은 구멍)

> **2026-09-16: 아래 세 줄 중 앞의 둘은 끝났다.** 만료는 `qm:proposal:pending` + 스위퍼로,
> 확정 후속은 `cleanup-confirmed.lua` + `MATCH_CONFIRMED` 로 처리한다. 취소 구멍도 `3d3efaf`
> 로 막혔다. 남은 것은 **outbox → `ProposalConfirmed.fifo`** 와 **`PartyClosed` 소비**뿐이다.
> 자세한 것은 §1 의 "2026-09-16 갱신" 블록.

- **만료:** `expiresAt` 을 쓰기만 하고 읽는 주체가 없다 → 시한이 지난 제안에 수락이 오면 그대로
  확정된다. sweeper 필요(`queuemate.sweep.interval-ms` 설정만 있고 읽는 코드 없음).
- **확정 후속:** `status=CONFIRMED` 만 찍고 끝난다. `ProposalConfirmed.fifo` 발행,
  `MATCH_CONFIRMED` 알림, 파티·색인·활성 요청·수락자 SET 정리가 전부 없다
  (`ProposalService#accept()` TODO).
- `CLAUDE.md` INV-5 행의 "cancelled 구멍"은 **취소 API(`DELETE /match-requests/{id}`)를
  제안 도중에 부를 때만** 해당한다. **거절 버튼 경로는 문제없다** — `decline-proposal.lua` 가
  수락자 SET 과 `status` 를 먼저 지운 뒤 취소를 부른다.

### C. INV-6 스키마 (Flyway) — 배포 전 필수 <sub>(2026-09-17: 그대로 남아 있다 — §0-1 ②)</sub>

### D. 낮은 우선순위 (기록만, 급하지 않음)

- **자랭 파티 생성 시 ZADD 145회.** 두 덩어리라 29칸에 같은 내용이 들어간다. 동작은 맞다.
  덩어리 단위 색인으로 줄일 수 있지만 솔랭(겹치는 범위)에는 안 된다.
- **`join-party.lua`(티어 없는 쪽)에 파티 존재 확인이 없다.** 찾기와 합류 사이 수 마이크로초에
  그 파티의 마지막 멤버가 취소하면 `HSET` 이 파티를 되살려 유령 파티가 된다. 취소가 풀 락을
  안 잡아서 이론상 가능하나 **확률은 극히 낮다.** 티어 쪽은 `HMGET tierLo` 가드로 막혀 있다.
- ~~`CLAUDE.md` §4 INV-8 상태가 "LoL만 구현·테스트됨"~~ **2026-09-17 에 "세 게임 모두
  구현·테스트됨" 으로 갱신했다.**
- `contracts/openapi.yaml` 이 아직 `PLAY_STYLE` — 계약 파일이라 안 고쳤다(`contracts/README.md` #14 에 기록).
- **부하 테스트는 성사 감지만 고쳤고(`32031a4`) 아직 다시 돌지 않는다.** 두 가지가 어긋난다
  (2026-09-15 확인, **2026-09-17 재확인 — 그대로다.** §0-1 ⑥).
  ① 요청 바디: `load-test/match_latency.py` · `prefill.py`(그리고 `measure.js` · `stock.js` · `throughput.js` ·
  `netpath/postload.js`)가 `modeKey: RANKED_SOLO` 를 `tier` 없이 보낸다. 시드의 `RANKED_SOLO` 는 `tierRule EXIST` 라
  `LolConditionValidator#validTier` 가 tier null 을 거절 → **400**. ② 색인 키: `prefill.py`(`...:needs:JUNGLE`) ·
  `run.sh` · `netpath/runpost.sh` · `runpost2.sh`(`K=...:needs` 뒤에 `:{포지션}`)가 티어 접미사 없는 needs 키를 `ZCARD`
  하는데, 티어 모드는 Lua 가 `:{tier}` 를 붙이므로 그 키는 비어 있다. 다시 돌리려면 `NORMAL_2`(정원 2, 포지션 중복 금지,
  `tierRule NONE`) 같은 모드로 바꾸거나, 바디에 `tier` 를 싣고 키에 같은 `:{tier}` 접미사를 붙여 맞춰라.
- **LoL Lua 주석에 PUBG 흔적.** `resources/redis/lol/create-or-check-party-untiered.lua:19` · `join-party.lua:18` 의
  `ARGV[3]` 주석이 "LoL 포지션 / VALORANT 역할 / **PUBG 플레이 스타일**" 이다 — 스크립트는 LoL 전용이 됐고 PUBG 조건은
  `PLATFORM` 이다. 같은 두 파일 `:34`/`:36`(·`join-party.lua:111`)의 "칼바람이나 PUBG처럼" 도 같은 흔적. 코드 파일이라 이번엔 안 고쳤다.

---

## 4. 이미 끝나서 다시 보지 않아도 되는 것

`decline` 의 무조건 `cancel()`(가드 들어감) / `LolConditionValidator` 키 문자열 하드코딩
(`keys.tierRangeKey` 로) / `LolTieredAssigner` 의 `TierRange` 죽은 코드 / 문서 전반의
`TABLE`·`WINDOW`·`maxTierGap`·`LolTier` 서술 / `CLAUDE.md` INV-2·4·5 상태 열 / `PLAY_STYLE` → `PLATFORM` 문서 반영 /
모드 목록 SET 제거와 그 문서 반영 / PUBG validator 의 뒤집힌 티어 분기·null 가드·파일명 오타 /
`RedisConfig` 공통/게임별 분리, LoL 클래스·빈 게임 접두사 / Redis 설정 `redis/` 패키지 이동(`b99525a`)과 문서 반영 /
부하 테스트 성사 감지 `size` → `member:` 세기(`32031a4`, 재실행은 §3-D) / 낡은 코드 주석(`b05e2eb`) /
`docs/GAME_CONFIG.md` PUBG 반영(`822599e`) /
`CLAUDE.md` §3 의 "확정·만료가 미구현" 낡은 문장과 INV-5 행의 `ProposalStatus`/`AcceptanceStatus` 서술.

---

## 5. 테스트 돌리는 법 — 환경 함정

- **2026-09-15 확인 기준으로** Docker 엔진이 꺼져 있고 `redis-server` 가 설치돼 있지 않았다. `sudo` 는 비밀번호를 요구한다. 먼저 `docker ps` / `which redis-server` 로 다시 확인해라.
- 이전에는 **Redis 7.2.5 를 소스에서 빌드해 스크래치패드에 두고 포트 6390** 으로 띄웠다(수 분 걸림).
  **스크래치패드는 세션마다 새로 생기므로 그 바이너리는 없다.** 다시 빌드하거나, 사용자에게
  Docker Desktop 을 켜 달라고 하거나, `! sudo apt install redis-server` 를 직접 쳐 달라고 해라.
- 붙이기: `REDIS_HOST=127.0.0.1 REDIS_PORT=6390 ./gradlew test --tests '...'`
- 작업 트리가 컴파일되지 않으면(사용자 작성 중 파일) `git archive HEAD backend` 를 스크래치패드에 풀어 거기서 돌려라
- **6379 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 절대 건드리지 마라.**
- 끝나면 Redis 종료 + `./gradlew --stop`. `bootRun` 금지.

```bash
cd backend
./gradlew compileJava compileTestJava
./gradlew test --tests 'com.queuemate.matching.concurrency.*'
./gradlew test --tests 'com.queuemate.matching.notification.PushNotificationTest'
./gradlew test --tests 'com.queuemate.matching.proposal.ProposalIdempotencyTest'
```
