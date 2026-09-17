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

## 코드 ↔ 계약 불일치 (2026-09-11 확인, 2026-09-17 갱신)

계약과 구현이 어긋난 지점이다. **한쪽에 맞추기 전에 어느 쪽이 맞는지부터 판단해라.**
아래 "판정" 열이 그 판단이다.

| # | 지점 | 계약 | 구현 | 판정 |
|---|---|---|---|---|
| 1 | `VoicePreference` enum | `[REQUIRED, OPTIONAL, NO_VOICE]` | `[REQUIRED, NO_VOICE]` (`domain/condition/VoicePreference.java`) | **코드가 맞다.** `OPTIONAL` 제거는 의도된 결정이고 근거가 enum 주석에 있다. 계약을 고쳐야 한다 |
| 2 | `CreateMatchRequest` 바디 | `userId` 없음 (JWT 로 식별) | `userId` **필수** (`dto/CreateMatchRequestCommand.java`) | **계약이 맞다.** 구현의 `userId` 는 JWT 도입 전 임시다. 인증이 붙으면 지워야 한다 |
| 3 | `DELETE /match-requests/{id}` | 쿼리 파라미터 없음 | `?userId=` **필수** (`controller/MatchingController.java`) | 위와 같은 임시 조치 |
| 4 | `MatchRequestView` **필드** | `{ id, status, queuedAt, proposalId }` **4개** | `MatchRequestResponse` **8개** — `{ status, requestId, queuedAt, partyId, target, memberCount, expiresAt, isAccepted }` (`dto/MatchRequestResponse.java`, **record** + `@JsonInclude(NON_NULL)`) | **양쪽 다 고쳐야 한다.** 이름은 **계약이 맞다** — `requestId` 가 아니라 `id` 여야 하고, `partyId` 도 계약 이름으로는 `proposalId` 다(확정되면 둘이 같은 값이다 — proposal = party). 반대로 **필드 수는 구현이 앞서 있다**: `target`/`memberCount`(대기 화면의 "3/5명"), `expiresAt`/`isAccepted`(제안 화면의 남은 시간과 내가 눌렀는지)는 계약에 자리가 없는데 클라이언트가 화면을 그리려면 필요하다. `queuedAt` 은 타입도 다르다 — 계약은 `date-time` 문자열, 구현은 **epoch millis `Long`**. `@JsonInclude(NON_NULL)` 이라 그 갈래에서 뜻이 없는 칸은 **응답에 아예 나타나지 않는다**(예: `IDLE` 은 `{"status":"IDLE"}` 하나뿐이다) |
| 4-1 | `MatchRequestView.status` enum | `[QUEUED, PROPOSED, MATCHED, CANCELLED, EXPIRED]` | `domain/MatchRequestStatus.java` 에 **`IDLE` 이 추가**돼 6개다. 반대로 `CANCELLED`/`EXPIRED` 는 **enum 에만 있고 조회가 절대 돌려주지 않는다** | **구현이 맞다.** 취소·만료는 활성 요청 키를 지우므로 서버에 근거가 남지 않아 "원래 큐에 없었다"와 구분되지 않는다 — 둘 다 `IDLE` 로 나간다. 계약에 `IDLE` 을 추가해야 하고, `CANCELLED`/`EXPIRED` 는 남겨 둘지 정해야 한다(자리만 있는 값이라는 것이 enum 주석에 적혀 있다) |
| 5 | **상태 조회의 경로** | `GET /match-requests/{requestId}` — **경로 변수**로 찾는다 | **구현됐다. 그러나 경로가 다르다** — `GET /match-requests?userId=...` 로 **쿼리 파라미터**다. 경로 변수가 아예 없다 (`MatchingController#getMatchRequest` → `service/MatchQueryService`) | **구현이 맞다고 보고 그렇게 뒀다. contract 변경이 필요한 사안이다 (CLAUDE.md §5).** 이유 둘: ① 활성 요청은 `qm:user:active-request:{userId}` 로 **사용자 단위** 저장이라(INV-1) `requestId` 는 그 HASH 안에 든 값이지 찾는 열쇠가 아니다. ② 이 조회가 가장 필요한 순간이 **페이지를 새로 열었을 때**인데 그때 클라이언트는 `requestId` 를 잃은 상태다 — 그 값을 요구하면 정작 필요할 때 못 쓰는 API 가 된다. 취소(`DELETE`)가 `requestId` 를 받는 것은 **쓰기**라서다(늦게 도착한 취소가 그 사이 새로 만든 요청을 지우면 안 된다). 조회에는 그 위험이 없다. **JWT 가 붙으면 `GET /match-requests/me` 형태가 된다** — 그때 `?userId=` 는 사라진다. 근거는 그 메서드 주석 |
| 5-1 | `POST /match-requests` 의 **201 본문** | `MatchRequestView` | **JSON 이 아니라 문자열 `"CREATED"`** (`MatchingController#createMatchRequest` 가 `.body("CREATED")`) | **계약이 맞다. 구현을 고쳐야 한다.** `MatchRequestResponse` 는 `queued(requestId, queuedAt)` 정적 팩토리로 접수 직후 갈래를 이미 표현할 수 있는데 쓰이지 않고 있다. 접수 응답이 `requestId` 를 안 주면 클라이언트가 취소(`DELETE /{requestId}`)를 부를 값을 잃는다 — #5 의 조회로 되찾을 수는 있으나 왕복이 하나 는다 |
| 6 | `POST /proposals/{id}/accept`·`decline` | 있다 (`200`) | **구현됨.** `controller/ProposalController.java` → `service/ProposalService.java` → `redis/proposal/accept-proposal.lua`·`decline-proposal.lua`. 응답 갈래는 `domain/ProposalResult.java` 한 enum 이다(`AcceptResult`/`DeclineResult` 는 없어졌다). `decline` 은 `?requestId=` 도 받는다 | **구현이 앞서 있다.** `requestId` 쿼리 파라미터와 409/403 갈래가 계약에 없다 |
| 6-1 | 위 두 엔드포인트의 성공 응답 | `200` (본문 스키마 없음) | `204 No Content` | **구현 쪽이 낫다고 보고 그렇게 뒀다.** 본문 스키마가 계약에 없어 `200` 이 돌려줄 것이 없고, 취소(`DELETE`)와 모양이 맞는다. 근거는 `ProposalController` 의 `decline` 주석. 계약을 `204` 로 고쳐야 한다 |
| 6-2 | **수락 재전송**의 응답 | 계약에 없다 | **`204`**. 이미 확정된 제안에 수락이 또 오면 `accept-proposal.lua` 가 `ALREADY_RESPONDED` 를 돌려주고, 컨트롤러가 `ACCEPTED` / `CONFIRMED` 와 **같이 묶어 204** 로 내보낸다 (`ProposalController#accept`) | **구현이 맞다.** 같은 명령을 두 번 보내 결과가 같으면 성공이다 — 응답이 유실돼 자동 재시도한 클라이언트에게 오류를 보이지 않는다. 스크립트가 `CONFIRMED` 와 값을 가른 것은 확정 알림(`MATCH_CONFIRMED`)이 두 번 나가지 않게 하려는 것이지 실패라는 뜻이 아니다. **거절 쪽 `ALREADY_RESPONDED` 는 409 그대로다** — 그쪽은 재시도가 아니라 "수락해 놓고 거절을 눌렀다"는 진짜 충돌이다 |
| 7 | `GET /games` | 있다 | **없다** | 계약이 맞다. gameconfig 는 `seed/gameconfig.redis` 로만 다뤄지고 조회 API 가 없다 |
| 8 | `ErrorResponse` 스키마 | **없다** (원본이 스스로 구멍이라고 지적) | 있다 (`common/error/ErrorResponse.java`: `{code, message, details}`) | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다 |
| 9 | `503 MATCHING_UNAVAILABLE` 응답 | 계약에 없다 | 있다 (`GlobalExceptionHandler#handleRedisFailure`, INV-10). 후보 풀 락 획득 실패(`redisLock/PoolLock.java`)도 같은 자리로 나간다 | 구현이 맞다. 계약에 추가해야 한다 |
| 10 | `securitySchemes` (JWT bearer) | **없다** | 인증 자체가 없다 | 양쪽 다 비어 있다 |
| 11 | SSE `MATCH_*` 5종 | 있다 | **5종 모두 발행됨** — 앞의 3종에 더해 `MATCH_PROPOSAL_EXPIRED`(`service/ProposalExpiryService.java`) · `MATCH_CONFIRMED`(`service/ProposalService.java#accept()`)가 붙었다. 전부 `notification/PushPublisher.java` 가 `qm:pubsub:push:{userId}` 로 publish 한다 | **구현됨.** SSE 배달 자체는 `app:realtime` 몫이므로 이 저장소가 할 일은 publish 까지다. 상세는 `events.md` 의 "구현 상태" 표 |
| 11-1 | SSE payload 스키마 | **없다** (14종 전부 미정의 — 아래 "미해결 계약 구멍") | 구현이 먼저 정했다. `MATCH_QUEUE_UPDATED`·`MATCH_CANCELLED` = `{memberNumber}`, `MATCH_PROPOSAL_CREATED` = `{memberNumber, target, partyId}`, `MATCH_PROPOSAL_EXPIRED`·`MATCH_CONFIRMED` = `{partyId}` | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다. `PushPublisher` 의 `payload` 가 `Map` 인 것도 그 때문이다 |
| 12 | SQS `ProposalConfirmed` / `BlockChanged` | 있다 | **없다** — AWS SDK 의존성 없음. 확정 자체는 되고 Redis 쪽 뒷정리(`cleanup-confirmed.lua`)와 `MATCH_CONFIRMED` 알림까지 붙었지만, `matching.outbox` 기록과 `ProposalConfirmed.fifo` 발행이 없어 `app:platform` 이 파티를 만들지 못한다. 반대 방향인 `PartyClosed` 소비도 없어 확정된 사용자의 활성 요청에 찍힌 `status=PARTY` 를 푸는 코드가 없다 | 계약이 맞다 |
| 13 | `CreateMatchRequest` 의 `tier` | **없다** | `tier` (선택 필드, String). `tierRule` 이 `NONE` 이 아닌(= `EXIST` 인) 모드에서는 사실상 필수이고 빠지면 400 이다 (`validation/lol/LolConditionValidator.java`). **값은 단(division)까지 적는다** — `GOLD` 가 아니라 `GOLD_2` 다. 허용되는 이름의 원본은 자바 enum 이 아니라 Redis ZSET `qm:gameconfig:LOL:tier` 다 (32개) | **구현이 앞서 있다.** 조건 5번째가 아니라 derived/자격 조건이다 (docs/02 §6, CLAUDE.md §2). 계약에 추가해야 하고, 계정 연동이 붙으면 요청 필드에서 사라진다 |

