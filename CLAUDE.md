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
  파티 모집 게시판은 제품에 들어오지만(`matching` docs/11 D-11) `platform`의 일이고, 이 서비스는
  그 알림을 다른 알림처럼 흘려보낼 뿐이다. 게시판 목록을 F5 없이 갱신하려고 **게시판 채널 구독**이 생겼지만
  (D-20 · D-22. **구현됐다** — §7.1) 그것도 받은 것을 그대로 — **살아 있는 모든 연결에** — 흘려보내는 일이다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 안 한다 |
|---|---|
| 사용자의 SSE 연결을 들고 있는다 | **놓친 알림 재전송.** Pub/Sub은 구독자가 없으면 메시지를 버린다. 재접속한 사용자는 `matching`의 `GET /api/v1/match-requests?userId=`로 현재 상태를 복구한다 |
| `qm:pubsub:push:{userId}`로 들어온 메시지를 **해석하지 않고 그대로** 그 사용자 연결로 흘려보낸다 | **알림 이력 저장.** DB도, Redis List/Stream도 쓰지 않는다 |
| | **매칭 상태 읽기/쓰기.** 매칭 Redis 키(`qm:party:*`, `qm:user:*`, `qm:proposal:*`, `qm:gameconfig:*`)에 접근하지 않는다. Redis에서 만지는 것은 `qm:pubsub:push:{userId}` 구독과 게시판 채널 `qm:pubsub:board` 하나의 구독뿐이다(§7.1) |
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
| `eventId` | 매번 새 UUID 문자열. **SSE `id:` 필드에 그대로 싣는다.** 클라이언트가 중복을 거르는 데 쓴다 — `Last-Event-ID` 로 이어 보내지는 **않는다**(`contracts/events.md` "재연결") | `PushPublisher#publish()` · `contracts/events.md` "재연결" |
| `occurredAt` | ISO-8601 UTC, 밀리초 단위 (`Instant.truncatedTo(MILLIS)`) | `PushPublisher#publish()` |
| `payload` | 객체. 담을 것이 없어도 `null`이 아니라 `{}` | `PushPublisher#publish()` |

`matching`이 발행하는 종류는 5종이다 — `MATCH_QUEUE_UPDATED`, `MATCH_PROPOSAL_CREATED`,
`MATCH_PROPOSAL_EXPIRED`, `MATCH_CONFIRMED`, `MATCH_CANCELLED`. 전체 SSE 계약은 15종이고
나머지 10종(`RESERVATION_*`, `PARTY_*`, `FRIEND_*`, `WEBRTC_SIGNAL`)은 다른 앱이 발행한다
(`contracts/events.md`). **`WEBRTC_SIGNAL`도 다른 알림과 똑같이 흘려보낸다** — 시그널을 받는 길이
SSE 이고 보내는 쪽은 `app:room`의 REST `POST`다(`matching` docs/11 D-16. D-9 는 `app:platform`으로 적었다). WebSocket(`/ws`)은 없어졌으니 여기 만들지 않는다
(`matching`의 `docs/11_DECISION_LOG.md` D-9).

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
- **하트비트** — 15~30초마다 **이름 있는 이벤트**(`event: heartbeat`, `data` 필수)를 보낸다. 프록시·
  로드밸런서의 유휴 타임아웃(Stage 2 ALB 300초)보다 짧아야 하고, 쓰기에 실패한 죽은 연결이 이때
  드러난다. 주석 줄이 아닌 이유는 **프런트가 조용히 죽은 연결을 알아챌 수 있게** 하기 위해서다 —
  `EventSource`는 주석 줄을 자바스크립트에 올리지 않는다. `data`가 비면 브라우저가 디스패치하지
  않는다. 알림은 **이름 없는 이벤트**로 남긴다(`onmessage` 하나로 받고 `type`으로 나눈다).
- **재접속 대기 시간** — `retry:`를 **연결할 때 한 번**, 연결마다 무작위로 내려 재배포 직후 재접속
  몰림을 흩는다. 알림마다 붙이지 않는다.
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

