# 앱을 Sentinel 에 붙이는 설정 스니펫

**이 폴더의 규칙상 기존 소스는 건드리지 않았다.** 여기 있는 것은 전부
"이렇게 바꾸면 된다" 는 예시다. 적용 여부와 값은 사용자가 정한다.

적용 전에 알아야 할 것: 이 앱은 Redis 클라이언트가 **둘**이고 붙는 방식이 다르다.

| 클라이언트 | 담당 | Sentinel 로 바꾸는 법 |
|---|---|---|
| Lettuce (`StringRedisTemplate`) | 데이터 전부 (파티/색인/active-request/Lua/pub-sub) | **설정만.** §1 |
| Redisson (`RLock`) | `qm:lock:pool:*` 만 | **자바 코드를 고쳐야 한다.** §2 |

한쪽만 바꾸면 `EXPERIMENTS.md` 실험 2 의 "판 B" 가 된다 — 겉보기는 멀쩡한데
매칭이 하나도 안 되는 상태다.

---

## 1. Lettuce — `application.yaml` 또는 환경 변수

### 1-1. 최소 변경 (환경 변수만)

기존 파일을 안 고치고 실험할 수 있다. IDE 의 Run Configuration 이나 셸에서 준다.

```bash
export SPRING_DATA_REDIS_SENTINEL_MASTER=mymaster
export SPRING_DATA_REDIS_SENTINEL_NODES=${HOST_IP}:26379,${HOST_IP}:26380,${HOST_IP}:26381

# RedissonConfig 가 @Value 로 필수로 읽으므로(RedissonConfig.java:29-30)
# 남겨 둬야 한다. 안 그러면 컨텍스트 로딩부터 실패한다.
export REDIS_HOST=${HOST_IP}
export REDIS_PORT=6379
```

`spring.data.redis.sentinel.nodes` 가 있으면 Spring Boot 는 `host`/`port` 대신
Sentinel 구성으로 `LettuceConnectionFactory` 를 만든다. 코드 변경이 없다.

### 1-2. `application.yaml` 판

```yaml
spring:
  data:
    redis:
      # 단일 노드 설정은 남겨 둔다 - RedissonConfig 가 이 둘을 읽는다.
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      timeout: 2s

      # sentinel 블록이 있으면 Lettuce 는 이쪽을 쓴다.
      sentinel:
        master: ${REDIS_SENTINEL_MASTER:mymaster}
        nodes: ${REDIS_SENTINEL_NODES:}
```

> **정정 (2026-09-09, 실행해 보고 알았다)**: 위 주석의 "비워 두면 단일 노드로
> 되돌아간다" 는 **틀렸다.** Spring Boot 4.1.1 의
> `DataRedisConnectionConfiguration#determineMode()` 는 `getSentinelConfig() != null`
> 만 보고 Sentinel 모드를 확정한다 — **`nodes` 가 비었는지는 보지 않는다.**
> 그래서 이 블록을 `application.yaml` 에 두면 값을 비워도 앱이 아예 뜨지 않는다.
>
> ```
> Cannot build a RedisURI. One of the following must be provided Host, Socket or Sentinel
> ```
>
> **해결**: 이 블록을 `application-sentinel.yaml` 프로파일로 분리하고
> `--spring.profiles.active=sentinel` 로 켠다. 분리한 뒤 프로파일 없이 띄우면
> 오류가 `Connection refused: localhost/127.0.0.1:6379` 로 바뀐다 —
> 단일 노드로 정상 동작한다는 뜻이다. 판 A/B/C 전환 명령은 `README.md` §6 에 있다.

### 1-3. 복구 시간을 정하는 클라이언트 옵션 (실험 2 축 1)

**Sentinel 구성에는 "주기적 토폴로지 refresh" 설정이 없다.**
`spring.data.redis.lettuce.cluster.refresh.*` 는 이름 그대로 **Cluster 전용**이고
Sentinel 에는 아무 효과도 없다. Lettuce 는 Sentinel 의 pub/sub(`+switch-master`)
을 구독해서 이벤트로 갱신한다.

그래서 실제 복구 시간을 정하는 것은 아래 세 가지다.
`@Bean` 하나를 새로 만들어 준다 (기존 `RedisConfig.java` 는 Lua 스크립트 빈만
갖고 있으므로 커넥션 팩토리를 정의하지 않는다 — 이 빈을 추가하면 자동 설정을 덮는다).

