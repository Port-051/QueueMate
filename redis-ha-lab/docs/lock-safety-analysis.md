# 분산 락 안전성 분석 — Sentinel 페일오버 상황

`PoolLock` 이 Sentinel 페일오버에서 **어느 줄에서 어떻게 깨지는지** 코드를 인용해서 짚는다.
실험으로 재현하는 절차는 `EXPERIMENTS.md` 실험 4·5 인데, **그 둘은 미실행이다** (§2-a).

**실측이 붙은 항목은 §1-4(비동기 예외 삼킴)와 §1-5(락 클라이언트가 페일오버를 모름)
둘뿐이다** — `EXPERIMENTS.md` 실험 2 판 B 에서 **조용한 유실 202건**으로 확인했다 (§1-4-a).
나머지는 여전히 코드와 Redis 복제 모델에서 읽은 것이지 잰 것이 아니다.

인용은 전부 원문 그대로이며 `파일:라인` 을 병기한다
(`docs/PERFORMANCE_EVIDENCE.md` 가 지키는 규칙과 같다).

---

## 0. 지금 구조 요약

| 층 | 무엇 | 어디 |
|---|---|---|
| 락 획득 | Redisson `RLock.tryLock(3000, 3000, MILLISECONDS)` | `PoolLock.java:66` |
| 락 대상 | 후보 풀 하나 = `qm:lock:pool:qm:party:open:LOL:{mode}:{voice}:{purpose}` | `PoolLock.java:43` + `LolPartyKeys.java:24-28` |
| 임계 구역 | 후보 순회 전체 (`create-or-check` → 차단 판정 → `join-party`) | `UntieredAssigner.java:50-76` |
| 호출부 | `poolLock.run(keys.poolKey(command), () -> ...assign(...))` | `LolCandidateRule.java:57`, `:60` |
| 실행 스레드 | `@Async("matchingExecutor")` — 톰캣 스레드가 아니다 | `MatchTrigger.java:25` |
| 클라이언트 | Redisson `useSingleServer()` — **자바 코드에 박혀 있다** | `RedissonConfig.java:42` |

락이 **Lua 를 대체하지 않는다**는 점이 중요하다. `PoolLock` 클래스 주석이 그렇게 적혀 있지만
(`PoolLock.java:30-32`), 실제로는 락 **안에서** Lua 를 여러 번 부르는 구조다.
락은 "Lua 호출 사이의 틈" 을 메우려고 있다.

---

## 1. 취약점 — 어느 줄이 어떻게 깨지나

### 1-1. `PoolLock.java:66` — 락이 master 한 대에만 기록된다

```java
acquired = lock.tryLock(WAIT_MILLIS, LEASE_MILLIS, TimeUnit.MILLISECONDS);
```

Redisson `RLock` 은 락 키를 **master 한 대에만** 쓴다. Redis 복제는 비동기라
master 는 replica 의 ACK 를 기다리지 않고 성공을 돌려준다.

```
t=0    앱 A: tryLock -> master 에 HSET 성공, OK 를 받는다
t=1ms  master 사망 (아직 replica 에 복제 안 됨)
t=5s   Sentinel 이 replica 를 승격. 새 master 에는 그 락 키가 없다
t=5s+  앱 B: tryLock -> 성공. A 와 B 가 동시에 같은 풀을 만진다
```

**이건 설정 오류가 아니라 복제 모델의 성질이다.** `WAIT` 나
`min-replicas-to-write` 로 확률을 낮출 수는 있어도 없앨 수 없다 (§3).

재현: `scripts/lock-loss-repro.sh`

### 1-2. `PoolLock.java:54` + `UntieredAssigner.java:50` — 페일오버 중에 유지 시간이 반드시 터진다

```java
private static final long LEASE_MILLIS = 3000;
```

```java
for (int start = 0; start < MAX_CANDIDATE_SCAN; start++) {
```

`MAX_CANDIDATE_SCAN` 은 50 이다 (`LolScriptSupport.java:37`).
한 회차마다 `create-or-check` Lua 를 한 번 부르고, 후보를 찾으면 `join-party` 를
한 번 더 부른다 (`UntieredAssigner.java:51`, `:86`). **최대 51번의 Redis 왕복**이
3초 안에 끝나야 한다.

평상시에는 넉넉하다. `docs/PERFORMANCE_EVIDENCE.md` 가 재 둔 대로 호스트에서
Redis RTT 는 0.11ms 수준이다. 그런데 **페일오버 중에는 왕복 하나가
`application.yaml:8` 의 `timeout: 2s` 까지 늘어난다.**

