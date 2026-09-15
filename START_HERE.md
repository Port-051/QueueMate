# START_HERE — 5분 안에 착수하기

이 저장소는 QueueMate의 **매칭 엔진(`app:matching`)** 을 떼어낸 작업 공간이다.
처음 여는 사람이 5분 안에 손을 댈 수 있도록 필요한 것만 순서대로 적었다.

---

## 0. 새 Claude 세션에 붙여넣을 인계 프롬프트

```
너는 지금 QueueMate의 매칭 엔진 저장소에서 작업한다.
경로: /mnt/c/Users/kimye/OneDrive/바탕 화면/matching

먼저 이 순서로 읽어라. 다 읽기 전에는 코드를 고치지 마라.
  1. CLAUDE.md            — 지켜야 할 규칙, INV-1~10이 코드 어디서 지켜지는지
  2. START_HERE.md        — 이 파일. 빌드/테스트 명령, 코드 구조, 현재 진척
  3. contracts/README.md  — 코드와 계약이 어긋난 지점 표
  4. docs/03_MATCHING_ENGINE_SPEC.md, docs/07_REDIS_DESIGN.md — 설계 원문

핵심 사실 6개:
  · 이 저장소는 단일 Gradle 모듈이다. 스프링/Gradle 프로젝트는 backend/ 아래에 있고
    빌드도 거기서 돌린다. 진행 중 매칭 상태의 source of truth는 Redis뿐이고,
    DB를 치는 곳은 차단 조회 하나뿐이다. match_requests 테이블을 만들지 마라.
  · 배정까지 구현된 게임은 LoL 하나다. PUBG 는 조건 validator
    (validation/pubg/PubgConditionValidator.java)와 seed/gameconfig.redis 의 PUBG 섹션만
    커밋돼 있고, 배정 규칙·Lua·테스트는 아직 커밋되지 않았다(사용자 작성 중 — 이어받기 전에 물어라).
    그래서 PUBG 요청은 검증을 통과하면 201 이 나가지만 배정에서 조용히 실패한다.
    VALORANT 는 enum 에만 있어 요청이 오면 400 으로 거절된다.
  · 불변식은 Lua 안에서 지킨다. GET → 판단 → SET 으로 지키지 마라.
    backend/src/main/resources/redis/ 의 lua 8개(커밋 기준)가 그 자리다. 후보 찾기와 합류가
    두 스크립트로 나뉘어 있고, 그 사이 틈은 redis/PoolLock.java 의 후보 풀 락이 막는다.
  · 서버→클라 알림은 Redis Pub/Sub 으로 나간다 (qm:pubsub:push:{userId}).
    다만 "지금 상태가 뭐냐"를 묻는 조회 API가 없다 — GET /match-requests/{id} 는 501이다.
  · 제안 수락/거절과 확정은 구현돼 있다 (POST /api/v1/proposals/{id}/accept|decline →
    service/ProposalService.java → redis/proposal/*.lua). 없는 것은 만료 처리(sweeper)와
    확정 후속 처리(MATCH_CONFIRMED 알림, ProposalConfirmed.fifo, 파티·색인·활성 요청 정리)다.
  · 이 저장소는 git 저장소다. private 원격 github.com/rlaehddus302/queuemate-matching
    (main)에 push 한다. 커밋 규칙은 CLAUDE.md §8. IntelliJ 가 새 파일을 자동으로
    스테이징하므로 커밋은 `git commit -- <파일>` 로 파일을 지정해서 해라.

빌드: cd backend && ./gradlew --offline compileJava compileTestJava
테스트: cd backend && ./gradlew test   (동시성·알림·제안 테스트는 localhost:6379에 Redis가 떠 있어야 한다)

작업 규칙은 CLAUDE.md가 전부다. 특히 §4 불변식 표와 §9 "하지 말 것"을 어기지 마라.
```

---

## 1. 읽는 순서

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | `CLAUDE.md` | 규칙. INV-1~10이 **코드 어느 파일에서** 지켜지는지 표가 있다 |
| 2 | **이 파일** | 빌드·테스트·구조·현재 진척 |
| 3 | `contracts/README.md` | 코드 ↔ 계약 불일치 14건. 손대기 전에 봐야 한다 |
| 4 | `docs/03_MATCHING_ENGINE_SPEC.md` | 매칭 엔진 설계 원문 |
| 5 | `docs/07_REDIS_DESIGN.md` | Redis 키 설계 원문 |
| 6 | `docs/CONCURRENCY_TESTS.md` | 동시성 테스트가 무엇을 왜 증명하는가 (사용자 원본) |
| 7 | `docs/GAME_CONFIG.md` | 모드 설정을 Redis에 심는 법 (사용자 원본) |
| 8 | `docs/11_DECISION_LOG.md` | 왜 이렇게 됐는가. #30~#35가 현재 구조를 만든 결정 |
| 9 | `docs/14_ARCHITECTURE_RATIONALE.md` | 결정의 근거 설명 (매칭 발췌본) |
| 10 | `docs/AWS_ARCHITECTURE.md` | 이 서버가 전체 그림 어디에 붙는가 |
| 참고 | `docs/00`, `02`, `04`, `12` | 제품 스펙 / 조건 스키마 / 예약 / 조건 추가 절차 (queueMate 원문 사본) |
| 참고 | `docs/PERFORMANCE_EVIDENCE.md` | `load-test/` 산출물에서 뽑은 수치 색인 |

> `docs/PERFORMANCE.md`는 사용자가 직접 쓴 원본이며 **복원 대상이 아니다.**
> 지금 없다면 IntelliJ Local History로 복원할 것이다. **새로 지어내지 마라.**

---

## 2. 빌드 · 테스트 (실제로 실행해서 확인한 것)

환경: WSL2 / Java **Corretto 21.0.12** / Gradle wrapper **9.7.1** / Spring Boot **4.1.1**

> **스프링/Gradle 프로젝트는 `backend/` 아래에 있다.** 루트에는 `gradlew` 가 없다.
> 이 절의 Gradle 명령은 전부 `backend/` 에서 돌린다. `seed/` · `load-test/` ·
> `redis-ha-lab/` 은 그대로 루트에 있으므로 그쪽 명령은 루트에서 돌린다.

### 확인됨 ✅

```bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching/backend"

# 컴파일 (main + test).  → BUILD SUCCESSFUL
./gradlew --offline compileJava compileTestJava

# 컨텍스트 로딩 테스트. Redis 없이도 통과한다. → BUILD SUCCESSFUL (약 1분)
./gradlew --offline test --tests '*MatchingApplicationTests*'
```

`--offline`은 이 환경에 Gradle 캐시(`~/.gradle/caches/9.7.1`)가 이미 있어서 쓴 것이다.
캐시가 없는 환경이면 빼고 돌려라.

