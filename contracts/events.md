# WebSocket Event Contract

Endpoint: `/ws`
인증된 session만 연결한다.

## Envelope
```json
{
  "type": "MATCH_PROPOSAL_CREATED",
  "eventId": "uuid",
  "occurredAt": "2026-08-29T09:00:00Z",
  "payload": {}
}
```

## Server → Client
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
