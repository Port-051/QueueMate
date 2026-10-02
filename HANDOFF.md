# HANDOFF — 다음 세션 인계

**작성:** 2026-09-15 (화) 12:29 KST · **갱신:** 2026-09-17 (목) · 2026-09-19 (§0 의 ①·③·④ 에 docs/11 D-11·D-12·D-13 반영, §0-0 · ① 에 D-19 반영) · 2026-09-24 (§0-4 신설 — `platform` 쪽에서 넘어온 일. §0-1 ① 에 참조 한 줄) · 2026-09-24 두 번째 (§0-4 (나) 에 같은 날 늦게 결정된 넷을 더했다 — P-13 의 개정 · P-17 · P-14 의 개정 · P-18) · 2026-09-27 (§0-5 신설 — 임시 식별 `?userId=` 를 쿠키 `qm_access` 검증으로 바꿨다) · 2026-09-27 두 번째 (§0-4 (나) 에 ⑯ ~ ⑱ — D-38 ~ D-40. (다) 가 D-40 으로 세부가 정해졌다) · **2026-09-27 세 번째 (§0-6 신설 — D-41 · D-42 로 오늘 닫힌 것과 남은 것. §0-1 ① · ② · ③ · ⑥ · ⑦ 이 닫혔다)** · 2026-09-29 (§0-7 신설 — 플레이 목적 `NORMAL` → `TRYHARD`, D-49) · 2026-10-01 (§0-8 신설 — 제안 수락 시한 20초 → 5분, D-55) · 2026-10-01 두 번째 (§0-9 신설 — platform 이 제안 중에도 파티 HASH 를 읽는다, D-56)
**읽는 순서:** `CLAUDE.md` → `START_HERE.md` → **이 파일의 §0 부터**

이 파일은 "지금 어디까지 왔고 무엇이 열려 있는가"만 담는다. 규칙은 `CLAUDE.md`,
결정의 근거는 `docs/11_DECISION_LOG.md`(특히 **D-8**), 조사 원문과 출처는
`WORKLOG_2026-09-14.md` 에 있다. 여기서 다시 설명하지 않는다.

작업이 끝나면 이 파일을 갱신하거나, 전부 끝났으면 지워라.

---

## 0. 2026-09-17 — 남은 것 전부 (이 절만 읽고 이어갈 수 있다)

아래 §1~§5 는 그날그날의 기록이라 겹치는 곳이 있다. **겹치면 이 절이 우선한다.**

### 0-10. 2026-10-02 — 제안 만료 Lua 가 시한을 다시 본다 (했다 — `b617c6f` · 배포 점검에서 찾은 버그)

ECS 배포 점검(태스크 둘이 같이 도는 경우)에서 찾았다. `ProposalSweeper` 는 Redis 를 두 번 부른다 — 목록에서 시한 지난 partyId 를 꺼낼 때와
`expiry-proposal.lua` 를 돌릴 때. 그 사이에 옛 제안이 깨지고(남은 사람은 파티에 그대로) 빈자리가 다시 차면 같은 partyId 에 **새 제안**이 열리는데,
Lua 가 `status == 'PENDING'` 만 보고 있어 그 새 제안을 옛 것으로 알고 깼다 — 아직 아무도 수락하지 않았으니 **전원이 무응답자로 나와 통째로 큐에서 빠졌다.**
스위퍼가 하나여도 거절 · 취소 → 재충원이 그 사이에 끼면 생기고, 둘이면 같은 목록을 둘 다 들고 있어 창이 넓다.
**고친 것** — `now` 를 ARGV[2] 로 넘기고 `expiresAt > now` 면 빈 결과(목록에서도 안 뺀다 — 새 제안의 점수가 이미 미래다). `ProposalExpiryService#expire` 한 줄.
회귀 테스트 `proposal/ProposalExpiryTest`(3건). `concurrency.*` 30 · `ProposalIdempotencyTest` 16 · `RequestAliveTest` 6 통과(`REDIS_PORT=6390`).
**같은 점검에서 남은 것(아직 안 고쳤다 — 소유자가 짠다)** — ① `RequestAliveExpiryService#expire` 가 `ZREM` 결과를 안 봐, A 가 취소한 직후 다시 접수한 새 요청을
B 가 또 취소할 수 있다("score ≤ now 일 때만 ZREM" 을 Lua 로, 0 이면 중단) ② `AsyncConfig` 의 배정 풀에 종료 대기가 없어 SIGTERM 때 201 받은 요청의 배정이 버려진다
(`setWaitForTasksToCompleteOnShutdown(true)` · `setAwaitTerminationSeconds(20)`) ③ 장애 조치 때 비동기 복제로 락이 사라지면 정원 초과 가능(드묾 — `join-party*.lua` 에 `count >= target` 거절 분기 없음).

### 0-9. 2026-10-01 — `app:platform` 이 제안 중에도 파티 HASH 를 읽는다 (문서만 — docs/11 D-56)

소유자 결정 — 제안 화면부터 팀원 정보(닉네임 · 게임 프로필 — 게시판 카드 수준)를 보여 준다. platform 이 `GET /api/v1/match-parties/{partyId}/members?game=`
(platform P-47)을 만들고, 그 자격 확인으로 `qm:party:{partyId}` 를 **`PENDING` 에도 `HGETALL`** 한다(D-42 의 "확정 뒤에만" 을 넓혔다 · 쓰기 · 지우기 · `EXPIRE` 금지는 그대로).
**이 앱의 코드는 바뀌지 않았다** — 배정 스크립트가 이미 `status` · `member:{userId}` 를 쓴다. 바뀐 것은 그 둘의 이름과 값 모양이 **확정 전에도** 앱 사이의 약속이 된 것이다
(대조 테스트 없음 — `redisKeys/SharedKeys` 머리 주석 · `CLAUDE.md` §3 · `contracts/events.md` · `contracts/README.md` A-20). 제안 중 HASH 에는 `game` 이 없어 프런트가 `?game=` 을 넘긴다
(배정 때 `game` 을 적는 안은 택하지 않았다 — 소유자). 차단은 거르지 않는다(소유자).
**알아 둘 것** — PUBG 의 `member:` 값은 keyValue(플랫폼)가 아니라 `'EXIST'` 다. 계약 사본(`events.md`)이 "PUBG 플랫폼" 이라 잘못 적고 있어 같이 바로잡았다.
`../platform/contracts/platform-api.md` "파티 HASH 의 계약" 의 `member:{userId}` 행도 같은 잘못이 있다(그 폴더의 일).

### 0-8. 2026-10-01 — 제안 수락 시한을 20초에서 5분으로 늘렸다 (했다 — docs/11 D-55)

소유자 결정 — 처음엔 "20초는 좀 짧은 것 같으니 60초로", 같은 날 다시 **5분**(제안 화면에서 팀원 정보를 보고 고를 시간 — 그 화면 작업은 따로다).
**바뀐 것은 기본값 하나** — `application.yaml` 의 `queuemate.proposal.ttl-seconds`(`PROPOSAL_TTL_SECONDS`) `20` → `300`. 만료 스위퍼 · `accept-proposal.lua` 의
시한 검사 · `expiresAt` 계산(세 게임의 `*Assigner`)은 이 값을 읽으므로 코드 변경이 없다. 테스트는 기본값을 베낀 `ProposalIdempotencyTest.TTL_MILLIS` 하나만 맞췄다.
프런트는 서버가 준 `expiresAt` 으로 남은 시간을 그린다(하드코딩 없음). matching 대기 접속 확인의 유예 90초와는 부딪히지 않는다 — 프런트가 `PROPOSED` 동안에도 30초마다 신호를 보낸다.
**남은 것(다른 폴더의 일)** — `../notification/backend/src/main/resources/application.yaml` 의 재접속 대기 주석이 "제안 수명이 20초다" 라고 적고 있다(값은 그대로 맞고 주석만 낡았다).
`../frontend` 의 `InlineProposal.tsx` 는 남은 시간을 초 숫자 하나로 그린다(5분이면 `300` 초부터 — 모양은 그 폴더가 정한다).
**감수** — D-41 이 감수한 창("같은 파티에 들어온 뒤 차단")이 20초에서 5분으로 넓어졌다. **제안 중에 matching 대기 접속 확인(`POST /match-requests/heartbeat`)이 90초 끊긴 사람은 수락했더라도 나간 것으로 처리한다**(소유자 결정 — 창을 닫았든 다른 앱으로 가 브라우저가 멈췄든). 그 순간 제안이 깨지고
다른 사람의 수락 기록도 지워지며 남은 사람은 `MATCH_CANCELLED` 를 받는다. 20초였을 때는 확정 · 만료가 먼저 와서 생기지 않던 갈래다(D-55 감수 — 고르지 않은 대안 셋도 거기).

