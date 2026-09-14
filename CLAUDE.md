# CLAUDE.md — matching 엔진 규칙 (Non-Negotiable)

작업 전에 이 파일과 `START_HERE.md`, 그리고 `docs/`를 읽어라.

이 저장소는 QueueMate의 **`app:matching` 배포 단위 하나**다.
전체 시스템 규칙은 queueMate 본 저장소의 `CLAUDE.md`에 있고, 이 파일은 그중
**매칭 엔진에 걸리는 부분만** 옮긴 것이다. 프런트엔드 / 소셜 / 파티 REST /
예약 REST / 인증 규칙은 여기 없다 — 그건 이 저장소의 책임이 아니다.

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다.

반드시 지킨다:
- 지원 게임은 **LoL, VALORANT, PUBG 셋뿐**이다. 넷째 게임을 추가하지 않는다.
- **상대팀/VS/대전 상대를 만들거나 보여주지 않는다.** proposal 하나는 언제나
  **하나의 party**를 뜻한다. 두 팀을 만들어 붙이는 코드는 이 제품이 아니다.
- 공개 사용자 탐색, 게시판, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다.
- 프리미엄/과금 기능을 구현하지 않는다.
- 차단 관계의 사용자는 **어떤 매칭에서도 같은 파티가 될 수 없다** (INV-6).

## 2. 사용자 매칭 조건 — 게임당 정확히 4개

조건을 임의로 추가하지 않는다. 조건 하나가 늘 때마다 후보 풀이 곱셈으로 쪼개진다.

| | LoL | VALORANT | PUBG |
|---|---|---|---|
| 1 | 게임 모드 | 게임 모드 | 게임 모드 |
| 2 | 희망 포지션 | 선호 역할군 | 플레이 스타일 |
| 3 | 음성 사용 | 음성 사용 | 음성 사용 |
| 4 | 플레이 목적 | 플레이 목적 | 플레이 목적 |

2번 줄이 게임마다 이름만 다른 **핵심 조건(keyValue)** 이다. 코드에서는
`KeyConditionType`(`POSITION` / `ROLE` / `PLAY_STYLE`) + `String value`로 통일해 다룬다.

예약 매칭에만 붙는 추가 조건 (이 저장소 범위 밖, `app:platform` + `app:reservation-batch`):
- 플레이 가능한 시간: 30분 단위 start/end
- 플레이할 양: `ONE_GAME` / `TWO_PLUS`

**새 조건은 `docs/12_ELBOW_CONDITION_SELECTION.md` 절차를 거치기 전에는 추가 금지.**

### 조건 enum의 현재 값 (코드가 원본)

| 개념 | 코드 | 값 |
|---|---|---|
| 게임 | `domain/GameKey.java` | `LOL, VALORANT, PUBG` |
| 조건 타입 | `domain/KeyConditionType.java` | `POSITION, ROLE, PLAY_STYLE` |
| 음성 | `domain/VoicePreference.java` | **`REQUIRED, NO_VOICE`** — `OPTIONAL`은 **제거됐다** |
| 목적 | `domain/PlayPurpose.java` | `RANK_UP, NORMAL, FUN` |
| LoL 포지션 | `domain/lol/LolPosition.java` | `TOP, JUNGLE, MID, ADC, SUPPORT, NONE` |
| LoL 티어 | **자바에 없다.** Redis ZSET `qm:gameconfig:LOL:tier` (`seed/gameconfig.redis`) | `UNRANKED`(score 0), `IRON_4` … `CHALLENGER`(score 31) — 단(division)까지 **32개**. **조건이 아니다, 아래 참고** |

> **티어는 다섯 번째 조건이 아니다.** `tier`는 사용자가 고르는 조건이 아니라
> docs/02 §6의 **derived 조건**(연동 계정에서 가져오는 값)이자 같은 문서 §3이
> `rank eligibility = hard`라고 선언한 **자격 조건**이다. 랭크 모드는 티어 차이가 크면
> 게임에서 큐를 같이 돌 수 없으므로, 티어를 안 보면 애초에 같이 들어갈 수 없는 파티가
> 만들어진다. 그래서 조건 4개 규칙을 어기는 것이 아니고 docs/12 절차 대상도 아니다.
> 무엇을 볼지는 gameconfig의 `tierRule`(**`NONE` / `EXIST` 둘뿐이다**)이 정한다
> (`docs/GAME_CONFIG.md`, `seed/gameconfig.redis`). 지금은 사용자 자기신고이고,
> 라이엇 계정 연동이 붙으면 `userId`와 함께 요청 바디에서 사라진다.