```
왕복 2번만 타임아웃에 걸려도 4초 → LEASE_MILLIS(3초) 초과
```

유지 시간이 지나면 **Redis 는 락을 지워 버린다.** 그런데 `work.get()` 은 아직
돌고 있다 (`PoolLock.java:77`). `PoolLock` 클래스 주석이 정확히 이 상황을
경고하고 있다.

> **락 안에서 DB 조회 / 외부 호출 / 느린 작업을 하지 마라.** 유지 시간을 넘기면
> 락이 저 혼자 풀리고, 그 사이 들어온 다른 요청과 같은 파티에 동시에 손을 대게 된다.
> (`PoolLock.java:26-28`)

주석은 "느린 작업" 을 걱정했지만, **페일오버는 Redis 명령 자체를 느리게 만든다.**
락 안에 Redis 명령만 있어도 조건이 성립한다.

의도적으로 워치독을 끈 이유도 주석에 있다.

> 명시하지 않으면 Redisson 워치독이 30초를 잡고 백그라운드에서 계속 연장한다.
> 그러면 작업 스레드가 죽어도 락이 30초 동안 안 풀려 그 풀 전체가 멈춘다.
> (`PoolLock.java:51-52`)

**이 판단은 맞다.** 워치독을 켜면 "락이 안 풀림" 문제가 대신 생긴다.
문제는 3000ms 라는 값이 **페일오버를 고려하지 않은 값**이라는 것이다.
정상 상태의 왕복(0.11ms × 51 ≈ 6ms)만 보고 잡혔다.

### 1-3. `PoolLock.java:81` — 해제가 조용히 건너뛰어진다

```java
finally {

    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

이 방어는 주석(주석 처리된 옛 판의 `PoolLock.java:112-116`)이 설명한 대로
`IllegalMonitorStateException` 이 원래 예외를 덮는 것을 막으려고 넣은 것이다.
정상 상황에서는 맞다.

페일오버 상황에서는 두 갈래로 깨진다.

**(a) `false` 가 나와서 해제를 건너뛴다.**
승격된 새 master 에는 락 키가 없으므로 `isHeldByCurrentThread()` 가 `false` 다.
`unlock()` 을 안 부른다. 그런데 이 시점에 **다른 스레드 B 가 잡아 둔 락이
새 master 에 있다.** A 는 아무것도 안 하고 나가고, B 의 락은 정상적으로 남는다.
겉보기에는 아무 문제가 없어 보이지만, **A 는 락 없이 임계 구역을 끝까지 돌았다.**

**(b) 이 줄 자체가 예외를 던진다.**
`isHeldByCurrentThread()` 는 로컬 상태를 보는 것이 아니라 **Redis 에 다시 묻는다**
(락 HASH 에 내 필드가 있는지). Redis 가 아직 안 돌아왔으면 여기서
`RedisException` 이 난다. `finally` 안에서 던진 예외는 `work.get()` 이 던진
원래 예외를 **덮는다** — 옛 판이 막으려고 했던 바로 그 일이 다른 경로로 일어난다.

> **확인 필요**: Redisson 3.52.0 의 `RedissonBaseLock#isHeldByThread` 가
> 원격 호출인지 로컬 캐시를 보는지는 해당 클래스를 직접 읽어 확인해라.
> 이 문서는 "락 상태의 원본은 Redis 이므로 물어봐야 안다" 는 구조에서 추론했다.
> **2026-09-09 기준 여전히 미확인이다** — 실험 4 를 건너뛰었으므로 이 경로를 밟아 보지
> 않았다.

### 1-4. `PoolLock.java:129-138` — 주석이 사실과 다르다 (이 프로젝트에서 가장 실질적인 구멍)

```java
/**
 * 락 실패는 Redis 장애와 같은 자리로 내보낸다 (INV-10 fail-closed).
 *
 * QueryTimeoutException 은 DataAccessException 이므로
 * GlobalExceptionHandler#handleRedisFailure 가 이미 잡는다.
 * ...
 */
private QueryTimeoutException unavailable(String poolKey, Throwable cause) {
```

**이 주석은 이 락의 유일한 호출 경로에서 성립하지 않는다.**

호출 경로를 따라가 보자.

```
MatchingController.createMatchRequest()          (MatchingController.java:38)
  └─ matchRequestService.join()                  (:46)  ← 여기까지가 톰캣 스레드
  └─ matchTrigger.trigger(request)               (:52)  ← @Async 로 던지고 즉시 반환
  └─ return 201 CREATED                          (:54-55)

        ┈┈┈┈ 스레드 경계 ┈┈┈┈

MatchTrigger.trigger()  @Async("matchingExecutor")   (MatchTrigger.java:25)
  └─ LolCandidateRule.canJoin()                      (:32)
      └─ poolLock.run(...)                           (LolCandidateRule.java:57)
          └─ PoolLock.call() → unavailable() 던짐    (PoolLock.java:73)
```