### Redis가 필요한 것 (이 환경에서는 실행 못 함) ⚠️

동시성 테스트 3종(7건), `PushNotificationTest`(6건), `ProposalIdempotencyTest`(11건,
`backend/src/test/java/com/queuemate/matching/proposal/ProposalIdempotencyTest.java`)는
`localhost:6379`의 Redis **DB 15번**을 쓰고 매 테스트마다 `FLUSHDB` 한다
(`ConcurrencyTestSupport`. 알림·제안 테스트도 그것을 상속한다).
이 문서를 처음 쓴 환경에는 `docker`도 `redis-cli`도 없어 실행을 확인하지 못했다.
2026-09-15 에는 소스에서 빌드한 Redis(포트 6390, `REDIS_HOST`/`REDIS_PORT` 로 붙임)로
이 24건 통과를 확인했다 — 환경 함정은 `HANDOFF.md` §5.
명령 자체는 `docs/CONCURRENCY_TESTS.md`(사용자 원본)에 적힌 것과 동일하다.

```bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching/backend"

# 전체
./gradlew test

# 동시성 테스트만
./gradlew test --tests 'com.queuemate.matching.concurrency.*'

# 순진한 방식 vs Lua 비교만 (라운드별 숫자가 표준출력에 찍힌다)
./gradlew test --tests '*NaiveVsLuaComparisonTest' --info

# 알림 발행만 (실제로 qm:pubsub:push:* 를 구독해서 확인한다)
./gradlew test --tests '*PushNotificationTest'

# 제안 수락/거절 멱등성만 (INV-4/5. 단일 스레드)
./gradlew test --tests '*ProposalIdempotencyTest'
```

Redis를 띄우는 법 — 이 저장소에는 `docker-compose.yml`이 **없다.**
queueMate 본 저장소의 것을 쓴다 (`redis:7-alpine`, 컨테이너명 `qm-redis`, 포트 6379).

```bash
# queueMate 저장소에서
docker compose up -d redis

# 모드 설정 시드 (matching 저장소 **루트**에서 — seed/ 는 backend/ 밖이다)
docker exec -i qm-redis redis-cli < seed/gameconfig.redis

# 확인 — 티어 사다리가 LoL 32 / PUBG 27 이어야 한다
docker exec qm-redis redis-cli ZCARD qm:gameconfig:LOL:tier
docker exec qm-redis redis-cli ZCARD qm:gameconfig:PUBG:tier
```

### 앱 실행

```bash
cd backend                 # 스프링 프로젝트는 여기 있다
./gradlew bootRun          # 기본 8080. REDIS_HOST/REDIS_PORT/SERVER_PORT 환경변수로 바꾼다
curl localhost:8080/actuator/health
```

### 스모크 호출

```bash
# 매칭 요청 — 티어를 보는 모드 (RANKED_SOLO 는 tierRule=EXIST 라 tier 가 필수다)
# tier 는 단(division)까지 적는다. "GOLD" 는 이제 400이다 — 사다리에 없는 이름이다
curl -sS -X POST localhost:8080/api/v1/match-requests \
  -H 'Content-Type: application/json' -d '{
    "userId":"u1", "game":"LOL", "modeKey":"RANKED_SOLO", "tier":"GOLD_2",
    "keyCondition":{"type":"POSITION","value":"TOP"},
    "voicePreference":"REQUIRED", "playPurpose":"RANK_UP"
  }'
# → 201 {"requestId":"...","status":"QUEUED"}   같은 userId로 또 하면 409 ALREADY_QUEUED

# 티어를 안 보는 모드 (tierRule=NONE). tier 를 빼도 된다
curl -sS -X POST localhost:8080/api/v1/match-requests \
  -H 'Content-Type: application/json' -d '{
    "userId":"u2", "game":"LOL", "modeKey":"ARAM_5",
    "keyCondition":{"type":"POSITION","value":"NONE"},
    "voicePreference":"REQUIRED", "playPurpose":"FUN"
  }'

# 취소 (userId는 JWT 도입 전 임시 파라미터다)
curl -sS -X DELETE "localhost:8080/api/v1/match-requests/<requestId>?userId=u1" -i
# → 204

# 상태 조회는 아직 껍데기다
curl -sS "localhost:8080/api/v1/match-requests/<requestId>?userId=u1" -i
# → 501 NOT_IMPLEMENTED

# 알림을 눈으로 보려면 배정 전에 구독해 둔다
docker exec -it qm-redis redis-cli PSUBSCRIBE 'qm:pubsub:push:*'
```

**주의 세 가지**
- `voicePreference`에 `OPTIONAL`을 넣으면 400이다. enum에서 제거된 값이다.
- `tierRule` 이 `NONE` 이 아닌(= `EXIST` 인) 모드에 `tier` 를 빼면 400이다. 값은 티어 사다리
  (`qm:gameconfig:LOL:tier`)에 있는 이름이어야 하고, 그 모드의 `tier-range` 표에 줄이 없거나
  `SOLO_ONLY` 여도 400이다 (`RANKED_SOLO` 의 `UNRANKED`/`MASTER` 이상이 그렇다).
  반대로 `positionUniqueness` 가 `false` 인 모드(칼바람)는 포지션이 **반드시 `NONE`** 이어야 한다.
- **`social.blocks` 스키마가 없으면 배정이 조용히 실패한다.** `LolCandidateRule#canJoin`
  이 차단 목록을 DB에서 읽는데 기본 실행은 H2 + `ddl-auto: none` 이라 그 테이블이 없다.
  요청은 201로 나가지만 파티가 생기지 않고 `matchingExecutor` 스레드에 예외 로그만 남는다
  (§4.2 INV-6 줄 참고).

---

## 3. 코드 구조와 진입점

경로는 **저장소 루트 기준**이다. 스프링 프로젝트는 `backend/` 아래에 있고,
`seed/` · `load-test/` · `docs/` · `contracts/` · `redis-ha-lab/` 은 루트에 그대로 있다.

