# 18. Redis 분산 락 기반 참가자 선점

2026-09-14, 실시간·예약 제안의 선점 업무 로직을 Lua에서 Java로 옮긴다.
Lua의 원자성은 인정하며, 선점 규칙을 Java에서 탐색·수정·디버깅·테스트하기 위해 Redis 분산 락을
채택한다. 원자성 보장은 유지하고, 유지보수 편의를 위해 추가 비용을 수용하는 결정이다.

## 비교 결과와 채택 이유

[전환 전 비교 실험](../harness/studies/redis-claim/RESULTS.md)은 실제 JVM 4개에서 96,000건을 측정했다.
그 실험의 분산 락 대안은 Lua 대비 처리량이 약 15~24%, 선점 p99는 약 3.4~6.7배였다.
측정된 비용을 수용하고 업무 로직의 Java 관리를 우선해 분산 락 전환을 선택했다.
해당 수치는 당시 Lettuce 실험 구현의 결과이며, 이번 Spring 저장소의 성능 측정값은 아니다.

설명할 때는 다음처럼 구분한다.

> Lua 블로킹과 별도 언어로 관리하는 업무 로직의 유지보수 비용을 검토했다. 부하 비교에서 분산 락의
> 성능 비용을 확인했으며, 성능 이점을 주장하지 않고 Java에서 선점 로직을 관리하기 위해 전환했다.
> 사용자별 분산 락과 상태 재검증을 적용하고, 임대 만료·부분 실패 및 DB 롤백 후 재매칭을 검증했다.

## 범위

- `ProposalClaimRepository`: 실시간·예약 선점의 공통 Java 검증과 Redis 트랜잭션.
- `RedisClaimLock`: 사용자별 작업 락 획득, 부분 실패 정리, 안전한 해제.
- 기존 `atomic-proposal-claim.lua`, `atomic-reservation-claim.lua`는 제품에서 제거한다.
- `release-claim-lock.lua`, `release-proposal-claim.lua`는 짧은 소유권 비교·삭제 원시 연산으로 유지한다.
- 큐 등록·삭제·조회 Lua는 별개 경로로 유지한다. Lua 사용 전체를 없앤 변경은 아니다.
- 기존 DB 모드 잠금·행 잠금·활성 참여 PK 및 유일성 제약조건은 유지한다.
- API 응답과 이벤트 계약은 변경하지 않는다.

## 처리 순서

1. 참가자 ID를 기준으로 `qm:lock:claim:{userId}` 키를 정렬한다.
2. UUID 소유권 토큰으로 `SET NX PX`를 수행한다. 충돌하면 즉시 실패하며 이미 얻은 자기 락만 해제한다.
3. 락을 모두 얻으면 같은 연결을 사용하는 `SessionCallback`을 시작한다.
4. 작업 락·활성 제안 키·실시간 활성 요청 키·제안 참가자 집합 키를 WATCH한다.
5. Java에서 모든 작업 락의 소유권, 활성 제안 부재, 기대한 요청 ID, 제안 ID 미사용 및 큐 타입을 확인한다.
6. MULTI/EXEC으로 전원 `active-proposal` 설정, 참가자 집합·TTL 설정, 실시간 큐 제거를 적용한다.
7. WATCH 충돌이면 실패한다. 성공 후 작업 락을 해제해도 `active-proposal` 배정 기록은 남는다.
8. 기존 매처가 DB에 제안·참가자·사용자별 활성 참여 기록을 저장한다. DB 롤백 시 기존 보상 경로를 따른다.

작업 락 기본 임대는 10초다. `MATCH_CLAIM_LOCK_LEASE_MS`로 설정하며 1ms 미만이면 시작을 거부한다.
제안 수락 제한 시간인 `PROPOSAL_TTL_SECONDS`와 독립적이다. 락을 자동 연장하지 않으며,
임대가 검증 후 만료되면 WATCH가 오래된 작업의 저장을 거부한다. 실시간과 예약은 같은 사용자 키를
사용하므로 게임·모드·매칭 종류가 달라도 동일 사용자를 중복 선점할 수 없다(정상 단일 master 기준).

## 실패와 복구

