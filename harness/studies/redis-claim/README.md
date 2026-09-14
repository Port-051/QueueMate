# Lua 선점과 Redis 분산 락 비교

전환 이전 제품의 `atomic-proposal-claim.lua`와, 참가자별 Redis 분산 락을 획득한 뒤 Java에서
검증하는 실험 구현을 같은 Redis에서 비교한다. 하네스 실행은 제품의 선점 구현을 변경하지 않는다.
기본 백엔드 테스트에는 들어가지 않는 명시적 실행용 하네스다.
실행 결과와 채택 판단은 [RESULTS.md](RESULTS.md)에 정리했다.

## 실행

Java 21과 Docker가 필요하다. 저장소 루트에서:

```bash
cd backend
./gradlew -I ../harness/studies/redis-claim/init.gradle redisClaimStudy bootJar
```

Java 설치가 자동으로 탐지되지 않는 macOS에서는 설치된 JDK의 `JAVA_HOME`을 지정한다.
출력 기본값은 `/private/tmp/queuemate-redis-claim`이며
`-Dstudy.output=/절대/경로`로 바꿀 수 있다.
`results.json`, `environment.json`, `correctness.json`, 회차별 개별 요청 표본,
워커 오류 로그와 JUnit XML/HTML 보고서를 생성한다. Docker 이미지는 `redis:7-alpine`이며
실제 실행된 Redis 버전은 환경 파일에 기록한다. Testcontainers가 만든 독립 Redis만 비운다.

## 비교 대상

### Lua

전환 이전 제품 리소스의 사본을 `resources/study-baseline`에 보존하고 `SCRIPT LOAD`한 뒤
`EVALSHA`로 호출한다. 내용은 결과 보고서의 SHA-256과 같으며 인위적인 반복 계산은 없다.
전환 이전 Repository와 동일하게 참가자별 활성 요청·제안
키 및 조건별 큐 키를 전달한다. Java 입력 검증·Spring Repository 호출 비용은 비교 대상에서 제외한다.

### 분산 락 + Java

`ClaimAlgorithms.locked()`의 실험 구현이다. 이후 채택한 Spring 저장소나 Redisson,
Redlock의 성능 측정이 아니다. 과거 측정값을 이후 제품 구현의 실측값으로 해석하지 않는다.

1. 참가자별 mutex 키를 정렬해 `SET NX PX`로 획득한다. 임대 시간은 10초다.
2. 하나라도 획득하지 못하면 획득한 mutex만 해제하고 실패한다. 대기·재시도는 하지 않는다.
3. mutex·활성 제안·활성 요청을 `WATCH`하고, `MGET` 결과를 Java에서 검증한다.
4. `MULTI/EXEC`으로 선점 기록·참가자 집합·큐 제거를 적용한다. 쓰기 명령은 파이프라인으로 보낸다.
5. 자기 토큰과 일치하는 mutex만 해제한다. 해제에는 짧은 compare-and-delete Lua를 사용한다.

mutex 유효시간과 제안 유효시간은 다르다. mutex는 선점 처리 동안만 보유하고, 제안 기록은
성공 후에도 남는다. 검증 중 임대가 끝나거나 요청이 취소·교체되는 경우를 막기 위해 WATCH가
필요하다. EXEC은 중간 결과 노출과 EXEC 이전 연결 종료의 부분 쓰기를 막지만, 실행 중 오류를
자동 롤백하거나 Sentinel 전환 중 기록 유실을 막아 주지는 않는다.

즉 비교 대상은 단순 `GET → SET`으로 기능을 줄인 구현이 아니라, 기존 전원 선점 동작을
유지하려고 추가 보호를 갖춘 분산 락 구현이다. WATCH만 쓰는 낙관적 동시성 제어, Redisson,
전역 락, DB에만 선점을 기록하는 구조는 이 실험에서 비교하지 않는다.

## 부하 조건

- 실제 JVM 프로세스 4개, 각 8개 스레드와 전용 Lettuce 연결: 최대 32개 동시 선점 호출.
- JVM 사이에 Java mutex를 공유하지 않는다. 양쪽 전략 모두 같은 Lettuce 클라이언트를 사용한다.
- 두 방식 각각 5인·비경합 2,000건으로 예열한다.
- 파티 크기 2명·5명, 비경합·경합 각각 3회 비교한다. 실행 순서는 회차별로 바꾼다.
- 회차별 JVM당 1,000건, 총 4,000건이다. 본 측정은 총 96,000건이다.
- 비경합은 모든 참가자가 서로 다르다. 경합은 같은 번호의 요청이 JVM 4개에서 한 명을 공유하며,
  나머지 참가자는 서로 다르다. 따라서 성공 수는 각각 4,000건·1,000건이어야 한다.
