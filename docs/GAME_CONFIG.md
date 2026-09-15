# 게임 모드 설정 (GameConfig)

QueueMate 실시간 매칭 MVP는 LoL만 지원하고 DB 없이 Redis만 쓴다.
매칭 엔진이 파티를 묶을 때 필요한 모드별 규칙(`targetPartySize`, `positionUniqueness`,
`tierRule`)과 **티어 사다리**, 그리고 티어 허용 범위 표가 이 문서가 말하는 "게임 모드 설정"이다.

> **티어 값의 원본도 여기다.** 예전에는 자바 enum(`LolTier`)이 티어 이름과 순서를 들고
> 있었지만 그 enum은 **삭제됐다.** 지금은 ZSET `qm:gameconfig:LOL:tier`가 원본이다.
> 뺀 이유는 모드 설정을 뺀 이유와 같다 — 단(division)을 넣거나 라이엇이 티어를 추가할
> 때마다 재배포해야 한다면 설정을 데이터로 분리한 의미가 없다.

## 왜 Redis에 미리 심는가

**코드가 부팅 시 밀어넣지 않는다. Redis에 미리 심어두고 앱은 읽기만 한다.**

코드가 밀어넣으면 모드를 하나 추가할 때마다 코드를 고치고 재배포해야 한다.
그러면 설정을 데이터로 분리한 의미가 없어진다.
설정이 Redis에만 있으면 모드 추가/삭제는 `redis-cli` 한 번으로 끝나고 앱은 그대로 둔다.

| | 코드가 밀어넣는 방식 | 미리 심어두는 방식 (채택) |
|---|---|---|
| 모드 추가 | 코드 수정 + 재배포 | `redis-cli` 실행 |
| 앱의 역할 | 쓰기 + 읽기 | 읽기 전용 |
| 설정 원본 | 코드 | 시드 파일 |

## 모드 12개

| modeKey | 한글 이름 | targetPartySize | positionUniqueness | tierRule |
|---|---|---|---|---|
| `RANKED_SOLO` | 개인/2인 랭크 | 2 | true | `EXIST` |
| `RANKED_FLEX_2` | 자유 랭크 2인 | 2 | true | `EXIST` |
| `RANKED_FLEX_3` | 자유 랭크 3인 | 3 | true | `EXIST` |
| `RANKED_FLEX_5` | 자유 랭크 5인 | 5 | true | `EXIST` |
| `ARAM_2` | 무작위 총력전(칼바람 나락) 2인 | 2 | false | `NONE` |
| `ARAM_3` | 무작위 총력전(칼바람 나락) 3인 | 3 | false | `NONE` |
| `ARAM_4` | 무작위 총력전(칼바람 나락) 4인 | 4 | false | `NONE` |
| `ARAM_5` | 무작위 총력전(칼바람 나락) 5인 | 5 | false | `NONE` |
| `NORMAL_2` | 일반 2인 | 2 | true | `NONE` |
| `NORMAL_3` | 일반 3인 | 3 | true | `NONE` |
| `NORMAL_4` | 일반 4인 | 4 | true | `NONE` |
| `NORMAL_5` | 일반 5인 | 5 | true | `NONE` |

> **`maxTierGap` 열이 없어졌다.** 필드 자체를 폐기했고, 예전 시드로 채워진 Redis에 남아
> 있는 것은 시드가 `HDEL`로 걷어낸다.

**각주 — 왜 이런 값인가**

- **`RANKED_FLEX_4`가 없다.** 자유 랭크는 게임 자체가 4인 파티 큐를 금지한다.
- **파티 인원 1짜리 모드가 없다.** QueueMate는 팀원을 붙여주는 서비스라 혼자인 파티는 의미가 없다.
- **ARAM은 `positionUniqueness=false`.** 칼바람 나락에는 포지션 개념이 없어 포지션 중복을 막을 이유가 없다.
- **모드를 인원별로 쪼갰다.** 인원을 사용자 조건으로 빼면 조건이 4개에서 5개로 늘어난다.
  modeKey에 인원을 합치면 "모드 하나 = 인원 하나"가 유지되어 조건 개수가 그대로다.
