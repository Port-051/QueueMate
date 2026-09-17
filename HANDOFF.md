# HANDOFF — 다음 세션 인계

**작성:** 2026-09-15 (화) 12:29 KST · **갱신:** 2026-09-16 (수)
**읽는 순서:** `CLAUDE.md` → `START_HERE.md` → **이 파일**

이 파일은 "지금 어디까지 왔고 무엇이 열려 있는가"만 담는다. 규칙은 `CLAUDE.md`,
결정의 근거는 `docs/11_DECISION_LOG.md`(특히 **D-8**), 조사 원문과 출처는
`WORKLOG_2026-09-14.md` 에 있다. 여기서 다시 설명하지 않는다.

작업이 끝나면 이 파일을 갱신하거나, 전부 끝났으면 지워라.

---

## 1. 지금 상태

- **LoL 티어 재설계 완료** (docs/11 D-8). `LolTier` enum 삭제 → Redis ZSET
  `qm:gameconfig:LOL:tier`(단 포함 32개). `tierRule` 은 `NONE`/`EXIST` 둘. Lua 가 칸 키를 조립한다.
- **`KeyConditionType.PLAY_STYLE` → `PLATFORM`** (PUBG = `STEAM` / `KAKAO`).
- 쓰이지 않던 `AcceptanceStatus` / `ProposalStatus` 삭제.
- **PUBG 시드 들어감** (모드 8 / 티어 사다리 27 / 랭크 티어 범위 표 4). **모드 목록 SET 은 LoL·PUBG 모두 없앴다.**
- **PUBG validator 커밋됨** (`validation/pubg/PubgConditionValidator.java`). PUBG 배정 규칙은 사용자가 작성 중.
- **Lua 스크립트 빈 설정을 나눴다** (`02d654f`). 공통(claim·accept·decline + `readScript()`) / LoL 5개.
- **Redis 설정 클래스를 `config/` 에서 `redis/` 패키지로 옮겼다** (`b99525a`). `redis/RedisConfig` ·
  `redis/RedissonConfig`(게임 무관, `PoolLock` 옆) / `redis/lol/LolRedisConfig`(게임별). 빈 이름은 그대로다.
  `config/` 에는 `AsyncConfig` 하나만 남았다. **규칙: Redis 설정은 `redis/`, 게임 무관은 바로 아래, 게임별은 `redis/{game}/`.**
- **LoL 클래스와 스크립트 빈에 게임 접두사를 붙였다** (`bf4b0da`). `LolModeConfig` `LolPartyLeaver`
  `LolTieredAssigner` `LolUntieredAssigner` / 빈 `lolCreateOrCheckParty{Untiered,Tiered}Script`
  `lolJoinParty{Untiered,Tiered}Script` `lolLeavePartyScript`. 티어 합류 빈은 이름이
  `joinTieredPartyUntieredScript` 로 틀려 있던 것을 `lolJoinPartyTieredScript` 로 같이 고쳤다.
- **부하 테스트 성사 감지 수정** (`32031a4`). `match_latency.py` 가 없어진 `size` 필드 대신 파티 HASH 의
  `member:` 필드를 센다. **고치기만 했고 다시 돌리지는 않았다 — 지금은 그대로 못 돈다(§3-D).**
- **코드와 어긋난 주석 수정** (`b05e2eb`). `PoolLock` · `CandidateRule` · `lol/create-or-check-party-untiered.lua` ·
  `lol/join-party.lua` 주석만. 코드 줄은 그대로다.
- **`docs/GAME_CONFIG.md` 에 PUBG 설정 반영, 모드 목록 SET 서술 제거** (`822599e`).
- **GitHub**: private 저장소 `github.com/rlaehddus302/queuemate-matching` (`main`).

### 2026-09-16 갱신 — 아래 §1 본문 중 낡은 것

이 절이 우선한다. 본문은 2026-09-15 기준이라 그대로 두었다.

- **PUBG 배정이 전부 커밋됐다.** `rule/pubg/` 6개 · `config/redis/pubg/PubgRedisConfig.java` ·
  `redis/pubg/*.lua` 5개. 아래 "작업 트리에 커밋 안 된 것"은 해소됐다.