```
backend/src/main/java/com/queuemate/
├── MatchingApplication.java              @SpringBootApplication
├── common/error/
│   ├── ErrorResponse.java                {code, message, details}
│   └── GlobalExceptionHandler.java       ★ INV-10 fail-closed (503)이 여기
└── matching/
    ├── config/
    │   ├── AsyncConfig.java              matchingExecutor (core4/max8/queue200, CallerRunsPolicy)
    │   ├── RedisConfig.java              공통 Lua 3개(claim·accept·decline) 빈 + readScript()
    │   ├── lol/LolRedisConfig.java       LoL Lua 5개 빈. 빈 이름에 lol 접두사 (lolJoinPartyTieredScript 등)
    │   └── RedissonConfig.java           분산 락 전용 클라이언트. 단일 노드 / Sentinel 두 갈래
    ├── controller/
    │   ├── MatchingController.java       ★ 진입점. POST/GET/DELETE /api/v1/match-requests
    │   │                                   GET 은 아직 501 NOT_IMPLEMENTED 다
    │   └── ProposalController.java       POST /api/v1/proposals/{id}/accept|decline → 204
    │                                       (거부 갈래는 404 / 403 / 409). {id} = partyId
    ├── dto/                              CreateMatchRequestCommand / MatchRequestResponse
    ├── domain/                           enum 모음 + ActiveRequest / CancelResult
    │   │                                 + ProposalResult (수락·거절이 같이 쓰는 응답 갈래 한 enum)
    │   │                                 (ProposalStatus / AcceptanceStatus 는 삭제됐다)
    │   ├── lol/                          LolPosition.java (티어 enum 은 없다 — 사다리는 Redis 다)
    │   └── pubg/ · valorant/             .gitkeep 만 있다
    ├── block/                            Block.java / BlockRepository.java (social.blocks 읽기 전용)
    ├── notification/
    │   ├── PushEventType.java            MATCH_* 5종. 실제 발행되는 것은 3종
    │   └── PushPublisher.java            ★ qm:pubsub:push:{userId} 로 publish. 예외를 안 던진다
    ├── redis/PoolLock.java               ★ 후보 풀 분산 락 (Redisson). 배정 전체를 감싼다
    ├── failover/                         [실험용] Redis 페일오버 재시도. 기본 꺼짐
    │                                       (application.yaml 의 queuemate.failover 블록)
    ├── service/
    │   ├── MatchRequestService.java      join() — INV-1 선점 (claim-request.lua)
    │   ├── MatchTrigger.java             @Async — 톰캣 스레드를 놓고 파티 배정으로 넘긴다
    │   ├── MatchCancelService.java       Redis에 저장된 활성 요청을 읽어 게임별 규칙에 위임
    │   └── ProposalService.java          제안 수락/거절 (accept/decline-proposal.lua). 거절이면
    │                                       거절한 본인만 MatchCancelService 로 큐에서 뺀다.
    │                                       ★ 확정 후속 처리는 accept() 의 TODO 로 비어 있다
    ├── rule/
    │   ├── CandidateRule.java            게임별 파티 배정 규칙 인터페이스
    │   └── lol/
    │       ├── LolCandidateRule.java     ★ 설정 읽기 + 차단 목록 조회 + 락 잡고 assigner 호출
    │       ├── LolUntieredAssigner.java  ★ 티어 안 보는 배정. JOINED_AND_FULL 분기가 제안 트리거
    │       ├── LolTieredAssigner.java    ★ (포지션 x 티어) 격자 배정. tierRule 해석은 Lua 가 한다
    │       │                               (자바는 티어 접미사 없는 needs 키만 넘긴다)
    │       ├── LolPartyLeaver.java       취소. MATCH_CANCELLED 알림도 여기서 발행
    │       ├── LolPartyKeys.java         Redis 키 조립을 한 자리에 모은 것
    │       ├── LolScriptSupport.java     반환 코드 읽기 / 멤버 추출 / 차단 판정 / 스캔 상한 20
    │       └── LolModeConfig.java        gameconfig 에서 읽은 모드 설정 record
    │                                     (rule/pubg/ 는 커밋돼 있지 않다 — 사용자 작성 중)
    └── validation/
        ├── MatchConditionValidator.java  게임별 validator로 라우팅
        ├── GameConditionValidator.java
        ├── lol/LolConditionValidator.java    포지션 + 티어(tierRule) 검증
        └── pubg/PubgConditionValidator.java  플랫폼(STEAM/KAKAO) + 모드 + 티어(tierRule) 검증

backend/src/main/resources/redis/         ★ 불변식이 실제로 지켜지는 곳 (커밋된 것 8개.
                                            redis/pubg/ 스크립트는 작성 중이라 세지 않는다)
├── shared/claim-request.lua            INV-1     EXISTS + HSET + EXPIRE 60
├── lol/create-or-check-party-untiered.lua        후보 찾기, 없으면 만들고 들어감 (티어 안 봄)
├── lol/create-or-check-party-tiered.lua          위의 (포지션 x 티어) 격자판. tier-range 표와
│                                                 티어 사다리를 Lua 가 직접 읽어 tierLo/tierHi 를 정한다
├── lol/join-party.lua                  INV-3/7   찾아 둔 파티에 합류. 반환 2 = 정원 참
├── lol/join-party-tiered.lua           INV-3/7   위의 격자판. 티어 범위를 다시 계산하지 않는다
├── lol/leave-party.lua                           취소. compare-and-delete + 색인 되돌리기
│                                                 (티어를 안 보는 모드는 티어 이름에 "NONE" 을 넘겨
│                                                  접미사를 빈 문자열로 접는다)
├── proposal/accept-proposal.lua        INV-4     수락 집계. 전원이 차면 확정까지
└── proposal/decline-proposal.lua       INV-5     거절. 제안 흔적과 수락자 집합을 지운다

backend/src/test/java/com/queuemate/matching/
├── concurrency/
│   ├── ConcurrencyTestSupport.java       DB15 flush + LoL 3모드 시드 + 가상스레드 동시 출발
│   │                                     + H2 ddl-auto=create-drop (social.blocks 때문)
│   ├── ActiveRequestConcurrencyTest.java INV-1
│   ├── PartyJoinConcurrencyTest.java     INV-3 / INV-8 / 한 사용자 한 파티
│   └── NaiveVsLuaComparisonTest.java     순진한 방식이 깨짐을 대조로 증명
├── notification/PushNotificationTest.java  알림 6건. 실제로 구독해서 받아 본다
└── proposal/ProposalIdempotencyTest.java   수락/거절 멱등성 11건

backend/src/test/resources/schema.sql     테스트용 H2 에만 만드는 social.blocks
seed/gameconfig.redis                     모드 설정 원본. LoL(12모드 + 티어 사다리 32 + tier-range 표 4모드)
                                          + PUBG(8모드 + 티어 사다리 27 + tier-range 표 4모드).
                                          티어 값의 원본도 여기다. 앱은 읽기만 한다
load-test/                                k6 + python 부하 테스트 자산
redis-ha-lab/                             [실험용] Sentinel 페일오버 실습 자산
```

### 요청 흐름 한 번 훑기

