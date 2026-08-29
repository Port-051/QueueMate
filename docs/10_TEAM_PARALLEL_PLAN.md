# 10. Three-Person Parallel Plan

3명이 같은 파일을 잡지 않도록 **bounded context와 directory ownership을 분리**한다.

## Member 1 — Frontend / UX
Owner:
```text
frontend/**
design/** (read/reference)
```

책임:
- React routing/app shell
- 모든 페이지 UI
- match form: LoL/VALORANT/PUBG 조건
- realtime waiting/proposal UI
- reservation form/management UI
- party room UI
- friend/recent/me/settings UI
- REST API adapter + WebSocket client
- WebRTC browser client
- Playwright E2E

금지:
- backend matching rule 임의 변경
- UI에 스펙 외 조건 추가

## Member 2 — Matching / Reservation Core
Owner:
```text
backend/src/**/matching/**
backend/src/**/reservation/**
backend/src/**/gameconfig/**
backend/src/main/resources/redis/**
harness/fixtures/**
harness/k6/realtime-match.js
harness/k6/reservation-match.js
```

책임:
- GameModeConfig
- condition compatibility
- realtime Redis queue
- atomic claim
- proposal lifecycle
- reservation slot index/matcher
- state/invariant tests
- queue recovery

## Member 3 — Platform / Party / Social / Ops
Owner:
```text
backend/src/**/auth/**
backend/src/**/user/**
backend/src/**/party/**
backend/src/**/social/**
backend/src/**/realtime/**
backend/src/**/common/**
infra/**
docker-compose.yml
```

책임:
- auth/profile/game account
- DB schema/migrations
- party lifecycle
- WebSocket event channel
- WebRTC signaling
- friend/block/report/recent
- observability/rate limit
- deployment/operations

## Shared read-only contract zone
```text
contracts/**
docs/**
```
C0 이후 contract 변경은 독립 PR/commit으로 먼저 반영하고 세 명이 동의한 뒤 구현한다.

## Integration checkpoints
### C0 — Contract freeze
- openapi endpoints
- event names
- enum names
- DB IDs/UUID policy
- match condition schema

### C1 — Fake vertical slice
Frontend mock → match request → fake proposal → fake party까지 각 영역이 독립 동작.

### C2 — Real integration
Redis/Postgres 연결 후 real matcher → proposal → party.

### C3 — Safety loop
friend/block/report/recent + blocked candidate exclusion.

### C4 — Reservation
reservation create/index/matcher/proposal/management.

### C5 — Harness gate
race, E2E, WebRTC reconnect, outage runbook.

## Merge policy
- `main` always runnable.
- 각 member branch는 자신의 owner directory 중심.
- shared contract 변경과 feature 구현을 한 giant commit에 섞지 않는다.