`@Async` 메서드에서 던진 예외는 **호출자에게 전파되지 않는다.**
`AsyncConfig.java:38-41` 이 잡는다.

```java
@Override
public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return (ex, method, params) ->
            log.error("비동기 작업 실패: {} args={}", method.getName(), Arrays.toString(params), ex);
}
```

**로그만 남기고 삼킨다.** `GlobalExceptionHandler#handleRedisFailure` 는 이 예외를
영원히 못 본다. 컨트롤러는 이미 201 을 돌려준 뒤다.

결과:

| | 기대 (주석대로라면) | 실제 |
|---|---|---|
| 응답 | 503 `MATCHING_UNAVAILABLE` + `Retry-After: 5` | **201 CREATED** |
| 클라이언트 | 재시도한다 | "매칭 중" 화면에서 기다린다 |
| 서버 상태 | 활성 요청 없음 | `qm:user:active-request:{userId}` 는 남아 있다 (claim 은 성공했으므로) |
| 풀리는 시점 | 즉시 | `claim-request.lua:28` 의 `EXPIRE 60` 이 지나야 |

`claim-request.lua:26-27` 의 근거 계산도 페일오버를 고려하지 않았다.

> 60 초인 이유: claim 부터 배정까지는 설정상 상한이 7초다
> (차단 조회 300ms + 락 대기 3s + 락 유지 3s). 8배 여유다

페일오버 중에는 락 대기 3초 안에 왕복이 몇 번 타임아웃(2초)에 걸릴지 모른다.
7초 상한은 **정상 상태의 상한**이다. 60초 자체는 여전히 넉넉하지만,
"8배 여유" 라는 근거는 이 상황에서 성립하지 않는다.

**측정 방법**: `scripts/verify-assign.py` 가 이 구멍을 센다 —
HTTP 결과와 Redis 의 실제 상태(`qm:user:active-request:{userId}` 에 `partyId` 가 있나)를
따로 재서 그 차이를 낸다. (`scripts/assign-gap.sh` 가 원래 그 자리였는데,
프로브 CSV 와 t0 를 같이 봐야 해서 python 쪽으로 옮겼다.)

### 1-4-a. 실측 — 202건이 조용히 사라졌다 (실험 2 판 B, 2026-09-09)

**이 항목은 이제 추정이 아니다.** `EXPERIMENTS.md` 실험 2 를 실제로 돌렸다.
판 B(Lettuce 만 Sentinel, Redisson 은 단일 노드)에서 master 를 `docker kill` 하고
60초를 쟀다.

| | 판 B |
|---|---|
| 총 요청 | 278건 (**세 판 중 최고 처리량**) |
| HTTP 201 | 241건 (86.7%) |
| HTTP 503 | 37건 (13.3%) |
| **실제 배정 성공** | **39건 (14.0%)** — 전부 `t0` 이전 |
| **HTTP 성공인데 배정 실패** | **202건 (72.7%)** |
| 그 구간 | 첫 건 `t0+9,360ms` ~ 끝 건 `t0+49,764ms` |

t0 이후 구간별로 보면 더 분명하다.

| 구간 | 요청 | HTTP 201 | 실제 배정 |
|---|---|---|---|
| t0 이전 | 39 | 39 | 39 |
| t0 ~ +10초 | 41 | 4 | **0** |
| +10 ~ +30초 | 99 | **99** | **0** |
| +30초 ~ | 99 | **99** | **0** |

**+10초 이후로는 요청이 전부 201 을 받는데 배정은 하나도 안 된다.**
Lettuce 는 새 master 로 옮겨 가 `claim` 이 성공하니 컨트롤러는 201 을 돌려주고,
Redisson 은 죽은 노드만 보므로 `PoolLock.call()` 에서 터지는데 그 예외는
`@Async` 뒤에서 `AsyncConfig` 가 삼킨다. **위 표의 "기대 vs 실제" 가 그대로 재현됐다.**

앱 로그에서 확인한 것.

| 로그 | 건수 |
|---|---|
| `비동기 작업 실패` (`AsyncConfig` 의 uncaught 핸들러) | **101건** |
| `Redis 장애로 매칭 요청을 거절한다` (`GlobalExceptionHandler`) | 37건 (= 503 건수와 일치) |