> **티어 값의 원본은 자바가 아니라 Redis다.** `domain/lol/LolTier.java` enum은 **삭제됐고**
> `grep -rn LolTier backend/src`는 0건이다 — 자바에 티어 이름을 아는 코드가 한 줄도 없다.
> 사다리는 ZSET `qm:gameconfig:LOL:tier`이고 score가 단계 번호다(`0 UNRANKED`, `1 IRON_4`
> … `31 CHALLENGER`). 뺀 이유는 gameconfig를 데이터로 뺀 것과 같다 — 단(division)을
> 넣거나 라이엇이 티어를 추가할 때마다 재배포하지 않기 위해서다. 실제로 그 일이 한 번
> 일어났다: `GOLD` 하나가 `GOLD_4`~`GOLD_1`로 쪼개졌다(라이엇 실제 규칙이 **단 단위**라
> 티어로 뭉개면 표현이 안 된다). 단이 있는 티어는 `IRON`~`DIAMOND` 7개이고 **숫자가 클수록
> 낮다**(골드4 → 골드1 → 플래티넘4). `UNRANKED` / `MASTER` / `GRANDMASTER` / `CHALLENGER`는
> 단이 없다. 값을 고치려면 자바가 아니라 `seed/gameconfig.redis`를 고쳐라.

