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
- WebSocket session routing
- rate limit

## 2. Key naming
```text
qm:queue:{game}:{mode}:{key}:{voice}:{purpose} ZSET(requestId, queuedAtEpoch)
qm:request:{requestId}                         HASH + TTL
qm:user:active-request:{userId}                STRING requestId
qm:user:active-proposal:{userId}               STRING proposalId + TTL
qm:proposal:{proposalId}                       HASH + TTL
qm:proposal:members:{proposalId}               SET userIds
qm:reservation:slot:{game}:{mode}:{slot}       SET reservationIds
qm:party:presence:{partyId}                    HASH(userId -> sessionId)
qm:party:ready:{partyId}                       SET userIds
qm:rate:{scope}:{identity}:{window}            counter
qm:lock:reservation-sweep:{game}:{mode}        short lease
```

## 3. Queue semantics
- ZSET score = 최초 queuedAt. 재시도해도 보존.
- request detail은 HASH/JSON cache.
- DB에 match_request history를 남기되 매칭 hot path는 Redis 중심.

### 3.1 Condition bucket
대기열은 모드 하나당 ZSET 하나가 아니라 **조건이 완전히 같은 사람끼리 묶인 bucket ZSET**이다.
key suffix는 docs/02의 조건 셋이다.

```text
{key}      게임별 핵심 조건 값 (LoL position / VALORANT role / PUBG play style)
{voice}    REQUIRED | OPTIONAL | NO_VOICE
{purpose}  RANK_UP | NORMAL | FUN
```

mode당 bucket 수는 유한하고 작다. LoL 54, VALORANT 36, PUBG 27.

**왜 나누는가.** 모드당 ZSET 하나면 후보를 "앞에서부터 N개"로 읽을 수밖에 없고, 서로 매칭될 수
없는 사람들이 그 창을 채우면 뒤에 있는 실제 조합이 영원히 보이지 않는다. QUEUED 요청에는
만료가 없으므로 한 번 그 상태가 되면 그 모드는 스스로 회복하지 못한다. 매칭이 안 되는 사람만
앞머리에 농축되기 때문에 이 상태는 우연이 아니라 붐비는 큐의 평형점이다.

bucket으로 나누면 "이 사람과 파티가 될 수 있는 사람"이 어느 key에 있는지 조건만으로 결정되므로,
읽기 전에 대상을 고를 수 있다.

**bucket은 색인이지 판정이 아니다.** reservation slot index와 같은 원칙이다 (§8).
어떤 bucket끼리 같은 파티가 될 수 있는지는 `ConditionCompatibility`가 정하고,
Redis는 그 결정에 따라 읽을 key 목록을 받을 뿐이다. key 이름이 판정을 대신하기 시작하면
조건이 하나 바뀔 때마다 key schema가 따라 바뀐다.

### 3.2 Reading candidates
1. mode의 모든 bucket key에 대해 head score와 크기를 읽는다 (`queue-bucket-depths.lua`).
2. 가장 오래 기다린 bucket을 anchor로 삼아, 그와 파티가 될 수 있는 bucket을 tier 좋은 순으로
   고른다. 예산이 남으면 다음으로 오래된 anchor로 넘어간다.
3. 고른 bucket에서 오래 기다린 순으로 quota만큼 꺼낸다 (`queue-bucket-slice.lua`).

aging(docs/03 §6)은 anchor 선택과 bucket 내부 정렬 양쪽에서 유지된다.
가장 오래 기다린 사람은 언제나 후보에 들어가고 언제나 첫 seed가 된다.

### 3.3 Bucket key를 아는 쪽
큐를 건드리는 모든 경로는 조건을 함께 들고 있어야 한다. 조건 없이 requestId만으로는
어느 bucket에 있는지 알 수 없기 때문이다. DB 행이 이미 사라진 stale 항목은
읽어 온 bucket을 기억해 두고 그 key에서만 지운다.

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
- 참가자마다 조건이 다를 수 있으므로 queue removal은 **참가자별 bucket key**를 받는다

## 6. Proposal TTL
Redis key expiry만 믿지 않는다.
- expiresAt을 DB에도 저장
- keyspace notification을 필수 의존성으로 두지 않는다.
- scheduler가 expired proposal을 정리할 수 있어야 한다.

## 7. Block cache
blocks는 DB source of truth.
빈번한 candidate filter를 위해:
```text
qm:block:{userId} SET blockedUserIds
```
캐시 miss 시 DB load.
block 생성 시 양방향 candidate exclusion에 필요한 캐시 invalidate.

## 8. Reservation index
30분 slot마다 reservationId를 SET에 등록한다.
예약 수정/취소는 기존 모든 slot에서 제거 후 다시 색인한다.

## 9. Failure policy
Redis unavailable:
- 새 realtime request 생성 금지
- 새 proposal 생성 금지
- reservation matching pause
- 기존 WebRTC party media는 영향 없음

DB fallback으로 비원자적 matching을 시도하지 않는다.

## 10. Persistence
active match queue는 짧은 수명의 상태지만 Redis restart 시 사용자 경험 손실을 줄이기 위해 운영 환경에서는 provider의 persistence/replication 옵션을 사용한다.
정확한 recovery는 DB의 active request와 heartbeat를 이용해 queue를 재구성하는 admin operation으로 제공한다.