> **미확인**: 로그 101건과 조용한 유실 202건이 맞지 않는다. `matchingExecutor` 큐에
> 쌓인 채 끝내 실행되지 않은 작업이 있는 것으로 보이지만 **확인하지 못했다.**
> 만약 그렇다면 **삼킴보다 더 조용하다** — 로그조차 안 남는다.
> 확인하려면 `ThreadPoolTaskExecutor` 의 `queue.size` / `completedTaskCount` 를
> 같이 찍어야 한다.

**이 숫자가 §4 의 우선순위 1번을 뒷받침한다.** 202명이 60초 동안 "매칭 중" 화면을 보다가
아무 설명 없이 끝났다. 그리고 그동안 **모든 겉보기 지표가 정상보다 좋았다** —
처리량 1위, 201 비율 86.7%. 대시보드만 보면 이 판이 가장 건강해 보인다.

같은 실험의 판 C(둘 다 Sentinel)에서는 조용한 유실이 **0건**이다.
즉 이 구멍은 **설정이 반쪽일 때 열린다.** 그래서 §1-5 와 한 몸이다.

### 1-5. `RedissonConfig.java:42` — 락 클라이언트는 페일오버를 아예 모른다

```java
config.useSingleServer()
        .setAddress("redis://" + host + ":" + port)
```

`host` / `port` 는 `@Value("${spring.data.redis.host}")` 로 주입된다
(`RedissonConfig.java:29-30`). **Sentinel 을 태우려면 이 코드를 고쳐야 한다.**
환경 변수나 `application.yaml` 로는 안 된다.

반면 Lettuce(`StringRedisTemplate`)는 Spring Boot 자동 설정이라
`SPRING_DATA_REDIS_SENTINEL_*` 환경 변수만으로 Sentinel 을 탄다.

→ **급할 때 `application.yaml` 만 고치면 데이터 경로는 복구되고 락만 계속 죽는다.**
`EXPERIMENTS.md` 실험 2 의 "판 B" 가 이것이다. 겉보기 지표(201 응답)는 멀쩡한데
매칭이 하나도 안 되는 상태라 가장 늦게 발견된다.

### 1-6. 락이 유실됐을 때 실제로 깨지는 것

락이 두 스레드에 동시에 잡혔다고 해서 **정원 초과(INV-3)가 나지는 않는다.**
그건 Lua 안의 원자적 검사가 따로 막는다 (`join-party.lua` 의 `HINCRBY` +
`size >= target` 판정). 락은 그것보다 약한 것을 지킨다.

`UntieredAssigner.java:39-41` 이 무엇을 지키는지 정확히 적어 두었다.

> **순회 전체가 한 락 안에 있어야 한다.** 회차마다 락을 놓으면 그 사이 다른 요청이
> 색인(ZSET)을 바꾼다. 그러면 같은 인덱스가 다른 파티를 가리켜 어떤 파티는 건너뛰고
> 어떤 파티는 두 번 보게 된다.

락이 유실되면 정확히 그 일이 난다.

| 증상 | 무엇이 일어나나 |
|---|---|
| 후보 건너뜀 | `ZRANGE start start` 의 `start` 가 가리키는 파티가 그 사이 바뀐다. 들어갈 수 있었던 파티를 못 보고 새 파티를 만든다 |
| 후보 중복 조회 | 같은 파티를 두 번 보고 차단 판정을 두 번 한다. `MAX_CANDIDATE_SCAN` 50 을 헛되이 소모한다 |
| 불필요한 새 파티 | `UntieredAssigner.java:79` 의 `createNewParty` 로 빠진다. 1인 파티가 늘어난다 |
| needs 색인 팽창 | 1인 파티마다 `keyValue` 4개의 색인에 등록된다 (`create-or-check-party-untiered.lua:95-102`) |

**전부 조용하다.** 예외도 안 나고 로그도 안 남는다. 매칭 실패율과 대기 시간이
올라갈 뿐이다. `docs/CONCURRENCY_TESTS.md` 의 기존 테스트들도 이걸 못 잡는다 —
INV-1/3/7/8 은 Lua 가 지키므로 락 없이도 통과한다.

---

## 2. Redlock 논쟁 — 이 프로젝트에 어떻게 걸리나

### 짧은 정리

2016년 Martin Kleppmann 이 "How to do distributed locking" 에서 Redis 의 Redlock
알고리즘이 안전하지 않다고 썼고, Redis 저자 antirez(Salvatore Sanfilippo)가
"Is Redlock safe?" 로 반박했다. 요지만 추리면 이렇다.