```
POST /api/v1/match-requests
 └ MatchingController
    ├ MatchConditionValidator.validate()      게임별 라우팅 → LolConditionValidator
    │    · 모드가 Redis에 있나 · keyCondition.type이 POSITION인가
    │    · positionUniqueness에 맞는 포지션 값인가
    │    · tierRule(NONE/EXIST)에 맞는 티어 값인가   → 아니면 400
    │      (EXIST 면 tier-range 표를 읽어 줄이 없는 티어와 SOLO_ONLY 를 거른다)
    ├ MatchRequestService.join()              claim-request.lua (INV-1)  → 이미 있으면 409
    │                                         선점 키에 EXPIRE 60 이 걸린다
    ├ MatchTrigger.trigger()   @Async         톰캣 스레드를 놓아준다
    │    └ LolCandidateRule.canJoin()
    │         · qm:gameconfig:LOL:{mode} 에서
    │           targetPartySize / positionUniqueness / tierRule 읽기
    │           (maxTierGap 은 없어졌다. 허용 범위는 tier-range 표가 전부다)
    │         · BlockRepository.findBlockedUserIds()   ← 락 밖에서 DB 한 번 (INV-6 선필터)
    │         · PoolLock.run(poolKey) 안에서 tier 유무로 assigner 선택
    │              LolUntiered/LolTieredAssigner.assign()   최대 20회 후보를 훑는다
    │                                                   (LolScriptSupport.MAX_CANDIDATE_SCAN)
    │              ├ create-or-check-party-*.lua → {코드, ...}
    │              │    1=새로 만들고 들어감(→ MATCH_QUEUE_UPDATED, 여기서 끝)
    │              │    2=후보를 찾음(멤버 목록 반환) -1=안 맞는 값 -2=claim 만료
    │              ├ 멤버 중 차단 상대가 있으면 → 다음 후보 (전부 차단이면 새 파티)
    │              └ join-party*.lua → {코드, partyId, 인원}
    │                   1=합류(→ MATCH_QUEUE_UPDATED)
    │                   2=합류 후 정원 참(→ MATCH_PROPOSAL_CREATED, payload 에 partyId)
    │                     이때 파티 HASH 에 status=PENDING + expiresAt 을 HSETNX 로 쓴다
    └ 201 {requestId, status:QUEUED}          (배정 결과를 기다리지 않는다)

POST /api/v1/proposals/{partyId}/accept?userId=
 └ ProposalController → ProposalService.accept()
    └ accept-proposal.lua   수락자 SET(qm:proposal:accepts:{partyId})에 SADD → SCARD 가
                            target 에 닿으면 status=CONFIRMED (INV-4) → 204
                            ★ 확정 뒤(MATCH_CONFIRMED 알림 / ProposalConfirmed.fifo /
                              파티·색인·활성 요청 정리)는 아직 없다 (accept() TODO)

POST /api/v1/proposals/{partyId}/decline?userId=&requestId=
 └ ProposalController → ProposalService.decline()
    ├ decline-proposal.lua  status/expiresAt HDEL + 수락자 SET DEL (파티와 참가자는 남긴다)
    └ DECLINED 면 MatchCancelService.cancel() — 거절한 본인만 큐에서 뺀다 → 204
```

배정에 성공한 스크립트는 `PERSIST` 로 claim 의 만료를 뗀다. 그래서 배정 전에 앱이 죽으면
60초 뒤 선점이 저절로 풀리고, 배정에 성공했으면 영구히 남는다.

### Redis 키

| 키 | 타입 | 뜻 |
|---|---|---|
| `qm:gameconfig:LOL:{modeKey}` | HASH | `targetPartySize`, `positionUniqueness`, `tierRule`(`NONE` / `EXIST`). 시드가 원본. `maxTierGap` 은 **없앴다** — 시드가 `HDEL` 로 걷어낸다 |
| `qm:gameconfig:LOL:tier` | ZSET | **티어 사다리. 티어 값의 원본이다** (자바 enum 은 없다). score = 단계 번호 `0 UNRANKED`, `1 IRON_4` … `31 CHALLENGER` (32개). Lua 가 `ZRANK` 로 순번을 뽑고 `ZRANGE` 로 칸 목록을 만든다. **중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi` 가 엉뚱한 칸을 가리킨다** — 큐가 비어 있을 때 바꿔라 |
| `qm:gameconfig:LOL:tier-range:{modeKey}` | HASH | `tierRule` 이 `NONE` 이 **아닌** 모드가 갖는다(지금은 `RANKED_SOLO` + `RANKED_FLEX_2/3/5` 넷). `GOLD_4 → SILVER_4:PLATINUM_1` 처럼 **단 단위** 허용 범위. `SOLO_ONLY` 면 그 티어는 파티를 못 만든다. 줄이 없으면 그 티어는 400이다(fail-closed) |
| `qm:user:active-request:{userId}` | HASH | 활성 요청. `requestId/game/modeKey/voicePreference/playPurpose/keyValue/tier/partyId`. 이 키의 존재 자체가 INV-1 선점이다 (`claim-request.lua`). 배정 전까지는 TTL 60초 |
| `qm:party:{partyId}` | HASH | `partyId/target/createdAt/tierLo/tierHi` + `member:{userId} = keyValue` (+ 정원이 차면 `status`/`expiresAt`). 인원 수 필드는 없다 — `member:` 를 센다. `tierLo/tierHi` 는 사다리 **순번**(`ZRANK + 1`)이고 티어를 안 보는 모드도 `1/1` 로 같은 모양을 갖는다 |
| `qm:proposal:accepts:{partyId}` | SET | 제안 수락자 userId. `accept-proposal.lua` 가 `SADD` 후 `SCARD` 로 세어 `target` 과 비교한다. 거절하면 `decline-proposal.lua` 가 `DEL` 한다. 제안 상태(`status`/`expiresAt`)는 별도 `qm:proposal:{id}` 레코드가 아니라 파티 HASH 에 있다 — **제안 id = partyId** |
| `qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}` | ZSET | **그 값을 아직 못 채운** 파티들. score = createdAt |
| `qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}:{tier}` | ZSET | 위의 티어판. 색인이 (포지션 x 티어) 격자가 된다. `:{tier}` 접미사는 **Lua 가 붙인다** — 자바는 접미사 없는 키만 KEYS 로 넘긴다 |
| `qm:lock:pool:qm:party:open:LOL:{mode}:{voice}:{purpose}` | — | Redisson 후보 풀 락 (`PoolLock`). keyValue 는 **들어가지 않는다** |
| `qm:pubsub:push:{userId}` | 채널 | 사용자 알림. `app:realtime` 이 구독해 SSE 로 배달한다 (`PushPublisher`) |

**핵심 아이디어**: 후보를 검색하지 않는다. 조건을 키 이름에 넣어 색인을 뒤집었으므로,
내 값이 비어 있는 파티만 그 ZSET에 모여 있다. 맨 앞 하나를 꺼내면 끝이다(O(1)).
그래서 claim 재시도 루프가 없다 (docs/11 #33).

**티어가 붙으면서 달라진 것**: 색인이 (포지션 x 티어) 격자가 됐다. 파티가 받아들일 티어
범위는 **만든 사람 기준으로 생성 시 한 번** 정해지고 그 뒤 바뀌지 않는다 — 그 범위를
파티 HASH 의 `tierLo/tierHi` 에 적어 둬야 정원이 찼다가 풀릴 때 어느 칸으로 되돌릴지 알 수 있다.
대가로 서로 직접은 안 받을 두 사람이 같은 파티가 될 수 있다 (의도한 것이다).
`tierRule` 해석은 **Lua 가 한다.** 예전에는 `LolTieredAssigner#tierRange()` 가 표를 읽어
`[최저, 최고]` 로 환산해 숫자만 넘겼는데, 그 메서드가 없어졌다. 지금은 Lua 가 tier-range
표(`KEYS[3]`)와 티어 사다리(`KEYS[4]`)를 직접 읽고 `ZRANK + 1` 을 `tierLo/tierHi` 에 적는다.
`join` 과 `leave` 는 **자기 tier-range 를 다시 읽지 않는다** — 파티의 `tierLo/tierHi` 를
되돌려 칸을 만든다. 내 범위로 빼면 파티가 실제로 올라가 있는 칸과 어긋나 유령 색인이 남는다.

