<!-- 출처: queueMate 저장소 / 브랜치 feature/frontend / 경로 docs/02_MATCH_CONDITION_SCHEMA.md -->
<!-- 커밋: 825d673 (git show feature/frontend:docs/02_MATCH_CONDITION_SCHEMA.md) -->
<!-- 원문 verbatim. 수정하지 말 것 — 변경은 queueMate 원본에서 하고 다시 가져온다. -->

# 02. Match Condition Schema

## 1. Design rule
게임별 조건을 하나의 거대한 공통 폼으로 만들지 않는다. 공통 골격 + 게임별 핵심 조건 하나를 사용한다.

## 2. Common fields
```text
game              required
modeKey            required
voicePreference    required
playPurpose        required
```

### VoicePreference
- `REQUIRED`: 음성을 사용할 수 있는 팀원만
- `OPTIONAL`: 음성 여부 무관
- `NO_VOICE`: 음성 사용을 원하지 않음

Compatibility:
- REQUIRED ↔ NO_VOICE = incompatible
- 그 외 = compatible

### PlayPurpose
- `RANK_UP`
- `NORMAL`
- `FUN`

playPurpose는 soft condition이다. 일치 후보를 우선하지만 부족하면 완화할 수 있다.

## 3. LoL
```text
keyCondition.type  = POSITION
keyCondition.value = TOP | JUNGLE | MID | ADC | SUPPORT
```

Rules:
- 게임/모드/rank eligibility/block = hard
- 같은 파티 내 동일 `POSITION` 충돌은 hard reject (해당 mode가 role uniqueness를 요구할 때)
- Position은 사용자가 명시적으로 ANY를 선택하지 않는 한 자동 완화하지 않는다.

