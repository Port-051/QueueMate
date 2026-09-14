<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 docs/04_RESERVATION_MATCHING_SPEC.md -->
<!-- 커밋: 825d673 (git show feature/frontend:docs/04_RESERVATION_MATCHING_SPEC.md) -->
<!-- 원문 verbatim. 수정하지 말 것 — 변경은 queueMate 원본에서 하고 다시 가져온다. -->

# 04. Reservation Matching Spec

## 1. Principle
예약 매칭은 별도 매칭 제품이 아니다.

> `기존 MatchCondition + 플레이 가능한 시간 + 플레이할 양`

만 추가한다.

## 2. Reservation fields
```text
id
userId
baseCondition
availableFrom
availableTo
playAmount: ONE_GAME | TWO_PLUS
status
createdAt
updatedAt
```

시간은 사용자 locale로 입력하되 서버 저장은 UTC Instant로 정규화한다.
입력 start/end는 30분 단위만 허용한다.

## 3. State
```text
ACTIVE
PROPOSED
MATCHED
CANCELLED
EXPIRED
COMPLETED
```

## 4. Hard reservation compatibility
- base match hard filters 통과
- availability window overlap 존재
- playAmount 동일
- 같은 user가 아님
- block 관계 없음
- 같은 시간대에 이미 MATCHED된 예약 없음

`availableFrom/To`는 새로운 preference가 아니라 hard availability constraint다.

## 5. Scheduled start
호환되는 모든 사용자의 window 교집합에서 가장 이른 30분 slot을 `scheduledStart` 후보로 정한다.

## 6. Matching execution
reservation matching은 **1분 주기 배치 단일 경로**로만 실행한다 (docs/11 #23).
등록/수정 직후의 즉시 candidate scan은 하지 않는다.

- 실행 주체는 `app:reservation-batch`다. 다른 앱은 예약 매칭을 실행하지 않는다.
- 예약 REST(`/api/v1/reservations` 등록·조회·수정·취소, INV-9 검증)는 `app:platform`이
  서빙한다. `module:reservation`은 라이브러리이므로 두 앱이 함께 의존하며,
  역할은 `app:platform` = 예약 데이터 CRUD / `app:reservation-batch` = 예약 짝 찾기로 갈린다.
- 진입점이 하나뿐이므로 같은 매칭 로직에 대한 race 조합과 테스트 조합이 반으로 준다.
  예약은 즉시성이 요구되지 않는 기능이라 두 경로를 유지할 이유가 없다.
- 등록/수정은 DB에 쓰고 끝난다. 매칭은 다음 배치 주기에 일어난다.

PostgreSQL이 reservation source of truth이고 Redis는 claim용이다.

## 6-1. 최소 리드타임과 시간 기반 tier 완화
### 최소 리드타임 30분
슬롯 시작까지 남은 시간이 **30분 미만이면 등록을 거부한다 (`400`).**
배치 주기가 1분이므로 리드타임이 짧으면 매칭 성사 자체를 보장할 수 없다.
그 이하 리드타임은 실시간 매칭이 담당한다.

- 검증 기준 시각은 `availableFrom`(첫 슬롯 시작)이다.
- 수정 시에도 같은 규칙을 적용한다.

### 시간 기반 tier 완화
슬롯 시작까지 남은 시간이 줄수록 성사를 우선해 tier를 넓힌다.
docs/03 §5의 tier 정의를 그대로 쓰고, hard condition은 어떤 구간에서도 완화하지 않는다.

```text
남은 시간 > 2시간        → Tier 0만
2시간 ≥ 남은 시간 > 30분  → Tier 1까지
30분 ≥ 남은 시간         → Tier 2까지
```

## 6-2. 배치 실행 모델
- **주기**: 내부 1분(`queuemate.reservation.batch.interval-ms: 60000`).
  최소 리드타임은 `queuemate.reservation.batch.min-lead-minutes: 30`.
- **인스턴스 수**: 상시 1개. 스케일아웃 금지 (docs/11 #24). 늘려도 처리량이 늘지 않고
  같은 예약을 두 인스턴스가 집는 헛일만 는다.
- **락**: 전역 `qm:lock:reservation-batch` 한 줄. 단일 인스턴스이므로 실행이 겹쳐도
  atomic claim이 INV-2/INV-7을 보장한다. 겹침은 정합성 사고가 아니라 헛일일 뿐이다.
- **진입점**: `runOnce()` 코어 + `BATCH_MODE=daemon|oneshot`.
  `daemon`은 주기 루프, `oneshot`은 1회 실행 후 종료코드 0(성공)/1(실패)로 끝난다.
  나중에 CronJob/RunTask로 전환해도 코드 변경이 없다.
- **실패 처리**: 1회 실행 실패는 다음 주기에 재시도한다. 부분 성사 상태를 남기지 않는다.
  연속 실패는 `reservation_batch_runs_total{result=failure}`로 관측한다.
- **알람**: 배치가 안 도는 것은 사용자 에러가 나지 않는 조용한 장애다.
  `reservation_batch_last_success_epoch_seconds` 기반 deadman 감시를 필수로 둔다
  (docs/09 §6 예약 배치 미실행).

## 7. Redis indexing
각 예약을 포함되는 30분 slot bucket에 색인한다.

예:
```text
reservation:slot:LOL:SOLO_DUO_RANKED:20260829T2000
reservation:slot:LOL:SOLO_DUO_RANKED:20260829T2030
...
```

bucket은 reservation ID만 가진다. 상세 조건은 Redis cache 또는 DB 조회.

**이 인덱스는 제거 후보다 (docs/11 D7).** 배치는 1분 주기의 콜드 패스라
인덱스 걸린 DB 쿼리만으로 충분할 가능성이 높다. 실측 후 제거 여부를 정한다.

## 8. Proposal
예약도 realtime과 동일한 proposal acceptance 모델을 사용한다.
- all accept → MATCHED
- decline/expire → 해당 reservation을 ACTIVE로 되돌리거나 user cancellation policy에 따라 종료

`RESERVATION_UPDATED` / `RESERVATION_PROPOSAL_CREATED` 이벤트의 발행 주체는
`app:reservation-batch`다 (`contracts/events.md`).

## 9. Double booking invariant
한 사용자의 ACTIVE/PROPOSED/MATCHED reservation window가 다른 예약과 겹치면 생성/수정 요청을 reject한다.

## 10. Edit policy
- ACTIVE: 수정/취소 가능
- PROPOSED: 수정 금지, decline/cancel 후 다시 수정
- MATCHED: 조건 수정 금지. 취소만 가능하며 다른 참가자에게 이벤트 전달
