# 03. Realtime Matching Engine Spec

## 1. Definition
QueueMate는 추천 목록을 주는 서비스가 아니다.

```text
Hard filtering
→ Compatibility tiering
→ 같은 tier 내부 random selection
→ Proposal
```

즉 **Compatible Random Matching**이다.

## 2. MatchRequest state
```text
QUEUED
PROPOSED
MATCHED
CANCELLED
EXPIRED
```

허용 전이:
```text
QUEUED → PROPOSED
QUEUED → CANCELLED
PROPOSED → QUEUED       (decline/expire 후 요청 유지 시)
PROPOSED → MATCHED
PROPOSED → CANCELLED
```

## 3. Proposal state
```text
PENDING
CONFIRMED
DECLINED
EXPIRED
CANCELLED
```

모든 참가자의 acceptance를 `proposal_acceptance`로 별도 추적한다.

## 4. Hard filters
모든 게임 공통:
- same game
- same modeKey or config-defined compatible mode
- game eligibility compatible
- not blocked either direction
- not same user
- no other active proposal
- no other active realtime request conflicting with this claim
- target party size exactly respected

Game-specific:
- LoL: role uniqueness config가 true면 primary position 중복 금지
- VALORANT: 역할 중복 허용
- PUBG: play style 불일치만으로 hard reject하지 않음

## 5. Compatibility tiers
숫자 가중치를 임의로 박지 않는다. 설명 가능한 tier를 사용한다.

예:
- Tier 0: key condition + voice + purpose 모두 최적 호환
- Tier 1: key condition + voice 호환, purpose 다름
- Tier 2: key condition 호환, voice optional 범위, purpose 다름

Hard condition은 어떤 tier에서도 완화 금지.
동일 tier에 여러 후보가 있으면 deterministic seed를 주입 가능한 random으로 선택한다.

## 6. Aging
오래 기다린 요청을 candidate ordering에서 우선한다. 정확한 시간 threshold는 `GameMatchPolicy` config로 관리한다.

목적:
- starvation 방지
- low-liquidity 상황에서 soft condition 완화

## 7. Atomic claim
후보 계산과 실제 claim은 분리해서 생각한다.

1. 후보 탐색
2. Redis atomic claim으로 사용자 N명을 모두 잠금
3. 한 명이라도 claim 실패하면 전체 rollback/retry
4. proposal 저장
5. queue에서 proposal 참가자를 제거

절대 `GET → 애플리케이션 판단 → SET`만으로 구현하지 않는다.
Lua script 또는 Redisson multi-lock/transaction 중 하나로 원자성을 보장한다.

## 8. Proposal
- 참가자 전원에게 동시에 전달
- TTL 존재
- 전원 accept → CONFIRMED → Party 생성
- 1명 decline → proposal DECLINED
- TTL 만료 → EXPIRED
- decline/expire 참가자는 조건이 유지되면 queue에 복귀 가능
- 기존 대기 시작 시각을 보존해 aging 손실을 막는다.

## 9. Party size
party size는 `GameModeConfig.targetPartySize`가 결정한다.
클라이언트가 임의로 정원을 보내지 않는다.

예:
- LoL Solo/Duo: 2
- PUBG Duo: 2
- PUBG Squad: 4
- VALORANT team mode: config value

## 10. No opponent model
QueueMate의 Match/Party 모델에는 `opponent`, `enemyTeam`, `versusTeam` 개념을 두지 않는다.
한 MatchProposal은 **함께 플레이할 하나의 party**만 의미한다.
