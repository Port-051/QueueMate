# WebSocket Event Contract

Endpoint: `/ws`
인증된 session만 연결한다.

## Handshake authentication

브라우저 WebSocket API는 커스텀 헤더를 붙일 수 없다. access token은
`Sec-WebSocket-Protocol` subprotocol로 넘긴다.

```js
new WebSocket("wss://host/ws", ["queuemate.v1", "bearer." + accessToken])
```

- 첫 번째 항목은 프로토콜 버전, 두 번째는 `bearer.<access token>`이다.
- 서버는 선택한 subprotocol로 `queuemate.v1`만 되돌려준다. 토큰은 되돌려주지 않는다.
- 토큰이 없거나 유효하지 않으면 handshake 단계에서 401로 끊는다. 연결 후 인증하지 않는다.
- query string(`?token=`)을 쓰지 않는다. access log와 프록시 로그에 token이 남아
  `docs/09 §3`의 로그 금지 항목을 위반한다.

access token이 만료되면 서버가 연결을 끊는다. 클라이언트는 재발급 후 다시 연결한다.

## Envelope
```json
{
  "type": "MATCH_PROPOSAL_CREATED",
  "eventId": "uuid",
  "occurredAt": "2026-08-29T09:00:00Z",
  "payload": {}
}
```

## Connection snapshot

연결이 맺어지면 서버가 `SESSION_SNAPSHOT`을 먼저 한 번 보낸다.

WebSocket은 끊긴 동안 서버가 보낸 이벤트를 이어받는 수단이 없다. 재연결한 클라이언트는
그 사이 무엇이 바뀌었는지 모른다. 이벤트를 보관했다가 재전송하는 대신 현재 상태를
다시 알려주는 방식을 쓴다. 멱등하고 보관 기간을 정할 필요가 없다.

```json
{
  "type": "SESSION_SNAPSHOT",
  "eventId": "uuid",
  "occurredAt": "2026-09-01T09:00:00Z",
  "payload": {
    "parties": [
      {
        "id": "uuid",
        "game": "LOL",
        "modeKey": "SOLO_DUO",
        "targetSize": 2,
        "status": "OPEN",
        "members": [ { "userId": "uuid", "nickname": "...", "ready": false } ]
      }
    ]
  }
}
```

- 첫 연결과 재연결을 구분하지 않는다. 언제나 같은 것을 보낸다.
- 탭마다 보낸다. 세션 단위이지 사용자 단위가 아니다.
- `payload`는 영역이 늘어나면 키가 추가된다. matching과 reservation이 각자 채운다.
  클라이언트는 모르는 키를 무시한다.

## Server → Client
- `SESSION_SNAPSHOT`
- `MATCH_QUEUE_UPDATED`
- `MATCH_PROPOSAL_CREATED`
- `MATCH_PROPOSAL_EXPIRED`
- `MATCH_CONFIRMED`
- `MATCH_CANCELLED`
- `RESERVATION_UPDATED`
- `RESERVATION_PROPOSAL_CREATED`
- `PARTY_MEMBER_JOINED`
- `PARTY_MEMBER_LEFT`
- `PARTY_READY_CHANGED`
- `PARTY_CLOSED`
- `FRIEND_REQUEST_RECEIVED`
- `FRIEND_REQUEST_UPDATED`
- `PARTY_INVITE_RECEIVED`
- `WEBRTC_SIGNAL`

## Client → Server
WebRTC signaling only:
```json
{
  "type": "WEBRTC_SIGNAL",
  "partyId": "uuid",
  "targetUserId": "uuid",
  "signalType": "OFFER|ANSWER|ICE",
  "data": {}
}
```

Server validates:
- sender authenticated
- sender in party
- target in same party

## Not transported over server WebSocket
- normal party text chat body
- voice media

Those use WebRTC DataChannel/audio track.
