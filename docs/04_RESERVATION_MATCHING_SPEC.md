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
reservation matching은 두 경로로 실행한다.
- 예약 create/update 직후 즉시 candidate scan
- periodic sweep으로 놓친 조합 재검사

PostgreSQL이 reservation source of truth이고 Redis는 slot index/claim용이다.

## 7. Redis indexing
각 예약을 포함되는 30분 slot bucket에 색인한다.

예:
```text
reservation:slot:LOL:SOLO_DUO_RANKED:20260829T2000
reservation:slot:LOL:SOLO_DUO_RANKED:20260829T2030
...
```

bucket은 reservation ID만 가진다. 상세 조건은 Redis cache 또는 DB 조회.

## 8. Proposal
예약도 realtime과 동일한 proposal acceptance 모델을 사용한다.
- all accept → MATCHED
- decline/expire → 해당 reservation을 ACTIVE로 되돌리거나 user cancellation policy에 따라 종료

## 9. Double booking invariant
한 사용자의 ACTIVE/PROPOSED/MATCHED reservation window가 다른 예약과 겹치면 생성/수정 요청을 reject한다.

## 10. Edit policy
- ACTIVE: 수정/취소 가능
- PROPOSED: 수정 금지, decline/cancel 후 다시 수정
- MATCHED: 조건 수정 금지. 취소만 가능하며 다른 참가자에게 이벤트 전달
