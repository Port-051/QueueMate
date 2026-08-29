# 09. Operations Strategy

## 1. Deployment shape
초기 운영은 modular monolith를 유지한다.

```text
Browser
  → CDN/static React
  → Spring Boot API (1 logical service, N instances 가능)
      → PostgreSQL
      → Redis
      → WebSocket signaling
Browser ↔ Browser WebRTC
Browser → TURN fallback when direct connection fails
```

## 2. Environments
- local
- staging
- production

운영 DB/Redis를 개발자가 로컬에서 직접 사용하지 않는다.

## 3. Observability
필수 structured log fields:
```text
traceId
userId hashed/internal
requestId
proposalId
partyId
reservationId
game
mode
stateFrom
stateTo
errorCode
```

로그에 저장 금지:
- 비밀번호/token
- WebRTC audio
- DataChannel chat body

## 4. Metrics
### Matching
- queue size by game/mode
- candidate count distribution
- time to proposal
- time to confirmed party
- proposal accept/decline/expire
- matcher claim conflict count

### Reservation
- active reservations
- reservation matched/expired/cancelled
- window overlap candidate count

### Party
- WebSocket connected
- signaling error
- WebRTC connection established client telemetry
- ready completion

### Safety
- friend created
- block created
- report created

### Correctness
`invariant_violation_total{type=...}`
모든 invariant violation은 Sev-1 후보로 취급한다.

## 5. Feature flags
DB config:
```text
game active
mode active
realtime matching active
reservation matching active
voice active
```

게임별 대기자가 부족한 상황에서 mode를 운영적으로 닫을 수 있어야 한다.

## 6. Runbooks
### Redis outage
1. health 상태 RED
2. new realtime/reservation proposal creation 차단
3. API는 `MATCHING_TEMPORARILY_UNAVAILABLE`
4. active party WebRTC는 유지
5. Redis 복구 후 active DB records로 queue rebuild job 수행

### PostgreSQL outage
1. auth/profile/reservation writes 차단
2. block relation을 검증할 수 없으면 새 proposal 생성 fail-closed
3. active media/signaling session은 가능 범위에서 유지

### Matcher bug / invariant violation
1. matching feature flag off
2. queue write 중단
3. incident snapshot: relevant Redis keys + DB rows
4. 재현 harness case 추가
5. fix 후 load/race suite 통과 전 재개 금지

### WebRTC failure spike
1. signaling/API 정상 여부 분리 확인
2. TURN reachability 확인
3. party room에 음성 장애 표시
4. 매칭/친구/ID 확인 기능은 유지

## 7. Data lifecycle
- account/friend/block/report/history: DB retention policy에 따름
- active Redis state: TTL + cleanup
- 음성/텍스트 DataChannel 내용: 서버 저장하지 않음

## 8. Backup
- PostgreSQL managed backup 또는 정기 dump
- restore rehearsal를 운영 체크리스트에 포함
- Redis는 source of truth가 아니므로 DB backup과 동일하게 취급하지 않음

## 9. Cost control
- 단일 Spring Boot logical service 유지
- 불필요한 message broker/search engine/object storage 금지
- media를 서버가 transcoding하지 않음
- WebRTC direct path 우선, TURN은 fallback
- 로그 cardinality 제한