**격자를 KEYS 로 통째로 넘기지 않는다.** 단(division)이 들어가 칸이 (포지션 6 x 티어 32)
= 192 개가 되면서, 자바가 격자를 평평하게 펴서 넘기던 방식(`KEYS[2 + (p-1)*T + t]`)을 버렸다.
지금은 티어 접미사가 없는 needs 키만 넘기고 Lua 가 `':' .. 티어이름` 을 붙인다 —
KEYS 개수가 `4 + 포지션 개수` 로 고정된다. `leave-party.lua` 는 `3 + keyValue 개수` 이고,
`ARGV[5]` 가 **내 티어 이름**이다(티어를 안 보는 모드는 `"NONE"` 을 넘겨 접미사를 빈 문자열로
접는다 — 그래서 사다리에 `NONE` 이라는 티어를 넣으면 안 된다).

---

## 4. 현재 상태 — 사실만

> ⚠️ **이 절은 2026-09-11 에 `backend/` 아래 코드를 직접 읽고 다시 적은 것이다.**
> 그 전 판은 2026-09-06 시점 기록이었고, 그 사이에 티어 매칭 · 후보 풀 락 ·
> 알림 발행 · claim TTL · proposal 껍데기가 들어오면서 상당 부분이 사실과 어긋났다.
> **2026-09-15 에 제안(proposal) 수락·거절·확정 관련 서술만 코드를 읽고 다시 고쳤다** —
> 그 사이 껍데기였던 `ProposalService` 에 수락 집계 Lua 가 붙었다. 같은 날 PUBG 구현 현황(§4.3(a)),
> git 저장소 여부(§4.3(f)), §4.3(e) 의 주석 목록도 코드를 읽고 고쳤다. 그 밖의 서술은 09-11 판 그대로다.
> 이 절도 **금방 낡을 수 있다.** 손대기 전에 §4.4의 검증 명령을 돌려 직접 확인해라.

### 4.1 되는 것 ✅

- `POST /api/v1/match-requests` — 조건 검증(포지션 + 티어) → INV-1 선점 → 비동기 파티 배정
- `DELETE /api/v1/match-requests/{id}?userId=` — compare-and-delete + 색인 되돌리기
- LoL 파티 배정 전체 (찾기/만들기/합류/정원 참 판정/색인 갱신) — **티어 유/무 두 갈래**
- 티어 매칭 — `tierRule` 은 `NONE` / `EXIST` 둘뿐이고, `EXIST` 면 허용 범위를 정하는 것은
  전적으로 `tier-range` 표다. 색인이 (포지션 x 티어) 격자이고, 파티의 허용 범위는 생성 시
  고정된다. 표는 라이엇 실제 규칙을 **단 단위**로 편 것이다 — 솔랭은 다이아가 안 끼면 ±1 티어,
  다이아가 끼면 ±2 단, 마스터 이상은 KR 규정상 `SOLO_ONLY`. 자유 랭크는 규정이 달라
  (`UNRANKED`~`DIAMOND_1`) / (`MASTER`~`CHALLENGER`) **두 덩어리**다
- 후보 풀 분산 락 (`redis/PoolLock.java` + `config/RedissonConfig.java`) — 후보를 훑는
  구간 전체를 한 락 안에 둔다. 락 실패는 503 으로 fail-closed 한다
- claim TTL — `claim-request.lua` 가 `EXPIRE 60`, 배정 스크립트 4개가 `PERSIST`.
  배정 스크립트에는 `EXISTS` 가드(반환 `-2`)가 있어 만료된 선점에는 배정하지 않는다
- 제안 수락 / 거절 / 확정 — `POST /api/v1/proposals/{partyId}/accept|decline`
  (`ProposalController` → `ProposalService` → `redis/proposal/accept-proposal.lua` ·
  `decline-proposal.lua`). 수락자 SET 을 `SCARD` 로 세어 `target` 에 닿을 때만
  `status=CONFIRMED` (INV-4), 확정은 거절로 뒤집히지 않는다 (INV-5 의 confirmed 갈래).
  거절은 제안 흔적(`status`/`expiresAt`/수락자 SET)을 지우고 거절한 본인만 큐에서 뺀다.
  `ProposalIdempotencyTest` 11건이 멱등성으로 지킨다
- 사용자 알림 publish — `qm:pubsub:push:{userId}` 로 `MATCH_QUEUE_UPDATED` /
  `MATCH_PROPOSAL_CREATED` / `MATCH_CANCELLED` 3종. 봉투는 `{type, eventId, occurredAt, payload}`
- INV-1 / INV-3 / INV-7 / INV-8(LoL) — Lua + 후보 풀 락으로 보장되고 동시성 테스트가 지킨다
- INV-10 — Redis 장애 시 503 fail-closed
- gameconfig를 Redis에서 읽기만 하는 구조 (재배포 없이 모드 추가/삭제)

### 4.2 안 되는 것 ❌

