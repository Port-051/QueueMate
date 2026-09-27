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
  · 배정까지 구현된 게임은 LoL · PUBG · VALORANT 셋이다
    (전부 validator + CandidateRule + Lua + 시드). **세 게임 모두 동시성 테스트가 있다** —
    LoL 3종 10건 + VALORANT 8건 + PUBG 9건 = 27건.
  · 불변식은 Lua 안에서 지킨다. GET → 판단 → SET 으로 지키지 마라.
    backend/src/main/resources/redis/ 의 lua 20개가 그 자리다. 후보 찾기와 합류가
    두 스크립트로 나뉘어 있고, 그 사이 틈은 redisLock/PoolLock.java 의 후보 풀 락이 막는다.
  · 서버→클라 알림은 Redis Pub/Sub 으로 나간다 (qm:pubsub:push:{userId}).
    "지금 상태가 뭐냐"를 묻는 조회도 생겼다 — 다만 경로가 계약과 다르다.
    GET /match-requests 이고 경로 변수가 없다 (contracts/README.md #5). "나"는
    쿠키 qm_access 의 access 토큰(sub)이다 — 2026-09-27 까지는 ?userId= 였다.
  · 인증 — 2026-09-27 부터 모든 /api/v1/** 가 쿠키 qm_access(RS256 JWT)를 요구한다.
    app:platform 이 발급하고 이 앱은 공개 키(JWT_PUBLIC_KEY 또는 JWT_PUBLIC_KEY_FILE)로
    검증만 한다. ?userId= 와 바디의 userId 는 없어졌다 (CLAUDE.md §3 "인증" · docs/11 D-24).
  · Redis 키 문자열의 자바 쪽 단일 출처는 redisKeys/SharedKeys.java 다.
    게임별 *PartyKeys 는 그 조각을 조합해 needs 색인을 만든다.
  · 제안 수락/거절/확정/만료가 전부 구현돼 있다 (POST /api/v1/proposals/{id}/accept|decline →
    service/ProposalService.java → redis/proposal/*.lua, 만료는 ProposalSweeper +
    ProposalExpiryService 가 qm:proposal:pending ZSET 을 훑는다). 확정되면
    cleanup-confirmed.lua 가 파티 HASH qm:party:{partyId} 에 game/modeKey/voicePreference/
    playPurpose/confirmedAt 을 채우고(이 필드 이름이 app:platform 과의 계약이다) 파티원 활성 요청에
    status=PARTY 를 찍은 뒤 TTL 을 건다 — 파티 HASH 600초, 활성 요청 · 수락자 SET 60초(docs/11 D-42).
    MATCH_CONFIRMED 가 파티 전원에게 나가고, 파티를 DB 에 만드는 것은 app:platform 이다 —
    프런트가 platform 의 "이 매칭으로 파티 만들기"(경로 미정)를 부르면 그 HASH 를 읽어 만든다.
    outbox · SQS ProposalConfirmed.fifo 는 두지 않는다(D-42 — 넣었다 같은 날 뺐다).
  · 이 저장소는 git 저장소다. private 원격 github.com/rlaehddus302/queuemate-matching
    (matching)에 push 한다. 커밋 규칙은 CLAUDE.md §8. IntelliJ 가 새 파일을 자동으로
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

동시성 테스트 4종(LoL 3 + `ValorantPartyJoinConcurrencyTest`), `PushNotificationTest`(6건), `ProposalIdempotencyTest`(11건,
`backend/src/test/java/com/queuemate/matching/proposal/ProposalIdempotencyTest.java`)는
`localhost:6379`의 Redis **DB 15번**을 쓰고 매 테스트마다 `FLUSHDB` 한다
(`ConcurrencyTestSupport`. 알림·제안 테스트도 그것을 상속한다).
이 문서를 처음 쓴 환경에는 `docker`도 `redis-cli`도 없어 실행을 확인하지 못했다.
2026-09-15 에는 소스에서 빌드한 Redis(포트 6390, `REDIS_HOST`/`REDIS_PORT` 로 붙임)로
당시 24건 통과를 확인했다 — 그 뒤 VALORANT 동시성 테스트와 만료 구현이 들어왔으므로
**그 숫자는 지금 기준이 아니다.** 환경 함정은 `HANDOFF.md` §5.
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

**공개 키가 있어야 뜬다(2026-09-27~).** access 토큰(쿠키 `qm_access`)을 `app:platform` 의 공개 키로 검증하기 때문이다.
`JWT_PUBLIC_KEY`(X.509 PEM)를 주거나, 없으면 `JWT_PUBLIC_KEY_FILE` 의 파일을 읽는다 — 기본값
`../../platform/backend/.dev-keys/public.pem` 은 `backend/` 에서 띄울 때의 `platform` 개발용 키다(`platform` 을 한 번 띄우면 생긴다).
**둘 다 없으면 기동이 실패한다.** 상태를 바꾸는 요청의 `Origin` 허용 목록은 `ALLOWED_ORIGINS`(기본값 `http://localhost:5173,http://localhost:3000`).

토큰은 `platform` 에 소셜 로그인해 받은 쿠키를 쓰거나, 로컬에서는 `platform` 의 개발용 **개인 키**로 직접 찍는다
(개인 키는 `platform` 폴더에만 있다 — 이 앱에 들이지 마라):

```bash
K=../../platform/backend/.dev-keys/private.pem
b64(){ openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"RS256","kid":"dev-1"}' | b64); now=$(date +%s)
P=$(printf '{"iss":"queuemate-platform","sub":"%s","iat":%d,"exp":%d,"jti":"x","token_use":"access"}' 1 $now $((now+900)) | b64)
TOKEN="$H.$P.$(printf '%s.%s' "$H" "$P" | openssl dgst -sha256 -sign "$K" | b64)"   # sub=1 인 사용자
```

### 스모크 호출

```bash
# 매칭 요청 — 티어를 보는 모드 (RANKED_SOLO 는 tierRule=EXIST 라 tier 가 필수다)
# tier 는 단(division)까지 적는다. "GOLD" 는 이제 400이다 — 사다리에 없는 이름이다
curl -sS -X POST localhost:8080/api/v1/match-requests --cookie "qm_access=$TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "game":"LOL", "modeKey":"RANKED_SOLO", "tier":"GOLD_2",
    "keyCondition":{"type":"POSITION","value":"TOP"},
    "voicePreference":"REQUIRED", "playPurpose":"RANK_UP"
  }'
# → 201  {"status":"QUEUED","requestId":"...","queuedAt":1758...}  — 취소(DELETE /{requestId})에 쓸 값이 여기 온다
#    같은 사용자(같은 sub)로 또 하면 409 ALREADY_QUEUED, 게시판 방에 있으면 409 IN_ROOM
#    (TOKEN2 는 위 TOKEN 을 sub=2 로 찍은 것이다. 바디의 userId 는 2026-09-27 에 없어졌다 — 넣어도 무시된다)
#    (한때 문자열 "CREATED" 를 돌려줬다 — 2026-09-27 에 고쳤다, contracts/README.md ~~#5-1~~)

# 티어를 안 보는 모드 (tierRule=NONE). tier 를 빼도 된다
curl -sS -X POST localhost:8080/api/v1/match-requests --cookie "qm_access=$TOKEN2" \
  -H 'Content-Type: application/json' -d '{
    "game":"LOL", "modeKey":"ARAM_5",
    "keyCondition":{"type":"POSITION","value":"NONE"},
    "voicePreference":"REQUIRED", "playPurpose":"FUN"
  }'

# 취소 — 누구의 요청인지는 쿠키의 sub 다 (2026-09-27 까지는 ?userId= 였다)
curl -sS -X DELETE "localhost:8080/api/v1/match-requests/<requestId>" --cookie "qm_access=$TOKEN" -i
# → 204

# 상태 조회 — requestId 가 아니라 "나"(쿠키의 sub)로 찾는다 (경로 변수 없음)
curl -sS "localhost:8080/api/v1/match-requests" --cookie "qm_access=$TOKEN" -i
# 쿠키가 없거나 틀리면 → 401 {"code":"UNAUTHENTICATED",...}
# 허용 목록 밖의 Origin 을 단 POST/DELETE → 403 {"code":"ORIGIN_NOT_ALLOWED",...} (Origin 이 없는 curl 은 통과한다)
# → 200 {"status":"QUEUED","requestId":"...","queuedAt":1758...,"partyId":"...","target":5,"memberCount":2}
#    갈래는 IDLE / QUEUED / PROPOSED / MATCHED 넷. 그 갈래에서 뜻이 없는 칸은 응답에서 빠진다
#    (@JsonInclude(NON_NULL)). 큐에 없으면 404 가 아니라 200 {"status":"IDLE"} 이다

# 알림을 눈으로 보려면 배정 전에 구독해 둔다
docker exec -it qm-redis redis-cli PSUBSCRIBE 'qm:pubsub:push:*'
```

**주의 세 가지**
- `voicePreference`에 `OPTIONAL`을 넣으면 400이다. enum에서 제거된 값이다.
- `tierRule` 이 `NONE` 이 아닌(= `EXIST` 인) 모드에 `tier` 를 빼면 400이다. 값은 티어 사다리
  (`qm:gameconfig:LOL:tier`)에 있는 이름이어야 하고, 그 모드의 `tier-range` 표에 줄이 없거나
  `SOLO_ONLY` 여도 400이다 (`RANKED_SOLO` 의 `UNRANKED`/`MASTER` 이상이 그렇다).
  반대로 `positionUniqueness` 가 `false` 인 모드(칼바람)는 포지션이 **반드시 `NONE`** 이어야 한다.
- **차단 테이블(`blocks` — 옛 이름 `social.blocks`, 2026-09-26 부터 `public.blocks` · docs/11 D-34)이 없으면 배정이 조용히 실패한다.** `LolCandidateRule#canJoin`
  (`Pubg` · `ValorantCandidateRule` 도 같다) 이 차단 목록을 DB에서 읽는다.
  없으면 요청은 201로 나가지만 파티가 생기지 않고 `matchingExecutor` 스레드에 예외 로그만 남는다.
  **기본 실행(H2)은 `backend/src/main/resources/schema.sql` 이 그 테이블을 만든다**(`spring.sql.init.mode: embedded` — Postgres 에는
  만들지 않는다, 그건 platform 의 Flyway 것이다). 운영은 `DB_URL` 이 `app:platform` 의 Postgres 를 가리켜야 하고, `Block.java` 는
  2026-09-26 에 `public.blocks` · `Long` 으로 맞췄다(docs/11 D-25 · D-34 · D-41).

---

## 3. 코드 구조와 진입점

경로는 **저장소 루트 기준**이다. 스프링 프로젝트는 `backend/` 아래에 있고,
`seed/` · `load-test/` · `docs/` · `contracts/` · `redis-ha-lab/` 은 루트에 그대로 있다.

```
backend/src/main/java/com/queuemate/
├── MatchingApplication.java              @SpringBootApplication + @EnableScheduling (만료 스위퍼)
├── common/error/
│   ├── ErrorResponse.java                {code, message, details}
│   └── GlobalExceptionHandler.java       ★ INV-10 fail-closed (503)이 여기
├── common/security/                      ★ 인증(2026-09-27) — 쿠키 qm_access 의 RS256 JWT 를 platform 공개 키로 검증만.
│                                           SecurityConfig · JwtConfig(iss · exp · token_use=access · sub 숫자) · JwtPublicKeys ·
│                                           CookieBearerTokenResolver · @CurrentUserId(= sub, String) · TokenClaims(platform 사본)
├── common/web/                           OriginCheckFilter(POST/PUT/PATCH/DELETE 의 Origin → 403) · ErrorResponseWriter
└── matching/
    ├── config/
    │   ├── AsyncConfig.java              matchingExecutor (core4/max8/queue200, CallerRunsPolicy)
    │   └── redis/                        Redis 설정. 게임 무관은 바로 아래, 게임별은 config/redis/{game}/
    │       ├── RedisConfig.java          공통 Lua 5개 빈 + readScript()
    │       │                             claim · accept · decline · expireProposal · cleanupConfirmed
    │       ├── RedissonConfig.java       분산 락 전용 클라이언트(qm:lock:* 만 만짐). 단일 노드 / Sentinel 두 갈래
    │       ├── lol/LolRedisConfig.java   LoL Lua 5개 빈. 빈 이름에 lol 접두사 (lolJoinPartyTieredScript 등)
    │       ├── pubg/PubgRedisConfig.java PUBG Lua 5개 빈. 빈 이름에 pubg 접두사
    │       └── valorant/ValorantRedisConfig.java  VALORANT Lua 5개 빈. 빈 이름에 valorant 접두사
    ├── controller/
    │   ├── MatchingController.java       ★ 진입점. POST/GET/DELETE /api/v1/match-requests
    │   │                                   "나"는 @CurrentUserId(토큰의 sub) — ?userId= 는 2026-09-27 에 없어졌다.
    │   │                                   계약과 경로가 다르다 — contracts/README.md #5
    │   └── ProposalController.java       POST /api/v1/proposals/{id}/accept|decline → 204
    │                                       (거부 갈래는 404 / 403 / 409). {id} = partyId
    ├── dto/                              CreateMatchRequestCommand / MatchRequestResponse /
    │                                       JoinResult (접수 결과 — ACCEPTED / ALREADY_QUEUED / IN_ROOM)
    │                                       MatchRequestResponse 는 record 8필드 + @JsonInclude(NON_NULL).
    │                                       정적 팩토리 idle/queued/proposed/matched 로 만든다
    ├── domain/                           GameKey / ActiveRequest / CancelResult /
    │   │                                 MatchRequestStatus (IDLE 포함 6개) /
    │   │                                 ProposalResult (수락·거절이 같이 쓰는 응답 갈래 한 enum)
    │   │                                 (ProposalStatus / AcceptanceStatus 는 삭제됐다)
    │   └── condition/                    ★ 사용자가 고르는 조건 값만 모은다
    │       │                             KeyConditionType / VoicePreference / PlayPurpose
    │       │                             (GameKey 는 조건이 아니라 갈래라 domain/ 에 남았다)
    │       ├── lol/                      LolPosition.java (티어 enum 은 없다 — 사다리는 Redis 다)
    │       ├── valorant/                 ValorantRole.java (DUELIST/INITIATOR/CONTROLLER/SENTINEL)
    │       └── pubg/                     .gitkeep 만 있다 — PUBG 핵심 조건은 플랫폼 문자열이라
    │                                       enum 이 없고 validator 가 값을 직접 본다
    ├── redisKeys/SharedKeys.java         ★ Redis 키 문자열의 자바 쪽 단일 출처.
    │                                       같은 문자열이 Lua 안에도 있다 — 어느 스크립트인지는
    │                                       그 클래스 주석의 목록에 있다
    ├── block/                            Block.java / BlockRepository.java (blocks 읽기 전용 — 옛 social.blocks, D-34)
    ├── notification/
    │   ├── PushEventType.java            MATCH_* 5종. **5종 모두 발행된다**
    │   └── PushPublisher.java            ★ qm:pubsub:push:{userId} 로 publish. 예외를 안 던진다
    ├── redisLock/PoolLock.java           ★ 후보 풀 분산 락 (Redisson). 배정 전체를 감싼다
    ├── failover/                         [실험용] Redis 페일오버 재시도. 기본 꺼짐
    │                                       (application.yaml 의 queuemate.failover 블록)
    ├── service/
    │   ├── MatchRequestService.java      join() — INV-1 선점 (claim-request.lua). JoinResult 를 돌려준다
    │   ├── MatchTrigger.java             @Async — 톰캣 스레드를 놓고 파티 배정으로 넘긴다
    │   ├── MatchCancelService.java       Redis에 저장된 활성 요청을 읽어 게임별 규칙에 위임
    │   ├── MatchQueryService.java        ★ 상태 조회. 활성 요청 HASH → 파티 HASH → 수락자 SET 을
    │   │                                   읽어 IDLE/QUEUED/PROPOSED/MATCHED 로 답한다.
    │   │                                   없는 필드는 null 로 흘려보낸다 (조회가 터지면 안 된다)
    │   ├── ProposalService.java          제안 수락/거절 (accept/decline-proposal.lua). 거절이면
    │   │                                   거절한 본인만 MatchCancelService 로 큐에서 뺀다.
    │   │                                   확정되면 cleanup-confirmed.lua 로 뒷정리하고
    │   │                                   MATCH_CONFIRMED 를 파티 전원에게 발행한다
    │   │                                   (파티를 DB 에 만드는 것은 platform — 파티 HASH 를 읽는다, D-42)
    │   ├── ProposalSweeper.java          @Scheduled(fixedDelay = queuemate.sweep.interval-ms).
    │   │                                   qm:proposal:pending 에서 시한 지난 것을 회차당 100건 꺼낸다
    │   └── ProposalExpiryService.java    꺼낸 제안 하나를 만료시킨다 (expiry-proposal.lua).
    │                                       무응답자만 큐에서 빼고 MATCH_PROPOSAL_EXPIRED 를
    │                                       그 제안에 있던 전원에게 발행한다
    ├── rule/
    │   ├── CandidateRule.java            게임별 파티 배정 규칙 인터페이스
    │   ├── ScriptSupport.java            게임 공통. 반환 코드 읽기 / 멤버 추출 / 차단 판정 / 스캔 상한 20
    │   │                                   (게임 Lua 가 member:{userId} 필드 · {code, ...} 반환 약속을 지켜야 쓸 수 있다)
    │   ├── lol/
    │   │   ├── LolCandidateRule.java     ★ 설정 읽기 + 차단 목록 조회 + 락 잡고 assigner 호출
    │   │   ├── LolUntieredAssigner.java  ★ 티어 안 보는 배정. JOINED_AND_FULL 분기가 제안 트리거
    │   │   ├── LolTieredAssigner.java    ★ (포지션 x 티어) 격자 배정. tierRule 해석은 Lua 가 한다
    │   │   │                               (자바는 티어 접미사 없는 needs 키만 넘긴다)
    │   │   ├── LolPartyLeaver.java       취소. MATCH_CANCELLED 알림도 여기서 발행
    │   │   ├── LolPartyKeys.java         Redis 키 조립을 한 자리에 모은 것
    │   │   └── LolModeConfig.java        gameconfig 에서 읽은 모드 설정 record
    │   ├── pubg/                         PubgCandidateRule / PubgModeConfig / PubgPartyKeys /
    │   │                                 PubgPartyLeaver / Pubg{Tiered,Untiered}Assigner
    │   └── valorant/                     같은 6개 (Valorant* 접두사).
    │                                     합류마다 파티 티어 범위를 좁힌다
    └── validation/
        ├── MatchConditionValidator.java  게임별 validator로 라우팅
        ├── GameConditionValidator.java
        ├── lol/LolConditionValidator.java    포지션 + 티어(tierRule) 검증
        ├── pubg/PubgConditionValidator.java  플랫폼(STEAM/KAKAO) + 모드 + 티어(tierRule) 검증
        └── valorant/ValorantConditionValidator.java  역할군 + 모드 + 티어(tierRule) 검증

backend/src/main/resources/redis/         ★ 불변식이 실제로 지켜지는 곳 (20개)
├── shared/claim-request.lua            INV-1     EXISTS + HSET + EXPIRE 60. KEYS[2] 로 app:platform 의
│                                                 (옛 app:room — D-33) 입장 표시 키도 EXISTS 로 본다 — 있으면 -1 (docs/11 D-19)
├── lol/create-or-check-party-untiered.lua        후보 찾기, 없으면 만들고 들어감 (티어 안 봄)
├── lol/create-or-check-party-tiered.lua          위의 (포지션 x 티어) 격자판. tier-range 표와
│                                                 티어 사다리를 Lua 가 직접 읽어 tierLo/tierHi 를
│                                                 정한다 (ZRANK 그대로, 0 부터)
├── lol/join-party.lua                  INV-3/7   찾아 둔 파티에 합류. 반환 2 = 정원 참
├── lol/join-party-tiered.lua           INV-3/7   위의 격자판. 티어 범위를 다시 계산하지 않는다
├── lol/leave-party.lua                           취소. compare-and-delete + 색인 되돌리기
│                                                 (티어를 안 보는 모드는 티어 이름에 "NONE" 을 넘겨
│                                                  접미사를 빈 문자열로 접는다)
├── pubg/ (5개)                                   배정·취소. 포지션도 중복 금지도 없어 색인이
│                                                 (조건 x 티어) 격자가 아니라 티어 한 줄이다
├── valorant/ (5개)                               배정·취소. 역할군 x 티어 격자이고
│                                                 합류마다 파티 티어 범위를 좁힌다
├── proposal/accept-proposal.lua        INV-4     수락 집계. 전원이 차면 확정까지.
│                                                 확정 시 qm:proposal:pending 에서 뺀다
├── proposal/decline-proposal.lua       INV-5     거절. 제안 흔적과 수락자 집합을 지운다
├── proposal/expiry-proposal.lua        INV-5     만료. status 가 PENDING 일 때만 깨고,
│                                                 무응답자 / 수락자 목록을 돌려준다
└── proposal/cleanup-confirmed.lua                확정 뒷정리. 파티 HASH 에 game/modeKey/voice/purpose/
                                                  confirmedAt 을 채우고(platform 이 읽는 계약 — D-42), 활성 요청에
                                                  status=PARTY 를 찍은 뒤 TTL — 파티 600초 · 활성 요청 · 수락자 SET 60초

backend/src/test/java/com/queuemate/matching/
├── concurrency/
│   ├── ConcurrencyTestSupport.java       DB15 flush + LoL 3모드 시드 + 가상스레드 동시 출발
│   │                                     + H2 ddl-auto=create-drop (blocks 때문 — 옛 social.blocks)
│   ├── ActiveRequestConcurrencyTest.java INV-1 + 방에 있으면 IN_ROOM (docs/11 D-19)
│   ├── PartyJoinConcurrencyTest.java     INV-3 / INV-8 / 한 사용자 한 파티
│   ├── ValorantPartyJoinConcurrencyTest.java  같은 것을 VALORANT 경로로
│   └── NaiveVsLuaComparisonTest.java     순진한 방식이 깨짐을 대조로 증명
├── notification/PushNotificationTest.java  알림 6건. 실제로 구독해서 받아 본다
└── proposal/ProposalIdempotencyTest.java   수락/거절 멱등성 11건

backend/src/test/resources/schema.sql     테스트용 H2 에만 만드는 social.blocks (운영은 public.blocks · bigint — 맞춰야 한다, D-25 · D-34)
seed/gameconfig.redis                     모드 설정 원본. LoL(12모드 + 티어 사다리 32 + tier-range 표 4모드)
                                          + PUBG(8모드 + 티어 사다리 27 + tier-range 표 4모드)
                                          + VALORANT(4모드 + 티어 사다리 26 + tier-range 표 2모드).
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
    ├ MatchRequestService.join()              claim-request.lua (INV-1)  → 이미 있으면 409 ALREADY_QUEUED
    │                                         게시판 방에 들어가 있으면 409 IN_ROOM (docs/11 D-19)
    │                                         선점 키에 EXPIRE 60 이 걸린다
    ├ MatchTrigger.trigger()   @Async         톰캣 스레드를 놓아준다
    │    └ LolCandidateRule.canJoin()
    │         · qm:gameconfig:LOL:{mode} 에서
    │           targetPartySize / positionUniqueness / tierRule 읽기
    │           (maxTierGap 은 없어졌다. 허용 범위는 tier-range 표가 전부다)
    │         · BlockRepository.findBlockedUserIds()   ← 락 밖에서 DB 한 번 (INV-6 선필터)
    │         · PoolLock.run(poolKey) 안에서 tier 유무로 assigner 선택
    │              LolUntiered/LolTieredAssigner.assign()   최대 20회 후보를 훑는다
    │                                                   (ScriptSupport.MAX_CANDIDATE_SCAN)
    │              ├ create-or-check-party-*.lua → {코드, ...}
    │              │    1=새로 만들고 들어감(→ MATCH_QUEUE_UPDATED, 여기서 끝)
    │              │    2=후보를 찾음(멤버 목록 반환) -1=안 맞는 값 -2=claim 만료
    │              ├ 멤버 중 차단 상대가 있으면 → 다음 후보 (전부 차단이면 새 파티)
    │              └ join-party*.lua → {코드, partyId, 인원}
    │                   1=합류(→ MATCH_QUEUE_UPDATED)
    │                   2=합류 후 정원 참(→ MATCH_PROPOSAL_CREATED, payload 에 partyId)
    │                     이때 파티 HASH 에 status=PENDING + expiresAt 을 HSETNX 로 쓰고,
                     그 성공 분기 안에서 qm:proposal:pending 에 ZADD 한다
    └ 201 {requestId, status:QUEUED}          (배정 결과를 기다리지 않는다)

POST /api/v1/proposals/{partyId}/accept          ("나" = 쿠키의 sub — 2026-09-27 까지는 ?userId=)
 └ ProposalController → ProposalService.accept()
    ├ accept-proposal.lua   수락자 SET(qm:proposal:accepts:{partyId})에 SADD → SCARD 가
    │                       target 에 닿으면 status=CONFIRMED + pending 에서 ZREM (INV-4)
    │                       이미 확정된 제안에 또 오면 ALREADY_RESPONDED (둘 다 204)
    └ CONFIRMED 면 cleanup-confirmed.lua
                            파티 HASH 에 game/modeKey/voicePreference/playPurpose/confirmedAt 채움
                            (platform 이 읽는 계약 — docs/11 D-42), 파티원 활성 요청에 status=PARTY,
                            TTL — 파티 HASH 600초 · 활성 요청 · 수락자 SET 60초,
                            돌려받은 파티원 전원에게 MATCH_CONFIRMED (payload {partyId}) → 204
                            ★ 그 뒤는 platform 몫: 프런트가 "이 매칭으로 파티 만들기"(경로 미정)를 부르면
                              platform 이 qm:party:{partyId} 를 읽어 파티 · 방을 만들고 파티원에게 입장 표시 키를
                              찍는다. 60초 뒤 활성 요청이 만료돼 새 매칭이 가능하다. outbox · SQS 는 없다

POST /api/v1/proposals/{partyId}/decline?requestId=   ("나" = 쿠키의 sub)
 └ ProposalController → ProposalService.decline()
    ├ decline-proposal.lua  status/expiresAt HDEL + 수락자 SET DEL + pending ZREM
    │                       (파티와 참가자는 남긴다)
    └ DECLINED 면 MatchCancelService.cancel() — 거절한 본인만 큐에서 뺀다 → 204

제안 만료 (REST 가 아니라 스케줄러다)
 └ ProposalSweeper.sweep()  @Scheduled(fixedDelay = queuemate.sweep.interval-ms, 기본 1초)
    ├ ZRANGEBYSCORE qm:proposal:pending 0 now LIMIT 0 100   (한 회차 100건, 파티별 try/catch)
    └ ProposalExpiryService.expire(partyId)
         ├ expiry-proposal.lua  status 가 PENDING 일 때만 status/expiresAt HDEL +
         │                      수락자 SET DEL + pending ZREM.
         │                      PENDING 이 아니면(이미 확정·거절됐다) pending 에서만 빼고
         │                      빈 목록 → 만료가 확정을 뒤집지 못한다 (INV-5)
         ├ 무응답자만 MatchCancelService.cancel()  — 수락한 사람은 파티에 남는다
         └ MATCH_PROPOSAL_EXPIRED 를 그 제안에 있던 전원에게 (payload {partyId})
```

배정에 성공한 스크립트는 `PERSIST` 로 claim 의 만료를 뗀다. 그래서 배정 전에 앱이 죽으면
60초 뒤 선점이 저절로 풀리고, 배정에 성공했으면 영구히 남는다.

### Redis 키

| 키 | 타입 | 뜻 |
|---|---|---|
| `qm:gameconfig:LOL:{modeKey}` | HASH | `targetPartySize`, `positionUniqueness`, `tierRule`(`NONE` / `EXIST`). 시드가 원본. `maxTierGap` 은 **없앴다** — 시드가 `HDEL` 로 걷어낸다. **`app:platform` 도 이 키를 `EXISTS` 로 읽어 모집 글의 `mode` 를 검증한다**(docs/11 D-29 — 키 모양을 바꾸면 그쪽 검증이 조용히 꺼진다) |
| `qm:gameconfig:LOL:tier` | ZSET | **티어 사다리. 티어 값의 원본이다** (자바 enum 은 없다). score = 단계 번호 `0 UNRANKED`, `1 IRON_4` … `31 CHALLENGER` (32개). Lua 가 `ZRANK` 로 순번을 뽑고 `ZRANGE` 로 칸 목록을 만든다. **중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi` 가 엉뚱한 칸을 가리킨다** — 큐가 비어 있을 때 바꿔라. **`app:platform` 도 `ZSCORE` 로 읽어 게임 계정의 `tier` 를 검증한다**(docs/11 D-29) |
| `qm:gameconfig:LOL:tier-range:{modeKey}` | HASH | `tierRule` 이 `NONE` 이 **아닌** 모드가 갖는다(지금은 `RANKED_SOLO` + `RANKED_FLEX_2/3/5` 넷). `GOLD_4 → SILVER_4:PLATINUM_1` 처럼 **단 단위** 허용 범위. `SOLO_ONLY` 면 그 티어는 파티를 못 만든다. 줄이 없으면 그 티어는 400이다(fail-closed) |
| `qm:user:active-request:{userId}` | HASH | 활성 요청. `requestId/game/modeKey/voicePreference/playPurpose/keyValue/tier/queuedAt/partyId`. **`queuedAt` 은 줄 선 시각(epoch millis)이고 Lua 가 아니라 `MatchRequestService` 가 필드로 넘긴다** — 조회가 "얼마나 기다렸나"를 답하려면 요청이 살아 있는 동안 남는 자리가 필요한데 이 HASH 말고는 없다(`match_requests` 테이블은 만들지 않는다). 이 키의 존재 자체가 INV-1 선점이다 (`claim-request.lua`). 배정 전까지는 TTL 60초. **제안이 확정되면 `status = PARTY` 필드가 붙고 TTL 60초(`queuemate.proposal.confirmed-retention-seconds`)가 걸린다** (`cleanup-confirmed.lua`, docs/11 D-42) — 키를 바로 지우면 그 순간 새 매칭을 걸 수 있어 INV-2 가 깨지므로 표시하고 만료시킨다. 60초 뒤부터 "한 번에 하나만" 은 platform 의 입장 표시 키가 맡는다 |
| `qm:user:active-room:{userId}` | STRING | **이 앱의 키가 아니다 — `app:platform`(의 `room` 패키지. 2026-09-25 까지는 `app:room`)의 입장 표시 키다** (docs/11 D-19 · D-33). 사용자가 게시판 방에 들어가 있는 동안 있고 값은 `roomId` 다. 이 앱은 `claim-request.lua` 의 `KEYS[2]` 로 받아 **`EXISTS` 로 있는지만 본다** — 쓰지도 지우지도 값을 읽지도 `EXPIRE` 를 걸지도 않는다. 있으면 매칭 요청이 409 `IN_ROOM` 이다. 접두사의 원본은 `../platform` 의 `room/redisKeys/RoomKeys.java` `ACTIVE_ROOM_PREFIX`(옛 `../room` 의 같은 파일)이고 `SharedKeys.ACTIVE_ROOM_PREFIX` 가 따라 적는다. 자료형 · 값 · 수명은 `app:platform` 이 정한다(`../platform/contracts/platform-api.md` "방" 참조) |
| `qm:party:{partyId}` | HASH | `partyId/target/createdAt/tierLo/tierHi` + `member:{userId} = keyValue` (+ 정원이 차면 `status`/`expiresAt`). **확정되면 `cleanup-confirmed.lua` 가 `status=CONFIRMED` 위에 `confirmedAt`(epoch ms) · `game` · `modeKey` · `voicePreference` · `playPurpose` 를 더 채우고 TTL 600초(`queuemate.proposal.confirmed-party-ttl-seconds`)를 건다 — `app:platform` 이 이 HASH 만 읽고 파티를 만들 수 있게 한 것이고, 그 필드 이름이 platform 과의 계약이다(docs/11 D-42, `contracts/events.md` SQS 절).** 인원 수 필드는 없다 — `member:` 를 센다. `tierLo/tierHi` 는 사다리 **순번**이고 `ZRANK` 값 **그대로**라 0부터다 — 읽는 쪽이 `ZRANGE lo hi` 에 그대로 넘긴다. 티어를 안 보는 모드도 `0/0` 으로 같은 모양을 갖는다. VALORANT 파티는 여기에 `minTier`/`maxTier`(지금까지 들어온 사람의 최저·최고 순번)가 더 붙는다 |
| `qm:proposal:accepts:{partyId}` | SET | 제안 수락자 userId. `accept-proposal.lua` 가 `SADD` 후 `SCARD` 로 세어 `target` 과 비교한다. 거절(`decline-proposal.lua`) · 만료(`expiry-proposal.lua`) · 취소(`leave-party.lua`)가 `DEL` 하고, 확정되면 `cleanup-confirmed.lua` 가 `EXPIRE`(`queuemate.proposal.confirmed-retention-seconds`, 기본 60초)만 건다 — 재전송된 수락이 도착하는 창만큼만 남긴다. 제안 상태(`status`/`expiresAt`)는 별도 `qm:proposal:{id}` 레코드가 아니라 파티 HASH 에 있다 — **제안 id = partyId** |
| `qm:proposal:pending` | ZSET | **진행 중인 제안 목록.** member = partyId, score = `expiresAt`. 정원이 찰 때 합류 스크립트가 `HSETNX status 'PENDING'` 성공 분기 안에서 `ZADD` 하고, 제안이 끝나는 **모든** 자리(확정 · 거절 · 취소 · 만료)가 `ZREM` 한다. 읽는 쪽은 `ProposalSweeper#sweep()` 하나다 — `ZRANGEBYSCORE 0 now LIMIT 0 100` 으로 시한이 지난 것만 꺼낸다. 게임을 구분하지 않는 키가 하나뿐인 것은 제안이 파티 HASH 위에서만 돌기 때문이다 |
| `qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}` | ZSET | **그 값을 아직 못 채운** 파티들. score = createdAt |
| `qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}:{tier}` | ZSET | 위의 티어판. 색인이 (포지션 x 티어) 격자가 된다. `:{tier}` 접미사는 **Lua 가 붙인다** — 자바는 접미사 없는 키만 KEYS 로 넘긴다 |
| `qm:lock:pool:qm:party:open:LOL:{mode}:{voice}:{purpose}` | — | Redisson 후보 풀 락 (`PoolLock`). keyValue 는 **들어가지 않는다** |
| `qm:pubsub:push:{userId}` | 채널 | 사용자 알림. `app:realtime` 이 구독해 SSE 로 배달한다 (`PushPublisher`) |

**핵심 아이디어**: 후보를 검색하지 않는다. 조건을 키 이름에 넣어 색인을 뒤집었으므로,
내 값이 비어 있는 파티만 그 ZSET에 모여 있다. 맨 앞 하나를 꺼내면 끝이다(O(1)).
그래서 claim 재시도 루프가 없다 (docs/11 #33).

**티어가 붙으면서 달라진 것**: 색인이 (포지션 x 티어) 격자가 됐다. 파티가 받아들일 티어
범위는 **LoL·PUBG 에서는 만든 사람 기준으로 생성 시 한 번** 정해지고 그 뒤 바뀌지 않는다 —
그 범위를 파티 HASH 의 `tierLo/tierHi` 에 적어 둬야 정원이 찼다가 풀릴 때 어느 칸으로
되돌릴지 알 수 있다. (**VALORANT 는 다르다** — 합류할 때마다 범위를 좁힌다. 아래를 보라.)
대가로 서로 직접은 안 받을 두 사람이 같은 파티가 될 수 있다 (의도한 것이다).
`tierRule` 해석은 **Lua 가 한다.** 예전에는 `LolTieredAssigner#tierRange()` 가 표를 읽어
`[최저, 최고]` 로 환산해 숫자만 넘겼는데, 그 메서드가 없어졌다. 지금은 Lua 가 tier-range
표(`KEYS[3]`)와 티어 사다리(`KEYS[4]`)를 직접 읽고 **`ZRANK` 를 그대로**(0 부터)
`tierLo/tierHi` 에 적는다. 읽는 쪽은 `ZRANGE lo hi` 에 그대로 넘긴다 — 더하고 빼는 자리가 없다.
`join` 과 `leave` 는 **자기 tier-range 를 다시 읽지 않는다** — 파티의 `tierLo/tierHi` 를
되돌려 칸을 만든다. 내 범위로 빼면 파티가 실제로 올라가 있는 칸과 어긋나 유령 색인이 남는다.

**VALORANT 만 범위가 움직인다.** 발로란트 규칙은 "파티 최고 티어 <= 한계(파티 최저 티어)"라
3인 파티를 방장 줄 하나로 표현할 수 없다. 그래서 `valorant/join-party-tiered.lua` 가 합류할
때마다 파티 범위를 **지금 범위 ∩ 들어온 사람의 tier-range 줄** 로 좁힌다 — 옛 범위 칸 전부에서
파티를 빼고, **아직 빈 역할군만**(`qm:party:needs-roles:{partyId}` SET) 새 범위 칸에 다시
올린다. 정렬값은 지금 시각이 아니라 파티의 `createdAt` 이다. 파티 HASH 의 `minTier`/`maxTier`
는 지금까지 들어온 사람의 최저·최고 순번이다 — `join-party-tiered.lua` 가 읽어 범위를 좁히고,
`valorant/leave-party.lua` 가 남은 사람 기준으로 다시 적는다(취소는 `needs-roles` 도 되돌린다).

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

- `POST /api/v1/match-requests` — 조건 검증(포지션 + 티어) → INV-1 선점 → 비동기 파티 배정.
  거절은 둘 다 409 다 — 이미 활성 요청이 있으면 `ALREADY_QUEUED`, **게시판 방에 들어가 있으면 `IN_ROOM`**
  (`claim-request.lua` 가 `app:platform`(옛 `app:room` — D-33)의 입장 표시 키를 `EXISTS` 로 본다 — docs/11 D-19)
- **인증(2026-09-27)** — 모든 `/api/v1/**` 가 쿠키 `qm_access`(RS256 JWT)를 요구한다. `app:platform` 의 공개 키로 검증만 하고
  (`iss` · `exp` · `token_use == access` · `sub` 숫자), "나"는 `sub` 다. 상태를 바꾸는 요청은 `Origin` 을 허용 목록과 대조한다(403 `ORIGIN_NOT_ALLOWED`).
  테스트 `web/AuthenticationApiTest`(21건) · `common/security/JwtPublicKeysTest`(5건)
- `DELETE /api/v1/match-requests/{id}` — compare-and-delete + 색인 되돌리기 (2026-09-27 까지는 `?userId=` 를 받았다)
- `GET /api/v1/match-requests` — **상태 조회**(2026-09-27 까지는 `?userId=`). `MatchQueryService` 가 활성 요청 HASH
  → 파티 HASH → 수락자 SET 을 읽어 `IDLE` / `QUEUED` / `PROPOSED` / `MATCHED` 로 답한다.
  응답은 `MatchRequestResponse`(record 8필드, `@JsonInclude(NON_NULL)`)이고 접수(201)도 같은 DTO 의
  `queued(requestId, queuedAt)` 갈래를 돌려준다(2026-09-27 — 그 전에는 문자열 `"CREATED"` 였다).
  **경로가 계약과 다르다**(계약은 `/{requestId}`) — `contracts/README.md` #5
- LoL 파티 배정 전체 (찾기/만들기/합류/정원 참 판정/색인 갱신) — **티어 유/무 두 갈래**
- 티어 매칭 — `tierRule` 은 `NONE` / `EXIST` 둘뿐이고, `EXIST` 면 허용 범위를 정하는 것은
  전적으로 `tier-range` 표다. 색인이 (포지션 x 티어) 격자이고, 파티의 허용 범위는 생성 시
  고정된다. 표는 라이엇 실제 규칙을 **단 단위**로 편 것이다 — 솔랭은 다이아가 안 끼면 ±1 티어,
  다이아가 끼면 ±2 단, 마스터 이상은 KR 규정상 `SOLO_ONLY`. 자유 랭크는 규정이 달라
  (`UNRANKED`~`DIAMOND_1`) / (`MASTER`~`CHALLENGER`) **두 덩어리**다
- 후보 풀 분산 락 (`redisLock/PoolLock.java` + `config/redis/RedissonConfig.java`) — 후보를 훑는
  구간 전체를 한 락 안에 둔다. 락 실패는 503 으로 fail-closed 한다
- claim TTL — `claim-request.lua` 가 `EXPIRE 60`, 배정 스크립트 4개가 `PERSIST`.
  배정 스크립트에는 `EXISTS` 가드(반환 `-2`)가 있어 만료된 선점에는 배정하지 않는다
- 제안 수락 / 거절 / 확정 — `POST /api/v1/proposals/{partyId}/accept|decline`
  (`ProposalController` → `ProposalService` → `redis/proposal/accept-proposal.lua` ·
  `decline-proposal.lua`). 수락자 SET 을 `SCARD` 로 세어 `target` 에 닿을 때만
  `status=CONFIRMED` (INV-4), 확정은 거절로 뒤집히지 않는다 (INV-5 의 confirmed 갈래).
  거절은 제안 흔적(`status`/`expiresAt`/수락자 SET)을 지우고 거절한 본인만 큐에서 뺀다.
  `ProposalIdempotencyTest` 11건이 멱등성으로 지킨다
- 확정 뒷정리 — `cleanup-confirmed.lua` 가 파티 HASH 에 `game` / `modeKey` / `voicePreference` /
  `playPurpose` / `confirmedAt` 을 채우고(`app:platform` 이 읽는 계약 — docs/11 D-42), 파티원 활성 요청에
  `status=PARTY` 를 찍은 뒤 TTL 을 건다 — 파티 HASH 600초, 활성 요청 · 수락자 SET 60초(지우지 않고
  만료시킨다 — INV-2). 그다음 파티 전원에게 `MATCH_CONFIRMED` 를 발행한다. **60초 뒤 사용자는 새 매칭을
  걸 수 있다** — 확정된 사용자가 평생 갇히던 문제(옛 `HANDOFF.md` §0-1 ①)는 D-42 로 닫혔다
- INV-6 차단 선필터 — `rule/{lol,pubg,valorant}/*CandidateRule#canJoin` 이 락을 잡기 전에
  `BlockRepository#findBlockedUserIds` 로 내 차단 목록을 한 번 읽어 후보 파티를 거른다(D-41 — 한 겹이 전부다).
  기본 실행(H2)은 `backend/src/main/resources/schema.sql` 이 `blocks` 를 만든다
- 색인 복원 — 정원이 찼다 한 명 취소로 풀린 파티가 남은 사람이 맡지 않은 줄 **전부**에 되돌아온다
  (LoL · VALORANT, `78f5c3f`, 회귀 테스트 포함)
- 제안 만료 — `qm:proposal:pending` ZSET + `ProposalSweeper`(`@Scheduled`, 기본 1초) +
  `ProposalExpiryService` + `expiry-proposal.lua`. **수락하지 않은 사람만** 큐에서 빼고
  (수락자는 파티에 남아 다시 기다린다) `MATCH_PROPOSAL_EXPIRED` 를 그 제안에 있던 전원에게 보낸다
- 사용자 알림 publish — `qm:pubsub:push:{userId}` 로 `MATCH_QUEUE_UPDATED` /
  `MATCH_PROPOSAL_CREATED` / `MATCH_CANCELLED` / `MATCH_PROPOSAL_EXPIRED` /
  `MATCH_CONFIRMED` **5종 전부**. 봉투는 `{type, eventId, occurredAt, payload}`
- INV-1 / INV-3 / INV-7 / INV-8(LoL) — Lua + 후보 풀 락으로 보장되고 동시성 테스트가 지킨다
- INV-10 — Redis 장애 시 503 fail-closed
- gameconfig를 Redis에서 읽기만 하는 구조 (재배포 없이 모드 추가/삭제)

### 4.2 안 되는 것 ❌

| 없는 것 | 근거 (직접 확인한 것) |
|---|---|
| **확정된 파티를 DB 에 만드는 것 — platform 쪽 진입점** | **이 앱 몫은 끝났다**(§4.1 — 파티 HASH 를 자기완결로 채우고 `MATCH_CONFIRMED` 를 보낸다, docs/11 D-42). 만드는 것은 `app:platform` 이다 — 프런트가 "이 매칭으로 파티 만들기" 를 부르면 `qm:party:{partyId}` 를 읽어 파티와 방을 만들고 파티원에게 입장 표시 키를 찍는다. **그 진입점의 경로 · 본문 · 에러 코드가 미정**이라 지금은 확정돼도 파티가 DB 에 생기지 않는다(파티 HASH 는 600초 뒤 증발한다). outbox · SQS 로 푸는 길은 D-42 로 닫았다 |
| **취소·만료의 구분** | 상태 조회는 생겼지만(§4.1) **왜 큐에서 빠졌는지는 답하지 못한다.** 취소도 만료도 활성 요청 키를 지우므로 `IDLE` 과 구분되지 않는다 — `MatchRequestStatus` 에 `CANCELLED`/`EXPIRED` 가 있지만 조회가 그 값을 돌려주는 경로는 없다(그 enum 주석에 "자리만 남겨 둔다"고 적혀 있다). 이유를 알려면 알림을 받았어야 하는데 Pub/Sub 은 at-most-once 다 |
| ~~**INV-6 차단 검증**~~ | **구현됐다(선필터 한 겹, docs/11 D-41 — §4.1).** 확정 직전 최종 검증은 두지 않기로 했고, 로컬 H2 는 `backend/src/main/resources/schema.sql` 이 `blocks` 를 만든다. 남은 것은 운영에서 `DB_URL` 이 platform 의 Postgres 를 가리키는 것뿐이다 |
| ~~**SQS outbox**~~ | **두지 않는다(docs/11 D-42).** 2026-09-27 에 Flyway + `matching_outbox` 를 넣었다 같은 날 뺐다. AWS SDK · Flyway 가 `backend/build.gradle` 에 없는 것이 맞다 |
| **메트릭** | `CLAUDE.md` §6 Definition of done 4번이 요구하는데 `MeterRegistry` / `@Timed` / `Metrics.` 가 `backend/src/main` 에 **0건**이다. `spring-boot-starter-actuator` 는 들어 있고 `/actuator/metrics` 도 열려 있어(`application.yaml`) JVM·HTTP 기본 지표는 나오지만, 매칭 고유 지표(큐 대기 시간, 배정까지 걸린 시간, 제안 수락률, 만료·취소 건수)는 하나도 없다 |
| ~~**인증**~~ | **2026-09-27 에 붙었다**(§4.1) — 그 전에는 JWT 가 없고 `userId`를 요청 바디와 쿼리 파라미터로 받는 임시 상태였다. 남은 것 — 조회 경로를 `/match-requests/me` 로 옮길지(계약 #5 와 같이 정한다), `load-test/` 의 스크립트가 아직 바디에 `userId` 를 싣고 쿠키가 없다(6번과 같이 고친다) |
| **`GET /games`** | 계약에 있으나 컨트롤러가 없다 |
| **Dockerfile** | 없다 |

> `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 은 여전히 `backend/src/` 에 0건이고,
> **그게 맞다** — 이 앱의 몫은 Redis Pub/Sub publish 까지이고 SSE 배달은 `app:realtime` 이 한다
> (CLAUDE.md §3). 넣지 마라.

### 4.3 알아두면 헷갈리지 않을 것

**(a) 지원 게임 3개의 배정이 전부 구현됐고, 테스트도 세 게임 모두 있다.**

`GameKey` enum은 `LOL, VALORANT, PUBG` 셋이다 — 그건 **제품 경계**이지 구현 현황이 아니다
(docs/11 #8). 커밋된 구현체는 이렇다:

- `GameConditionValidator` 구현체: `LolConditionValidator` / `PubgConditionValidator` /
  `ValorantConditionValidator` **3개**
- `CandidateRule` 구현체: `LolCandidateRule` / `PubgCandidateRule` / `ValorantCandidateRule` **3개**
- `seed/gameconfig.redis` 는 LoL · PUBG · VALORANT 세 섹션을 갖는다. PUBG 는 모드 8
  (`{NORMAL,RANKED}_{DUO,SQUAD}_{TPP,FPP}`) / 티어 사다리 27 / 랭크 tier-range 표 4,
  VALORANT 는 모드 4 / 티어 사다리 26 / tier-range 표 2. 섹션마다 `seed done: ...` 줄이 하나씩 있다
- `rule/valorant` 도 채워졌다 (`Valorant*` 6개). `domain/condition/pubg` 에는 `.gitkeep` 만
  있다 — PUBG 핵심 조건은 플랫폼 문자열이라 enum 이 없고 validator 가 값을 직접 본다
- **테스트도 세 게임 모두 있다** — `PartyJoinConcurrencyTest`(LoL) ·
  `ValorantPartyJoinConcurrencyTest`(8건) · `PubgPartyJoinConcurrencyTest`(9건)

LoL 만으로 시작한 범위 축소는 사고가 아니라 결정이다 — docs/11 **#30**("LoL만 / Tier 0만 / 차단 검증 제외").

> 이력 참고: 한때 PUBG 시드·테스트 헬퍼가 구현 없이 들어갔다가 LoL 전용으로 되돌린 적이 있다.
> 지금은 시드·검증·배정이 세 게임 모두 맞춰져 있고, 어긋난 것은 테스트뿐이다 —
> `ConcurrencyTestSupport` 의 시드 헬퍼는 **여전히 LoL 전용**이라 PUBG·VALORANT 테스트는
> 자기 시드를 직접 심는다 (`Valorant`/`PubgPartyJoinConcurrencyTest` 가 그렇게 한다).

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

**"지금 상태" 조회는 생겼다.** `GET /match-requests`(2026-09-27 까지는 `?userId=`) 가 `IDLE`/`QUEUED`/`PROPOSED`/
`MATCHED` 로 답한다(§4.1). 경로가 계약(`/{requestId}`)과 다른 이유는 활성 요청이 **사용자
단위**로 저장되고(INV-1), 이 조회가 가장 필요한 순간인 "페이지를 새로 열었을 때" 클라이언트가
`requestId` 를 잃은 상태이기 때문이다 — `contracts/README.md` #5.

**남은 구멍은 두 개다.**
1. 조회는 **왜 큐에서 빠졌는지**를 답하지 못한다. 취소도 만료도 활성 요청 키를 지우므로
   `IDLE` 과 구분되지 않는다 (§4.2).
2. 확정 알림(`MATCH_CONFIRMED`)과 만료 알림(`MATCH_PROPOSAL_EXPIRED`)은 **이제 나간다.**
   남은 것은 그 뒤 — 파티를 DB 에 만드는 `app:platform` 쪽 진입점("이 매칭으로 파티 만들기")이 미정이다
   (docs/11 D-42 — 이 앱은 파티 HASH 를 채워 두고 알림을 보내는 데까지 했다. `status=PARTY` 는 60초 뒤 만료된다).

`load-test/match_latency.py` 가 성사를 감지하려고 HTTP 가 아니라 **Redis 를 직접 폴링**하는
것은 그 스크립트가 알림 도입 전에 쓰였기 때문이다
(`HGET qm:user:active-request:{uid} partyId` → 파티 HASH 조회). 예전에는 `HGET qm:party:{pid} size` 를
봤는데 **그 `size` 필드는 이제 없다** — 파티 HASH 는 인원을 `member:` 필드를 세어 구한다. 스크립트도
`member:` 필드를 세도록 고쳤지만(`32031a4`) 요청 바디·색인 키가 지금 시드와 어긋나 아직 그대로는
못 돈다(`HANDOFF.md` §3-D).

**(d) 코드 주석의 결정 번호가 `docs/11`과 맞지 않는다.**

- `domain/condition/VoicePreference.java`가 OPTIONAL 제거 근거로 `(docs/11 #31)`을 인용하는데,
  `docs/11`의 #31은 "파티 인원이 가변인 모드는 modeKey를 인원별로 쪼갠다"이다.
- `docs/11`의 Fixed decisions는 **#35까지**다.

즉 OPTIONAL 제거를 기록한 결정 항목이 `docs/11`에 **없다.** 결정 로그가 그 시점보다
낡았거나 번호가 어긋난 것이다. 자세한 것은 `docs/11_DECISION_LOG.md` 맨 끝의
"복원 시점 관찰 기록" 절.

**(e) 코드 주석이 자기보다 낡은 적이 있다. 주석을 사실로 믿지 마라.**

여기 적혀 있던 세 건은 `b05e2eb` 에서 주석만 고쳐 해소됐다 — `rule/CandidateRule.java` 의
없는 `AbstractCandidateRule` 언급, `redisLock/PoolLock.java`(당시 `redis/PoolLock.java`)의
"아무 데서도 쓰이지 않는다"(`LolCandidateRule#canJoin` 이 쓴다), LoL
`create-or-check-party-untiered.lua` · `join-party.lua` 머리의 "`join-or-create-party-tiered.lua`
(아직 없다)". 남은 낡은 LoL Lua 주석(PUBG 흔적)은 `HANDOFF.md` §3-D 에 있다.

이 저장소에 `join-or-create-party*.lua` 라는 파일은 **하나도 없다.** 그 이름이 보이면
옛 이름이다 (docs/11 D-6).

(예전에 여기 있던 `LolTieredAssigner` 클래스 주석의 `TABLE`/`WINDOW`·`ARGV[9..12]`·`TierRange`
항목은 주석과 코드가 고쳐져 뺐다. 지금 주석은 `ARGV[9]` = 티어 이름, keyValue 는 `ARGV[10..]` 로 코드와 맞다.)

**(f) 이 저장소는 git 저장소다.** private 원격 `github.com/rlaehddus302/queuemate-matching`
(`matching`)에 push 한다. 커밋 규칙은 `CLAUDE.md` §8.
**IntelliJ 가 새 파일을 자동으로 스테이징한다** — 사용자 작업 파일이 문서 커밋에 딸려 올라간
적이 있다. 커밋은 `git commit -- <파일>` 로 파일을 지정하거나, 직전에 `git diff --cached --stat`
으로 스테이징 목록을 확인해라 (`HANDOFF.md` §1).

### 4.4 위 서술을 다시 검증하는 명령

```bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching"

git ls-tree -r --name-only HEAD backend/src/main/resources/redis   # lua 20개
                                                      # shared 1 / lol 5 / pubg 5 / valorant 5 / proposal 4
grep -rn "LolTier" backend/src/                       # 0건이어야 맞다 — 티어 enum 은 삭제됐다
docker exec qm-redis redis-cli ZRANGE qm:gameconfig:LOL:tier 0 -1 WITHSCORES  # 티어 값의 원본 (32개)
grep -rn "TODO" backend/src/                          # 남은 TODO 지점
grep -rn "UnsupportedOperationException" backend/src/ # 0건이어야 맞다 — proposal 수락/거절은 구현됐다
grep -rn "outbox\|ProposalConfirmed\|PartyClosed\|flyway" backend/src/ backend/build.gradle  # 0건이어야 맞다 — D-42 로 두지 않는다
grep -rn "NOT_IMPLEMENTED" backend/src/               # 0건이어야 맞다 — 501 스텁은 없어졌다
grep -rn "MeterRegistry\|@Timed" backend/src/main/     # 0건 — 메트릭이 아직 없다는 근거
grep -rn "PushEventType\." backend/src/main/          # 실제로 발행되는 알림 종류
grep -rn "SseEmitter\|WebSocketConfig\|@MessageMapping" backend/src/ | wc -l   # 0이어야 맞다
grep -rln "implements CandidateRule" backend/src/     # 구현된 게임 목록
grep -rln "implements GameConditionValidator" backend/src/
grep -n "enum VoicePreference" -A2 backend/src/main/java/com/queuemate/matching/domain/condition/VoicePreference.java
grep -rn "@Scheduled\|@EnableScheduling" backend/src/main/            # 만료 스위퍼 — 2건이어야 맞다
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
>
> **여기에 셋이 더 끝났다.** ① **만료 sweeper** — `qm:proposal:pending` ZSET + `ProposalSweeper`
> + `ProposalExpiryService` + `proposal/expiry-proposal.lua`. 만료되면 **수락하지 않은 사람만**
> 큐에서 빠지고 `MATCH_PROPOSAL_EXPIRED` 가 그 제안에 있던 전원에게 나간다. ② **확정 후속 처리의
> 절반** — `proposal/cleanup-confirmed.lua` 가 활성 요청에 `status=PARTY` 를 찍고 수락자 SET 에
> TTL 을 걸며, `MATCH_CONFIRMED` 가 파티 전원에게 나간다. **남은 절반(outbox → SQS)은 2026-09-27 에 두지 않기로 했다 — docs/11 D-42.
> 파티 HASH 에 `game`/`modeKey`/`voicePreference`/`playPurpose`/`confirmedAt` 을 채우고 TTL 을 거는 것으로 이 앱 몫은 끝났다.**
> ③ **VALORANT 배정·취소** — Lua 5개 · `rule/valorant` 6개 · `ValorantRedisConfig` · 시드 ·
> `ValorantPartyJoinConcurrencyTest` 까지 들어왔다.
>
> **2026-09-17 에 둘이 더 끝났다.** ④ **PUBG 동시성 테스트**(`PubgPartyJoinConcurrencyTest`, 9건)
> — 이제 세 게임 모두 테스트가 있다. ⑤ **상태 조회** — `GET /match-requests?userId=` +
> `MatchQueryService`. 옛 표의 1·3번이 그것이라 표에서 뺐다. 같은 날 `accept-proposal.lua` 가
> `expiresAt` 을 직접 보게 되어 **만료 직후 수락이 확정되던 창도 닫혔다.**

**자세한 목록과 각 항목의 근거는 `HANDOFF.md` 의 2026-09-17 블록에 있다.** 아래는 그 요약이다.

| # | 할 일 | 시작 지점 | 왜 이 순서인가 |
|---|---|---|---|
| ~~1~~ | ~~**확정된 사용자를 파티에서 풀어 주는 경로**~~ | **2026-09-27 닫혔다 — docs/11 D-42.** `cleanup-confirmed.lua` 가 활성 요청(`status=PARTY`)에 TTL 60초(`confirmed-retention-seconds`)를 걸어 만료시킨다. 그 뒤 "한 번에 하나만" 은 `app:platform` 이 파티를 만들며 찍는 입장 표시 키가 맡는다(`claim-request.lua` 가 이미 본다). 후보 셋 가운데 3번(TTL 안전망)에 가깝지만 임시방편이 아니라 설계다 — 파티의 수명은 platform 것이고 이 앱은 확정 순간만 안다 | 사용자가 매칭을 평생 한 번만 할 수 있던 문제가 없어졌다. 남은 것은 platform 쪽 진입점(아래 3번) |
| ~~2~~ | ~~**INV-6 차단 검증 완성**~~ | **2026-09-27 닫혔다 — docs/11 D-41.** 선필터 한 겹(세 게임 모두)이 전부다. 확정 직전 동기 SELECT 는 두지 않는다. `Block.java` 는 `public.blocks` · `Long`(09-26), 로컬 H2 는 `backend/src/main/resources/schema.sql` 이 표를 만든다 | docs/11 #30 "차단 검증 없이 배포하지 않는다" 는 선필터가 실제로 도는 것으로 충족된다. 운영은 `DB_URL` 이 platform 의 Postgres 여야 한다 |
| 3 | **확정된 파티를 실제로 만드는 platform 쪽 진입점** (옛 "outbox → SQS" 는 docs/11 D-42 로 두지 않는다) | 이 앱 몫은 끝났다 — `cleanup-confirmed.lua` 가 파티 HASH 를 자기완결로 채우고(`game`/`modeKey`/`voicePreference`/`playPurpose`/`confirmedAt`, TTL 600초) `MATCH_CONFIRMED` 를 보낸다. **남은 것은 `app:platform` 의 "이 매칭으로 파티 만들기"** — 경로 · 본문 · 에러 코드 · 입장 표시 키를 찍고 지우는 때(D-36) 전부 미정(D-42 "아직 미정"). 이 저장소에서 할 일은 그 HASH 필드 이름을 바꾸지 않는 것이다 | 파티를 DB에 만드는 것은 `app:platform` 이다 (docs/11 #30 · D-42). 진입점이 생기기 전까지는 확정돼도 파티가 DB 에 없다 |
| 4 | ~~**`BlockChanged.fifo` 소비**~~ | **할 일이 아니게 됐다** — `BlockChanged.fifo` 와 Redis 선필터(`qm:block:{userId}`)는 폐기됐다 (docs/11 D-12). 계약 사본도 그렇게 고쳤다(`contracts/README.md` A-5) | 차단은 2번의 DB 직접 조회 한 겹으로 지킨다 |
| 5 | **메트릭** | `MeterRegistry` 가 `backend/src/main` 에 0건이다. actuator 는 이미 있다 | `CLAUDE.md` §6 Definition of done 4번 |
| ~~6~~ | ~~**부하 테스트 복구**~~ | **2026-09-27 에 했다** — `load-test/README.md`(쿠키 토큰 · `tier` · 티어 접미사 색인 키) | 성능 근거를 다시 잴 수 있다 |
| 7 | **계약 정리** | `openapi.yaml` 이 `OPTIONAL` · `PLAY_STYLE` · 조회 경로 · 새 모드 키를 반영하지 않는다. **본 저장소 contract 변경이 선행** (CLAUDE.md §5) | 불일치 표(`contracts/README.md`)가 길어질수록 어느 쪽이 맞는지 판단하는 비용이 는다 |
| 8 | ~~**인증(JWT)**~~ | **2026-09-27 에 했다** — `userId` 요청 필드와 `?userId=` 쿼리 파라미터를 없앴다(`MatchingController` · `ProposalController` 둘 다). "나"는 쿠키 `qm_access` 의 `sub` 다. **조회 경로를 `/match-requests/me` 로 옮기는 것은 하지 않았다** — 경로는 계약(#5)과 같이 정한다 | 계약 불일치 #2, #3 이 해소됐다(`contracts/README.md`) |

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
