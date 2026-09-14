# 실시간 매칭 — Redis 구조 요약

## Redis에 넣는 건 딱 3개

식당 대기로 비유하면:

| Redis 키 | 식당으로 치면 | 내용 |
|---|---|---|
| `mq:...` | 대기 명단 | 누가 먼저 왔는지 순서만 |
| `req:10482` | 주문 메모 | 몇 명이서, 어떤 조건으로 원하는지 |
| `claim:10482` | "배정 중" 포스트잇 | 지금 이 사람 파티 짜는 중이니 건들지 마 |

## 매칭 서비스가 하는 일은 이게 전부

1. 대기 명단 쭉 읽는다 — `ZRANGE`
2. 메모 보고 같이 앉힐 5명 찾는다 — `MGET` → 조건 비교
3. 5명한테 포스트잇 동시에 붙인다 — `Lua`
   - 하나라도 이미 붙어 있으면 전부 취소하고 다음 사이클
4. "이 5명이요" 하고 Core API에 알린다 — `PUBLISH`

**DB는 한 번도 안 본다.** 메모(JSON)에 필요한 게 다 들어있으니까.

그리고 취소·만료·파티확정은 **Core API가 명단에서 이름을 지워버린다.**
매칭은 그냥 "명단에 없네" 하고 넘어가면 끝이다.

## 그 "주문 메모"가 이 JSON

```json
{
  "requestId": 10482,
  "userId": 7,

  "queue": "SOLO_DUO",
  "targetSize": 5,
  "purpose": "RANK_UP",
  "playMinutes": 120,
  "voiceMode": "POSSIBLE",

  "primaryPosition": "MID",
  "subPositions": ["TOP", "JUNGLE"],

  "tierOrder": 14,
  "allowedTierMinOrder": 11,
  "allowedTierMaxOrder": 18,

  "requestedAt": 1755960000000
}
```

| 필드 | 뜻 |
|---|---|
| `queue` | 어떤 큐 |
| `targetSize` | 몇 명 파티 |
| `purpose` | 플레이 목적 |
| `playMinutes` | 몇 시간 할 건지 |
| `voiceMode` | 음성 채팅 |
| `primaryPosition` | 주 포지션 |
| `subPositions` | 부 포지션 |
| `tierOrder` | 내 티어 (숫자) |
| `allowedTierMinOrder` | 같이 할 티어 하한 |
| `allowedTierMaxOrder` | 상한 |
| `requestedAt` | 신청 시각 (순서용) |

## 핵심

**이 안에 조건이 전부 들어있어서 매칭이 다른 데를 안 봐도 된다.**
그래서 DB가 필요 없다.