- **UI는 모드 4개로 보인다.** 사용자는 "모드 4개 + 인원"을 따로 고르고,
  서버로 보낼 때 `RANKED_FLEX_5`처럼 합쳐 보낸다.
- **AI 대전은 제외 확정.**
- **`tierRule`은 그 모드가 티어를 보는가다. 값은 둘뿐이다.**
  - `NONE` — 안 본다. 게임 자체에 티어 제한이 없는 일반/칼바람이 여기다.
  - `EXIST` — 본다. **어떻게 보는지는 전적으로 그 모드의 티어 허용 범위 표가 정한다.**
- **`WINDOW`와 `TABLE`을 없앤 이유.** 그 값은 "폭으로 거를지 표로 거를지"까지 설정에
  적는 것이었는데, **설정이 실제 데이터와 어긋날 수 있었다.** `WINDOW`라고 적어 놓고
  `maxTierGap`을 빠뜨리면 폭이 0이 되어 자기 티어하고만 매칭되는데 **에러가 나지 않았다.**
  지금은 "표가 있으면 그 표대로"가 전부라 설정이 거짓말할 수 없다. 자바에서 `"TABLE"` /
  `"WINDOW"` 문자열 비교가 전부 사라졌고 `LolModeConfig` 레코드도 `maxTierGap` 필드를 잃었다.
- **`maxTierGap 1`도 함께 없앴다.** 라이엇이 요구한 적 없는 **우리 자체 제약**이었고,
  후보 풀만 쪼개고 있었다. 자유 랭크의 실제 공식 규정은 아래 표가 말하는 그것이다.
- **받아들일 티어 범위는 파티를 만든 사람 기준으로 한 번 정해지고 바뀌지 않는다.**
  뒤에 누가 들어와도 범위를 다시 계산하지 않으므로 파티는 만들어질 때 정해진 색인 자리에
  끝까지 머문다. 대가로 서로 직접은 안 받을 두 사람이 같은 파티가 될 수 있다 — 의도한 타협이다.
- **`tierRule`이 `EXIST`인데 표가 없으면 그 모드 요청은 전부 400이다.** 표에 줄이 없는
  티어도 마찬가지다 (fail-closed). 모드를 추가할 때 표를 같이 심어야 하는 이유다.

## 티어 사다리 (32개)

롤에 어떤 티어가 있고 누가 더 높은지를 정하는 곳. **티어 값의 원본이다.**

```
0  UNRANKED
1  IRON_4     2  IRON_3     3  IRON_2     4  IRON_1
5  BRONZE_4   6  BRONZE_3   7  BRONZE_2   8  BRONZE_1
9  SILVER_4  10  SILVER_3  11  SILVER_2  12  SILVER_1
13 GOLD_4    14  GOLD_3    15  GOLD_2    16  GOLD_1
17 PLATINUM_4 18 PLATINUM_3 19 PLATINUM_2 20 PLATINUM_1
21 EMERALD_4 22  EMERALD_3 23  EMERALD_2 24  EMERALD_1
25 DIAMOND_4 26  DIAMOND_3 27  DIAMOND_2 28  DIAMOND_1
29 MASTER    30  GRANDMASTER  31  CHALLENGER
```

**각주 — 왜 이런 모양인가**

- **단(division)이 들어갔다.** `GOLD` 하나였던 것이 `GOLD_4`~`GOLD_1`로 쪼개졌다.
  라이엇 실제 규칙이 **단 단위**라 티어로 뭉개면 표현이 안 되기 때문이다(아래 솔랭 표 각주).
  단이 있는 티어는 `IRON`~`DIAMOND` 7개이고, **숫자가 클수록 낮다**(골드4 → 골드1 → 플래티넘4).
  `UNRANKED` / `MASTER` / `GRANDMASTER` / `CHALLENGER`는 단이 없다.
- **score는 순서일 뿐 아니라 "몇 단계 차이"를 그대로 준다.** 두 티어의 score를 빼면 단계
  차이가 나오므로, 롤의 "다이아는 2단 이내"도 배그의 "12단계 이내"도 뺄셈 하나가 된다.
