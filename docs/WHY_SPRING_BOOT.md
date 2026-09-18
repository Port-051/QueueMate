# 왜 Spring Boot인가 — 그리고 가상 스레드, 동시 접속, 알림 서비스의 스택

> **이 문서를 먼저 어떻게 읽어야 하는지.**
>
> 1. **"왜 Spring Boot인가"를 논증한 결정 항목은 없다.** `docs/11_DECISION_LOG.md` 의
>    Fixed decisions #3 은 *"Backend는 Java Spring Boot."* 한 줄뿐이다(`docs/11_DECISION_LOG.md:24`).
>    `WHY_POSTGRESQL.md` §0 과 같은 처지다. 따라서 §1 은 **초기 전제를 지금 코드에 비추어 다시
>    설명한 것**이다. 다만 근거는 전부 이 저장소에서 확인할 수 있는 것으로 댄다.
> 2. §2~§4 는 일반 기술 설명이다. 버전에 따라 달라지는 숫자는 그렇게 적었다.
> 3. §5 는 알림 서비스(`notification`)의 스택 결정이다. 예전 문서의 `app:realtime` 이
>    이제 **notification** 이다. 이 문서 안에서도 그렇게 부른다.

---

## 결론

| 질문 | 한 줄 답 |
|---|---|
| 왜 Spring Boot인가 | 이 도메인의 핵심은 **동시성과 정합성**이고, 그 주변(Redis·락·비동기·스케줄링·DB)을 검증된 모듈로 채워 **불변식 코드에만 집중**할 수 있었다 |
| 한계는 | 무겁다(기동 시간·메모리·설정). Stage 1 이 단일 EC2 상시 실행이라 치명적이지 않다 |
| 스레드 모델 한계는 | Java 21 **가상 스레드**로 줄일 수 있다. **지금 이 저장소는 켜져 있지 않다** |
| "Spring은 동시에 수천 명밖에 못 받나" | **아니다.** 처리 중 요청 수(200)와 열린 연결 수(8192)와 처리량은 서로 다른 숫자다 (§3) |
| 알림 서비스 스택 | **Spring Boot(MVC) + SSE + Redis Pub/Sub.** matching 과 한 벌의 운영 지식을 쓴다 |

---

## 0. 이 저장소에서 직접 확인한 사실

아래 표의 값은 전부 이 저장소 파일에서 읽은 것이다. 본문은 이 값을 전제로 쓴다.

| 항목 | 값 | 출처 |
|---|---|---|
| Spring Boot | **4.1.1** | `backend/build.gradle` — `id 'org.springframework.boot' version '4.1.1'` |
| Java | **21** (toolchain) | `backend/build.gradle` — `languageVersion = JavaLanguageVersion.of(21)` |
| 웹 스택 | **Spring MVC (서블릿)**. WebFlux 없음 | `spring-boot-starter-webmvc` |
| Redis | Spring Data Redis(Lettuce) + Lua | `spring-boot-starter-data-redis` |
| 분산 락 | Redisson **3.52.0**, 코어만 (starter 아님) | `org.redisson:redisson:3.52.0`, `config/redis/RedissonConfig.java` |
| DB | JPA + H2(기본) / PostgreSQL 드라이버 | `spring-boot-starter-data-jpa`, `runtimeOnly h2 / postgresql` |
| 관측 | Actuator (`health,info,metrics` 노출) | `spring-boot-starter-actuator`, `application.yaml` `management.endpoints` |
| 검증 | Bean Validation | `spring-boot-starter-validation` |
| Flyway | **없다** | `build.gradle` 에 없음. `application.yaml` 의 `ddl-auto: none` 주석만 "Flyway가 만든다"고 적혀 있다 |
| AWS SDK | **없다** | `build.gradle` 에 없음 (SQS 발행 미구현) |
| `@Transactional` | **0건** | `grep -rn Transactional backend/src/main` |
| 가상 스레드 | **꺼져 있다** | `application.yaml` 에 `spring.threads.virtual.enabled` 없음 |
| Tomcat 스레드 설정 | **없다 (기본값 사용)** | `application.yaml` 에 `server.tomcat.*` 없음. `server.port` 만 있다 |
| `@Async` | 1곳 — `service/MatchTrigger.java#trigger()` 의 `@Async("matchingExecutor")` | `config/AsyncConfig.java` 가 풀을 만든다 |
| `matchingExecutor` | `ThreadPoolTaskExecutor` core 4 / max 8 / queue 200 / `CallerRunsPolicy` | `config/AsyncConfig.java` |
| `@Scheduled` | 1곳 — `service/ProposalSweeper.java#sweep()`, `fixedDelayString = "${queuemate.sweep.interval-ms}"`(기본 1000) | `@EnableScheduling` 은 `MatchingApplication.java` |
| DI 로 갈아 끼우는 규칙 | `rule/CandidateRule.java` 인터페이스 + `LolCandidateRule` / `ValorantCandidateRule` / `PubgCandidateRule` | 주입받는 곳: `MatchTrigger`, `MatchCancelService` 의 `List<CandidateRule>` |
| JPA 사용처 | `block/BlockRepository.java` 하나 (`JpaRepository` 가 아니라 `Repository` 상속, 읽기 전용) | CLAUDE.md §3 |
| Hikari 커넥션 대기 | `connection-timeout: 300` (ms) | `application.yaml` |

