# 동시성 테스트

실시간 매칭 엔진의 정합성은 Lua 스크립트에 몰려 있다. 이 문서는 그 스크립트들이
실제 경합에서 무엇을 지키는지, 그리고 그것을 어떻게 확인했는지를 남긴다.

이 테스트들이 직접 겨냥하는 것은 `claim-request.lua`(INV-1)와 파티 배정 경로
(`create-or-check-party-untiered.lua` + `join-party.lua`)다.

> **2026-09-11 갱신.** 이 문서를 처음 쓸 때는 배정이 `create-or-check-party-untiered.lua`
> **하나**로 끝났다. 지금은 차단 검증(자바)을 끼우려고 **찾기와 합류가 두 스크립트로
> 나뉘어 있고**, 그 사이의 틈은 Lua가 아니라 `redisLock/PoolLock.java` 의 후보 풀 락이 막는다.
> 티어를 보는 모드는 같은 역할의 `-tiered` 판을 쓴다. 아래 본문에서 배정 스크립트를
> 하나로 적은 곳은 그 시절 서술이다.

테스트 위치: `backend/src/test/java/com/queuemate/matching/concurrency/`

## 왜 이 테스트가 있는가

**통과하는 테스트만 있으면 "원래 안 깨지는 것"인지 "우리가 막은 것"인지 구분이 안 된다.**

`ActiveRequestConcurrencyTest`가 초록불이어도, 그게 Lua 덕분인지
애초에 경합이 재현되지 않은 것인지는 그 테스트만으로 알 수 없다.
그래서 **깨지는 버전을 같이 남겼다.** `NaiveVsLuaComparisonTest`는
같은 부하를 두 방식에 그대로 걸어 한쪽은 깨지고 한쪽은 안 깨지는 것을 보인다.

| | 확인되는 것 | 확인 안 되는 것 |
|---|---|---|
| 통과 테스트만 있을 때 | 지금 코드가 안 깨진다 | Lua가 필요했는지 |
| 깨지는 버전을 같이 둘 때 | 위 + **Lua가 원인이라는 것** | — |

깨지는 쪽 테스트(`naiveApproachBreaksUnderConcurrency`)는
`totalDuplicates > 0`을 단언한다. 이게 실패하면 코드가 좋아진 게 아니라
**비교의 전제(경합 재현)가 무너진 것**이다.

## 검증하는 불변식

| 불변식 | 뜻 | 지키는 것 | 테스트 |
|---|---|---|---|
| INV-1 | 한 사용자는 활성 매칭 요청을 1개만 가진다 | `claim-request.lua` | `ActiveRequestConcurrencyTest.onlyOneRequestSucceedsPerUser` |
| INV-1 | (반대 방향) 서로 다른 사용자는 서로를 막지 않는다 | 〃 | `ActiveRequestConcurrencyTest.differentUsersAllSucceed` |
| INV-1 | 순진한 구현이면 실제로 깨진다 | — (대조군) | `NaiveVsLuaComparisonTest.naiveApproachBreaksUnderConcurrency` |
| INV-1 | 같은 부하에서 Lua는 중복 0 | `claim-request.lua` | `NaiveVsLuaComparisonTest.luaApproachHoldsUnderSameLoad` |
| INV-3 | 파티 인원이 `targetPartySize`를 넘지 않는다 | `join-party.lua` / `join-party-tiered.lua` 가 참가자를 `HSET member:{userId}` 한 뒤 **`member:` 필드를 세어** `size >= target` 이면 모든 needs 색인에서 제거, **그리고** `redisLock/PoolLock.java` 의 후보 풀 락 | `PartyJoinConcurrencyTest.partyNeverExceedsTarget` |
| INV-3 / INV-8 | `positionUniqueness=true` 모드에서 같은 포지션이 둘 들어가지 않는다 | 〃 (`unique` 분기의 `ZREM`) | `PartyJoinConcurrencyTest.positionIsUniqueWithinParty` |
| INV-2 근사 / INV-7 | 한 사용자가 두 파티에 동시에 속하지 않는다 | `create-or-check-party-untiered.lua` / `join-party.lua` 가 활성 요청 HASH 의 `partyId` 필드를 하나만 쓰고, 참가자를 `member:{userId}` 필드로 쓴다 | `PartyJoinConcurrencyTest.userBelongsToOnlyOneParty` |

