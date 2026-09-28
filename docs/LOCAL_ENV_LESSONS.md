> **이 파일은 옛 `room` 폴더의 `docs/NOTIFICATION_LESSONS.md` 를 2026-09-27 에 옮겨 온 것이다**(`room` 폴더를 지우면서 — 로컬 환경의 함정은 지금도 유효해서). 아래 "이 앱" · "이 폴더" 는 옛 `room` 을 가리킨다.

# `notification` 을 만들며 겪은 것 — room 에서 같은 데 걸리지 않으려고

> **2026-09-25 에 이 앱은 `platform` 에 합쳐졌다 — 이 폴더는 합치기 전의 기록이다. 방의 규칙은 `../platform/CLAUDE.md` §3.3 · `../platform/contracts/platform-api.md` "방" 이다.**
> 이 파일은 합치기 전 그대로 둔다. **다만 로컬 환경의 함정(띄우고 죽이기 · IntelliJ · worktree · 테스트)은 지금도 유효하다** — `../platform/CLAUDE.md` §9 가 이 파일을 가리킨다. 방 앱 이야기는 합치기 전의 것이다.

옆 서비스 `notification`(SSE 알림)은 같은 컴퓨터, 같은 스택(Java 21 · Spring Boot 4.1.1 · Gradle · Redis)으로 먼저 만들었다.
그때 **실제로 겪은 것만** 적는다. 출처가 코드에 남아 있는 것은 경로를 붙였다(경로는 `../notification/` 기준, 2026-09-19 · 커밋 `aebd3f5`).

---

## 1. 로컬 환경

- **WSL2(Ubuntu) + Windows 의 IntelliJ** 로 일한다. 소스는 `/mnt/c/…` 에 있다.
- WSL 은 `networkingMode=mirrored` 로 설정돼 있다(`C:\Users\kimye\.wslconfig`). 그래서 **Windows 에서 띄운 앱(IntelliJ 실행)을
  WSL 의 `localhost` 로 칠 수 있다.**
- **Docker 는 Windows 의 Docker Desktop 뿐이다.** WSL 에서는 `docker` 가 아니라 **`docker.exe`** 로 부른다.
- **포트 6379 · 5432 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 건드리지 않는다.** 그래서 앱의 기본값(`REDIS_PORT` 기본 6379)으로
  띄우면 남의 Redis 에 붙는다 — **테스트용 Redis 를 다른 포트로 따로 띄우고 앱에 그 포트를 알려 준다.**

```bash
# 테스트용 Redis (6380). --rm 이라 멈추면 컨테이너가 사라진다
docker.exe run -d --rm --name qm-room-test-redis -p 6380:6379 redis:7-alpine

# 앱은 그 포트로 (backend/ 안에서)
REDIS_PORT=6380 ./gradlew bootRun

# redis-cli 는 WSL 에 없다. 컨테이너 것을 빌려 쓴다
docker.exe exec qm-room-test-redis redis-cli PING

# 끝나면 멈춘다
docker.exe stop qm-room-test-redis
```

## 2. IntelliJ 와 같이 쓸 때

- **IntelliJ 가 열어 둔 파일을 Claude 가 고치면, IntelliJ 가 메모리의 옛 내용으로 덮어쓸 수 있다.** 실제로 한 번 일어났다.
  Claude 가 파일을 고친 뒤에는 IntelliJ 에서 **`Ctrl+Alt+Y`**(Reload All from Disk)를 누른다.
- **IntelliJ 에 git 이력 · 커밋 창이 안 보인다.** 이 폴더는 worktree 라 `.git` 이 폴더가 아니라 `gitdir: /mnt/c/…` 한 줄짜리
  **파일**이고, Windows 의 IntelliJ 는 그 WSL 경로를 못 읽는다.
  - 커밋은 WSL 터미널이나 Claude 가 한다. 이력은 `git log --oneline --graph` 로 본다.
  - **고치려 들지 마라.** 상대 경로 worktree 는 이 컴퓨터의 git 2.34 가 공식 지원하지 않는다. Windows git 과 WSL git 이 같은 저장소를
    번갈아 만지면 줄바꿈 · 권한 차이로 **파일이 전부 수정된 것으로 뜬다.**
- IntelliJ 가 새 파일을 자동으로 스테이징해 두는 일이 있다. 커밋은 경로를 명시해서 한다(`../CLAUDE.md` §8).

## 3. 띄우고 죽이기

- **`bootRun` / `java -jar` 로 띄운 앱은 반드시 종료한다.** 안 죽이면 포트가 물려 다음 검증이 실패한다. 끝나면 `./gradlew --stop` 도 한다.
- **`pkill -f <패턴>` 을 쓰지 마라.** 그 패턴을 담은 **자기 셸까지 죽인다.** 포트로 PID 를 찾아 PID 로 죽인다.