| 14 | `KeyCondition.type` 의 PUBG 값 | `PLAY_STYLE` | `PLATFORM` (`domain/condition/KeyConditionType.java`), 값은 `STEAM` / `KAKAO` | **코드가 맞다.** 스팀·카카오는 서로 파티를 맺을 수 없어 플레이 스타일(취향) 대신 플랫폼(hard)을 핵심 조건으로 교체했다. `openapi.yaml` 의 enum 을 고쳐야 한다. 근거는 enum 클래스 주석 |

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
| 503 | `MATCHING_UNAVAILABLE` | Redis 장애 또는 후보 풀 락 획득 실패. `Retry-After: 5` 헤더 동반 (INV-10 fail-closed) |

`501 NOT_IMPLEMENTED` 는 **없어졌다** — 상태 조회가 구현되면서 그 스텁이 사라졌다(위 #5).
**상태 조회는 에러를 내지 않는다.** 활성 요청이 없어도 404 가 아니라 `200 {"status":"IDLE"}` 이다 —
"큐에 없음"은 오류가 아니라 답의 한 갈래이고, 클라이언트는 어느 경우든 `status` 하나로 화면을
고르면 된다. 나갈 수 있는 실패는 Redis 장애의 503 뿐이다.

`ProposalController` 가 실제로 내는 것은 셋이다 — `PROPOSAL_CONFLICT`(409, 이미 확정됐거나
다른 참가자가 거절했거나 수락해 놓고 거절을 누른 경우) · `PROPOSAL_NOT_FOUND`(404, 진행 중인
제안이 없다) · `NOT_PROPOSAL_MEMBER`(403, 남의 제안). 예전에 여기 적혀 있던
`PROPOSAL_EXPIRED`(410) · `PROPOSAL_DECLINED` · `PROPOSAL_ALREADY_RESPONDED` 는
`ProposalService` 가 껍데기이던 시절의 목록이고, 지금 코드에는 없다.

---

## 메워진 구멍 — "지금 상태"를 물어볼 곳이 생겼다 (2026-09-17)

계약은 매칭 결과를 알리는 경로를 **두 가지**로 정의한다.

1. SSE `MATCH_PROPOSAL_CREATED` / `MATCH_CONFIRMED` (`events.md`)
2. 폴링 `GET /api/v1/match-requests/{id}` (`openapi.yaml`)

**둘 다 메워졌다.** 다만 2번은 **계약과 다른 경로**로 메워졌다 — 위 표 #5 참고.
아래 본문은 그 구멍이 왜 있었는지의 기록이라 남겨 둔다.

- SSE — 이 앱의 몫인 Redis Pub/Sub publish 는 **된다**
  (`notification/PushPublisher.java`, 채널 `qm:pubsub:push:{userId}`). 정원이 차면
  `MATCH_PROPOSAL_CREATED` 가, 전원 수락으로 확정되면 `MATCH_CONFIRMED` 가, 시한이 지나면
  `MATCH_PROPOSAL_EXPIRED` 가 파티 전원에게 나간다. SSE 배달은 `app:realtime` 몫이고
  이 저장소에 `SseEmitter` 를 넣지 않는 것이 맞다 (CLAUDE.md §3).
- 폴링 — **구현됐다.** `GET /match-requests?userId=` → `service/MatchQueryService` 가
  활성 요청 HASH 와 파티 HASH, 수락자 SET 을 읽어 `IDLE` / `QUEUED` / `PROPOSED` / `MATCHED`
  네 갈래로 답한다. 경로가 계약과 다른 이유는 위 표 #5 에 있다.

**왜 필요했나 (기록).**

1. **알림은 사건만 전한다.** "방금 이렇게 됐다"는 말하지만 "지금 이렇다"는 못 한다.
   앱을 껐다 켜거나 새로고침하거나 다른 기기로 접속한 클라이언트는 아무것도 모른 채
   "매칭 시작" 버튼을 그리고, 누르면 409 `ALREADY_QUEUED` 를 받는다. 확정된 사용자는
   활성 요청에 `status=PARTY` 가 찍힌 채 남아 있어 더욱 그렇다. 게다가 Pub/Sub 은
   at-most-once 라 놓친 알림의 복구도 이 REST 몫이다 — 계약이 "놓친 상태는 REST 로
   복구한다"고 정한 그 REST 가 이것이다.

(옛 2번 "수락 집계·확정이 없다"는 해소됐다 — `POST /proposals/{id}/accept|decline` 이
동작하고 만료도 스위퍼가 처리한다.)

**이 조회가 답하지 못하는 것 두 가지.**

- **취소·만료의 구분.** 둘 다 활성 요청 키를 지우므로 `IDLE` 과 같아진다(위 #4-1).
  "왜 큐에서 빠졌는지"를 클라이언트가 알려면 알림(`MATCH_CANCELLED` /
  `MATCH_PROPOSAL_EXPIRED`)을 받았어야 하는데, 그 알림은 at-most-once 라 놓칠 수 있다.
  그때 사용자는 이유를 모른 채 대기 화면에서 시작 화면으로 돌아간다.
- **파티 상세(누가 같이 있는지).** 여기서 답하지 않는다. 진행 중인 매칭 상태만 이 앱의
  소유이고 확정된 파티는 `app:platform` 의 것이다 (CLAUDE.md §9). 그래서 `MATCHED` 응답은
  `partyId` 까지만 준다.

남은 작업과 우선순위는 `HANDOFF.md` 의 2026-09-17 블록에 있다.

`load-test/match_latency.py` 가 성사를 감지하려고 HTTP 가 아니라 **Redis 를 직접 폴링**하는
것(`HGET qm:user:active-request:{uid} partyId` → `HGET qm:party:{pid} size`)은 그 스크립트가
알림 도입 전에 쓰였기 때문이다. 알림 경로가 생겼으니 `PSUBSCRIBE qm:pubsub:push:*` 로
바꿀 수 있다.