배포 기준은 **Stage 2(ECS Fargate)**다. Stage 1(단일 EC2 + Docker Compose)은 적용하지 않는다
(`matching` docs/11 D-18). **k8s / HPA / sticky session을 전제한 구현 금지**는 그대로다.

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라.

### 7.1 게시판 채널 구독 — 구현됐다 · `topics` 는 없앴다 (`matching` docs/11 D-20 · D-22, 2026-09-20)

**왜 생기나.** 파티 모집 게시판의 목록이 **F5 없이 갱신**돼야 한다(다른 사람이 글을 올리거나 방의 인원이 바뀌면 보고 있는 화면에 반영). 지금 알림은 사용자 한 명의
채널(`qm:pubsub:push:{userId}`)로 가는데, 목록을 보는 사람은 발행하는 쪽이 **"누구인지 모르는 다수"**다. 그래서 사용자 채널과 별개인 **게시판 채널**을 둔다.

**D-22가 D-20을 고쳤다.** D-20은 "채널은 게임별 `qm:pubsub:board:{game}` 셋이고, 클라이언트가 SSE를 열 때 `topics=board:LOL`로 구독을 알리고, 이 서비스는 그 주제를 구독한 연결에만 보낸다"였다.
**채널은 게임을 구분하지 않는 `qm:pubsub:board` 하나가 됐고, `topics` 파라미터를 없앴다** —
이 서비스는 게시판 신호를 **구독 여부와 상관없이 살아 있는 모든 SSE 연결에 그대로 보낸다.** 서버에서 거르지 않는다. **거르는 것은 클라이언트다**(아래 표).

**정해진 것.**

| 항목 | 값 |
|---|---|
| 채널 | **`qm:pubsub:board`** — 게임을 구분하지 않는 **하나**다(D-22) |
| 발행하는 앱 | `platform`(글이 생기거나 사라지거나 상태가 바뀔 때) · `room`(방의 인원이 바뀔 때). **둘 다 아직 발행하지 않는다** — `platform`은 코드가 없고 `room`은 아직 구현하지 않았다(막는 미정은 없다). **둘 다 같은 채널 하나에 `{}`를 발행한다 — 발행하는 쪽이 게임을 알 필요가 없다** |
| 메시지 | 사용자 채널과 같은 네 칸 봉투다. `type`은 **`BOARD_CHANGED`**, **`payload`는 빈 객체 `{}`** — `roomId`도 `game`도 싣지 않는다 |
| 받는 연결 | **살아 있는 모든 SSE 연결.** `topics` 파라미터는 없다 — 클라이언트는 구독을 알리지 않는다(D-22) |
| 받은 클라이언트가 하는 일 | **거른다** — 게시판 페이지(어느 게임이든)를 보고 있으면 `platform`의 목록을 `GET`으로 다시 요청하고, 게시판 페이지가 아니면 무시한다. 다른 게임의 방이 바뀐 신호에도 재요청이 나가므로 **프런트가 재요청을 묶는다**(예: 몇 초에 최대 1번. 간격은 미정). **이 신호는 "다시 받아라"일 뿐이다** — 데이터와 차단 거르기는 그 응답에서 온다 |
| 구현 (2026-09-20) | `subscription/BoardChannelSubscriber`가 **기동 때 `qm:pubsub:board` 하나를 한 번만 구독하고 풀지 않는다.** `subscription/PushBoardListener` → `SseConnections.sendAll`이 살아 있는 모든 연결에 보낸다. 채널 이름 상수는 `redisKeys/BoardChannels.java`에 있다(그것이 원본인지는 미정). **게임 enum은 없다** |

**이 서비스의 원칙과 어떻게 맞물리나.**

- **"본문을 열어 보지 않고 그대로 흘려보낸다"(§2 · §3)는 그대로다.** 게시판 채널로 들어온 메시지도 해석하지 않고 SSE `data:`에 그대로 싣는다. `type`별 분기·필터링도 여전히 없다.
  D-20이 말한 "연결이 사용자 채널 말고 주제 채널도 구독할 수 있다"는 개념은 **생기지 않는다**(D-22) —
  이 서비스는 연결마다 주제를 기억하지 않는다.
