# Integration Checklist

## Contract
- [ ] frontend enums == OpenAPI enums
- [ ] backend DTO == OpenAPI
- [ ] WebSocket event names exact

## Realtime
- [ ] POST match request 201
- [ ] duplicate same user 409
- [ ] waiting UI receives queue event
- [ ] proposal reaches all members
- [ ] all accept creates one party
- [ ] decline returns valid users to queue

## Reservation
- [ ] 30min validation
- [ ] create/edit/delete slot indexes consistent
- [ ] overlap double booking 409
- [ ] proposal → matched reservation

## Party
- [ ] members same as proposal
- [ ] ready event
- [ ] WebRTC offer/answer/ICE
- [ ] DataChannel text
- [ ] leave cleanup

## Social
- [ ] friend flow
- [ ] block removes friendship if present
- [ ] block user never matched again
- [ ] recent player comes from completed party

## Operations
- [ ] Redis down = new matching fails closed
- [ ] actuator health
- [ ] invariant metric