> `VoicePreference.OPTIONAL` 제거는 되돌리지 마라. 이유는 `VoicePreference.java`의
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
- **진행 중인 실시간 매칭 상태의 source of truth는 Redis다.**
  매칭 요청 / 아직 안 찬 파티 / 진행 중 proposal / 수락 집계 — 전부 Redis에만 둔다.
  - **`match_requests` 테이블을 만들지 않는다** (docs/11 #27).
  - PostgreSQL은 **확정된 것**만 안다. 시도했다 실패한 요청은 DB를 치지 않는다.
  - DB 의존성은 **차단 조회 하나 때문에만** 있다 (JPA + H2/PostgreSQL 드라이버).
    Flyway는 아직 없고 스키마도 없다. 매칭 상태를 DB로 옮기는 용도로 쓰지 마라.
- `app:matching`이 DB를 치는 유일한 지점은 INV-6 검증의 `social.blocks` 동기 SELECT
  하나다 (아직 미구현). 스키마는 앱별로 나누고 크로스 스키마 JOIN을 금지하되,
  **이 테이블 하나만 예외로 `matching` 롤에 SELECT 권한을 준다** (docs/11 D-1).
  뷰(`shared_read.blocked_pairs`)를 두는 원안은 폐기했다 — 층을 하나 더 만드는 값보다
  단순함이 크다고 판단했다. 예외는 여기 하나뿐이며 늘리지 않는다.
- **Redis 장애 시 fail-closed 한다.** 중복 매칭을 감수하는 fallback을 만들지 마라 (INV-10).
- Kafka/RabbitMQ 추가 금지. 앱 간 도메인 이벤트는 outbox → **SQS FIFO**다.
  `app:matching`은 `ProposalConfirmed.fifo` 발행 / `BlockChanged.fifo` 소비를 맡는다.
- **사용자 알림을 이 앱이 직접 보내지 않는다.** Redis Pub/Sub에 publish 까지만 하고
  SSE 배달은 `app:realtime`이 한다. 이 저장소에 `SseEmitter`나 WebSocket을 넣지 마라.
  publish 쪽은 **구현돼 있다** — `notification/PushPublisher.java`가 채널
  `qm:pubsub:push:{userId}`에 `{type, eventId, occurredAt, payload}` JSON을 보낸다.
  종류는 `notification/PushEventType.java` 5종이고 그중 **3종만 실제로 발행된다** —
  `MATCH_QUEUE_UPDATED` / `MATCH_PROPOSAL_CREATED`(배정, `rule/lol/*Assigner.java`) ·
  `MATCH_CANCELLED`(취소, `rule/lol/PartyLeaver.java`). 나머지 2종
  (`MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED`)은 확정·만료가 미구현이라 발행 코드가 없다.
  `publish()`는 **어떤 예외도 밖으로 내보내지 않는다** — 알림 실패로 이미 성립한 매칭을
  503으로 뒤집지 않기 위해서다. 그 대가로 발행이 틀려도 조용하니 `PushNotificationTest`를 돌려라.
- 게임 모드 설정(`gameconfig`)은 **Redis에서 읽기만 한다.** 앱이 부팅 시 밀어넣지 않는다.
  밀어넣으면 모드 추가마다 재배포가 필요해져 설정을 데이터로 뺀 의미가 사라진다
  (`seed/gameconfig.redis`, `docs/GAME_CONFIG.md`).
- 현재 배포 기준은 Stage 1(단일 EC2 + Docker Compose)다.
  **k8s/HPA/sticky session을 전제한 구현 금지.**

## 4. 불변식 (INV) — 매칭 해당분과 그것이 지켜지는 자리

깨는 구현은 완료가 아니다. `<repo>` = 이 저장소 루트.

| INV | 내용 | 어디서 지켜지나 | 상태 |
|---|---|---|---|
| **INV-1** | 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다 | `backend/src/main/resources/redis/shared/claim-request.lua` — `EXISTS` + `HSET`을 한 원자 실행으로 묶고 마지막에 `EXPIRE 60`을 건다(선점만 하고 배정 전에 죽으면 그 사용자가 영영 막히는 것을 막는 안전장치). 호출은 `service/MatchRequestService.java#join()`. 실패 시 `controller/MatchingController.java`가 `409 ALREADY_QUEUED`. 배정에 성공한 스크립트가 `PERSIST`로 그 만료를 뗀다 | **구현·테스트됨** |
| **INV-2** | 한 사용자는 동시에 하나의 활성 proposal에만 속한다 | 수락/거절이 붙은 뒤에도 **여전히 "한 사용자는 한 파티에만"으로 근사된다.** 근사가 성립하는 이유는 **proposal이 곧 party**이기 때문이다 — `proposalId = partyId`이고 제안 상태(`status`/`expiresAt`)를 별도 레코드가 아니라 파티 HASH에 얹는다(`service/ProposalService.java` 클래스 주석). 그 한 파티를 지키는 것은 배정 스크립트 4개(`redis/lol/create-or-check-party-untiered.lua` · `create-or-check-party-tiered.lua` · `join-party.lua` · `join-party-tiered.lua`)가 전부 `qm:user:active-request:{userId}` HASH의 `partyId` 필드 **하나만** 쓰는 것이다. 그 필드를 지우는 것은 `redis/lol/leave-party.lua` 하나이고, 거절 시에는 `ProposalService#decline()`이 스크립트 뒤에 `MatchCancelService#cancel()`을 불러 거절한 본인만 큐에서 뺀다(수락해 놓고 기다리던 나머지는 남긴다). **확정돼도 활성 요청·파티는 정리되지 않는다**(`ProposalService#accept()`의 TODO) — 그래서 확정 뒤에도 그 사용자는 그 파티 하나에 묶인 채다 | **부분 (파티 단위 근사)** |
| **INV-3** | 파티 인원은 mode의 target party size를 넘지 않는다 | `redis/lol/join-party.lua` / `join-party-tiered.lua` — 참가자를 `HSET` 한 뒤 `member:` 필드를 **세어** `size >= target`이면 그 파티를 **모든 needs 색인(티어 모드는 파티의 `tierLo`~`tierHi` 칸 전부)에서 제거**한다. 인원 카운터 필드는 두지 않는다 — Lua는 롤백이 없어 페일오버 뒤 재시도가 `HINCRBY`를 두 번 더하면 실제 멤버 수와 어긋나지만, `HSET` + 세기는 몇 번 해도 같기 때문이다. **주의: 세는 자리에는 target 확인 분기가 없다.** 초과를 막는 것은 ① 후보가 needs 색인(=아직 안 찬 파티)에서만 나온다는 것과 ② 후보 선택부터 합류까지가 `redis/PoolLock.java`의 후보 풀 락 안에 있다는 것, 두 겹이다. 그 락을 건너뛰는 호출부를 만들면 INV-3이 깨진다 | **구현·테스트됨** |
| **INV-4** | proposal의 모든 참가자가 accept하기 전에는 party 확정 금지 | `redis/proposal/accept-proposal.lua` — **수락자 SET `qm:proposal:accepts:{partyId}`를 `SCARD`로 세어 파티 HASH의 `target`과 비교하고, `count >= target`일 때만 `HSET status 'CONFIRMED'`** 한 뒤 `CONFIRMED`를 돌려준다. 세기와 확정이 한 스크립트 안이라 마지막 두 명이 동시에 눌러도 둘 다 "내가 마지막"이 될 수 없다. `target`을 못 읽으면 확정하지 않고 수락만 기록한다(fail-closed). 쓰기가 `SADD`/`HSET`뿐이고 `SADD` 반환값으로 early return 하지 않아 **재시도해도 답이 같다**(카운터 대신 집합을 쓰는 이유 — 중간에 죽어도 다음 호출이 다시 세어 확정한다). `decline-proposal.lua`가 `DEL acceptsKey`까지 하는 것도 INV-4를 위해서다 — 옛 수락을 남기면 다시 찬 파티가 한 명만 눌러도 `SCARD`가 `target`에 닿는다. 제안이 열리는 자리는 `rule/lol/UntieredAssigner.java#joinParty()` / `rule/lol/TieredAssigner.java#joinParty()`의 `JOINED_AND_FULL`(Lua 반환 **`2`**) 분기이고, 거기서 `MATCH_PROPOSAL_CREATED` 알림을 파티 전원에게 발행한다. `status='PENDING'` + `expiresAt`을 쓰는 것은 `join-party.lua` / `join-party-tiered.lua`의 `HSETNX`다. **아직 없는 것**: 확정 후속 처리(`ProposalConfirmed.fifo` 발행, `MATCH_CONFIRMED` 알림, 확정 파티의 색인·활성 요청 정리 — `ProposalService#accept()`의 TODO)와 만료 처리(INV-5 참고) | **구현·테스트됨 (확정 후속 처리는 없음)** |
| **INV-5** | expired/declined/cancelled proposal은 다시 confirm될 수 없다 | 네 갈래 중 **둘만 막혀 있다.** ① **declined — 막힘.** `redis/proposal/decline-proposal.lua`는 `status`를 `'DECLINED'`로 **바꾸지 않고 `HDEL status, expiresAt` + `DEL acceptsKey`로 지운다.** 남겨 두면 그 파티가 다시 찼을 때 `join-party*.lua`의 `HSETNX status 'PENDING'`이 0을 돌려주어 아무도 확정시킬 수 없는 **좀비 파티**가 되기 때문이다(그 파일 머리말). 그래서 거절된 제안에 들어온 수락은 `accept-proposal.lua` 1번에서 `NOT_FOUND`로 걸린다 — `status == 'DECLINED'` 분기는 현재 **도달하지 않는 방어 코드**다. ② **confirmed — 되돌릴 수 없음.** 두 스크립트 모두 쓰기 전에 `HGET status`를 먼저 보고, `CONFIRMED`면 수락은 `CONFIRMED`를 그대로, 거절은 `CONFIRMED`(=깨지 못함, 409)를 돌려준다. 페일오버 재실행 대비도 있다 — `join-party*.lua`가 `HSET`이 아니라 `HSETNX`로 `PENDING`을 써서 확정된 제안이 `PENDING`으로 되돌아가지 않는다. ③ **expired — 구멍.** `expiresAt`(= now + `queuemate.proposal.ttl-seconds`, 기본 20초)은 정원이 찰 때 **쓰이기만 하고 읽는 주체가 없다.** sweeper가 없어 `accept-proposal.lua`는 시한을 보지 않으므로 **만료된 제안에 수락이 들어오면 그대로 확정된다.** ④ **cancelled — 구멍.** `redis/lol/leave-party.lua`는 `member:` 필드만 지우고 `status` / `expiresAt` / 수락자 SET을 건드리지 않는다. `PENDING` 제안 도중 한 명이 취소하면 파티는 needs 색인으로 되돌아가지만 `status=PENDING`과 **취소자의 옛 수락이 SET에 남아**, 새로 합류한 사람의 수락으로 `SCARD`가 `target`에 닿아 확정될 수 있다(거절 경로는 해당 없음 — `decline-proposal.lua`가 먼저 지우고 그 뒤에 취소한다). 응답 갈래는 `domain/ProposalResult.java` 한 enum이 맡는다(`AcceptResult`/`DeclineResult`는 없어졌다). `domain/ProposalStatus.java` / `domain/AcceptanceStatus.java`는 **여전히 쓰는 코드가 없다** — 상태는 Redis의 문자열이다 | **부분 (declined·confirmed는 막힘, expired·cancelled는 구멍)** |
| **INV-6** | block 관계 사용자는 같은 proposal/party에 들어갈 수 없다 | **미구현.** 두 겹으로 설계했는데 아랫단만 있다. ① **선필터(코드 있음)** — `rule/lol/LolCandidateRule.java#canJoin()`이 락을 잡기 전에 `block/BlockRepository.java#findBlockedUserIds()`를 실제로 부르고, Lua가 돌려준 후보 파티 멤버 목록을 `rule/lol/LolScriptSupport.java#blockedWith()`로 거른다(상한 `MAX_CANDIDATE_SCAN = 20`, 전부 차단이면 새 파티를 만든다). Redis 선필터(`qm:block:{userId}`)가 아니라 **DB 조회**다 (docs/11 D-2). ② **확정 직전 최종 검증(없음)** — `social.blocks` 동기 SELECT (docs/11 D-1). 확정 단계가 없으므로 이것도 없다. **그리고 ①은 스키마가 없어 실제로는 실패한다** — Flyway가 없고 `application.yaml`이 `ddl-auto: none`이라 기본 실행(H2)에 `social.blocks`가 없다. 배정은 `@Async` 안이라 요청은 201로 나가고 배정만 조용히 실패한다. 테스트만 `ConcurrencyTestSupport`의 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql`로 테이블을 만들어 통과한다. **차단 검증 없이 배포하지 않는다** (docs/11 #30) | **미구현 (선필터 코드만, 스키마 없음)** |
| **INV-7** | 동일 사용자의 PartyMember 중복 금지 | 배정 스크립트 4개가 참가자를 `member:{userId} = keyValue` **HASH 필드**로 쓴다 — 새로 만들 때는 `create-or-check-party-untiered.lua` / `create-or-check-party-tiered.lua`의 `HSET`, 합류할 때는 `join-party.lua` / `join-party-tiered.lua`의 `HSET`. 같은 userId면 필드가 하나뿐이라 구조적으로 중복이 불가능하다. 앞단에서 INV-1이 이미 두 번째 요청을 막는다 | **구현됨** |
| **INV-8** | 게임별 hard rule 위반 파티 생성 금지 | 두 겹이다. ① 값 검증 — `validation/lol/LolConditionValidator.java`가 modeKey 존재 여부, `positionUniqueness`에 맞는 포지션 값, 그리고 `tierRule`(**`NONE` / `EXIST`**)에 맞는 티어 값까지 확인한다. `EXIST`면 `qm:gameconfig:LOL:tier-range:{modeKey}` 표를 읽어 **줄이 없는 티어와 `SOLO_ONLY` 티어를 거른다**(표가 없는 모드는 그 모드 요청이 전부 400이다 — fail-closed다). `WINDOW`/`TABLE`과 `maxTierGap`은 없앴다. 폭으로 거를지 표로 거를지를 설정에 또 적으면 설정이 데이터와 어긋날 수 있었기 때문이다 — `WINDOW`라고 적어 놓고 `maxTierGap`을 빠뜨리면 폭이 0이 되어 자기 티어하고만 매칭되는데 **에러가 안 났다**. 지금은 "표가 있으면 그 표대로"가 전부다 (`seed/gameconfig.redis`). ② 구조적 분리 — 조건이 **Redis 키 이름**에 들어가므로(`qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}`, 티어 모드는 뒤에 `:{tier}`가 더 붙어 (포지션 x 티어) 격자가 된다. 그 접미사는 **Lua가 스스로 붙인다** — 자바는 티어 없는 needs 키만 넘긴다) 조건이 다르면 애초에 같은 색인에 없다. 포지션 중복 금지는 Lua의 `unique` 분기가 처리 | **LoL만 구현·테스트됨** |
| INV-9 | 시간이 겹치는 활성 예약 중복 등록 금지 | **이 저장소 범위 밖.** `app:platform`의 예약 REST가 검증한다 (docs/11 #24) | 해당 없음 |
| **INV-10** | Redis 장애 시 중복 매칭을 감수하는 fallback 금지. 새 매칭을 fail-closed 한다 | `common/error/GlobalExceptionHandler.java#handleRedisFailure()` — `DataAccessException`을 `503 MATCHING_UNAVAILABLE` + `Retry-After: 5`로 바꾼다. 이미 성립한 파티는 건드리지 않고 새 요청만 거절한다 | **구현됨** |