```bash
ss -ltnp | grep :8083      # PID 를 찾는다
kill <PID>
```

- **`/mnt/c` 위에서 Gradle 빌드는 느리다**(전체 1~2분). 전체 테스트를 매번 돌리지 말고 **`--tests` 로 좁힌다.**

```bash
./gradlew test --tests '*SseConnectionsTest'     # notification 에서 쓰던 형태. room 에서는 자기 테스트 이름으로
```

- **끝나지 않는 요청(SSE)을 curl 로 볼 때는 `curl -N --max-time <초>`.** `-N` 은 curl 의 출력 버퍼링을 꺼서 이벤트가 오는 대로
  보이게 하고, `--max-time` 이 없으면 curl 이 끝나지 않는다. room 이 발행한 알림이 가는지 볼 때 이 형태를 쓴다.

```bash
curl -N --max-time 30 "http://localhost:8081/api/v1/events?userId=u1"
```

> (2026-09-27 부터 `notification` 은 `?userId=` 를 보지 않고 쿠키 `qm_access` 를 검증한다 — 지금은 `--cookie "qm_access=<access 토큰>"` 을 붙여야 한다. 위 줄은 옮겨 온 당시의 것이다.)

## 4. 스프링 · 라이브러리에서 걸린 것

- **Lombok 과 Spring 에 같은 이름의 `@Value` 가 있다.** 설정값 주입은 **`org.springframework.beans.factory.annotation.Value`** 다.
  자동 import 가 `lombok.Value` 를 고르지 않았는지 본다.
- **`@RequiredArgsConstructor` 가 만든 생성자에는 `@Value` 가 안 붙는다.** 설정값을 받는 클래스는 **생성자를 직접 쓴다.**
  본보기: `backend/src/main/java/com/queuemate/notification/controller/EventStreamController.java`, `…/sse/ReconnectDelay.java`.
- **Spring Boot 4 는 Jackson 3 이다.** 패키지가 `com.fasterxml.jackson.databind` 가 아니라 **`tools.jackson.databind`** 이고,
  `JsonNode` 의 `asText` 가 아니라 **`asString`** 이다.
  (`matching` 의 `PushPublisher` 도 `tools.jackson.databind.ObjectMapper` 를 쓴다 — `MATCHING_REFERENCE.md` 3번.)
- **빈 순환 참조는 앱을 못 띄운다.** `notification` 에서는 `SseConnections → UserChannelSubscriber → PushMessageListener → SseConnections`
  가 순환이었고, `UserChannelSubscriber` 가 리스너를 직접 주입받지 않고 **`ObjectProvider`** 로 받아 끊었다. `@Lazy` 프록시가 아닌
  이유까지 그 클래스 주석에 있다 — `backend/src/main/java/com/queuemate/notification/subscription/UserChannelSubscriber.java`.

## 5. 테스트

### 서블릿 · 스프링 없이 로직을 테스트하는 법

본보기는 `backend/src/test/java/com/queuemate/notification/sse/SseConnectionsTest.java` 다.

- 대상 클래스를 **`new` 로 직접 만든다.** 스프링 컨텍스트를 띄우지 않는다. 협력 객체(`UserChannelSubscriber`)는 Mockito `mock` 으로 넣는다.
- `SseEmitter` 는 상속해서 **`send` 가 소켓에 쓰는 대신 나갈 글자를 목록에 기록하게** 만든 테스트용 클래스(`RecordingEmitter`)를 쓴다.
  예외를 던지게도 만들 수 있어 "끊긴 클라이언트"를 흉내 낸다.
- **함정 하나 — 서블릿 컨테이너가 없으면 `completeWithError` 를 불러도 `onCompletion` / `onError` 콜백이 실행되지 않는다.**
  그래서 "전송 실패 → 자동 정리"는 그 테스트에서 기대하지 않고, 콜백이 하는 일(`remove`)을 직접 불러 흉내 낸다. 테스트 클래스 주석에 적혀 있다.
- room 에 옮기면 — 서비스를 HTTP 를 모르는 평범한 클래스로 두라는 규칙(`../CLAUDE.md` §5)이 이 방식의 전제다. 다만 **Lua 는 Redis 안에서
  도는 코드라 mock 으로는 검증되지 않는다.** `matching` 의 알림 테스트(`PushNotificationTest`)도 실제 Redis 를 구독해서 확인한다
  (`CONTRACTS.md` 의 원본 `contracts/events.md` 가 그렇게 적는다).

