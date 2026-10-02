# notification — QueueMate 알림 서비스

매칭 엔진(`matching`)이 Redis Pub/Sub 에 던진 알림을 받아, 사용자 브라우저로 **SSE** 로 흘려보낸다.
예전 문서에서 `app:realtime` 이라고 부르던 배포 단위가 이것이다.

```
matching  ──publish──▶  Redis Pub/Sub               ──subscribe──▶  notification  ──SSE──▶  브라우저
platform  ──publish──▶  qm:pubsub:push:{userId}
                        qm:pubsub:board (게시판 신호)
```

`{userId}` 는 **사용자 번호(숫자)** 다 — `qm:pubsub:push:42`(2026-09-22 `platform` 소유자 결정 · P-11. 그 전에는 로그인 아이디 문자열이었다).
그림은 처음에 `matching` 한 줄이었고, `platform`(친구 알림 · 방 알림 · 시그널 · 게시판 신호)이 더해졌다. 방 쪽 발행은 `room` 앱이 하다가
**2026-09-25 에 `room` 이 `platform` 에 합쳐져 `platform` 의 `room` 패키지가 한다**(P-22 — 옆 폴더 `room` 은 합치기 전의 기록이다).

## 이 서비스가 하는 일 — 두 가지뿐

1. 사용자의 SSE 연결을 들고 있는다
2. 그 사용자 채널에 들어온 메시지를 연결로 그대로 흘려보낸다

매칭 로직은 하나도 없다. 메시지 봉투(`{type, eventId, occurredAt, payload}`)는
`matching` 의 `notification/PushPublisher.java` 가 정하고, 여기서는 **해석하지 않고 전달만** 한다.
`matching` 이 발행하는 종류는 `notification/PushEventType.java` 5종이고, 전체 SSE 계약은 15종이다.
다른 앱이 발행한 것(`PARTY_*`, `FRIEND_*`, `RESERVATION_*`, `WEBRTC_SIGNAL`)도 같은 채널로 들어오면
**종류를 가리지 않고 그대로** 흘려보낸다. 종류가 늘어도 이 서비스는 다시 배포하지 않는다.

## 이 서비스가 하지 않는 일

- **놓친 알림을 다시 보내지 않는다.** Redis Pub/Sub 은 구독자가 없으면 메시지를 버린다.
  페이지를 나가 있던 동안의 알림은 사라지고, 다시 들어온 사용자는 `matching` 의 상태 조회
  (`GET /api/v1/match-requests` — 2026-09-27 부터 그쪽도 `?userId=` 가 아니라 쿠키 `qm_access` 다)로 현재 상태를 따라잡는다. 이력을 쌓으려 하지 마라.
- 매칭 상태를 읽거나 바꾸지 않는다. Redis 에서 만지는 것은 접속 중인 사용자의
  `qm:pubsub:push:{userId}` 채널 구독뿐이다.

## 저장소 구성

`matching` 과 **같은 GitHub 저장소의 다른 브랜치**(`notification`)다. 이력이 없는 빈 브랜치에서
시작했으므로 매칭 코드는 이 브랜치에 없다. 로컬에서는 `git worktree` 로 나란히 둔다.

```
queuemate/
├── matching/       matching 브랜치
├── platform/       platform 브랜치 (계정 · 게시판 · 방 안의 일)
├── room/           room 브랜치 — 옛 app:room. 2026-09-25 에 platform 에 합쳐졌다(기록으로 남는다)
└── notification/   notification 브랜치 (이 폴더)
    └── backend/    스프링 앱. matching/backend/ 와 같은 모양이다
```

## 설계 메모

- **구독은 사용자별로 건다.** 연결이 생기면 `qm:pubsub:push:{userId}` 를 구독하고, 그 사용자의
  마지막 연결이 끊기면 푼다. `qm:pubsub:push:*` 패턴 구독은 사용자가 늘면 모든 인스턴스가 모든
  메시지를 받게 되므로 쓰지 않는다.
- **한 사용자가 여러 탭을 연다.** `userId → 연결 목록` 으로 들고 같은 메시지를 전부에 보낸다.
- **하트비트** — 15~30초마다 이름 있는 이벤트(`event: heartbeat`, `data` 필수)를 보낸다. 프록시·
  로드밸런서가 유휴 연결을 끊지 않게 하고, 서버가 죽은 연결을 발견하고, 프런트가 조용히 죽은 연결을
  알아챌 수 있게 한다(주석 줄은 `EventSource` 가 자바스크립트에 올리지 않는다). 알림은 이름 없는
  이벤트로 남긴다.
- **재접속 대기 시간** — `retry:` 를 연결할 때 한 번, 연결마다 무작위로 내려 재배포 직후 재접속
  몰림을 흩는다. 알림마다 붙이지 않는다.
- **정리** — 완료·타임아웃·에러 어느 쪽으로 끝나도 연결 목록과 구독을 비운다.
- **느린 클라이언트가 리스너를 막지 않게** Redis 리스너 스레드에서 바로 보내지 않고 별도 풀로 넘긴다.
- **보내기 실패는 그 연결만 버린다.** 알림 하나 때문에 다른 사용자가 영향받으면 안 된다.
- 인스턴스가 늘어도 sticky session 이 필요 없다 — Redis 가 모든 구독자에게 뿌린다.

## 게시판 채널 구독 — 구현됐다 · `topics` 는 없앴다 (`matching` docs/11 D-20 · D-22)

