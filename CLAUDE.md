# CLAUDE.md — notification 서비스 규칙 (Non-Negotiable)

작업 전에 이 파일과 `README.md`를 읽어라.

이 폴더는 QueueMate의 **알림 배달 배포 단위 하나**다. 예전 문서에서 `app:realtime`이라고
부르던 것을 이제 **`notification`**이라 부른다. 매칭 엔진 규칙은 옆 폴더 `matching`의
`CLAUDE.md`에 있고, 이 파일은 그중 **알림 배달에 걸리는 부분만** 담는다.

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다. 이 서비스는 그 결과를 사용자
브라우저로 **배달만** 한다.

```
matching  ──PUBLISH──▶  Redis Pub/Sub               ──SUBSCRIBE──▶  notification  ──SSE──▶  브라우저
                        qm:pubsub:push:{userId}
```

- **매칭 로직이 하나도 없다.** 매칭·제안·수락·확정은 전부 `matching`의 일이다.
- 공개 채팅, 피드, 게시판처럼 사용자끼리 주고받는 기능을 여기 만들지 않는다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 안 한다 |
|---|---|
| 사용자의 SSE 연결을 들고 있는다 | **놓친 알림 재전송.** Pub/Sub은 구독자가 없으면 메시지를 버린다. 재접속한 사용자는 `matching`의 `GET /api/v1/match-requests?userId=`로 현재 상태를 복구한다 |
| `qm:pubsub:push:{userId}`로 들어온 메시지를 **해석하지 않고 그대로** 그 사용자 연결로 흘려보낸다 | **알림 이력 저장.** DB도, Redis List/Stream도 쓰지 않는다 |
| | **매칭 상태 읽기/쓰기.** 매칭 Redis 키(`qm:party:*`, `qm:user:*`, `qm:proposal:*`, `qm:gameconfig:*`)에 접근하지 않는다. Redis에서 만지는 것은 `qm:pubsub:push:{userId}` 구독뿐이다 |
| | **`type`별 분기·필터링.** 모르는 `type`이 와도 그대로 보낸다. 종류가 늘 때 이 서비스를 재배포하지 않기 위해서다 |

## 3. 메시지 계약

메시지는 **JSON 문자열 하나**다. 이 서비스는 열어 보지 않고 SSE `data:`에 그대로 싣는다.
자바 객체가 아니라 문자열로 주고받는 이유는 `PushPublisher.java` 클래스 주석에 있다 —
봉투 하나 고칠 때마다 두 앱을 같이 배포하지 않기 위해서다.

| 항목 | 값 | 원본 (`matching` 기준) |
|---|---|---|
| 채널 | `qm:pubsub:push:{userId}` | `redisKeys/SharedKeys.java`의 `PUSH_CHANNEL_PREFIX = "qm:pubsub:push:"` + `pushChannel(userId)` |
| 봉투 | `{type, eventId, occurredAt, payload}` 네 칸 고정 | `notification/PushPublisher.java`의 `record Envelope` |
| `type` | `PushEventType` 이름 문자열 | `notification/PushEventType.java` |
| `eventId` | 매번 새 UUID 문자열. **SSE `id:` 필드에 그대로 싣는다**(`eventId` = `Last-Event-ID`) | `PushPublisher#publish()` · `contracts/events.md` "재개" |
| `occurredAt` | ISO-8601 UTC, 밀리초 단위 (`Instant.truncatedTo(MILLIS)`) | `PushPublisher#publish()` |
| `payload` | 객체. 담을 것이 없어도 `null`이 아니라 `{}` | `PushPublisher#publish()` |

`matching`이 발행하는 종류는 5종이다 — `MATCH_QUEUE_UPDATED`, `MATCH_PROPOSAL_CREATED`,
`MATCH_PROPOSAL_EXPIRED`, `MATCH_CONFIRMED`, `MATCH_CANCELLED`. 전체 SSE 계약은 14종이고
나머지 9종(`RESERVATION_*`, `PARTY_*`, `FRIEND_*`)은 다른 앱이 발행한다(`contracts/events.md`).