| 쟁점 | Kleppmann | antirez |
|---|---|---|
| 무엇을 위한 락인가 | **효율(efficiency)** 과 **정확성(correctness)** 을 구분해야 한다. 정확성이 목적이면 Redlock 은 부족하다 | 두 목적 모두에 쓸 수 있게 만들었다 |
| GC 정지 / 스케줄링 지연 | 락을 잡은 프로세스가 GC 로 몇 초 멈추면 리스가 만료되고, 깨어난 뒤에도 자기가 락을 쥔 줄 안다. **어떤 리스 기반 락도 이걸 못 막는다** | 실제 시스템에서 그런 정지는 드물고, 리스 갱신으로 완화된다 |
| 시계 | Redlock 은 노드 간 시계가 크게 어긋나지 않는다고 가정한다. 관리자가 시계를 맞추면 락이 깨진다 | 단조 시계를 쓰면 되고, 그런 운영은 하지 말아야 한다 |
| 해법 | **fencing token** — 락을 줄 때 단조 증가하는 번호를 같이 주고, **보호 대상 자원이** 낮은 번호의 쓰기를 거부해야 한다 | 자원이 토큰을 검사할 수 있으면 애초에 락이 덜 필요하다. 모든 자원이 그럴 수 있는 것도 아니다 |

### 이 프로젝트에 걸리는 지점

**중요한 것은, 이 프로젝트는 Redlock 을 쓰지도 않는다는 것이다.**
Redisson `RLock` + 단일 master 다. Redlock 은 여러 독립 master 에 락을 걸어
과반을 얻는 알고리즘이고, **antirez 가 Redlock 을 만든 이유가 정확히
"단일 master + Sentinel 은 비동기 복제 때문에 락이 안전하지 않다" 였다.**

즉 이 프로젝트가 서 있는 자리는 논쟁의 두 진영 중 어느 쪽도 아니고,
**둘 다 안전하지 않다고 인정하는 자리**다.

| | 안전성 | 이 프로젝트 |
|---|---|---|
| 단일 master + Sentinel | 페일오버 시 락 유실 (§1-1) | **여기** |
| Redlock (N개 독립 master) | 유실은 줄지만 Kleppmann 의 GC/시계 비판이 남는다 | 아님 |
| 합의 기반 (etcd, ZooKeeper) | 유실 없음. 그래도 fencing 없이는 GC 정지 문제가 남는다 | 아님 |

그리고 **Kleppmann 의 비판은 여기에도 그대로 걸린다.**
`LEASE_MILLIS = 3000` 은 리스다. `matchingExecutor` 스레드가 GC 나
컨테이너 CPU throttle 로 3초 이상 멈추면, 깨어난 뒤에도 자기가 락을 쥔 줄 알고
`work.get()` 을 계속한다 (`PoolLock.java:77`). 페일오버가 없어도 성립한다.

→ **§1-2 는 페일오버 특유의 문제가 아니라, 리스 기반 락 일반의 문제가
페일오버 때문에 훨씬 잘 일어나게 된 것이다.**

---

## 2-a. 실험 4·5 는 돌리지 않았다 (2026-09-09)

이 문서의 §1-1 ~ §1-3 과 §3 (B)(C) 는 `EXPERIMENTS.md` 실험 4(락 유실 재현)와
실험 5(스플릿 브레인)를 근거로 삼도록 쓰여 있다. **그 둘은 실행하지 않았다.**
아래 서술은 **여전히 코드와 Redis 복제 모델에서 읽은 것이지, 실측이 아니다.**

**실험 4 를 건너뛴 근거.**

1. **결론이 자명하다.** Redis 복제는 비동기고 `RLock` 은 master 한 대에만 쓴다.
   "락을 잡은 직후 master 가 죽으면 승격된 replica 에 그 락이 없다" 는 재현 없이도 참이다.
   `lock-loss-repro.sh` 가 하는 일은 `docker pause` 로 그 창을 인위적으로 벌리는 것이라
   나오는 답이 이미 정해져 있다.
2. **피해를 재려면 임계 구역이 먼저 완성돼야 한다.** §1-6 에서 정리했듯 락 유실의 비용은
   "정원 초과" 가 아니라 "후보를 건너뛰거나 두 번 보는 것" 이고, 그것이 사용자에게 보이는
   손해가 되려면 **INV-4 / INV-5(proposal)와 INV-6(차단 검증)** 이 있어야 한다.
   셋 다 미구현이라 지금 재면 **"락이 유실됐다" 는 사실만 확인하고 얼마나 손해인지는
   못 잰다.** 숫자 없는 재현은 이 문서가 이미 글로 하고 있다.

