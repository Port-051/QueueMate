# 페일오버 재시도 — 켜는 법 / 끄는 법 / 지우는 법

**이 기능은 실험용 임시 기능이다.** 실험이 끝나면 통째로 걷어낸다. 프로젝트를 완성한 뒤
다시 붙일 때 이 문서만 보고 복원할 수 있게 쓴 것이다.

기본값은 **꺼짐**이다. 플래그가 꺼져 있으면 `com.queuemate.matching.failover` 패키지의
빈이 하나도 만들어지지 않고, 기존 동작과 100% 같다.

> **2026-09-09 에 실제로 돌렸다.** 무엇이 검증됐고 무엇이 안 됐는지는 **§9** 에 있다.
> 요약: pub/sub 구독은 작동한다. 서킷은 죽은 서버 호출을 **1,290건 → 28회**로 줄였고
> 익명 ERROR 로그를 **101건 → 0건**으로 바꿨다. 그런데 **재배정 성공은 0건**이다 —
> 이 기능은 장애를 복구하지 **설정 실수를 고치지 못한다.**

---

## 1. 무엇을 해결하는 기능인가

`MatchingController.createMatchRequest()` 는 두 단계다.

| 단계 | 코드 | 방식 | 실패하면 |
|---|---|---|---|
| **[1단계] 대기열 등록** | `MatchRequestService.join()` → `claim-request.lua` | **동기** | 예외가 요청 스레드로 올라가 `GlobalExceptionHandler` 가 **503 MATCHING_UNAVAILABLE** 로 바꾼다 (INV-10) |
| **[2단계] 배정** | `MatchTrigger.trigger()` → `LolCandidateRule.canJoin()` | **`@Async`** | `AsyncConfig#getAsyncUncaughtExceptionHandler()` 가 **로그만 남기고 삼킨다** |

문제는 2단계다. 컨트롤러는 이미 `201 CREATED` 를 돌려준 뒤라 **사용자는 실패를 모른다.**
`qm:user:active-request:{userId}` 는 만들어졌는데 파티에는 못 들어간 상태로, 60초 TTL 이
끝날 때까지 대기 화면만 보게 된다. 그동안 INV-1 때문에 새 요청도 못 건다.

**이 기능은 그 2단계 실패만 맡는다.**

### 왜 1단계는 거절이고 2단계는 재시도인가

- **1단계**는 요청이 **아직 어디에도 기록되지 않았고**, 기록할 저장소(Redis)가 죽어서
  실패한 것이다. 재시도할 근거 자체가 없다 → 503 으로 사용자에게 넘긴다.
- **2단계**는 요청이 이미 `qm:user:active-request:{userId}` 에 기록돼 있다.
  **재시도할 근거가 남아 있다.**

### INV-10(fail-closed)과 충돌하지 않는다

fail-closed 는 **"한 번의 시도"에 걸리는 규칙**이다 — 확인 못 하면 진행 금지.
재큐잉은 **"요청의 수명"에 대한 결정**이다. 재시도할 때마다 확인(Lua)을 다시 하므로
확인을 건너뛰는 경로가 새로 생기지 않는다.

INV-10 을 좁게 해석한 선례가 이미 셋 있다. 새로 만든 해석이 아니다.

| 어디 | 무엇을 말했나 |
|---|---|
| `GlobalExceptionHandler#handleRedisFailure()` 주석 | "이미 성립한 파티에는 영향이 없다. **새 요청만 거절한다**" |
| `PushPublisher#publish()` 주석 | "INV-10 은 '**Redis 없이 새 매칭을 만들지 마라**'이지 '알림이 실패하면 매칭을 취소해라'가 아니다" |
| `docs/11_DECISION_LOG.md` #16 | "Redis 장애가 인증까지 번지지 않아 **INV-10 의 fail-closed 범위를 새 매칭으로 한정할 수 있다**" |

### `docs/11` #29 와의 관계 — 겹치지 않는다

#29 는 **"Redis 장애 시 매칭 큐 재구축 불가를 감수한다"** 이고, 그 영향으로
"서버가 대신 재등록하지 않는다. **사용자 재요청**"이라고 적혀 있다.

이 기능은 그 결정을 뒤집지 않는다. 둘의 대상이 다르다.