| 없는 것 | 근거 (직접 확인한 것) |
|---|---|
| **확정 후속 처리** | 확정 자체는 된다(§4.1). `accept-proposal.lua` 가 `status=CONFIRMED` 만 찍고 끝나며, 그 뒤에 와야 할 것이 `ProposalService#accept()` 의 TODO 로 비어 있다 — `MATCH_CONFIRMED` 알림 발행, `ProposalConfirmed.fifo` 발행, 확정 파티의 색인·활성 요청(·수락자 SET) 정리. 그래서 확정 뒤에도 사용자는 그 파티에 묶인 채다 |
| **만료 sweeper** | `@Scheduled` 가 0건. `queuemate.sweep.interval-ms` 도 읽는 코드가 없다. `expiresAt`(= now + `queuemate.proposal.ttl-seconds`, 기본 20초 — `LolUntieredAssigner`/`LolTieredAssigner` 가 읽어 `join-party*.lua` 에 넘긴다)은 쓰이기만 하고 **읽는 주체가 없어**, 시한이 지난 제안에 수락이 오면 그대로 확정된다 (INV-5 expired 구멍). `MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED` 알림도 발행 코드가 없다 |
| **상태 조회** | `MatchingController#getMatchRequest` 가 **501 `NOT_IMPLEMENTED`** 를 돌려준다. 껍데기와 설계 메모만 있다. 알림은 사건만 전하므로 새로 접속한 클라이언트는 여전히 아무것도 알 수 없다 |
| **INV-6 차단 검증** | 선필터 코드는 **있다** (`LolCandidateRule#canJoin` → `BlockRepository.findBlockedUserIds` → `LolScriptSupport.blockedWith`). 그런데 `social.blocks` 스키마가 없다 — Flyway 미도입, `ddl-auto: none`. 기본 실행(H2)에서는 그 조회가 실패하고, 배정이 `@Async` 안이라 **요청은 201로 나가고 배정만 조용히 실패한다.** 테스트만 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql` 로 통과한다. 확정 직전 최종 검증(docs/11 D-1, `BlockRepository#findBlocksAmong`)은 확정 경로(`accept-proposal.lua`)에 붙어 있지 않아 미구현 |
| **SQS outbox** | AWS SDK 의존성이 `backend/build.gradle` 에 없다 |
| **인증** | JWT 없음. `userId`를 요청 바디와 쿼리 파라미터로 받는 임시 상태 |
| **VALORANT / PUBG 배정** | VALORANT 는 구현체가 없다. PUBG 는 validator 와 시드만 커밋됐고 `CandidateRule` 구현체·Lua·테스트가 커밋돼 있지 않다 (§4.3) |
| **`GET /games`** | 계약에 있으나 컨트롤러가 없다 |
| **Dockerfile** | 없다 |

