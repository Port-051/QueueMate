<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 docs/07_REDIS_DESIGN.md -->
<!-- 커밋: 825d673 (git show feature/frontend:docs/07_REDIS_DESIGN.md) -->
<!-- 원문 verbatim. 수정하지 말 것 — 변경은 queueMate 원본에서 하고 다시 가져온다. -->

# 07. Redis Design

## 1. Role
Redis는 캐시가 아니라 **실시간 매칭 정합성 구성요소**다.

사용:
- queue ordering
- one-user-one-active-request guard
- atomic proposal claim
- proposal TTL
- reservation slot index
- party presence/ready cache
- 연결/세션 routing
- rate limit
- block read model (`qm:block:{userId}` Set)
- **Pub/Sub — 사용자 알림 fanout** (발행 앱 → Pub/Sub → `app:realtime` → SSE)
- **game mode config 캐시** (DB가 원본, docs/11 #27)
- **실시간 매칭 요청 / 진행 중 proposal / 수락 집계** — DB에 복제하지 않는다

Redis는 **진행 중인 실시간 매칭 상태의 source of truth**다 (docs/11 #27, docs/14 §19).
DB는 확정된 것만 안다. `match_requests` 테이블은 없다.

**앱 간 도메인 이벤트는 Redis가 아니라 SQS FIFO로 간다 (docs/11 #21).**
Redis Streams는 컨슈머 그룹으로 읽는 순간 순서가 깨져 `BlockCreated`/`BlockRemoved`
역전이 가능하므로 채택하지 않는다.
Redis는 queue + block read model + 알림 fanout 3역할을 겸하므로 여전히 장애 반경이 넓다.
§9 failure policy를 fail-closed로 지킨다.

## 2. Key naming
```text
qm:queue:{game}:{mode}                         ZSET(requestId, queuedAtEpoch)
qm:request:{requestId}                         HASH + TTL
qm:user:active-request:{userId}                STRING requestId
qm:user:active-proposal:{userId}               STRING proposalId + TTL
qm:proposal:{proposalId}                       HASH + TTL
qm:proposal:members:{proposalId}               HASH(userId -> PENDING|ACCEPTED|DECLINED) + TTL
qm:reservation:slot:{game}:{mode}:{slot}       SET reservationIds
qm:party:presence:{partyId}                    HASH(userId -> sessionId)
qm:party:ready:{partyId}                       SET userIds
qm:rate:{scope}:{identity}:{window}            counter
qm:lock:reservation-batch                      short lease (전역 1개, docs/11 #24)
qm:block:{userId}                              SET blockedUserIds (read model)
qm:gameconfig:modes:{game}                     (없앴다 2026-09-15 — 모드 HASH 존재로 판단. docs/GAME_CONFIG.md)
qm:gameconfig:{game}:{mode}                    HASH + TTL (mode 설정 캐시, DB가 원본)
qm:pubsub:push:{userId}                        PUB/SUB channel (사용자 알림 fanout)
qm:sse:conn:{userId}                           SET connectionIds (연결 관측용)
qm:sse:lastevent:{connectionId}                STRING eventId + TTL (Last-Event-ID 재개)
```

`qm:lock:reservation-sweep:{game}:{mode}`는 폐기했다. 배치가 단일 인스턴스이므로
게임/모드별 락이 필요 없다.

`qm:proposal:members:{proposalId}`는 **참가자 목록이자 수락 집계 저장소**다. SET에서 HASH로
바꾼 이유는 SET으로는 "아직 안 누름"과 "거절함"을 구분할 수 없기 때문이다 (docs/11 #28).
수락 집계용 키를 따로 두지 않는다. 참가자 목록과 수락 상태가 두 키로 갈리면 둘이 어긋날 수
있고, 하나의 HASH여야 Lua 한 번으로 전원 판정까지 끝난다.

## 3. Queue semantics
- ZSET score = 최초 queuedAt. 재시도해도 보존. decline 후 큐 복귀 시에도 보존한다.
- request detail은 `qm:request:{requestId}` HASH가 **원본**이다. 캐시가 아니다.
- **DB에 match_request history를 남기지 않는다** (docs/11 #27). 요청은 확정되면
  `match_proposals.condition_snapshot_json`으로만 흔적을 남기고, 확정되지 못한 요청은
  아무 기록도 남기지 않는다. 매칭 시도 지표는 Prometheus로만 본다.

## 4. User guard
`SET qm:user:active-request:{userId} requestId NX`
실패 시 duplicate request = HTTP 409.

삭제는 값이 현재 requestId와 같은지 확인하는 compare-and-delete Lua를 사용한다.

## 5. Atomic proposal claim
필수 조건:
- 참가자 모두 active request가 존재
- 참가자 모두 active proposal key가 없음
- 모든 guard를 한 atomic operation에서 설정

추천 구현:
- Lua script를 repository에 버전 관리
- script input: proposalId, ttl, userIds, requestIds
- any conflict → 아무 변경 없이 fail
- success → 모든 `user:active-proposal` set + queue removal

## 5-1. 수락 집계
`qm:proposal:members:{proposalId}` HASH에 `userId -> PENDING|ACCEPTED|DECLINED`를 담고,
수락/거절 처리는 **Lua 스크립트 1회 원자 실행**으로 한다 (docs/11 #28, docs/14 §18).

한 번의 실행에서 아래를 모두 처리한다.
- 제안 생존 확인 (`qm:proposal:{proposalId}` 존재 + 상태, INV-5)
- 해당 userId가 참가자인지 확인
- 이미 눌렀는지 확인 (중복 수락 차단, INV-4)
- 상태 기록 후 **전원 ACCEPTED 판정**
- 판정 결과에 따른 상태 전이 (거절 1건이면 즉시 `DECLINED`, 전원 수락이면 확정 절차로 넘김)

`GET → 애플리케이션 판단 → SET`으로 구현하지 않는다 (docs/03).
DB 트랜잭션은 **전원 수락 판정이 난 뒤 1회만** 친다.

## 6. Proposal TTL
Redis key expiry만 믿지 않는다.
- 진행 중 proposal은 Redis에만 있으므로 `expiresAt`은 `qm:proposal:{proposalId}` HASH 필드다.
  DB `match_proposals.expires_at`은 **확정된 제안의 기록**일 뿐 만료 판정에 쓰지 않는다.
- keyspace notification을 필수 의존성으로 두지 않는다.
- scheduler(sweeper)가 expired proposal을 정리하고 참가자 요청을 큐로 되돌릴 수 있어야 한다.
  이때 `queuedAt`을 보존한다.

## 7. Block read model
`blocks` 테이블은 `social` 스키마의 source of truth다.
`matching`은 다른 스키마이므로 JOIN이나 동기 REST 호출로 읽지 않는다.
`social`이 block 생성/해제 이벤트를 outbox로 발행하고 `matching`이 소비해 read model을 유지한다.

```text
qm:block:{userId} SET blockedUserIds
```

**이 read model은 후보 필터링용이며 INV-6의 최종 보증 지점이 아니다.**
이벤트 전파에는 지연 창이 있어 결과적 일관성만 보장한다. 여기서는 그 창을 허용한다.
목적은 후보 수십~수백 명 중 명백히 안 맞는 쪽을 싸게 걸러내는 것이다.
INV-6의 실제 보증은 최종 claim 직전 `shared_read.blocked_pairs` 뷰를 **동기로** 읽는
DB 재검증이다 (docs/11 #19, docs/06 Schema layout, docs/03 §7).

- hot path candidate filter는 이 SET을 O(1)로 조회한다.
- 양방향 exclusion이 필요하므로 blocker/blocked 양쪽 key를 함께 갱신한다.
- 이벤트는 at-least-once이므로 `SADD`/`SREM` 기반 멱등 반영을 사용한다.
- 이 read model은 캐시가 아니라 정합성 구성요소다. miss를 DB fallback으로 메우지 않는다.
  §9에 따라 조회 실패는 fail-closed 한다.
- 전체 rebuild는 `social` 스키마 재적재 admin operation으로 제공한다.

## 7-1. 앱 간 도메인 이벤트는 Redis가 아니다
outbox relay는 **SQS FIFO**로 발행한다 (docs/11 #21). Redis는 이 경로에 관여하지 않는다.

```text
ProposalConfirmed.fifo   matching → party    (party 생성)
PartyClosed.fifo         party    → social   (recent_players 구축)
BlockChanged.fifo        social   → matching (block read model 갱신)
```

- `MessageGroupId`: block은 정규화된 차단 쌍(`min(id):max(id)`),
  나머지는 aggregate id(`proposalId` / `partyId`). 그룹 단위 순서가 보장된다.
- `MessageDeduplicationId`는 outbox id. relay 재발행 중복을 5분 창에서 억제한다.
- 전달 보장은 at-least-once. 소비자는 여전히 멱등해야 한다.
- 재시도/DLQ/적체 알람은 큐 설정(`maxReceiveCount` + DLQ)으로 얻는다.
  Redis에서 pending entry를 직접 claim/재처리하던 코드는 필요 없다.

## 7-2. 알림 fanout (Pub/Sub)
Redis가 담당하는 것은 **휘발성 사용자 알림**뿐이다 (docs/11 #22).

```text
발행 앱 → PUBLISH qm:pubsub:push:{userId}
       → app:realtime 전 인스턴스 수신
       → 해당 userId 연결을 가진 인스턴스만 로컬 필터 통과
       → SSE 전송
```

- 브로드캐스트 + 로컬 필터이므로 **sticky session이 필요 없다.**
- 알림은 휘발성이라 재전송 보장이 없다. 내구성이 필요한 것은 SQS로 보낸다 (§7-1).
- 구독자가 없으면 메시지는 버려진다. 오프라인 사용자의 상태 복구는 REST 재조회로 한다.

## 8. Reservation index
30분 slot마다 reservationId를 SET에 등록한다.
예약 수정/취소는 기존 모든 slot에서 제거 후 다시 색인한다.

**`qm:reservation:slot:*`는 제거 후보다 (docs/11 D7).** 예약 매칭은 1분 주기 배치의
콜드 패스이므로 인덱스 걸린 DB 쿼리로 충분할 가능성이 높다. 실측 후 제거 여부를 정한다.

## 9. Failure policy
Redis unavailable:
- 새 realtime request 생성 금지
- 새 proposal 생성 금지
- game mode config 캐시를 읽을 수 없으므로 매칭 루프 자체가 멈춘다 (docs/11 #27).
  DB 직접 조회로 우회해 매칭을 이어가지 않는다. INV-10과 같은 판단이다.
- 진행 중이던 요청/proposal/수락 상태는 유실되며 **DB로부터 재구축하지 않는다** (§10, docs/11 #29)
- block read model을 조회할 수 없으면 후보 필터링을 할 수 없으므로 새 proposal 생성을
  fail-closed 한다. INV-6은 `social.blocks` 직접 조회나 임의의 크로스 스키마 JOIN으로
  우회하지 않는다.
- 단, INV-6 2단계 검증(`shared_read.blocked_pairs` 동기 조회)은 DB 경로이므로 Redis 장애와
  무관하게 동작한다. 즉 Redis 장애로 막히는 것은 **후보 필터링과 Redis atomic claim**이지
  최종 재검증 자체가 아니다. Redis 장애 중에는 claim이 불가능하므로 결과적으로 새 proposal이
  만들어지지 않는다.
- reservation matching pause
- **도메인 이벤트는 계속 흐른다.** 전송은 SQS이므로 Redis 장애와 무관하다.
  멈추는 것은 `BlockChanged` 소비자가 Redis에 read model을 반영하는 단계뿐이고,
  실패한 메시지는 SQS가 재시도한다. 한계를 넘으면 DLQ로 간다 (docs/09 §6)
- 알림 fanout(Pub/Sub) 중단. 알림은 휘발성이라 재전송하지 않는다.
  클라이언트는 복구 후 REST 재조회로 상태를 맞춘다
- 기존 WebRTC party media는 영향 없음

DB fallback으로 비원자적 matching을 시도하지 않는다.

## 10. Persistence
active match queue는 짧은 수명의 상태지만 Redis restart 시 사용자 경험 손실을 줄이기 위해 운영 환경에서는 provider의 persistence/replication 옵션을 사용한다.

**DB로부터의 queue 재구축은 불가능하다.** 매칭 요청·진행 중 proposal·수락 집계가 Redis에만
있고 DB에 복제본이 없기 때문이다 (docs/11 #27, #29, docs/14 §19). 이것은 감수하기로 한
트레이드오프이지 미구현이 아니다. 데이터를 잃으면 다음과 같이 처리한다.

- INV-10에 따라 새 매칭을 fail-closed 한다. 부분 복원을 시도하지 않는다.
- 복구 후 **사용자가 다시 요청한다.** 클라이언트는 REST 재조회로 상태를 맞춘다.
- 확정된 파티는 DB + outbox에 있으므로 영향받지 않는다.
- 재구축이 유효한 것은 **block read model뿐**이다 (§7, `social` 스키마 재적재).

운영 절차는 docs/09 §6 Redis outage 런북을 따른다.


---

<!-- 아래는 원문이 아니다. app:matching 저장소에서 덧붙인 주석이다. -->

> **이 저장소에서 바뀐 부분.** 위 본문의 `shared_read.blocked_pairs` 뷰 조회는
> 폐기됐다. 지금은 `social.blocks`를 직접 SELECT 하고, Redis 선필터는 보류했다.
> (2026-09-26 — 그 테이블은 `app:platform` 의 스키마가 `public` 하나로 합쳐져 **`public.blocks`** 가 됐고 두 칸은 bigint 다. 스키마별 롤도 없다 — docs/11 **D-34 · D-25**.)
> 근거와 대가는 `docs/11_DECISION_LOG.md`의 **D-1 / D-2**에 있다.
> 본문은 queueMate 원문이라 고치지 않는다 — 어긋나면 D-1이 우선한다.