- **`tierLo`/`tierHi` 가 `ZRANK` 값 그대로(0부터)가 됐다** (`6fe3f99`). `+ 1` 로 적고 읽을 때
  1 을 빼던 것을 없앴고, 티어를 안 보는 모드의 자리 채움 값도 `1/1` → **`0/0`** 이다.
  LoL·PUBG 스크립트 8개를 같이 고쳤다 (docs/11 Q-1).
- **VALORANT 가 전부 들어왔다** (`79a9c02` 티어 Lua 2개 → `7559b89` 배정·취소 →
  `c4c946c` 시드 → `b256769` 동시성 테스트). Lua 5개 · `rule/valorant` 6개 ·
  `ValorantRedisConfig` · 시드(모드 4 / 사다리 26 / tier-range 표 2) ·
  `ValorantPartyJoinConcurrencyTest`. 색인은 (역할군 x 티어)이고 **합류마다 파티 티어 범위를
  좁힌다** — LoL·PUBG 의 "범위는 만든 사람 기준으로 한 번 정해진다"가 발로란트에는 해당하지 않는다.
  곁딸린 키 `qm:party:needs-roles:{partyId}` SET 과 파티 HASH 의 `minTier`/`maxTier` 가 같이 생겼다.
- 테스트는 이제 LoL 과 VALORANT 경로를 탄다. **PUBG 스크립트를 도는 테스트는 아직 없다.**
- **취소가 제안의 흔적을 지운다** (`3d3efaf`). `{lol,pubg,valorant}/leave-party.lua` 가 멤버를 빼기
  전에 `status`/`expiresAt` HDEL + 수락자 SET DEL 을 한다. INV-5 의 cancelled 구멍이 막혔다.
- **제안 만료를 구현했다** (`251453a`). 새 키 `qm:proposal:pending` ZSET(member = partyId,
  score = `expiresAt`) + `service/ProposalSweeper`(`@Scheduled`, `queuemate.sweep.interval-ms`,
  회차당 100건) + `service/ProposalExpiryService` + `redis/proposal/expiry-proposal.lua`.
  `MatchingApplication` 에 `@EnableScheduling` 을 붙였다(별도 `SchedulingConfig` 는 두지 않았다).
  **ZADD 는 합류 스크립트 6개의 `HSETNX status 'PENDING'` 성공 분기 안**이고, ZREM 은 제안이
  끝나는 모든 자리(확정·거절·취소·만료)에 있다. 정책은 **B안** — 만료되면 수락하지 않은 사람만
  큐에서 빼고, 수락한 사람은 파티에 남아 빈자리가 채워지면 **다시 눌러야 한다**(옛 수락 기록은
  지워진다). `MATCH_PROPOSAL_EXPIRED` 는 그 제안에 있던 전원에게 나간다.
  INV-5 의 expired 구멍이 막혔다 — 다만 `accept-proposal.lua` 는 여전히 `expiresAt` 을 보지
  않으므로 **시한 직후 스위퍼가 꺼내기 전까지(주기만큼)는 수락이 그대로 확정된다.**
- **확정 후속 처리의 절반이 들어왔다** (작업 트리, 아직 커밋 안 됨). 새 스크립트
  `redis/proposal/cleanup-confirmed.lua` 를 `ProposalService#accept()` 가 확정 직후 부른다 —
  파티원의 **활성 요청을 지우지 않고 `status='PARTY'` 를 찍고**(지우면 그 순간 새 매칭을 걸 수
  있어 INV-2 가 깨진다), 파티 HASH 는 남기고, 수락자 SET 에만 TTL
  (`queuemate.proposal.confirmed-retention-seconds`, 기본 60)을 건다. 돌려받은 파티원 전원에게
  `MATCH_CONFIRMED`(payload `{partyId}`)를 발행한다. 같은 작업 트리에서 `accept-proposal.lua` 가
  **이미 확정된 제안의 재수락에 `ALREADY_RESPONDED`** 를 돌려주도록 바뀌었고(확정 알림이 두 번
  나가지 않게 하려는 것이다), 컨트롤러는 수락 분기에서 `ACCEPTED`/`CONFIRMED`/`ALREADY_RESPONDED`
  를 **모두 204** 로 받는다(거절 분기의 `ALREADY_RESPONDED` 는 409 그대로다).
