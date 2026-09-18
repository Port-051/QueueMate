<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 contracts/events.md (110줄) -->
<!-- 커밋: 825d673 -->
<!-- ★ 발췌본이다. app:matching 이 발행하는 이벤트와 그 전달 규약만 옮겼다. -->

# Server Event Contract — app:matching 발췌

서버→클라 전송은 **2종으로 분리**한다 (docs/11 #22).

| 전송 | 엔드포인트 | 방향 | 담는 것 |
|---|---|---|---|
| SSE | `GET /api/v1/events` | 서버 → 클라 단방향 | 14종 |
| WebSocket | `/ws` | 양방향 | `WEBRTC_SIGNAL` 1종만 |

> **연결을 받는 것은 `app:realtime` 하나다.**
> 이 저장소(`app:matching`)는 SSE도 WebSocket도 열지 않는다.
> 여기서 하는 일은 **Redis Pub/Sub 에 publish 하는 것까지**이고,
> 그 뒤 `Redis Pub/Sub → app:realtime → SSE` 는 다른 앱의 책임이다.
> 이 저장소에 `SseEmitter` / `WebSocketConfig` / `@MessageMapping` 을 넣지 마라.

## `app:matching` 이 발행하는 5종

전체 14종 중 이 앱이 발행 주체인 것만 옮긴다.
나머지 9종(`RESERVATION_*` 2종 = `app:reservation-batch`,
`PARTY_*`·`FRIEND_*` 7종 = `app:platform`)은 이 저장소 소관이 아니다.

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
14종 전부 payload 스키마가 비어 있어서, 구현이 먼저 정하고 여기에 적어 둔 것이다.
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

### heartbeat
- 서버는 15~30초마다 heartbeat(코멘트 라인)를 보낸다.
- 프록시 idle timeout 보다 짧아야 한다 (Stage 2 ALB 기준 300초).

### 순서 보장 범위
- **스트림 간 인과 순서는 보장하지 않는다.** 알림은 Redis Pub/Sub fanout 이고,
  이를 유발한 도메인 이벤트는 SQS FIFO 의 서로 다른 `MessageGroupId` 를 탄다.
  예를 들어 `MATCH_CONFIRMED` 와 `PARTY_MEMBER_JOINED` 의 도착 순서는 고정이 아니다.
- 따라서 **클라이언트 핸들러는 멱등하고 순서에 무관해야 한다.**
  이벤트를 상태 전이 트리거가 아니라 "다시 조회하라"는 신호로 다루는 편이 안전하다.
- 알림은 휘발성이라 재전송 보장이 없다. 놓친 상태는 REST 로 복구한다.

---

## 서버 간 이벤트 — SQS FIFO

알림(Redis Pub/Sub)과 **성격이 다르다.** 놓치면 데이터가 어긋나므로 내구성·재시도·DLQ·
순서 보장이 필요하다 (docs/14 §7).

`app:matching` 이 걸린 큐는 2개다.

| 큐 | 이 앱의 역할 | MessageGroupId | 비고 |
|---|---|---|---|
| `ProposalConfirmed.fifo` | **생산자** — 확정된 제안을 내보내면 `app:platform` 이 소비해 파티를 만든다 | `proposalId` | DLQ + `maxReceiveCount` |
| `BlockChanged.fifo` | **소비자** — 차단 목록 갱신을 받아 `qm:block:{userId}` read model 을 고친다 | 차단 쌍 | 순서 보장 필수 — `BlockCreated`/`BlockRemoved` 가 역전되면 INV-6 이 뒤집힌다 |

- 전달은 **at-least-once** 이므로 **소비자는 반드시 멱등해야 한다.**
- Redis Streams 로 대체하지 마라. 컨슈머 그룹으로 읽는 순간 순서가 깨진다 (docs/11 #26).
- outbox 는 **transactional outbox** 다. 확정 트랜잭션과 같은 트랜잭션에서 outbox 행을
  쓰고, relay 가 그것을 SQS 로 밀어낸다.

**구현 상태: 둘 다 미구현이다.** AWS SDK 의존성이 `build.gradle` 에 없다.

---

## 이 저장소가 전송하지 않는 것

- 파티 텍스트 채팅 본문 / 음성 미디어 — WebRTC DataChannel·audio track 을 탄다
- `WEBRTC_SIGNAL` — `app:realtime` 의 `/ws` 소관

## 미해결 계약 구멍 (매칭 해당분)

원본 `contracts/events.md` 가 직접 지적한 것 중 이 저장소에 걸리는 것만 옮긴다.
**구현 전에 채워야 하며, 임의로 지어내지 않는다.**

- SSE 14종의 **payload 가 전부 미정의다.** envelope 만 있고 `payload` 스키마가 없다.
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
