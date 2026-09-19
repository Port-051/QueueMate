<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 contracts/events.md (110줄) -->
<!-- 커밋: 825d673 -->
<!-- ★ 발췌본이다. app:matching 이 발행하는 이벤트와 그 전달 규약만 옮겼다. -->
<!-- ★ 이 사본이 원본보다 앞서 개정된 부분이 있다 — "재연결"(2026-09-18), 전송 표와 WEBRTC_SIGNAL(2026-09-19), heartbeat 와 retry(2026-09-19), SQS 큐 표의 BlockChanged 폐기(2026-09-19). 각 자리의 "개정 이력"과 contracts/README.md 를 봐라. -->

# Server Event Contract — app:matching 발췌

서버→클라 전송은 **SSE 하나**다 (docs/11 #22 를 D-9 가 개정).

| 전송 | 엔드포인트 | 방향 | 담는 것 |
|---|---|---|---|
| SSE | `GET /api/v1/events` | 서버 → 클라 단방향 | 15종 (`WEBRTC_SIGNAL` 포함) |

> 개정 이력: 예전 판은 "전송을 **2종으로 분리**한다 — SSE 14종 + WebSocket(`/ws`, 양방향)
> `WEBRTC_SIGNAL` 1종"이라고 적었다. 2026-09-19 에 위와 같이 바꿨다 (docs/11 D-9) —
> WebSocket 을 남긴 유일한 이유인 클라→서버 방향은 REST `POST` 로 충분하고, 1종 때문에
> WebSocket 스택 전체를 따로 만들어 운영하는 것이 과하다. 시그널을 **보내는** 쪽은
> `app:platform` 의 REST `POST` 다 — 아래 "`WEBRTC_SIGNAL` 의 전달" 절.

> **연결을 받는 것은 `app:realtime` 하나다.**
> 이 저장소(`app:matching`)는 SSE 를 열지 않는다. WebSocket 은 어느 앱에도 없다.
> 여기서 하는 일은 **Redis Pub/Sub 에 publish 하는 것까지**이고,
> 그 뒤 `Redis Pub/Sub → app:realtime → SSE` 는 다른 앱의 책임이다.
> 이 저장소에 `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 을 넣지 마라.

## `app:matching` 이 발행하는 5종

전체 15종 중 이 앱이 발행 주체인 것만 옮긴다.
나머지 10종(`RESERVATION_*` 2종 = `app:reservation-batch`,
`PARTY_*`·`FRIEND_*` 7종과 `WEBRTC_SIGNAL` 1종 = `app:platform`)은 이 저장소 소관이 아니다.

| type | 발행 주체 | 언제 |
|---|---|---|
| `MATCH_QUEUE_UPDATED` | `app:matching` | 대기 상태가 바뀔 때 (payload 미정의) |
| `MATCH_PROPOSAL_CREATED` | `app:matching` | 파티 정원이 차서 제안이 생겼을 때 |
| `MATCH_PROPOSAL_EXPIRED` | `app:matching` | 제안 TTL 만료 |
| `MATCH_CONFIRMED` | `app:matching` | 전원 수락으로 확정 (INV-4) |
| `MATCH_CANCELLED` | `app:matching` | 매칭 취소 |

<!-- 아래 "구현 상태" 줄은 계약 본문이 아니라 이 저장소가 붙인 주석이다. 2026-09-16 갱신. -->
**구현 상태: 5종 모두 발행된다.** `notification/PushPublisher.java` 가
`qm:pubsub:push:{userId}` 채널에 publish 한다.

| type | 발행하는 곳 | 상태 |
|---|---|---|
| `MATCH_QUEUE_UPDATED` | `rule/{lol,pubg,valorant}/*Assigner.java` (새 파티 생성 / 정원 미달 합류) | **발행됨.** payload `{memberNumber}` |
| `MATCH_PROPOSAL_CREATED` | 같은 클래스들의 `JOINED_AND_FULL` 분기 | **발행됨.** payload `{memberNumber, target, partyId}` |
| `MATCH_CANCELLED` | `rule/{lol,pubg,valorant}/*PartyLeaver.java` (남은 파티원에게만, 취소한 본인 제외) | **발행됨.** payload `{memberNumber}` |
| `MATCH_PROPOSAL_EXPIRED` | `service/ProposalExpiryService.java#expire()` | **발행됨.** payload `{partyId}`. 시한이 지난 제안을 `service/ProposalSweeper.java`(`@Scheduled`, `queuemate.sweep.interval-ms`, 기본 1초)가 `qm:proposal:pending` ZSET 에서 꺼내 `redis/proposal/expiry-proposal.lua` 로 깬다. **받는 사람은 그 제안에 있던 전원**이다 — 수락하지 않은 사람(큐에서도 빠진다)과 수락한 사람(파티에 남아 다시 기다린다)을 가리지 않는다. 수락자도 받아야 제안 화면에 갇히지 않기 때문이다. **거절**로 제안이 깨졌을 때 남은 사람에게 알리는 것은 여전히 없다(계약에 그 type 이 없다 — 아래 "미해결 계약 구멍") |
| `MATCH_CONFIRMED` | `service/ProposalService.java#accept()` | **발행됨.** payload `{partyId}`. `redis/proposal/accept-proposal.lua` 가 수락자 SET 을 `SCARD` 로 세어 `target` 에 닿으면 `status = CONFIRMED` 를 찍고(INV-4), 이어서 `redis/proposal/cleanup-confirmed.lua` 가 돌려준 파티원 전원에게 나간다. **확정을 만든 그 한 번의 호출에서만 나간다** — 이미 확정된 제안에 수락이 또 오면 스크립트가 `CONFIRMED` 가 아니라 `ALREADY_RESPONDED` 를 돌려주므로 같은 알림이 두 번 나가지 않는다. 아직 없는 것은 `ProposalConfirmed.fifo` 발행이다 — 그래서 `app:platform` 이 파티를 DB 에 만들지 못한다 |

payload 필드는 계약이 정한 것이 아니다 — 아래 "미해결 계약 구멍"이 지적한 그대로
15종 전부 payload 스키마가 비어 있어서, 구현이 먼저 정하고 여기에 적어 둔 것이다.
발행 여부는 `backend/src/test/java/com/queuemate/matching/notification/PushNotificationTest.java`
가 실제로 구독해서 검증한다. **다만 그 6건이 보는 것은 앞의 3종뿐이다** —
`MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED` 를 구독해서 확인하는 테스트는 아직 없다.

## Envelope

```json
{
  "type": "MATCH_PROPOSAL_CREATED",
  "eventId": "uuid",
  "occurredAt": "2026-08-29T09:00:00Z",
  "payload": {}
}
```

### 재연결 — 놓친 알림은 다시 보내지 않는다
- envelope 의 `eventId` 를 SSE `id:` 필드에 그대로 싣는다(클라이언트가 중복을 거르는 데 쓸 수 있다).
- **서버는 `Last-Event-ID` 로 이어 보내지 않는다.** 연결이 끊긴 동안 발행된 알림은 사라진다
  — 발행이 Redis Pub/Sub 이라 구독자가 없는 순간의 메시지는 어디에도 남지 않는다.
- 대신 **클라이언트가 재연결 직후(그리고 페이지 진입 시) 상태를 한 번 조회**해 현재 상태를 맞춘다
  (`GET /api/v1/match-requests?userId=` — 대기/제안/확정 여부와 제안의 남은 시간을 돌려준다).
- 순서: **SSE 를 먼저 연결하고 그다음 조회한다.** 반대로 하면 조회와 연결 사이에 온 알림을 놓친다.
- 이렇게 정한 이유: 이 알림들은 대부분 "상태가 바뀌었다"는 신호라 중간 과정보다 **지금 상태**가
  중요하다. 버퍼를 두고 이어 보내도 오래 나가 있던 사용자는 결국 조회로 복구해야 해서, 재개를
  구현하는 복잡도(서버별 버퍼, 여러 인스턴스면 공유 저장소)에 비해 얻는 것이 작다.

  > 개정 이력: 예전 판은 "`Last-Event-ID` 로 유한 버퍼에서 재개한다"고 적었다. 2026-09-18 에
  > 위와 같이 바꿨다 — notification 서비스는 재전송하지 않는다.

- **재접속 대기 시간은 서버가 내려 준다.** 서버는 **연결할 때 한 번** SSE `retry:` 필드로 재접속
  대기 시간을 보낸다. 값은 연결마다 무작위다(현재 구현 기본 1000~2000ms, 설정으로 바꿀 수 있다).
  재배포 직후 모든 클라이언트가 같은 순간에 재접속하고 각자 상태 조회까지 하는 몰림을 흩으려는
  것이다. 알림마다 붙이지 않는다 — 브라우저가 마지막 값을 기억한다. 클라이언트가 따로 할 일은
  없다(`EventSource` 가 알아서 쓴다).

  > 개정 이력: 2026-09-19 에 새로 넣은 항목이다 (docs/11 D-10). 이 발췌본의 예전 판에는
  > `retry:` 에 대한 서술이 없었다.

### heartbeat — 주석 줄이 아니라 이름 있는 이벤트다
- 서버는 15~30초마다(현재 구현 20초) `event: heartbeat` 줄과 `data: heartbeat` 줄로 된 이벤트를
  보낸다. `data` 의 내용에는 의미가 없다 — 읽지 마라. 데이터가 빈 이벤트는 브라우저가 디스패치하지
  않아서 싣는다.
- 원래 하던 일은 그대로다. 프록시·로드밸런서 idle timeout(Stage 2 ALB 기준 300초)보다 짧게 바이트를
  흘리고, 서버가 쓰기 실패로 죽은 연결을 찾아 정리한다.
- 클라이언트는 `es.addEventListener("heartbeat", ...)` 로 받는다. **`onmessage` 로는 오지 않는다.**
  알림(envelope)은 계속 **이름 없는 이벤트**라 `onmessage` 로 온다 — 통로가 갈리므로 알림 처리
  코드가 하트비트를 JSON 파싱할 일이 없다.
- 클라이언트는 마지막으로 하트비트(또는 알림)를 받은 시각을 기록하고, **일정 시간 아무것도 오지
  않으면 연결을 닫고 새로 연 뒤 상태를 조회한다**(위 "재연결"의 순서 그대로 — SSE 먼저, 그다음 조회).
  기준 시간은 **권장값**이다 — 서버 간격의 상한이 30초이므로 60초(두 번 연속 놓침) 정도.
- 리스너를 등록하지 않아도 동작에는 문제가 없다. 이벤트가 버려질 뿐이고, 감시를 하지 않을 뿐이다.
- 연결 직후 서버가 보내는 `connected` 는 여전히 주석 줄이다. 계약 대상이 아니다 — 클라이언트는
  `onopen` 을 쓴다.
- 이렇게 정한 이유: 주석 줄은 `EventSource` 가 자바스크립트에 전달하지 않는다. 그래서 클라이언트는
  **종료 신호 없이 길만 사라진** 연결(공유기 재부팅, 와이파이는 잡혀 있는데 인터넷만 끊김, 절전 복귀,
  서버가 있는 기계가 통째로 죽음, 중간 프록시가 말없이 버림)을 알아챌 재료가 없었다. 서버는 주기마다
  **쓰기** 때문에 실패로 곧 알지만, 브라우저는 **읽기만** 해서 실패할 일이 없고 운영체제가 알아챌
  때까지 환경에 따라 1분 안쪽~수 분이 걸린다. 매칭 제안 수명이 20초라 그 시간이 길다.

  > 개정 이력: 예전 판은 "heartbeat(코멘트 라인)를 보낸다"고 적었다. 2026-09-19 에 위와 같이
  > 바꿨다 (docs/11 D-10) — 주석 줄은 자바스크립트에 닿지 않아 클라이언트가 죽은 연결을 감시할 수 없다.

### 순서 보장 범위
- **스트림 간 인과 순서는 보장하지 않는다.** 알림은 Redis Pub/Sub fanout 이고,
  이를 유발한 도메인 이벤트는 SQS FIFO 의 서로 다른 `MessageGroupId` 를 탄다.
  예를 들어 `MATCH_CONFIRMED` 와 `PARTY_MEMBER_JOINED` 의 도착 순서는 고정이 아니다.
- 따라서 **클라이언트 핸들러는 멱등하고 순서에 무관해야 한다.**
  이벤트를 상태 전이 트리거가 아니라 "다시 조회하라"는 신호로 다루는 편이 안전하다.
- 알림은 휘발성이라 재전송 보장이 없다. 놓친 상태는 REST 로 복구한다.
  (`WEBRTC_SIGNAL` 은 예외다 — 서버에 상태가 없어 REST 로 복구할 수 없다. 아래 절.)

### `WEBRTC_SIGNAL` 의 전달 — 받기는 SSE, 보내기는 `app:platform` 의 REST `POST`

> 개정 이력: 2026-09-19 에 새로 넣은 절이다 (docs/11 D-9). 예전 판은 `WEBRTC_SIGNAL` 을
> `app:realtime` 의 WebSocket(`/ws`) 소관으로 두었다. **이 저장소(`app:matching`)는
> `WEBRTC_SIGNAL` 을 발행하지 않는다** — 15종을 세는 근거와 복구 책임을 남기려고 적는다.

- **받기** — 다른 알림과 같은 SSE 다. 같은 envelope 에 `type` 이 `WEBRTC_SIGNAL` 이다.
  `app:realtime` 은 `type` 을 해석하지 않고 그대로 흘려보내므로 바뀌는 것이 없다.
- **보내기** — 클라이언트가 `app:platform` 에 REST `POST` 한다. `app:platform` 이 보내는 사람과
  받는 사람이 **같은 파티원인지 확인한 뒤** `qm:pubsub:push:{상대 userId}` 에 publish 한다.
  파티를 소유한 앱이 `app:platform` 이기 때문이다. 발행 주체는 `app:platform` 이다.
- **순서** — 여러 `POST` 는 도착 순서가 보장되지 않는다. 받는 쪽은 offer 가 오기 전에 도착한
  ICE 후보를 모아 두어야 한다. 통화 시작 시 ICE 후보마다 `POST` 가 나가므로 HTTP/2 를 전제한다.
- **놓친 시그널은 클라이언트가 복구한다.** 받는 쪽 SSE 가 끊긴 순간의 시그널은 사라진다
  (WebSocket 이어도 같다). 시그널은 서버에 저장되지 않으므로 위 "재연결"의 상태 조회로는
  되찾을 수 없다. 복구의 기준점은 서버에 저장된 **파티원 목록**이다.
  - 답이 없으면 offer 를 다시 보낸다.
  - SSE 재연결 시 파티원 목록과 실제 peer 연결을 비교해 빠진 상대에게 재협상한다.
  - peer 연결이 `failed` 면 ICE restart 를 한다.
  - 동시 offer 충돌을 피하는 규칙을 둔다(예: perfect negotiation 의 polite/impolite, 또는
    userId 비교).
- 음성 데이터 자체는 브라우저 직결(또는 TURN 중계)이라 서버를 거치지 않는다. 통화가 맺어진 뒤
  SSE 가 끊겨도 통화는 유지된다.
- *(제안 사항, 미정)* `PUBLISH` 반환값(그 순간 구독자 수)을 `POST` 응답에 실어 주면 클라이언트가
  "상대가 지금 연결 안 됨(0)"을 알고 기다리지 않고 재시도할 수 있다. 0 이 아니어도 브라우저
  도착을 보장하지는 않는다.

**미정이다 — 임의로 지어내지 않는다.**

- `POST` 엔드포인트의 경로와 요청/응답 스키마. (`POST /api/v1/parties/{partyId}/signals` 는
  **후보**일 뿐이다.) 이 발췌본의 `openapi.yaml` 에는 들어오지 않는다 — `app:platform` 소관이다.
- `WEBRTC_SIGNAL` 의 `payload` 스키마. 정해야 할 항목: 보낸 사람 식별자 / 파티 식별자 /
  종류(offer · answer · ICE candidate) / SDP 또는 candidate 본문 / 재협상 시도를 구분할 식별자.

---

## 서버 간 이벤트 — SQS FIFO

알림(Redis Pub/Sub)과 **성격이 다르다.** 놓치면 데이터가 어긋나므로 내구성·재시도·DLQ·
순서 보장이 필요하다 (docs/14 §7).

`app:matching` 이 걸린 큐는 **1개**다 — 생산만 하고, 소비하는 큐는 없다.

| 큐 | 이 앱의 역할 | MessageGroupId | 비고 |
|---|---|---|---|
| `ProposalConfirmed.fifo` | **생산자** — 확정된 제안을 내보내면 `app:platform` 이 소비해 파티를 만든다 | `proposalId` | DLQ + `maxReceiveCount` |
| ~~`BlockChanged.fifo`~~ | ~~**소비자** — 차단 목록 갱신을 받아 `qm:block:{userId}` read model 을 고친다~~ | — | **폐기됐다 — 만들지 않는다** (docs/11 D-12). 차단은 `app:platform` 이 `social.blocks` 에 저장하면 끝이고, 이 앱은 확정 직전에 그 테이블을 직접 조회해 INV-6 을 지킨다 (D-1) |

> 개정 이력: 예전 판은 "`app:matching` 이 걸린 큐는 2개다"라고 적고 `BlockChanged.fifo` 를 이 앱이
> **소비**하는 큐로 두었다(차단 쌍 단위 순서 보장 필수). 2026-09-19 에 위와 같이 바꿨다 (docs/11 D-12) —
> 선필터는 정확성을 책임지지 않고(D-2), 큐 하나와 그에 딸린 발행·소비·DLQ·멱등 처리가 통째로 없어진다.
>
> **`PartyClosed.fifo` 는 이 앱이 걸린 큐가 아니다.** 보내는 쪽도 받는 쪽도 `app:platform` 이고
> 이 앱은 읽지 않는다 (docs/11 D-13).

- 전달은 **at-least-once** 이므로 **소비자는 반드시 멱등해야 한다.**
- Redis Streams 로 대체하지 마라. 컨슈머 그룹으로 읽는 순간 순서가 깨진다 (docs/11 #26).
- outbox 는 **transactional outbox** 다. 확정 트랜잭션과 같은 트랜잭션에서 outbox 행을
  쓰고, relay 가 그것을 SQS 로 밀어낸다.

**구현 상태: `ProposalConfirmed.fifo` 발행은 미구현이다.** AWS SDK 의존성이 `build.gradle` 에 없다.

---

## 이 저장소가 전송하지 않는 것

- 파티 텍스트 채팅 본문 / 음성 미디어 — WebRTC DataChannel·audio track 을 탄다
- `WEBRTC_SIGNAL` — `app:platform` 이 발행한다. 받는 길은 다른 알림과 같은 SSE 다
  (위 "`WEBRTC_SIGNAL` 의 전달")

  > 개정 이력: 예전 판은 "`app:realtime` 의 `/ws` 소관"이라고 적었다. 2026-09-19 에 `/ws` 가
  > 없어지면서 위와 같이 바꿨다 (docs/11 D-9).

## 미해결 계약 구멍 (매칭 해당분)

원본 `contracts/events.md` 가 직접 지적한 것 중 이 저장소에 걸리는 것만 옮긴다.
**구현 전에 채워야 하며, 임의로 지어내지 않는다.**

- SSE 15종의 **payload 가 전부 미정의다.** envelope 만 있고 `payload` 스키마가 없다.
  (원본이 지적한 것은 14종이다. 2026-09-19 에 `WEBRTC_SIGNAL` 이 SSE 로 들어오면서 15종이
  됐고, 그 `payload` 도 미정이다 — 위 "`WEBRTC_SIGNAL` 의 전달".)
- **`MATCH_QUEUE_UPDATED` 의 payload 가 미정의다.** 대기 순번인지 예상 시간인지
  인원수인지 결정되지 않았다.
- **decline 전용 이벤트가 없다.** `MATCH_PROPOSAL_EXPIRED` 는 있지만 누군가 decline 해서
  proposal 이 깨진 경우를 나머지 참가자에게 알릴 type 이 없다.
- **accept 진행상황 이벤트가 없다.** "N명 중 M명 수락" 을 화면에 표시할 수단이 없다.
- **`PARTY_CREATED` 이벤트가 없다.** party 생성은 `ProposalConfirmed` 소비로 비동기
  진행되므로 `MATCH_CONFIRMED` 직후 클라이언트가 `GET /parties` 를 치면 404 가 날 수 있다.
- `contracts/openapi.yaml` 에 **`GET /api/v1/events` 가 없다.**
- `openapi.yaml` 에 **`ErrorResponse` 스키마가 없다** (이 저장소 발췌본에는 구현에서
  역으로 적어 두었다).
- `openapi.yaml` 에 **`securitySchemes` 가 없다.** JWT bearer 정의가 빠져 있다.
  (2026-09-19: docs/11 D-14 로 access 토큰은 `Authorization` 헤더가 아니라 **쿠키**로 주고받는다. 채울
  정의는 bearer 가 아니라 cookie 방식이다. 쿠키 이름은 미정이다.)