### 불변식 회귀 테스트

| 테스트 | 지키는 것 |
|---|---|
| `backend/src/test/java/.../concurrency/ActiveRequestConcurrencyTest.java` | INV-1 (같은 사용자 동시 100회 → 1건만 성공 / 다른 사용자 100명 → 전원 성공) |
| `backend/src/test/java/.../concurrency/NaiveVsLuaComparisonTest.java` | INV-1 (순진한 `EXISTS`-후-`HSET`은 깨지고 Lua는 중복 0건임을 대조로 보인다) |
| `backend/src/test/java/.../concurrency/PartyJoinConcurrencyTest.java` | INV-3 (정원 초과 없음), INV-8 (같은 포지션 2명 없음), INV-2 근사 (한 사용자 = 한 파티) |
| `backend/src/test/java/.../proposal/ProposalIdempotencyTest.java` | INV-4 (전원 수락 전 확정 없음 / 재시도가 수락자 수를 부풀리지 않음), INV-5 (확정은 거절로 뒤집히지 않음, 거절된 제안은 확정 경로에 못 들어옴, 페일오버 재실행이 `CONFIRMED`를 `PENDING`으로 되돌리지 않음) |

**동시성이 걸린 코드를 고쳤으면 위 표의 `concurrency/*` 3개를 반드시 다시 돌려라.**

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