- 테스트 전 참가자와 큐를 준비하고, 종료 후 결과를 검증한다. 준비·검증 시간은 성능 측정에서 뺀다.
- 두 방식 모두 제안 TTL 120초를 사용해 검증 도중 만료되지 않게 한다. 운영 TTL을 비교하는 실험은 아니다.
- 영속 워커와 연결은 예열 뒤 재사용한다. 별도 연결에서 약 2ms 간격으로 PING한다.
- 재시도 없이 각 시도의 성공·충돌을 기록한다. 실패를 성공 처리량으로 계산하지 않는다.

## 지표

- `allLatencyMs`, `successLatencyMs`, `conflictLatencyMs`: 개별 선점 함수 호출부터 반환까지.
  워커 실행 대기·후보 생성·HTTP·DB·사용자 수락 시간을 포함하지 않는다.
- `attemptsPerSecond`, `successesPerSecond`: 부모가 워커들에게 실행을 지시한 시점부터
  모든 결과를 받을 때까지의 처리량. IPC와 결과 직렬화 비용이 포함된다.
- `unrelatedPingMs`: 별도 연결의 PING 지연. 표본 수를 함께 기록하며 짧은 구간의 p99를
  운영 지연이나 Redis 내부 블로킹 시간으로 해석하지 않는다.
- `redisCpuSeconds`, `redisCpuMicrosPerAttempt`: INFO CPU의 시스템·사용자 CPU 차이.
  PING 관측 부하도 포함한다. `redisCpuPercentOfOneCore`는 측정 시간으로 나눈 값이며,
  전략 간 실행 시간이 다르므로 요청당 CPU 비용도 함께 본다.
- `commandstats`: 해당 구간의 명령별 실행 횟수와 서버 실행시간. `EVALSHA` 통계는 Lua 방식에서는
  선점 스크립트, 락 방식에서는 해제 스크립트를 뜻한다. 내부 명령과 스크립트 시간을 중복 합산하지 않는다.
- `slowEvalOver1msCount`, `slowEvalMaxMicros`: 1ms 이상 SLOWLOG에 남은 EVALSHA.
  전체 로그 보관 한도는 1,024개다. 0이면 관측된 1ms 이상 실행이 없다는 뜻이며 실행 비용이 0인 것은 아니다.

## 정합성 확인

모든 측정 회차에서 기대 성공 수, 사용자 중복 선점, 승자 ID, 정확한 참가자 집합, 실패한 제안의
부분 쓰기, 승자만 큐에서 제거되는지, 양수 TTL, 남은 mutex가 없는지를 검증한다.
별도 검증은 이미 선점된 참가자·변경된 요청, 부분 락 획득 실패, 검증 후 임대 만료,
검증 후 요청 교체, EXEC 이전 예외, EXEC 이전 연결 종료와 임대 만료를 다룬다.
검증 후 임대 만료는 `PEXPIRE 0`으로 재현하는 의도적 장애 주입이며 성능 측정에 섞지 않는다.

## 해석 범위

로컬 Redis 하나의 선점 단계 비교다. 전체 모집 API, 후보 조회 Lua, PostgreSQL 모드 잠금,
예약 제안, 이벤트 전달, Sentinel 장애 전환은 측정하지 않는다. 제품 전환을 결정하려면 해당
경로도 검증해야 한다. 실제 Redis/DB 장애나 잘못된 키 타입에 대한 완전한 복구 구현도 아니다.

Lua가 느리다고 결론을 미리 정하지 않는다. 변경 후 지연·CPU 비용이 늘었다면 그 결과를
그대로 남기고, 유지보수성을 위해 비용을 수용할 것인지 별도로 판단한다.

기술 근거:

- [Redis Lua 실행과 블로킹](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- [분산 락의 임대와 소유권 확인](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
- [WATCH, 만료, MULTI/EXEC 및 롤백의 한계](https://redis.io/docs/latest/develop/using-commands/transactions/)