- ⚠️ **`ProposalIdempotencyTest` 가 지금 작업 트리와 어긋난다.** "마지막 수락자가 두 번 보내도
  두 번 다 `CONFIRMED`" 를 단언하는 자리(그 파일 세 곳)가 이제 `ALREADY_RESPONDED` 를 받는다.
  **커밋 전에 테스트를 새 값에 맞춰 고쳐라.** 만료·확정 뒷정리를 덮는 테스트도 아직 없다
  (`MATCH_PROPOSAL_EXPIRED` / `MATCH_CONFIRMED` 를 구독해 보는 `PushNotificationTest` 항목 포함).
- **그래서 §3-B 는 대부분 해소됐다.** 남은 것은 `matching.outbox` 기록 + `ProposalConfirmed.fifo`
  발행(파티가 DB 에 안 생긴다), `PartyClosed` 소비(확정된 사용자의 `status=PARTY` 를 푸는 자리),
  `GET /api/v1/match-requests/{requestId}`(여전히 501), PUBG 동시성 테스트,
  Flyway + `social.blocks`(INV-6, §3-C) 다.

### 테스트 — 커밋 `8d7f094` 기준 24건 통과 (2026-09-15)

`concurrency.*` 7 + `PushNotificationTest` 6 + `ProposalIdempotencyTest` 11 = **24/24 통과.**
결과 로그에서 `ERR Error running script` / `RedisSystemException` / ERROR 로그도 0건이었다.

**설정 분리(`02d654f`)도 같은 24건으로 컨텍스트 기동까지 확인했다** (HEAD `31d23f7` + 설정 두 파일, PUBG 파일 제외).
**접두사 변경(`bf4b0da`)도 같은 24건 통과, 기동 오류 0건.**

그 전 커밋 중 자바를 바꾼 것은 `4c1c492`(PUBG validator 추가)다. 나머지(`af3b7ff` 시드 ·
`3198f74` `07b77be` `13dea28` 문서)는 자바를 건드리지 않았고, 테스트는 자기 시드를 직접 심으므로 영향이 없다.
**PUBG validator 는 그 24건에 포함되지 않았고, PUBG 를 검증하는 테스트는 아직 하나도 없다.**

### PUBG validator — 커밋됨, 규칙은 사용자가 작성 중

`validation/pubg/PubgConditionValidator.java` (파일명 오타 `Conditoin` 고침). 사용자 코드에 세 가지를 고쳐 커밋했다.
- 뒤집혀 있던 티어 분기(`EXIST` 모드가 `"NONE"` 만 통과시키던 것)
- **티어 없음 규약을 롤과 맞춤** — `NONE` 모드는 `tier` 가 **null** 이어야 통과. 클라이언트는 tier 를 안 싣는다
- `keyCondition` null 가드 + `type == PLATFORM` 확인

⚠️ **새 빈 등록인데 컨텍스트 기동 테스트(`MatchingApplicationTests`)는 안 돌렸다.** 의존성이
`StringRedisTemplate` 하나로 `LolConditionValidator` 와 같은 모양이라 위험은 낮지만, Redis 를 띄울 일이
생기면 같이 확인해라.

**아직 남은 문제 (사용자가 알고 있고 나중에 올린다고 했다):** validator 는 떴는데 PUBG `CandidateRule` 이
없다. PUBG 요청이 검증을 통과하면 `claim-request.lua` 가 활성 요청을 선점한 뒤 `MatchTrigger` 가 규칙을 못 찾아
`IllegalArgumentException("파티 배정 규칙이 없는 게임")` 으로 끝난다. `@Async` 라 201 로 나가고 그 사용자는
claim 의 `EXPIRE 60` 동안 다른 매칭을 못 잡는다.

**작업 트리에 커밋 안 된 것 — 건드리지 마라.**
- 사용자 작성 중: `rule/pubg/` 전체(`PubgCandidateRule` `PubgModeConfig` `PubgPartyKeys` `PubgScriptSupport`
  `PubgTieredAssigner` `PubgUntieredAssigner`), `resources/redis/pubg/create-or-check-party-{tiered,untiered}.lua`.
  `PubgPartyKeys` 는 IntelliJ 가 스테이징해 둔 상태(`AM`)다 — 커밋에 딸려 들어가지 않게 조심해라
- **Claude 가 만들었지만 일부러 안 올린 것:** `redis/pubg/PubgRedisConfig.java` — 위 PUBG Lua 두 개를 읽는 빈이다.
  Lua 가 커밋되지 않은 채 이것만 올리면 커밋된 코드로는 앱이 안 뜬다. **사용자가 PUBG Lua 를 올릴 때 같이 올려라.**