**실험 5 를 건너뛴 근거.** 축소판을 실험 3 중에 우연히 관측했다 —
죽인 노드를 `docker start` 로 되살리면 자기 설정 파일 때문에 **약 2초 동안 자기를
master 라고 믿는다**(실측). Sentinel 이 `REPLICAOF` 로 강등시킨다.
"격리된 구 master 는 자기가 죽은 줄 모른다" 는 전제가 그 2초 안에 들어 있다.
제대로 하려면 T-B 토폴로지를 다시 깔아야 하는데(`README.md` §2), 그 비용에 비해
새로 얻는 결론이 "유실된다" 하나뿐이라 뒤로 미뤘다.

**대신 지금 할 수 있는 조각이 하나 있다.** §3 (C) `min-replicas-to-write` 의
**가용성 비용**은 락 유실과 무관하게 지금도 잴 수 있다. 절차는 `EXPERIMENTS.md`
실험 4 의 "지금 당장 할 수 있는 조각" 에 있다. 그 측정이 나오면 실험 3 과 같은 모양의
결론("안전을 사는 값이 얼마인가")이 나오고, **그때 §4 의 3번을 결정할 수 있다.**

**한편 §1-4 와 §1-5 는 실측으로 확인됐다.** 위 §1-4-a 를 보라. 실험 4 없이도
"이 프로젝트에서 가장 실질적인 구멍" 이라는 §1-4 의 평가는 숫자로 뒷받침된다.

---

## 3. 대안과 트레이드오프

### (A) 아무것도 안 한다 — 위험을 문서화하고 감수

| | |
|---|---|
| 비용 | 0 |
| 얻는 것 | 없음 |
| 잃는 것 | 페일오버마다 조용한 매칭 실패 (§1-6) |
| 언제 맞나 | **락 유실의 피해가 "매칭이 조금 덜 된다" 뿐이라면 합리적이다.** §1-6 에서 봤듯이 INV-3/7/8 은 Lua 가 따로 지킨다 |

포트폴리오 관점에서는 이 선택도 근거만 있으면 좋은 답이다.
"측정했고, 피해 범위를 확인했고, 감수하기로 했다" 가 "락을 걸었으니 안전하다"
보다 낫다.

**단 §1-4(비동기 삼킴)는 별개로 고쳐야 한다.** 그건 락 안전성이 아니라
"실패를 사용자에게 안 알린다" 는 문제다.

### (B) `WAIT` 를 락 획득 뒤에 넣는다

```java
// 개념 스케치. 실제로는 Redisson 의 RFuture 나 StringRedisTemplate 으로 부른다.
acquired = lock.tryLock(WAIT_MILLIS, LEASE_MILLIS, TimeUnit.MILLISECONDS);
if (acquired) {
    long replicated = redis.execute((RedisCallback<Long>) c ->
            (Long) c.execute("WAIT", "1".getBytes(), "500".getBytes()));
    if (replicated < 1) {
        lock.unlock();
        throw unavailable(poolKey, null);   // 복제를 확인 못 했으면 거절 (INV-10)
    }
}
```

| | |
|---|---|
| 얻는 것 | "복제가 안 따라온 채로 죽는" 창을 크게 줄인다 |
| 잃는 것 | 락 획득마다 replica 왕복이 추가된다. `WAIT_MILLIS` 예산을 먹는다 |
| **막지 못하는 것** | `WAIT 1` 은 "replica **한 대**가 받았다" 만 보장한다. **그 replica 가 승격된다는 보장이 없다.** replica1 이 받았는데 Sentinel 이 replica2 를 승격시키면 그대로 유실이다 |
| | `WAIT 2` 로 올리면 그 구멍은 막히지만, replica 한 대만 죽어도 모든 락 획득이 실패한다 |

측정: `EXPERIMENTS.md` 실험 4 절차 (C-1). **단, 실험 4 는 미실행이다** — 아래 상자를 보라.

### (C) `min-replicas-to-write` / `min-replicas-max-lag`

`redis/master.conf` 에 자리를 만들어 두었다 (`.env` 의 `MIN_REPLICAS_TO_WRITE`).

| | |
|---|---|
| 얻는 것 | 복제가 끊긴 master 는 **쓰기를 아예 거부한다.** 스플릿 브레인에서 특히 강하다 (실험 5) |
| 잃는 것 | **가용성.** replica 가 조건을 못 채우면 매칭 전체가 멈춘다 |
| 앱에 미치는 영향 | `NOREPLICAS` 오류 → `DataAccessException` → `GlobalExceptionHandler.java:44-51` 이 503 으로 바꾼다. **INV-10 의 fail-closed 와 방향이 같다** |
| **막지 못하는 것** | 조건 확인과 쓰기가 원자적이지 않다. 확인 직후 replica 가 죽으면 그 쓰기는 여전히 유실될 수 있다 |
| 코드 변경 | **없다.** Redis 설정만 바꾸면 된다 |