---

## 1. 왜 서버를 Spring Boot로 했나

면접·발표에서 **선택 이유 → 대안 비교 → 한계를 알고 선택** 순서로 답할 수 있게 정리한다.

### 1-1. 선택 이유 ① — 도메인의 핵심이 동시성·정합성이다

QueueMate 매칭은 **여러 사람이 동시에 같은 파티의 같은 빈자리에 들어가려는 경쟁**이다.
그래서 깨지면 안 되는 규칙이 불변식(INV)으로 정의돼 있다 (CLAUDE.md §4).

| INV | 규칙 | 깨지면 생기는 일 |
|---|---|---|
| INV-1 | 한 사용자는 활성 매칭 요청을 1개만 가진다 | 한 사람이 두 파티에 동시에 배정된다 |
| INV-3 | 파티 인원은 모드의 정원을 넘지 않는다 | 5인 파티에 6명이 들어간다 |
| INV-4 | 전원 수락 전에는 확정 금지 | 한 명이 안 눌렀는데 파티가 확정된다 |
| INV-5 | 만료·거절·취소된 제안은 다시 확정될 수 없다 | 이미 깨진 제안이 되살아난다 |
| INV-7 | 같은 사용자의 파티 멤버 중복 금지 | 한 파티에 같은 사람이 두 번 들어간다 |

**정직하게 적을 것:** 이 불변식들을 실제로 지키는 것은 Spring이 아니라 **Redis Lua 스크립트**와
**Redisson 락**이다(`redis/**/*.lua`, `redisLock/PoolLock.java`). Spring의 역할은 그 주변을
표준 부품으로 채워서, 개발 시간이 불변식 코드에 몰리게 해 준 것이다.

| 필요한 것 | 이 저장소에서 Spring이 준 것 | 위치 |
|---|---|---|
| Lua 스크립트 실행 | Spring Data Redis `StringRedisTemplate` + `RedisScript` 빈 | `config/redis/**` |
| 두 Lua 사이 틈을 막는 분산 락 | Redisson 을 빈으로 등록 | `config/redis/RedissonConfig.java`, `redisLock/PoolLock.java` |
| 요청 스레드를 붙잡지 않는 배정 | `@Async("matchingExecutor")` | `service/MatchTrigger.java`, `config/AsyncConfig.java` |
| 제안 만료 스위프 | `@Scheduled(fixedDelay...)` | `service/ProposalSweeper.java` |
| Redis 장애 시 503 (INV-10) | `@RestControllerAdvice` 로 `DataAccessException` 일괄 변환 | `common/error/GlobalExceptionHandler.java` |
| 차단 조회 (INV-6 선필터) | Spring Data JPA 선언형 쿼리 | `block/BlockRepository.java` |

트랜잭션(`@Transactional`)은 **지금은 쓰지 않는다**(0건). 쓰게 되는 자리는 제안 확정 시
`match_proposals` + `proposal_members` + `outbox` 를 한 트랜잭션으로 쓰는 지점이고,
아직 미구현이다(`WHY_POSTGRESQL.md` §2, CLAUDE.md §4 INV-4 "outbox 발행은 없음").
그때 Spring의 선언형 트랜잭션을 그대로 쓸 수 있다는 것이 선택 이유의 일부다.

### 1-2. 선택 이유 ② — 생태계가 표준 조합으로 맞물린다

| 필요 | 표준 조합 | 이 저장소 상태 |
|---|---|---|
| Redis | Spring Data Redis (Lettuce) | 사용 중 |
| 분산 락 | Redisson | 사용 중 (코어 3.52.0) |
| RDB | Spring Data JPA + Hikari | 사용 중 (차단 조회 하나) |
| 스키마 마이그레이션 | Flyway | **미도입** |
| 헬스체크·메트릭 | Actuator (+ Micrometer) | 사용 중 |
| SQS FIFO 발행 | AWS SDK for Java (또는 Spring Cloud AWS) | **미도입** |
| 테스트 | Spring Boot Test + JUnit 5 | 사용 중 (동시성 테스트 5종) |