⚠️ **IntelliJ 가 새 파일을 git 에 자동으로 스테이징한다.** 실제로 위 Lua 두 개가 문서 커밋 `b785297` 에
딸려 올라갔다가 `e8932ad` 로 추적에서만 뺐다(원격 이력에는 남아 있다). 커밋은
**`git commit <파일경로>` 로 파일을 지정**하거나, 하기 직전에 `git diff --cached --stat` 으로 스테이징된 목록을 확인해라.

---

## 2. 사용자가 정한 것 (2026-09-15)

- **폴더는 아무것도 지우지 않는다.** `domain/lol/LolPosition` 은 쓰인다.
- **`domain/` 구조는 그대로 유지한다.** `domain/common/` 으로 옮기지 않는다.
- **PUBG 모드는 8개** (NORMAL/RANKED × DUO/SQUAD × TPP/FPP).

---

## 3. 다음 할 일

### A. PUBG 구현 (진행 중)

**시드와 validator 는 들어갔다. 배정 규칙·Lua·테스트는 아직 없다.**
모드 목록 SET(`qm:gameconfig:modes:*`)은 사용자 결정으로 **없앴다** — 모드 존재는 모드 HASH 로 판단한다.

| 키 | 내용 |
|---|---|
| `qm:gameconfig:PUBG:{modeKey}` | `NORMAL_{DUO,SQUAD}_{TPP,FPP}` → `tierRule NONE` / `RANKED_{DUO,SQUAD}_{TPP,FPP}` → `EXIST`. 듀오 2, 스쿼드 4. **`positionUniqueness` 필드는 없다** — 포지션이 없어 중복 금지할 대상이 없다(LoL 과 다르다) |
| `qm:gameconfig:PUBG:tier` | ZSET 27개. `0 UNRANKED`, `1 BRONZE_4` … `24 DIAMOND_1`, `25 MASTER`, `26 SURVIVOR` (크리스탈 포함 6티어 × 4단) |
| `qm:gameconfig:PUBG:tier-range:{modeKey}` | **듀오 ±11 / 스쿼드 ±5.** `UNRANKED` 는 `SOLO_ONLY` |

**왜 11 인가** — 공식 규칙은 "파티 단계 차이 **최대 12단계**"(패치 38.1, 2025-10-14)인데
**12 를 포함하는지 확인하지 못했다**(38.1 이후 실측 제보 0건). 틀렸을 때 12 로 잡으면 게임에서
큐가 안 잡히는 파티가 생기고, 11 로 잡으면 딱 12칸 차이 파티만 놓친다 → **fail-closed 로 11.**
실측으로 12 가 확인되면 표만 넓혀라.

**왜 스쿼드는 절반인가** — 규칙은 "파티 최고와 최저의 차이"다. 우리 엔진은 만든 사람 범위 안이면
누구든 받으므로, 4인에 ±11 을 쓰면 파티 폭이 22 가 된다. ±5 면 폭이 최대 10. LoL 솔랭/자랭을
다르게 한 것과 같은 이유(2인은 대칭만, 3인 이상은 전이까지 필요).

**표는 손으로 쓰지 말고 생성 스크립트로 만들고**, 비대칭 0건 / 파티 폭을 기계로 검증해라.

남은 코드:
- **붙이는 자리는 이미 있다.** `MatchConditionValidator` · `MatchTrigger` · `MatchCancelService` 가 전부
  `supports(GameKey)` 로 게임별 구현을 고른다. PUBG 는 `CandidateRule` 구현체를 `@Component` 로 하나 두면 된다
- `rule/pubg/PubgPartyKeys` — `LolPartyKeys` 가 `qm:party:open:LOL:` 과 `qm:gameconfig:LOL:` 을 **박아 두었으므로**
  재사용할 수 없다. 색인 키는 `qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{STEAM|KAKAO}` (+ 랭크는 `:{티어}` 를 Lua 가 붙인다)