### Lua 스크립트 목록 (`backend/src/main/resources/redis/`, 8개)

| 파일 | 하는 일 | 반환 코드 |
|---|---|---|
| `shared/claim-request.lua` | 활성 요청 선점 (INV-1). `EXISTS` + `HSET` + `EXPIRE 60` | `1` 선점 / `0` 이미 있음 |
| `lol/create-or-check-party-untiered.lua` | 후보 파티 찾기. 없으면 새로 만들고 들어간다 | `1` 새로 만듦 / `2` 후보 찾음(멤버 목록 반환) / `-1` 설정과 안 맞는 값 / `-2` claim 만료 |
| `lol/create-or-check-party-tiered.lua` | 위의 (포지션 x 티어) 격자판. tier-range 표와 티어 사다리를 **Lua가 직접 읽어** 이 파티가 받아들일 범위를 정하고 `tierLo`/`tierHi`(사다리 순번, `ZRANK + 1`)에 적는다 | 같음. `-1`에 "tier-range 표에 내 티어 줄이 없다 / `SOLO_ONLY`다 / 사다리에 없는 티어다"가 포함된다 |
| `lol/join-party.lua` | 이미 찾아 둔 파티에 합류 | `1` 합류 / **`2` 합류했고 정원이 찼다** / `-1` / `-2` |
| `lol/join-party-tiered.lua` | 위의 격자판. **자기 tier-range를 다시 읽지 않는다** — 파티의 `tierLo`/`tierHi`를 읽어 뺄 칸을 정한다 | 같음 |
| `lol/leave-party.lua` | 취소. 티어 유/무 한 벌로 처리(티어를 안 보는 모드는 티어 이름 자리에 `"NONE"`을 넘겨 접미사를 빈 문자열로 접는다) | `1` 취소(파티 남음) / `2` 취소(파티 없음) / `0` 활성 요청 없음 / `-1` requestId 불일치 |
| `proposal/accept-proposal.lua` | 제안 수락 집계. 전원이 차면 확정까지 (INV-4) | 문자열. `ACCEPTED` / `CONFIRMED` / `NOT_FOUND` / `NOT_A_MEMBER` / `DECLINED` |
| `proposal/decline-proposal.lua` | 제안 거절. 파티 HASH의 제안 흔적(`status`/`expiresAt`)과 수락자 집합을 지운다 | 문자열. `DECLINED` / `ALREADY_RESPONDED` / `CONFIRMED` / `NOT_FOUND` / `NOT_A_MEMBER` |