부품마다 "Spring과 붙이는 법"이 이미 정해져 있어서, 붙이는 일에 설계 결정을 쓰지 않아도 된다.

### 1-3. 선택 이유 ③ — 정적 타입과 DI가 "게임별로 다른 규칙" 구조에 맞는다

게임 3개가 각자 다른 매칭 규칙을 가진다 — LoL은 포지션 중복 금지, VALORANT는 합류할 때마다
티어 범위를 좁힘, PUBG는 핵심 조건(플랫폼) 중복 허용(CLAUDE.md §4 "Lua 스크립트는 게임별로 나눈다").

이것을 **인터페이스 하나 + 구현체 3개를 DI로 주입**하는 구조로 풀었다.

```java
// rule/CandidateRule.java
public interface CandidateRule {
    boolean supports(GameKey game);
    void canJoin(CreateMatchRequestCommand command);
    CancelResult leave(ActiveRequest active, String expectedRequestId);
}
```

| 구현체 | 위치 |
|---|---|
| `LolCandidateRule` | `rule/lol/LolCandidateRule.java` |
| `ValorantCandidateRule` | `rule/valorant/ValorantCandidateRule.java` |
| `PubgCandidateRule` | `rule/pubg/PubgCandidateRule.java` |

호출부(`service/MatchTrigger.java`, `service/MatchCancelService.java`)는 `List<CandidateRule>` 을
주입받아 `supports(game)` 로 고른다. 게임별 `if/else` 가 호출부에 없고, 게임을 추가할 때
호출부를 고치지 않는다. 컴파일러가 인터페이스 시그니처를 강제하므로 한 게임만 메서드를
빠뜨리는 일이 빌드에서 걸린다.

**DI의 함정도 겪었다.** 같은 타입(`RedisScript<List>`) 빈이 여럿이면 Spring은 주입 필드 이름으로
고르므로, 이름이 겹치면 **에러 없이 다른 게임의 스크립트가 주입된다.** 그래서 `lol*` / `pubg*` 접두사
규칙을 뒀다(CLAUDE.md §4).

### 1-4. 대안 비교

| 대안 | 이 프로젝트에서 얻는 것 | 잃는 것 |
|---|---|---|
| **Spring Boot (MVC)** — 채택 | 위 1-1~1-3 전부. 동기 코드로 동시성 로직을 읽기 쉽게 쓴다 | 기동 시간·메모리 |
| Spring WebFlux | 적은 스레드로 많은 연결 | 코드 전체가 `Mono`/`Flux` 가 된다. 디버깅·스택트레이스가 어렵다. JPA(블로킹)와 섞기 어렵다 |
| Node.js (NestJS) | 가볍고 I/O 서버에 강하다 | 싱글 스레드 이벤트 루프라 CPU 작업이 전체를 막는다. 분산 락·트랜잭션 조합의 성숙도가 Java 쪽보다 낮다 |
| Go | 빠르고 메모리가 적고 배포가 단순하다 | 락·트랜잭션·DI 같은 부품을 직접 조립해야 한다. 팀의 익숙함이 낮다 |
| Python (FastAPI) | 개발 속도 | 동시성 모델(GIL·async)과 타입 강제가 약하다. 불변식 중심 도메인에 불리하다 |

### 1-5. 한계를 알고 선택했다

