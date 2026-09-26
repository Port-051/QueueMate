<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 docs/03_MATCHING_ENGINE_SPEC.md -->
<!-- 커밋: 825d673 (git show feature/frontend:docs/03_MATCHING_ENGINE_SPEC.md) -->
<!-- 원문 verbatim. 수정하지 말 것 — 변경은 queueMate 원본에서 하고 다시 가져온다. -->

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

모든 참가자의 acceptance는 Redis HASH `qm:proposal:members:{proposalId}`
(`userId -> PENDING|ACCEPTED|DECLINED`)로 추적하고 Lua로 원자 집계한다
(docs/11 #28, docs/07 §5-1). DB 테이블로 별도 추적하지 않는다.
DB에는 전원 수락으로 **확정된 proposal만** 기록된다 (docs/11 #27).

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

1. 후보 탐색 — hot path. block 후보 필터링은 Redis read model `qm:block:{userId}`를
   O(1)로 조회한다. 이 단계는 결과적 일관성만 보장하며 INV-6의 보증 지점이 아니다.
2. **INV-6 최종 재검증** — 확정 대상 N명(파티 정원, 보통 2~5명)에 대해
   `shared_read.blocked_pairs` 뷰를 **동기로** 읽어 차단 쌍이 없는지 확인한다.
   한 쌍이라도 걸리면 이 조합을 폐기하고 후보 탐색으로 되돌아간다.
3. Redis atomic claim으로 사용자 N명을 모두 잠금
4. 한 명이라도 claim 실패하면 전체 rollback/retry
5. proposal 저장
6. queue에서 proposal 참가자를 제거

절대 `GET → 애플리케이션 판단 → SET`만으로 구현하지 않는다.
Lua script 또는 Redisson multi-lock/transaction 중 하나로 원자성을 보장한다.

### 두 단계의 결합 순서
**DB 재검증이 통과한 뒤에 Redis Lua atomic claim이 실행된다.** 순서를 뒤집지 않는다.

- 2단계 검증은 5단계 proposal 저장과 **같은 DB 트랜잭션** 안에서 수행한다. 뷰를 쓰는
  이유가 이것이다. 동기 REST 호출로 검증하면 검증과 claim 사이에 창이 다시 생겨
  TOCTOU를 못 막는다.
- 2단계와 3단계 사이에도 이론상 창이 남는다. 이 창은 claim 실패 경로로 닫는다.
  Redis claim이 실패하면 아무 변경 없이 전체를 rollback 하고 1단계부터 재시도하므로,
  재시도 시점의 최신 뷰로 다시 검증된다. 부분 확정 상태는 남지 않는다.
- 반대로 Redis claim이 성공한 뒤 DB 트랜잭션이 실패하면 claim된 guard key를
  compare-and-delete로 해제하고 참가자를 queue에 복귀시킨다 (대기 시작 시각 보존).
- 파티 확정은 초당 수 회 수준이라 이 동기 읽기 비용은 무시할 만하다. 후보 필터링과 달리
  hot path가 아니다.
- Redis 장애로 후보 필터링/claim이 불가능하면 새 proposal 생성은 fail-closed 한다 (INV-10).
  2단계 검증은 DB 경로이므로 Redis 장애와 독립이다.

## 8. Proposal
- 참가자 전원에게 동시에 전달
- TTL 존재
- 전원 accept → proposal CONFIRMED. Party 생성은 여기서 동기로 일어나지 않는다.
- 1명 decline → proposal DECLINED
- TTL 만료 → EXPIRED
- decline/expire 참가자는 조건이 유지되면 queue에 복귀 가능
- 기존 대기 시작 시각을 보존해 aging 손실을 막는다.

### CONFIRMED → Party 생성은 비동기다
`matching`이 CONFIRMED와 같은 트랜잭션에서 `matching.outbox`에 `ProposalConfirmed`를
기록하고, outbox relay가 **`ProposalConfirmed.fifo`(SQS FIFO)** 로 발행하면
`party`가 소비해 파티를 만든다 (docs/11 #21). `MessageGroupId`는 `proposalId`다.
`matching`과 `party`는 서로 다른 배포 단위이자 다른 스키마이므로 동기 호출로 잇지 않는다.

- CONFIRMED 시점과 party row 생성 시점 사이에 **전파 지연이 존재한다.** 클라이언트는
  CONFIRMED 직후 party가 즉시 조회된다고 가정하면 안 되고, 파티 준비 완료는 서버 이벤트로 받는다.
- 전달 보장은 at-least-once이므로 `party`의 소비는 `parties.proposal_id` UNIQUE 기준으로
  멱등해야 한다 (INV-3, INV-7).

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


---

<!-- 아래는 원문이 아니다. app:matching 저장소에서 덧붙인 주석이다. -->

> **이 저장소에서 바뀐 부분.** 위 본문의 `shared_read.blocked_pairs` 뷰 조회는
> 폐기됐다. 지금은 `social.blocks`를 직접 SELECT 하고, Redis 선필터는 보류했다.
> (2026-09-26 — 그 테이블은 `app:platform` 의 스키마가 `public` 하나로 합쳐져 **`public.blocks`** 가 됐고 두 칸은 bigint 다. 스키마별 롤도 없다 — docs/11 **D-34 · D-25**.)
> 근거와 대가는 `docs/11_DECISION_LOG.md`의 **D-1 / D-2**에 있다.
> 본문은 queueMate 원문이라 고치지 않는다 — 어긋나면 D-1이 우선한다.