| 상황 | 처리 |
| --- | --- |
| 한 명의 작업 락 획득 실패 | 자기 소유의 부분 획득 락을 해제하고 false |
| 검증 후 락 만료·요청 교체·제안 상태 변경 | WATCH 충돌로 쓰기를 적용하지 않고 false |
| EXEC 전 예외 | DISCARD 시도, 작업 락 정리, 오류 전파로 새 매칭 중단 |
| EXEC 응답 유실 | 반영 여부가 불명확하므로 성공으로 처리하거나 무조건 재실행하지 않음 |
| 작업 락 해제 오류 | 이미 성공한 선점 결과는 보존, 지표·로그 기록 후 유한한 임대로 정리 |
| DB 저장 실패 | 기존 DB 롤백 콜백이 자기 제안 기록만 해제하고 실시간 큐를 원래 대기 시각으로 복구 |
| Redis 장기 장애 | 새 매칭 fail-closed, 남은 제안 기록은 TTL, 큐는 DB 기준 reconciliation으로 복구 |

EXEC 결과가 유실되면 DB에 제안 없이 Redis 배정 기록만 남을 수 있다. 기록은 제안 TTL로 만료되고,
DB에 남은 QUEUED 요청은 기존 60초 주기 정합성 복구가 큐에 되돌린다. 실패 직후 즉시 복구를
보장하지 않는다. Redis 트랜잭션은 런타임 명령 오류를 자동 롤백하지 않으므로 오류를 성공으로
반환하지 않는다. 관리자가 동시 키 타입 변경 등으로 상태를 훼손하는 상황까지 원자적 롤백하지 않는다.

Sentinel master 전환 시 mutex와 배정 기록 모두 유실될 수 있다. 사용자별 `active_proposal_claims`
PK와 요청의 유일성 제약조건이 최종 중복 저장을 막는다. 분산 락 도입 자체가 고가용성의 무손실
보장을 추가하지 않는다. 기존 단일 Redis/Sentinel 연결 설정을 그대로 사용하며 별도 Redis 클라이언트
설정이나 Redisson 의존성은 추가하지 않는다.

## 관측과 검증

- `qm.matching.claim.duration{source=realtime|reservation,outcome=success|conflict|error}`
- `qm.matching.claim.lock.conflict`
- `qm.matching.claim.lock.release.failure`
- `qm.matching.claim.transaction.conflict`
- `qm.matching.claim.transaction.cleanup.failure`

태그에 사용자·제안 ID를 넣지 않는다. 오류 로그에 제안 ID와 실시간/예약 출처를 남긴다.

`ProposalDistributedLockTest`는 독립 Redis 연결 팩토리로 실시간·예약 동시 선점, 부분 락 실패,
검증 후 임대 만료와 요청 교체, EXEC 전 예외, EXEC 응답 유실, 락 해제 실패를 검증한다.
`RealtimeMatchingIntegrationTest`, `ReservationMatchingIntegrationTest`는 DB 롤백 뒤 재매칭을 확인한다.
기존 정합성·API·이벤트·복구 테스트도 전환된 저장소를 통해 실행한다.

검증 명령:

```bash
cd backend
./gradlew test bootJar
```

2026-09-14 최종 회귀 실행은 645개 중 644개 통과, 1개 실패다. 실패한
`MatchTriggerIntegrationTest.requeuedWaiterIsMatchedAgain`은 작업 전에 이미 수정되어 있던 테스트로,
세 명을 등록해 두 명을 매칭한 뒤 대기 인원이 0명이라고 기대한다. 별도 사본의 전환 전 Lua 코드에서도
같은 실패가 재현됐다. 해당 사용자 변경은 수정하거나 이 전환 커밋에 포함하지 않는다.
원래 커밋된 자동 재매칭 테스트 4개는 별도 사본에서 새 분산 락 구현으로 모두 통과했다.
새 분산 락 경합 테스트 9개, 실시간·예약 DB 롤백 후 재매칭 테스트도 위 통과 수에 포함된다.

전체 테스트의 컨텍스트 종료 지연을 줄이기 위해 최종 실행은 임시 Gradle init 설정으로
`spring.lifecycle.timeout-per-shutdown-phase=1s`, `spring.test.context.cache.maxSize=1`을 사용했다.
제품 설정과 테스트의 기대 결과는 바꾸지 않았다. `bootJar`도 생성했으며 실행 파일에 기존 선점 Lua
두 개가 없고 `RedisClaimLock`과 락 해제 리소스가 포함된 것을 확인했다.

기술 근거:

- [Spring Data Redis SessionCallback과 트랜잭션](https://docs.spring.io/spring-data/redis/reference/redis/transactions.html)
- [Redis WATCH의 만료 감지와 트랜잭션 한계](https://redis.io/docs/latest/develop/using-commands/transactions/)
- [Redis 분산 락의 임대·소유권·복제 한계](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