- **`UNRANKED`는 score 0이지만 사다리 위의 값이 아니다.** 배치를 안 끝낸 사람의 실력은
  알 수 없다. 랭크 모드에서는 아래 표가 `SOLO_ONLY`로 막으므로 뺄셈에 들어가지 않는다.
- **`NONE`이라는 티어를 넣지 마라.** `leave-party.lua`가 "티어를 안 보는 모드"를 가리키는
  신호로 쓰는 값이다. 사다리에 있으면 그 모드의 색인 키가 엉킨다.

> ⚠️ **사다리 중간에 값을 끼워 넣으면 뒤가 전부 밀린다.** 이미 만들어진 파티는
> `tierLo`/`tierHi`에 **이름이 아니라 순번**을 들고 있으므로, 운영 중에 사다리를 바꾸면
> 그 파티들이 **엉뚱한 칸을 가리킨다.** 정원이 찼다 풀릴 때 되돌아갈 자리를 못 찾아
> 색인에 유령이 남는다. **큐가 비어 있을 때 바꿔라.**

## 티어 허용 범위 표 (4개 모드)

`tierRule`이 `NONE`이 **아닌** 모드는 티어별 허용 범위를 표로 갖는다.
현재 표가 있는 모드는 `RANKED_SOLO` + `RANKED_FLEX_2` / `_3` / `_5` **넷**이고,
필드는 각각 32개다 (사다리와 같은 수).

**공통 각주 — 값의 뜻**

- `MIN:MAX` — 그 티어가 파티를 만들면 그 범위의 사람을 받는다(양끝 포함).
- `SOLO_ONLY` — 이 모드에서 파티 자체가 불가능하다.
- **필드가 없는 티어는 규칙을 모르는 티어다.** validator가 통과시키지 않는다 (fail-closed).
- **기준은 2026 시즌 / 한국 서버(KR)다. 라이엇 공식 지원 문서 2026-09-14 확인.**
  다른 지역을 지원하면 키에 지역을 넣어 쪼갠다.

### 개인/2인 랭크 (`RANKED_SOLO`)

라이엇 실제 규칙은 두 줄이다.

- 다이아가 안 낀 조합 → **±1 티어** (단을 보지 않는다)
- 다이아가 낀 조합 → **±2 단(division)**
  — *"다이아몬드 플레이어는 랭크가 친구와 세 단계 이상 차이 나면 함께 게임을 시작할 수 없습니다."*
- 마스터 이상 → 듀오 불가 (KR 전용 조항)
  — *"한국 서버의 플레이어는 마스터 티어 이상의 플레이어와 게임을 플레이할 수 없습니다."*

| 티어 | 값 |
|---|---|
| `UNRANKED` | `SOLO_ONLY` |
| `IRON_4` ~ `IRON_1` | `IRON_4:SILVER_1` |
| `BRONZE_4` ~ `BRONZE_1` | `IRON_4:SILVER_1` |
| `SILVER_4` ~ `SILVER_1` | `IRON_4:GOLD_1` |
| `GOLD_4` ~ `GOLD_1` | `SILVER_4:PLATINUM_1` |
| `PLATINUM_4` ~ `PLATINUM_1` | `GOLD_4:EMERALD_1` |
| `EMERALD_4` | `PLATINUM_4:EMERALD_1` |
| `EMERALD_3` | `PLATINUM_4:EMERALD_1` |
| `EMERALD_2` | `PLATINUM_4:DIAMOND_4` |
| `EMERALD_1` | `PLATINUM_4:DIAMOND_3` |
| `DIAMOND_4` | `EMERALD_2:DIAMOND_2` |
| `DIAMOND_3` | `EMERALD_1:DIAMOND_1` |
| `DIAMOND_2` | `DIAMOND_4:DIAMOND_1` |
| `DIAMOND_1` | `DIAMOND_3:DIAMOND_1` |
| `MASTER` / `GRANDMASTER` / `CHALLENGER` | `SOLO_ONLY` |