> **INV-3 을 Lua 혼자 지키지 않는다.** `join-party*.lua` 는 참가자를 먼저 `HSET` 하고 세는데,
> 그 자리에 **target 을 넘으면 합류를 거절하는 분기가 없다** (`size >= target` 분기는 색인에서
> 빼고 제안을 여는 일만 한다). 초과를 막는 것은 ① 후보가 needs 색인(=아직 안 찬 파티)에서만
> 나온다는 것과 ② 후보 선택부터 합류까지가 후보 풀 락 안에 있다는 것, 두 겹이다.
>
> **인원 카운터 필드(`size`)는 없다.** 예전에는 `HINCRBY size 1` 로 인원을 올렸다. Lua 는 롤백이
> 없어서 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기가 남고, 페일오버 뒤 재시도가 같은
> 명령을 한 번 더 실행한다 — 그때 `HINCRBY` 는 두 번 더해져 카운터가 실제 멤버 수와 어긋나지만
> (정원 미만 파티가 "꽉 찼다"고 판정된다), `HSET` + 세기는 몇 번 해도 결과가 같다
> (`join-party.lua` 의 `memberCount` 주석). 취소도 같다 — `leave-party.lua` 는 `HDEL member:{userId}`
> 뒤에 세므로 필드를 지우는 것이 곧 인원 감소다.

포지션 유일성은 테스트 코드에서 INV-3으로 표기돼 있으나,
CLAUDE.md 기준으로는 게임별 hard rule이므로 INV-8에도 걸린다.

`partyNeverExceedsTarget`은 각 파티의 `member:*` 필드 수가 5 이하인지 보고, 파티 HASH 에
`size` 필드가 **없는지**도 함께 단언한다. 카운터가 되살아나면 재시도 때 실제 멤버 수와
어긋날 수 있으므로 그 회귀를 여기서 잡는다.

## 측정 결과

### 순진한 방식 vs Lua

조건: **같은 `userId`로 100 스레드 동시 요청 × 5 라운드. 인위적 지연 없음.**
매 라운드 시작 전 Redis를 비운다.

| | 순진한 방식 (자바에서 `EXISTS` 확인 후 `HSET`) | Lua |
|---|---|---|
| round 1 통과 | 100건 (중복 99) | 1건 (중복 0) |
| round 2 통과 | 100건 (중복 99) | 1건 (중복 0) |
| round 3 통과 | 100건 (중복 99) | 1건 (중복 0) |
| round 4 통과 | 100건 (중복 99) | 1건 (중복 0) |
| round 5 통과 | 100건 (중복 99) | 1건 (중복 0) |
| **누적 중복** | **495건** | **0건** |

읽는 법:

- 정상이면 라운드마다 통과 1건이다. 100건이 통과했다는 것은
  **한 사용자에게 활성 요청이 100개 생겼다**는 뜻이다.
- **라운드별 편차가 0이다.** 플레이키한 테스트가 아니라 재현되는 결과다.
- `Thread.sleep` 같은 인위적 지연을 넣지 않았다. 100 스레드의 자연 경합만으로 재현된다.
- 스레드는 가상 스레드로 만들어 `CountDownLatch`로 전부 대기시켰다가 한 번에 출발시킨다.
  순차로 실행하면 경합이 재현되지 않는다.

### 전체 테스트

`backend/src/test/java` 의 `@Test` 를 세어 적은 개수다. 동시성 테스트는 7건이고, 같은
`ConcurrencyTestSupport`(Redis DB 15)를 상속하는 알림·제안 테스트와 컨텍스트 기동 테스트까지 합치면 25건이다.