파티 모집 게시판의 목록을 F5 없이 갱신하려고, 사용자 채널과 별개인 **게시판 채널 `qm:pubsub:board`**(게임을 구분하지 않는 하나다 — D-22 가 D-20 의 게임별 세 채널을 고쳤다)를
구독해 **살아 있는 모든 SSE 연결에** 그대로 흘려보낸다(D-22 — 서버에서 거르지 않는다). 목록을 보는 사람은 발행하는 쪽(`platform` — 2026-09-25 까지는 `room` 도)이 누구인지 모르는 다수라서 사용자 채널로는 보낼 수 없다.

- 메시지는 같은 네 칸 봉투이고 `type` 은 `BOARD_CHANGED`, `payload` 는 빈 객체 `{}` 다(`roomId` 도 `game` 도 싣지 않는다). **여기서도 열어 보지 않고 그대로 흘려보낸다.**
  발행하는 쪽은 게임을 몰라도 된다 — 같은 채널 하나에 `{}` 를 발행한다(처음에는 `room` · `platform` 두 앱이었고 2026-09-25 부터 `platform` 하나다 — 글 쪽과 방 쪽이 같은 앱에서 발행한다).
- **`topics` 파라미터는 없앴다**(D-22. D-20 은 `GET /api/v1/events?topics=board:LOL` 로 구독을 알리게 했다). **거르는 것은 클라이언트다** — 게시판 페이지(어느 게임이든)를 보고 있으면
  목록을 묶어서 다시 요청하고, 게시판 페이지가 아니면 무시한다. 대가는 다른 게임의 변화에도 재요청이 나가는 것과 게시판을 안 보는 연결에도 작은 이벤트가 가는 것이고 MVP 규모에서 받아들인다.
- 구현(2026-09-20) — `BoardChannelSubscriber` 가 기동 때 `qm:pubsub:board` 하나를 한 번만 구독하고 풀지 않는다. `PushBoardListener` → `SseConnections.sendAll`. 채널 이름 상수는 `redisKeys/BoardChannels.java`, 게임 enum 은 없다. **`room` 의 발행은 2026-09-21(D-23)에, `platform` 의 발행도 같은 날 구현됐다 — 2026-09-25 에 둘이 `platform` 한 앱이 됐다.**
- 신호는 "다시 받아라"일 뿐이다. 받은 프런트가 `platform` 의 목록을 다시 요청하고, 데이터와 차단 거르기는 그 응답에서 온다. 그래서 이 서비스는 사람별로 거르지 않아도 된다.
- "연결이 주제 채널도 구독한다"는 개념은 생기지 않는다 — 연결마다 주제를 기억하지 않는다. 패턴 구독 금지 · sticky session 불필요 · 놓친 것을 다시 보내지 않는다는 그대로다.
- 미정 — 채널 이름의 원본 상수를 어느 서비스에 둘지(지금은 이 서비스의 `redisKeys/BoardChannels.java` 와 `platform` 의 `party/board/BoardChannels` 두 곳에 같은 값이 있다 — 옛 `room` 사본은 합치며 없어졌다. `topics` 표기와 "`room` 이 방의 게임을 아는 법"의 미정은 D-22 로 없어졌다). 상세는 `CLAUDE.md` §7.1.

## 인증 — 쿠키 `qm_access` (2026-09-27)

`GET /api/v1/events` 는 **쿠키 `qm_access` 의 access 토큰**(`platform` 이 RS256 으로 서명한 JWT)으로 "나"를 정한다 — 토큰의 `sub`(사용자 번호 `"42"`)가
채널 `qm:pubsub:push:{userId}` 의 `userId` 다. `EventSource` 는 헤더를 못 붙이지만 같은 출처의 쿠키는 자동으로 싣는다.
**2026-09-26 까지는 `?userId=` 를 그대로 받았다 — 2026-09-27 에 없어졌다**(줘도 보지 않는다. 개발용 스위치도 두지 않았다).

- **검증만 한다** — 공개 키로 서명 · `exp` · `iss`(`queuemate-platform`) · `token_use`(`access`) · `sub`(숫자 문자열)를 본다. 개인 키는 `platform` 만 갖는다.
- **공개 키** — 환경변수 `JWT_PUBLIC_KEY`(X.509 PEM — 운영). 비어 있으면 `JWT_PUBLIC_KEY_FILE`(기본값 `../../platform/backend/.dev-keys/public.pem` —
  `backend/` 에서 띄울 때 `platform` 이 만든 개발용 공개 키)을 읽는다. **둘 다 없으면 기동하지 않는다** — 로컬에서는 `platform` 을 먼저 한 번 띄운다.
- 실패는 전부 401 `{"code":"UNAUTHENTICATED", …}`(`platform` 과 같은 본문). 연결할 때만 검증하고 열린 연결은 만료돼도 끊지 않는다 —
  401 로 재접속이 멈추면 프런트가 재발급한 뒤 `EventSource` 를 새로 만든다.
- `Origin` 검사(`ALLOWED_ORIGINS`)도 `platform` 과 같은 모양으로 넣었지만 지금은 GET 하나뿐이라 걸릴 요청이 없다.
- 상세는 `CLAUDE.md` §5.1, 원본 규칙은 `../platform/CLAUDE.md` §5.1.

엔드포인트 경로는 `GET /api/v1/events` 로 정했다. 계약 원본의 `contracts/openapi.yaml` 에는 이 경로가 아직 없다.

## 헬스 체크 (2026-10-02)

- `GET /health/live` — 살아 있나(ALB 헬스 체크는 여기에 건다). `GET /health/ready` — Redis 까지 붙었나. 둘 다 인증 없이 `{"status":"UP"}` 만 준다(`platform` 과 같은 모양).
- 떠 있나 확인 — `curl -s localhost:8081/health/live` 가 `{"status":"UP"}` 이면 떠 있다(`/api/v1/events` 는 쿠키 없이 401 이다). 상세는 `CLAUDE.md` §6.