**각주**

- **공식 문서의 티어별 표를 그대로 옮기지 않았다.** 그 표는 위 규칙을 티어 단위로 뭉개
  적은 것이라 **행끼리 모순된다** — 에메랄드 행은 티어 전체의 합집합이고 다이아 행은
  사실상 다이아 IV 기준이다. 실제 플레이어 제보와 대조한 결과 **표가 아니라 위 문장이
  맞다**: 에메2+다이아4 가능 / 에메3+다이아4 불가 / 에메1+다이아3 가능 / 에메2+다이아3 불가.
  그래서 단까지 펴서 적었다.
- **에메랄드부터 4개 단의 범위가 서로 다르고, 그 아래는 ±1 티어라 한 티어의 4줄이 전부 같다.**
- **이 표는 완전히 대칭이다** — `EMERALD_2`가 `DIAMOND_4`를 받으면 `DIAMOND_4`도
  `EMERALD_2`를 받는다. 우리 엔진은 "파티를 만든 사람의 범위"로 후보를 찾으므로 비대칭이면
  **누가 먼저 큐를 눌렀느냐로 결과가 갈린다.** 표를 고칠 때 대칭을 깨지 마라.
- **전이(transitive)는 성립하지 않는다** — 에메2~다이아4 되고 다이아4~다이아2 되지만
  에메2~다이아2는 안 된다. 개인/2인 랭크는 정원이 2라 문제가 없다.
  **3인 이상 모드에 이 모양의 표를 그대로 쓰지 마라.**
- **`DIAMOND_2` / `DIAMOND_1`의 위쪽은 원래 마스터까지지만 KR 규칙 때문에 잘랐다.**
- **`UNRANKED`는 `SOLO_ONLY`다.** 배치 전 계정의 실력을 알 수 없어 사다리에 올릴 수 없고,
  언랭끼리만 모으면 그 칸에 사람이 모이지 않아 영영 매칭되지 않는 대기가 된다.
  큐에 넣기 전에 거절하는 편이 낫다.

### 자유 랭크 (`RANKED_FLEX_2` / `_3` / `_5`)

공식 규정이 솔랭과 완전히 다르다. 원문 두 줄이 전부다.

> *"there are no Flex rank restrictions for Diamond and below"*
> *"If you want to play with Masters and up, you'll have to be at least Emerald."*

그래서 티어별로 범위가 갈리지 않고 **두 덩어리**로만 끊긴다. 세 모드의 내용은 같고
다른 것은 정원뿐이다.

| 티어 | 값 |
|---|---|
| `UNRANKED` ~ `DIAMOND_1` (29줄) | `UNRANKED:DIAMOND_1` |
| `MASTER` / `GRANDMASTER` / `CHALLENGER` | `MASTER:CHALLENGER` |

**각주**

- **왜 덩어리인가 — 정원이 3인/5인이라 전이(transitive)가 성립해야 한다.**
  에메랄드는 아이언과도 되고 마스터와도 되는데 아이언+마스터는 불가다. 범위 하나로 표현하면
  아이언·에메랄드·마스터가 한 파티에 모여 게임에서 큐가 안 잡힌다. 덩어리로 끊으면 같은
  덩어리 안의 모두가 같은 범위를 가져 **대칭과 전이가 저절로 성립한다.**
- **대가로 에메랄드/다이아가 마스터와 파티하는 경우를 버린다.** 공식상 되는 조합이지만
  마스터 이상은 인원이 극소수라 잃는 것이 작다.
- **`SOLO_ONLY`가 하나도 없는 것이 맞다.** 자유 랭크는 마스터 이상도 자기들끼리 파티가
  된다. 한국 전용 제한 문장은 공식 문서의 Solo/Duo 절 안에만 있고 Flex 절에는 없다.
- **`UNRANKED`를 다이아 이하에 넣은 것은 판단이다.** 공식에 언랭크 규정이 없고, 자유
  랭크는 친구끼리 하는 큐라 막으면 배치 전 계정이 이 모드를 통째로 못 쓴다.