> `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 은 여전히 `backend/src/` 에 0건이고,
> **그게 맞다** — 이 앱의 몫은 Redis Pub/Sub publish 까지이고 SSE 배달은 `app:realtime` 이 한다
> (CLAUDE.md §3). 넣지 마라.

### 4.3 알아두면 헷갈리지 않을 것

**(a) 지원 게임은 3개인데 배정까지 구현된 것은 LoL 하나다. PUBG 는 검증과 시드까지만 커밋됐다.**

`GameKey` enum은 `LOL, VALORANT, PUBG` 셋이다 — 그건 **제품 경계**이지 구현 현황이 아니다
(docs/11 #8). 커밋된 구현체는 이렇다:

- `GameConditionValidator` 구현체: `LolConditionValidator` / `PubgConditionValidator` **2개**
- `CandidateRule` 구현체: `LolCandidateRule` **1개**
- `seed/gameconfig.redis` 는 LoL 섹션과 PUBG 섹션을 둘 다 갖는다. PUBG 는 모드 8
  (`{NORMAL,RANKED}_{DUO,SQUAD}_{TPP,FPP}`) / 티어 사다리 27 (`qm:gameconfig:PUBG:tier`) /
  랭크 모드 tier-range 표 4. 섹션마다 `seed done: ...` 줄이 하나씩 있다
- `rule/valorant`, `validation/valorant` 는 **빈 디렉터리**이고
  `domain/pubg`, `domain/valorant` 에는 `.gitkeep` 만 있다
- **커밋되지 않은 사용자 작업**: `rule/pubg/` 전체, `resources/redis/pubg/*.lua`,
  `config/pubg/PubgRedisConfig.java`. PUBG 를 검증하는 테스트는 아직 하나도 없다.
  이어받기 전에 사용자에게 물어라 (`HANDOFF.md` §1·§3-A)

그래서 요청 결과가 게임마다 다르다:

- `game: "VALORANT"` → `MatchConditionValidator`의 `orElseThrow` →
  `IllegalArgumentException("지원하지 않는 게임: VALORANT")` → `GlobalExceptionHandler#handleIllegalArgument`
  → **400 `BAD_REQUEST`**. 500으로 터지지는 않지만, 조건이 틀린 것과 게임이 미구현인 것이
  **같은 400 코드로 묶여** 클라이언트가 구분할 수 없다. 게임을 늘릴 때 여기부터 손봐라.
- `game: "PUBG"` → `PubgConditionValidator` 가 검증한다. 통과하면 `claim-request.lua` 가
  활성 요청을 선점하고 **201** 이 나간 뒤, `@Async` 안의 `MatchTrigger` 가 규칙을 못 찾아
  `IllegalArgumentException("파티 배정 규칙이 없는 게임: PUBG")` 으로 끝난다. 그 사용자는
  claim 의 `EXPIRE 60` 동안 다른 매칭을 못 잡는다.

LoL 만으로 시작한 범위 축소는 사고가 아니라 결정이다 — docs/11 **#30**("LoL만 / Tier 0만 / 차단 검증 제외").

> 이력 참고: 한때 PUBG 시드·테스트 헬퍼가 구현 없이 들어갔다가 LoL 전용으로 되돌린 적이 있다.
> 지금은 PUBG 시드와 validator 가 다시 커밋됐고 배정 규칙·테스트는 아직이라, **시드·검증과
> 배정이 다시 어긋난 상태**다. `ConcurrencyTestSupport` 의 시드 헬퍼는 여전히 LoL 전용이다.

**(b) `docs/02`가 말하는 조건 완화(compatibility tier)는 여전히 코드에 없다. 랭크 티어는 이제 있다.**

**둘은 다른 것이다.** 헷갈리기 쉬우니 나눠서 본다.

- **랭크 티어 (있다)** — 티어 사다리 `qm:gameconfig:LOL:tier` (ZSET, 자바 enum 은 없다),
  gameconfig 의 `tierRule`(`NONE`/`EXIST`), `qm:gameconfig:LOL:tier-range:*` 표,
  (포지션 x 티어) 격자 색인,
  `LolTieredAssigner` + `create-or-check-party-tiered.lua` / `join-party-tiered.lua`.
  이건 `docs/02` §3 의 `rank eligibility = hard`(자격 조건)를 채운 것이다.
- **compatibility tier / 단계적 완화 (없다)** — `docs/02` §4, `docs/03` §5 가 말하는
  "시간이 지나면 조건을 느슨하게 푼다"는 그것. 지금 매칭은 여전히 **완전 일치**이고,
  그 일치는 알고리즘이 아니라 **Redis 키 분할**로 이뤄진다 — 조건
  (`game`/`mode`/`voice`/`purpose`/`keyValue`[`/tier`])이 전부 키 이름에 들어가므로
  조건이 다르면 애초에 같은 색인에 존재하지 않는다. aging 도 없다.
  이것도 결정이다 (docs/11 #30 "Tier 0(조건 완전 일치)만", #33 역색인).

즉 `docs/02` 부록 A-2 / A-3 은 **A-3 만 해소됐다.** A-2(완화 없음)는 아직 유효하다.

**(c) 매칭 성사는 알림으로 나간다. 없는 것은 "지금 상태" 조회다.**

정원이 차면 `MATCH_PROPOSAL_CREATED` 가 파티 전원의 `qm:pubsub:push:{userId}` 채널로
나간다 (`LolUntieredAssigner` / `LolTieredAssigner` 의 `JOINED_AND_FULL` 분기). SSE 배달은
`app:realtime` 몫이므로 이 저장소에는 없는 게 맞다.

**남은 구멍은 두 개다.**
1. 알림은 **사건**만 전한다. 새로고침하거나 다른 기기로 접속한 클라이언트는 아무것도
   모른다. 그걸 물어볼 `GET /match-requests/{id}` 가 **501** 이다
   (`MatchingController#getMatchRequest` 의 주석에 왜 필요한지와 함께 적혀 있다).
2. 확정(`status=CONFIRMED`)은 되지만 `MATCH_CONFIRMED` 를 발행하는 코드가 없어
   그 알림은 오지 않는다 (`ProposalService#accept()` TODO). 만료 알림(`MATCH_PROPOSAL_EXPIRED`)도
   sweeper 가 없어 오지 않는다.

`load-test/match_latency.py` 가 성사를 감지하려고 HTTP 가 아니라 **Redis 를 직접 폴링**하는
것은 그 스크립트가 알림 도입 전에 쓰였기 때문이다
(`HGET qm:user:active-request:{uid} partyId` → `HGET qm:party:{pid} size`).
**그 `size` 필드는 이제 없다** — 파티 HASH 는 인원을 `member:` 필드를 세어 구한다. 그래서 이
스크립트는 지금 코드에서는 성사를 감지하지 못한다(코드 확인만 했고 고치지 않았다).

**(d) 코드 주석의 결정 번호가 `docs/11`과 맞지 않는다.**

- `domain/VoicePreference.java`가 OPTIONAL 제거 근거로 `(docs/11 #31)`을 인용하는데,
  `docs/11`의 #31은 "파티 인원이 가변인 모드는 modeKey를 인원별로 쪼갠다"이다.
- `docs/11`의 Fixed decisions는 **#35까지**다.

즉 OPTIONAL 제거를 기록한 결정 항목이 `docs/11`에 **없다.** 결정 로그가 그 시점보다
낡았거나 번호가 어긋난 것이다. 자세한 것은 `docs/11_DECISION_LOG.md` 맨 끝의
"복원 시점 관찰 기록" 절.

**(e) 코드 주석 몇 개가 자기보다 낡았다. 주석을 사실로 믿지 마라.**

- `rule/CandidateRule.java`가 "배관은 `AbstractCandidateRule`에 있다"고 하는데
  그 클래스는 **없다.**
- `redis/PoolLock.java`의 클래스 주석이 "지금은 아직 아무 데서도 쓰이지 않는다"고 하는데
  `LolCandidateRule#canJoin`이 **실제로 쓴다.**
- `create-or-check-party-untiered.lua` · `join-party.lua` 머리의
  "티어를 보는 모드는 `join-or-create-party-tiered.lua`가 담당한다 (아직 없다)"도
  낡았다. 실제 파일명은 `create-or-check-party-tiered.lua` / `join-party-tiered.lua`다.

이 저장소에 `join-or-create-party*.lua` 라는 파일은 **하나도 없다.** 그 이름이 보이면
옛 이름이다 (docs/11 D-6).

(예전에 여기 있던 `LolTieredAssigner` 클래스 주석의 `TABLE`/`WINDOW`·`ARGV[9..12]`·`TierRange`
항목은 주석과 코드가 고쳐져 뺐다. 지금 주석은 `ARGV[9]` = 티어 이름, keyValue 는 `ARGV[10..]` 로 코드와 맞다.)

**(f) 이 저장소는 git 저장소다.** private 원격 `github.com/rlaehddus302/queuemate-matching`
(`main`)에 push 한다. 커밋 규칙은 `CLAUDE.md` §8.
**IntelliJ 가 새 파일을 자동으로 스테이징한다** — 사용자 작업 파일이 문서 커밋에 딸려 올라간
적이 있다. 커밋은 `git commit -- <파일>` 로 파일을 지정하거나, 직전에 `git diff --cached --stat`
으로 스테이징 목록을 확인해라 (`HANDOFF.md` §1).

### 4.4 위 서술을 다시 검증하는 명령

```bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching"

git ls-tree -r --name-only HEAD backend/src/main/resources/redis   # 커밋된 lua 8개 (shared 1 / lol 5 / proposal 2)
                                                      # find 로 세면 작성 중인 redis/pubg/ 까지 잡힌다
grep -rn "LolTier" backend/src/                       # 0건이어야 맞다 — 티어 enum 은 삭제됐다
docker exec qm-redis redis-cli ZRANGE qm:gameconfig:LOL:tier 0 -1 WITHSCORES  # 티어 값의 원본 (32개)
grep -rn "TODO" backend/src/                          # 남은 TODO 지점
grep -rn "UnsupportedOperationException" backend/src/ # 0건이어야 맞다 — proposal 수락/거절은 구현됐다
grep -n "TODO" backend/src/main/java/com/queuemate/matching/service/ProposalService.java  # 확정·거절 후속 처리 빈자리
grep -rn "NOT_IMPLEMENTED" backend/src/               # 501 스텁
grep -rn "PushEventType\." backend/src/main/          # 실제로 발행되는 알림 종류
grep -rn "SseEmitter\|WebSocketConfig\|@MessageMapping" backend/src/ | wc -l   # 0이어야 맞다
grep -rln "implements CandidateRule" backend/src/     # 구현된 게임 목록
grep -rln "implements GameConditionValidator" backend/src/
grep -n "enum VoicePreference" -A2 backend/src/main/java/com/queuemate/matching/domain/VoicePreference.java
grep -c "@Scheduled" -r backend/src/ | grep -v ':0'   # 비면 sweeper 없음
grep "seed done" seed/gameconfig.redis                # 시드가 커버하는 게임/모드 수 (LoL 줄 + PUBG 줄)
find backend/src -type d -empty                       # 빈 게임 패키지
```

---

## 5. 다음에 할 일 (권장 순서)

위쪽 1~4는 `HANDOFF.md` §3(A~C)이 정한 지금 남은 일이고, 그 아래는 `docs/11` #30이 정한
착수 순서의 나머지다. 각 항목은 `CLAUDE.md` §6 Definition of done 6개를 모두 채워야 완료다.

> **끝난 것 (표에서 뺐다).** 옛 1번 "proposal 레코드 만들기"와 2번 "수락 집계 + 확정(INV-4/INV-5)"은
> 구현됐다. 별도 `qm:proposal:{id}` 레코드 대신 정원이 찰 때 `join-party*.lua` 가 파티 HASH 에
> `status=PENDING` + `expiresAt` 을 `HSETNX` 로 쓰고(제안 id = partyId), 수락자는
> `qm:proposal:accepts:{partyId}` SET 에 담는다. 집계·확정은 `accept-proposal.lua`, 거절은
> `decline-proposal.lua`, 응답 갈래는 `domain/ProposalResult.java` 한 enum 이다. 근거는
> `service/ProposalService.java` 클래스 주석, 회귀 테스트는 `ProposalIdempotencyTest`(11건).

| # | 할 일 | 시작 지점 | 왜 이 순서인가 |
|---|---|---|---|
| 1 | **PUBG 배정 규칙 · Lua · 동시성 테스트** (진행 중) | 시드와 `validation/pubg/PubgConditionValidator.java` 는 들어갔다. 남은 것은 `CandidateRule` 구현체(`rule/pubg/`) + `redis/pubg/*.lua` + **PUBG 동시성 테스트**. **사용자가 작성 중이다 — 이어받기 전에 물어라.** 스크립트 빈·필드 이름은 `pubg*` 접두사여야 한다(아니면 LoL 스크립트가 주입된다). 자세한 것은 `HANDOFF.md` §3-A | validator 만 있고 규칙이 없어, PUBG 요청은 claim 을 잡은 뒤 `MatchTrigger` 에서 조용히 실패하고 그 사용자는 60초 동안 막힌다. 스크립트를 게임별로 나눈 대가로 게임마다 테스트가 있어야 한다 (CLAUDE.md §4) |
| 2 | **만료 sweeper** | `@Scheduled` 가 아직 0건이다. `expiresAt` 은 파티 HASH 에 쓰이기만 하고 읽는 주체가 없어, 시한이 지난 제안에 수락이 오면 그대로 확정된다. `queuemate.sweep.interval-ms`(기본 1000) 자리가 이미 있다. 만료 시 `MATCH_PROPOSAL_EXPIRED` 알림 발행이 함께 붙는다 (`PushEventType` 에 값은 있고 발행 코드가 없다) | INV-5 의 expired 구멍이다. 만료 처리 없이는 응답하지 않은 제안이 영원히 남는다 |
| 3 | **확정 후속 처리** | `service/ProposalService.java#accept()` 의 TODO. `CONFIRMED` 뒤에 `MATCH_CONFIRMED` 알림(파티 전원), 확정 파티의 색인·활성 요청·수락자 SET 정리. `ProposalConfirmed.fifo` 발행은 6번(outbox)과 묶인다 | 지금은 확정돼도 사용자가 그 파티와 활성 요청에 묶인 채라 다음 매칭을 못 잡는다 |
| 4 | **INV-6 차단 검증 완성** | 선필터는 이미 돈다 (`LolCandidateRule#canJoin`). 남은 것은 ① **Flyway 도입 + `social.blocks` 스키마** — 지금은 테이블이 없어 기본 실행에서 배정이 통째로 실패한다 ② 확정 직전 동기 SELECT (`BlockRepository#findBlocksAmong`, docs/11 D-1) — 확정 경로(`accept-proposal.lua`)가 생겼으니 그 앞에 붙인다 | docs/11 #30: "차단 검증 없이 배포하지 않는다." **배포 전 필수.** ①은 사실상 버그 수정이다 |
| 5 | **상태 조회 `GET /match-requests/{id}`** | `controller/MatchingController.java#getMatchRequest` — 껍데기가 이미 있고 **501** 을 돌려준다. 그 메서드 주석에 왜 필요한지, `userId` 로 찾는 판이 왜 함께 필요한지, 계약(`MatchRequestView`)과 어디가 다른지가 적혀 있다 | 알림(Pub/Sub)은 **사건**만 전한다. 새로고침한 클라이언트가 "지금 상태"를 물어볼 곳이 없다. 알림은 at-most-once 라 놓친 상태 복구도 이 REST 몫이다 |
| 6 | **확정 트랜잭션 + outbox → SQS** | Flyway 3테이블(`match_proposals`/`proposal_members`/`outbox`) + `ProposalConfirmed.fifo` 발행. AWS SDK 의존성부터 없다 | 파티를 DB에 만드는 것은 `app:platform` 이다 (docs/11 #30) |
| 7 | **VALORANT 규칙** | `rule/valorant`, `validation/valorant`, `domain/valorant` 를 채운다. **시드·테스트 헬퍼·구현체를 같은 커밋에** | 동시성 축과 PUBG 가 끝난 뒤 얹는 확장이다 |
| 8 | **인증(JWT)** | 붙는 순간 `userId` 요청 필드와 `?userId=` 쿼리 파라미터를 제거한다 (`MatchingController` · `ProposalController` 둘 다) | 계약 불일치 #2, #3이 이때 해소된다 |

### 손대기 전 체크리스트

- [ ] `CLAUDE.md` §4 불변식 표에서 내가 건드릴 INV가 어디서 지켜지는지 확인했다
- [ ] 불변식이 걸린 변경이면 **하나의 원자 실행 안**에서 처리했다 (`GET → 판단 → SET` 아님)
- [ ] Lua 안에 후보 순회 루프를 넣지 않았다 (docs/11 #33). 후보를 훑는 루프는 자바에 있고
      **후보 풀 락 안**에 있어야 한다 — 회차마다 락을 놓으면 색인이 바뀌어 후보를 빠뜨린다
- [ ] `cd backend && ./gradlew test --tests 'com.queuemate.matching.concurrency.*'` 를 돌렸다
- [ ] 알림 발행을 건드렸으면 `--tests '*PushNotificationTest'` 도 돌렸다
      (`PushPublisher` 가 예외를 삼키므로 구독해 보는 것 말고는 검증 수단이 없다)
- [ ] 계약이 바뀌었으면 `contracts/README.md` 불일치 표를 갱신했다
- [ ] 제안 Lua(`proposal/*.lua`)나 `join-party*.lua` 의 `HSETNX` 분기를 건드렸으면 `--tests '*ProposalIdempotencyTest'` 도 돌렸다
- [ ] 커밋을 `docs/**`·`contracts/**` 변경과 섞지 않았다 (`CLAUDE.md` §8)
- [ ] `git commit -- <파일>` 로 파일을 지정해 커밋했다 (IntelliJ 자동 스테이징 때문)
