# 08. Test & Verification Harness

하네스의 목적은 UI가 동작하는지보다 **매칭이 절대 깨지지 않는지**를 증명하는 것이다.

## 1. Layers
### Unit
- game condition compatibility
- reservation time overlap
- state transition
- friendship/block rule

### Integration
Spring Boot + Testcontainers:
- PostgreSQL
- Redis

검증:
- repository constraint
- Redis atomic claim
- transaction boundary
- expired proposal cleanup

### Contract
- OpenAPI response shape
- WebSocket event envelope
- frontend mock fixtures와 backend DTO 일치

### E2E
Playwright:
- signup → onboarding → realtime match → proposal → party
- reservation create → manage → proposal
- friend request
- block → 동일 사용자 future match 없음

### Load / race
k6 + backend test endpoint/seed fixtures.

## 2. Mandatory invariant tests
### INV-1 duplicate request
100 concurrent `POST /match-requests` same user → exactly 1 success.

### INV-2 duplicate proposal claim
A와 호환되는 B~Z를 동시에 matcher가 claim → A active proposal exactly 1.

### INV-3 party capacity
동일 proposal confirm retry/duplicate accept → party member count never target size 초과.

### INV-4 all accept
N-1명 accept 상태에서 party 생성되지 않음.

### INV-5 expiration race
accept와 expiry가 동시에 발생 → final state 하나만 가능, expired proposal resurrection 금지.

### INV-6 block race
candidate 탐색 직후 block 생성 후 claim → final claim 직전에 block 재검증하여 same party 금지.

### INV-9 reservation double booking
같은 user가 overlapping reservation을 concurrent create → exactly 1 accepted.

## 3. Game fixtures
`harness/fixtures/`에 사람이 읽을 수 있는 JSON case를 둔다.

### LoL
- jungle + mid compatible
- jungle + jungle rejected when uniqueness true
- required voice + no voice reject

### VALORANT
- same role allowed
- diverse role higher tier

### PUBG
- aggressive + aggressive exact tier
- aggressive + survival lower tier but not hard reject unless policy config says so

## 4. Deterministic random
matching core에 `RandomSource` interface를 둔다.
- production: secure/normal random implementation
- tests: seeded random

동일 fixture는 항상 재현 가능해야 한다.

## 5. Reservation harness
- 30분 정렬 검증
- no overlap reject
- overlap exact playAmount match
- cross-day window
- timezone normalization
- update removes stale Redis slot indexes

## 6. WebRTC harness
브라우저 2~5개 context로:
- offer/answer/ICE signaling
- audio permission denied
- DataChannel open/close
- refresh/reconnect
- mute/unmute
- one peer leave

서버는 media 내용이 아닌 signaling 성공/실패만 관측한다.

## 7. k6 scenarios
- `harness/k6/realtime-match.js`
- `harness/k6/reservation-match.js`

테스트는 평균 응답시간만 보지 않는다.
최종적으로 invariant count를 DB/Redis query로 검증한다.

## 8. CI gate
merge 전 최소:
```text
frontend build
backend test
contract validation
core integration tests
```

release 전:
```text
Playwright critical flow
k6 race suite
Redis restart recovery drill
```