```java
// 예시. redis-ha-lab 밖에 두려면 config 패키지에 새 클래스로 만든다.
@Bean
public LettuceClientConfigurationBuilderCustomizer lettuceFailoverCustomizer() {
    return builder -> builder.clientOptions(ClientOptions.builder()
            // (1) 커맨드 타임아웃을 실제로 걸리게 한다.
            //     이걸 안 켜면 죽은 연결에 보낸 명령이 TCP 타임아웃까지 매달린다.
            //     application.yaml 의 timeout: 2s 가 그냥 장식이 된다.
            .timeoutOptions(TimeoutOptions.enabled())

            // (2) 끊긴 동안 명령을 어떻게 할지.
            //     기본값은 큐에 쌓는 것이다 - 재연결 뒤 한꺼번에 터지고,
            //     그동안 요청 스레드가 묶인다.
            //     REJECT_COMMANDS 면 즉시 예외 -> INV-10 대로 503 이 나간다.
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)

            // (3) 새 master 로 붙는 시도 자체의 상한.
            //     길면 다음 Sentinel 로 넘어가는 것이 늦어진다.
            .socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(500))
                    .keepAlive(true)
                    .build())
            .build());
}
```

`import` 는 `io.lettuce.core.ClientOptions`, `io.lettuce.core.SocketOptions`,
`io.lettuce.core.TimeoutOptions`,
`org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer` 다.

> **확인 완료 (2026-09-09)**: 위 스니펫은 **Spring Boot 3.x 기준이고 4.1.1 에서는
> 그대로 쓰면 안 된다.** `spring-boot-data-redis-4.1.1.jar` 를 열어 확인한 사실 셋.
>
> 1. 패키지가 `org.springframework.boot.data.redis.autoconfigure` 로 옮겨졌다.
>    `org.springframework.boot.autoconfigure.data.redis...` 경로는 **없다.**
> 2. 같은 패키지에 **`LettuceClientOptionsBuilderCustomizer`** 가 새로 생겼다.
>    `LettuceConnectionConfiguration#getLettuceClientConfiguration()` 은
>    `builder.clientOptions(...)` 를 **먼저** 하고
>    `LettuceClientConfigurationBuilderCustomizer` 를 **나중에** 실행하므로,
>    옛 훅에서 `clientOptions(...)` 를 부르면 Boot 가 만든 옵션을 통째로 덮어쓴다.
>    새 훅은 Boot 가 쓰는 빌더를 그대로 받아 마지막에 손대므로 안전하다.
> 3. **`TimeoutOptions.enabled()` 는 Boot 4.1.1 이 이미 기본으로 넣는다**
>    (`createClientOptions()` 바이트코드로 확인). 위 (1)번 항목은 4.x 에서는
>    켜도 달라지는 것이 없다 — **실험 2 축 1 에서 그 항목의 효과를 기대하지 마라.**
>    실제로 남는 효과는 (2) `DisconnectedBehavior` 와 (3) `connectTimeout` 이다.
>
> 4.x 로 옮긴 실제 코드는
> `backend/src/main/java/com/queuemate/matching/failover/LettuceFailoverOptionsConfig.java`
> 에 있다 (`queuemate.failover.lettuce.enabled` 로 켠다).
> 그 파일과 `docs/failover-retry-guide.md` §7 을 보라.
>
> **그 코드는 위 (1) `timeoutOptions` 를 아예 넣지 않는다.** Boot 4.1.1 이 같은 값을 이미
> 넣으므로 두 번 쓰는 줄일 뿐이라 지웠다. 실제로 손대는 것은 (2)와 (3) 둘뿐이다.

---

## 2. Redisson — 자바 코드를 고쳐야 한다

`RedissonConfig.java:42` 가 `config.useSingleServer()` 다.
**설정 파일로는 못 바꾼다.** 아래처럼 갈래를 하나 추가한다.

