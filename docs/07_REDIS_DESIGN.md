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
qm:lock:claim:{userId}                         STRING ownerToken + short TTL
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
anchor를 정하고, 그와 파티가 될 수 있는 bucket을 tier 좋은 순으로 고른 뒤, 고른 bucket에서
오래 기다린 순으로 quota만큼 꺼낸다 (`queue-bucket-slice.lua`).

anchor를 누가 정하느냐로 입구가 둘이다.

**평소 경로 (docs/03 §11).** 요청이 들어온 bucket이 곧 anchor다. 어느 bucket이 상대가 될 수
있는지는 조건만으로 정해지므로, depth를 읽을 때부터 그 subset만 읽는다. mode 전체 bucket을
훑지 않는다.

**안전망 경로.** 어디를 볼지 모르는 채로 부른다. mode의 모든 bucket key에 대해 head score와
크기를 읽고(`queue-bucket-depths.lua`), 가장 오래 기다린 bucket부터 anchor로 삼는다.
예산이 남으면 다음으로 오래된 anchor로 넘어간다.

aging(docs/03 §6)은 anchor 선택과 bucket 내부 정렬 양쪽에서 유지된다. 평소 경로에서도
bucket 안은 오래 기다린 순이므로, 방금 들어온 사람이 자기 trigger로 앞자리를 새치기하지 못한다.

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

현재 구현:
- `SET NX PX` 사용자별 분산 락을 정렬된 순서로 획득한다. 실시간과 예약이 같은 키를 쓴다.
- `SessionCallback` 안에서 WATCH 후 락 소유권·활성 요청 ID·활성 제안 부재를 Java로 확인한다.
- conflict → 선점 기록을 쓰지 않고 실패한다. 부분 획득한 작업 락은 자기 토큰일 때만 해제한다.
- success → MULTI/EXEC으로 모든 `user:active-proposal` 설정, 참가자 집합과 queue 제거를 적용한다.
- 참가자마다 조건이 다를 수 있으므로 queue removal은 **참가자별 bucket key**를 받는다.
- 작업 락 기본 임대는 10초(`MATCH_CLAIM_LOCK_LEASE_MS`)다. 제안 수락 TTL과 별개다.
- 만료/요청 변경으로 WATCH가 깨지면 EXEC을 거부한다. mutex 해제 실패는 로그·지표에 남기고 임대 만료로 정리한다.
- 선점 업무 Lua 두 개는 제거했다. 작업 락과 제안 기록의 소유권 비교·삭제 및 큐 조회/등록 Lua는 유지한다.
- Sentinel 전환의 기록 유실을 보장하는 설계는 아니다. DB 제약조건이 마지막 방어선이다.

채택 이유·실험 결과·실패 처리와 지표는 [분산 락 선점 설계](18_REDIS_CLAIM_LOCK.md)에 정리한다.

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

## 11. High availability (Sentinel)
운영에서는 master 1 + replica 2 + sentinel 3으로 띄우고 애플리케이션은 sentinel을 통해 붙는다.
정족수는 2다.

```text
REDIS_SENTINEL_MASTER=queuemate
REDIS_SENTINEL_NODES=host-a:26379,host-b:26379,host-c:26379
```

두 값이 비어 있으면 `REDIS_HOST`/`REDIS_PORT`의 단일 인스턴스로 붙는다.
sentinel로 붙을 때 `REDIS_HOST`는 쓰이지 않으므로 주지 않아도 된다. 다만 둘 다 비어 있으면
기동을 막는다. 설정을 통째로 빠뜨린 배포가 localhost의 빈 Redis에 붙으면 guard가 전부
통과해 INV-1/INV-2가 조용히 깨진다.

판단은 `RedisConfig`가 한다. 빈 문자열을 "sentinel을 쓰겠다"로 읽지 않기 위해서다.

### 11.1 읽기는 반드시 master에서
`readFrom`을 replica로 돌리지 않는다. 복제 지연 동안 `user:active-request`와
`user:active-proposal`이 낡은 값을 돌려주면 그 순간 INV-1과 INV-2가 조용히 깨진다.
이건 성능 선택지가 아니라 정합성 요구다.

### 11.2 failover는 write를 잃는다
Redis 복제는 비동기다. master가 ack했지만 복제되지 않은 write는 승격 때 사라진다.
매칭 guard는 전부 이 write 하나에 걸려 있으므로 세 겹으로 막는다.

| 겹 | 무엇 | 어디 |
|---|---|---|
| 1 | 복제가 밀리면 write 자체를 거부한다 | `min-replicas-to-write 1`, `min-replicas-max-lag 10` |
| 2 | INV-1은 DB partial unique index가 받는다 | `match_requests_one_active_per_user_idx` |
| 3 | INV-2는 DB PK가 받는다 | `active_proposal_claims.user_id` (V3) |

1은 창을 줄일 뿐 0으로 만들지 못한다. 확실한 관문은 2와 3이다.

failover 진행 중 명령 실패는 `DataAccessException`으로 올라오고, 기존 fail-closed 경로가
그대로 받는다 (§9, INV-10). 새 매칭이 몇 초 멈추는 것은 의도된 동작이다.

### 11.3 queue 재구성
failover로 대기열 항목이 유실되면 `MatchQueueRecoveryService.reconcile()`이 60초 주기로
DB의 QUEUED 요청을 근거로 다시 세운다 (§10). guard가 남고 큐만 빈 상태도 여기서 복구된다.

### 11.4 연습
`docker compose --profile ha up`으로 같은 모양을 띄우고 `scripts/redis-failover-drill.sh`로
승격을 확인할 수 있다. 단일 호스트에서는 sentinel이 호스트와 함께 죽으므로 실제 HA가 아니다.
연습과 검증용이다.