- `rule/pubg/PubgCandidateRule` + Assigner — **사용자가 작성 중이다.** 이어받기 전에 물어라
- **스크립트 빈 이름은 게임 접두사가 붙는다** — LoL 은 `lol*`(`redis/lol/LolRedisConfig`), PUBG 는 `pubg*`
  (`pubgCreateOrCheckPartyTieredScript` 등, `redis/pubg/PubgRedisConfig`). 같은 타입(`RedisScript<List>`) 빈이 여럿이라
  Spring 은 **주입 필드 이름 = 빈 이름**으로 고른다. PUBG Assigner 가 LoL 이름(`lolCreateOrCheckPartyTieredScript`)으로
  필드를 선언하면 **LoL 스크립트가 주입된다** — 컴파일도 기동도 통과하고 배그가 롤 Lua 로 돈다. 필드 이름을 반드시
  `pubg...` 로 맞춰라. 클래스 이름도 같은 이유로 `Pubg*` 다(같은 이름이면 빈 이름이 겹쳐 기동이 실패한다)
- `redis/pubg/*.lua` — **LoL 스크립트를 고쳐 쓰지 말고 자기 디렉터리에** (D-7)
- **PUBG 동시성 테스트** — 스크립트를 나눈 대가다(CLAUDE.md §4 "게임마다 테스트")

미확인: PUBG 에 배치 전(UNRANKED) 상태가 있는지 / 단이 없는 MASTER·SURVIVOR 를 몇 단계로 세는지 /
한국 서버 FPP 큐 유무(리전별로 패치마다 바뀜).

### B. 만료 처리 + 확정 후속 (INV-4/5 의 남은 구멍)

> **2026-09-16: 아래 세 줄 중 앞의 둘은 끝났다.** 만료는 `qm:proposal:pending` + 스위퍼로,
> 확정 후속은 `cleanup-confirmed.lua` + `MATCH_CONFIRMED` 로 처리한다. 취소 구멍도 `3d3efaf`
> 로 막혔다. 남은 것은 **outbox → `ProposalConfirmed.fifo`** 와 **`PartyClosed` 소비**뿐이다.
> 자세한 것은 §1 의 "2026-09-16 갱신" 블록.

- **만료:** `expiresAt` 을 쓰기만 하고 읽는 주체가 없다 → 시한이 지난 제안에 수락이 오면 그대로
  확정된다. sweeper 필요(`queuemate.sweep.interval-ms` 설정만 있고 읽는 코드 없음).
- **확정 후속:** `status=CONFIRMED` 만 찍고 끝난다. `ProposalConfirmed.fifo` 발행,
  `MATCH_CONFIRMED` 알림, 파티·색인·활성 요청·수락자 SET 정리가 전부 없다
  (`ProposalService#accept()` TODO).
- `CLAUDE.md` INV-5 행의 "cancelled 구멍"은 **취소 API(`DELETE /match-requests/{id}`)를
  제안 도중에 부를 때만** 해당한다. **거절 버튼 경로는 문제없다** — `decline-proposal.lua` 가
  수락자 SET 과 `status` 를 먼저 지운 뒤 취소를 부른다.

### C. INV-6 스키마 (Flyway) — 배포 전 필수

### D. 낮은 우선순위 (기록만, 급하지 않음)

- **자랭 파티 생성 시 ZADD 145회.** 두 덩어리라 29칸에 같은 내용이 들어간다. 동작은 맞다.
  덩어리 단위 색인으로 줄일 수 있지만 솔랭(겹치는 범위)에는 안 된다.
- **`join-party.lua`(티어 없는 쪽)에 파티 존재 확인이 없다.** 찾기와 합류 사이 수 마이크로초에
  그 파티의 마지막 멤버가 취소하면 `HSET` 이 파티를 되살려 유령 파티가 된다. 취소가 풀 락을
  안 잡아서 이론상 가능하나 **확률은 극히 낮다.** 티어 쪽은 `HMGET tierLo` 가드로 막혀 있다.
- `CLAUDE.md` §4 INV-8 상태가 "**LoL만 구현·테스트됨**" — PUBG 도 값 검증(validator)과 시드는 생겼다. 테스트가 없으니
  "테스트됨"은 여전히 LoL 만 맞지만, PUBG 동시성 테스트가 붙으면 그 행을 갱신해라.
