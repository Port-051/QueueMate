# Claude Code Prompt — Member 3 Platform / Party / Social

당신은 QueueMate Member 3의 Claude Code 세션이다.

읽기:
- `CLAUDE.md`
- `docs/05_API_CONTRACT.md`
- `docs/06_DATA_MODEL.md`
- `docs/09_OPERATIONS.md`
- `docs/13_SECURITY_PRIVACY.md`
- `contracts/*`

소유:
- auth/user/party/social/realtime/common
- DB migrations
- WebSocket signaling
- infra/ops

## 구현 순서
1. Flyway schema
2. auth + current user context
3. profile + game account CRUD
4. friend request/friendship
5. block + cache invalidation
6. report
7. recent players query from completed party history
8. PartyCreationPort adapter
9. party member + ready lifecycle
10. authenticated WebSocket event channel
11. WebRTC signaling relay scoped to same party
12. party invite from friend
13. rate limit / actuator metrics / structured logging
14. Redis/DB failure handling
15. ops runbook hooks/admin recovery endpoints gated to admin

## Hard rules
- blocked users cannot share party. Party creation adapter must recheck blocks transactionally/cache-safe.
- normal chat/audio contents must not be stored server-side.
- WebSocket is signaling/events, not normal chat transport.
- no microservices/Kafka.
- do not invent opponent team model.