- **왜 서버에서 거르지 않나(D-22).** 거르려면 이 서비스가 연결마다 주제를 기억하고 `topics` 파라미터 · 주제→연결 맵 · 표기 규칙을 가져야 한다. 클라이언트가 무시하면 전부 필요 없다.
  채널이 하나라서 **게임 목록(enum)도 필요 없다.** 대가는 둘이다 — 다른 게임의 변화에도 프런트의 재요청이 나가고(프런트가 묶어서 상한을 둔다), 게시판을 안 보는 연결(방 안에서 음성 중인 사람 등)에도 작은 이벤트가 간다(인원이 바뀔 때마다 (연결 수)만큼 SSE 쓰기). MVP 규모에서 받아들인다.
  **다시 볼 조건** — 재요청이나 SSE 쓰기가 부담이 되면 `payload`에 `game`을 싣거나(발행하는 쪽의 일이다 — 이 서비스는 여전히 열어 보지 않는다) `topics`를 되살린다.
- **신호에 데이터가 없는 이유가 이 서비스에 있다.** 방송은 사람별로 거를 수 없고, 이 서비스는 거르지 않는다(§2). 그래서 차단 관계에 따라 달라지는 목록 데이터는 방송에 싣지 않고
  `platform`의 조회에서만 나간다. **이 서비스에 사람별 거르기를 넣자는 요구가 오면 그것은 이 결정과 반대다** — 묻는다.
- **구독은 채널 하나에 이름으로 건다 — 패턴 구독 금지(§5)는 그대로다.** **사용자별 구독과 다르게 다룬다** — 게시판 채널은 하나이고 접속한 모두가 같이 쓰므로
  연결의 접속 · 종료에 맞춰 걸고 풀지 않는다. **기동 때 한 번만 걸고 풀지 않는다.**
- **정리(§5)에 더해지는 것이 없다.** 주제→연결 목록이 없으므로 `onCompletion` / `onTimeout` / `onError`에서 따로 뺄 것이 없다 — 사용자 쪽 연결 목록에서 빠지면 `sendAll`의 대상에서도 빠진다.
- **sticky session이 필요 없다는 점도 그대로다.** 어느 인스턴스에 붙든 그 인스턴스가 게시판 채널을 구독하고 Redis가 모든 구독자에게 뿌린다.
- **놓친 신호를 다시 보내지 않는다(§2)도 그대로다.** 클라이언트는 SSE를 (다시) 연결한 직후 목록을 다시 받는다.
- **채널 이름은 `qm:pubsub:push:` 접두사와 같은 위험이다**(§3) — 발행하는 앱 둘과 이 서비스가 어긋나도 컴파일·테스트가 통과한 채로 목록이 조용히 갱신되지 않는다. 상수 한 곳에만 둔다.
- 매칭 Redis 키에 접근하지 않는다는 규칙(§2)과 부딪히지 않는다 — `qm:pubsub:board`는 키가 아니라 Pub/Sub 채널이고 이 서비스는 구독만 한다.

**미정 — 지어내지 마라.**

- 게시판 채널 이름의 **원본 상수를 어느 서비스에 둘지.** 지금은 이 서비스의 `redisKeys/BoardChannels.java`에 적혀 있다 — 그것이 원본인지는 정해지지 않았다. 알림 채널 접두사는 `matching`의 `SharedKeys`가 원본이다 — 같은 방식으로 갈지 정해지지 않았다.
- 프런트가 재요청을 묶는 간격(이 서비스의 일은 아니다).
- **없어진 미정(D-22)** — `room`이 어느 게임의 채널에 발행할지를 어떻게 아는가(채널이 하나라 발행하는 쪽이 게임을 알 필요가 없다), 한 연결이 여러 주제를 구독할 때의 `topics` 표기, 주제 구독의 인증 · `?userId=` 없이 주제만 구독하는 연결. `topics`가 없어져 물음 자체가 없어졌다.