| 테스트 클래스 | 종류 | 개수 | 결과 |
|---|---|---|---|
| `concurrency/ActiveRequestConcurrencyTest` | 동시성 | 2 | 통과 |
| `concurrency/PartyJoinConcurrencyTest` | 동시성 | 3 | 통과 |
| `concurrency/NaiveVsLuaComparisonTest` | 동시성 | 2 | 통과 |
| `notification/PushNotificationTest` | 알림 발행 (구독으로 확인) | 6 | 통과 |
| `proposal/ProposalIdempotencyTest` | 제안 수락/거절 멱등성 (단일 스레드, INV-4/5) | 11 | 통과 |
| **Redis 를 쓰는 테스트 합계** | | **24** | **24 통과** |
| `MatchingApplicationTests` | 컨텍스트 기동 | 1 | 이번 기록에서 돌리지 않음 |

결과 열은 `HANDOFF.md` §1 의 2026-09-15 실행 기록(커밋 `8d7f094`, `bf4b0da` 에서 24/24)을 옮긴 것이다.
`MatchingApplicationTests` 는 CLAUDE.md §7 에 따라 설정·의존성을 건드릴 때만 돌린다.
~~**24건은 전부 LoL 경로만 탄다** — PUBG 를 검증하는 테스트는 아직 없다.~~

> **2026-09-17 기준은 41건이다.** 위 표는 2026-09-15 실행 기록이라 그대로 둔다. 그 뒤
> `ValorantPartyJoinConcurrencyTest`(8건)와 `PubgPartyJoinConcurrencyTest`(9건)가 들어와
> **세 게임 모두 동시성 테스트가 있다** — 동시성 24(LoL 7 + VALORANT 8 + PUBG 9) +
> 제안 멱등성 11 + 알림 6 = 41건. `docs/11` R-3 참고.

## 왜 순진한 방식이 깨지는가

대조군 코드는 이렇게 생겼다.

```java
private boolean naiveJoin(String userId) {
    String key = "qm:user:active-request:" + userId;
    if (Boolean.TRUE.equals(redis.hasKey(key))) {   // (1) EXISTS
        return false;
    }
    redis.opsForHash().put(key, "requestId", ...);  // (2) HSET
    return true;
}
```

(1)과 (2)는 **자바에서 보낸 별개의 Redis 명령 두 개**다.
그 사이에는 네트워크 왕복이 있고, 다른 스레드가 끼어들 수 있다.

| 시각 | 스레드 A | 스레드 B | Redis 상태 |
|---|---|---|---|
| t1 | `EXISTS` → 0 | | 키 없음 |
| t2 | | `EXISTS` → 0 | 키 없음 |
| t3 | `HSET` | | 키 생김 |
| t4 | | `HSET` (덮어씀) | 키 있음 |
| 결과 | `true` 반환 | `true` 반환 | **둘 다 활성 요청을 가졌다고 믿는다** |

스레드가 100개면 이 틈에 100개가 전부 들어간다. 그래서 라운드마다 100건이 통과한다.