| | #29 | 이 기능 |
|---|---|---|
| 대상 | Redis 가 **데이터를 잃은 뒤**의 큐 재구축 | 배정 **한 번의 시도**가 실패한 것 |
| 입력 | 없다 (DB 에 요청이 없다 — #27) | 인메모리 큐에 원본 요청이 그대로 있다 |
| 하는 일 | 재등록 (claim 을 새로 만든다) | **재배정** (claim 은 이미 있다) |

`claim` 을 다시 만드는 코드는 이 패키지에 없다. INV-1 을 건드리지 않는다.

### 만료 판단은 하지 않는다 — Lua 가 이미 해 준다

TTL 이 지난 요청은 배정 Lua 가 `EXISTS userKey == 0` 일 때 **`-2` 를 반환**한다
(`create-or-check-party-untiered.lua:56-58`, `join-party.lua:54`). 그러면 배정 코드가
`code < 0` 으로 보고 조용히 끝낸다. 재시도 쪽에서는 "예외 없이 끝났다"로 보이므로
큐에서 치우면 된다. **여기에 별도의 만료 판정을 두면 Lua 와 두 벌의 규칙이 생긴다.**

---

## 2. 추가된 파일

전부 `backend/src/main/java/com/queuemate/matching/failover/` 하나에 들어 있다.
**이 폴더만 지우면 코드는 사라진다.**

```
backend/src/main/java/com/queuemate/matching/failover/
├── package-info.java                 실험용 임시 기능이라는 선언 + 삭제 절차 위치
├── FailoverRetryCoordinator.java     서킷 + 큐 + 프로브 + 배출 + 예외 판정. 기존 코드의 유일한 진입점
├── SentinelRecoveryWatcher.java      +switch-master 구독 (복구 신호의 주 경로)
├── FailoverRetryConfig.java          빈 등록 + FailoverRetryProperties 중첩 클래스
└── LettuceFailoverOptionsConfig.java Lettuce 클라이언트 옵션. retry 와 독립된 플래그
```

**파일 5개다.** 처음에는 11개였는데 한 줄짜리 타입과 한 메서드짜리 클래스가 대부분이라
읽는 사람이 파일을 열 때마다 맥락을 잃었다. 지금은 이렇게 흡수돼 있다.

| 없어진 파일 | 어디로 갔나 |
|---|---|
| `FailoverRetryProperties.java` | `FailoverRetryConfig.FailoverRetryProperties` (중첩 클래스) |
| `CircuitState.java` | `FailoverRetryCoordinator.CircuitState` (중첩 enum) |
| `RetryEntry.java` | `FailoverRetryCoordinator.RetryEntry` (중첩 record) |
| `AssignmentCircuitBreaker.java` | `FailoverRetryCoordinator` 의 상태 전이 코드로 흡수 |
| `AssignmentRetryQueue.java` | `FailoverRetryCoordinator` 의 `LinkedBlockingDeque` 필드로 흡수 |
| `RedisFailures.java` | `FailoverRetryCoordinator#isRedisFailure()` (private static) |

**삭제 절차는 그대로다** — 폴더 하나를 지우는 것이라 파일 개수는 절차에 영향을 주지 않는다.

### 동작 흐름

```
[정상 CLOSED]
  MatchTrigger.trigger() -> Coordinator.execute() -> 배정 시도
        성공 -> recordSuccess()
        실패(Redis 장애) -> recordFailure() -> 큐 뒤에 등록
                              연속 N회 -> OPEN

[OPEN]
  들어오는 배정 요청은 Redis 를 때리지 않고 바로 큐로 간다
        |
        +-- Sentinel +switch-master 수신  (주 경로)
        +-- OPEN 이 open-timeout-ms 를 넘김  (시간 폴백, 최후 안전장치)
        |
        v
[HALF_OPEN]  큐에서 실제 요청 1건을 꺼내 배정을 시도한다 (프로브)
        성공 -> CLOSED -> drain-batch-size 씩, drain-interval-ms 간격으로 순차 배출
        실패 -> 그 건을 큐 맨 앞으로 되돌리고 OPEN 으로 복귀
```

### 프로브에 PING 을 쓰지 않는 이유

**강등된 구 master 에 아직 붙어 있으면 PING 은 성공한다.** 그런데 쓰기는
`READONLY You can't write against a read only replica` 로 실패한다. PING 으로 판정하면
서킷을 닫고 큐를 전부 배출했다가 전부 실패한다. 프로브는 반드시 **쓰기 경로**를
검증해야 하고, 그렇다면 큐에 있는 진짜 요청을 쓰는 것이 가장 정확하다 —
성공하면 그 건도 처리된 것이라 버려지는 시도가 없다.

큐가 비어 있어 프로브할 거리가 없으면 서킷을 그냥 닫는다. **다음 실제 요청이 프로브가
된다.** 틀렸으면 그 한 건이 실패하며 다시 OPEN 이 되므로 손해는 요청 하나다.

---

## 3. 수정된 기존 파일 — 두 개뿐이다

### 3-1. `backend/src/main/java/com/queuemate/matching/service/MatchTrigger.java`

원래 파일은 이랬다. **되돌릴 때 이것을 그대로 쓰면 된다.**

```java
package com.queuemate.matching.service;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 요청을 받아 게임에 맞는 규칙으로 파티 배정을 넘긴다.
 *
 * 톰캣 스레드를 붙잡지 않도록 별도 풀에서 실행한다.
 * 컨트롤러는 이 메서드를 부르고 바로 응답을 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTrigger {

    private final List<CandidateRule> candidateRules;

    @Async("matchingExecutor")
    public void trigger(CreateMatchRequestCommand command) {
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "파티 배정 규칙이 없는 게임: " + command.getGame()))
                .canJoin(command);
    }
}
```

바꾼 곳은 네 군데다 (현재 파일 기준 줄 번호).

| 줄 | 무엇을 | 왜 |
|---|---|---|
| **7** | `import org.springframework.beans.factory.ObjectProvider;` 추가 | 아래 필드 때문에 |
| **13-14** | `// [실험용] ...` 주석 + `import com...failover.FailoverRetryCoordinator;` | 이 패키지를 지울 때 같이 지울 import |
| **29-32** | 필드 `ObjectProvider<FailoverRetryCoordinator> failoverRetryCoordinator` 추가 | **`ObjectProvider` 라서 빈이 없어도 주입이 성립한다.** 플래그가 꺼지면 비어 있고, 아래 분기가 통째로 지나간다 |
| **36-46** | `trigger()` 안의 `// [실험용] ... (시작)` ~ `(끝)` 블록 | 켜져 있으면 코디네이터에 위임하고 끝낸다 |
| **48 / 58-65** | 원래 본문을 `private void assign(...)` 으로 빼고 `trigger()` 는 `assign(command)` 호출 | 재시도 경로가 **같은 일**을 다시 부를 수 있어야 한다. 내용은 한 글자도 바뀌지 않았다 |

**플래그가 꺼져 있을 때의 동작이 원본과 같은지**는 이렇게 확인한다.
`getIfAvailable()` 이 `null` → `if` 를 지나감 → `assign(command)` → 원본 본문 실행.
예외도 그대로 밖으로 나가 `AsyncConfig` 의 uncaught 핸들러가 받는다. 경로가 하나도
추가되지 않는다.

**플래그가 켜져 있어도, Redis 장애가 아닌 예외는 코디네이터가 다시 던진다**
(`FailoverRetryCoordinator#execute()` 의 `if (!isRedisFailure(e)) throw e;` — 같은 클래스의
private static 메서드다).
즉 `IllegalArgumentException("파티 배정 규칙이 없는 게임")` 같은 것은 켜든 끄든 같다.

### 3-2. `backend/src/main/resources/application.yaml`

`queuemate:` 아래에 `failover:` 블록 하나를 더했다 (현재 파일 **77-106행**).
**다른 줄은 건드리지 않았다.** 지울 때는 그 블록만 지우면 된다 — 자바 쪽에 전부
기본값이 있어서 블록이 없어도 기동이 깨지지 않는다.

### 건드리지 않은 것

`AsyncConfig` / `GlobalExceptionHandler` / `PoolLock` / Lua 스크립트 6개 /
`RedissonConfig` / `RedisConfig` / `backend/build.gradle` — **하나도 손대지 않았다.**
새 외부 의존성도 없다 (`java.util.concurrent` + 이미 있는 Spring / Lettuce 만 쓴다).

---

## 4. 켜는 법 / 끄는 법

두 축이 **독립**이다. 실험 2 는 클라이언트 옵션만 바꿨을 때의 복구 시간을 재야 하므로
따로 켤 수 있어야 한다.

```bash
cd backend    # 스프링 프로젝트는 저장소 루트의 backend/ 에 있다

# 재시도만 켠다
FAILOVER_RETRY_ENABLED=true ./gradlew bootRun

# Lettuce 옵션만 켠다
FAILOVER_LETTUCE_ENABLED=true ./gradlew bootRun

# 둘 다 + Sentinel
REDIS_SENTINEL_NODES=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381 \
FAILOVER_RETRY_ENABLED=true FAILOVER_LETTUCE_ENABLED=true \
./gradlew bootRun
```

> `bootRun` 으로 띄웠으면 **반드시 종료해라.** 안 죽이면 포트 8080 이 물려 다음 검증이
> 실패한다 (CLAUDE.md §7).

### 프로퍼티 전체 목록

| 프로퍼티 | 환경변수 | 기본값 | 의미 |
|---|---|---|---|
| `queuemate.failover.retry.enabled` | `FAILOVER_RETRY_ENABLED` | **`false`** | 기능 전체의 on/off. 꺼지면 이 패키지의 빈이 하나도 안 생긴다 |
| `queuemate.failover.retry.queue-capacity` | `FAILOVER_QUEUE_CAPACITY` | `5000` | 재시도 큐 상한. 넘치면 버린다 (OOM 방지) |
| `queuemate.failover.retry.max-attempts` | `FAILOVER_MAX_ATTEMPTS` | `5` | 한 요청의 재시도 상한. 넘으면 버린다 |
| `queuemate.failover.retry.failure-threshold` | `FAILOVER_FAILURE_THRESHOLD` | `3` | 연속 실패 N회 → CLOSED → OPEN |
| `queuemate.failover.retry.drain-batch-size` | `FAILOVER_DRAIN_BATCH_SIZE` | `20` | 한 번에 배출할 건수 |
| `queuemate.failover.retry.drain-interval-ms` | `FAILOVER_DRAIN_INTERVAL_MS` | `200` | 배출 배치 사이 간격. 스케줄러 tick 주기 |
| `queuemate.failover.retry.open-timeout-ms` | `FAILOVER_OPEN_TIMEOUT_MS` | `10000` | OPEN 이 이 시간을 넘으면 복구 신호가 없어도 프로브. **0 이하면 시간 폴백을 끈다** |
| `queuemate.failover.lettuce.enabled` | `FAILOVER_LETTUCE_ENABLED` | **`false`** | Lettuce 클라이언트 옵션의 on/off |
| `queuemate.failover.lettuce.connect-timeout-ms` | `FAILOVER_LETTUCE_CONNECT_TIMEOUT_MS` | `500` | 새 master 로 붙는 시도의 상한 |
| `queuemate.failover.lettuce.reject-when-disconnected` | `FAILOVER_LETTUCE_REJECT` | `true` | `DisconnectedBehavior.REJECT_COMMANDS` 적용 여부 |

Sentinel 주소는 **이 기능이 따로 갖지 않는다.** `RedissonConfig` 와 같은 프로퍼티를 읽는다.

```
queuemate.lock.sentinel.nodes > spring.data.redis.sentinel.nodes
spring.data.redis.sentinel.master  (기본 mymaster)
```

한쪽만 바꾸면 "락은 Sentinel 인데 복구 신호는 안 온다" 같은, 겉보기로는 멀쩡한 상태가
만들어지기 때문이다.

> **이 판단은 실험 6 에서 틀린 것으로 드러났다.** 반대 방향의 사고가 난다 —
> **Redisson 을 단일 노드로 되돌리면 복구 신호까지 같이 꺼진다.**
> `../EXPERIMENTS.md` 실험 2 의 판 B 를 만들 때 실제로 그랬다.
> 자세한 것과 고치는 방법은 **§6 (A)** 와 **§8-8**.

### 로그로 보는 법

전이·지표 로그에 전부 `[failover]` 접두사가 붙어 있다.

```bash
# 타임라인
grep '\[failover\]' app.log

# 서킷 전이만 (deadMasterAttempts 가 여기 붙는다)
grep '\[failover\] 서킷' app.log
# -> [failover] 서킷 CLOSED -> OPEN (연속 실패 3회: RedisException) deadMasterAttempts=3

# 버린 건수 (누적버림 이 유일한 출처다)
grep '\[failover\] 재시도 요청을 버린다' app.log
# -> userId=... attempts=6 age=76137ms 이유=... 누적버림=1
```

| 로그의 값 | 어디 로그에 있나 | 실험 지표 |
|---|---|---|
| `deadMasterAttempts` | 서킷 전이 로그 | 페일오버 중 죽은 master 에 실제로 보낸 요청 수 |
| `누적버림` | `재시도 요청을 버린다` 로그 | 큐 상한/재시도 상한으로 버린 누적 건수 |

> **`배출 완료` 로그와 `drainMs` / `withinTtl` / `drained` / `dropped` 지표는 없다.**
> 배출 시각을 재고 TTL 안에 들었는지 세던 기능을 코드 정리 때 걷어냈다
> (`request-ttl-seconds` 프로퍼티도 같이 사라졌다). 실험 6 에서 그 지표들이 애초에
> **정의되지 않았기** 때문이다 — 판 C 는 큐가 비었고 판 B 는 재배정 성공이 0 이라
> 비율의 분모가 없었다 (§9-3). 다시 재고 싶으면 그때 로그를 새로 붙여라.

---

## 5. 삭제 절차

**아래 순서대로 하면 원상복구된다. 실제로 한 번 해 보고 `compileJava` 가 통과하는 것을
확인한 뒤에 쓴 절차다.**

1. **패키지 폴더를 통째로 지운다.** (자바 파일 **5개** — §2 목록)
   ```bash
   rm -rf backend/src/main/java/com/queuemate/matching/failover
   ```

2. **`backend/src/main/java/com/queuemate/matching/service/MatchTrigger.java` 를 원본으로 되돌린다.**
   위 §3-1 의 "원래 파일은 이랬다" 코드 블록을 그대로 덮어쓰면 된다.
   손으로 지운다면 네 곳이다.
   - 7행 `import org.springframework.beans.factory.ObjectProvider;`
   - 13-14행 `// [실험용] ...` 주석과 그 아래 import
   - 29-32행 `failoverRetryCoordinator` 필드와 그 위 주석
   - 36-46행 `// [실험용] ... (시작)` ~ `(끝)` 블록
   - 그리고 `assign()` 의 본문을 `trigger()` 안으로 되돌리고 `assign()` 메서드를 지운다

3. **`backend/src/main/resources/application.yaml` 의 `failover:` 블록을 지운다.**
   `# [실험용] Redis 페일오버 재시도 - 제거 시 이 failover 블록만 통째로 삭제` 주석부터
   `reject-when-disconnected:` 줄까지. `queuemate.proposal / scan / sweep` 은 남긴다.

4. **컴파일 확인.**
   ```bash
   cd backend
   ./gradlew compileJava
   ./gradlew test --tests 'com.queuemate.matching.concurrency.*'   # Redis 가 떠 있어야 한다
   ```

5. **이 문서와 `EXPERIMENTS.md` 의 실험 6 은 남겨도 된다.** 코드가 아니라 기록이다.
   다시 붙일 때 §2 의 파일 목록과 §3 의 수정 내역이 그대로 재료가 된다.

**남는 흔적이 있는지 확인:**

```bash
grep -rn "failover" backend/src/ --include=*.java --include=*.yaml
# 아무것도 안 나와야 한다
```

---

## 6. 나중에 다시 붙일 때 확인할 것

프로젝트가 완성되면 배정 경로가 지금보다 훨씬 길어진다. **재시도의 단위가 바뀐다.**

### INV-4 / INV-5 (proposal) 이 구현되면

지금은 배정 = "파티에 넣는다" 하나라서 재시도 단위가 `CreateMatchRequestCommand` 하나면
충분하다. proposal 이 생기면 흐름이 이렇게 갈라진다.

```
파티 정원 참 -> proposal 생성 -> 전원 accept 대기 (TTL 20초, queuemate.proposal.ttl-seconds)
                                        -> 전원 accept -> 확정 -> DB + outbox
                                        -> 하나라도 decline/expire -> 파티 해체 or 복귀
```

- **proposal 생성 이후에 Redis 가 죽으면 이 기능으로 못 고친다.** 재시도 단위가
  "요청 하나"가 아니라 "proposal 에 걸린 N 명"이 되기 때문이다. 한 명분만 다시 배정하면
  나머지 N-1 명의 상태와 어긋난다.
  → **재시도 경계를 "proposal 생성 전까지"로 명시해야 한다.**
  `LolCandidateRule#canJoin()` 안의 Lua 반환 코드 `3`(정원 참) 자리에 proposal 생성이
  들어가면, 그 지점 이후는 이 큐가 아니라 proposal 만료(INV-5)가 처리해야 한다.
- 수락 집계 Lua(docs/11 #28)는 **멱등하지 않다.** 같은 요청을 두 번 배정하면
  같은 사람이 두 파티에 들어갈 수 있다. 지금은 `join-party.lua` 가
  `member:{userId}` 필드 하나만 쓰므로(INV-7) 재시도가 안전하지만,
  **proposal 단계가 붙으면 이 안전성을 다시 확인해야 한다.**

### INV-6 (차단 검증) 이 구현되면

`LolCandidateRule#canJoin()` 이 락을 잡기 전에 `blockRepository.findBlockedUserIds()` 로
**DB 를 친다.** 지금은 스키마가 없어 사실상 비어 있다.

- 재시도 경로도 `canJoin()` 을 통째로 다시 부르므로 **차단 조회도 매번 다시 한다.**
  이건 정확성 면에서는 옳다 — 대기 중에 차단이 추가됐을 수 있다.
- 다만 **배출이 순차적이어도 DB 부하가 건수만큼 곱해진다.** `drain-batch-size` 를
  줄이거나, 배출 중에는 차단 목록을 캐시하는 판을 고려해야 한다.
- Redis 는 살아났는데 **DB 가 죽어 있으면** `DataAccessException` 이 나고,
  `FailoverRetryCoordinator#isRedisFailure()` 가 그것을 **Redis 장애로 오판한다**
  (`DataAccessException` 은 JPA 도 던진다). 그러면 서킷이 DB 장애로 열린다.
  → **차단 조회가 붙는 시점에 `FailoverRetryCoordinator#isRedisFailure()` 의 판정을
  좁혀야 한다.**
  지금은 Redis 경로 말고 예외를 던지는 데가 사실상 없어서 문제가 안 될 뿐이다.

### `TieredAssigner` 가 완성되면

지금 `LolCandidateRule#canJoin()` 은 `command.getTier()` 유무로 두 assigner 를 고른다.
재시도는 `canJoin()` 을 다시 부르므로 **어느 쪽이든 자동으로 따라간다.** 이 기능 쪽에
바꿀 것은 없다. 다만 티어 격자는 후보 순회가 넓어 락 유지 시간이 길어지므로,
`drain-batch-size` × 락 유지 시간이 스케줄러 tick 을 얼마나 잡아먹는지 다시 봐야 한다.

---

### 남은 판단 항목 — 사용자 결정 대기 (A) / (B), 이번에 처리한 것 (C)

아래 셋은 **코드에 손대지 않았거나(A·B), 이번에 뺀 것(C)** 이다.
각각 *문제 / 근거(실측) / 고치는 법 / 지금 안 고친 이유* 순서로 적는다.

#### (A) `SentinelRecoveryWatcher` 의 프로퍼티 결합 결함 — **미결, 사용자 판단 대기**

**문제.** `FailoverRetryConfig` 가 감시자에게 넘기는 Sentinel 주소가
**Redisson(락 클라이언트)과 같은 프로퍼티**다.

```java
// FailoverRetryConfig#sentinelRecoveryWatcher — RedissonConfig 와 똑같은 표현식이다
@Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}")
String sentinelNodes
```

원래 의도는 "한쪽만 Sentinel 이 되는 것을 막자" 였는데, 실제로는 **반대 방향의 사고**를
만든다. 락을 단일 노드로 되돌리면 **복구 신호까지 같이 꺼진다.**

**근거(실측).** 실험 6 에서 판 B(Lettuce 만 Sentinel, Redisson 은 단일 노드)를 만들려고
`QUEUEMATE_LOCK_SENTINEL_NODES=""` 로 비웠더니 이 로그가 나왔다.

```
[failover] Sentinel 노드가 없어 +switch-master 구독을 건너뛴다.
```

**Lettuce 는 여전히 Sentinel 에 붙어 있고 `+switch-master` 는 실재하는데** 감시자만 눈을
감았다. 그 결과 판 B 의 복구는 전부 `open-timeout` 시간 폴백으로 돌았다.
**이 기능이 가장 필요한 판(반쪽 설정)에서만 구독이 꺼진다** — 결함의 방향이 정확히 반대다.

**고치는 법.** 감시자 전용 프로퍼티를 두고, **락 쪽 값으로 폴백하지 않는다.**

```java
// FailoverRetryConfig.java
@Bean
public SentinelRecoveryWatcher sentinelRecoveryWatcher(
        FailoverRetryCoordinator coordinator,
        @Value("${spring.data.redis.sentinel.master:mymaster}") String sentinelMaster,
        // (변경) 감시자 전용 프로퍼티 -> 없으면 Spring Data 의 Sentinel 주소.
        //        queuemate.lock.sentinel.nodes 는 더 이상 보지 않는다.
        //        락이 단일 노드여도 복구 신호는 계속 받아야 하기 때문이다.
        @Value("${queuemate.failover.retry.sentinel-nodes:${spring.data.redis.sentinel.nodes:}}")
        String sentinelNodes) {

    return new SentinelRecoveryWatcher(coordinator, sentinelMaster, sentinelNodes);
}
```

```yaml
# application.yaml — queuemate.failover.retry 아래. 안 적으면 spring.data.redis 값을 쓴다
      sentinel-nodes: ${FAILOVER_SENTINEL_NODES:}
```

최소판은 표현식에서 `queuemate.lock.sentinel.nodes:` 앞부분만 지우고
`@Value("${spring.data.redis.sentinel.nodes:}")` 로 두는 것이다.
중요한 것은 **`queuemate.lock.*` 을 참조하지 않는 것** 하나다.

**지금 안 고친 이유.** 판 C(정상 설정)에서는 두 프로퍼티가 같은 값이라 **무해하고**,
이 결함을 드러낸 실험 6 은 이미 끝났다. 임시 기능이라 지금 고칠지 정식 도입 때 고칠지는
**사용자 판단**이다. 상세 경위는 §8-8.

#### (B) `assigner` 필드의 `volatile` 과 `PartyAssigner` 추출 — **미결, 사용자 판단 대기**

**문제.** `FailoverRetryCoordinator` 는 배정 로직을
`Consumer<CreateMatchRequestCommand>` 콜백(`MatchTrigger::assign`)으로 받아
`volatile` 필드에 들고 있다. 배정 규칙을 아는 객체를 직접 주입받지 못하는 이유는
**순환 의존**이다 — `MatchTrigger` 가 코디네이터를 알고, 코디네이터가 배정을 알아야 한다.

```java
private volatile Consumer<CreateMatchRequestCommand> assigner;
```

**`volatile` 은 최적화가 아니라 정확성상 필수다.** `matching-N` 스레드가 쓰고
`failover-retry` 스케줄러 스레드가 읽는다. 빼면 스케줄러 스레드가 `null` 을 볼 수 있고,
그러면 NPE 가 나는데 `tick()` 의 `catch (Throwable)` 가 그것을 삼켜 **재시도가 조용히
멈춘다.** 이 기능이 없애려던 "조용한 실패" 가 기능 안에서 재현되는 셈이다.

**비용은 무시할 수준이다.** 이번 정리로 쓰기가 **최초 1회**로 줄었고, 판 B 실측 기준
쓰기 1회 + 읽기 약 30회(= `attempt()` 호출 수)였다. 핫 패스가 아니다.

**더 나은 설계.** 배정 로직을 `PartyAssigner` 같은 **별도 빈**으로 빼면
`MatchTrigger` 와 코디네이터가 **둘 다 그것을 주입**받게 되어 순환 의존이 사라진다.
그러면 코디네이터가 `final` 필드로 받을 수 있어 **`volatile` 과 콜백 간접 참조가 함께
없어진다.** 덤으로 `MatchTrigger` 가 지금 갖고 있는 두 역할("비동기로 던지는 역할" 과
"게임별 배정 규칙을 고르는 역할")도 갈라진다.

```java
// (새 빈) backend/src/main/java/com/queuemate/matching/service/PartyAssigner.java
@Component
@RequiredArgsConstructor
public class PartyAssigner {
    private final List<CandidateRule> candidateRules;

    public void assign(CreateMatchRequestCommand command) {
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "파티 배정 규칙이 없는 게임: " + command.getGame()))
                .canJoin(command);
    }
}

// FailoverRetryCoordinator — 콜백 대신 final 주입. volatile 이 사라진다
private final PartyAssigner assigner;          // 생성자 주입
public void execute(CreateMatchRequestCommand command) { ... assigner.assign(command); ... }

// MatchTrigger — 같은 빈을 주입받아 쓴다
@Async("matchingExecutor")
public void trigger(CreateMatchRequestCommand command) { assigner.assign(command); }
```

**지금 안 한 이유.** 프로덕션 패키지(`service/`)에 **새 클래스가 생긴다.**
그러면 이번 작업의 최우선 제약인 **"`failover` 폴더만 지우면 원상복구"** 가 깨진다
(§5 삭제 절차가 파일 하나를 더 챙겨야 한다). 임시 기능 때문에 영구 코드의 구조를 바꾸는
순서가 거꾸로다 — **프로젝트를 완성하고 이 기능을 정식 도입할 때 함께 하는 것**이
자연스럽다. 그때는 `PartyAssigner` 추출이 이 기능과 무관하게도 옳은 정리다.

#### (C) Sentinel 폴링(백업 경로)을 **제거했다** — 이번 작업에서 처리

**무엇을 뺐나.** `SentinelRecoveryWatcher` 의 `startPolling()` / `poll()` /
`readMasterAddress()`, `poller` 스케줄러 필드와 `destroy()` 의 종료 처리,
`lastMasterAddress` 필드, `FailoverRetryConfig.FailoverRetryProperties` 의
`sentinelPollIntervalMs`, `application.yaml` 의 `sentinel-poll-interval-ms` 줄.
설계 의도는 "`+switch-master` 구독이 끊겼을 때의 백업 경로" 였다.

**근거(실측) — 4회 실행에서 신호 0건.** 기동 로그가 매번 이랬다.

```
[failover] Sentinel 폴링 시작 intervalMs=1000 현재master=null
```

`readMasterAddress()` 가 **항상 `null`** 을 돌려줬고, `poll()` 은 `current == null` 이면
그냥 return 했다. 즉 **영원히 신호가 나갈 수 없는 구조**였다.
`폴링이 master 변경을 감지했다` 로그가 **전 실행에서 0건**이고, 복구는 전부
`source=pubsub` 또는 코디네이터의 `open-timeout` 폴백으로 처리됐다.
**실동작에는 문제가 없었다** — 이 경로가 없어도 되는 것이었다는 뜻이다.

**왜 고치지 않고 뺐나.** "돌지 않는 코드"를 남기는 쪽이 더 나쁘다는 판단이다.
남겨 두면 다음 사람이 문서의 "백업 경로가 있다"를 사실로 믿고, 그 위에서 판단한다.
**뺀 뒤 근거를 남기는 편이 정직하다.** 근거는 이 절과
`SentinelRecoveryWatcher` 클래스 주석 두 곳에 있다.

**다시 필요해지면.** 폴링을 그대로 되붙이지 마라. **먼저 `readMasterAddress()` 가 왜
`null` 이었는지부터 규명해야 한다.**

- 의심되는 지점: **Spring Data 의 standalone 커넥션(`LettuceConnectionFactory` +
  `RedisStandaloneConfiguration`)으로 Sentinel 포트에 `SENTINEL get-master-addr-by-name`
  을 `connection.commands().execute(...)` 로 치는 방식 자체.**
  Sentinel 은 일반 Redis 명령 대부분을 거절하고, 응답 파싱(중첩 배열 → `List<byte[]>`)도
  일반 커넥션 경로와 다를 수 있다. **원인은 규명하지 못했다 — 미확인이다.**
- 확인 방법은 단순하다. 같은 노드에 `redis-cli -p 26379 SENTINEL get-master-addr-by-name
  mymaster` 를 쳐 보고, 앱 쪽에서 반환 객체의 **실제 타입과 값**을 로그로 찍어 비교해라.
  `null` 로 뭉개지 말고 예외/타입을 그대로 남기는 것이 첫걸음이다.
- 폴링이 아니라 **구독 끊김 감지**로 문제를 다시 정의하는 판도 있다. 백업 경로가 필요했던
  이유는 "구독이 끊길 수 있다" 였으므로, `RedisMessageListenerContainer` 의 재연결 상태를
  보는 쪽이 주소를 폴링하는 것보다 문제에 가깝다.

**지금 남은 복구 계기는 둘이다** — `+switch-master` pub/sub(주) + `open-timeout-ms`
시간 폴백. 실험 6 에서 실제로 신호를 낸 것도 이 둘뿐이다.

---

### 다중 인스턴스가 되면 (Stage 2, ECS Fargate)

큐가 **인스턴스별로 따로 논다.** 인스턴스 A 가 죽으면 A 의 큐가 사라진다.
Stage 1(단일 EC2)에서는 무관하지만 Stage 2 로 가면 이 설계 자체를 다시 봐야 한다.
그때는 인메모리가 아니라 다른 답이 필요하다.

---

## 7. 설계 근거와 대안

### 왜 인메모리 큐인가

| 대안 | 왜 안 썼나 |
|---|---|
| **Redis** | **자기모순이다.** Redis 장애로 실패한 것을 Redis 에 저장할 수 없다 |
| **Kafka / RabbitMQ** | `CLAUDE.md` §3 이 추가를 금지한다 |
| **SQS FIFO** | 이 프로젝트에서 SQS 는 **앱 간** 도메인 이벤트용이다 (`ProposalConfirmed.fifo` 등). 같은 프로세스가 자기 작업을 재시도하려고 네트워크 왕복과 수 초 지연을 붙일 이유가 없다. 재시도해야 할 시간 창이 **TTL 60초**뿐이다 |
| **DB** | 진행 중 매칭 상태를 DB 에 두는 것을 `docs/11` #27 이 금지한다 (`match_requests` 테이블 없음). #29 가 "요청 DB 복제"를 명시적으로 기각했다 |
| **Spring Retry** | 재시도하는 동안 **스레드를 붙잡는다.** 페일오버는 수 초~수십 초라 `matchingExecutor` 4~8 스레드가 통째로 묶이고, `CallerRunsPolicy` 때문에 그 압력이 톰캣 스레드까지 번진다 |

Stage 1 은 단일 EC2 라 인스턴스 분산 문제가 없다는 것도 근거다 (`CLAUDE.md` §3).

### 왜 ThreadLocal 이 안 되는가

"실패한 요청을 그 스레드에 들고 있다가 나중에 처리"는 두 군데서 막힌다.

1. **다른 스레드가 못 읽는다.** 복구 신호는 Sentinel pub/sub 스레드로 오는데 요청은
   배정 스레드의 ThreadLocal 안에 있다. 신호를 받은 쪽이 그 값을 볼 방법이 없다.
2. **붙잡고 기다리면 그 스레드가 묶인다.** 페일오버 동안 `matchingExecutor` 가 마르고,
   `CallerRunsPolicy` 가 호출한 톰캣 스레드에 일을 떠넘기면서 장애가 매칭 밖으로 번진다.

큐는 그래서 **스레드 밖에** 있어야 한다.

### 왜 시간 기반이 아니라 이벤트 기반인가

일반적인 서킷 브레이커는 OPEN → HALF_OPEN 전환을 **시간으로 추측**한다
("30초 지났으니 이제 살아났겠지"). 추측은 두 방향으로 다 틀린다.

- **너무 이르면** 아직 안 끝난 페일오버 중에 죽은 master 를 계속 때린다
  (지표: `deadMasterAttempts` 가 는다).
- **너무 늦으면** 이미 새 master 가 떴는데도 큐가 안 빈다. TTL 60초가 그동안 흐른다
  (지표: `withinTtl / drained` 비율이 떨어진다).

**Sentinel 의 `+switch-master` 는 추측이 아니라 사실이다.** "지금 승격이 끝났다"를
인프라가 직접 알려 준다. 이 설계는 그 이벤트로 시간 추측을 대체한다.
그것이 요점이다.

시간 기반은 **폴백으로만** 남겼다 (`open-timeout-ms`). Sentinel 을 안 쓰는 판에서는
이벤트 자체가 없기 때문이다. 이 값을 `0` 으로 두면 순수 이벤트 기반이 되고,
크게 두면 순수 시간 기반에 가까워진다 — **`EXPERIMENTS.md` 실험 6 이 이 축을 흔든다.**

### 복구 계기는 둘이다 — 이벤트(주) + 시간(폴백)

- **주 경로**: Sentinel 포트(26379 등)에 붙어 `+switch-master` 를 구독한다.
  **master 포트가 아니다.** 그래서 앱의 기본 커넥션 팩토리로는 못 받고
  Sentinel 전용 커넥션이 따로 필요하다 (`SentinelRecoveryWatcher`).
- **시간 폴백**: `FailoverRetryCoordinator` 의 `open-timeout-ms`. Sentinel 을 안 쓰는
  판에는 `+switch-master` 자체가 없고, 구독이 죽어 있을 수도 있다. 큐가 영원히
  안 비는 것을 막는 최후 안전장치다.

> **셋째 경로였던 Sentinel 폴링은 제거했다.** `SENTINEL get-master-addr-by-name` 을
> 주기적으로 물어 주소 변경을 감지하는 백업 경로가 있었는데, **실험 6 의 4회 실행에서
> 신호를 한 번도 못 냈다** — 이유와 다시 붙일 때의 조건은 **§6** 에 있다.

> **`LettuceConnectionFactory` 를 `@Bean` 으로 올리지 마라.**
> Spring Boot 의 `LettuceConnectionConfiguration` 이
> `@ConditionalOnMissingBean(RedisConnectionFactory.class)` 때문에 **통째로 물러난다.**
> 그러면 앱의 진짜 Redis 커넥션이 사라진다. `SentinelRecoveryWatcher` 가 자기 팩토리를
> 필드로 들고 생명주기를 직접 관리하는 이유가 이것이다.

### Lettuce 클라이언트 옵션 — Spring Boot 4 에서 달라진 것

`docs/app-config-snippets.md` §1-3 은 Boot 3.x 기준으로
`org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer`
라고 적어 두었고, 그 아래 "확인 필요" 로 남겨 두었다. **확인했다.**

1. 그 경로는 **Boot 4.1.1 에 없다.** 패키지가
   `org.springframework.boot.data.redis.autoconfigure` 로 옮겨졌다
   (`spring-boot-data-redis` 모듈로 분리됐다).
2. 같은 패키지에 **`LettuceClientOptionsBuilderCustomizer` 가 새로 생겼다.**
   이 코드는 이쪽을 쓴다.

왜 새쪽인가. Boot 4 의 `LettuceConnectionConfiguration#getLettuceClientConfiguration()` 은
`builder.clientOptions(createClientOptions(...))` 를 **먼저** 하고
`LettuceClientConfigurationBuilderCustomizer` 를 **나중에** 실행한다. 그래서 옛 훅에서
`builder.clientOptions(...)` 를 부르면 Boot 가 만든 옵션을 **통째로 덮어쓴다.**
`LettuceClientOptionsBuilderCustomizer` 는 Boot 가 쓰는 그 빌더를 그대로 받아 마지막에
손대므로 덮어쓸 위험이 없다.

**그리고 `TimeoutOptions.enabled()` 는 Boot 4.1.1 에서 이미 기본이다.**
`createClientOptions()` 가 이미 넣고 있는 것을 바이트코드로 확인했다.
`app-config-snippets.md` 와 `EXPERIMENTS.md` 실험 2 축 1 은 "이걸 안 켜면
`timeout: 2s` 가 장식이 된다"고 적었는데, **그건 Boot 3.x 기준의 서술이고 이 버전에는
해당하지 않는다.** 실험 2 에서 "TimeoutOptions 를 켰더니 빨라졌다"는 결과를 기대하지 마라.
**그래서 이 코드는 `TimeoutOptions` 를 넣지 않는다.** 예전에는 "같은 값이라 부작용이 없고
Boot 기본값이 바뀌어도 전제가 안 흔들린다"는 이유로 명시적으로 다시 넣었는데,
Boot 4.1.1 이 이미 넣는 것을 확인한 뒤로는 **같은 값을 두 번 쓰는 줄**일 뿐이라
코드에서 아예 지웠다 (`LettuceFailoverOptionsConfig` 의 클래스 주석에 그 근거가 있다).
Boot 를 올릴 때 `createClientOptions()` 가 여전히 넣는지 확인하는 것으로 대신한다.

실제로 효과가 남는 것은 나머지 둘이다.

| 옵션 | 안 잡으면 | 잡으면 |
|---|---|---|
| `DisconnectedBehavior.REJECT_COMMANDS` | 기본값은 끊긴 동안 명령을 **큐에 쌓는다.** 요청 스레드가 묶이고 톰캣 스레드 풀이 마르면서 장애가 매칭 밖으로 번진다 | 즉시 실패. `PoolLock` 의 짧은 대기, HikariCP `connection-timeout: 300` 과 같은 fail-closed 판단이다 |
| `SocketOptions.connectTimeout` | 새 master 로 붙는 시도 자체가 오래 매달린다 | 짧게 끊고 다음 Sentinel 로 넘어간다 |

---

## 8. 알려진 한계

1. **앱을 재시작하면 큐가 사라진다.**
   새 위험이 아니다. `claim-request.lua:21-24` 주석이 이미 "자리만 잡고 배정 전에 앱이
   죽으면 TTL 로 정리된다"를 감당하기로 한 범위다. 잃는 것은 "다시 시도할 기회"뿐이고,
   사용자 상태는 60초 뒤 그 기능이 없을 때와 같아진다.

2. **다중 인스턴스에서는 큐가 인스턴스별로 따로 논다.**
   Stage 1 은 단일 EC2 (`CLAUDE.md` §3) 라 지금은 무관하다. Stage 2 로 가면 §6 참고.

3. **사용자에게 실패를 알리는 경로는 이번 범위 밖이다.**
   재시도가 상한을 넘겨 버려진 요청도 사용자는 여전히 모른다. 60초 TTL 이 끝나면
   다시 요청할 수 있게 될 뿐이다. 알림/SSE 는 나중에 하기로 했다
   (`app:realtime` 의 몫 — `CLAUDE.md` §3).

4. **큐가 넘치면 버린다.** 상한이 없으면 OOM 이므로 유한해야 하고, 유한하면 버릴 수밖에
   없다. 버려도 TTL 이 정리한다. `dropped` 로그로 얼마나 버렸는지 볼 수 있다.

5. **`FailoverRetryCoordinator#isRedisFailure()` 의 판정이 넓다.** `DataAccessException` 을
   Redis 장애로 본다.
   지금은 배정 경로에서 그 예외를 던지는 것이 Redis 뿐이라 맞지만, **차단 조회(INV-6)가
   붙으면 DB 장애를 Redis 장애로 오판한다.** §6 참고.

6. **배출이 스케줄러 스레드 하나에서 돈다.**
   `PoolLock` 이 최대 3초 대기 + 3초 유지이므로, 한 건이 최악 3초를 잡아먹는다.
   `drain-batch-size` 를 크게 잡으면 tick 하나가 길어진다. `scheduleWithFixedDelay` 라
   겹치지는 않지만, 배출 속도의 상한이 여기서 정해진다.

7. ~~**Sentinel 포트에 Spring Data 의 standalone 커넥션으로 붙는 것을 실기로 검증하지
   못했다.**~~ → **해소됨 (2026-09-09).** 실험 6 판 C 에서 아래 두 줄이 실제로 찍혔다.
   ```
   [failover] +switch-master 수신 ...
   [failover] 복구 신호 수신 source=pubsub
   ```
   **주 경로(Sentinel pub/sub 구독)가 작동한다.**
   기동 직후 이 두 줄이 나오는지는 여전히 먼저 확인해라 — 안 나오면 주 경로가 죽은 것이고,
   그 상태로 잰 숫자는 "이벤트 기반" 의 숫자가 아니다.
   ```
   [failover] Sentinel 에 붙었다: 127.0.0.1:26379
   [failover] +switch-master 구독 시작
   ```

8. **`SentinelRecoveryWatcher` 의 Sentinel 주소가 락 클라이언트에 묶여 있다 (설계 결함).**

   `FailoverRetryConfig.java:54` 가 감시자의 주소를 이렇게 읽는다.

   ```java
   @Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}")
   String sentinelNodes
   ```

   **`RedissonConfig.java:41` 과 똑같은 표현식이다.** 원래 의도는 "한쪽만 Sentinel 이
   되는 것을 막자" 였는데, 실제로는 **반대 방향의 사고**를 만든다.

   실험 6 에서 판 B(Lettuce 만 Sentinel)를 만들려고 `QUEUEMATE_LOCK_SENTINEL_NODES=""`
   로 비웠더니 이 로그가 나왔다.

   ```
   [failover] Sentinel 노드가 없어 +switch-master 구독을 건너뛴다.
              복구는 open-timeout-ms 시간 폴백만으로 이뤄진다
   ```

   **Lettuce 는 여전히 Sentinel 에 붙어 있고 `+switch-master` 는 실재하는데**, 감시자만
   눈을 감았다. 그 결과 판 B 의 재시도는 시간 폴백만으로 돌았다(서킷 전이 로그에
   `복구 신호(open-timeout)` 만 찍힌 이유).

   **복구 신호를 받는 것과 락 클라이언트가 Sentinel 을 쓰는지는 아무 상관이 없다.**
   감시자는 `spring.data.redis.sentinel.nodes` 를 **독립적으로** 읽어야 한다.

   고치는 방법 — 프로퍼티를 따로 두고, 락 쪽 값으로 폴백하지 않는다.

   ```java
   // FailoverRetryConfig.java
   @Bean
   public SentinelRecoveryWatcher sentinelRecoveryWatcher(
           FailoverRetryCoordinator coordinator,
           @Value("${spring.data.redis.sentinel.master:mymaster}") String sentinelMaster,
           // (변경) 감시자 전용 프로퍼티 -> 없으면 Spring Data 의 Sentinel 주소.
           //        queuemate.lock.sentinel.nodes 는 더 이상 보지 않는다.
           //        락이 단일 노드여도 복구 신호는 계속 받아야 하기 때문이다.
           @Value("${queuemate.failover.retry.sentinel-nodes:${spring.data.redis.sentinel.nodes:}}")
           String sentinelNodes) {

       return new SentinelRecoveryWatcher(coordinator, sentinelMaster, sentinelNodes);
   }
   ```

   `FailoverRetryConfig.FailoverRetryProperties` 에 `sentinelNodes` 필드를 더해
   바인딩하는 판도 같다. 중요한 것은 **`queuemate.lock.*` 을 참조하지 않는 것** 하나다.
   (감시자 생성자에 `props` 가 없는 것은 폴링 제거의 결과다 — 감시자가 읽던 설정값이
   `sentinel-poll-interval-ms` 하나뿐이었다. §6 참고.)

   > **코드는 고치지 않았다.** 이 폴더의 규칙(기존 소스는 한 줄도 건드리지 않는다)이고,
   > 이 기능 자체가 걷어낼 임시 기능이라 지금 고칠지 다시 붙일 때 고칠지는 사용자 판단이다.
   > 지금 상태로도 **판 C(정상 설정)에서는 문제가 없다** — 그때는 두 프로퍼티가 같은 값이다.
   > 문제가 되는 것은 판 B 같은 반쪽 설정, 즉 **이 기능이 가장 필요한 상황**이다.

---

## 9. 실험 6 이 확인한 것 / 확인 못 한 것

측정 조건: 동시 10스레드 프로브, 50초, `t0 = +10초` 에 `docker kill`.
전체 표와 절차는 `EXPERIMENTS.md` 실험 6.

### 9-1. 판 C(정상 설정) + OFF ↔ ON

| | OFF | ON |
|---|---|---|
| 총 요청 | 2,070건 | 2,320건 |
| 201 / 503 | 2,040 / 30 | 1,960 / 360 |
| 배정 성공 | 2,034건 | 1,960건 |
| **HTTP 성공인데 배정 실패** | **6건** | **0건** |
| `비동기 작업 실패` 로그 | 7건 | **0건** |
| 서킷 | (기능 없음) | **한 번도 안 열림. 큐 0건** |

**조용한 유실 6건이 0건이 됐지만, 그건 재시도의 공이 아니다.**
서킷이 열린 적이 없고 큐에 들어간 것도 없다 — **배정 단계 실패 자체가 없었다.**
유실 360건은 전부 503 이다. 사용자가 실패를 **알고** 받았다.

### 9-2. 정상 설정에서는 이 기능이 할 일이 거의 없다 (정직한 평가)

이유는 이 문서 §1 이 이미 적어 둔 구조 그대로다.

```
[1단계] claim   동기   -> 실패하면 503 (INV-10 fail-closed)
[2단계] 배정    @Async -> 실패하면 삼켜진다   <- 이 기능이 맡는 자리
```

**Redis 가 죽으면 1단계가 먼저 막힌다.** 그래서 2단계까지 도달하는 요청이 애초에 없다.
재시도 큐가 노리는 "claim 성공 + 배정 실패" 는 **두 클라이언트가 서로 다른 것을 볼 때**
생기고, 그건 판 B 같은 **반쪽 설정**이다.

→ **이 기능의 값은 "페일오버를 더 빨리 복구한다" 가 아니다.**
실제 값은 아래 9-3 의 마지막 두 줄에 있다.

### 9-3. 판 B(반쪽 설정) + ON — 결정적 검증

| | 값 |
|---|---|
| 총 요청 | 2,050건 |
| 201 / 503 / 연결실패 | 1,681 / 360 / 9 |
| 배정 성공 | 400건 (19.5%) |
| **HTTP 성공인데 배정 실패** | **1,281건 (62.5%)** — t0+9,296ms ~ t0+39,848ms |
| **큐 등록** | **1,290건** |
| **`deadMasterAttempts`** | **28회** |
| **재배정 성공** | **0건** |
| 재시도 상한 초과로 버림 | 1건 (`attempts=6 age=76,137ms`) |
| `비동기 작업 실패` 익명 ERROR | **0건** |

서킷 전이는 이렇게 반복됐다.

```
[failover] 서킷 CLOSED -> OPEN (연속 실패 3회: RedisException) deadMasterAttempts=3
[failover] 서킷 OPEN -> HALF_OPEN (복구 신호(open-timeout))
[failover] 서킷 HALF_OPEN -> OPEN (프로브 실패: RedisException)
```

**검증된 것.**

| | |
|---|---|
| 서킷이 죽은 서버 호출을 막는다 | 큐 등록 **1,290건** 중 실제로 죽은 Redis 를 때린 것은 **28회**. 1/46 |
| 조용한 실패가 진단 가능한 실패가 된다 | 익명 `비동기 작업 실패` 가 **0건**. 대신 `[failover]` 접두사와 지표(`deadMasterAttempts` / `dropped` / `attempts` / `age`)가 붙은 로그가 남는다. (플래그 OFF 였던 실험 2 판 B 에서는 익명 ERROR 가 101건이었다. 그쪽은 단일 스레드 프로브라 건수를 1:1 로 비교할 수는 없다) |
| pub/sub 구독이 작동한다 | 판 C 에서 `복구 신호 수신 source=pubsub` 확인 (§8-7) |
| 프로브에 PING 을 안 쓴 판단이 옳았다 | 죽은 Redisson 경로에서 프로브가 `RedisException` 으로 정확히 실패했다. PING 이었으면 서킷이 닫힌 채 전부 실패했을 것이다 |

**검증되지 않은 것 / 실패한 것.**

| | |
|---|---|
| **재배정 성공 0건** | 판 B 에서는 Redisson 이 끝까지 죽은 노드만 본다. 몇 번을 재시도해도 배정은 성공할 수 없다 |
| `drainMs` / `withinTtl` / `drained` | **미측정.** 판 C 는 큐가 비었고 판 B 는 성공이 0 이라 비율이 정의되지 않는다 |
| `Δ_signal`(`t_signal − t_switch`) | **미측정.** `source=pubsub` 이 온 것만 확인했다 |
| 판 T / T-fast / E 비교 (복구 계기 축) | **미실행.** 위 두 이유로 잴 판이 없다 |
| 판 B 의 `open-timeout` 폴백이 pub/sub 이었다면 달랐을까 | **미확인.** §8-8 의 결함 때문에 판 B 에서는 pub/sub 이 아예 꺼져 있었다 |

### 9-4. 결론

> **재시도·서킷은 장애를 복구하지만 설정 실수는 고치지 못한다.
> 대신 "조용한 실패" 를 "진단 가능한 실패" 로 바꾼다.**

판 B 에서 이 기능이 한 일은 정확히 두 가지다.

1. 죽은 서버를 1,290번 때릴 뻔한 것을 **28번**으로 줄였다 (장애 중 애꿎은 부하를 안 만든다)
2. 스택 트레이스만 있던 익명 ERROR 101건을 **지표가 딸린 `[failover]` 로그**로 바꿨다

**매칭은 여전히 하나도 안 됐다.** 그건 `RedissonConfig` 를 Sentinel 로 바꿔야 고쳐진다.
**기능으로 설정을 덮으려 하지 마라** — 이 실험이 준 교훈은 그쪽이다.
