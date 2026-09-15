# HANDOFF — 다음 세션 인계

**작성:** 2026-09-15 (화) 12:29 KST · **갱신:** 2026-09-15 (화) 12:57 KST
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
- **GitHub**: private 저장소 `github.com/rlaehddus302/queuemate-matching` (`main`).

### 테스트 — 커밋 `8d7f094` 기준 24건 통과 (2026-09-15)

`concurrency.*` 7 + `PushNotificationTest` 6 + `ProposalIdempotencyTest` 11 = **24/24 통과.**
결과 로그에서 `ERR Error running script` / `RedisSystemException` / ERROR 로그도 0건이었다.
그 뒤 커밋(`af3b7ff` 시드, `3198f74` 문서)은 **자바를 건드리지 않았다** — 테스트는 자기 시드를 직접 심으므로
결과는 그대로 유효하다. **아래 사용자 작성 파일은 그 테스트에 포함되지 않았다.**

### 작업 트리에 커밋 안 된 것

`validation/pubg/PubgConditoinValidator.java` — **사용자가 작성 중**이다. 사용자에게 묻기 전에 고치지 마라.

지금은 **컴파일되고 `@Service` 라 빈으로 떠 있다** — 즉 PUBG 요청이 이미 이 validator 를 탄다.
갱신 시점에 코드를 읽고 확인한 것 (사용자에게 전할 것):

- **43~45행 분기가 뒤집혀 있다.** `if (tierRule.equals("EXIST")) return tier.equals("NONE");` —
  랭크 모드에 진짜 티어(`GOLD_2`)를 보내면 거절되고, `"NONE"` 을 보내면 통과한다
- **`NONE` 모드는 아래 ZSET 조회로 떨어진다.** 티어가 없으면(null) 조회가 터지고, `"NONE"` 이면 `score == null` 로 거절된다 — 일반 모드가 전부 막힌다
- **티어 없음 표기가 롤과 다르다.** 롤 validator 는 `NONE` 모드에 **tier 가 null 이어야** 통과시킨다(tier 를 실어 오면 400). 이 파일은 문자열 `"NONE"` 을 전제한다. 게임마다 클라이언트 규약이 달라지지 않게 맞춰야 한다
- `command.getKeyCondition()` null 가드가 없다(DTO 에 `@NotNull` 없음 → NPE → 500). `LolConditionValidator` 첫 두 가드 참고 — `keyCondition.type == PLATFORM` 확인도 없다
- 파일명 오타(`Conditoin`)
- 모드 존재는 모드 HASH 의 `tierRule` 로 본다 — 모드 목록 SET 을 없앤 결정과 맞다

**그리고 validator 가 먼저 떠서 생기는 일:** PUBG 요청이 검증을 통과하면 `claim-request.lua` 가
활성 요청을 선점(INV-1)한 뒤 `MatchTrigger` 가 PUBG `CandidateRule` 을 못 찾아
`IllegalArgumentException("파티 배정 규칙이 없는 게임")` 으로 끝난다. `@Async` 라 요청은 201 로 나가고,
그 사용자는 claim 의 `EXPIRE 60` 이 끝날 때까지 **60초 동안 다른 매칭을 못 잡는다.** `PubgCandidateRule` 이 붙기 전까지 그렇다.

---

## 2. 사용자가 정한 것 (2026-09-15)

- **폴더는 아무것도 지우지 않는다.** `domain/lol/LolPosition` 은 쓰인다.
- **`domain/` 구조는 그대로 유지한다.** `domain/common/` 으로 옮기지 않는다.
- **PUBG 모드는 8개** (NORMAL/RANKED × DUO/SQUAD × TPP/FPP).

---

## 3. 다음 할 일

### A. PUBG 구현 (진행 중)

**시드는 들어갔다(`seed/gameconfig.redis` 의 PUBG 섹션, 생성·검증 완료). 코드는 아직 없다.**
모드 목록 SET(`qm:gameconfig:modes:*`)은 사용자 결정으로 **없앴다** — 모드 존재는 모드 HASH 로 판단한다.

| 키 | 내용 |
|---|---|
| `qm:gameconfig:PUBG:{modeKey}` | `NORMAL_{DUO,SQUAD}_{TPP,FPP}` → `tierRule NONE` / `RANKED_{DUO,SQUAD}_{TPP,FPP}` → `EXIST`. 듀오 2, 스쿼드 4. `positionUniqueness false` |
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
- `validation/pubg/` — 위 §1 참고
- **붙이는 자리는 이미 있다.** `MatchConditionValidator` · `MatchTrigger` · `MatchCancelService` 가 전부
  `supports(GameKey)` 로 게임별 구현을 고른다. PUBG 는 `CandidateRule` 구현체를 `@Component` 로 하나 두면 된다
- `rule/pubg/PubgPartyKeys` — `LolPartyKeys` 가 `qm:party:open:LOL:` 과 `qm:gameconfig:LOL:` 을 **박아 두었으므로**
  재사용할 수 없다. 색인 키는 `qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{STEAM|KAKAO}` (+ 랭크는 `:{티어}` 를 Lua 가 붙인다)
- `rule/pubg/PubgCandidateRule` + Assigner
- `redis/pubg/*.lua` — **LoL 스크립트를 고쳐 쓰지 말고 자기 디렉터리에** (D-7)
- **PUBG 동시성 테스트** — 스크립트를 나눈 대가다(CLAUDE.md §4 "게임마다 테스트")

미확인: PUBG 에 배치 전(UNRANKED) 상태가 있는지 / 단이 없는 MASTER·SURVIVOR 를 몇 단계로 세는지 /
한국 서버 FPP 큐 유무(리전별로 패치마다 바뀜).

### B. 만료 처리 + 확정 후속 (INV-4/5 의 남은 구멍)

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
- `CLAUDE.md` §3 "`MATCH_PROPOSAL_EXPIRED`/`MATCH_CONFIRMED` 는 **확정**·만료가 미구현" — 확정은
  구현됐고 알림만 없다. 낡은 문장.
- `contracts/openapi.yaml` 이 아직 `PLAY_STYLE` — 계약 파일이라 안 고쳤다(`contracts/README.md` #14 에 기록).

---

## 4. 이미 끝나서 다시 보지 않아도 되는 것

`decline` 의 무조건 `cancel()`(가드 들어감) / `LolConditionValidator` 키 문자열 하드코딩
(`keys.tierRangeKey` 로) / `TieredAssigner` 의 `TierRange` 죽은 코드 / 문서 전반의
`TABLE`·`WINDOW`·`maxTierGap`·`LolTier` 서술 / `CLAUDE.md` INV-2·4·5 상태 열.

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