### 테스트가 진짜 버그를 잡는지 확인하는 법 — 버그를 일부러 되살린다

테스트가 초록이라는 것만으로는 그 테스트가 **무엇을 막는지** 알 수 없다. `notification` 에서는 이렇게 확인했다.

1. 테스트를 쓰고 통과시킨다.
2. **고쳐 둔 코드를 잠깐 버그 상태로 되돌린다**(예: 조건문 한 줄을 뺀다).
3. 그 테스트가 **실패하는지** 본다. 실패하지 않으면 그 테스트는 그 버그를 못 잡는 것이다 — 테스트를 고친다.
4. **코드를 원래대로 돌려놓고** `git diff` 로 남은 것이 없는지 확인한다.

### 실제로 났던 버그 넷

전부 `SseConnectionsTest` 의 테스트 이름(`@DisplayName`)에 "있었던 버그"로 기록돼 있다. 버그가 있던 코드는 커밋된 적이 없어 git 이력에는 없다.

| # | 버그 | 증상 | 못 박아 둔 테스트 | 교훈 |
|---|---|---|---|---|
| 1 | **무조건 unsubscribe** — 연결을 뺄 때 남은 연결이 있는지 보지 않고 구독을 풀었다 | 탭 두 개 중 하나만 닫아도 **남은 탭이 알림을 못 받는다** | `탭_두_개_중_하나만_빠지면_구독을_풀지_않는다` | "마지막일 때만"이라는 조건은 **조건이 거짓인 경우를 테스트해야** 드러난다. 하나만 넣고 하나만 빼는 테스트는 통과한다 |
| 2 | **빈 집합이 맵에 남음** — 마지막 연결이 빠진 뒤에도 그 사용자의 빈 집합이 맵에 남았다 | 재접속이 "첫 연결"로 안 보여 **subscribe 가 다시 안 불린다.** 다시 들어온 사용자가 알림을 못 받는다 | `전부_빠진_뒤_재접속하면_다시_구독한다` | **"들어왔다 나갔다 다시 들어온다"를 테스트한다.** 한 번 들어오고 끝나는 테스트는 남은 찌꺼기를 못 본다. room 의 입장 · 나가기 · 재입장이 같은 모양이다 |
| 3 | **`asString("null")`** — `eventId` 가 없을 때의 기본값 | `eventId` 가 없거나 JSON `null` 인 봉투에서 SSE **`id:` 가 문자열 `null` 로 나간다** | `eventId_가_없어도_보내고_id_는_싣지_않는다` · `eventId_가_JSON_null_이면_id_를_싣지_않는다` | "없음"을 문자열로 표현하지 않는다. 지금 코드는 `asString(null)` 로 자바 `null` 을 받고, `null` 이면 `id:` 를 싣지 않는다 |
| 4 | **`send` 가 `if` 안에 들어감** — `eventId` 가 있을 때만 하는 일(`id` 싣기)의 `if` 블록 안에 보내는 줄까지 들어갔다 | **`eventId` 가 없는 알림이 아예 안 간다** | `eventId_가_없어도_보내고_id_는_싣지_않는다` | 선택 사항을 처리하는 `if` 와 **반드시 해야 하는 일**을 눈으로 구분한다. 중괄호 하나 차이이고 정상 입력으로는 드러나지 않는다 — **빠진 입력**으로 테스트한다 |

## 6. 코드를 읽고 한 추측을 확인 없이 사실처럼 말하지 마라

실제 사례다. `SseConnections#remove` 를 읽다가 이런 추측이 나왔다 — "Redis 가 죽어 있는 동안 마지막 연결이 닫히면 구독 해제가 실패해서
버그가 된다." 코드만 읽으면 그럴듯했다.

**Redis 를 실제로 껐다 켜며 확인하니 틀렸다**(2026-09-19). 구독 해제는 예외를 던지지 않았고, 맵에서 정상으로 빠졌고, 복구 뒤 그 사용자가
재접속하면 구독이 새로 걸렸다. 그 결과가 `backend/src/main/java/com/queuemate/notification/config/RedisConfig.java` 의 주석에 남아 있다.

- 코드를 읽고 "이럴 수 있다"고 생각한 것은 **실험으로 확인한 뒤에 말한다.** 확인하지 못했으면 "확인 안 됐다"고 밝힌다.
- **확인 안 된 것을 고치자고 하지 않는다.** 없는 버그를 고치는 코드는 진짜 버그를 만든다.
- 확인한 결과는 그 자리의 주석에 **날짜와 함께** 남긴다. 다음 사람이 같은 추측을 다시 하지 않는다.