```java
@Bean(destroyMethod = "shutdown")
public RedissonClient redissonClient(
        @Value("${spring.data.redis.host}") String host,
        @Value("${spring.data.redis.port}") int port,
        // 비어 있으면 기존 단일 노드 그대로 - 실험 2 의 판 A/B 를 오갈 수 있다.
        @Value("${spring.data.redis.sentinel.master:}") String sentinelMaster,
        @Value("${spring.data.redis.sentinel.nodes:}") String sentinelNodes) {

    Config config = new Config();
    config.setNettyThreads(4);
    config.setCodec(StringCodec.INSTANCE);

    if (sentinelNodes.isBlank()) {
        // 지금까지의 동작. 페일오버를 모른다.
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)
                .setTimeout(2000);
    } else {
        SentinelServersConfig sentinel = config.useSentinelServers()
                .setMasterName(sentinelMaster)

                // 풀 크기는 단일 노드 판과 같은 근거로 줄인다 (RedissonConfig.java:45-52).
                // 다만 Sentinel 판은 master/slave 별로 풀이 따로 잡힌다.
                .setMasterConnectionMinimumIdleSize(2)
                .setMasterConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)

                // application.yaml 의 timeout: 2s 와 맞춘다 (RedissonConfig.java:54-55).
                .setTimeout(2000)

                // 페일오버 중 재시도. 이 둘의 곱이 실질 상한이다.
                //   retryAttempts x retryInterval = 3 x 500ms = 1.5s
                // PoolLock 의 WAIT_MILLIS(3000, PoolLock.java:46) 안에 들어가야 한다.
                .setRetryAttempts(3)
                .setRetryInterval(500)

                // *** 실험 2 축 2 의 대상 ***
                // Sentinel 을 얼마나 자주 확인해서 master 주소 변경을 반영할지.
                // 이 값이 Redisson 쪽 복구 시간을 지배한다.
                .setScanInterval(1000);

        // Sentinel 주소는 "redis://ip:port" 형식이다. 콤마로 나눠 넣는다.
        for (String node : sentinelNodes.split(",")) {
            sentinel.addSentinelAddress("redis://" + node.trim());
        }
    }

    return Redisson.create(config);
}
```

`import org.redisson.config.SentinelServersConfig;` 가 추가로 필요하다.

주의할 것 두 가지.

1. **락은 master 에서만 잡아야 한다.** Redisson 의 Sentinel 구성은 기본적으로
   읽기를 slave 로 보낼 수 있다. `RLock` 은 쓰기 명령이라 master 로 가지만,
   `setReadMode(ReadMode.MASTER)` 를 명시해 두면 오해를 줄인다.
2. **Sentinel 이 announce 한 주소로 붙는다.** `redis-ha-lab` 의 T-A 토폴로지는
   `${HOST_IP}` 를 announce 하므로 호스트에서 IDE 로 띄운 앱이 그대로 붙는다.
   T-B 로 띄웠는데 앱은 호스트에 있으면 **여기서 연결이 안 된다** —
   `README.md` §2 를 보라.

---

## 3. 실험 2 의 세 판을 오가는 법

위 §1-2 와 §2 를 적용하면 환경 변수만으로 판을 바꿀 수 있다.

| 판 | `REDIS_SENTINEL_NODES` | Redisson 쪽 | 결과 |
|---|---|---|---|
| A (before) | 빈 문자열 | 빈 문자열이면 단일 노드 | 페일오버 후 영원히 복구 안 됨 |
| B (절반) | `ip:26379,...` | **일부러 빈 문자열로 둔다** | claim 은 복구, 배정은 계속 실패 |
| C (after) | `ip:26379,...` | 같은 값 | 양쪽 복구 |

판 B 를 만들려면 Redisson 쪽만 별도 프로퍼티로 빼면 편하다.

```java
@Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}") String sentinelNodes
```

이러면 `QUEUEMATE_LOCK_SENTINEL_NODES=` (빈 값) 으로 Redisson 만 단일 노드로
되돌릴 수 있다.

---

## 4. 실제 `PoolLock` 으로 락 유실을 재현하는 테스트 (실험 4 절차 B)

**이 파일을 만들면 `redis-ha-lab/` 밖에 파일이 생긴다.** 붙일지는 사용자가 정한다.
붙인다면 `backend/src/test/java/com/queuemate/matching/redis/FailoverLockLossTest.java` 다.

기존 동시성 테스트와 다른 점: `ConcurrencyTestSupport` 를 상속하지 않는다.
그쪽은 DB 15 를 `flushDb` 로 비우는데(`ConcurrencyTestSupport.java:37-42`),
이 테스트는 **페일오버로 노드가 통째로 바뀌므로** 그 전제가 안 맞는다.