`WATCH`/`MULTI`로 낙관적 락을 걸 수도 있지만, 경합이 심할수록 재시도가 늘고
재시도 루프를 다시 검증해야 한다. 그 경로를 택하지 않았다 (docs/11 #33).

## 왜 Lua는 안 깨지는가

```lua
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('HSET', KEYS[1], unpack(ARGV))
return 1
```

명령은 똑같이 `EXISTS` → `HSET` 두 개지만, **자바가 아니라 Redis 안에서 실행된다.**

| | 순진한 방식 | Lua |
|---|---|---|
| 명령이 실행되는 곳 | 자바 (Redis 왕복 2회) | Redis 내부 (왕복 1회) |
| 원자 단위 | 명령 하나씩 | **스크립트 전체** |
| 다른 요청이 끼어들 지점 | (1)과 (2) 사이 | 없음 |

Redis는 명령을 싱글 스레드로 처리하고, Lua 스크립트를 **하나의 명령처럼** 취급한다.
스크립트가 도는 동안 다른 클라이언트의 명령은 대기한다.
그래서 "확인하고 쓰기" 사이에 틈이 생기지 않는다.

배정 스크립트도 같은 이유로 안전하다. 각각이 한 덩어리다.

- `create-or-check-party-untiered.lua` — "색인 조회 → 없으면 파티 생성 → 색인 등록 →
  active-request에 partyId 기록 → claim 만료 해제(`PERSIST`)"
- `join-party.lua` — "참가자 기록(`HSET member:{userId}`) → `member:` 필드 세기 → partyId 기록 →
  `PERSIST` → 정원이 찼으면 모든 색인에서 제거 + `HSETNX status PENDING`"

각각을 나누면 예컨대 참가자는 들어갔는데 색인이 안 정리되어 정원 초과가 난다 (docs/11 #34).

**다만 두 스크립트 사이는 Lua가 덮지 못한다.** "빈 파티가 있다"는 응답과 실제 합류
사이에 마지막 자리가 차버릴 수 있고, 그 구간을 막는 것은 `redisLock/PoolLock.java` 의
후보 풀 락이다 — 후보를 훑는 루프 전체가 한 락 안에서 돈다.
**그 락을 건너뛰는 호출부를 만들면 INV-3이 깨진다.**

**대가:** 스크립트가 도는 동안 Redis 전체가 멈춘다. 그래서 스크립트 안에
후보를 순회하는 루프를 두지 않는다. 역색인(`...:needs:{keyValue}`)으로
맨 앞 하나만 꺼내 O(1)로 끝낸다 (docs/11 #33).
쓰기를 한 스크립트는 `SCRIPT KILL`이 불가능해 길어지면 `SHUTDOWN NOSAVE`밖에 없다.

## 테스트를 어떻게 돌리는가

**전제: 로컬 Docker Redis가 떠 있어야 한다.** Testcontainers를 쓰지 않으므로
Redis가 없으면 컨텍스트 로딩부터 실패한다.

```bash
cd backend      # 스프링 프로젝트는 저장소 루트의 backend/ 에 있다

# 전체
./gradlew test

# 동시성 테스트만
./gradlew test --tests 'com.queuemate.matching.concurrency.*'

# 비교 테스트만 (라운드별 숫자가 표준출력에 찍힌다)
./gradlew test --tests '*NaiveVsLuaComparisonTest' --info

# 같은 Redis(DB 15)를 쓰는 알림 테스트. 동시성 테스트는 아니지만 전제가 같다
./gradlew test --tests '*PushNotificationTest'

# 같은 Redis(DB 15)를 쓰는 제안 멱등성 테스트 (단일 스레드)
./gradlew test --tests '*ProposalIdempotencyTest'
```

결과 XML: `backend/build/test-results/test/TEST-*.xml` (`<system-out>`에 라운드별 출력이 남는다)

### 왜 Redis DB 15번을 쓰는가

`ConcurrencyTestSupport`는 매 테스트 시작 전 **`flushDb`로 DB를 통째로 비운다.**
개발용 데이터(DB 0번)를 지우면 안 되므로 격리된 DB 번호를 쓴다.

| | 값 | 이유 |
|---|---|---|
| DB 번호 | 15 | 개발 데이터(0번)와 격리. `flushDb`가 개발 데이터를 지우지 않는다 |
| 초기화 | `flushDb` + gameconfig 재시드 | 앞 테스트가 남긴 파티/요청 키가 다음 테스트를 오염시키지 않게 |
| 재시드 대상 | `RANKED_SOLO`, `RANKED_FLEX_5`, `ARAM_5` | `flushDb`가 gameconfig도 지우므로 매번 다시 심는다 |

`flushDb`는 **선택된 DB만** 비운다(`flushAll`이 아니다).
설정은 `@TestPropertySource(properties = "spring.data.redis.database=15")`로 준다.

## 한계와 남은 것

| 항목 | 상태 | 내용 |
|---|---|---|
| Testcontainers | 미적용 | Spring Boot 4.1 BOM에서 `org.testcontainers:junit-jupiter` 해석 실패 (Testcontainers 2.x 모듈 구조 변경). 대신 로컬 Redis DB 15번 + `flushDb`로 갔다 |
| 실행 전제 | 로컬 Redis 필요 | CI에서 돌리려면 Redis 서비스 컨테이너를 붙여야 한다. 지금은 로컬에서만 재현 가능 |
| 요청 취소 | 동시성 테스트 없음 | 구현은 있다 (`MatchCancelService` + `rule/lol/LolPartyLeaver` + `leave-party.lua`). 배정과 취소가 동시에 같은 파티를 건드리는 경합을 검증하지 않았다 |
| 제안(proposal) | **동시성** 테스트 없음 | 수락 집계·확정·거절은 구현됐고(`accept-proposal.lua` / `decline-proposal.lua`) `ProposalIdempotencyTest`(11건)가 **단일 스레드 멱등성**으로 INV-4/INV-5 를 지킨다. 마지막 두 명이 동시에 수락하는 경합은 스크립트 원자성에 기대고 있고 동시 부하로 재현해 보지는 않았다 |
| 만료 sweeper | 테스트 없음 | 만료 처리는 **구현됐다** (`qm:proposal:pending` ZSET + `ProposalSweeper` + `ProposalExpiryService` + `proposal/expiry-proposal.lua`). 검증되지 않은 경합은 "시한이 지나는 순간 들어온 수락" 이다 — **막혀는 있다**(2026-09-17: `accept-proposal.lua` 가 `ARGV[3] = now` 로 시한을 보고 `NOT_FOUND` 를 돌려준다, `docs/11` R-2). 그 분기를 밟는 테스트가 없을 뿐이다 |
| 제안 도중 취소 | 테스트 없음 | 구멍은 막혔다 — `{lol,pubg,valorant}/leave-party.lua` 가 멤버를 빼기 전에 `status`/`expiresAt`/수락자 SET/pending 을 지운다 (CLAUDE.md §4 INV-5 ④). 막은 뒤의 경합(취소와 마지막 수락이 동시에)을 재현하는 테스트는 아직 없다 |
| PUBG | **해소 (2026-09-17)** | `PubgPartyJoinConcurrencyTest` 9건이 붙었다. 다른 둘과 달리 **핵심 조건이 겹쳐도 되는 것**(같은 플랫폼만으로 파티가 정원까지 찬다)과 **스팀·카카오가 섞이지 않는 것**(INV-8 구조적 분리)을 같이 본다. 이제 세 게임 모두 테스트가 있다 |
| 차단(INV-6) | 동시성 테스트 없음 | 선필터 코드는 배정 경로에 있다 (`LolCandidateRule#canJoin` → `BlockRepository`). 그런데 `social.blocks` 스키마가 없어 테스트는 H2에 `ddl-auto=create-drop` + `backend/src/test/resources/schema.sql` 로 **빈 테이블만** 만들어 두고 돌린다 — 즉 "차단이 없는 경우"만 지나간다. 확정 직전 최종 검증은 미구현 (docs/11 #30). 차단 검증 없이 배포하지 않는다 |
| 티어 배정 | 동시성 테스트 없음 | `LolTieredAssigner` + `-tiered` 스크립트 2개가 (포지션 x 티어) 격자 색인을 다루는데, 동시성 테스트는 전부 티어 없는 모드다. 알림 테스트만 티어 모드를 한 번 밟는다 (`PushNotificationTest.tieredAssignerPublishesTheSameEnvelopes`) |
| 후보 풀 락 | 테스트 없음 | `PoolLock` 자체(대기 시간 초과 → 503, 유지 시간 초과)를 겨냥한 테스트가 없다. 지금은 배정 테스트가 간접적으로만 지나간다 |
| ARAM 경로 | 테스트 없음 | 시드는 하지만(`ARAM_5`) `positionUniqueness=false` 경로를 동시성으로 검증하지 않았다. docs/11 #35의 "칼바람 5인이 2/2/1로 쪼개진" 버그가 났던 경로다 |
| 다중 인스턴스 | 미검증 | 한 JVM 안의 100 스레드로만 검증했다. Lua의 원자성은 Redis 쪽 성질이라 인스턴스가 늘어도 동일해야 하지만, 실측하지는 않았다 |

**우선순위:** 제안 수락 집계·확정 뒷정리·만료가 전부 붙었고, 멱등성 테스트는 수락/거절만
덮는다. 남은 동시성 위험 지점은 **만료와 수락의 경합**(스위퍼가 꺼내는 순간 도착한 수락),
**확정 뒷정리와 취소의 경합**(`cleanup-confirmed.lua` 가 `status=PARTY` 를 찍는 사이의 취소),
그리고 **제안 도중 취소**다. 붙일 때 같은 방식(Lua + 대조군)으로 남긴다.
