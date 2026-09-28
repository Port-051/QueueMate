<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 contracts/events.md (110줄) -->
<!-- 커밋: 825d673 -->
<!-- ★ 발췌본이다. app:matching 이 발행하는 이벤트와 그 전달 규약만 옮겼다. -->
<!-- ★ 이 사본이 원본보다 앞서 개정된 부분이 있다 — "재연결"(2026-09-18), 전송 표와 WEBRTC_SIGNAL(2026-09-19), heartbeat 와 retry(2026-09-19), SQS 큐 표의 BlockChanged 폐기(2026-09-19), RESERVATION_* 발행 주체(2026-09-19), WEBRTC_SIGNAL 발행 주체 app:room(2026-09-19) → app:platform(2026-09-25 합침, docs/11 D-33), "재연결"의 상태 조회 경로에서 ?userId= 제거(2026-09-27), SQS 절 — 이 앱이 걸린 큐 0개 · ProposalConfirmed.fifo 는 만들지 않고 app:platform 이 파티 HASH 를 읽는다(2026-09-27, docs/11 D-42, A-15). 각 자리의 "개정 이력"과 contracts/README.md 를 봐라. -->

# Server Event Contract — app:matching 발췌

서버→클라 전송은 **SSE 하나**다 (docs/11 #22 를 D-9 가 개정).

| 전송 | 엔드포인트 | 방향 | 담는 것 |
|---|---|---|---|
| SSE | `GET /api/v1/events` | 서버 → 클라 단방향 | 15종 (`WEBRTC_SIGNAL` 포함) |

> 개정 이력: 예전 판은 "전송을 **2종으로 분리**한다 — SSE 14종 + WebSocket(`/ws`, 양방향)
> `WEBRTC_SIGNAL` 1종"이라고 적었다. 2026-09-19 에 위와 같이 바꿨다 (docs/11 D-9) —
> WebSocket 을 남긴 유일한 이유인 클라→서버 방향은 REST `POST` 로 충분하고, 1종 때문에
> WebSocket 스택 전체를 따로 만들어 운영하는 것이 과하다. 시그널을 **보내는** 쪽은
> `app:room` 의 REST `POST` 다 — 아래 "`WEBRTC_SIGNAL` 의 전달" 절. (D-9 는 `app:platform` 으로 적었고
> 같은 날 D-16 이 `app:room` 으로 개정했다. **2026-09-25 에 `app:room` 을 `app:platform` 에 합쳐(docs/11 D-33)
> 다시 `app:platform`(의 `room` 패키지)이다.**)

> **연결을 받는 것은 `app:realtime` 하나다.**
> 이 저장소(`app:matching`)는 SSE 를 열지 않는다. WebSocket 은 어느 앱에도 없다.
> 여기서 하는 일은 **Redis Pub/Sub 에 publish 하는 것까지**이고,
> 그 뒤 `Redis Pub/Sub → app:realtime → SSE` 는 다른 앱의 책임이다.
> 이 저장소에 `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 을 넣지 마라.

## `app:matching` 이 발행하는 5종

전체 15종 중 이 앱이 발행 주체인 것만 옮긴다.
나머지 10종(`RESERVATION_*` 2종 = `app:reservation-batch`,
`PARTY_*`·`FRIEND_*` 7종 = `app:platform`, `WEBRTC_SIGNAL` 1종 = `app:room` → **2026-09-25 부터 `app:platform`**(docs/11 D-33))은 이 저장소 소관이 아니다.

> 개정 이력: 예전 판은 "`PARTY_*`·`FRIEND_*` 7종과 `WEBRTC_SIGNAL` 1종 = `app:platform`"이라고 적었다.
> 2026-09-19 에 방이 `app:room` 으로 분리되면서(docs/11 D-16) **`WEBRTC_SIGNAL` 의 발행 주체는 `app:room`**
> 이 됐다. **`PARTY_*` 가운데 방 입장 · 퇴장 · 강퇴 알림을 어느 앱이 어떤 `type` 으로 내는지는 미정이다
> (D-16)** — 그래서 `PARTY_*` 는 옮기지 않고 그대로 두었다. 7종의 이름이 이 컴퓨터에 없어 가를 수도 없다.
>
> 개정 이력(2026-09-26 적음): 방 입장 · 퇴장 · 방 닫힘 · 강퇴 · 방장 확정 알림은 `PARTY_*` 를 다시 쓰지 않고 새 이름
> `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` 로 정해졌다(docs/11 D-21).
> **2026-09-25 에 `app:room` 을 `app:platform` 에 합쳐(docs/11 D-33) 이것들과 `WEBRTC_SIGNAL` 의 발행 주체는 `app:platform` 이다.**
> `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` 의 이름과 `payload` 도 `app:platform` 이 정했다(`../platform/contracts/platform-api.md`
> "이 앱이 내는 알림" — 원본 `events.md` 와 맞춰야 한다). 알림 채널 `qm:pubsub:push:{userId}` 의 `{userId}` 는 **사용자 번호의 십진 문자열**이다(docs/11 D-25).
> 이 발췌본이 세는 15종과 그 새 이름들을 어떻게 맞출지는 원본과 합칠 때 정한다.

> 개정 이력: `RESERVATION_*` 2종의 발행 주체 `app:reservation-batch` 는 2026-09-19 에
> **`app:reservation`(AWS Lambda)이 대체한다** (docs/11 D-15). 예약 등록 REST 와 짝 찾기 배치가 한
> 덩어리가 되어 Lambda 로 빠졌다. 발행 방식(Redis `PUBLISH`)과 종류 수는 그대로다.

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
| `MATCH_CONFIRMED` | `service/ProposalService.java#accept()` | **발행됨.** payload `{partyId}`. `redis/proposal/accept-proposal.lua` 가 수락자 SET 을 `SCARD` 로 세어 `target` 에 닿으면 `status = CONFIRMED` 를 찍고(INV-4), 이어서 `redis/proposal/cleanup-confirmed.lua` 가 돌려준 파티원 전원에게 나간다. **확정을 만든 그 한 번의 호출에서만 나간다** — 이미 확정된 제안에 수락이 또 오면 스크립트가 `CONFIRMED` 가 아니라 `ALREADY_RESPONDED` 를 돌려주므로 같은 알림이 두 번 나가지 않는다. **이 알림이 파티 생성의 신호다**(docs/11 D-42) — `cleanup-confirmed.lua` 가 파티 HASH `qm:party:{partyId}` 에 `game` / `modeKey` / `voicePreference` / `playPurpose` / `confirmedAt` 을 채워 두고 TTL 600초를 걸며, 받은 클라이언트가 `app:platform` 의 `POST /api/v1/match-parties/{partyId}/room`(그쪽 계약 P-30, 2026-09-27)을 부르면 platform 이 그 HASH 를 읽어 파티와 방을 만든다(파티원 다섯이 다 눌러도 `partyId` 로 한 번만). 알림을 놓쳤으면 60초 안(`confirmed-retention-seconds`)에는 `GET /match-requests` 가 `MATCHED` + `partyId` 를 답해 복구된다. `ProposalConfirmed.fifo` 는 두지 않는다 — 아래 "서버 간 이벤트" 절 |

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
  (`GET /api/v1/match-requests` — 쿠키 `qm_access` 의 `sub` 가 "나"다. 대기/제안/확정 여부와 제안의 남은 시간을
  돌려준다. 항상 200 이고 활성 요청이 없으면 `{"status":"IDLE"}` 이다 — `openapi.yaml` `MatchRequestView`).

  > 개정 이력: 2026-09-27 — 이 항목은 2026-09-17 ~ 09-27 사이 `GET /api/v1/match-requests?userId=` 로 적혀 있었다.
  > `?userId=` 는 JWT 도입 전 임시 식별이었고 쿠키 인증으로 없어졌다 (contracts/README.md A-12 · A-13).
  > 원본 계약의 경로는 `GET /api/v1/match-requests/{requestId}` 다 — `/me` 로 옮길지는 미정이다(README #5).
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
  (2026-09-27 — 이 앱이 SQS 를 타는 이벤트는 없어졌다(docs/11 D-42). 원칙은 그대로다 — `MATCH_CONFIRMED` 와
  그 뒤 `app:platform` 이 내는 방 알림의 순서도 고정이 아니다.)
- 따라서 **클라이언트 핸들러는 멱등하고 순서에 무관해야 한다.**
  이벤트를 상태 전이 트리거가 아니라 "다시 조회하라"는 신호로 다루는 편이 안전하다.
- 알림은 휘발성이라 재전송 보장이 없다. 놓친 상태는 REST 로 복구한다.
  (`WEBRTC_SIGNAL` 은 예외다 — 서버에 상태가 없어 REST 로 복구할 수 없다. 아래 절.)

### `WEBRTC_SIGNAL` 의 전달 — 받기는 SSE, 보내기는 `app:room` 의 REST `POST`(→ 2026-09-25 부터 `app:platform` — D-33)

> 개정 이력(2026-09-26 적음): **아래의 `app:room` 은 `app:platform`(의 `room` 패키지)으로 읽는다** — 2026-09-25 에 합쳤다(docs/11 D-33).
> 경로는 `POST /api/v1/rooms/{roomId}/signals`(본문 `{toUserId, signal}` · 202)로 정해졌다(docs/11 D-21 — 아래 "미정"의 경로 · `payload` 는 그것으로 걸러 읽는다).
> 계약은 `../platform/contracts/platform-api.md` "방" 이다(옛 `../room/contracts/room-api.md` 는 합치기 전의 기록).

> 개정 이력: 2026-09-19 에 새로 넣은 절이다 (docs/11 D-9). 예전 판은 `WEBRTC_SIGNAL` 을
> `app:realtime` 의 WebSocket(`/ws`) 소관으로 두었다. **이 저장소(`app:matching`)는
> `WEBRTC_SIGNAL` 을 발행하지 않는다** — 15종을 세는 근거와 복구 책임을 남기려고 적는다.
>
> 개정 이력: 이 절은 처음에 `POST` 를 받고 발행하는 앱을 `app:platform` 으로 적었다(D-9). 같은 날 방을
> `app:room` 으로 분리하면서(docs/11 D-16) 아래와 같이 바꿨다 — 지금 방에 누가 있는지를 아는 앱이
> `app:room` 이다. 확인하는 대상도 자동 매칭 파티방에서는 "같은 파티원", 게시판 방에서는 "같은 방에 들어와
> 있는 사람"이다(D-11).

- **받기** — 다른 알림과 같은 SSE 다. 같은 envelope 에 `type` 이 `WEBRTC_SIGNAL` 이다.
  `app:realtime` 은 `type` 을 해석하지 않고 그대로 흘려보내므로 바뀌는 것이 없다.
- **보내기** — 클라이언트가 `app:room` 에 REST `POST` 한다. `app:room` 이 보내는 사람과
  받는 사람이 **같은 방에 들어와 있는지(같은 파티원인지) 확인한 뒤** `qm:pubsub:push:{상대 userId}` 에
  publish 한다. 방 안에 누가 있는지를 아는 앱이 `app:room` 이기 때문이다. 발행 주체는 `app:room` 이다.
- **순서** — 여러 `POST` 는 도착 순서가 보장되지 않는다. 받는 쪽은 offer 가 오기 전에 도착한
  ICE 후보를 모아 두어야 한다. 통화 시작 시 ICE 후보마다 `POST` 가 나가므로 HTTP/2 를 전제한다.
- **놓친 시그널은 클라이언트가 복구한다.** 받는 쪽 SSE 가 끊긴 순간의 시그널은 사라진다
  (WebSocket 이어도 같다). 시그널은 서버에 저장되지 않으므로 위 "재연결"의 상태 조회로는
  되찾을 수 없다. 복구의 기준점은 서버에 저장된 **파티원 목록**(게시판 방은 방에 있는 사람 목록)이다.
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
  **후보**일 뿐이다.) 이 발췌본의 `openapi.yaml` 에는 들어오지 않는다 — `app:room` 소관이다.
- `WEBRTC_SIGNAL` 의 `payload` 스키마. 정해야 할 항목: 보낸 사람 식별자 / 파티 식별자 /
  종류(offer · answer · ICE candidate) / SDP 또는 candidate 본문 / 재협상 시도를 구분할 식별자.

---

## 서버 간 이벤트 — SQS FIFO

알림(Redis Pub/Sub)과 **성격이 다르다.** 놓치면 데이터가 어긋나므로 내구성·재시도·DLQ·
순서 보장이 필요하다 (docs/14 §7).

**`app:matching` 이 걸린 큐는 0 개다** — 생산도 소비도 하지 않는다 (docs/11 D-42, 2026-09-27).

| 큐 | 이 앱의 역할 | MessageGroupId | 비고 |
|---|---|---|---|
| ~~`ProposalConfirmed.fifo`~~ | ~~**생산자** — 확정된 제안을 내보내면 `app:platform` 이 소비해 파티를 만든다~~ | — | **만들지 않는다** (docs/11 D-42). 확정된 파티는 `app:platform` 이 이 앱의 파티 HASH `qm:party:{partyId}` 를 **직접 읽어** 만든다 — 아래 개정 이력 |
| ~~`BlockChanged.fifo`~~ | ~~**소비자** — 차단 목록 갱신을 받아 `qm:block:{userId}` read model 을 고친다~~ | — | **폐기됐다 — 만들지 않는다** (docs/11 D-12). 차단은 `app:platform` 이 `social.blocks`(2026-09-26 부터 `public.blocks` — D-34)에 저장하면 끝이고, 이 앱은 배정 때 그 테이블을 직접 조회해 INV-6 을 지킨다 (D-1 · D-41) |

> 개정 이력: 예전 판은 "`app:matching` 이 걸린 큐는 2개다"라고 적고 `BlockChanged.fifo` 를 이 앱이
> **소비**하는 큐로 두었다(차단 쌍 단위 순서 보장 필수). 2026-09-19 에 1개로 바꿨다 (docs/11 D-12) —
> 선필터는 정확성을 책임지지 않고(D-2), 큐 하나와 그에 딸린 발행·소비·DLQ·멱등 처리가 통째로 없어진다.
>
> **`PartyClosed.fifo` 는 이 앱이 걸린 큐가 아니다.** 보내는 쪽도 받는 쪽도 `app:platform` 이고
> 이 앱은 읽지 않는다 (docs/11 D-13).

> **개정 이력: 2026-09-27 (contracts/README.md A-15, docs/11 D-42).** 남아 있던 1개 `ProposalConfirmed.fifo` 도 **만들지 않는다.**
> 이유 — SQS 를 고른 근거(앱마다 스키마를 나눠 DB 로 대화할 수 없었던 것, `BlockChanged` 의 순서 보장)가 D-34(스키마 하나) ·
> D-12(그 큐 폐기)로 둘 다 사라졌고, `app:platform` 은 이미 이 앱의 Redis 키를 읽는다(D-19 · D-29). 같은 DB · Redis 를 쓰는 두 앱이
> AWS 를 한 바퀴 도는 것은 장치만 늘린다. 2026-09-27 에 Flyway + `matching_outbox` 를 넣었다가 같은 날 되돌렸다.
>
> **대신 이렇게 간다.**
> 1. 전원 수락으로 확정되면 이 앱의 `cleanup-confirmed.lua` 가 파티 HASH **`qm:party:{partyId}`** 를 자기완결로 채우고 `MATCH_CONFIRMED {partyId}` 를 파티 전원에게 보낸다.
> 2. 클라이언트가 `app:platform` 의 **`POST /api/v1/match-parties/{partyId}/room`** 을 부른다 — 본문 없음, 201(방을 만듦)/200(들어감), 404 `MATCH_PARTY_NOT_FOUND` · 403 `NOT_PARTY_MEMBER` · 409 `IN_OTHER_ROOM` · 503 (`../platform/contracts/platform-api.md` "자동 매칭 파티의 방" · P-30, 2026-09-27).
> 3. platform 이 그 HASH 를 읽어(`status == CONFIRMED` 확인) 파티와 방을 만들고 파티원 전원에게 입장 표시 키 `qm:user:active-room:{userId}` 를 찍는다(D-19). 파티원 다섯이 다 눌러도 `partyId` 유일 키로 **한 번만** 만든다.
>
> **파티 HASH 의 필드가 계약이다** — 이름을 바꾸면 platform 이 조용히 깨진다(`redisKeys/SharedKeys` 의 경고와 같다).
>
> | 필드 | 뜻 |
> |---|---|
> | `status` | `CONFIRMED` 일 때만 읽어도 된다(`PENDING` 은 아직 제안 중, 없으면 아직 안 찬 파티) |
> | `confirmedAt` | 확정 시각, epoch millis. `HSETNX` 라 재실행이 옮기지 않는다 |
> | `game` | `LOL` / `VALORANT` / `PUBG` |
> | `modeKey` | 예 `RANKED_SOLO` — gameconfig `qm:gameconfig:{GAME}:{MODE}` 의 `{MODE}` |
> | `voicePreference` | `REQUIRED` / `NO_VOICE` |
> | `playPurpose` | `RANK_UP` / `NORMAL` / `FUN` |
> | `target` | 정원 |
> | `member:{userId}` | 값은 그 사람의 keyValue(LoL 포지션 · VALORANT 역할군 · PUBG 플랫폼). `{userId}` 는 사용자 번호의 십진 문자열. 인원 수 필드는 없다 — 이 필드를 센다 |
> | `tierLo` / `tierHi` | 티어를 보는 모드의 파티 허용 범위 — 티어 사다리 `qm:gameconfig:{GAME}:tier` 의 `ZRANK` 순번(0부터). 티어를 안 보는 모드는 `0/0` |
>
> **수명** — 파티 HASH 는 확정 뒤 **TTL 600초**(`queuemate.proposal.confirmed-party-ttl-seconds`). 그 안에 아무도 platform 을 부르지 않으면
> 파티가 증발한다(확정하고 아무도 안 들어온 파티라 잃어도 된다). 파티원의 활성 요청(`status=PARTY`)과 수락자 SET 은 **60초**
> (`confirmed-retention-seconds`) — 그 뒤 사용자는 새 매칭을 걸 수 있고, "한 번에 하나만" 은 platform 의 입장 표시 키가 맡는다.
>
> 아래 at-least-once · Redis Streams · transactional outbox 의 서술은 **큐를 두는 앱에 걸리는 원본 규칙**이고, 이 앱에는 지금 해당하는
> 큐가 없다. 서비스별 DB 로 진짜 갈라지는 날 `ProposalService#confirmed()` 자리에 큐를 넣으면 그때 다시 걸린다.

- 전달은 **at-least-once** 이므로 **소비자는 반드시 멱등해야 한다.**
- Redis Streams 로 대체하지 마라. 컨슈머 그룹으로 읽는 순간 순서가 깨진다 (docs/11 #26).
- outbox 는 **transactional outbox** 다. 확정 트랜잭션과 같은 트랜잭션에서 outbox 행을
  쓰고, relay 가 그것을 SQS 로 밀어낸다.

**구현 상태: 이 앱에는 SQS 도 outbox 도 없고, 두지 않는다** (docs/11 D-42). AWS SDK · Flyway 의존성이 `build.gradle` 에 없는 것이 맞다.

---

## 이 저장소가 전송하지 않는 것

- 파티 텍스트 채팅 본문 / 음성 미디어 — WebRTC DataChannel·audio track 을 탄다
- `WEBRTC_SIGNAL` — `app:room` 이 발행한다 (docs/11 D-16. D-9 는 `app:platform` 으로 적었다. **2026-09-25 부터 다시 `app:platform` — D-33**). 받는 길은 다른 알림과 같은 SSE 다
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
  (2026-09-27 — docs/11 D-42 로 모양이 바뀌었다. 파티는 `MATCH_CONFIRMED` 를 받은 클라이언트가 `app:platform` 의
  "이 매칭으로 파티 만들기" 를 **직접 불러** 만들어지므로 비동기 소비의 404 경합은 없어졌다. 대신 **그 진입점의 경로 ·
  응답이 미정**이고, 같은 파티의 다른 파티원이 먼저 만들었을 때 뒤에 누른 사람이 받는 답을 platform 이 정해야 한다.)
- `contracts/openapi.yaml` 에 **`GET /api/v1/events` 가 없다.**
- `openapi.yaml` 에 **`ErrorResponse` 스키마가 없다** (이 저장소 발췌본에는 구현에서
  역으로 적어 두었다).
- `openapi.yaml` 에 **`securitySchemes` 가 없다.** JWT bearer 정의가 빠져 있다.
  (2026-09-19: docs/11 D-14 로 access 토큰은 `Authorization` 헤더가 아니라 **쿠키**로 주고받는다. 채울
  정의는 bearer 가 아니라 cookie 방식이다. **2026-09-27: 쿠키 이름은 `qm_access` 로 정해졌고 이 발췌본의
  `openapi.yaml` 에는 `cookieAuth` 를 먼저 적었다 — contracts/README.md A-12. 원본에는 아직 없다.**)