**코드를 안 고쳐도 되는 유일한 방어**라는 점이 크다. (B)와 같이 쓰면 겹쳐서 막는다.

### (D) fencing token

Kleppmann 의 해법이다. 락을 줄 때 단조 증가 번호를 같이 주고, 보호 대상 자원이
자기가 본 최대 번호보다 작은 요청을 거부한다.

이 프로젝트에서 **보호 대상 자원은 Redis 자체**다 (파티 HASH, needs ZSET).
그래서 이렇게 된다.

```lua
-- create-or-check-party-untiered.lua 에 넣는다면
local fence = tonumber(ARGV[10])
local seen  = tonumber(redis.call('HGET', poolFenceKey, 'max') or '0')
if fence < seen then
  return { -3, '', 0 }          -- 뒤늦게 도착한 락 소유자다. 거부한다
end
redis.call('HSET', poolFenceKey, 'max', fence)
```

| | |
|---|---|
| 얻는 것 | §1-2 (리스 만료 후에도 계속 도는 스레드)를 **실제로 막는다.** GC 정지도 막는다 |
| 잃는 것 | 모든 Lua 스크립트에 인자가 하나 늘고 분기가 하나 는다. `CLAUDE.md` §4 의 "조건 하나가 늘 때마다..." 와 같은 종류의 복잡도 비용 |
| **막지 못하는 것** | **fence 카운터 자체가 Redis 에 있다.** 페일오버로 카운터가 되감기면 fencing 이 무의미해진다. §1-1 을 못 고친다 |
| 고치려면 | 카운터를 Redis 밖(Postgres 시퀀스 등)에 둬야 하는데, 그러면 락 경로에 DB 왕복이 생긴다 — `PoolLock.java:26-28` 이 금지한 바로 그것이다 |

→ **fencing 은 §1-2 에는 답이지만 §1-1 에는 답이 아니다.** 둘을 구분해야 한다.

### (E) 락을 없앤다 — 순회를 Lua 한 덩어리로 접는다

**가장 근본적인 답이고, 이 프로젝트에서 실현 가능성이 있다.**

락이 필요한 이유는 후보 순회가 **여러 번의 왕복**으로 쪼개져 있기 때문이다
(`UntieredAssigner.java:50-76`). 왕복이 하나면 Lua 의 원자성이 락을 대신한다.

지금 순회가 쪼개져 있는 이유는 **차단 판정을 자바가 하기 때문**이다.

```java
String partyId = (String) found.get(2);
List<String> memberIds = memberIds(found);
if (blockedWith(memberIds, blockedUserIds)) {
    continue;   // 차단 관계다. 다음 후보를 본다
}
joinParty(command, config, scriptKeys, partyId, now, memberIds);
```
(`UntieredAssigner.java:69-74`)

그런데 **차단 목록은 이미 락을 잡기 전에 통째로 읽어 뒀다.**

```java
Set<String> blockedUserIds = Set.copyOf(blockRepository.findBlockedUserIds(command.getUserId()));
```
(`LolCandidateRule.java:54`)

즉 판정에 필요한 재료가 전부 손에 있다. **`blockedUserIds` 를 `ARGV` 로 넘기면
Lua 안에서 판정할 수 있다.**

| | |
|---|---|
| 얻는 것 | 락이 통째로 사라진다. §1-1 ~ §1-4 가 전부 없어진다. 왕복도 51번 → 1번으로 준다 |
| 잃는 것 | Lua 가 길어진다. `CLAUDE.md` §4 의 **"Lua 안에 후보를 순회하는 루프를 넣지 마라"** 와 정면으로 부딪힌다 |
| 그 금지의 근거 | "쓰기를 한 스크립트는 `SCRIPT KILL` 이 안 되고 `SHUTDOWN NOSAVE` 만 남는다 (docs/11 #33)" |
| 반론 | 그 금지는 **무한/비유계 루프**를 겨냥한 것이다. 여기 루프는 `MAX_CANDIDATE_SCAN`(=50)으로 **상한이 박혀 있다**. `ZRANGE 0 49` 로 한 번에 가져와 in-memory 리스트를 도는 것이라 왕복도 추가되지 않는다 |
| 실측이 필요한 것 | 50개 순회 Lua 의 실행 시간. Redis 전체가 그동안 멈추므로 `down-after-milliseconds` 보다 훨씬 짧아야 한다 (`EXPERIMENTS.md` 실험 3 해석 2) |
| 남는 위험 | 유실 자체는 여전히 있다. Lua 가 원자적으로 쓴 것도 복제가 안 되면 사라진다 (실험 5 해석 2) |