- **예전에는 이 세 모드가 `WINDOW maxTierGap 1`이었다.** 라이엇이 요구한 적 없는 우리 자체
  제약이었고 후보 풀만 쪼개고 있었다.

### 알고도 못 막는 것 — 숨은 MMR

라이엇은 **표시 티어가 아니라 숨은 MMR**로도 듀오를 막는다(공식 문서에 명시). 실제로
다이아 구간에서 "듀오 안 됨"의 1순위 원인이 이것이다 — 둘 다 다이아3, LP 4점 차이인데
차단된 제보가 있다. **우리는 MMR을 볼 방법이 없으므로 이 표를 지켜도 게임에서 막히는
조합이 남는다.** 라이엇 계정 연동이 붙어도 MMR은 여전히 안 보인다.
표를 더 좁혀서 맞출 수 있는 종류의 문제가 아니다.

## Redis 키 구조

`qm:` prefix를 쓴다.

| 키 | 타입 | 내용 | 쓰는 곳 |
|---|---|---|---|
| `qm:gameconfig:{game}:{modeKey}` | HASH | 필드 `targetPartySize`, `positionUniqueness`, `tierRule`(`NONE` / `EXIST`) | 매칭 엔진이 파티 규칙 판정 |
| `qm:gameconfig:{game}:tier` | ZSET | 멤버 = 티어 이름, score = 사다리 단계 번호. **티어 값의 원본** | Lua가 `ZRANK`로 순번을 뽑고 `ZRANGE`로 색인 칸 목록을 만든다 |
| `qm:gameconfig:{game}:tier-range:{modeKey}` | HASH | 필드 = 티어 이름, 값 = `MIN:MAX` 또는 `SOLO_ONLY` | `tierRule`이 `NONE`이 아닌 모드의 티어 검증 + 파티 생성 시 범위 결정 |

예: `qm:gameconfig:LOL:RANKED_FLEX_5`, `qm:gameconfig:LOL:tier`,
`qm:gameconfig:LOL:tier-range:RANKED_SOLO`, `qm:gameconfig:PUBG:tier`.

