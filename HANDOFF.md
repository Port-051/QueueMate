# HANDOFF — 다음 세션 인계

**작성:** 2026-09-15 (화) 12:29 KST
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

### ⚠️ 테스트를 마지막으로 돌린 뒤에 코드가 바뀌었다

마지막 통과 기록은 `concurrency.*` 7건 + `PushNotificationTest` 6건이다. 그 뒤에
validator(`NONE` 모드에 tier 가 오면 400), `TieredAssigner` 정리, enum 삭제,
`KeyConditionType` 변경이 들어갔다. **컴파일만 확인했다.**
`ProposalIdempotencyTest`(11건)는 티어 변경 뒤 한 번도 안 돌렸다.

→ **다음 세션 첫 작업으로 셋 다 돌려라.** 방법은 아래 §5.

### 작업 트리에 커밋 안 된 것

`validation/pubg/PubgConditoinValidator.java` — **사용자가 작성 중**이다(플랫폼 값 검증을
시작한 상태). 건드리지 말고, 이어서 할지 사용자에게 물어라. 참고로:
- 파일명 오타(`Conditoin`), `@Component` 가 없어 **빈으로 등록되지 않는다**
- `command.getKeyCondition()` 이 null 일 수 있다(DTO 에 `@NotNull` 없음) — `LolConditionValidator` 첫 가드 참고

---

## 2. 사용자 결정 대기 — 시작 전에 물어라

1. **"lol 폴더 지워라"가 어느 폴더인가.** `domain/lol/` 은 `LolPosition.java` 하나만 남았는데
   `LolCandidateRule` · `PartyLeaver` · `LolConditionValidator` 3곳이 써서 지우면 빌드가 깨진다.
   빈 폴더는 `rule/pubg/`, `rule/valorant/`, `validation/valorant/` 이다.
2. **`domain/common/` 구조.** "공통인 건 common 폴더 하나에" 요청. `domain/` 최상위 8개
   (`GameKey` `KeyConditionType` `VoicePreference` `PlayPurpose` `ActiveRequest`
   `CancelResult` `MatchRequestStatus` `ProposalResult`)를 `domain/common/` 으로 옮기고
   `domain/lol/` 을 남기는 안을 제시했고 **답을 못 받았다.** import 가 넓게 바뀌니 한 번에 해라.
3. **PUBG 모드를 8개(TPP/FPP 분리)로 갈지.** 시점은 파티 구성을 막지 않는 큐 선택이라
   `modeKey` 에 접기로 했으나 확정 답은 없다.

---

## 3. 다음 할 일

### A. PUBG 구현 (진행 중)

**설계는 끝났고 시드·코드는 아직 없다.**

| 키 | 내용 |
|---|---|
| `qm:gameconfig:PUBG:{modeKey}` | `NORMAL_{DUO,SQUAD}_{TPP,FPP}` → `tierRule NONE` / `RANKED_{DUO,SQUAD}_{TPP,FPP}` → `EXIST`. 듀오 2, 스쿼드 4. `positionUniqueness false` |
| `qm:gameconfig:modes:PUBG` | 위 8개 |
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
- `seed/gameconfig.redis` 에 PUBG 섹션
- `validation/pubg/` — 위 §1 참고
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

- **Docker 엔진이 꺼져 있고 `redis-server` 가 설치돼 있지 않다.** `sudo` 는 비밀번호를 요구한다.
- 지난 세션은 **Redis 7.2.5 를 소스에서 빌드해 스크래치패드에 두고 포트 6390** 으로 띄웠다.
  **스크래치패드는 세션마다 새로 생기므로 그 바이너리는 없다.** 다시 빌드하거나, 사용자에게
  Docker Desktop 을 켜 달라고 하거나, `! sudo apt install redis-server` 를 직접 쳐 달라고 해라.
- 붙이기: `REDIS_HOST=127.0.0.1 REDIS_PORT=6390 ./gradlew test --tests '...'`
- **6379 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 절대 건드리지 마라.**
- 끝나면 Redis 종료 + `./gradlew --stop`. `bootRun` 금지.

```bash
cd backend
./gradlew compileJava compileTestJava
./gradlew test --tests 'com.queuemate.matching.concurrency.*'
./gradlew test --tests 'com.queuemate.matching.notification.PushNotificationTest'
./gradlew test --tests 'com.queuemate.matching.proposal.ProposalIdempotencyTest'
```