초기 mode config 예:
- `SOLO_DUO_RANKED`: targetPartySize=2, positionUniqueness=true
- 다른 mode는 config에서 관리하고 UI 노출 여부도 config로 결정
- 파티 인원이 가변인 모드(예: 자유 랭크)는 인원별로 modeKey를 분리해 표현한다
  (예시일 뿐 지원 모드 확정 아님, docs/11 #31)

## 4. VALORANT
```text
keyCondition.type  = ROLE
keyCondition.value = DUELIST | INITIATOR | CONTROLLER | SENTINEL
```

Rules:
- game/mode/rank eligibility/block = hard
- ROLE 중복은 게임 자체에서 불가능한 것이 아니므로 hard reject하지 않는다.
- 서로 다른 ROLE 구성은 더 높은 compatibility tier를 가진다.

초기 mode config:
- `COMPETITIVE`
- `UNRATED`

## 5. PUBG
```text
keyCondition.type  = PLATFORM
keyCondition.value = STEAM | KAKAO
```

> **2026-09-14 변경.** 원래 `PLAY_STYLE`(`AGGRESSIVE | BALANCED | SURVIVAL`, soft)이었으나
> **플랫폼으로 교체했다.** 스팀과 카카오는 서버가 분리돼 서로 파티를 맺을 수 없다
> (카카오게임즈 공식 FAQ: "스팀 및 기타 타 플랫폼 이용자와 게임 진행이 불가능합니다").
> 플랫폼이 아래 규칙의 "platform rule = hard" 그 자체라, 따로 두지 않고 핵심 조건 자리에
> 넣었다. 플레이 스타일은 어긋나도 게임이 되는 취향이라 뺐다. 조건 개수는 4개 그대로다.
> 시점(TPP/FPP)은 파티 구성을 막지 않는 큐 선택이라 조건이 아니라 `modeKey` 에 접었다.

Rules:
- game/mode/platform/block = hard
- 플랫폼은 서로 다른 값끼리 절대 매칭하지 않는다 (색인 키가 갈린다)

mode config (계획, 아직 시드에 없다):
- `NORMAL_DUO_TPP` / `NORMAL_DUO_FPP` / `NORMAL_SQUAD_TPP` / `NORMAL_SQUAD_FPP` — `tierRule NONE`
- `RANKED_DUO_TPP` / `RANKED_DUO_FPP` / `RANKED_SQUAD_TPP` / `RANKED_SQUAD_FPP` — `tierRule EXIST`

TPP/FPP 등 세부 mode는 문자열 config로 확장하고 코드 enum을 불필요하게 늘리지 않는다.

## 6. Derived conditions
사용자 폼에서 직접 받지 않고 시스템이 가져오거나 계산:
- linked game account
- rank / eligibility
- region when game account/provider가 제공
- party target size
  (모드가 인원을 결정한다는 전제를 유지하기 위해, 인원이 가변인 모드는 modeKey를
  인원별로 분리한다, docs/11 #31)
- block relation
- active request conflict

## 7. Reservation-only additions
```text
availableFrom      ZonedDateTime, 30분 경계
availableTo        ZonedDateTime, 30분 경계
playAmount         ONE_GAME | TWO_PLUS
```

기존 실시간 조건을 복사하고 시간 조건만 추가한다.

## 8. No-condition-creep rule
새 필드를 UI에 추가하려면:
1. 커뮤니티/LFG 데이터에서 반복 사용 확인
2. 실제 mismatch 영향 확인
3. candidate pool fragmentation 측정
4. `docs/12_ELBOW_CONDITION_SELECTION.md`의 elbow 기준 통과

그 전에는 profile 참고 정보로만 둘 수 있다.

---

<!-- 아래는 원문이 아니다. app:matching 저장소에서 덧붙인 구현 대조 기록이다. -->
<!-- 위 본문은 verbatim이므로 고치지 않는다. 본문을 바꿔야 하면 queueMate 원본에서 바꾼다. -->

## 부록 A. 구현 대조 기록 (2026-09-06, `app:matching`)

> ⚠ **A-2 / A-3 은 낡았다. 2026-09-11 시점의 사실은 [부록 B](#부록-b-구현-대조-기록-갱신-2026-09-11-appmatching)에 있다.**
> A-3(랭크 티어 미구현)은 **해소됐고**, A-2(조건 완화 없음)는 범위가 달라졌다.
> 부록도 기록이라 지우지 않고 아래에 새 항목으로 덧붙인다.

위 본문은 queueMate 본 저장소의 원문 사본이라 **이 저장소에서 고칠 수 없다.**
그런데 본문의 서술 몇 개가 현재 구현과 어긋난다. 본문을 믿고 코드를 찾다가 헛수고하지
않도록 어긋난 지점만 여기에 적는다. **어긋난 곳은 아래를, 나머지는 본문을 따른다.**

### A-1. soft로 적힌 조건이 구현에서는 전부 hard다

본문의 두 문장이 현재 코드와 다르다.

| 위치 | 본문 서술 | 구현 |
|---|---|---|
| §2 | "playPurpose는 soft condition이다. 일치 후보를 우선하지만 부족하면 완화할 수 있다" | **hard.** 완화 경로가 없다 |
| §5 | "PLAY_STYLE는 soft. exact match 우선" | PUBG는 미구현이지만, 현재 구조에 얹으면 **hard가 된다** — *(2026-09-14: `PLAY_STYLE` 자체가 `PLATFORM` 으로 교체돼 이 행은 당시 기준이다. 본문 §5 참고)* |

근거는 후보 색인 키의 모양이다 (`rule/lol/LolCandidateRule.java#needsKey()`):

```
qm:party:open:LOL:{modeKey}:{voicePreference}:{playPurpose}:needs:{keyValue}
```

`mode`·`voice`·`purpose`·`keyValue`가 **전부 키 이름 안에 들어간다.** 그래서 조건이 하나라도
다른 사용자는 애초에 **같은 ZSET에 존재하지 않는다.** `create-or-check-party-untiered.lua`는 그 키
하나만 `ZRANGE` 하고 다른 키를 대안으로 뒤지지 않는다. 즉 soft로 완화하려면 다른 키를
추가로 조회하는 코드가 있어야 하는데, 그런 코드가 없다.

**이건 사고가 아니라 결정이다** — `docs/11` #30이 "Tier 0(조건 완전 일치)만"으로 착수 범위를
정했고, #33이 후보 순회 대신 역색인을 택했다. 완전 일치를 알고리즘이 아니라 **키 분할**로
구현했으므로 soft/hard 구분이 사라진 것이다. **본문의 'soft'가 낡았고 코드가 맞다.**

되돌리려는 게 아니라면 이렇게 읽어라: **현재 사용자 입력 조건 4개는 전부 hard filter다.**

### A-2. compatibility tier / 조건 완화는 코드에 없다 <sub>(2026-09-11: 결론은 유효, 근거 문장은 낡았다 — B-2)</sub>

같은 이유로 §4의 "서로 다른 ROLE 구성은 더 높은 compatibility tier를 가진다"도 구현이 없다.
`tier`로 `backend/src/`를 grep하면 **0건**이다. tier 코드를 찾지 마라 — 없는 게 맞다.

### A-3. `rank eligibility = hard`인데 구현이 없다 — 현재 가장 큰 구멍 <sub>(2026-09-11: 해소됨 — B-1)</sub>

§3·§4·§5가 공통으로 `rank eligibility = hard`라고 선언하고, §6이 rank를 derived(연동 계정에서
가져오는 값)로 분류한다. **그런데 구현에 티어 개념이 아예 없다.** `backend/src/`에 rank/tier가 0건이고
후보 색인 키에도 없다. 계정 연동 자체가 없다.

이건 조건을 5개로 늘리는 문제가 아니라 **선언된 hard filter가 빠진 것**이므로 `docs/12`의
elbow 절차 대상이 아니다. 그리고 취향이 아니라 게임 규칙이다:

- **LoL** — 개인/2인 랭크 듀오는 티어 1단계 차이까지만 가능하다. 저티어는 완화되고 고티어는
  더 빡빡하며, 에메랄드~다이아II는 마스터 MMR 보유자와 큐를 같이 돌 수 없다.
- **VALORANT** — 경쟁전 파티는 랭크 윈도우로 묶인다(아이언~골드 / 플래티넘~다이아 /
  애센던트·불멸은 1티어 차이까지). 레디언트는 듀오만 가능하다.

따라서 지금 엔진은 **게임에서 큐를 같이 돌 수조차 없는 파티를 확정할 수 있다.**
조건이 아니라 정합성 문제다.

구현할 때 주의: 티어를 다른 조건처럼 색인 키 이름에 넣으면 후보 풀이 티어 수만큼 또
쪼개진다. 밴드 단위(VALORANT식 윈도우)로 굵게 넣거나, 키에 넣지 말고 배정 스크립트
안에서 파티의 티어 범위와 비교하는 쪽을 검토해라. 지금 도는 스크립트는
`create-or-check-party-untiered.lua`이고 티어를 보지 않는다. 티어를 보는 배정은 별도
스크립트 `join-or-create-party-tiered.lua`가 맡는다 — 아직 없다 (`docs/11` D-6).

### A-4. §2의 `VoicePreference.OPTIONAL`은 코드에서 제거됐다

현재 값은 `REQUIRED, NO_VOICE` 둘뿐이다(`domain/VoicePreference.java`). 제거 근거는 그
enum의 클래스 주석에 있다. **코드가 맞고 본문이 낡았다.** `contracts/openapi.yaml`도 아직
`OPTIONAL`을 남기고 있다 (`contracts/README.md` 불일치 #1).

### A-5. §3의 LoL 포지션 값 목록이 코드보다 하나 적다

본문은 `TOP | JUNGLE | MID | ADC | SUPPORT`인데 `domain/lol/LolPosition.java`에는 **`NONE`이
하나 더 있다.** 칼바람처럼 포지션 개념이 없는 모드(`positionUniqueness=false`)를 위한 값이다.

### 재확인 명령

```bash
grep -n "needsKey" -A6 backend/src/main/java/com/queuemate/matching/rule/lol/LolCandidateRule.java
grep -rn "tier\|rank" backend/src/ | wc -l               # 0이면 A-2·A-3이 아직 유효하다
grep -n "enum VoicePreference" -A2 backend/src/main/java/com/queuemate/matching/domain/VoicePreference.java
grep -n "TOP" backend/src/main/java/com/queuemate/matching/domain/lol/LolPosition.java
```

---

## 부록 B. 구현 대조 기록 갱신 (2026-09-11, `app:matching`)

부록 A를 쓴 뒤 코드가 바뀌어 A-2 / A-3 이 사실과 어긋나게 됐다. **A는 기록이라 지우지 않고
여기에 갱신분만 적는다.** 겹치는 항목은 이쪽이 우선한다.

### B-1. A-3 해소 — 랭크 티어가 구현됐다

> ⚠️ **이 절의 티어 값·규칙 서술은 2026-09-14 에 갈아엎였다. 부록 C를 먼저 읽어라.**
> 바뀐 것은 세 가지다 — 티어 enum 삭제(원본이 Redis ZSET), 단(division) 도입,
> `tierRule` 이 `NONE`/`EXIST` 둘로 축소. 겹치는 항목은 부록 C가 우선한다.

A-3이 "가장 큰 구멍"이라고 적은 `rank eligibility = hard`가 들어왔다.

| 무엇 | 어디 |
|---|---|
| 티어 값 | `domain/lol/LolTier.java` — `UNRANKED, IRON … CHALLENGER` 11개. **단(division)은 넣지 않았다** |
| 요청 필드 | `dto/CreateMatchRequestCommand.java` 의 `tier` (선택 필드, String). 어느 사다리의 티어인지는 `modeKey`가 정한다 |
| 규칙 | gameconfig 의 `tierRule` — `NONE` / `WINDOW`(±`maxTierGap`) / `TABLE`(`qm:gameconfig:LOL:tier-range:{modeKey}` 표, 값은 `MIN:MAX` 또는 `SOLO_ONLY`) |
| 검증 | `validation/lol/LolConditionValidator.java#validTier` — 규칙에 맞지 않으면 400 |
| 배정 | `rule/lol/LolTieredAssigner.java` + `redis/lol/create-or-check-party-tiered.lua` / `join-party-tiered.lua` |
| 색인 | `...:needs:{포지션}:{티어}` — (포지션 x 티어) **격자** |

**A-3의 걱정("티어를 키 이름에 넣으면 후보 풀이 또 쪼개진다")은 실제로 그렇게 됐다.**
대신 파티가 받아들일 티어 범위를 **만든 사람 기준으로 생성 시 한 번** 정하고 그 범위의
모든 칸에 색인을 걸어, 한 사람이 여러 칸에서 잡히게 했다. 그 범위는 파티 HASH의
`tierLo` / `tierHi`(이름이 아니라 **순번**)에 적힌다. 대가로 서로 직접은 안 받을 두 사람이
같은 파티가 될 수 있다 — 의도한 것이다 (`create-or-check-party-tiered.lua` 머리 주석).

`UNRANKED`는 사다리 밖이라 `UNRANKED`끼리만 붙인다 (fail-closed).
`tierRule` 해석은 전부 자바가 하고 Lua는 환산된 `[최저, 최고]` 순번만 받는다 —
규칙이 바뀌어도 스크립트는 그대로다.

**티어는 다섯 번째 조건이 아니다.** 본문 §6의 derived 조건이자 §3의 자격 조건이므로
`docs/12`의 elbow 절차 대상이 아니다 (A-3이 이미 그렇게 판단했다).

### B-2. A-2는 결론만 유효하다 — "tier로 grep하면 0건"은 이제 틀렸다

A-2가 말한 **compatibility tier / 단계적 조건 완화**는 여전히 코드에 없다. aging도 없다.
매칭은 완전 일치이고 그 일치는 알고리즘이 아니라 Redis 키 분할로 이뤄진다 (docs/11 #30, #33).
**그 결론은 그대로다.**

다만 A-2와 A-3의 근거로 쓴 `grep -rn "tier\|rank" backend/src/ | wc -l` → 0 은 더 이상
0이 아니다. B-1의 랭크 티어가 걸린다. **두 개는 다른 것이다** — 랭크 티어는 "같이 큐를
돌 수 있는가"(자격)이고, compatibility tier는 "조건을 얼마나 풀어 줄까"(완화)다.

### B-3. A-1 / A-4 / A-5 는 그대로다

- A-1(조건 4개가 전부 hard) — 유효
- A-4(`VoicePreference.OPTIONAL` 제거) — 유효
- A-5(`LolPosition`에 `NONE`이 하나 더 있다) — 유효

### 재확인 명령 (2026-09-11판)

```bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching"

find backend/src/main/resources/redis -name '*.lua'   # lua 8개. -tiered 두 개가 B-1이다
# 티어 값의 원본은 자바가 아니라 Redis 다 (부록 C). LolTier.java 를 grep 하던 자리다
grep -rn "LolTier" backend/src/                       # 0건이어야 맞다
docker exec qm-redis redis-cli ZRANGE qm:gameconfig:LOL:tier 0 -1 WITHSCORES  # 티어 32개
grep -n "tierRule" seed/gameconfig.redis              # 모드별 규칙 (NONE / EXIST)
grep -n "validTier" -A25 backend/src/main/java/com/queuemate/matching/validation/lol/LolConditionValidator.java
grep -rn "aging\|relax\|완화" backend/src/ | wc -l    # 0이면 A-2/B-2가 아직 유효하다
```

---

## 부록 C. 티어 설계 변경 (2026-09-14, `app:matching`)

부록 B-1 을 쓴 지 사흘 만에 티어 쪽이 통째로 바뀌었다. **B는 기록이라 지우지 않고
여기에 갱신분만 적는다.** 티어에 관해 겹치는 항목은 이쪽이 우선한다.

### C-1. 티어 값의 원본이 자바에서 Redis로 옮겨갔다

`domain/lol/LolTier.java` enum 은 **삭제됐다.** `grep -rn LolTier backend/src` 는 0건이고,
자바에는 티어 이름을 아는 코드가 한 줄도 없다. 원본은 ZSET `qm:gameconfig:LOL:tier` 이고
score 가 사다리의 단계 번호다 — `0 UNRANKED`, `1 IRON_4` … `31 CHALLENGER`, 총 **32개**.

**왜 뺐나.** gameconfig 를 데이터로 뺀 것과 같은 논리다. 단을 넣거나 라이엇이 티어를
추가할 때마다 재배포해야 한다면 설정을 데이터로 뺀 의미가 없다. 실제로 그 일이 바로
일어났다 — C-2 가 그것이다.

score 가 순서만이 아니라 **"몇 단계 차이"** 를 그대로 준다는 점이 부수 효과다. 두 티어의
score 를 빼면 단계 차이가 나오므로, 롤의 "다이아는 2단 이내"도 배그의 "12단계 이내"도
뺄셈 하나가 된다.

### C-2. 단(division)이 들어갔다 — B-1의 "단은 넣지 않았다"는 뒤집혔다

`GOLD` 하나였던 것이 `GOLD_4` / `GOLD_3` / `GOLD_2` / `GOLD_1` 로 쪼개졌다.

| | 값 |
|---|---|
| 단이 있는 티어 | `IRON` `BRONZE` `SILVER` `GOLD` `PLATINUM` `EMERALD` `DIAMOND` — 각 4개. **숫자가 클수록 낮다** (골드4 → 골드1 → 플래티넘4) |
| 단이 없는 값 | `UNRANKED`, `MASTER`, `GRANDMASTER`, `CHALLENGER` |

**왜 넣었나.** 라이엇 실제 규칙이 **단 단위**라 티어로 뭉개면 표현이 안 되기 때문이다 (C-4).

### C-3. `tierRule` 이 `NONE` / `EXIST` 둘로 줄었다

`WINDOW` 와 `TABLE` 이 없어졌고 `maxTierGap` 필드도 없어졌다(시드가 `HDEL` 로 걷어낸다).

| 값 | 뜻 |
|---|---|
| `NONE` | 티어를 안 본다 (일반 / 칼바람) |
| `EXIST` | 본다. **어떻게 보는지는 전적으로 `qm:gameconfig:LOL:tier-range:{modeKey}` 표가 정한다** |

**왜 없앴나.** enum 값이 실제 데이터와 어긋날 수 있었다. `WINDOW` 라고 적어 놓고
`maxTierGap` 을 빠뜨리면 폭이 0 이 되어 자기 티어하고만 매칭되는데 **에러가 안 났다.**
지금은 "표가 있으면 그 표대로"가 전부라 설정이 거짓말할 수 없다. 표에 줄이 없는 티어는
400 이다 (fail-closed).

### C-4. 허용 범위 표가 라이엇 실제 규칙이 됐고, 표를 갖는 모드가 1개 → 4개다

라이엇 공식 지원 문서(2026-09-14 확인)의 실제 규칙은 두 줄이다.

- 다이아가 안 낀 조합 → **±1 티어**
- 다이아가 낀 조합 → **±2 단(division)**
  — *"다이아몬드 플레이어는 랭크가 친구와 세 단계 이상 차이 나면 함께 게임을 시작할 수 없습니다."*
- 마스터 이상 → 듀오 불가 (KR 전용 조항)
  — *"한국 서버의 플레이어는 마스터 티어 이상의 플레이어와 게임을 플레이할 수 없습니다."*

> 공식 문서의 **티어별 표는 이 규칙을 티어 단위로 뭉개 적은 것이라 행끼리 모순된다**
> (에메랄드 행은 티어 전체의 합집합, 다이아 행은 사실상 다이아 IV 기준). 실제 플레이어
> 제보와 대조한 결과 **표가 아니라 위 문장이 맞다** — 에메2+다이아4 가능 / 에메3+다이아4 불가 /
> 에메1+다이아3 가능 / 에메2+다이아3 불가. 그래서 단까지 표현해야 했다.

| 모드 | 표의 모양 |
|---|---|
| `RANKED_SOLO` | 위 규칙을 단 단위로 편 32줄. `UNRANKED` 와 `MASTER`/`GRANDMASTER`/`CHALLENGER` 는 `SOLO_ONLY` |
| `RANKED_FLEX_2` / `_3` / `_5` | **신설.** 두 덩어리뿐이다 — `UNRANKED`~`DIAMOND_1` 은 전부 `UNRANKED:DIAMOND_1`, `MASTER`~`CHALLENGER` 는 전부 `MASTER:CHALLENGER`. **`SOLO_ONLY` 가 하나도 없다** |

자유 랭크는 공식 규정이 다르다 — *"there are no Flex rank restrictions for Diamond and below"*,
*"If you want to play with Masters and up, you'll have to be at least Emerald."*

**왜 덩어리로 끊었나.** 자유 랭크는 정원이 3인/5인이라 **전이(transitive)가 성립해야 한다.**
에메랄드는 아이언과도 되고 마스터와도 되는데 아이언+마스터는 불가라, 범위 하나로 표현하면
아이언·에메랄드·마스터가 한 파티에 모인다. 덩어리 안의 모두가 같은 범위를 가지면 대칭과
전이가 저절로 성립한다. 대가로 에메랄드/다이아가 마스터와 파티하는 경우를 버리지만
마스터 이상은 인원이 극소수라 잃는 것이 작다.

**예전 `WINDOW maxTierGap 1` 은 라이엇이 요구한 적 없는 우리 자체 제약이었고** 후보 풀만
쪼개고 있었다. 없앤 것이 맞다.

### C-5. `tierRule` 해석 자리가 자바에서 Lua로 옮겨갔다

B-1 이 "`tierRule` 해석은 전부 자바가 하고 Lua는 환산된 `[최저, 최고]` 순번만 받는다"고
적은 것은 **반대가 됐다.** `LolTieredAssigner#tierRange()` 와 `TierRange` 레코드,
`addTierArgs()` 가 없어졌고, Lua 가 tier-range 표와 티어 사다리를 직접 읽는다.
파티 HASH 의 `tierLo`/`tierHi` 는 `ZRANK + 1` (1부터 시작하는 사다리 순번)이다.
`join` 과 `leave` 는 자기 tier-range 를 다시 읽지 않고 그 두 값을 되돌려 칸을 만든다.

격자를 KEYS 로 통째로 넘기던 방식도 함께 버렸다. 단이 들어가 칸이 (포지션 6 x 티어 32)
= **192개**가 되면서 호출마다 그만큼을 넘겨야 했기 때문이다. 지금은 티어 접미사가 없는
needs 키만 넘기고 Lua 가 `':' .. 티어이름` 을 붙인다.

> **사다리 중간에 값을 끼워 넣으면 이미 만들어진 파티의 `tierLo`/`tierHi` 가 엉뚱한 칸을
> 가리킨다.** 티어를 추가할 일이 생기면 큐가 비어 있을 때 바꿔라.

### C-6. 알고도 못 막는 것 — 숨은 MMR

라이엇은 **표시 티어가 아니라 숨은 MMR** 로도 듀오를 막는다(공식 문서에 명시). 실제로
다이아 구간에서 "듀오 안 됨"의 1순위 원인이 이것이다 — 둘 다 다이아3, LP 4점 차이인데
차단된 제보가 있다. 우리는 MMR 을 볼 방법이 없으므로 **표를 지켜도 게임에서 막히는 조합이
남는다.** 라이엇 계정 연동이 붙어도 MMR 은 여전히 안 보인다. 이건 고칠 수 있는 버그가
아니라 받아들여야 하는 한계다 — 사용자에게 "게임에서 큐가 안 잡힐 수 있다"고 말할지는
제품 결정이다.