> **모드 목록 SET(`qm:gameconfig:modes:{game}`)은 없앴다 (2026-09-15).** 모드가 있는지는 모드 HASH가
> 답한다 — 없는 모드면 `HMGET`이 필드를 전부 null로 돌려주고 validator가 그걸로 거른다
> (`LolConditionValidator` 참고). 목록을 따로 두면 모드를 추가·삭제할 때 두 곳을 같이 고쳐야 하고
> 한쪽만 고치면 어긋난다. 이 저장소 코드에는 그 SET을 읽는 곳이 없었다. UI가 모드 목록이 필요해지면
> 조회 API를 만들어 모드 HASH에서 뽑아라(`contracts/README.md` #7 `GET /games`).

> ⚠️ **`qm:gameconfig:LOL:tier`는 다른 키들과 무게가 다르다.** 모드 HASH는 고쳐도 그 모드의
> 다음 요청부터 적용되고 끝이지만, 사다리는 **이미 만들어진 파티가 순번으로 참조한다.**
> 사다리 중간에 값을 끼워 넣으면 그 파티들의 `tierLo`/`tierHi`가 엉뚱한 칸을 가리켜
> 색인에 유령이 남는다. **큐가 비어 있을 때만 바꿔라.**

## 심는 방법

시드 파일: `seed/gameconfig.redis`

```bash
docker exec -i qm-redis redis-cli < seed/gameconfig.redis
```

멱등하다. 여러 번 실행해도 결과가 같다.

- `HSET`은 같은 값을 다시 써도 결과가 같다.
- 모드 목록 SET은 `SADD`만 하지 않고 `MULTI` / `DEL` / `SADD` / `EXEC`로 통째로 갈아끼운다.
  `SADD`만 하면 추가는 반영되지만 시드 파일에서 **제거**한 모드가 Redis에 남아
  삭제된 모드가 UI 선택지에 계속 뜬다. `MULTI`로 묶는 이유는
  `DEL`과 `SADD` 사이에 목록이 비는 순간 들어온 매칭 요청이 fail-closed 되는 걸 막기 위해서다.
- 티어 표도 같은 이유로 `MULTI` / `DEL` / `HSET` / `EXEC`로 통째로 갈아끼운다.
  `HSET`만 하면 시드 파일에서 지운 티어가 Redis에 남아,
  규칙이 좁아졌는데 옛 범위가 살아 있는 상태가 된다.
- `redis-cli`는 파이프 입력에서 `#` 주석을 지원하지 않는다(`unknown command '#'`).
  그래서 시드 파일의 주석은 `ECHO`로 남겼고, 실행하면 진행 로그처럼 출력된다.

## 확인하는 방법

```bash
# 설정 키 전부 보기
docker exec qm-redis redis-cli KEYS 'qm:gameconfig:*'

# 특정 모드 설정 보기
docker exec qm-redis redis-cli HGETALL qm:gameconfig:LOL:RANKED_SOLO

# 모드가 들어갔는지 보기 (모드 HASH 키 목록. LoL 12개 / PUBG 8개여야 한다)
docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:LOL:*' | grep -v ':tier'
docker exec qm-redis redis-cli --scan --pattern 'qm:gameconfig:PUBG:*' | grep -v ':tier'

# 티어 사다리 보기 (32개여야 한다)
docker exec qm-redis redis-cli ZRANGE qm:gameconfig:LOL:tier 0 -1 WITHSCORES
docker exec qm-redis redis-cli ZCARD qm:gameconfig:LOL:tier

# 티어 허용 범위 표 보기 (모드마다 32개여야 한다. 표를 갖는 모드는 넷)
docker exec qm-redis redis-cli HGETALL qm:gameconfig:LOL:tier-range:RANKED_SOLO
for m in RANKED_SOLO RANKED_FLEX_2 RANKED_FLEX_3 RANKED_FLEX_5; do
  docker exec qm-redis redis-cli HLEN qm:gameconfig:LOL:tier-range:$m
done
```

## 모드 추가 / 삭제 (재배포 없음)

### 추가

1. `seed/gameconfig.redis`에 `HSET` 한 줄을 넣고, `SADD` 줄의 목록에 modeKey를 추가한다.
   `HSET` 줄에는 `targetPartySize`, `positionUniqueness`, `tierRule`을 전부 쓴다.
2. **`tierRule`이 `EXIST`면 `qm:gameconfig:LOL:tier-range:<modeKey>` 표를 같이 심는다.**
   표는 `MULTI` / `DEL` / `HSET` / `EXEC`로 통째로 갈아끼운다.
   **사다리에 있는 티어 전부(32개)에 줄이 있어야 한다.** 한 줄이라도 비면 그 티어의 요청만
   400이 되는데, 모드는 목록에 멀쩡히 떠 있어서 증상이 모드 문제로 보이지 않는다.
3. 시드를 다시 실행한다: `docker exec -i qm-redis redis-cli < seed/gameconfig.redis`
4. `SMEMBERS`로 모드를, `HLEN ... tier-range:<modeKey>`로 표를 확인한다.

앱 재배포 불필요. 다음 매칭 요청부터 바로 적용된다.

> **`tierRule`을 빠뜨리면 그 모드의 매칭 요청이 전부 400으로 거부된다.**
> `LolConditionValidator`는 `positionUniqueness`나 `tierRule` 중 하나라도 없으면
> 설정이 불완전한 것으로 보고 통과시키지 않는다 (fail-closed).
> 필드 하나가 빠진 모드는 목록에는 떠 있고 요청만 거부되므로 증상이 모드 누락처럼 보이지 않는다.

> **`tierRule=EXIST`인데 `tier-range` 표를 안 심으면 그 모드 요청도 전부 400이다.**
> `maxTierGap`이 있던 시절에는 값이 없어도 폭 0으로 조용히 돌아갔지만, 지금은
> **표가 없으면 아무도 매칭되지 않는다.** 이건 의도한 것이다 — 조용히 틀리는 것보다
> 시끄럽게 막히는 편이 낫다. 모드 추가와 표 추가는 **한 번에 한다.**

> **표를 쓸 때 대칭을 지켜라.** `A`의 범위에 `B`가 있으면 `B`의 범위에도 `A`가 있어야 한다.
> 우리 엔진은 "파티를 만든 사람의 범위"로 후보를 찾으므로, 비대칭이면 누가 먼저 큐를
> 눌렀느냐로 결과가 갈린다. 정원이 3인 이상인 모드는 **전이(transitive)** 까지 성립해야
> 한다 — 성립하지 않는 규칙은 자유 랭크처럼 **덩어리로 끊어라.**

### 삭제

1. `seed/gameconfig.redis`에서 해당 `HSET` 줄을 지우고 `SADD` 목록에서도 modeKey를 뺀다.
2. 시드를 다시 실행한다. → `DEL` + `SADD` 덕분에 목록에서는 즉시 사라진다.
3. **HASH 키는 자동으로 지워지지 않으므로 직접 지운다.**
   `tierRule`이 `EXIST`이던 모드면 티어 표 키도 같이 지운다.
   ```bash
   docker exec qm-redis redis-cli DEL qm:gameconfig:LOL:<지울 modeKey>
   docker exec qm-redis redis-cli DEL qm:gameconfig:LOL:tier-range:<지울 modeKey>
   ```

> 목록(SET)에서만 빼고 HASH를 남겨두면 UI에는 안 뜨지만 키는 계속 남는다.
> 반대로 HASH만 지우고 목록에 남기면 UI에 뜬 모드가 매칭에서 거부된다. 항상 둘 다 처리한다.

## 주의: Redis가 재시작하면 설정이 날아간다

**설정이 없으면 모든 매칭 요청이 거부된다 (fail-closed).**
INV-10에 따라 설정을 못 읽었을 때 기본값으로 매칭을 진행하는 fallback은 만들지 않는다.
즉 Redis 컨테이너를 재시작하면 서비스가 사실상 멈춘다.

날아가는 것은 모드 HASH 12개와 모드 목록 SET만이 아니다.
**티어 사다리 `qm:gameconfig:LOL:tier`** 와 티어 허용 범위 표 4개
(`RANKED_SOLO` + `RANKED_FLEX_2`/`_3`/`_5`)도 같이 사라진다.
표만 없어도 `tierRule=EXIST`인 모드 넷의 요청은 표에서 티어를 못 찾아 전부 거부되고,
**사다리가 없으면 Lua가 티어 이름을 순번으로 환산하지 못해 그 모드의 배정 자체가 안 된다**
(`ZRANK`가 `false`를 돌려주면 `-1`로 끝낸다). 자바에 티어 값이 없으므로 복구할 방법도
시드를 다시 실행하는 것뿐이다.

대응책 두 가지:

| 대응 | 방법 | 상태 |
|---|---|---|
| Redis 영속화 | AOF(`appendonly yes`) 또는 RDB 스냅샷을 켜서 재시작 후에도 데이터 유지 | 미적용 |
| 자동 시드 | 컨테이너 시작 시 `seed/gameconfig.redis`를 자동 실행 (entrypoint / init 컨테이너) | 미적용 |

**지금은 수동 시드 상태다.** Redis를 재시작했으면 사람이 위 "심는 방법"을 다시 실행해야 한다.
운영 전에 둘 중 하나(가능하면 둘 다)를 적용해야 한다.

## 알려진 한계

상위 문서 `docs/07_REDIS_DESIGN.md`는 이 설정을 "Redis 캐시"라고 부른다.
하지만 캐시는 원본이 따로 있다는 뜻이다.

MVP에는 DB가 없어 **Redis가 원본이고, 시드 파일이 사실상의 원본 역할**을 한다.
용어와 실제가 어긋나 있다.

나중에 DB를 붙이면 `gameconfig` 스키마가 원본이 되고 Redis가 진짜 캐시가 된다.
그때 이 시드 파일은 마이그레이션/초기 데이터로 옮겨간다.

**이건 MVP 한정 타협이다.**