> **채널 접두사는 이 서비스가 정하지 않는다.** 원본은 `matching`의 `SharedKeys.PUSH_CHANNEL_PREFIX`다.
> 여기서 값을 따로 바꾸거나 오타를 내면 **컴파일도 테스트도 통과한 채로 알림이 전부 끊긴다**
> — 발행 쪽은 구독자 0명을 실패로 보지 않고 조용히 버리기 때문이다. 바꿔야 하면 `matching`과
> 같이 바꾼다. 이 서비스에서도 접두사는 상수 한 곳에만 둔다.

> `PushPublisher.java` 클래스 주석은 받는 쪽이 "지금은 패턴 구독(`PSUBSCRIBE qm:pubsub:push:*`)"이라고
> 적고 있다. **이 서비스의 결정은 사용자별 구독이다**(§5). 그 주석이 낡았다 — `matching` 쪽 수정은
> `matching`에서 한다.

## 4. 기술 스택 (결정됨)

| 항목 | 값 |
|---|---|
| 언어 | **Java 21** (`matching/backend/build.gradle`의 `JavaLanguageVersion.of(21)`) |
| 프레임워크 | **Spring Boot 4.1.1** (MVC, 서블릿) — `matching`과 같은 버전 |
| 빌드 | Gradle (`io.spring.dependency-management` 1.1.7) |
| SSE | `SseEmitter` |
| 구독 | Spring Data Redis `RedisMessageListenerContainer` (Pub/Sub) |

`matching`과 버전을 맞추는 이유는 한 사람이 두 서비스를 같이 다루므로 의존성·설정 감각을 한 벌로
유지하기 위해서다. WebFlux가 아니라 MVC인 이유는 서블릿 비동기(§5)로 유휴 연결 문제가 풀리기
때문이다. 상세 근거는 `matching` 저장소의 `docs/WHY_SPRING_BOOT.md`(작성 중)를 본다.

## 5. 설계 규칙

- **구독은 사용자별로 건다.** 그 사용자의 첫 연결이 생기면 `SUBSCRIBE qm:pubsub:push:{userId}`,
  마지막 연결이 끊기면 푼다. **`qm:pubsub:push:*` 패턴 구독 금지** — 사용자가 늘면 모든
  인스턴스가 모든 사용자의 메시지를 받아 버리고 로컬에서 거르는 낭비가 인스턴스 수만큼 곱해진다.
- **한 사용자가 여러 탭을 연다.** `userId → 연결 목록`으로 들고, 같은 메시지를 전부에 보낸다.
  구독은 연결 수가 아니라 사용자 단위로 하나다.
- **하트비트** — 15~30초마다 SSE 주석 줄(`:`로 시작)을 보낸다. 프록시·로드밸런서의 유휴 타임아웃
  (Stage 2 ALB 300초)보다 짧아야 한다.
- **정리** — `onCompletion` / `onTimeout` / `onError` **셋 모두**에서 연결 목록에서 빼고, 마지막
  연결이면 구독도 푼다. 하나라도 빠뜨리면 죽은 연결과 구독이 조용히 쌓인다.
- **Redis 리스너 스레드에서 바로 보내지 않는다.** 받은 메시지는 별도 스레드 풀로 넘겨 보낸다.
  느린 클라이언트 하나가 리스너를 막으면 모든 사용자의 알림이 멈춘다.
- **보내기 실패는 그 연결만 버린다.** 예외를 밖으로 올리지 않는다. 알림 하나 때문에 다른 연결·
  다른 사용자가 영향받으면 안 된다.
- **`SseEmitter`는 서블릿 비동기다 — 유휴 연결이 요청 스레드를 잡지 않는다.** 핸들러가 emitter를
  반환하는 순간 톰캣 요청 스레드는 풀로 돌아간다. 그래서 동시 연결 수의 상한은 스레드 풀이 아니라
  Tomcat `server.tomcat.max-connections`다. 연결 수를 늘리려고 스레드 풀을 키우지 마라.
- **sticky session이 필요 없다.** 어느 인스턴스에 붙든 그 인스턴스가 그 사용자 채널을 구독하고,
  Redis가 모든 구독자에게 뿌린다.

## 6. 배포 기준

현재 배포 기준은 **Stage 1(단일 EC2 + Docker Compose)**다.
**k8s / HPA / sticky session을 전제한 구현 금지.**

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라.