### 0-7. 2026-09-29 — 플레이 목적 `NORMAL` 을 `TRYHARD`(빡겜)로 바꿨다 (했다 — docs/11 D-49)

소유자 결정 — 화면의 "일반 플레이" 를 "빡겜" 으로, enum 까지. 값 이름 `TRYHARD` 는 Claude 가 정했다(검토 항목). 뜻도 "진지하게 한다" 로 바뀌었다.
**이 앱에서 바뀐 것** — `domain/condition/PlayPurpose` 한 줄. 색인 키 · 락 키(`SharedKeys#poolKey` ← `{Lol,Pubg,Valorant}PartyKeys` 의 `name()`) ·
활성 요청 · 파티 HASH 의 `playPurpose` 는 enum 을 따라 저절로 바뀐다 — 문자열로 박힌 자리가 없었다(Lua 는 `cleanup-confirmed.lua` 주석 하나,
`load-test/` · `redis-ha-lab/` 은 `RANK_UP` 만 쓴다). 테스트 `web/PlayPurposeApiTest`(2건) · 전체 100건 통과(`REDIS_PORT=6390`). 계약 A-19.
**남은 것(다른 폴더의 일)** — `../frontend`(타입 · 라벨 · 기본값 `playPurpose: 'NORMAL'` · 저장해 둔 옛 값을 새 값으로 옮겨 읽기) ·
`../platform`(코드 변경 없음 — `MatchParty.java` 주석 · `contracts/platform-api.md` 의 값 목록만 낡는다).
**배포할 때** — Redis 에 `NORMAL` 로 남은 대기 요청은 새 코드로 취소도 거둬 가기도 안 된다(D-49 "감수하는 것" — 실험으로 확인). 운영 뒤라면 대기열을 비우고 배포한다.

### 0-6. 2026-09-27 — 오늘 닫힌 것과 남은 것 (§0-1 · §0-2 · §3 보다 이 절이 우선한다)

**닫힘.**
- **§0-1 ① 확정된 사용자 갇힘 → docs/11 D-42.** 활성 요청(`status=PARTY`)과 수락자 SET 은 `confirmed-retention-seconds`(60초) 뒤 만료되고,
  파티 HASH 는 `confirmed-party-ttl-seconds`(600초). 그 뒤 "한 번에 하나만" 은 platform 의 입장 표시 키가 맡는다. **platform 쪽 진입점은 같은 날 구현됐다** —
  `POST /api/v1/match-parties/{partyId}/room`(`../platform` P-30).
- **§0-1 ② INV-6 → 선필터 한 겹으로 확정(D-41).** 확정 직전 동기 SELECT 는 두지 않는다. 로컬 H2 에서 `blocks` 가 없어 배정이 조용히 실패하던 것은
  `backend/src/main/resources/schema.sql`(`spring.sql.init.mode: embedded`)로 해소됐다. 운영은 platform 의 `public.blocks` 그대로.
- **§0-1 ③ outbox → 두지 않는다(D-42).** Flyway + `matching_outbox` 를 넣었다가(`7ac6209` · `55236da` · `cef2c7b`) 같은 날 revert 했다. 파티는
  `cleanup-confirmed.lua` 가 파티 HASH 에 `game` / `modeKey` / `voicePreference` / `playPurpose` / `confirmedAt` 을 채워 두면 **platform 이 그것을 읽어**
  만든다. **HASH 필드 이름이 계약이다** — `contracts/events.md` SQS 절.
