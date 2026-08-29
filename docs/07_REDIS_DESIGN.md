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
qm:queue:{game}:{mode}                         ZSET(requestId, queuedAtEpoch)
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