| 항목 | 상황 |
|---|---|
| 인증 | `EventSource`는 요청 헤더를 붙일 수 없다. 쿼리 파라미터 토큰이냐 쿠키냐 |
| 엔드포인트 경로 | `contracts/events.md`는 `GET /api/v1/events`라고 적지만 `contracts/openapi.yaml`에는 아직 없다 |
| `Last-Event-ID` 재개 | `contracts/events.md` "재개"는 유한 버퍼로 재개한다고 적는다. 이 서비스의 결정은 **재전송 없음**(§2)이다. 계약과 어긋나 있으므로 계약을 고칠지 정해야 한다 |

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `notification` 브랜치**다. 이력이 없는 별도 브랜치에서
시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       main 브랜치
└── notification/   notification 브랜치 (이 폴더)
```

커밋은 `matching`과 같은 AngularJS commit convention을 따른다. 형식: `type(scope): subject`

- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: **`notification`** (문서만 바꾸면 `docs`, 인프라 설정은 `infra`)
- subject: **한글**, 50자 이내, 끝에 마침표 없음
- body: **한글**로 무엇을/왜. 어떻게는 코드가 말한다. 3줄 이내
- type/scope 키워드만 영어를 유지한다
- footer: `BREAKING CHANGE: <설명>`, revert는 `revert: <원 subject>` + 원 commit hash

규칙:
- **커밋은 파일 경로를 명시해서 한다** (`git add <경로>` / `git commit <경로>`). IDE가 자동으로
  스테이징해 둔 엉뚱한 파일이 섞여 들어가는 일이 있다. `git add -A` / `git add .` 금지.
- 하나의 커밋은 하나의 목적만 담는다. type이 다르면 나눈다 — 의존성/설정(`build`)과 구현(`feat`)을
  섞지 않는다. 문서(`CLAUDE.md`, `README.md`) 변경과 기능 구현을 한 커밋에 섞지 않는다.
- 기능과 그 기능의 테스트는 한 커밋에 담는다.
- 각 커밋 시점에서 빌드가 통과해야 한다.

## 9. 작업 방식

작업 지시를 받으면 **기본적으로 서브 에이전트를 띄워서 처리한다.** 직접 파고들지 않는다.

- 조사, 코드 탐색, 문서 정리, 여러 파일에 걸친 변경은 전부 서브 에이전트에 맡긴다
- 서브 에이전트는 이 대화를 모른다. **필요한 맥락을 프롬프트에 다 적어 준다** —
  읽어야 할 파일, 건드리면 안 되는 파일, 지금까지 정해진 결정
- 서로 겹치지 않는 일이면 **여러 개를 한 번에 띄운다**
- 돌아온 결과는 그대로 옮기지 말고 **직접 확인한 뒤** 요약해서 보고한다

예외는 하나다 — 사용자가 **묻기만 한 것**(설명, 확인, 의견)은 서브 에이전트 없이 바로 답한다.

운영 규칙:
- **포트 6379와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 절대 건드리지 마라.**
  테스트용 Redis는 다른 포트로 따로 띄우고, 끝나면 종료한다.
- `bootRun`으로 앱을 띄웠으면 **반드시 종료해라.** 안 죽이면 포트가 물려 다음 검증이 실패한다.
  끝나면 `./gradlew --stop`도 한다.
- **옆 폴더 `matching`의 파일은 여기서 고치지 않는다.** 읽기만 한다. 고칠 것이 있으면 그 폴더에서
  따로 작업한다.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 매칭 엔진 규칙 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 계약 (봉투·재개·하트비트·순서) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/events.md` |
| 봉투를 만드는 코드 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` |
| 알림 종류 5종 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushEventType.java` |
| 채널 접두사 원본 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` (`PUSH_CHANNEL_PREFIX`) |

## 11. 이 저장소에서 하지 말 것 (요약)

- 메시지 `type`/`payload` 해석, 종류별 분기 — 그대로 흘려보낸다
- 놓친 알림 재전송, 알림 이력 저장 — 복구는 `matching` 상태 조회가 맡는다
- 매칭 Redis 키(`qm:party:*`, `qm:user:*`, `qm:proposal:*`, `qm:gameconfig:*`) 접근
- `qm:pubsub:push:*` 패턴 구독 — 사용자별 구독만 쓴다
- 채널 접두사를 `matching`과 따로 바꾸기
- Redis 리스너 스레드에서 직접 전송
- k8s/HPA/sticky session 전제 구현
- 포트 6379 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`