**이 대안은 `CLAUDE.md` 의 규칙을 바꾸는 결정이다.** `docs/11_DECISION_LOG.md`
형식대로 #33 을 취소선 처리하고 "개정됨 → #N" 을 다는 절차를 밟아야 한다.
그리고 그 근거로 삼을 실측이 실험 3 이다.

### (F) 락을 Redis 밖으로 뺀다

| 방법 | 얻는 것 | 잃는 것 |
|---|---|---|
| PostgreSQL advisory lock (`pg_try_advisory_lock`) | 유실 없음. 이미 DB 커넥션이 있다 (`BlockRepository`) | 락 경로에 DB 왕복 추가. `application.yaml:31` 의 `connection-timeout: 300` 이 락 대기와 충돌한다. **매칭 상태를 DB 로 옮기지 않는다는 `CLAUDE.md` §3 과 충돌** |
| etcd / ZooKeeper | 합의 기반이라 유실 없음. 리스 갱신 지원 | **배포 단위가 하나 는다.** `docs/AWS_ARCHITECTURE.md` 가 NAT Gateway $43 도 아끼는 판이다. 비용과 운영 부담이 이 프로젝트 단계에 안 맞는다 |
| 앱 인스턴스 파티셔닝 (poolKey 를 해시해서 담당 인스턴스로 라우팅) | **분산 락이 아예 필요 없어진다.** 같은 풀은 항상 같은 인스턴스가 처리하므로 JVM 내부 락이면 충분하다 | 라우팅 계층이 필요하다. 인스턴스가 죽으면 재배치 동안 그 풀이 멈춘다. Stage 1(단일 EC2)에서는 **인스턴스가 하나라 지금 당장은 공짜다** |

마지막 것이 흥미롭다. **지금 이 앱은 인스턴스가 하나다.**
`docs/CONCURRENCY_TESTS.md` 도 "다중 인스턴스 미검증" 을 한계로 적어 두었다.
즉 지금 `PoolLock` 이 막고 있는 것은 **같은 JVM 안의 스레드끼리의 경합**이고,
그건 `ReentrantLock` 하나면 된다 — 유실도 없고 왕복도 없다.

→ **"분산 락이 지금 정말 필요한가" 를 먼저 물어야 한다.**
필요해지는 시점(인스턴스 2대 이상)을 `docs/11_DECISION_LOG.md` 의
재검토 트리거로 적어 두는 것이 이 단계에 맞는 답일 수 있다.

---

## 4. 정리 — 무엇부터 할 것인가

우선순위는 "피해 크기 ÷ 고치는 비용" 으로 매겼다.

| 순위 | 무엇 | 왜 | 비용 |
|---|---|---|---|
| 1 | **§1-4 (비동기 삼킴)** 을 고친다 | 락 안전성과 무관하게 지금도 사용자가 영원히 기다린다. 페일오버 없이 Redis 가 잠깐 느려져도 난다. **실측: 판 B 에서 202건 (72.7%)** (§1-4-a) | 작다. `MatchTrigger` 의 실패를 알림/상태로 내보내면 된다 |
| 2 | **§1-5** — `RedissonConfig` 를 Sentinel 로 바꾼다 | 안 바꾸면 Sentinel 을 띄운 의미가 절반뿐이다. **실측: 판 B 는 t0 이후 배정 0건, 판 C 는 6.04초 만에 완전 복구** | 작다. `docs/app-config-snippets.md` §2 |
| 3 | **(C) `min-replicas-to-write`** | 코드 변경 0 으로 실험 5 를 막는다 | 설정 한 줄. 대신 가용성 결정이 필요하다 |
| 4 | **§1-2** — `LEASE_MILLIS` 재산정 | 지금 값은 정상 상태만 보고 잡혔다 | 작다. 다만 근거가 될 실측(실험 3)이 먼저다 |
| 5 | **(E) 락 제거** 또는 **(F) 파티셔닝** | 근본 해결 | 크다. `CLAUDE.md` 규칙 개정 또는 아키텍처 결정이 필요하다 |

**1~4 는 실험 없이도 근거가 서지만, 5 는 반드시 측정이 먼저다.**
그게 이 폴더를 만든 이유다.

> **2026-09-09 갱신.** 1번과 2번은 이제 실측 근거가 있다 (§1-4-a, `RESULTS.md`).
> 3번은 **미측정**이다 — `min-replicas-to-write` 의 가용성 비용부터 재야 한다
> (§2-a 의 마지막 문단). 4번·5번은 그대로다.