급하면 프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다(D-20) — 그 경우 이 서비스는 바뀌지 않는다.

### 7.2 그 밖의 미정

| 항목 | 상황 |
|---|---|
| 알림 종류의 수 | `room`이 새 알림 `type` 다섯(`ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED`)을 발행한다(`matching` docs/11 D-21 · `../room/contracts/room-api.md` "알림"). **§3의 "전체 SSE 계약은 15종"이 어떻게 달라지는지는 계약 원본에서 정할 일이라 여기서 고치지 않았다** — 그 다섯이 `PARTY_*`와 같은 뜻인지에 달려 있다. **이 서비스의 코드는 바뀌지 않는다** — `type`을 열어 보지 않고 그대로 흘려보낸다 |
| 인증 | **방식은 쿠키로 정해졌다 (`matching` docs/11 D-14). 구현은 아직이다.** access 토큰은 쿠키로 오는 JWT이고 브라우저가 SSE 연결에도 자동으로 붙인다. 이 서비스에 남은 미정 — ① access 토큰을 **검증하는 방법**(서명 방식과 키를 나눠 갖는 법) ② 토큰이 **없거나 만료됐을 때의 응답** — 401을 주면 `EventSource`는 재접속을 영구히 멈춘다 ③ 인증이 붙으면 `?userId=` 쿼리 파라미터가 없어지고 **토큰의 사용자로 대체**된다 — 그 전환 ④ 로컬 개발의 CORS(출처가 포트마다 다르고, 자격증명을 실으면 허용 출처에 `*`를 못 쓴다) |

정해진 것 (2026-09-18): 엔드포인트 경로는 **`GET /api/v1/events`**다. 인증이 **구현될** 때까지 `userId`를
쿼리 파라미터로 그대로 받는다 — 인증이 붙으면 이 파라미터는 없어진다. 계약 원본의
`contracts/openapi.yaml`에는 이 경로가 아직 없다.

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `notification` 브랜치**다. 이력이 없는 별도 브랜치에서
시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       matching 브랜치
├── platform/       platform 브랜치
├── room/           room 브랜치
└── notification/   notification 브랜치 (이 폴더)
    └── backend/    스프링 앱. matching/backend/ 와 같은 모양이다
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
| 알림 계약 (봉투·재연결·하트비트·순서) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/events.md` |
| 봉투를 만드는 코드 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` |
| 알림 종류 5종 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushEventType.java` |
| 게시판 채널 구독의 결정 (D-20 · D-22) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/11_DECISION_LOG.md` (`### D-20.` · `### D-22.`로 검색) |
| 채널 접두사 원본 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` (`PUSH_CHANNEL_PREFIX`) |

## 11. 이 저장소에서 하지 말 것 (요약)

- 메시지 `type`/`payload` 해석, 종류별 분기 — 그대로 흘려보낸다
- 놓친 알림 재전송, 알림 이력 저장 — 복구는 `matching` 상태 조회가 맡는다
- 매칭 Redis 키(`qm:party:*`, `qm:user:*`, `qm:proposal:*`, `qm:gameconfig:*`) 접근
- `qm:pubsub:push:*` 패턴 구독 — 사용자별 구독만 쓴다. 게시판 채널(§7.1)도 패턴 구독을 하지 않는다 — `qm:pubsub:board` 하나를 이름으로 구독한다
- 게시판 채널의 메시지를 열어 보거나 사람별 · 게임별로 거르기 — `BOARD_CHANGED`도 모든 연결에 그대로 흘려보낸다. 게시판을 보고 있는지는 클라이언트가 거르고(D-22), 차단 거르기는 `platform`의 목록 조회가 한다(D-20)
- 채널 접두사를 `matching`과 따로 바꾸기
- Redis 리스너 스레드에서 직접 전송
- k8s/HPA/sticky session 전제 구현
- 포트 6379 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`
