# contracts/ — app:matching 이 노출하는 계약

## 이게 뭔가

`contracts/openapi.yaml` 과 `contracts/events.md` 는 팀 간 계약이다.
**원본은 queueMate 본 저장소의 `contracts/`** 이고, 여기 있는 두 파일은
**`app:matching` 이 노출하는 부분만 발췌한 사본**이다.

| 파일 | 원본 | 발췌 기준 |
|---|---|---|
| `openapi.yaml` | queueMate `feature/frontend:contracts/openapi.yaml` (662줄) | `/games`, `/match-requests*`, `/proposals/{id}/accept|decline` + 그에 필요한 스키마 |
| `events.md` | queueMate `feature/frontend:contracts/events.md` (110줄) | `MATCH_*` 5종 + SQS FIFO 2개(`ProposalConfirmed` 생산 / `BlockChanged` 소비) |

**계약을 바꿔야 하면 여기서 바꾸지 마라.** queueMate 본 저장소에서 contract 변경 커밋을
먼저 만들고, 그 뒤 이 사본을 다시 뜬다. 소유 영역 밖 계약을 임의로 바꾸지 않는다.

---

## 코드 ↔ 계약 불일치 (2026-09-11 확인)

계약과 구현이 어긋난 지점이다. **한쪽에 맞추기 전에 어느 쪽이 맞는지부터 판단해라.**
아래 "판정" 열이 그 판단이다.

