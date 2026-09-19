# notification — QueueMate 알림 서비스

매칭 엔진(`matching`)이 Redis Pub/Sub 에 던진 알림을 받아, 사용자 브라우저로 **SSE** 로 흘려보낸다.
예전 문서에서 `app:realtime` 이라고 부르던 배포 단위가 이것이다.

```
matching  ──publish──▶  Redis Pub/Sub               ──subscribe──▶  notification  ──SSE──▶  브라우저
                        qm:pubsub:push:{userId}
```

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
  (`GET /api/v1/match-requests?userId=`)로 현재 상태를 따라잡는다. 이력을 쌓으려 하지 마라.
- 매칭 상태를 읽거나 바꾸지 않는다. Redis 에서 만지는 것은 접속 중인 사용자의
  `qm:pubsub:push:{userId}` 채널 구독뿐이다.

## 저장소 구성

`matching` 과 **같은 GitHub 저장소의 다른 브랜치**(`notification`)다. 이력이 없는 빈 브랜치에서
시작했으므로 매칭 코드는 이 브랜치에 없다. 로컬에서는 `git worktree` 로 나란히 둔다.

```
queuemate/
├── matching/       main 브랜치
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

## 아직 정할 것

- 인증 — SSE(`EventSource`)는 헤더를 못 붙인다. 쿼리 파라미터 토큰이냐 쿠키냐

엔드포인트 경로는 `GET /api/v1/events` 로 정했다. 인증이 정해질 때까지 `userId` 를 쿼리 파라미터로
받는다. 계약 원본의 `contracts/openapi.yaml` 에는 이 경로가 아직 없다.