- **§0-1 ⑥ 부하 테스트 → 복구됨.** `load-test/README.md`(2026-09-27 API 기준 — 쿠키 토큰 · `tier` · 티어 접미사 붙은 색인 키).
- **§0-1 ⑦ 계약 → 사본을 구현에 맞췄다** — `contracts/README.md` A-13(openapi 전반) · A-14(수락 · 거절) · A-15(SQS 큐 0개, 파티 HASH 필드 계약).
- **3-1 색인 복원 버그 → 고쳤다** — 정원이 찼다 풀린 파티를 남은 사람이 맡지 않은 줄 **전부**에 되돌린다(LoL · VALORANT, `78f5c3f`, 회귀 테스트 포함).
- **3-3 거절의 `requestId` → 서버가 읽는다.** `POST /proposals/{id}/decline` 은 쿼리 파라미터를 받지 않고 활성 요청 HASH 에서 읽는다(A-14).
- **§0-2 "접수 응답이 DTO 를 안 쓴다" → 고쳤다.** `POST /match-requests` 201 이 `MatchRequestResponse.queued(requestId, queuedAt)` 를 돌려준다(README ~~#5-1~~).

**남음.**
- **§0-1 ⑤ 메트릭** — 여전히 0건.
- **3-2 확정 뒤 취소 가드** — D-42 로 **별도 가드를 두지 않는다.** TTL 60초 안에서만 취소가 뜻이 있고, 그 뒤엔 방 나가기가 곧 파티 나가기다.
- **platform 쪽** — 진입점은 됐다(`POST /api/v1/match-parties/{partyId}/room`, P-30). 2026-09-28 에 둘이 정해졌다 — 말없이 사라진 자동 매칭
  파티는 그 게임의 게시판 목록 GET 이 닫고, `playPurpose` 는 `parties` 에 담지 않는다. `PARTY_*` 알림도 2026-09-28 에 두지 않기로 했다(D-44). platform 몫의 미정은 P-30 의 소유자 검토와 강퇴당한 파티원의 재입장(게시판 방은 2026-09-29 P-32 로 막았지만 이 방은 아니다 — P-30 "미정")이 남는다.
- **계약 이름 결정** — `MatchRequestView` 의 `requestId` vs `id` · `partyId` vs `proposalId` · epoch millis vs `date-time`(README #4), 조회 경로를
  `/match-requests/me` 로 옮길지(#5). 원본(queueMate 본 저장소)과 같이 정한다.
- **untiered `join-party.lua` 의 파티 존재 가드 없음** — 찾기와 합류 사이에 마지막 멤버가 취소하면 `HSET` 이 파티를 되살린다. 확률이 극히 낮아 그대로 둔다(§3-D).
- ~~**접속 확인(heartbeat)으로 대기 요청 거두기 — 소유자가 직접 구현한다** (docs/11 **D-43**, 2026-09-27)~~ → **됐다(2026-09-28).** 소유자가 구현하고 Claude 가 일부를 고쳤다 —
  `POST /api/v1/match-requests/heartbeat`(204 / 404 `MATCH_REQUEST_NOT_FOUND`) · ZSET `qm:request:alive`(score = 시한) · `claim-request.lua` `KEYS[3]` 의 첫 `ZADD` ·
  `HeartBeatService` · `RequestAliveSweeper`(5초) · `RequestAliveExpiryService`(`ZREM` 먼저, 확정된 요청은 건너뜀, 아니면 `MatchCancelService#cancel`) · 유예 90초.
  **끝난 요청은 게으르게 지운다 — Lua 에 `ZREM` 이 없다**(D-43 첫 초안과 다른 점). 닫기 신호는 두지 않는다. 테스트 `alive/RequestAliveTest`.
  D-43 의 "미정" · `CLAUDE.md` §3 · §4(Lua 표 · "`qm:request:alive` ZSET의 수명" · 회귀 테스트 표) · `contracts/README.md` A-16 · `openapi.yaml` · `START_HERE.md` · `load-test/README.md` 를 같이 고쳤다.
  **남은 것은 없다.** 단 하나 기억할 것 — `load-test/` 스크립트는 신호를 보내지 않으므로 **부하 테스트는 앱을 `ALIVE_GRACE_MS=3600000` 으로 띄운다**(`load-test/README.md` "접속 확인(heartbeat)과 부하 테스트"). 안 그러면 적재한 대기자가 90초 뒤 전부 빠진다.

**환경 함정 한 줄.** 이 zsh 에서 `/dev/tcp` 로 포트를 확인하면 **항상 "닫힘"으로 나온다** — python `socket` 으로 확인해라. 테스트 Redis 는
`docker.exe run -d --rm --name qm-matching-test-redis -p 6390:6379 redis:7-alpine`, 테스트는 `REDIS_PORT=6390`.

### 0-0. 방금 들어온 것 (문서가 "미구현"이라 적고 있던 것들)

| 무엇 | 어디 | 비고 |
|---|---|---|
| **매칭 요청 상태 조회** | `controller/MatchingController#getMatchRequest` · `service/MatchQueryService` | **경로가 계약과 다르다** — 계약 `GET /match-requests/{requestId}` vs 구현 `GET /match-requests?userId=`. 경로 변수가 없다. 활성 요청이 `qm:user:active-request:{userId}` 로 **사용자 단위** 저장이라(INV-1) requestId 는 찾는 열쇠가 아니고, 이 조회가 가장 필요한 순간(페이지 새로 열기)에 클라이언트는 requestId 를 잃은 상태다. **contract 변경이 필요한 사안이다**(CLAUDE.md §5, 아래 0-1 ⑦). JWT 가 붙으면 `/match-requests/me` 가 된다(← **2026-09-27 에 JWT 는 붙었고 `?userId=` 는 없어졌지만 경로는 `GET /match-requests` 그대로다** — §0-5) |
| 응답 DTO 확장 | `dto/MatchRequestResponse` | **record 로 바뀌고 8필드**가 됐다 — `{status, requestId, queuedAt, partyId, target, memberCount, expiresAt, isAccepted}`. `@JsonInclude(NON_NULL)` 이라 그 갈래에서 뜻이 없는 칸은 응답에서 빠진다. 정적 팩토리 `idle`/`queued`/`proposed`/`matched` 로 만든다 |
| `MatchRequestStatus.IDLE` | `domain/MatchRequestStatus` | 갈래는 `IDLE`(활성 요청 없음) / `QUEUED` / `PROPOSED` / `MATCHED`. **`CANCELLED`·`EXPIRED` 는 enum 에만 있고 조회가 절대 돌려주지 않는다** — 취소·만료는 활성 요청 키를 지우므로 `IDLE` 과 구분되지 않는다 |
| `accept-proposal.lua` 가 시한을 본다 | `redis/proposal/accept-proposal.lua` | `ARGV[3] = now` 를 받아 `expiresAt <= now` 면 `NOT_FOUND`. **INV-5 expired 의 "스위퍼 주기만큼 남던 창"이 닫혔다.** 흔적 지우기는 여전히 스위퍼 몫이다(만료 알림이 거기서 나간다) |
| 도메인 패키지 정리 | `domain/condition/` 신설 | `KeyConditionType`·`VoicePreference`·`PlayPurpose` + `condition/lol/LolPosition` · `condition/valorant/ValorantRole` · `condition/pubg/`(빈 디렉터리 — PUBG 핵심 조건은 플랫폼 문자열이라 enum 이 없다). `GameKey` 는 조건이 아니라 갈래라 `domain/` 에 남았고 `ActiveRequest`·`CancelResult`·`MatchRequestStatus`·`ProposalResult` 도 남았다 |
| Redis 키 통합 | `redisKeys/SharedKeys` 신설 | 자바 쪽 키 문자열의 단일 출처. 게임별 `*PartyKeys` 는 남아서 조각을 조합한다. **Lua 안의 같은 문자열은 그대로다** — 어느 스크립트에 박혀 있는지가 `SharedKeys` 클래스 주석에 목록으로 있다 |
| 활성 요청에 `queuedAt` | `service/MatchRequestService#requestFields()` | 줄 선 시각(epoch millis). **Lua 가 아니라 자바가 필드로 넘긴다** |
| 취소 스크립트 정리 | `{lol,pubg,valorant}/leave-party.lua` | 중복 정리 2줄 제거. 동작 무변경 |
| **PUBG 동시성 테스트 9건** | `concurrency/PubgPartyJoinConcurrencyTest` | **이제 세 게임 모두 테스트가 있다.** 총 41건 — 동시성 24(LoL 7 + VALORANT 8 + PUBG 9) + 제안 멱등성 11 + 알림 6 |
| **방에 있으면 매칭 거절 — 409 `IN_ROOM`** (2026-09-19, docs/11 D-19) | `redis/shared/claim-request.lua`(`KEYS[2]`, 반환 `-1`) · `redisKeys/SharedKeys`(`ACTIVE_ROOM_PREFIX` · `activeRoomKey`) · `dto/JoinResult` · `service/MatchRequestService#join()` · `controller/MatchingController` | "한 번에 하나만"을 **키 둘**로 지킨다 — `app:room` 의 입장 표시 키 `qm:user:active-room:{userId}` 를 `EXISTS` 로 보기만 한다. 활성 요청 키는 다시 이 앱만 쓴다. `join()` 의 반환이 `Optional<AcceptedRequest>` 에서 `JoinResult` 로 바뀌었다. `ActiveRequestConcurrencyTest` 에 3건이 붙어 `@Test` 개수로 동시성 27 · 총 44건이다(윗줄의 41건은 09-17 기록이라 그대로 둔다). 계약 사본은 `contracts/README.md` A-10 |

### 0-5. 2026-09-27 — 임시 식별 `?userId=` 를 쿠키 `qm_access` 검증으로 바꿨다 (했다)

소유자 지시. **`platform` 의 인증 세부(docs/11 D-24 — 옛 §0-4 (나) ③)를 이 앱에 적용한 것이다.** `platform` 이 말하는 "옆 서비스의 전환"
(`../platform/CLAUDE.md` §5.1 (아))의 `matching` 몫이다 — **했다.** (`notification` 은 그 폴더의 일이다.)

- **"나"는 access 토큰의 `sub` 다** — 쿠키 `qm_access` 의 RS256 JWT 를 `platform` 의 **공개 키로 검증만** 한다. 서명 · `exp` · `iss`(`queuemate-platform`) ·
  **`token_use == access`** · **`sub` 가 숫자 문자열**. 어긋나면 401 `UNAUTHENTICATED`. 코드는 `backend/src/main/java/com/queuemate/common/security/`.
- **없어진 것** — `GET /match-requests?userId=` · `DELETE /match-requests/{id}?userId=` · `POST /proposals/{id}/accept?userId=` ·
  `POST /proposals/{id}/decline?userId=`(`?requestId=` 는 남았다) · `POST /match-requests` 바디의 `userId`(보내도 무시된다 — `@JsonIgnore`).
  **개발용 `?userId=` 스위치는 두지 않았다.** 엔진 안(Redis 키 · Lua · 알림 채널)의 `userId` 는 문자열 그대로라 바뀐 것이 없다.
- **CSRF** — 상태를 바꾸는 요청의 `Origin` 을 허용 목록(`ALLOWED_ORIGINS`)과 대조한다(403 `ORIGIN_NOT_ALLOWED`. `Origin` 이 없으면 통과).
- **새 환경변수** — `JWT_PUBLIC_KEY`(X.509 PEM) · `JWT_PUBLIC_KEY_FILE`(기본값 `../../platform/backend/.dev-keys/public.pem` — `backend/` 기준) ·
  `ALLOWED_ORIGINS`. **공개 키가 없으면 기동하지 않는다.** 인증 없이 열린 것은 `/actuator/**` 뿐이다.
- **테스트** — `web/AuthenticationApiTest`(21건) · `common/security/JwtPublicKeysTest`(5건). 테스트 키 쌍은 JVM 마다 새로 만들고
  (`TestJwt`), 공개 키는 `META-INF/spring.factories` 의 `TestJwtKeyInitializer` 가 모든 테스트 컨텍스트에 넣는다 — 그래서 테스트에는 `platform` 의 키가 필요 없다.
  전체 71건 통과(2026-09-27, `REDIS_PORT=6390`).
- **남은 것** — ① 조회 경로를 `/match-requests/me` 로 옮길지(계약 #5 와 같이 정한다 — 이번에는 안 옮겼다) ② `load-test/` 가 아직 바디에 `userId` 를 싣고
  쿠키가 없다 — 그대로는 전부 401 이다(아래 0-1 ⑥ 과 같이 고친다) ③ docs/11 에 "D-24 를 matching 에 적용했다" 를 남길지는 결정 로그 쪽 일이다.

### 0-4. 2026-09-24 — `platform` 쪽에서 넘어온 일 (아직 하나도 안 했다)

> **번호와 자리가 어긋난다** — `0-2` · `0-3` 이 이미 쓰여 **다음 빈 번호**를 붙였고, 자리는 (가)가 급해서 §0-1 앞이다.

소유자 지시로 적는다. **`platform` 폴더에서 소유자가 직접 정한 것이 결정 로그(`docs/11_DECISION_LOG.md`)에 하나도 안 올라갔고,
그 가운데 하나는 이 저장소의 코드를 지금 깨뜨리고 있다.** 원본은 **`../platform/contracts/platform-api.md`** 맨 아래
"원본에 올려야 할 것" 표(**P-11 ~ P-18**) · `../platform/CLAUDE.md` · `../platform/START_HERE.md` §4 다.
**`docs/11_DECISION_LOG.md` 의 마지막 항목은 D-23 이다 — 새로 남길 것은 D-24 부터다.**

성질이 셋으로 갈린다 — **(가) 코드를 고치는 일** · **(나) 결정 로그에 남기는 일** · **(다) 아직 미정인 것.** 섞지 마라.
**이 절은 "해야 할 일"만 적는다** — 여기서 결정을 새로 하지도, D-항목의 문안을 완성하지도 않는다.

#### (가) 코드 — `block/Block.java` 의 두 칸을 `Long` 으로 바꾼다 **(가장 급하다)**

> **2026-09-26 에 했다** — ① 로 맞췄다(엔티티 · 리포지토리만 `Long`, 부르는 자리에서 문자열로 되돌린다 — `block/BlockedUsers#of`. 숫자가 아닌 `userId` 는 빈 집합 + WARN). `schema` 도 뺐고 테스트 `schema.sql` 도 `public` · `bigint` 로 맞췄다. 아래 표는 기록으로 둔다.

**안 하면: 운영 DB 에 붙는 순간 차단 조회가 깨져 INV-6 선필터가 통째로 죽는다.** 배정은 `@Async` 안이라
**요청은 201 로 나가고 배정만 조용히 실패한다** — §0-1 ② 의 "스키마가 없어 조회가 터진다"와 증상이 같다.

- **2026-09-22 소유자 결정**으로 `platform` 이 **모든 테이블의 PK 를 `bigint GENERATED ALWAYS AS IDENTITY`** 로 하고
  **사용자의 식별자를 둘로 갈랐다** — `account.users.id`(사용자 번호, bigint)가 `userId` 이고, 가입·로그인에 쓰는
  **로그인 아이디는 `login_id` · `loginId` 로 따로**다. 그래서 **`social.blocks.blocker_id` · `blocked_id` 가
  `varchar(20)` 에서 `bigint` 가 됐다** (확인함 — `../platform/backend/src/main/resources/db/migration/social/V4__social_blocks.sql`.
  그 파일 머리 주석이 "`matching` 의 `Block.java` 는 아직 String 이다 — 그쪽을 `Long` 으로 같이 바꿔야 한다.
  바꾸기 전까지 `matching` 은 이 테이블을 읽다가 런타임에 깨진다"고 적어 두었다).
- 이 앱은 그 테이블을 **직접 읽는다**(D-1 · D-2 — 뷰가 아니라 `social.blocks` 동기 조회로 INV-6 을 지킨다).
- **이것은 D-4 를 개정한다.** D-4 는 `blockerId`/`blockedId` 를 `String` 으로 두면서 근거로 **"사용자 id 타입을 `String` 으로
  통일한다 — 요청의 `userId` 가 `String` 이라 변환 지점이 생기지 않는다"**를 들었다. 그 근거가 뒤집힌 것이고,
  **이제는 변환 지점이 생긴다**(아래 표의 `findBlockedUserIds` 줄).
- **이 앱을 통째로 `Long` 으로 바꾸라는 뜻이 아니다 — `block` 패키지만이다.** Redis 키 · 요청 파라미터 · DTO ·
  파티 HASH 의 `member:{userId}` 필드는 **문자열 그대로여도 된다.** `platform` 이 JWT 의 `sub` 와 요청·응답 본문에
  **숫자를 십진 문자열로** 찍기 때문이다(`"42"`) — `../platform/contracts/platform-api.md` "공통" · P-11 이
  "`matching` · `notification` · `room` 은 그 값을 문자열로 다뤄 **코드 변경이 없다**"고 적었다.

**같이 고쳐야 하는 자리** (`grep` 으로 확인한 것. **이번에는 고치지 않았다 — 목록만이다**).

| 파일 | 자리 | 무엇 |
|---|---|---|
| `block/Block.java` | `:43` `:47` | `private String blockerId` · `blockedId` → `Long`. javadoc 의 **"이 테이블만 `matching` 롤에 SELECT 권한을 준다 (D-1)"** 도 같이 낡았다 — 롤은 두지 않는다(아래 (나) ①) |
| `block/Block.java` | `:32` | **`@Table(schema = "social", name = "blocks")` 의 `schema` 를 뺀다** — 2026-09-26 소유자 결정으로 `platform` 이 스키마 셋을 `public` 하나로 합쳤다(아래 (나) ⑫). 테이블은 `public.blocks` 이고 컬럼은 그대로다. **안 빼면 `Long` 으로 고쳐도 "relation social.blocks does not exist" 로 똑같이 깨진다.** 마이그레이션 원본도 `social/V4__social_blocks.sql` 이 아니라 **`db/migration/V1__schema.sql` 하나**가 됐다(위 문단의 경로는 낡았다) |
| `block/BlockRepository.java` | `:27` `:42` `:59` | 세 메서드의 파라미터·반환 타입 — `isBlocked(String, String)` · `findBlocksAmong(String, Collection<String>)` · **`List<String> findBlockedUserIds(String)`**. JPQL 본문은 필드 이름만 쓰므로 그대로다 |
| `rule/lol/LolCandidateRule.java` · `rule/pubg/PubgCandidateRule.java` · `rule/valorant/ValorantCandidateRule.java` | `:54` · `:41` · `:52` | 셋 다 `Set.copyOf(blockRepository.findBlockedUserIds(command.getUserId()))` 다. `command.getUserId()` 는 `String` 이고 결과를 `Set<String>` 으로 받는다 — **여기가 변환 지점이다** |
| `rule/ScriptSupport.java` | `:80` `blockedWith(List<String> memberIds, Set<String> blockedUserIds)` | `memberIds` 는 파티 HASH 의 `member:` 필드에서 잘라 낸 **문자열**이다(`memberIds()` — `:67`~`:77`). 차단 목록만 `Long` 으로 올리면 `contains` 가 **영원히 false** 다 — **컴파일도 테스트도 통과한 채 차단이 조용히 안 걸린다** |
| `rule/{lol,pubg,valorant}/{Tiered,Untiered}Assigner` | 각 `:84` ~ `:95` | `blockedWith(memberIds, blockedUserIds)` 호출 **6곳**이 위 타입을 따라간다 |
| `backend/src/test/resources/schema.sql` | `:12` `:13` | `blocker_id varchar(255)` · `blocked_id varchar(255)` → **`bigint`.** 테스트 H2 가 운영과 다른 타입이면 **이 변경이 깨져도 테스트가 못 잡는다** |

- **어느 쪽으로 맞출지는 정하지 않았다** — ① 엔티티만 `Long` 으로 바꾸고 조회 결과를 부르는 자리에서 문자열로 되돌리는 길
  ② `userId` 를 다루는 자리까지 `Long` 으로 올리는 길. **고르는 것은 코드를 만질 때다.**
- 곁딸린 것 — `isBlocked` · `findBlocksAmong` 은 **아직 아무도 부르지 않는다**(`grep` 0건). §0-1 ② 의 "확정 직전 최종 검증이 없다"가 그것이다.
- **§0-1 ② 의 "선필터가 LoL 에만 있다"는 낡았다** — `PubgCandidateRule` · `ValorantCandidateRule` 도 같은 조회를 부른다(위 표). 그래서 타입을 바꿀 자리도 셋이다.

#### (나) 문서 — docs/11 에 D-항목으로 남겨야 하는 것 열

> **2026-09-26 — 남겼다.** 아래 ① ~ ⑬ 를 `docs/11_DECISION_LOG.md` 의 **D-24 ~ D-34** 로 올렸다 — ③ → D-24 · ② → D-25 · ④ → D-26 ·
> ⑤ 앞쪽 + ⑦ → D-27 · ⑤ 뒤쪽 + ⑨ → D-28(표에 없던 P-20 보존 기간 · P-21 `game` 필수도 여기 접었다) · ⑥ → D-29 · ⑧ → D-30 · ⑩ → D-31 ·
> (표에 없던 P-19 "방에 다른 사람이 있으면 글을 못 고친다") → D-32 · ⑪ → D-33 · ① + ⑫ → D-34 · ⑬ → D-35(2026-09-26 — 소셜 로그인만) · ⑭ → D-36(파티 닫힘) · ⑮ → D-37(LoL 계정은 Riot 에서 티어 — 포지션은 같은 날 되물렸다) · **⑯ → D-38(소셜 계정 잇기 · 끊기) · ⑰ → D-39(글에서 `purpose` 를 없앤다) · ⑱ → D-40(자동 매칭이 게시판 방에 먼저 합류하는 길 — 세부 넷, 결정만)**(2026-09-27). 개정된 옛 항목(#15 · #16 · #17 · D-1 · D-3 · D-4 · D-9 · D-11 ·
> D-14 · D-16 · D-19 ~ D-23 — 2026-09-27 에 D-11 · D-28 · D-29 · D-32 · D-35 도)의 머리에 "낡음" 표시를 달고 파일 머리의 "낡은 항목 주의"에 한 덩어리로 더했다. **(가)의 코드는 아직이다.** 아래 표는 그날의 기록으로 둔다.

**안 하면: 결정 로그가 시스템의 원본인데 거기 없는 결정이 네 서비스의 코드에 들어가 있다.** 다음 사람이 #15 · #16 · #17 · D-1 · D-4 · **D-20** 을
그대로 읽고 지금 코드가 규칙을 어겼다고 판단한다(**D-20 의 ③ 은 이미 낡았다** — 아래 ⑩). **(가)를 고쳐야 하는 근거도 로그에 없다.**

전부 **소유자가 직접 정했고** 결정 로그에 항목이 없다. **무엇을 남겨야 하는지와 무엇을 개정하는지만** 적는다 — 문안은 그 파일에서 쓴다.

| # | 무엇 | 언제 | 개정하는 것 | `platform` 쪽 출처 |
|---|---|---|---|---|
| ① | **스키마별 DB 롤을 두지 않는다** — 앱 하나가 롤 하나로 붙는다. `qm_matching` 롤도 `GRANT` 도 없다. **스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다**(**← 이 대목은 2026-09-26 에 낡았다 — 아래 ⑫**) | 2026-09-22 | **#17 의 "스키마별 DB 롤" 대목**과 **D-1 의 GRANT**(`matching` 롤에 `social.blocks` SELECT) | `../platform/CLAUDE.md` §3.5 · `../platform/contracts/platform-api.md` "차단" |
| ② | **모든 PK 를 `bigint identity` 로 하고 `userId`(사용자 번호)와 `loginId` 를 갈랐다** | 2026-09-22 | **2026-09-19 의 "사용자 id 는 로그인 아이디(문자열)"**와 **D-4** | P-11 · `../platform/CLAUDE.md` §3.5 |
| ③ | **인증 세부** — RS256(서명은 `platform` 만 하고 옆 셋은 공개 키로 검증만 · 공개 키는 환경변수 · **JWKS 엔드포인트를 두지 않는다**) · CSRF 는 `SameSite=Lax` + `Origin` 검사(**CSRF 토큰을 쓰지 않는다**) · **access denylist 를 두지 않는다** · `token_use` 클레임 | 2026-09-21 | **#16 의 "Redis denylist, 조회 실패 시 fail-closed"** | `../platform/CLAUDE.md` §5.1 · P-2 |
| ④ | **refresh 토큰** — access `PT15M` · refresh `P7D` · 불투명 UUID 를 Redis `qm:auth:refresh:{uuid}` 에 두고 `GETDEL` 한 번으로 rotation | 2026-09-23 | **#16 의 같은 묶음**(③ 과 함께 본다) | P-15 · `../platform/CLAUDE.md` §5.1 (라) · (마) |
| ⑤ | **LoL 전적 동기화**(Riot API · 긁는 시점 둘 · 신선도 30분 · 비동기이고 실패해도 본 요청은 성공)와 **게시판 목록의 커서 페이지 나누기** — **둘은 별개의 결정이다.** 앞의 "시점 둘 · 신선도 30분" 과 뒤의 정렬 · 커서는 **하루 뒤에 ⑦ · ⑨ 가 각각 개정했다 — 올릴 때 같이 적는다** | 2026-09-23 | (개정이 아니라 새로 정한 것) | P-13 · P-14 |
| ⑥ | **`platform` 이 `qm:gameconfig:*` 를 읽어 `mode` · `tier` 를 검증한다** | 2026-09-24 | **#15**("gameconfig 는 `app:matching` 의 모듈이다")와 **`platform` 자기 규칙의 "`qm:gameconfig:*` 접근 — 예외가 없다"** | P-16 · `../platform/CLAUDE.md` §3.6 |
| ⑦ | **모집 글을 쓸 때는 전적을 긁지 않는다** — 전날 정한 "긁는 시점 둘" 의 절반을 하루 뒤에 되물렸다. 그 시점만 보던 **신선도 30분**(`platform.riot.freshness`)도 같이 없어졌다. 왜 — 긁는 것이 **비동기라 그 글쓰기 응답에 반영되지도 않으면서** 대가가 **Riot 호출 21번**이다(개발용 키의 한도가 2분에 100회다) | 2026-09-24 | **⑤ 의 앞쪽**(**P-13 의 개정이다 — `platform` 이 새 번호를 두지 않았다**) | P-13 · `../platform/START_HERE.md` §1 · `../platform/contracts/platform-api.md` "전적을 긁는 것" |
| ⑧ | **"전적 갱신" 요청을 두었다** — `POST /api/v1/users/me/game-accounts/{game}/refresh`. 사용자가 원할 때 부르는 **동기** 요청이고 **쿨타임 2분** · **상한 30초**다. ⑦ 로 전적이 낡은 채 남게 된 것을 사용자가 직접 갱신하는 길이라 **긁는 시점이 다시 둘이 됐다**(게임 계정을 연결 · 수정할 때 · 이 요청) | 2026-09-24 | (개정이 아니라 새로 정한 것 — **⑦ 을 뒤집지 않는다**) | P-17 · `../platform/START_HERE.md` §1 · `../platform/contracts/platform-api.md` "전적 갱신" |
| ⑨ | **게시판 목록의 정렬과 커서를 `id` 하나로 했다** — 정렬은 **`id` 내림차순 하나(= 최신순)**, 커서는 **글 번호 하나**다. 왜 — 옛 정렬 `(모집 중인가, created_at DESC, id)` 의 "모집 중인가" 가 **변하고 그것도 목록 조회 자신이 바꿔서**(방이 사라진 글을 그 자리에서 만료로 옮겨 적는다) **1쪽에 나간 글이 2쪽에 또 나왔다.** `id` 가 identity 라 **순증가 · 유일 · 불변**이어서 혼자 족하다 — `created_at` 도 정렬에서 뺐다(컬럼과 응답의 `createdAt` 은 그대로다) | 2026-09-24 | **⑤ 의 뒤쪽**(**P-14 의 개정이다 — `platform` 이 새 번호를 두지 않았다**) | P-14 · `../platform/START_HERE.md` §1 · `../platform/contracts/platform-api.md` "목록의 정렬" |
| ⑩ **← 넷 가운데 이것만 이미 있는 D-항목을 고친다** | **글 한 줄에서 `filledPositions` 를 없앴다** — "글의 찾는 포지션 가운데 이미 방 안에 있는 포지션의 강조" 다. 왜 — **주 포지션은 "내가 주로 하는 것" 이지 "이 방에서 할 것" 이 아니다**(주 포지션이 정글인 사람이 미드를 구하는 방에 미드로 들어와도 미드가 비었다고 표시했다 — 틀린 정보다). **글의 `wantedPositions` 와 카드의 주 포지션은 그대로다** | 2026-09-24 | **D-20 의 ③** — **D-20 의 ①(인원) · ②(방 안 사람들의 카드) · ④(F5 없이 갱신)는 그대로 유효하다** | P-18 · `../platform/START_HERE.md` §1 · `../platform/contracts/platform-api.md` "글 한 줄" |
| ⑪ | **`room` 앱을 `platform` 에 합쳤다** — 방 안의 일(입장 · 나가기 · 강퇴 · 확정 · 접속 확인 · 시그널 · `ROOM_*` 알림)이 `platform` 의 `room` 패키지가 됐다. 포트 8083 · 입장권 · `room_seen_at` · 방 만들기 요청 · `POST …/posts/{id}/confirm` 이 없어졌고, **글 쓰기가 방을 같이 만들고 방장 확정은 `POST /api/v1/rooms/{roomId}/confirm` 한 요청이 Redis 와 DB 를 같이 쓴다.** 왜 — 목록을 그릴 때마다 두 앱이 서로의 상태를 읽어야 했고(chatty) 확정 · 방 키 · 입장권을 같이 바꿔야 했다(design-time coupling). **이 폴더와의 키 약속(D-19)은 그대로다** — 활성 요청 키는 이 앱이 쓰고 `platform` 은 `EXISTS` 만, 입장 표시 키는 `platform` 이 쓰고 이 앱은 `EXISTS` 만. `notification` · `matching` 은 그대로 따로 둔다 | 2026-09-25 | **D-16 · D-19 ~ D-23 이 전부 "두 앱" 을 전제로 쓰였다** — `app:room` 이라는 배포 단위가 없어졌으므로 그 항목들의 "room 이 … platform 이 …" 를 "platform 의 room 패키지가 … party 패키지가 …" 로 개정한다. D-9 의 `app:platform` 서술도 다시 맞는다 | P-22 · `../platform/CLAUDE.md` §3.3 · `../platform/contracts/platform-api.md` "방" |
| ⑫ **← 이 폴더에 직접 걸린다(위 (가))** | **DB 스키마 셋(`account` · `social` · `party`)을 `public` 하나로 합치고 크로스 스키마 JOIN · FK 금지를 풀었다.** 사용자 번호를 담는 칸 전부에 `users(id)` FK(`ON DELETE CASCADE`)가 걸렸다. 마이그레이션은 `V1__schema.sql` 하나로 다시 썼다(운영 DB 가 없고 로컬 · 테스트 DB 는 `--rm` 컨테이너라 매번 빈 채로 뜬다). 왜 — DB 를 보는 앱이 사실상 `platform` 하나인데(이 앱이 `blocks` 를 읽는 것 하나뿐) 스키마를 나누고 JOIN · FK 를 금지한 탓에 닉네임을 따로 읽어 자바에서 정렬하고 사용자 존재를 앱이 확인하는 등 코드가 쓸데없이 복잡했다. **이 앱이 읽는 테이블이 `blocks` 하나라는 약속은 그대로다** — 이름이 `social.blocks` 에서 `public.blocks` 로 바뀌었을 뿐이다 | 2026-09-26 | **#17 의 schema-per-service 전체**와 **D-1 의 GRANT** · **위 ① 의 "스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다"**(스키마가 하나가 되며 롤 이야기는 물음째 없어졌다) · `docs/WHY_POSTGRESQL.md` §3 의 스키마 배치 | P-23 · `../platform/CLAUDE.md` §3.5 |
| ⑬ | **가입 · 로그인은 소셜(카카오 · 디스코드)뿐 — 직접 가입 · 비밀번호 · `loginId` · 로그인 실패 제한을 없앴다.** 식별자는 사용자 번호 하나, 이름은 닉네임 하나. 이 저장소에는 코드로 걸리지 않는다(`sub` 는 그대로 숫자 문자열) | 2026-09-26 | **D-25 의 `loginId` 절반 · D-24 의 로그인 실패 제한** | P-24 · `../platform/CLAUDE.md` §2 "계정" |
| ⑭ | **게시판 파티는 확정된 방이 없어질 때 닫히고 그 순간 최근 함께한 사람을 적는다.** `PartyClosed.fifo` 는 게시판 파티에 필요 없어졌다(자동 매칭 파티는 6단계). 이 저장소에는 코드로 걸리지 않는다 | 2026-09-26 | **D-13 의 절반 · #21 의 `PartyClosed.fifo`** | P-25 · `../platform/CLAUDE.md` §3.3 |
| ⑮ | **LoL 게임 계정의 티어를 Riot 에서 채운다(전적과 함께) — 저장 전에 동기로 긁는다.** 본문은 `{gameNickname, mainPosition}` — **주 포지션은 자기신고다**(처음에는 주 포지션도 Riot 에서 채웠다가 같은 날 소유자가 되물렸다 — "이번에 맡을 자리" 라 사용자가 정한다). VALORANT · PUBG 는 자기신고 그대로. 이 저장소에는 코드로 걸리지 않는다 | 2026-09-27 | **D-27 의 "커밋 뒤 비동기" · 자기신고 칸** | P-26 · `../platform/contracts/platform-api.md` "게임 프로필" |
| ⑯ | **소셜 계정 잇기 · 끊기** — 로그인한 채 다른 제공자로 오면 같은 사용자에 잇는다(302 `/settings?linked=…` · 남의 것 · 같은 제공자 둘은 `?error=…`). 끊기는 `DELETE /api/v1/users/me/social/{provider}` · 마지막 하나면 409 `LAST_SOCIAL_IDENTITY`. 자동으로 중복을 잡지 않는다(이메일을 안 받는다). `platform` 에 구현됐다. 이 저장소에는 코드로 걸리지 않는다 | 2026-09-27 | **D-35 "아직 미정" 의 절반** | P-27 · `../platform/contracts/platform-api.md` "소셜 로그인" |
| ⑰ | **모집 글에서 `purpose` 를 없앤다** — 컬럼 · 요청 · 응답 · `platform` 의 `party` 쪽 `PlayPurpose`. `voice` 는 남는다. **이 저장소의 `PlayPurpose` 는 그대로다** | 2026-09-27 | **D-28 · D-11 의 글 내용 서술**(P-8) | P-29 · `../platform/contracts/platform-api.md` "모집 글 · 목록" |
| ⑱ **← 이 폴더에 키 모양으로 걸린다** | **자동 매칭이 게시판 방에 먼저 합류하는 길 — 세부 넷**(아래 (다) 의 방향에 붙은 것). `platform` 의 요청 하나(없으면 404 → **프런트가** 이 앱의 매칭 요청을 부른다) · 조건(`game` · `mode` · `voice` · PUBG 시점 · 티어가 `tier-range:{MODE}` 안 · 주 포지션) · 가장 오래된 방부터 · **활성 요청 키를 만들지 않는다**(D-19 그대로). **결정만 — 소유자가 직접 구현한다.** 구현되면 `platform` 이 seed 의 `tier-range` 를 읽는다 | 2026-09-27 | **D-29 의 "읽는 키는 둘" · "아직 미정"** | P-28 · `../platform/CLAUDE.md` §7 |

- **세는 법이 문서마다 다르다** — `../platform/START_HERE.md` 끝은 같은 묶음을 **여덟**으로 세면서 ③(인증 세부)을 2026-09-21 의 원조로 빼 두고
  P-13 · P-14 를 둘로 세며, 같은 날 늦게 나온 ⑦ · ⑨ 를 **그 두 항목 안에 접어 넣는다**(`platform` 이 새 번호를 두지 않았기 때문이다).
  **남길 것은 어느 쪽으로 세든 아홉 덩어리다** — 위 표의 열 줄에서 ⑤ 를 둘로 풀고 ⑦ · ⑨ 를 그 둘에 접으면 아홉이다.
- **⑦ · ⑧ · ⑨ 는 `platform` 안에서 끝난다 — 이 폴더가 고칠 코드가 없다.** 전적을 긁는 시점도, 사용자가 누르는 갱신 요청도, 목록의 정렬 · 커서와 그 인덱스(`platform` 의 마이그레이션 `party/V7__board_order_index.sql`)도 그 앱 안의 일이다. **여기서 할 일은 docs/11 에 남기는 것 하나다.** ⑩ 도 코드는 그 앱 쪽이다 — 다만 고칠 D-항목이 이 폴더에 있다(바로 아래).
- **⑩ 은 오늘 더한 넷 가운데 무게가 다르다 — 이미 있는 D-항목을 고치는 일이다.** ⑦ · ⑧ · ⑨ 가 개정하는 것은 `platform` 자기 계약의 P-항목(P-13 · P-14)이라 docs/11 에는 **아직 그 항목이 없다** — 새로 올리기만 하면 된다.
  ⑩ 은 **`docs/11_DECISION_LOG.md` 의 D-20 ③ 을 걷어내는 것**이고 **①②④ 는 그대로 유효하다** — 새 D-항목을 쓰면서 D-20 쪽에도 낡은 대목임을 그 파일의 방식대로 표시해야 한다. **문안은 그 파일에서 쓴다.**
- **⑥은 이 폴더에 직접 걸린다 — 나머지 아홉과 다르다(코드 · 키 약속으로 걸리는 것은 ⑥ 하나다).**
  - gameconfig 값의 **원본은 이 폴더의 `seed/gameconfig.redis`** 이고, 그 머리가 **"이 파일이 MVP의 사실상 원본(source of truth)이다 ·
    앱은 부팅 시 설정을 밀어넣지 않고 Redis에서 읽기만 한다"**고 적었다(확인함). **쓰는 앱이 없다**는 것이 `platform` 이 읽어도 된다는 근거다.
  - `platform` 이 읽는 키는 **둘뿐**이다 — `qm:gameconfig:{GAME}:{MODE}`(HASH 의 **`EXISTS` 만**. 내용은 안 읽는다) ·
    `qm:gameconfig:{GAME}:tier`(ZSET 의 **`ZSCORE`**). **`:tier-range:` 는 읽지 않는다.**
  - **그래서 이 폴더가 앞으로 조심할 것이 생겼다.** `redisKeys/SharedKeys.GAMECONFIG_PREFIX`(`:138`)나 seed 의 키 모양을 바꾸면
    **`platform` 의 `mode` · `tier` 검증이 조용히 꺼진다** — 그쪽이 **fail-open** 이라 에러도 안 난다. 바꿀 때는 `platform` 과 같이 바꾼다.
  - **seed 에 모드를 더하거나 지우는 것도 `platform` 에 영향이 있다** — 없는 모드로는 모집 글 쓰기가 **400** 이고, 지운 모드는 되던 글쓰기가 안 된다.
  - **`platform` 은 이 앱을 HTTP 로 부르지 않는다** — 읽기만 하고 seed 를 심지도 않는다. 이 폴더가 해 줄 일은 없다.

#### (다) 미정 — 방향만 정해진 것 하나. **D-항목으로 못 쓴다**

> **2026-09-27 — 세부 넷이 정해졌다 → (나) ⑱ · docs/11 D-40.** 아래 "정할 것" 가운데 경로(`platform` 의 요청 · 없으면 프런트가 이 앱을 부른다) · 조건 · 여럿일 때(가장 오래된 방) · 활성 요청 키(만들지 않는다) · ②로 넘기는 주체(프런트)가 전부 답이 났다. **소유자가 직접 구현한다.** 남은 것은 D-40 의 "아직 미정"(경로 이름 · 에러 코드 · 본문 · 티어나 주 포지션이 없는 사람 · "자동 합류 허용" 칸)이다. 아래는 그날의 기록으로 둔다.

**"자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길을 둔다"** (2026-09-23 소유자 결정 — **방향만이다**).
**안 하면(정하지 않으면): 게시판 방과 대기열 매칭이 따로 논다** — 사람이 적을 때 대기열은 영영 안 모이는데(콜드 스타트) 옆에 열린 방이 있어도 넣을 길이 없다.

- "매칭 시작"을 누르면 ① **조건이 맞는 열린 게시판 방이 있으면 거기에 넣고** ② 없으면 기존 대기열 매칭으로 간다.
  **이 앱의 대기열 · 제안 · 수락 · 확정은 그대로 살린다 — 갈아엎지 않는다.**
- **①을 `platform` 이 맡는 쪽으로 기운다** — 방 키 · 차단 · 프로필을 이미 다 읽는 앱이 거기뿐이다
  (이 앱이 하려면 `room` 의 Redis 와 `social.blocks` 를 알아야 해서 경계가 무너진다).
- **정할 것** — ①의 요청이 어느 앱의 어느 경로인가 / "조건이 맞는다"를 무엇으로 보는가 / 맞는 방이 여럿이면 어느 것을 고르는가 /
  ①에서 방에 들어간 사람의 **활성 요청 키**를 어떻게 다루는가(**D-19 의 "대기와 방은 한 번에 하나만"에 걸린다** — **§0-1 ① 과 같이 봐야 한다**) /
  ①이 실패했을 때 ②로 넘기는 것을 누가 하는가(프런트인가 서버인가).
- **(나) ⑥이 여기에 밑감이 된다** — `platform` 이 글의 `mode` 를 gameconfig 로 검증하게 되면서 **글의 `mode` 와 매칭 요청의 `mode` 를
  이제 같은 이름으로 맞춰 볼 수 있다**(자유 문자열이면 판정할 수 없었다). **그래도 이 항목 자체는 미정이다.**
- 원문은 `../platform/CLAUDE.md` §7 의 그 행이다. **정해지면 이 폴더에서 D-항목으로 남긴다.**

### 0-1. 남은 것 — 우선순위 순

우선순위 근거는 "안 하면 무엇이 안 되는가"다.

#### ① 확정된 사용자를 파티에서 풀어 주는 경로가 없다 — **가장 급하다**

> **→ 2026-09-27 닫힘 (§0-6, docs/11 D-42).** 활성 요청은 60초 TTL 로 풀리고 그 뒤는 platform 의 입장 표시 키가 맡는다. 아래는 그 전의 기록이다.

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

> **2026-09-19 추가.** 후보 1(`PartyClosed.fifo` 소비)은 **닫혔다** — 그 큐의 소비자는 `app:platform`
> 하나이고 이 앱은 읽지 않는다 (docs/11 D-13). 큐 하나를 두 앱이 읽으면 메시지를 나눠 갖게 된다.
> **새 가능성이 하나 생겼다** — docs/11 D-11 16번으로 게시판 방을 맡는 앱이 활성 요청 키
> (`qm:user:active-request:{userId}`)를 쓰고 지울 수 있게 됐다. 그 앱은 D-16 으로 **`app:room`** 이다
> (처음에는 `app:platform` 으로 적었다). 그러므로 방이 닫힐 때 `app:room` 이 이 키를 지우는 길이 있다.
> **가능성일 뿐 정해진 것이 아니다.** 후보 2·3 은 그대로 열려 있다.

> **2026-09-19 추가 (docs/11 D-19).** **바로 위의 "새 가능성"은 없어졌다.** "한 번에 하나만"을 키 둘로
> 지키게 되면서 `app:room` 은 활성 요청 키에 **쓰지 않는다** — 자기 입장 표시 키
> `qm:user:active-room:{userId}` 만 쓰고 지우고, 활성 요청 키는 `EXISTS` 로 있는지만 본다. 그러므로 방이 닫힐 때
> `app:room` 이 `status=PARTY` 를 푸는 길은 없다. **푸는 주체는 `app:matching` 이나 `app:platform` 쪽에서
> 찾아야 한다** — 후보 2·3 은 그대로 열려 있다. **이 문제가 하나 더 물고 들어온다**: 확정된 사용자는 활성 요청
> 키가 남아 있으므로 매칭(409 `ALREADY_QUEUED`)뿐 아니라 **`app:room` 입장도 그대로는 거절된다.** 자동
> 매칭으로 확정된 파티의 방 입장(D-16 미정)을 정할 때 같이 풀어야 한다. 여전히 **미정**이다.

> **2026-09-24 참조.** §0-4 (다)의 "자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길"(2026-09-23 소유자 결정, 방향만)이
> 이 문제와 얽힌다 — 그 길로 방에 들어간 사람의 **활성 요청 키**를 어떻게 다루는지가 거기서도 미정이다. **같이 봐라.**
> **2026-09-27 — 그쪽은 정해졌다(D-40): 활성 요청 키를 만들지 않는다**(게시판을 먼저 한 번 보고, 없을 때만 매칭 요청으로 간다). 그래서 이 ① 과는 더 얽히지 않는다 — 이 ① 자체는 그대로 미정이다.

**건드릴 곳**: `redis/proposal/cleanup-confirmed.lua` · `service/ProposalService#confirmed()` ·
(1번이면) 새 SQS 소비자 패키지 · (2번이면) `MatchingController` + `MatchCancelService`.

#### ② INV-6 차단 검증 — **배포 차단 조건**

> **→ 2026-09-27 닫힘 (§0-6, docs/11 D-41).** 선필터 한 겹으로 확정 · 세 게임 모두 부른다 · 로컬 H2 는 `schema.sql`. 아래는 그 전의 기록이다.

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

> **→ 2026-09-27 닫힘 (§0-6, docs/11 D-42).** outbox · SQS 를 두지 않는다 — platform 이 파티 HASH 를 읽는다. 아래는 그 전의 기록이다.

**안 하면: 확정돼도 파티가 DB 에 안 생긴다.** `app:platform` 이 파티를 만들 신호를 못 받는다.
Redis 쪽 뒷정리(`cleanup-confirmed.lua`)와 `MATCH_CONFIRMED` 알림까지는 붙었는데 거기서 끝난다.

- **AWS SDK 의존성부터 없다** (`backend/build.gradle` 확인함 — `outbox`/`sqs`/`ProposalConfirmed`
  로 grep 하면 자바 코드는 0건이고 주석만 나온다).
- `matching.outbox` 테이블도 없다(Flyway 가 없으므로 ② 와 같은 덩어리다).
- ~~① 을 `PartyClosed` 로 풀기로 하면 그 소비도 여기 딸린다.~~ (2026-09-19: docs/11 D-13 으로 그 길은 닫혔다)

**건드릴 곳**: `backend/build.gradle` · `service/ProposalService#confirmed()` ·
새 `outbox/` 패키지 · Flyway 마이그레이션.

#### ④ `BlockChanged.fifo` 소비 없음 — **2026-09-19 닫힘: 할 일이 아니다**

> `BlockChanged.fifo` 와 Redis 선필터(`qm:block:{userId}`)는 **폐기됐다** (docs/11 D-12). 차단은 ② 의
> DB 직접 조회 한 겹으로 지킨다. 계약 사본도 그렇게 고쳤다(`contracts/README.md` A-5). 아래는 그 전의 서술이다.

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

> **→ 2026-09-27 닫힘 (§0-6).** 복구됐다 — `load-test/README.md`. 아래는 그 전의 기록이다.

티어가 필수가 된 뒤로 `load-test/` 가 그대로는 못 돈다. 두 군데가 어긋난다 (재확인함).
1. **요청 바디** — `load-test/match_latency.py` 의 `body()` 가 `modeKey: RANKED_SOLO` 를
   `tier` 없이 보낸다. 시드의 `RANKED_SOLO` 는 `tierRule EXIST` 라 validator 가 400 을 낸다.
   `prefill.py` · `measure.js` · `stock.js` · `throughput.js` · `netpath/postload.js` 도 같다.
2. **색인 키** — `prefill.py` · `run.sh` · `netpath/runpost.sh` · `runpost2.sh` 가 티어 접미사
   없는 needs 키를 `ZCARD` 하는데, 티어 모드는 Lua 가 `:{tier}` 를 붙이므로 그 키는 비어 있다.

**2026-09-27 부터 하나 더 어긋난다** — 모든 `/api/v1/**` 가 쿠키 `qm_access` 를 요구하므로 스크립트가 사용자마다 토큰을 찍어
쿠키로 실어야 한다(바디의 `userId` 는 무시된다 — §0-5). 서명에는 `platform` 의 개발용 개인 키를 쓴다(`START_HERE.md` "앱 실행" 의 openssl 예).

`tierRule NONE` 인 모드(`NORMAL_2` 등)로 바꾸거나, 바디에 `tier` 를 싣고 키에 같은 접미사를
붙여 맞춰라. **성사 감지 자체는 이미 고쳐져 있다**(`32031a4`, `member:` 필드를 센다).

#### ⑦ 계약 정리 — 본 저장소 contract 변경이 선행

> **→ 2026-09-27 닫힘 (§0-6).** 사본은 A-13 · A-14 · A-15 로 구현에 맞췄다. 남은 것은 이름 결정(#4 · #5)과 원본 반영이다. 아래는 그 전의 기록이다.

`contracts/openapi.yaml` 은 **원본의 발췌**라 여기서 고치지 않는다 (CLAUDE.md §5).
지금 어긋난 것은 `contracts/README.md` 의 불일치 표에 전부 적어 두었다. 큰 것만:
- `VoicePreference` 에 `OPTIONAL` 이 남아 있다 (#1, **코드가 맞다**)
- `KeyCondition.type` 이 `PLAY_STYLE` 이다 — 코드는 `PLATFORM` (#14, **코드가 맞다**)
- **상태 조회 경로** `/{requestId}` vs `?userId=` (#5, **코드가 맞다고 보고 그렇게 뒀다**. 2026-09-27 에 `?userId=` 가 없어져 지금은 파라미터 없는 `GET /match-requests` 다 — "나"는 쿠키)
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
  확정된 사용자를 푸는 경로, Flyway + `social.blocks`(INV-6) 다. (그 테이블은 2026-09-26 에 `public.blocks` · bigint 가 됐다 —
  docs/11 D-25 · D-34, §0-4 (가).) **→ 2026-09-27: 그 셋이 전부 닫혔다 — outbox · Flyway 는 두지 않고(D-42), 확정된 사용자는 60초 TTL 로
  풀리고(D-42), INV-6 은 선필터 한 겹(D-41). §0-6 을 봐라.**

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

### B. 만료 처리 + 확정 후속 (INV-4/5 의 남은 구멍) <sub>(2026-09-27: 전부 닫혔다 — outbox · `PartyClosed` 는 D-42 로 두지 않는다. §0-6)</sub>

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

### C. INV-6 스키마 (Flyway) — 배포 전 필수 <sub>(2026-09-17: 그대로 남아 있다 — §0-1 ②) → <b>2026-09-27 닫힘 — Flyway 는 두지 않고(D-42) 로컬 H2 는 `schema.sql`, 운영은 platform 의 `public.blocks`(D-41). §0-6</b></sub>

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
  (2026-09-27 에는 `docker.exe run -d --rm --name qm-matching-test-redis -p 6390:6379 redis:7-alpine` 로 띄웠다. 테스트는 `platform` 의 키가 필요 없다 — §0-5)
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