**티어 격자를 KEYS로 통째로 넘기지 않는다.** 예전에는 자바가 (포지션 x 티어) 격자를 평평하게
펴서 KEYS로 전부 넘기고 Lua가 `KEYS[2 + (p-1)*T + t]`로 칸을 찾았다. 단(division)이 들어가
칸이 (포지션 6 x 티어 32) = **192개**가 되면서 그 방식을 버렸다. 지금은 **티어 접미사가 없는
needs 키**를 넘기고 Lua가 `':' .. 티어이름`을 붙여 조립한다 — KEYS 개수가 `4 + 포지션 개수`로
고정된다 (`leave-party.lua`는 `3 + keyValue 개수`).

**`tierRule` 해석은 자바가 아니라 Lua에 있다.** `TieredAssigner`가 표를 읽어 `[최저, 최고]`로
환산해 넘기던 방식은 없어졌다. Lua가 tier-range 표(`KEYS[3]`)와 티어 사다리(`KEYS[4]`)를 직접
읽는다. 그래서 **티어 사다리 중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi`가
엉뚱한 칸을 가리킨다** — 사다리는 큐가 비어 있을 때 바꿔라.

**찾기와 합류가 두 스크립트로 나뉘어 있다.** 그 사이에 차단 검증(자바)이 끼기 때문이다.
두 호출 사이의 틈은 Lua가 아니라 `redis/PoolLock.java`의 후보 풀 락(Redisson `RLock`,
키는 `qm:lock:pool:` + `LolPartyKeys#poolKey()` = needs 키에서 keyValue만 뺀 조합)이 막는다.
락 키에 keyValue를 넣지 마라 — 이유는 그 클래스 주석에 있다. Redisson은 `qm:lock:*`만
만지고 데이터는 계속 `StringRedisTemplate` + Lua가 다룬다 (`config/RedissonConfig.java`).

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
├── proposal/                  수락·거절 2개. 게임을 보지 않는다 —
│                              제안은 파티 HASH 위에서만 돌아가고 조건을 읽지 않는다
├── pubg/                      (아직 없음)
└── valorant/                  (아직 없음)
```

**한 게임의 수정이 다른 게임 스크립트에 닿지 않게 한다.** 게임을 추가할 때 기존 게임
스크립트를 고쳐 쓰지 말고 그 게임 디렉터리에 자기 것을 둔다.

`claim-request.lua`만 `shared/`에 남는다. 게임을 구분하지 않을 뿐 아니라, 나누면
배그를 하다 롤 큐를 또 잡을 수 있게 되어 **INV-1이 오히려 깨진다.**

**나눈 대가가 있다. 게임마다 불변식 동시성 테스트가 있어야 한다.** 스크립트가 한 벌일
때는 한 벌의 테스트가 전부를 지켰지만, 나뉜 뒤로는 테스트 없는 게임 스크립트가 **아무도
실행하지 않는 코드**가 된다. 그러면 잘못된 수정이 그 게임에서만 조용히 깨진 채 배포된다 —
나눠서 막으려던 일이 그렇게 일어난다. 현재 테스트 24건(동시성 7 + 알림 6 + 제안 멱등성 11)은
**전부 LoL 경로만** 탄다.

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
- 예약 REST(`/api/v1/reservations`) 서빙 — `app:platform`의 일이다
- 파티를 DB에 만드는 코드 — `app:platform`이 `ProposalConfirmed.fifo`를 소비해서 한다
- `match_requests` 테이블 — docs/11 #27이 금지한다
- Redis 장애 시 우회 매칭 경로 — INV-10이 금지한다
- 매칭 조건 5번째 추가 — docs/12 절차 없이는 금지한다
- 앱 부팅 시 gameconfig를 Redis에 밀어넣기 — 설정은 `seed/gameconfig.redis`가 원본이다