| # | 지점 | 계약 | 구현 | 판정 |
|---|---|---|---|---|
| 1 | `VoicePreference` enum | `[REQUIRED, OPTIONAL, NO_VOICE]` | `[REQUIRED, NO_VOICE]` (`domain/VoicePreference.java`) | **코드가 맞다.** `OPTIONAL` 제거는 의도된 결정이고 근거가 enum 주석에 있다. 계약을 고쳐야 한다 |
| 2 | `CreateMatchRequest` 바디 | `userId` 없음 (JWT 로 식별) | `userId` **필수** (`dto/CreateMatchRequestCommand.java`) | **계약이 맞다.** 구현의 `userId` 는 JWT 도입 전 임시다. 인증이 붙으면 지워야 한다 |
| 3 | `DELETE /match-requests/{id}` | 쿼리 파라미터 없음 | `?userId=` **필수** (`controller/MatchingController.java`) | 위와 같은 임시 조치 |
| 4 | `MatchRequestView` | `{ id, status, queuedAt, proposalId }` | `{ requestId, status }` (`dto/MatchRequestResponse.java`) | **계약이 맞다.** 필드명도 `id` 여야 한다. 다만 `queuedAt`/`proposalId` 를 채우려면 proposal 구현이 먼저다 |
| 5 | `GET /match-requests/{id}` | 있다 (`200` + `MatchRequestView`) | **껍데기만 있다** — `MatchingController#getMatchRequest` 가 `501 NOT_IMPLEMENTED` 를 돌려준다 | **계약이 맞다.** 501 은 "아직"이라는 표시다. 그 메서드 주석에 왜 필요한지와 무엇을 같이 정해야 하는지가 적혀 있다. 아래 "남은 구멍" 참고 |
| 6 | `POST /proposals/{id}/accept`·`decline` | 있다 (`200`) | **구현됨.** `controller/ProposalController.java` → `service/ProposalService.java` → `redis/proposal/accept-proposal.lua`·`decline-proposal.lua`. 응답 갈래는 `domain/ProposalResult.java` 한 enum 이다(`AcceptResult`/`DeclineResult` 는 없어졌다). `decline` 은 `?requestId=` 도 받는다 | **구현이 앞서 있다.** `requestId` 쿼리 파라미터와 409/403 갈래가 계약에 없다 |
| 6-1 | 위 두 엔드포인트의 성공 응답 | `200` (본문 스키마 없음) | `204 No Content` | **구현 쪽이 낫다고 보고 그렇게 뒀다.** 본문 스키마가 계약에 없어 `200` 이 돌려줄 것이 없고, 취소(`DELETE`)와 모양이 맞는다. 근거는 `ProposalController` 의 `decline` 주석. 계약을 `204` 로 고쳐야 한다 |
| 7 | `GET /games` | 있다 | **없다** | 계약이 맞다. gameconfig 는 `seed/gameconfig.redis` 로만 다뤄지고 조회 API 가 없다 |
| 8 | `ErrorResponse` 스키마 | **없다** (원본이 스스로 구멍이라고 지적) | 있다 (`common/error/ErrorResponse.java`: `{code, message, details}`) | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다 |
| 9 | `503 MATCHING_UNAVAILABLE` 응답 | 계약에 없다 | 있다 (`GlobalExceptionHandler#handleRedisFailure`, INV-10). 후보 풀 락 획득 실패(`redisLock/PoolLock.java`)도 같은 자리로 나간다 | 구현이 맞다. 계약에 추가해야 한다 |
| 10 | `securitySchemes` (JWT bearer) | **없다** | 인증 자체가 없다 | 양쪽 다 비어 있다 |
| 11 | SSE `MATCH_*` 5종 | 있다 | **3종 발행됨** — `MATCH_QUEUE_UPDATED` / `MATCH_PROPOSAL_CREATED` / `MATCH_CANCELLED` 를 `notification/PushPublisher.java` 가 `qm:pubsub:push:{userId}` 로 publish 한다. `MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED` 는 확정·만료가 미구현이라 없다 | **부분 구현.** SSE 배달 자체는 `app:realtime` 몫이므로 이 저장소가 할 일은 publish 까지다. 상세는 `events.md` 의 "구현 상태" 표 |
| 11-1 | SSE payload 스키마 | **없다** (14종 전부 미정의 — 아래 "미해결 계약 구멍") | 구현이 먼저 정했다. `MATCH_QUEUE_UPDATED`·`MATCH_CANCELLED` = `{memberNumber}`, `MATCH_PROPOSAL_CREATED` = `{memberNumber, target, partyId}` | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다. `PushPublisher` 의 `payload` 가 `Map` 인 것도 그 때문이다 |
| 12 | SQS `ProposalConfirmed` / `BlockChanged` | 있다 | **없다** — AWS SDK 의존성 없음 | 계약이 맞다 |
| 13 | `CreateMatchRequest` 의 `tier` | **없다** | `tier` (선택 필드, String). `tierRule` 이 `NONE` 이 아닌(= `EXIST` 인) 모드에서는 사실상 필수이고 빠지면 400 이다 (`validation/lol/LolConditionValidator.java`). **값은 단(division)까지 적는다** — `GOLD` 가 아니라 `GOLD_2` 다. 허용되는 이름의 원본은 자바 enum 이 아니라 Redis ZSET `qm:gameconfig:LOL:tier` 다 (32개) | **구현이 앞서 있다.** 조건 5번째가 아니라 derived/자격 조건이다 (docs/02 §6, CLAUDE.md §2). 계약에 추가해야 하고, 계정 연동이 붙으면 요청 필드에서 사라진다 |

| 14 | `KeyCondition.type` 의 PUBG 값 | `PLAY_STYLE` | `PLATFORM` (`domain/KeyConditionType.java`), 값은 `STEAM` / `KAKAO` | **코드가 맞다.** 스팀·카카오는 서로 파티를 맺을 수 없어 플레이 스타일(취향) 대신 플랫폼(hard)을 핵심 조건으로 교체했다. `openapi.yaml` 의 enum 을 고쳐야 한다. 근거는 enum 클래스 주석 |

### 계약에는 없지만 구현이 실제로 내는 에러 코드

