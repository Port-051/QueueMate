# 실시간 모집의 모드별 잠금 경합 실험

실제 REST `/api/v1/recruitments`, 매칭 트리거, PostgreSQL, Redis를 사용한다.
`ApiContractTestSupport`의 별도 Testcontainers를 사용하며 운영 DB에 접속하지 않는다.
제품 코드와 잠금 범위는 바꾸지 않는다. 기본 `test`에는 포함되지 않는 명시적 실행용 실험이다.

## 실행

Java 21과 Docker가 필요하다. 저장소 루트에서:

```bash
cd backend
./gradlew -I ../harness/studies/matching-contention/init.gradle matchingContentionStudy
```

기본 결과 위치는 `/private/tmp/queuemate-matching-contention`이다.
`-Dstudy.output=/원하는/절대/경로`로 바꿀 수 있다.
`results.json`, `environment.json`, `report/index.html`, JUnit XML을 생성한다.
실행 결과 예시는 [RESULTS.md](RESULTS.md)에 정리했다.

## 비교 조건

- 발로란트 `COMPETITIVE` 100명과 `COMPETITIVE`·`UNRATED` 각각 50명을 비교한다.
- 두 모드 모두 기존 서버 설정의 5인 파티, 역할 중복 허용을 사용한다.
- 모든 요청은 같은 역할·음성·목적, 제한 없는 모집 선호, 자동매칭 사용이다.
- 각 모드 구성으로 20명 예열 후 100명 요청을 3회씩 보낸다. 실행 순서는 번갈아 바꾼다.
- 클라이언트 최대 동시 요청은 32개다. 사용자 생성과 토큰 발급은 측정 전에 끝낸다.
- 이벤트 트리거는 켠다. 주기 재탐색·만료·복구는 긴 주기로 설정해 정상 이벤트 경로를 관찰한다.
- DB와 Redis는 실행마다 비운다. 이전 매칭 작업이 끝난 것을 확인한 뒤 다음 실행을 시작한다.
- 중복 참가자와 5인 미만·초과 제안을 검사하고, 아직 대기 중인 사용자도 결과에 남긴다.

## 관측값 해석

- `registration`: 클라이언트의 HTTP 호출 시작부터 응답까지. 클라이언트 실행 대기열 시간은 제외한다.
- `proposalObservedAfterRequestStart`: 요청 시작부터 DB의 `PROPOSED` 상태를 처음 관측할 때까지.
  WebSocket 전달 시간이나 사용자 수락 시간은 포함하지 않는다. 관측 주기만큼 오차가 있다.
- `registrationLockCall`, `matcherLockCall`: 실제 `BoardStore.lock()` 호출 소요 시간.
  순수 PostgreSQL 잠금 대기 시간만을 뜻하지 않으며 SQL 실행 비용도 포함한다.
- 별도 DB 연결에서 약 10ms 간격으로 미획득 advisory lock 개수와 제안 상태를 관측한다.
  `pgAdvisoryWaiterSamples / monitorSamples`는 표본별 평균 대기 작업 수다.
- `hikariPendingMax`: 애플리케이션 DB 커넥션 풀을 기다리는 스레드 수의 관측 최대값.
  모드 잠금과 커넥션 풀 경합을 구별하기 위해 함께 기록한다.

## 등록이 매칭 잠금을 기다리는지 확인

비교 측정이 끝난 후, 매칭 스레드가 실제 모드 잠금을 획득한 직후 테스트 게이트로 잠시 멈춘다.
같은 모드와 다른 모드에 등록 요청을 보내 다음을 검증한다.

1. 다른 모드 등록은 잠금 해제 전에 성공한다.
2. 같은 모드 등록은 아직 완료되지 않고 PostgreSQL advisory lock 대기가 관측된다.
3. 매칭 스레드를 풀어주면 같은 모드 등록도 성공한다.

이 실험의 게이트 대기는 의도적으로 만든 것이며 정상 처리 지연의 벤치마크 수치가 아니다.
한 JVM·로컬 Docker의 소규모 반복 측정이므로 운영 처리량이나 틱 방식 대비 개선율을 주장하지 않는다.
잠금 제거의 안전성, 다중 서버 동작, 장애 복구는 별도 실험이 필요하다.