```java
package com.queuemate.matching.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 페일오버 중 후보 풀 락이 유실되어 상호 배제가 깨지는 것을 재현한다.
 *
 * <p><b>이 테스트는 깨지는 것을 증명하는 대조군이다.</b>
 * {@code NaiveVsLuaComparisonTest} 와 같은 자리다 — docs/CONCURRENCY_TESTS.md 의
 * "통과하는 테스트만 있으면 원래 안 깨지는 것인지 우리가 막은 것인지 구분이 안 된다"
 * 는 원칙에 따라, 막기 전에 깨지는 것을 먼저 남긴다.
 *
 * <p><b>수동 실험용이다.</b> redis-ha-lab 이 떠 있어야 하고 도중에 셸 조작이 필요하다.
 * CI 에 넣지 마라. 환경 변수 RUN_FAILOVER_LAB=1 이 없으면 건너뛴다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_FAILOVER_LAB", matches = "1")
class FailoverLockLossTest {

    /** LolPartyKeys.poolKey() 와 같은 규칙으로 만든 값이어야 한다 (LolPartyKeys.java:24-28). */
    private static final String POOL_KEY = "qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP";

    @Autowired
    PoolLock poolLock;

    @Test
    void 페일오버_뒤_같은_풀_락이_두_번_잡힌다() throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        // A: 락을 잡고 붙잡고 있는다.
        // 주의: LEASE_MILLIS 가 3000 이므로 (PoolLock.java:54) 3초가 지나면
        //       페일오버가 없어도 락이 저 혼자 풀린다. 그래서 latch 로 기다리는 시간이
        //       3초를 넘으면 이 테스트는 "페일오버 때문에 깨진 것" 을 증명하지 못한다.
        //       그 자체가 관측 대상이다 - lock-safety-analysis.md §1-2 를 보라.
        Thread a = Thread.ofVirtual().start(() ->
                poolLock.run(POOL_KEY, () -> {
                    acquired.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));

        assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

        System.out.println("=== 지금 셸에서 다음을 실행해라 ===");
        System.out.println("  redis-ha-lab/scripts/lock-loss-repro.sh 의 1,4,5 단계");
        System.out.println("  (docker pause replica -> docker kill master -> unpause)");
        System.out.println("=== 승격이 끝나면 Enter ===");
        System.in.read();

        // B: 같은 풀 락을 잡아 본다. 잡히면 상호 배제가 깨진 것이다.
        try {
            poolLock.run(POOL_KEY, () -> secondAcquired.set(true));
        } catch (RuntimeException e) {
            System.out.println("두 번째 획득 실패 (락이 살아남았다): " + e);
        }

        release.countDown();
        a.join();

        // 여기서 실패하면 좋은 소식이다 - 락이 유실되지 않았다는 뜻이다.
        assertThat(secondAcquired)
                .as("승격된 replica 에 락이 없어 B 가 같은 풀을 잡았다 = 상호 배제 깨짐")
                .isTrue();
    }
}
```

**A 스레드의 `finally` 를 반드시 같이 관찰해라.** `PoolLock.java:81` 의
`lock.isHeldByCurrentThread()` 가 어떻게 되는지가 §1-3 의 관측 대상이다.
디버거로 그 줄에 브레이크포인트를 걸거나, 로그를 추가한 사본으로 돌려 봐라.

---

## 5. 실험 중 로그 설정

`application.yaml:67-69` 의 `logging.level.com.queuemate: INFO` 를 실험 중에는
내려서 배정 경로의 `log.debug` 를 본다 (`UntieredAssigner.java:60`, `:92`, `:99`).

```bash
export LOGGING_LEVEL_COM_QUEUEMATE=DEBUG

# Redisson 이 언제 새 master 를 인지하는지 보려면 (실험 2 축 2)
export LOGGING_LEVEL_ORG_REDISSON=DEBUG

# Lettuce 가 +switch-master 를 받는 순간을 보려면
export LOGGING_LEVEL_IO_LETTUCE_CORE=DEBUG
```

단 `docs/PERFORMANCE_EVIDENCE.md` 가 로그 설정에 따른 처리량 차이를 따로 재 둔
만큼(`load-test/throughput.js`), **DEBUG 를 켠 채로 잰 다운타임 수치를
로그 INFO 판과 같은 표에 올리지 마라.**