- `contracts/openapi.yaml` 이 아직 `PLAY_STYLE` — 계약 파일이라 안 고쳤다(`contracts/README.md` #14 에 기록).
- **부하 테스트는 성사 감지만 고쳤고(`32031a4`) 아직 다시 돌지 않는다.** 두 가지가 어긋난다 (2026-09-15 파일 확인).
  ① 요청 바디: `load-test/match_latency.py` · `prefill.py`(그리고 `measure.js` · `stock.js` · `throughput.js` ·
  `netpath/postload.js`)가 `modeKey: RANKED_SOLO` 를 `tier` 없이 보낸다. 시드의 `RANKED_SOLO` 는 `tierRule EXIST` 라
  `LolConditionValidator#validTier` 가 tier null 을 거절 → **400**. ② 색인 키: `prefill.py`(`...:needs:JUNGLE`) ·
  `run.sh` · `netpath/runpost.sh` · `runpost2.sh`(`K=...:needs` 뒤에 `:{포지션}`)가 티어 접미사 없는 needs 키를 `ZCARD`
  하는데, 티어 모드는 Lua 가 `:{tier}` 를 붙이므로 그 키는 비어 있다. 다시 돌리려면 `NORMAL_2`(정원 2, 포지션 중복 금지,
  `tierRule NONE`) 같은 모드로 바꾸거나, 바디에 `tier` 를 싣고 키에 같은 `:{tier}` 접미사를 붙여 맞춰라.
- **LoL Lua 주석에 PUBG 흔적.** `resources/redis/lol/create-or-check-party-untiered.lua:19` · `join-party.lua:18` 의
  `ARGV[3]` 주석이 "LoL 포지션 / VALORANT 역할 / **PUBG 플레이 스타일**" 이다 — 스크립트는 LoL 전용이 됐고 PUBG 조건은
  `PLATFORM` 이다. 같은 두 파일 `:34`/`:36`(·`join-party.lua:111`)의 "칼바람이나 PUBG처럼" 도 같은 흔적. 코드 파일이라 이번엔 안 고쳤다.

---

## 4. 이미 끝나서 다시 보지 않아도 되는 것

`decline` 의 무조건 `cancel()`(가드 들어감) / `LolConditionValidator` 키 문자열 하드코딩
(`keys.tierRangeKey` 로) / `LolTieredAssigner` 의 `TierRange` 죽은 코드 / 문서 전반의
`TABLE`·`WINDOW`·`maxTierGap`·`LolTier` 서술 / `CLAUDE.md` INV-2·4·5 상태 열 / `PLAY_STYLE` → `PLATFORM` 문서 반영 /
모드 목록 SET 제거와 그 문서 반영 / PUBG validator 의 뒤집힌 티어 분기·null 가드·파일명 오타 /
`RedisConfig` 공통/게임별 분리, LoL 클래스·빈 게임 접두사 / Redis 설정 `redis/` 패키지 이동(`b99525a`)과 문서 반영 /
부하 테스트 성사 감지 `size` → `member:` 세기(`32031a4`, 재실행은 §3-D) / 낡은 코드 주석(`b05e2eb`) /
`docs/GAME_CONFIG.md` PUBG 반영(`822599e`) /
`CLAUDE.md` §3 의 "확정·만료가 미구현" 낡은 문장과 INV-5 행의 `ProposalStatus`/`AcceptanceStatus` 서술.

---

## 5. 테스트 돌리는 법 — 환경 함정

- **2026-09-15 확인 기준으로** Docker 엔진이 꺼져 있고 `redis-server` 가 설치돼 있지 않았다. `sudo` 는 비밀번호를 요구한다. 먼저 `docker ps` / `which redis-server` 로 다시 확인해라.
- 이전에는 **Redis 7.2.5 를 소스에서 빌드해 스크래치패드에 두고 포트 6390** 으로 띄웠다(수 분 걸림).
  **스크래치패드는 세션마다 새로 생기므로 그 바이너리는 없다.** 다시 빌드하거나, 사용자에게
  Docker Desktop 을 켜 달라고 하거나, `! sudo apt install redis-server` 를 직접 쳐 달라고 해라.
- 붙이기: `REDIS_HOST=127.0.0.1 REDIS_PORT=6390 ./gradlew test --tests '...'`
- 작업 트리가 컴파일되지 않으면(사용자 작성 중 파일) `git archive HEAD backend` 를 스크래치패드에 풀어 거기서 돌려라
- **6379 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 절대 건드리지 마라.**
- 끝나면 Redis 종료 + `./gradlew --stop`. `bootRun` 금지.

```bash
cd backend
./gradlew compileJava compileTestJava
./gradlew test --tests 'com.queuemate.matching.concurrency.*'
./gradlew test --tests 'com.queuemate.matching.notification.PushNotificationTest'
./gradlew test --tests 'com.queuemate.matching.proposal.ProposalIdempotencyTest'
```
