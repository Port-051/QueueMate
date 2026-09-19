# 실시간 매칭 — Redis 구조 요약

> **개정 안내 (2026-09-19).** 예전 판은 초기 설계의 구조를 적었다 — 키 3개(`mq:...` 대기 명단 / `req:{id}` 주문 메모 JSON /
> `claim:{id}` 포스트잇), "대기 명단을 읽고(`ZRANGE`) 메모를 비교해(`MGET`) 5명을 찾아 Lua 로 동시에 선점한 뒤 Core API 에
> `PUBLISH`", "취소·만료·파티확정은 Core API 가 명단에서 지운다". **그 구조는 통째로 바뀌어서 문서 전체를 다시 썼다.**
> 대기열과 후보 비교가 없어졌고(docs/11 #32·#33), Core API 라는 서비스가 없으며(docs/11 #15), 제안·수락·확정까지 Redis 위에서
> `app:matching` 이 한다(docs/11 #27·#28). 옛 본문은 git 이력에 있다.
> **이 문서는 요약이다. 기준은 `CLAUDE.md` §3·§4 와 `redisKeys/SharedKeys.java`, `backend/src/main/resources/redis/` 의 Lua 다.**
> 출처 표기는 저장소 루트(`matching/`) 기준이다.

## Redis에 넣는 것

식당으로 비유하면:

| Redis 키 | 식당으로 치면 | 내용 |
|---|---|---|
| `qm:user:active-request:{userId}` HASH | 번호표 | 한 사람에 한 장(INV-1). 어느 테이블(`partyId`)에 앉았는지가 적힌다 |
| `qm:party:{partyId}` HASH | 테이블 | 정원(`target`), 앉은 사람(`member:{userId} = 핵심 조건 값`), 제안 상태(`status` / `expiresAt`) |
| `qm:party:open:{game}:{mode}:{voice}:{purpose}:needs:{keyValue}` ZSET | "이 자리 비었음" 팻말 | 그 값의 자리가 **아직 빈** 테이블만 모여 있다. 티어를 보는 모드는 뒤에 `:{tier}` 가 더 붙는다 |
| `qm:proposal:accepts:{partyId}` SET | 수락 도장 | 그 제안을 수락한 사람 |
| `qm:proposal:pending` ZSET | 시한 목록 | 열려 있는 제안과 그 만료 시각 |
| `qm:gameconfig:*` | 식당 규칙 | 모드별 정원·중복 허용 여부·티어 규칙, 티어 사다리, 티어 허용 범위 표. **앱은 읽기만 한다** |

**대기 명단이 없다.** 혼자 기다리는 사람도 "1인 테이블"이다. 대기 상태는 "줄 선 사람"이 아니라 "아직 안 찬 테이블"이다.

## 매칭 서비스가 하는 일

1. 번호표를 뽑는다 — `claim-request.lua`. 이미 한 장 있으면 `409`
2. 내 값의 자리가 빈 테이블을 팻말에서 하나 꺼낸다. 없으면 새 테이블을 만들고 혼자 앉는다 — `create-or-check-party*.lua`
3. 꺼낸 테이블에 나와 차단 관계인 사람이 있는지 본다 — 자바, DB `social.blocks` (코드는 있으나 DB 스키마가 아직 없어 실제로는 동작하지 않는다. 차단 검증은 미구현 상태다 — CLAUDE.md §4 INV-6)
4. 앉는다 — `join-party*.lua`. 정원이 차면 팻말을 전부 내리고 **제안을 연다**(`status=PENDING`, 시한 기본 20초)
5. 전원이 수락하면 확정한다 — `accept-proposal.lua`. 한 명이라도 거절·무응답이면 제안만 깨지고 테이블은 남는다
6. 단계마다 그 테이블의 사람들에게 알림을 보낸다 — `PUBLISH qm:pubsub:push:{userId}`. SSE 로 배달하는 것은 다른 앱(`app:realtime`)이다

**비교를 하지 않는다.** 조건(게임·모드·음성·목적·핵심 조건 값)이 팻말의 **키 이름**에 들어 있어서, 같은 팻말 아래 있으면
조건이 같음이 보장된다. 그래서 후보를 훑는 루프가 없고 맨 앞 하나만 꺼내면 끝이다.

**매칭하는 동안 DB 는 3번에서 한 번만 본다.** 차단 관계(`social.blocks`)는 `app:platform` 의 DB 가 원본이고, 지금 구현에서
`matching` 이 DB 를 치는 지점은 그것 하나뿐이다(docs/11 D-1·D-2). 확정된 매칭을 DB 에 쓰는 것(`match_proposals` +
`proposal_members` + `outbox`, docs/11 #27)은 결정만 있고 아직 구현되지 않았다. 2번과 4번 사이의 틈은 후보 풀 락(`qm:lock:pool:*`)이 막는다.

취소·만료·확정 뒷정리도 **이 앱이 직접 한다** — 취소는 `leave-party.lua`, 만료는 1초마다 도는 스위퍼 + `expiry-proposal.lua`,
확정 뒷정리는 `cleanup-confirmed.lua`. 확정된 사람의 번호표는 지우지 않고 `status=PARTY` 로 남긴다(지우면 그 순간 새 매칭을
걸 수 있어 한 사람이 두 파티에 속한다).

## 요청에 실려 오는 것

```json
{
  "userId": "alice",
  "game": "LOL",
  "modeKey": "RANKED_SOLO",
  "tier": "GOLD_2",
  "keyCondition": { "type": "POSITION", "value": "MID" },
  "voicePreference": "REQUIRED",
  "playPurpose": "RANK_UP"
}
```

| 필드 | 뜻 |
|---|---|
| `userId` | 사용자 id(로그인 아이디 문자열). **임시 필드다** — JWT 인증이 붙으면 요청에서 사라진다 |
| `game` | `LOL` / `VALORANT` / `PUBG` |
| `modeKey` | 게임 모드. 목표 인원은 여기서 정해진다(사용자가 고르지 않는다) |
| `tier` | 티어를 보는 모드에서만 필요하다. 조건이 아니라 자격이다. 지금은 자기신고다 |
| `keyCondition` | 게임별 핵심 조건 **하나** — LoL 포지션(`POSITION`) / VALORANT 역할군(`ROLE`) / PUBG 플랫폼(`PLATFORM`) |
| `voicePreference` | `REQUIRED` / `NO_VOICE` |
| `playPurpose` | `RANK_UP` / `NORMAL` / `FUN` |

옛 판의 메모에 있던 `targetSize` · `playMinutes` · `subPositions` · `allowedTierMinOrder` / `allowedTierMaxOrder` 는 없다 —
조건은 게임당 정확히 4개이고(CLAUDE.md §2), 인원은 modeKey 에(docs/11 #31), 티어 허용 범위는 gameconfig 의 표에 있다.

## 핵심

**조건이 키 이름에 들어 있어서 비교할 것이 없다.** 그리고 진행 중인 매칭 상태는 Redis 에만 있다 — DB 는 확정된 것만 안다
(docs/11 #27). Redis 가 죽으면 새 매칭을 받지 않고(fail-closed, INV-10), 살아난 뒤 사용자가 다시 요청한다(docs/11 #29).