| 한계 | 왜 감수하나 |
|---|---|
| **기동이 느리다** | Stage 1 은 단일 EC2 + Docker Compose **상시 실행**이다(CLAUDE.md §3). 콜드 스타트가 요청 경로에 없다. Lambda 는 이 이유로 기각했다(`docs/11_DECISION_LOG.md` #26 "Spring Boot 콜드스타트와 DB 커넥션 폭발 문제") |
| **메모리를 많이 쓴다** | 인스턴스가 1대라 총량이 문제되지 않는다 |
| **설정이 많다** | 대신 기본값이 대부분 쓸 만하고, 바꾼 값에는 이유를 주석으로 남긴다(`application.yaml` 의 `connection-timeout: 300`, `RedissonConfig` 의 `setNettyThreads(4)` 등) |
| **요청당 스레드 모델** | Java 21 가상 스레드로 줄일 수 있다(§2). Java 21 은 이미 쓰고 있다 |
| **테스트 기동이 느리다** | 측정값이 있다 — `MatchingApplicationTests` 가 168초 중 대부분을 컨텍스트 기동에 쓴다(CLAUDE.md §7). 그래서 평소에는 동시성 테스트만 돌린다 |

**운영 안정성과 개발 속도가 기동 시간보다 중요했다**는 것이 판단의 요지다.

### 1-6. 받기 쉬운 추가 질문

**Q. Node가 더 빠르지 않나?**

"빠르다"가 무엇이냐에 따라 다르다.

- **I/O 대기가 대부분인 가벼운 요청**을 적은 메모리로 많이 받는 것은 Node가 유리하다. 이벤트 루프가
  기다리는 동안 스레드를 놀리지 않기 때문이다.
- 이 서비스의 병목은 **언어 속도가 아니라 Redis 왕복과 락 대기**다. 한 번의 배정은 Lua 스크립트 두 번과
  락 획득·해제로 끝나고, Redis 자체가 싱글 스레드라 앱이 빨라져도 그쪽이 먼저 한계다.
- Node의 장점(기다리는 동안 스레드를 놀리지 않기)은 Java 21 가상 스레드로 **동기 코드 그대로** 얻을 수 있다(§2).
- 반대로 Java 쪽이 가진 것 — 정적 타입, 성숙한 Redisson·JPA·트랜잭션 — 은 Node에서 같은 수준으로 얻기 어렵다.

**Q. 왜 WebFlux는 안 썼나?**

- 이 앱은 JPA(블로킹)를 쓴다. WebFlux에서 블로킹 호출을 하면 이벤트 루프 스레드를 막으므로 별도 스케줄러로
  감싸야 하고, 그러면 리액티브의 이점이 줄어든다.
- 불변식 코드는 **읽고 검증하기 쉬워야** 한다. "락 → Lua 1 → 차단 검증 → Lua 2 → 락 해제" 순서가 동기 코드로는
  위에서 아래로 읽히지만, 리액티브 체인으로 쓰면 락 해제 시점과 에러 경로를 추적하기 어렵다.
- WebFlux가 크게 이기는 것은 **동시에 붙잡힌 요청이 아주 많을 때**다. 이 앱은 배정을 `@Async` 로 넘기고
  요청을 바로 돌려주므로(`MatchTrigger` 클래스 주석 "톰캣 스레드를 붙잡지 않도록") 그 상황이 아니다.
  그런 상황이 오더라도 가상 스레드라는 더 싼 선택지가 있다.

---

## 2. 가상 스레드 (Virtual Thread)

### 2-1. 무엇인가

- **Java 21 정식 기능**이다(JEP 444). Spring 기능이 아니라 **JVM 기능**이다.
- Spring Boot 3.2 이상은 `spring.threads.virtual.enabled=true` 한 줄로 켠다. 켜면 Tomcat 요청 처리,
  Boot가 자동 설정하는 `applicationTaskExecutor`(`@Async` 기본 풀), 스케줄러 등이 가상 스레드를 쓴다.
  정확한 적용 범위는 Boot 버전마다 조금씩 넓어졌으므로 쓰는 버전의 문서로 확인한다.

### 2-2. 플랫폼 스레드와 비교

| | 플랫폼 스레드 (기존) | 가상 스레드 |
|---|---|---|
| OS 스레드와의 관계 | **1:1** | **M:N** — JVM이 스케줄링한다 |
| 스택 메모리 | 스레드당 수백 KB ~ 1MB 예약 (OS·설정에 따라 다름) | 힙에 필요한 만큼만. 수 KB 수준에서 시작 |
| 만드는 비용 | 비싸다. 그래서 **풀로 제한**한다 (Tomcat 기본 최대 200) | 싸다. **요청마다 새로 만든다** |
| 블로킹 I/O를 만나면 | OS 스레드가 그대로 잠든다 | **캐리어에서 내려온다(unmount)** |
| 적정 개수 | 수백 ~ 수천 | 수십만도 가능 |

### 2-3. 어떻게 동작하나

```
캐리어 스레드 (ForkJoinPool, 기본 CPU 코어 수만큼)
 ├─ carrier-1 : [VT-17 실행 중] ──Redis 호출(블로킹)──▶ VT-17 unmount, 스택을 힙에 보관
 │              [VT-42 mount → 실행]
 ├─ carrier-2 : [VT-03 실행 중]
 ...
Redis 응답 도착 ──▶ VT-17 이 비어 있는 캐리어에 다시 mount → 이어서 실행
```

1. 가상 스레드는 소수의 **캐리어 스레드**(기본 `ForkJoinPool`, 크기 = CPU 코어 수) 위에서 돈다.
2. 블로킹 I/O(소켓 읽기, `Thread.sleep`, `LockSupport.park` 등)를 만나면 **캐리어에서 내려오고**
   스택 프레임을 힙에 보관한다. 캐리어는 곧바로 다른 가상 스레드를 태운다.
3. I/O가 끝나면 비어 있는 캐리어에 **다시 올라타(mount)** 이어서 실행한다.

그래서 기다리는 스레드가 수십만 개여도 실제 OS 스레드는 코어 수만큼만 바쁘다.

### 2-4. 왜 대단한가

원래 WebFlux·Node로만 얻던 **"기다리는 동안 스레드를 놀리지 않는"** 효과를 **동기 코드 그대로** 얻는다.
`redis.execute(...)` 를 한 줄로 부르고 결과를 받는 코드가 바뀌지 않는다. 콜백도 `Mono` 도 없다.
스택트레이스도 평범한 동기 코드처럼 나온다.

### 2-5. 함정

| 함정 | 무엇이 문제인가 | 피하는 법 |
|---|---|---|
| **pinning** — `synchronized` | `synchronized` 블록 안에서 블로킹하면 가상 스레드가 캐리어에서 못 내려오고 캐리어를 붙잡는다. 캐리어가 코어 수뿐이라 몇 개만 붙잡혀도 전체가 멈춘다 | 블로킹이 있는 임계 구역은 `ReentrantLock` 으로. **Java 24(JEP 491)에서 `synchronized` pinning이 대부분 해소**됐다 |
| **pinning** — 네이티브 호출 | JNI 등 네이티브 프레임이 스택에 있으면 역시 고정된다 | 피할 수 없으면 그 구간을 짧게 |
| **풀링하지 않는다** | 가상 스레드를 풀에 넣어 재사용하는 것은 의미가 없다. 그리고 풀 크기가 하던 **동시 실행 수 제한** 역할이 사라진다 | 제한이 필요하면 풀이 아니라 **`Semaphore`** 로 건다 |
| **하류 자원이 먼저 마른다** | 스레드가 무제한이 되면 DB 커넥션 풀·Redis 연결이 새 병목이 된다 | 커넥션 대기 타임아웃을 짧게 둔다 (이 저장소는 이미 `hikari.connection-timeout: 300`) |
| **CPU 작업은 안 빨라진다** | 가상 스레드는 **대기 시간을 겹치는 것**이지 계산을 빠르게 하는 것이 아니다 | CPU 위주 작업은 코어 수 기준 풀에 둔다 |
| **`ThreadLocal` 남용** | 가상 스레드가 수십만 개면 스레드마다 든 값도 수십만 벌이다 | 큰 객체를 `ThreadLocal` 에 캐시하지 않는다 |

### 2-6. 이 저장소는 지금

- **꺼져 있다.** `application.yaml` 에 `spring.threads.virtual.enabled` 가 없다. Java 21 toolchain 이라
  켤 수는 있다.
- 켜더라도 **`matchingExecutor` 는 그대로 플랫폼 스레드 풀이다.** `config/AsyncConfig.java` 가
  `ThreadPoolTaskExecutor` 를 직접 만들어 `@Async("matchingExecutor")` 로 지정하기 때문이다. 이 풀은
  core 4 / max 8 로 **배정 동시 실행 수를 일부러 제한**하고 있다. 가상 스레드로 바꾸면 그 제한이 사라지므로
  2-5 의 "세마포어" 항목을 같이 적용해야 한다.
- pinning 후보를 찾아보면 `synchronized` 는 `failover/FailoverRetryCoordinator.java` (실험용 패키지, 기본 꺼짐)의
  서킷 상태 전이에만 있다. 블록 안은 필드 읽기·쓰기와 로그뿐이고 Redis·DB 호출이 없다.

---

## 3. "기존 Spring Boot는 동시에 수천 명밖에 못 받나?" — 오해 바로잡기

**아니다.** 흔히 섞어 쓰는 세 숫자를 구분해야 한다.

### 3-1. 세 가지 숫자

| 개념 | 무엇이 정하나 | Tomcat 기본값 (대략) |
|---|---|---|
| 동시에 **처리 중인** 요청 수 | 요청 처리 스레드 풀 `server.tomcat.threads.max` | **200** |
| 동시에 **열려 있는 연결** 수 | NIO 커넥터 `server.tomcat.max-connections` | **8192** |
| 연결 수 상한을 넘었을 때의 **대기열** | `server.tomcat.accept-count` | **100** |

기본값은 Spring Boot 2.x~3.x 에서 널리 알려진 값이다. 버전에 따라 바뀔 수 있으므로 정확한 값은 쓰는 버전의
`ServerProperties` 로 확인한다. 이 저장소(Boot 4.1.1)는 `server.tomcat.*` 를 **하나도 설정하지 않았으므로
기본값 그대로다.**

### 3-2. 처리량은 스레드 수가 아니라 스레드 수 ÷ 처리 시간이다

```
초당 처리량 ≈ 처리 스레드 수 ÷ 요청당 처리 시간
            = 200 ÷ 0.05초 = 약 4,000 건/초   (요청당 50ms 일 때)
```

그리고 **동시 접속자 ≠ 동시 요청**이다. 매칭 대기 화면을 보고 있는 사용자는 대부분의 시간 동안
요청을 보내고 있지 않다. 요청을 보내는 순간에만 스레드를 50ms 쓰고 돌려준다. 그래서 "동시 접속자"는
수천이 아니라 훨씬 많을 수 있다.

### 3-3. 스레드 풀이 진짜 병목이 되는 경우

**요청이 스레드를 오래 붙잡고 있을 때**다 — 느린 DB 쿼리, 느린 외부 API, 긴 락 대기.
요청당 2초가 걸리면 같은 200개로 초당 100건뿐이다. **이때 가상 스레드가 효과가 크다** — 기다리는 동안
캐리어를 내려놓으므로 "처리 중 요청 수" 상한이 사실상 사라진다(대신 하류 자원이 새 상한이 된다, §2-5).

이 저장소는 이 상황을 이미 다른 방법으로 피하고 있다 — 배정을 `@Async` 로 넘기고 컨트롤러는 바로 응답한다
(`service/MatchTrigger.java`). 락 대기(`PoolLock` 최대 3초)는 요청 스레드가 아니라 `matchingExecutor` 에서 일어난다.

### 3-4. SSE에 대한 정정 — "SSE 연결 하나 = 스레드 하나"는 틀렸다

> 앞서 대화에서 "SSE 연결 하나가 스레드 하나를 잡는다"고 설명한 적이 있다. **틀린 설명이다.** 여기서 바로잡는다.

Spring MVC의 `SseEmitter` 는 **서블릿 비동기(async)** 로 동작한다.

```
1. 요청 도착 → Tomcat 처리 스레드가 컨트롤러 실행
2. 컨트롤러가 SseEmitter 를 반환 → 서블릿 async 시작
3. 처리 스레드는 즉시 풀로 돌아간다          ← 여기서 스레드를 놓는다
4. 연결은 열린 채 커넥터의 연결 슬롯만 차지한다
5. 알림이 오면 "그때" 어떤 스레드가 emitter.send() 로 쓰고 끝낸다
```

| | 유휴 SSE 연결이 쓰는 것 | 쓰지 않는 것 |
|---|---|---|
| Tomcat | **연결 슬롯 1개** (`max-connections` 에 산입) | 처리 스레드 (`threads.max`) |
| OS | 소켓 = **파일 디스크립터 1개** | — |
| JVM | emitter 객체·버퍼 약간의 힙 | 스레드 스택 |

- **스레드를 쓰는 것은 실제로 메시지를 보내는 순간뿐이다.**
- 그래서 SSE 연결 수의 상한은 `threads.max`(200)가 아니라 **`max-connections`(기본 8192)와 OS 파일 디스크립터
  한도(`ulimit -n`)** 다. 연결을 더 받으려면 이 둘을 올린다(Docker 라면 컨테이너의 `ulimits` 도).
- 하나 더 볼 것은 **async 타임아웃**이다. `SseEmitter` 에 타임아웃을 주지 않으면 MVC async 기본 타임아웃
  (`spring.mvc.async.request-timeout`, 미설정이면 컨테이너 기본값 — Tomcat 은 일반적으로 30초)이 적용되어
  연결이 주기적으로 끊긴다. 알림 서비스는 타임아웃을 명시하고 클라이언트 재연결을 전제로 설계한다.

---

## 4. 서버 프레임워크 일반 장단점

알림 서비스 한정이 아니라 일반론이다.

### Spring Boot (MVC)

| 장점 | 단점 |
|---|---|
| 생태계가 가장 넓다 (DB·Redis·락·메시징·보안·관측) | 기동이 느리고 메모리를 많이 쓴다 |
| 정적 타입 + DI 로 큰 코드베이스가 버틴다 | 설정·애노테이션이 많아 "왜 이렇게 동작하나"가 숨는다 |
| 동기 코드라 읽고 디버깅하기 쉽다 | 요청당 스레드 모델 (가상 스레드로 완화) |
| 선언형 트랜잭션, 성숙한 테스트 지원 | 서버리스·콜드 스타트 환경에 불리하다 |

### Spring WebFlux

| 장점 | 단점 |
|---|---|
| 적은 스레드로 아주 많은 동시 연결 | `Mono`/`Flux` 가 코드 전체로 번진다 |
| 백프레셔(backpressure) 지원 | 스택트레이스·디버깅이 어렵다 |
| 스트리밍·게이트웨이류에 강하다 | 블로킹 라이브러리(JPA 등)와 섞기 어렵다 |
| Spring 생태계를 일부 공유한다 | 가상 스레드 이후 "굳이 써야 할" 영역이 좁아졌다 |

### Node.js (Express / NestJS)

| 장점 | 단점 |
|---|---|
| 가볍고 기동이 빠르다 | 싱글 스레드 이벤트 루프 — CPU 작업이 전체를 막는다 |
| I/O 위주 서버(실시간·프록시·BFF)에 강하다 | 타입은 TypeScript 로 보완하지만 런타임 강제는 없다 |
| 프런트와 같은 언어 | 패키지 생태계 품질 편차가 크다 |
| NestJS 는 Spring 과 비슷한 DI·모듈 구조 | 대형 도메인·트랜잭션 도구의 성숙도가 Java 보다 낮다 |

### Go (Gin / Echo)

| 장점 | 단점 |
|---|---|
| 빠르고 메모리가 적다 | 프레임워크가 얇아 많은 것을 직접 조립한다 |
| 고루틴 — 가상 스레드와 같은 발상을 처음부터 가졌다 | 표현력이 낮아 도메인 로직이 장황해진다 |
| 단일 바이너리 배포, 기동이 즉시 | ORM·DI 등은 커뮤니티 선택지가 갈린다 |
| 단순함 — 읽는 사람이 헷갈릴 여지가 적다 | 에러 처리가 반복적이다 |

### Python (Django / FastAPI)

| 장점 | 단점 |
|---|---|
| 개발 속도가 가장 빠르다 | GIL 로 CPU 병렬성이 제한된다 |
| Django 는 관리자·ORM·인증을 다 준다 | 성능이 상대적으로 낮다 |
| FastAPI 는 async + 타입 힌트 + 자동 문서 | 타입 힌트는 강제가 아니다 |
| 데이터·ML 생태계와 붙이기 쉽다 | 동시성 모델(동기·async 혼용)이 헷갈리기 쉽다 |

### 한 줄 정리

**Spring 은 무겁지만 크고 오래 가는 시스템에 안전하고, Node 는 가볍고 빠른 I/O 서버, Go 는 성능과 단순함,
Python 은 개발 속도다.**

---

## 5. 알림 서비스(notification)의 스택

### 5-1. 무엇을 하는 서비스인가

matching 이 Redis Pub/Sub 채널 `qm:pubsub:push:{userId}` 에 발행한 알림을 받아 브라우저로 흘려보낸다.
matching 쪽 발행은 이미 구현돼 있다(`notification/PushPublisher.java`, 이벤트 5종 `notification/PushEventType.java`).

```
matching ──PUBLISH──▶ Redis Pub/Sub (qm:pubsub:push:{userId}) ──구독──▶ notification ──SSE──▶ 브라우저
```

| 항목 | 값 |
|---|---|
| 이름 | **notification** (예전 문서의 `app:realtime`) |
| 저장소 | matching 과 **같은 GitHub 저장소의 `notification` 브랜치** — 이력을 공유하지 않는 별도 브랜치 (`git merge-base main notification` 결과 없음) |
| 로컬 위치 | `queuemate/notification/` 에 `git worktree` 로 둔다 (`queuemate/matching/` 은 `main`) |
| 현재 상태 | 설계 메모(README)뿐, 코드 없음 |

### 5-2. 결정 — Spring Boot(MVC) + SSE + Redis Pub/Sub

| 이유 | 설명 |
|---|---|
| **matching 과 같은 스택** | 빌드(Gradle)·설정(`application.yaml`)·배포(Docker 이미지)·운영(Actuator·로그) 지식이 한 벌이다. Redis 연결 설정도 같은 모양이다 |
| **`SseEmitter` 는 비동기다** | §3-4 — 유휴 연결은 스레드를 잡지 않고 연결 슬롯만 쓴다. "MVC 라서 연결마다 스레드가 필요하다"는 걱정이 성립하지 않는다 |
| **보내기 쪽도 가볍게 할 수 있다** | 보내는 순간에만 스레드를 쓰고, 느린 클라이언트가 리스너를 막지 않게 별도 풀로 넘긴다(notification README 설계 메모). 필요하면 그 풀을 가상 스레드로 바꿔 부담을 더 줄인다 |
| **WebFlux 가 필요 없다** | WebFlux 의 이점(적은 스레드로 많은 연결)을 서블릿 async 가 이미 준다 |

### 5-3. 브라우저 전송 방식 비교

| 방식 | 방향 | 탭을 닫아도 오나 | 장점 | 단점 | 판단 |
|---|---|---|---|---|---|
| **SSE** | 서버 → 클라 | 아니다 | HTTP 그대로, 자동 재연결, 프록시 친화적, 구현이 가장 단순 | 단방향. `EventSource` 는 헤더를 못 붙인다(인증 방식 결정 필요) | **채택** |
| WebSocket | 양방향 | 아니다 | 양방향·저지연 | 알림은 단방향이라 과하다. 프록시·LB 설정이 더 까다롭다 | 기각 (docs/11 #7 개정: WebSocket 은 WebRTC 시그널링 전용) |
| Long polling | 서버 → 클라 (흉내) | 아니다 | 어디서나 동작 | 메시지마다 요청을 다시 연다. 지연·오버헤드가 크다 | 기각 |
| **Web Push** | 서버 → 브라우저 푸시 서비스 → 클라 | **온다** (서비스 워커) | 페이지를 나간 사람에게도 "제안이 떴다"를 알릴 수 있다 | 알림 권한 요청, VAPID 키 관리, 서비스 워커, 브라우저별 제약(iOS 는 홈 화면에 추가한 웹 앱에서만 되는 등) | **나중에 보완으로** |
| FCM / APNs | 모바일 푸시 | 온다 | 네이티브 앱의 표준 | 네이티브 앱이 없다 | 해당 없음 |

**Web Push 의 가치는 분명하다.** 제안 수락 시한이 20초(`queuemate.proposal.ttl-seconds` 기본값)라,
다른 탭을 보고 있던 사람이 제안을 놓치면 그대로 만료된다. 다만 권한·키·서비스 워커가 붙으므로
SSE 로 기본 경로를 먼저 세우고 그 뒤에 붙인다.

### 5-4. 메시지 출처 비교 (matching → notification)

| 후보 | 특징 | 판단 |
|---|---|---|
| **Redis Pub/Sub** | 이미 쓰는 Redis. 모든 구독자에게 뿌리므로 인스턴스가 늘어도 sticky session 이 필요 없다. 구독자가 없으면 **메시지를 버린다** | **채택.** 놓친 알림은 재전송하지 않고, 다시 들어온 사용자가 상태 조회(`GET /api/v1/match-requests?userId=`)로 따라잡는다 (notification README) |
| Redis Streams | 메시지가 남아 재전송이 가능하다 | 재전송을 하지 않기로 했으므로 이점이 없다. 컨슈머 그룹의 순서 문제도 기록돼 있다 (`docs/11_DECISION_LOG.md` #26 "Redis Streams: 컨슈머 그룹 사용 시 순서를 보장하지 않는다 (#21)") |
| Kafka | 대용량 이벤트 스트림 | **금지** (CLAUDE.md §3 "Kafka/RabbitMQ 추가 금지") |
| SQS (FIFO) | 앱 간 도메인 이벤트용으로 이미 정해져 있다 | **팬아웃에 안 맞는다.** 메시지 하나를 컨슈머 하나가 가져가는 큐라, "그 사용자의 연결을 들고 있는 인스턴스"에 골라 보낼 수 없다. 알림은 지연에도 민감하다 |

---

## 부록 — 이 문서의 사실 출처

| 주장 | 확인 방법 |
|---|---|
| Spring Boot 4.1.1, Java 21, 의존성 목록 | `backend/build.gradle` |
| 가상 스레드 꺼짐, Tomcat 설정 없음, Hikari 300ms | `backend/src/main/resources/application.yaml` |
| `@Async` 1곳, `matchingExecutor` 4/8/200 | `service/MatchTrigger.java`, `config/AsyncConfig.java` |
| `@Scheduled` 1곳, `@EnableScheduling` | `service/ProposalSweeper.java`, `MatchingApplication.java` |
| `CandidateRule` 구현체 3개 | `rule/{lol,valorant,pubg}/*CandidateRule.java` |
| Redisson 코어 사용, 락 전용 | `config/redis/RedissonConfig.java`, `redisLock/PoolLock.java` |
| `@Transactional` 0건, `synchronized` 는 failover 패키지뿐 | `grep -rn` |
| "Backend는 Java Spring Boot" 한 줄 결정 | `docs/11_DECISION_LOG.md:24` |
| notification 브랜치·worktree | `git branch -a`, `git worktree list`, `notification` 브랜치 `README.md` |

일반 기술 사실(가상 스레드 동작, JEP 번호, Tomcat 기본값, SSE async 동작, 프레임워크 장단점)은 이 저장소로
확인할 수 없는 널리 알려진 내용이다. 버전에 따라 달라질 수 있는 값은 본문에 그렇게 적었다.