`GlobalExceptionHandler` · `MatchingController` · `ProposalController` 에서 추출했다.

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | `@Valid` 실패 (필수 필드 누락, enum 값 오류). `details` 에 필드별 메시지 |
| 400 | `BAD_REQUEST` | `IllegalArgumentException` — 예: 지원하는 규칙이 없는 게임 |
| 400 | `INVALID_MATCH_CONDITION` | 게임별 조건 검증 실패 (없는 modeKey, 모드에 안 맞는 포지션 값, `tierRule` 에 안 맞는 티어, `tierRule=EXIST` 인데 `tier-range` 표에 줄이 없거나 `SOLO_ONLY` 인 티어) |
| 409 | `ALREADY_QUEUED` | INV-1 — 이미 활성 요청이 있다 |
| 404 | `MATCH_REQUEST_NOT_FOUND` | 취소할 활성 요청이 없다 |
| 404 | `MATCH_REQUEST_MISMATCH` | 저장된 requestId 와 다르다 (늦게 도착한 취소) |
| 501 | `NOT_IMPLEMENTED` | `GET /match-requests/{id}` — 아직 구현되지 않았다 |
| 503 | `MATCHING_UNAVAILABLE` | Redis 장애 또는 후보 풀 락 획득 실패. `Retry-After: 5` 헤더 동반 (INV-10 fail-closed) |

아래 코드들은 `ProposalController` 에 **자리는 잡혀 있으나 실제로 나오지 않는다** —
`ProposalService` 가 먼저 `UnsupportedOperationException` 을 던지기 때문이다:
`PROPOSAL_EXPIRED`(410) · `PROPOSAL_DECLINED`(409) · `PROPOSAL_ALREADY_RESPONDED`(409) ·
`NOT_PROPOSAL_MEMBER`(403) · `PROPOSAL_NOT_FOUND`(404).

---

## 남은 구멍 — 클라이언트가 "지금 상태"를 물어볼 곳이 없다

계약은 매칭 결과를 알리는 경로를 **두 가지**로 정의한다.

1. SSE `MATCH_PROPOSAL_CREATED` / `MATCH_CONFIRMED` (`events.md`)
2. 폴링 `GET /api/v1/match-requests/{id}` (`openapi.yaml`)

**1번은 절반 메워졌고 2번은 비어 있다.**

- SSE — 이 앱의 몫인 Redis Pub/Sub publish 는 **된다**
  (`notification/PushPublisher.java`, 채널 `qm:pubsub:push:{userId}`). 정원이 차면
  `MATCH_PROPOSAL_CREATED` 가 파티 전원에게 나간다. 다만 `MATCH_CONFIRMED` 는 수락
  집계·확정이 없어 영원히 오지 않는다. SSE 배달은 `app:realtime` 몫이고 이 저장소에
  `SseEmitter` 를 넣지 않는 것이 맞다 (CLAUDE.md §3).
- 폴링 — `GET /match-requests/{id}` 가 **501** 이다.

**그래서 남은 문제는 두 개다.**

1. **알림은 사건만 전한다.** "방금 이렇게 됐다"는 말하지만 "지금 이렇다"는 못 한다.
   앱을 껐다 켜거나 새로고침하거나 다른 기기로 접속한 클라이언트는 아무것도 모른 채
   "매칭 시작" 버튼을 그리고, 누르면 409 `ALREADY_QUEUED` 를 받는다. 게다가 Pub/Sub 은
   at-most-once 라 놓친 알림의 복구도 이 REST 몫이다 — 계약이 "놓친 상태는 REST 로
   복구한다"고 정한 그 REST 가 이것이다.
2. **수락 집계·확정이 없다.** `MATCH_PROPOSAL_CREATED` 를 받아 수락 창을 띄워도
   누를 곳(`POST /proposals/{id}/accept`)이 500 을 돌려준다.

구현 순서와 각 시작 지점은 `START_HERE.md` §5 에 있다.

`load-test/match_latency.py` 가 성사를 감지하려고 HTTP 가 아니라 **Redis 를 직접 폴링**하는
것(`HGET qm:user:active-request:{uid} partyId` → `HGET qm:party:{pid} size`)은 그 스크립트가
알림 도입 전에 쓰였기 때문이다. 알림 경로가 생겼으니 `PSUBSCRIBE qm:pubsub:push:*` 로
바꿀 수 있다.
