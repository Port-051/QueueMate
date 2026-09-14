# 실험 시나리오

각 실험은 **가설 → 절차 → 측정 지표 → 실측 결과 → 해석 포인트** 로 쓴다.
`docs/PERFORMANCE_EVIDENCE.md` 가 지키는 규칙("모든 숫자는 파일에서 직접 읽은 값이며
추정치·보간치는 없다")을 여기서도 따른다. **아래 숫자는 전부 실제로 돌려서 나온 값이다.**
돌리지 않은 실험은 그렇게 적어 두었고, 확인하지 못한 것은 "미확인" 으로 남겼다.

포트폴리오에 그대로 옮길 요약본은 `RESULTS.md` 에 따로 있다.
이 문서는 **절차와 해석**을 담는다.

## 실행 상태

| 실험 | 상태 | 한 줄 결과 |
|---|---|---|
| 1 — 베이스라인 페일오버 | **실행 완료 (2026-09-09)** | Redis 레벨 쓰기 불가 **6.43초**. 그중 79% 가 감지(`down-after=5000`)다 |
| 2 — 클라이언트 토폴로지 | **실행 완료 (2026-09-09)** | 판 C 앱 다운타임 **6.04초**(≈Redis 기준선). 판 B 는 **조용한 유실 202건** |
| 3 — 감지 시간 튜닝 | **실행 완료 (2026-09-09)** | `down-after=2000` 이면 3.42초로 줄지만 3초 정지에 오탐. **안 바꾸기로 했다** |
| 4 — 락 유실 재현 | **미실행** | 건너뛰기로 결정. 근거는 실험 4 절 맨 앞 |
| 5 — 스플릿 브레인 | **미실행** | 건너뛰기로 결정. 축소판은 실험 3 중에 우연히 관측했다 |
| 6 — 재시도·서킷 | **실행 완료 (2026-09-09)** | 서킷이 죽은 서버 호출을 1,290건 → **28회**로 줄였다. 재배정 성공은 **0건** |

## 실행 환경 (실측값이 나온 판)

| | |
|---|---|
| Redis | `redis:7-alpine`, master 1 + replica 2 + Sentinel 3 |
| Sentinel | `quorum=2`, `down-after=5000`, `failover-timeout=60000` |
| 노드 | 6379(`qm-ha-master`) / 6380(`qm-ha-replica1`) / 6381(`qm-ha-replica2`), Sentinel 26379/26380/26381 |
| announce | `HOST_IP` (WSL2 eth0) |
| 앱 | Spring Boot 4.1.1, Redisson 3.52.0, Lettuce |
| 장애 주입 | `docker kill`(SIGKILL). `t0` 는 `.last-kill-ms` |

## 측정 기록

| 실험 | 조건 | Redis `+switch-master` | 쓰기/앱 복구 | 실패 요청 | 조용한 유실 |
|---|---|---|---|---|---|
| 1 | 앱 없음, Redis 레벨 프로브 | t0+6,270ms | t0+6,347ms (**6.43초**) | 연결실패 31 tick | 측정 안 함 (앱 없음) |
| 2-A | Lettuce·Redisson 둘 다 단일 | — | **복구 안 됨** | 503 25건 | 0건 |
| 2-B | Lettuce=Sentinel, Redisson=단일 | — | HTTP 만 복구 | 503 37건 | **202건** |
| 2-C | 둘 다 Sentinel | — | t0+6,203ms (**6.04초**) | 503 3건 | 0건 |
| 3 | `down-after=2000`, 진짜 kill | — | t0+3,345ms (**3.42초**) | 연결실패 16 tick | 측정 안 함 |
| 6-OFF | 판 C + 플래그 OFF, 동시 10스레드 | — | — | 503 30건 | 6건 |
| 6-ON | 판 C + 플래그 ON, 동시 10스레드 | — | — | 503 360건 | **0건** |
| 6-B-ON | 판 B + 플래그 ON, 동시 10스레드 | — | — | 503 360건 | 1,281건 |

전제:
- `README.md` §1 의 구동 절차를 마쳤다
- 기존 개발용 `qm-redis` 는 내렸다 (6379 충돌)
- 앱을 붙이는 실험(2·6)은 `--spring.profiles.active=sentinel` 로 띄운다.
  **프로파일 없이는 실험 2·6 이 성립하지 않는다** — 앱이 죽은 master 만 계속 본다.
  왜 `application.yaml` 이 아니라 프로파일인지는 `README.md` §1-3 에 적었다.

---

## 실험 1 — 베이스라인 페일오버 측정

> **실행 완료 (2026-09-09)** — Redis 레벨만 쟀다. 앱은 띄우지 않았다.

### 가설

`docker kill` 로 master 를 죽였을 때 **Redis 가 새 master 를 세우는 시간과
애플리케이션이 실제로 다시 쓰기 시작하는 시간은 크게 다르다.**
`down-after-milliseconds` 만 보고 다운타임을 예상하면 실제보다 짧게 잡는다.

### 실제로 돌린 절차 (계획과 다르다)

계획에는 k6(`run-probe.sh`)로 앱까지 같이 재는 것으로 적혀 있었다. 그렇게 하지 않았다.
**앱을 섞으면 "Redis 가 못 받은 시간" 과 "클라이언트가 몰라서 못 보낸 시간" 이 한
숫자에 섞인다.** 그 둘을 나누는 것이 실험 1 → 실험 2 의 구조이므로, 기준선에는
클라이언트 변수가 없어야 한다.

그래서 호스트에서 도는 쓰기 프로브를 새로 만들었다 (`scripts/redis-write-probe.py`).
매 200ms tick 마다 두 가지를 한다.

1. Sentinel 에게 `SENTINEL get-master-addr-by-name mymaster` 로 지금 master 를 묻는다
2. 그 주소에 `SET qm:probe <t>` 를 보낸다

```bash
./scripts/reset.sh && ./scripts/up.sh

# 터미널 1 — 60초 동안 200ms 간격으로 쓰기 프로브
./scripts/redis-write-probe.py 60 out/e1.csv

# 터미널 2 — 프로브 시작 후 10초쯤에 master 를 죽인다 (t0 를 .last-kill-ms 에 남긴다)
./scripts/kill-master.sh

# 끝나면 같은 t0 기준으로 해석
./scripts/analyze-probe.py out/e1.csv .last-kill-ms
./scripts/failover-timeline.sh
```

> **이 프로브는 매 tick 마다 Sentinel 에 새로 묻는 "이상적 클라이언트" 다.**
> 캐시한 주소를 붙들지 않고, 커넥션 풀도 없고, 토폴로지 갱신 지연도 없다.
> 그래서 여기서 나온 값은 **다운타임의 하한선**이다. 실제 앱이 이보다 나쁠 수 있는지가
> 실험 2 의 질문이다.

`docker exec` 로 컨테이너 안에서 재지 않은 이유도 같은 종류다 — busybox `date` 는
밀리초를 못 찍고 `docker exec` 는 호출마다 수십 ms 가 붙는다. 6초를 재는데 수십 ms
오차가 붙는 도구를 쓰면 ④ 구간(아래)이 통째로 노이즈에 묻힌다.

### 측정 지표

**두 계열을 반드시 분리해서 잰다.** 이게 이 실험의 전부다.

| 계열 | 지표 | 출처 | 정의 |
|---|---|---|---|
| Redis 측 | `t_odown` | `failover-timeline.sh` 의 `+odown` | 정족수가 "죽었다" 에 동의한 시각 |
| Redis 측 | `t_promote` | `+promoted-slave` | replica 에 `REPLICAOF NO ONE` 을 보낸 시각 |
| Redis 측 | `t_switch` | `+switch-master` | Sentinel 이 알려 주는 주소가 바뀐 시각. **Redis 측 복구 완료** |
| 쓰기 측 | 마지막 성공 / 첫 성공 | `analyze-probe.py` | 장애 시작의 상한 / 다시 쓸 수 있게 된 시점 |
| 쓰기 측 | 쓰기 불가 구간 | `analyze-probe.py` | 위 둘의 차 |
| 쓰기 측 | 실패 유형 | `analyze-probe.py` | `Connection refused` / `reset by peer` / `READONLY` 를 섞어 세지 않는다 |

### 실측 결과 (2026-09-09)

**쓰기 불가 구간 = 6.43초.**

| 값 | 실측 |
|---|---|
| t0 이전 마지막 성공 | `t0 - 85ms` |
| t0 이후 첫 성공 | `t0 + 6,347ms` — 승격된 **6381** 에 썼다 |
| **쓰기 불가 구간** | **6.43초** |
| 실패 tick | `Connection refused` **30회** + `Connection reset by peer` **1회** |
| `READONLY` | **0회** |

Sentinel 이벤트 (`failover-timeline.sh`, 같은 `t0` 기준).

| 이벤트 | t0+ (ms) | 뜻 |
|---|---|---|
| `+sdown` | 5,075 | 한 Sentinel 이 "안 보인다" 고 판단 |
| `+odown` | 5,148 | 정족수 동의 (quorum 3/2) |
| `+try-failover` | 5,148 | |
| `+elected-leader` | 5,252 | 집행할 Sentinel 이 정해짐 |
| `+selected-slave` | 5,312 | **6381** 선택 |
| `+promoted-slave` | 6,196 | `REPLICAOF NO ONE` |
| `+switch-master` | 6,270 | Sentinel 이 알려 주는 주소가 바뀜 |
| `+failover-end` | 6,973 | |

**구간 분해 — 6.43초가 어디로 갔나.**

| 구간 | 경계 | 시간 | 비중 |
|---|---|---|---|
| ① 감지 | `t0` → `+sdown` | **5.08초** | **79%** |
| ② 합의·선출 | `+sdown` → `+elected-leader` | 0.18초 | 3% |
| ③ 승격 | `+elected-leader` → `+switch-master` | 1.02초 | 16% |
| ④ 클라이언트 인지 | `+switch-master` → 첫 쓰기 성공 | 0.077초 | 1% |

네 구간의 합이 6.347초 = 첫 성공 시각이다. **다운타임의 4/5 는 `down-after=5000`
그 자체다.** 이것이 실험 3 의 출발점이다.

**replica 선정.** 6380 과 6381 중 6381 이 뽑혔다. 죽기 직전 복제 오프셋이 6381 쪽이
앞서 있었다(**6952 vs 6523**). `replica-priority` 가 같을 때 오프셋이 큰 쪽을 고른다는
규칙과 일치한다 — 문서로만 알던 것을 로그와 오프셋으로 확인했다.

### 예상 → 실측 → 왜 달랐나

| 예상 | 실측 | 왜 |
|---|---|---|
| `+odown` 은 `down-after`(5000ms) 근처에서 나온다 | `+odown` t0+5,148ms | **맞았다.** Sentinel 이 매 1초 PING 을 보내므로 감지가 이 값에 지배된다 |
| `+odown` → `+switch-master` 는 **짧다.** 선출과 `REPLICAOF NO ONE` 뿐이다 | **1.12초.** 전체의 17% | **틀렸다.** 승격(`+selected-slave` → `+promoted-slave`)에만 0.88초가 든다. 승격은 명령 한 줄이 아니라 replica 가 `REPLICAOF NO ONE` 을 처리하고 Sentinel 이 그 역할 변경을 `INFO` 로 다시 확인하는 왕복이다 |
| 프로브 실패가 `status=0`(연결 실패)과 `503` 두 종류로 갈린다 | 전부 연결 실패(31 tick) | 이 실험은 **앱을 안 띄웠다.** 503 은 앱이 있어야 나온다 — 실험 2 로 넘어간 항목이다 |
| 강등된 구 master 에 붙어 `READONLY` 를 볼 수 있다 | `READONLY` **0회** | 이 프로브가 **매 tick Sentinel 에 새로 묻기 때문**이다. 주소를 캐시하는 클라이언트라면 봤을 것이다 |

### 해석 포인트

1. **다운타임의 79% 가 감지다.** 클라이언트를 아무리 잘 만들어도 ④ 구간(0.077초)
   말고는 줄일 데가 없다. 줄이려면 `down-after` 를 건드려야 하고, 그건 공짜가 아니다
   → 실험 3.

2. **이 6.43초는 하한선이지 실측 다운타임이 아니다.** 이상적 클라이언트가 낸 값이다.
   실제 앱이 얼마나 더 나쁜지는 실험 2 에서 잰다.
   (결과를 미리 말하면 **거의 같았다.** 그게 실험 2 의 가장 큰 발견이다.)

3. `assign-gap.sh` 가 재는 "조용한 실패"(201 은 받았는데 `partyId` 가 안 붙은 요청)는
   앱이 있어야 성립하므로 여기서는 재지 않았다. 실험 2 에서 `verify-assign.py` 가
   같은 것을 Redis 상태로 직접 판정한다.

4. `docker stop`(SIGTERM)과 비교해서 "정상 종료 인사가 있고 없고" 의 값을 재는 것은
   **미실행**이다. 실제 장애에는 인사가 없으므로 보고할 숫자는 `docker kill` 쪽이고,
   비교값은 있으면 좋은 참고일 뿐이라 우선순위에서 밀렸다.

---

## 실험 2 — 클라이언트 토폴로지 갱신 (이 실험이 핵심이다)

> **실행 완료 (2026-09-09)** — 판 A / B / C 세 판 전부. 단, 판 C 안의 축 1·축 2 흔들기는 **미실행**이다(아래 "안 돌린 것" 참고).

### 가설

**실제 다운타임을 지배하는 것은 Sentinel 의 감지 속도가 아니라 클라이언트가
새 master 주소를 언제 반영하느냐다.** 같은 Redis 설정에서 클라이언트 설정만
바꿔도 앱 측 복구 시간이 몇 배 차이 난다.

이 프로젝트는 Redis 클라이언트가 **둘**이다. 하나만 고치면 절반만 복구된다.

| 클라이언트 | 담당 | 붙는 코드 |
|---|---|---|
| Lettuce (`StringRedisTemplate`) | 파티 HASH / needs ZSET / active-request / Lua 전부 | Spring Boot 자동 설정 |
| Redisson (`RLock`) | `qm:lock:pool:*` 만 | `RedissonConfig.java` 에 **자바 코드로 박혀 있다** |

> **결론부터**: 이 가설은 **틀렸다.** 다만 틀린 방식이 흥미롭다 —
> 클라이언트는 다운타임을 "몇 배" 로 늘리는 연속적인 변수가 아니라,
> **유한과 무한을 가르는 스위치**였다. 아래 "예상 → 실측 → 왜 달랐나" 를 보라.

### 실제로 돌린 절차

세 판 모두 **같은 조건**이다. 단일 스레드 프로브, 200ms 간격, 60초, `t0 = +10초` 에 kill.

```bash
./scripts/reset.sh && ./scripts/up.sh
docker exec -i qm-ha-master redis-cli < ../seed/gameconfig.redis

# 앱을 판에 맞게 띄운다 (판 전환 명령 전체는 README.md §6)
#   판 A : 프로파일 없이
#   판 B : --spring.profiles.active=sentinel + QUEUEMATE_LOCK_SENTINEL_NODES=""
#   판 C : --spring.profiles.active=sentinel

# 터미널 1 — 60초 프로브
./scripts/http-probe.py 60 out/e2-C.csv C

# 터미널 2 — 프로브 시작 후 10초에 kill
./scripts/kill-master.sh

# 끝나면 Redis 상태로 실제 배정 여부를 판정한다
./scripts/verify-assign.py out/e2-C.csv .last-kill-ms
```

**측정의 핵심은 지표를 셋으로 나눈 것이다.**

| 지표 | 무엇 | 어떻게 |
|---|---|---|
| HTTP 응답 분포 | 컨트롤러가 무엇을 돌려줬나 (201 / 503) | `http-probe.py` 의 CSV |
| **실제 배정 성공** | 진짜로 파티에 들어갔나 | `verify-assign.py` — Redis 의 `qm:user:active-request:{userId}` 에 `partyId` 가 있나 |
| **둘의 차이** | HTTP 는 성공했는데 배정이 안 된 요청 | 위 둘의 차. **이것이 "조용한 유실" 이다** |

세 번째 지표가 이 프로젝트 고유의 함정이다. 컨트롤러는 `claim-request.lua` 만 돌리고
201 을 돌려주고, 파티 배정은 `@Async` 로 뒤에서 돈다(`MatchTrigger`). 그 안에서 터진
`DataAccessException` 은 `GlobalExceptionHandler` 에 닿지 못하고 `AsyncConfig` 의
uncaught 핸들러가 **로그만 남기고 삼킨다.**
→ **HTTP 만 보면 "잘 되고 있다" 고 오판한다.** 실제로 그렇게 됐다(판 B).

프로브 모드는 `NORMAL_5` 다. 처음에 쓰려던 `RANKED_FLEX_5` 는 `tierRule=WINDOW` 라
티어가 필수여서 프로브가 전부 400 으로 튕겼다. `NORMAL_5` 는 `tierRule=NONE` 이다.
(2026-09-14 이후 `tierRule` 값이 `NONE`/`EXIST` 로 바뀌어 이 서술은 당시 기준이다.)

### 실측 결과 (2026-09-09)

| | **판 A** (둘 다 단일) | **판 B** (Lettuce 만 Sentinel) | **판 C** (둘 다 Sentinel) |
|---|---|---|---|
| 총 요청 | 66건 | 278건 | 269건 |
| HTTP 201 | 41건 (62.1%) | 241건 (86.7%) | **266건 (98.9%)** |
| HTTP 503 | 25건 (37.9%) | 37건 (13.3%) | 3건 (1.1%) |
| 실제 배정 성공 | 41건 | 39건 (14.0%) | **266건** |
| 유실 | 25건 | **239건 (86.0%)** | 3건 |
| **HTTP 성공인데 배정 실패** | **0건** | **202건 (72.7%)** | **0건** |
| t0 이후 복구 | **없음 (50초가 지나도 영구 장애)** | HTTP 만 복구. 배정은 끝까지 0건 | **t0+6,203ms** |

**t0 기준 구간별.**

| 구간 | 판 A (요청 / 201 / 배정) | 판 B (요청 / 201 / 배정) |
|---|---|---|
| t0 이전 | 41 / 41 / 41 | 39 / 39 / 39 |
| t0 ~ +10초 | 5 / 0 / 0 | 41 / 4 / **0** |
| +10 ~ +30초 | 10 / 0 / 0 | **99 / 99 / 0** |
| +30초 ~ | 10 / 0 / 0 | **99 / 99 / 0** |

**판 A — 영구 장애.** t0 이후 복구가 **0건**이다. 50초가 지나도 죽은 6379 만 본다.
"Sentinel 을 띄우기만 하고 클라이언트를 안 고치면 아무 일도 안 일어난다" 의 증거다.
총 요청이 66건뿐인 것 자체도 지표다 — 실패 요청이 `timeout: 2s` 만큼 매달리는 바람에
같은 60초에 처리량이 1/5 로 떨어졌다(t0 이후 25건이 50초를 썼다 = 건당 약 2초).

**판 B — 가장 건강해 보이는 판이 가장 망가진 판이었다.**
처리량 278건으로 **세 판 중 최고**이고 201 비율도 86.7% 다. 그런데 배정 성공은 39건,
그것도 **전부 t0 이전**이다. t0 이후 201 을 받은 요청 **202건이 전부 조용히 사라졌다**
(첫 건 t0+9,360ms, 끝 건 t0+49,764ms). Lettuce 는 새 master 로 옮겨 가 claim 이 성공하니
201 이 나가고, Redisson 은 죽은 6379 만 보므로 `PoolLock` 에서 터지는데 그 예외는
`@Async` 뒤에서 삼켜진다.

앱 로그에서 확인한 것:

| 로그 | 건수 | 어디서 |
|---|---|---|
| `비동기 작업 실패` | **101건** | `AsyncConfig` 의 uncaught 핸들러 |
| `Redis 장애로 매칭 요청을 거절한다` | 37건 | `GlobalExceptionHandler` (= 503 37건과 일치) |

> **미확인**: 로그 101건과 조용한 유실 202건이 맞지 않는다. `matchingExecutor` 큐에
> 쌓인 채 끝까지 실행되지 않은 작업이 있는 것으로 보이지만 **확인하지 못했다.**
> 확인하려면 `ThreadPoolTaskExecutor` 의 `queue.size` / `completedTaskCount` 를
> 같이 찍어야 한다.

**판 C — Redis 기준선과 사실상 같다.**

| t0+ | 무슨 일 |
|---|---|
| 159ms | 첫 503 |
| 2,193ms | 503 (직전 요청이 **2,034ms** 걸림) |
| 4,198ms | 503 (**2,005ms**) |
| **6,203ms** | **201 복구** |
| 7,302ms | 요청 간격이 201ms 로 정상화 |

앱 레벨 다운타임 **≈ 6.04초**. 실험 1 의 Redis 기준선 6.43초와 사실상 같다.
(두 값의 정의가 다르다 — 실험 1 은 "마지막 성공 → 첫 성공", 판 C 는 "첫 실패 → 첫 성공"
이라 200ms tick 만큼의 차이가 난다. 겹쳐 놓고 보면 같은 구간이다.)

**`timeout: 2s` 가 실제로 걸린다는 것도 여기서 나왔다.** 실패한 요청들의 소요 시간이
정확히 2,034 / 2,005 / 2,005ms 다. Boot 4.1.1 이 `TimeoutOptions.enabled()` 를 기본으로
넣는다는 사실(`docs/failover-retry-guide.md` §7)의 실측 확인이다.

60초 × 5 req/s = 300건이 나가야 하는데 269건이다. **부족한 31건이 6초 구간에 삼켜진
처리량**이다 — 다운타임은 "실패한 요청" 뿐 아니라 "아예 못 보낸 요청" 으로도 나타난다.

### 예상 → 실측 → 왜 달랐나

| 예상 | 실측 | 왜 |
|---|---|---|
| **클라이언트가 다운타임을 지배한다.** 설정에 따라 앱 복구가 몇 배 차이 난다 | 판 C 6.04초 ≈ Redis 6.43초. **클라이언트 몫이 사실상 0 이다** | 이 앱의 Lettuce 는 Sentinel pub/sub(`+switch-master`)로 즉시 갱신하고, 프로브도 매 요청 새로 보내는 구조라 **죽은 주소를 오래 붙들 이유가 없다.** 실험 1 의 "이상적 클라이언트" 와 실제 앱이 거의 같았다 |
| 판 A 는 영원히 복구되지 않는다 | **맞았다.** t0 이후 복구 0건 | |
| 판 B 는 claim 은 돌아오는데 조용한 실패가 줄지 않는다 | **맞았다.** 그것도 예상보다 심하다 — 조용한 유실이 **72.7%** | |
| 판 C 에서 `Δ_data` 와 `Δ_lock` 이 서로 다르게 나온다 | **구분되지 않았다.** 판 C 의 조용한 유실이 0건이라 `Δ_lock` 을 잴 대상 자체가 없다 | 정상 설정에서는 claim(Lettuce)이 먼저 fail-closed 로 막아 503 을 내므로, 배정(Redisson)까지 도달하는 요청이 장애 구간에 거의 없다. 이 관찰이 실험 6 의 결론으로 이어진다 |
| `TimeoutOptions` 를 켜면 크게 빨라진다 | **해당 없음.** Boot 4.1.1 은 이미 기본으로 켠다 | 그 서술은 **Boot 3.x 기준**이다. `spring-boot-data-redis-4.1.1.jar` 의 `createClientOptions()` 가 이미 넣고 있다 (`docs/failover-retry-guide.md` §7) |

**가설이 틀린 방향이 중요하다.** 클라이언트는 "다운타임을 몇 초 늘리는 변수" 가 아니라
**"유한이냐 무한이냐" 를 가르는 스위치**였다. 제대로 붙이면 Redis 기준선에 딱 붙고,
하나라도 빠뜨리면(판 A/B) 무한으로 돌아간다. 튜닝할 여지가 있는 게 아니라 **켜져 있냐
꺼져 있냐** 의 문제다. 그래서 "판 B 를 만들지 않는 것" 이 이 실험의 유일한 실무 산출물이다.

### 안 돌린 것 (미실행)

| | 왜 |
|---|---|
| **축 1 — Lettuce 옵션 스윕** (`TimeoutOptions` / `DisconnectedBehavior` / `connectTimeout`) | 판 C 의 클라이언트 몫이 0.077초(실험 1 ④ 구간)로 이미 측정 한계에 가깝다. **줄일 것이 없는 구간을 스윕해도 노이즈만 나온다.** `TimeoutOptions` 항목은 애초에 Boot 4.1.1 에서 무효였다 |
| **축 2 — Redisson `scanInterval` 스윕**(200 / 1000 / 5000ms) | 위와 같은 이유. 판 C 에서 락 경로 실패가 0건이라 흔들 대상이 없다 |
| Redisson 3.52.0 의 `scanInterval` **기본값** | **미확인.** 기동 로그로도 디버거로도 확인하지 않았다. 이 문서는 여전히 기본값을 단정하지 않는다 |

### 해석 포인트

1. **튜닝할 곳은 Redis 도 클라이언트도 아니었다.** 판 C 에서 클라이언트 몫은 0.077초고
   Redis 감지가 5.08초다. 그래서 다음 질문은 자동으로 "감지를 줄일까?" 가 된다 → 실험 3.

2. **판 B 가 이 프로젝트에 실재하는 위험이라는 것이 숫자로 확인됐다.** Lettuce 는 설정으로
   바꿀 수 있고 Redisson 은 자바 코드에 박혀 있다. 급할 때 `application.yaml` 만 고치면
   정확히 판 B 가 된다. 그 판은 **모든 겉보기 지표가 최고로 나온다** — 처리량 1위,
   201 비율 86.7%. 대시보드만 보는 사람은 절대 못 찾는다.
   → `RedissonConfig.java` 에 "여기도 같이 고쳐야 한다" 는 근거를 남기는 것이
   이 실험의 실무 산출물이다.

3. Lettuce 와 Redisson 은 커넥션을 공유하지 않는다. 그래서 복구 시각이 서로 다를 수
   있고, 한쪽만 보고 "복구됐다" 고 판단하면 안 된다 — **판 B 가 정확히 그 상태다.**

4. **`@Async` 예외 삼킴이 여기서 처음으로 숫자가 됐다.** 202건은 "사용자가 매칭 중
   화면을 60초 동안 보다가 아무 설명 없이 끝나는" 건수다.
   자세한 것은 `docs/lock-safety-analysis.md` §1-4.

---

## 실험 3 — 감지 시간 튜닝 트레이드오프

> **실행 완료 (2026-09-09)** — `down-after` 축만. `quorum` / `failover-timeout` 스윕과 `tc netem` 판은 **미실행**이다.

### 가설

`down-after-milliseconds` 를 줄이면 페일오버가 빨라진다. 대신 **일시적인 지연을
장애로 오인하는 오탐이 늘어난다.** 오탐 페일오버는 그 자체로 다운타임이고,
멀쩡한 master 를 강등시켜 복제 재동기화까지 유발한다.

실험 1 이 이 질문을 강제했다 — **다운타임 6.43초 중 5.08초(79%)가 감지다.**
줄일 데가 거기밖에 없다.

### 오탐 유발 도구를 바꿔야 했다 (DEBUG SLEEP → docker pause)

계획은 `scripts/debug-sleep.sh` 로 Redis 이벤트 루프를 얼리는 것이었다. **못 썼다.**

```
$ docker exec qm-ha-master redis-cli DEBUG SLEEP 3
ERR DEBUG command not allowed. If the enable-debug-command option is ...
```

**Redis 7 부터 `enable-debug-command` 기본값이 `no` 다.** 그리고 이 값은
`CONFIG SET` 으로도 못 바꾼다 — 기동 시 설정 파일이나 커맨드라인으로만 켤 수 있다.
컨테이너를 다시 만들어야 하는데, 그러면 "설정을 바꾸지 않고 오탐만 유발한다" 는
실험 조건이 깨진다.

대신 **`docker pause`(SIGSTOP)** 를 썼다 (`scripts/false-positive-test.sh`).

| | `DEBUG SLEEP` | `docker pause` (SIGSTOP) |
|---|---|---|
| 무엇이 멈추나 | Redis 이벤트 루프 | **프로세스 전체** |
| TCP 연결 / 포트 | 유지 | **유지** |
| PING 응답 | 안 온다 | **안 온다** |
| 깨어난 뒤 | 정상 | **정상** |
| Redis 7 에서 | **못 쓴다** | 쓴다 |

**Sentinel 이 보는 것은 "PING 이 안 온다" 하나뿐이므로 둘은 같은 자극이다.**
현실의 대응물도 같다 — RDB fork 지연, 큰 키 `DEL`, 무거운 Lua, swap.

```bash
# 얼릴 초 / 라벨. 얼린 노드는 N초 뒤 멀쩡히 돌아온다.
# 그런데도 페일오버가 났다면 그것이 오탐이다.
./scripts/false-positive-test.sh 3 A1
```

### 실측 결과 (2026-09-09)

**(a) 오탐 — 같은 3초 정지에 판이 갈린다.**

| 판 | `down-after` | 정지 | 오탐 | Sentinel 이벤트 (t0+ms) |
|---|---|---|---|---|
| **A1** | 5000 | 3초 | **없음** | 이벤트 **0건** |
| **A2** | 5000 | 8초 | **발생** | `+sdown` 5,069 / `+odown` 5,239 (quorum 2/2) / `+switch-master` 6,405 / `+failover-end` 6,719. 6379 → 6381 |
| **B1** | **2000** | **3초 (A1 과 같은 조건)** | **발생** | `+sdown` 2,193 / `+odown` 2,896 / `+switch-master` 4,013 / `+failover-end` 5,022. 6381 → 6379 |

**A1 과 B1 이 이 실험의 전부다.** 같은 3초 정지인데 `down-after` 만 5000 → 2000 으로
바꿨더니 오탐이 났다. A2 는 대조군이다 — `down-after=5000` 이어도 정지가 8초면 난다.
즉 경계는 `정지 시간 > down-after` 이고, 그건 예상대로였다.

**(b) 감지 속도 — 진짜 장애일 때.**

`down-after=2000` 으로 바꾸고 실험 1 과 똑같이 `docker kill` 했다.

| | `down-after=5000` (실험 1) | `down-after=2000` |
|---|---|---|
| `+sdown` | t0+5,075ms | **t0+2,053ms** |
| `+odown` | t0+5,148ms | **t0+2,192ms** |
| 첫 쓰기 성공 | t0+6,347ms | **t0+3,345ms** |
| **쓰기 불가 구간** | **6.43초** | **3.42초** |
| 실패 tick | `refused` 30 + `reset` 1 | `refused` 15 + `reset` 1 |

**3.01초가 줄었다.** 감지 구간이 거의 그대로 줄어든 것이고, 승격·클라이언트 인지
구간은 변하지 않았다. 실험 1 의 구간 분해가 그대로 재현된 셈이다.

### 트레이드오프 표

| | `down-after=5000` (현재) | `down-after=2000` |
|---|---|---|
| 진짜 장애 시 쓰기 불가 | 6.43초 | **3.42초** (−3.01초) |
| 3초 정지에 오탐 | **없음** (A1) | **발생** (B1) |
| 8초 정지에 오탐 | 발생 (A2) | 발생 |
| `claim` TTL 60초 대비 | **11%** | 6% |

### 결론 — 안 바꾼다

**이 프로젝트의 시간 예산은 `claim-request.lua` 의 TTL 60초다.**
claim 이 성립한 요청은 60초 안에 배정되면 사용자 입장에서 아무 일도 없었던 것이 된다.

- 6.43초는 그 예산의 **11%**
- 3.42초는 **6%**

**둘 다 넉넉히 들어간다.** 3초를 아껴서 얻는 것이 없다. 반면 잃는 것은 분명하다 —
**정상 운영 중에 반복해서 생기는 3초짜리 정지**(RDB fork, 큰 키 `DEL`, 무거운 Lua,
swap)마다 오탐 페일오버를 사게 된다.

오탐 1회의 값은 이렇다.

| 비용 | 근거 |
|---|---|
| 다운타임 6.43초 | 오탐도 진짜 페일오버와 같은 절차를 밟는다 (B1 에서 `+switch-master` 까지 4.0초) |
| 미복제 데이터 유실 위험 | 승격되는 replica 가 못 받은 쓰기는 사라진다 |
| 구 master 가 자기를 master 로 믿는 창 **약 2초** | 아래 "우연히 관측한 것" |

→ **`down-after=5000` 을 유지한다.**

이 결론은 `CLAUDE.md` §4 의 규칙과도 맞는다. Lua 가 도는 동안 Redis 는 PING 에도
응답하지 않는다. 지금 스크립트들은 루프가 없어 짧지만, `docs/lock-safety-analysis.md`
(E) 안(순회를 Lua 한 덩어리로 접기)을 채택하면 **최악의 단일 명령 시간이 길어진다.**
`down-after` 는 그 값보다 넉넉히 커야 한다. 2000 은 그 여지를 미리 없애는 선택이다.

### 우연히 관측한 것 — 죽은 노드를 되살리면 약 2초간 자기를 master 로 믿는다

`docker start` 로 죽인 노드를 되살리면, 그 노드는 자기 설정 파일(`/data/redis.conf`)
때문에 **약 2초 동안 `role:master` 로 뜬다.** Sentinel 이 그것을 보고 `REPLICAOF` 로
강등시킨다.

**이것이 실험 5(스플릿 브레인)의 축소판이다.** 그 2초 동안 누군가 그 노드에 직접 쓰면
강등 시점에 통째로 사라진다. 실험 5 를 따로 돌리지 않은 근거 중 하나가 이것이다.

### 안 돌린 것 (미실행)

| | 왜 |
|---|---|
| `quorum` 2 ↔ 3 스윕 | 3대에 `quorum=3` 은 Sentinel 한 대만 죽어도 페일오버가 영영 안 되는 구성이라 **채택 후보가 아니다.** 오탐을 줄이는 축으로는 `down-after` 가 이미 답을 줬다 |
| `failover-timeout` 스윕 (10s / 30s / 60s / 180s) | 실측 3판 모두 `+try-failover` 가 **1회**만 찍혔다. 60초로 재시도가 겹치지 않는다는 것이 확인됐으므로 흔들 이유가 없다 |
| `tc netem` 지연 주입 판 | `docker pause` 가 더 강한 자극(PING 이 아예 안 옴)이고 결론이 같은 방향이다. netem 은 "왕복이 `down-after` 를 넘길 때" 를 재는 것이라 경계값이 하나 더 필요한데, 결론(안 바꾼다)이 이미 정해진 뒤라 우선순위에서 밀렸다 |
| 오탐 비용의 정량화 (`+switch-master` → `+slave-reconf-done`) | B1/A2 에서 재동기화 완료까지는 따로 재지 않았다. **미확인** |

### 해석 포인트

1. **`down-after` 를 줄이는 것은 공짜가 아니라는 것이 같은 3초 자극으로 증명됐다.**
   A1(오탐 없음)과 B1(오탐 발생)은 자극이 완전히 같고 설정만 다르다.
   대조가 이렇게 깔끔하게 나온 것은 `docker pause` 가 결정적인 도구라서다.

2. **"빠르게 만들 수 있다" 와 "빠르게 만들어야 한다" 는 다른 질문이다.**
   3.42초는 만들 수 있었다. 그런데 우리 시간 예산에서 6.43초와 3.42초는 둘 다
   충분히 작다. **측정해서 얻은 답이 "안 바꾼다" 인 것도 결과다.**

3. `quorum` 은 "몇 대가 죽었다고 해야 죽은 것으로 볼지" 이고, 실제 집행에는
   **Sentinel 과반의 리더 선출**이 따로 필요하다. 실험 1 의 `+elected-leader` 가 그것이다.
   3대 구성에서 2대가 죽으면 `quorum=1` 로 낮춰도 페일오버가 안 된다.

---

## 실험 4 — 비동기 복제로 인한 락 유실 재현

> **미실행** — 사용자가 건너뛰기로 했다.
>
> **근거 둘.**
> 1. **결론이 자명하다.** Redis 복제는 비동기고 `RLock` 은 master 한 대에만 쓴다.
>    "락을 잡은 직후 master 가 죽으면 승격된 replica 에 그 락이 없다" 는 재현하지
>    않아도 참이다. 재현 스크립트(`lock-loss-repro.sh`)가 하는 일은 `docker pause` 로
>    그 창을 인위적으로 벌리는 것이라, 나오는 답이 이미 정해져 있다.
> 2. **피해를 재려면 임계 구역이 먼저 완성돼야 한다.** 락 유실의 진짜 비용은
>    "정원 초과" 가 아니라 "후보를 건너뛰거나 두 번 보는 것" 이고(§1-6),
>    그것이 사용자에게 보이는 손해가 되려면 **INV-4 / INV-5 (proposal) 과 INV-6
>    (차단 검증)** 이 구현돼 있어야 한다. 지금은 셋 다 미구현이라
>    "락이 유실됐다" 는 사실만 확인하고 **그래서 얼마나 손해인지는 못 잰다.**
>    숫자 없는 재현은 이미 `docs/lock-safety-analysis.md` 가 글로 하고 있다.
>
> 아래 절차는 그대로 남긴다. 위 두 조건이 갖춰지면 이 문서만 보고 돌릴 수 있다.

### 가설

`PoolLock` 은 Redisson `RLock` 이고, `RLock` 은 **master 한 대에만** 락 키를 쓴다.
Redis 복제는 비동기다 — master 는 replica 의 ACK 를 기다리지 않고 `OK` 를 돌려준다.
따라서 **락을 잡은 직후 master 가 죽으면, 승격된 replica 에는 그 락이 없다.**
두 번째 요청이 같은 락을 잡는다. 상호 배제가 깨진다.

`PoolLock` 의 클래스 주석은 이미 절반을 알고 있다.

> **Lua 와 달리 이 락은 저장소가 강제하지 않는다.** Redis 에는 "이 키는 잠겨 있다"는
> 개념이 없다. 파티 데이터를 만지는 모든 코드가 먼저 이 락을 잡는다는 약속을 지켜야만
> 성립한다. (`PoolLock.java:34-36`)

나머지 절반이 이 실험이다. **약속을 다 지켜도 저장소가 락을 잃어버릴 수 있다.**

### 절차 (A) — 스크립트로 재현

```bash
./scripts/reset.sh && ./scripts/up.sh
./scripts/lock-loss-repro.sh
```

`lock-loss-repro.sh` 가 하는 일:

| 단계 | 무엇 | 왜 |
|---|---|---|
| 1 | `docker pause qm-ha-replica1 qm-ha-replica2` | 복제 스트림이 replica 쪽에서 소비되지 않게 한다. **master 는 그래도 쓰기를 받는다** — 그게 비동기 복제다 |
| 2 | master 에 `qm:lock:pool:qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP` 을 HSET + PEXPIRE 3000 | `PoolLock.java:43` 의 `LOCK_PREFIX` + `LolPartyKeys.java:24` 의 `poolKey()` + `PoolLock.java:54` 의 `LEASE_MILLIS` 를 그대로 조립한 것이다 |
| 3 | `WAIT 1 100` | **0 이 나와야 한다.** "어떤 replica 도 이 쓰기를 못 받았다" 는 증거다 |
| 4 | `docker kill` master | |
| 5 | `docker unpause` replica → Sentinel 이 승격 | |
| 6 | 새 master 에 `EXISTS <락키>` | **0 이 나온다.** 락이 사라졌다 |
| 7 | 다른 holder 로 같은 락 획득 시도 | **1 이 나온다.** 상호 배제가 깨졌다 |

`docker pause` 를 쓰는 이유는 재현을 **결정적으로** 만들기 위해서다.
로컬 복제는 수백 마이크로초라 그냥 죽이면 재현이 운에 달린다.

### 절차 (B) — 앱의 실제 락 코드로 재현

스크립트는 Redisson `RLock` 의 내부 표현(HASH + PEXPIRE)을 손으로 흉내 낸 것이다.
**진짜 `PoolLock.call()` 로 재현하려면** `docs/app-config-snippets.md` §4 의
테스트 스니펫을 `backend/src/test/.../FailoverLockLossTest.java` 로 붙인다.
(이 폴더 밖에 파일을 만드는 것이므로 **적용 여부는 사용자가 정한다.**)

그 테스트가 하는 일:

1. `poolLock.call(poolKey, ...)` 안에서 `CountDownLatch` 로 멈춰 락을 쥐고 있는다
2. 그동안 replica 를 pause 하고 master 를 kill 한다
3. 승격이 끝난 뒤 다른 스레드가 같은 `poolKey` 로 `poolLock.call()` 을 부른다
4. **두 번째가 성공하면 상호 배제가 깨진 것이다**
5. 첫 번째 스레드가 `finally` 에서 `lock.isHeldByCurrentThread()` 를 부르는데
   (`PoolLock.java:81`) 새 master 에는 그 락이 없으므로 `false` 가 나온다.
   **unlock 이 조용히 건너뛰어진다.** 두 번째 스레드의 락은 그대로 남는다

이 구조는 `NaiveVsLuaComparisonTest` 와 같은 판이다 —
`docs/CONCURRENCY_TESTS.md` 의 원칙("통과하는 테스트만 있으면 원래 안 깨지는 것인지
우리가 막은 것인지 구분이 안 된다")대로, **깨지는 것을 먼저 증명한다.**

### 절차 (C) — 방어를 켜고 다시

**C-1. `WAIT` 를 락 획득 뒤에 넣는다.**

```bash
# 스크립트 안에서 락을 잡은 직후
docker exec qm-ha-master redis-cli WAIT 1 500
# 1 이 돌아와야 replica 한 대에 갔다는 뜻이다. 0 이면 락을 포기해야 한다.
```

**C-2. `min-replicas-to-write` 로 master 가 아예 쓰기를 거부하게 한다.**

```bash
./scripts/reset.sh
sed -i 's/^MIN_REPLICAS_TO_WRITE=.*/MIN_REPLICAS_TO_WRITE=1/' .env
sed -i 's/^MIN_REPLICAS_MAX_LAG=.*/MIN_REPLICAS_MAX_LAG=10/' .env
./scripts/up.sh
./scripts/lock-loss-repro.sh
```

이번에는 단계 2 에서 락 획득 자체가 실패해야 한다 —
replica 2대가 pause 되어 있으므로 master 가 조건을 못 채운다.
`NOREPLICAS Not enough good replicas to write` 가 나온다.

### 측정 지표

| 지표 | 정의 | 어떻게 |
|---|---|---|
| `락 유실 여부` | 단계 7 의 결과 (0/1) | `lock-loss-repro.sh` 출력 |
| `WAIT 결과` | 단계 3 의 `WAIT 1 100` | 0 이면 복제 안 됨 |
| `유실 창(window)` | 락을 잡고부터 replica 에 도달하기까지 | pause 없이 100회 반복해서 `WAIT 1 0` 이 걸리는 시간 분포 |
| `WAIT 비용` | `WAIT 1 500` 을 넣었을 때 락 획득 지연 증가분 | k6 `probe_ok` 의 p99 비교 |
| `min-replicas 비용` | 켰을 때 replica 1대가 죽으면 어떻게 되나 | 쓰기가 전부 `NOREPLICAS` 로 거절되는지 |

### 예상 결과 (미실행 — 아래는 전부 검증되지 않은 예상이다)

| 판 | 락 유실 | 쓰기 지연 | replica 1대 사망 시 |
|---|---|---|---|
| 기본 (아무것도 안 함) | **발생** | 최소 | 정상 동작 |
| `WAIT 1 500` 추가 | 크게 줄지만 **0 은 아니다** | replica RTT 만큼 증가 | `WAIT` 가 0 을 돌려줌 → 앱이 판단해야 함 |
| `min-replicas-to-write 1` | 크게 줄지만 **0 은 아니다** | 거의 없음 | **쓰기 전면 거부.** 가용성이 떨어진다 |

**"0 이 아니다" 가 핵심이다.** `WAIT` 는 "replica 가 받았다" 를 보장할 뿐
"그 replica 가 승격된다" 를 보장하지 않는다. `WAIT 1` 로 replica1 이 받았는데
Sentinel 이 replica2 를 승격시키면 그대로 유실이다.
`min-replicas-to-write` 도 같은 한계다 — 조건을 확인하는 것과 쓰기가 원자적이지 않다.

### 해석 포인트

1. **이건 설정으로 못 고치는 문제다.** Redis 복제 모델의 성질이지 버그가 아니다.
   `WAIT` 와 `min-replicas-to-write` 는 **확률을 낮출 뿐 성질을 바꾸지 않는다.**
   자세한 것은 `docs/lock-safety-analysis.md`.

2. `min-replicas-to-write` 를 켜는 것은 **INV-10 을 Redis 층으로 내리는 결정**이다.
   복제가 끊기면 master 가 쓰기를 거부하고, 앱은 `DataAccessException` 을 받아
   `GlobalExceptionHandler.java:44-51` 이 503 으로 바꾼다. INV-10 의 fail-closed
   와 방향이 같다. **가용성을 더 내주고 안전을 사는 쪽으로 일관되게 가는 것이다.**
   이 프로젝트의 기존 결정들과 맞는다.

3. 락이 유실됐을 때 **실제로 무엇이 깨지는지**가 중요하다.
   `PoolLock` 이 지키는 임계 구역은 `UntieredAssigner.assign()` 의
   **후보 순회 전체**다 (`UntieredAssigner.java:50-76`). 주석이 이유를 적어 두었다.

   > 순회 전체가 한 락 안에 있어야 한다. 회차마다 락을 놓으면 그 사이 다른 요청이
   > 색인(ZSET)을 바꾼다. 그러면 같은 인덱스가 다른 파티를 가리켜 어떤 파티는 건너뛰고
   > 어떤 파티는 두 번 보게 된다. (`UntieredAssigner.java:39-41`)

   락이 없으면 정확히 그 일이 난다. 다만 **정원 초과(INV-3)까지 깨지지는 않는다** —
   그건 `join-party.lua` 안의 원자적 `HINCRBY` + `size >= target` 검사가 따로 막는다.
   → **락 유실의 피해는 "정원 초과" 가 아니라 "후보를 건너뛰거나 두 번 보는 것"** 이다.
   증상은 매칭 실패율 상승과 needs 색인의 유령 항목이다. 조용해서 더 나쁘다.


### 지금 당장 할 수 있는 조각 — `min-replicas-to-write` 의 가용성 비용

**락 유실 재현과 무관하게 지금도 측정할 수 있는 부분이 하나 있다.**
(C) `min-replicas-to-write` 를 켰을 때 **얼마나 자주 쓰기가 거부되는가** 다.
이건 락이 아니라 가용성의 문제라서 임계 구역이 미구현이어도 성립한다.

```bash
./scripts/reset.sh
sed -i 's/^MIN_REPLICAS_TO_WRITE=.*/MIN_REPLICAS_TO_WRITE=1/' .env
sed -i 's/^MIN_REPLICAS_MAX_LAG=.*/MIN_REPLICAS_MAX_LAG=10/'  .env
./scripts/up.sh

# (1) 정상 상태에서 쓰기가 되는가
./scripts/redis-write-probe.py 30 out/e4-normal.csv

# (2) replica 를 한 대 죽이면? (남은 replica 1대 -> 조건 충족, 계속 써져야 한다)
docker kill qm-ha-replica2
./scripts/redis-write-probe.py 30 out/e4-one-replica.csv

# (3) 두 대 다 죽이면? (조건 미달 -> NOREPLICAS 로 전면 거부)
docker kill qm-ha-replica1
./scripts/redis-write-probe.py 30 out/e4-no-replica.csv
```

나올 결론의 모양은 **실험 3 과 같다** — "안전을 사는 대신 가용성을 낸다. 그 값이 얼마인가."

| 재는 것 | 뜻 |
|---|---|
| (3) 에서 실패한 tick 수 | replica 가 전부 없을 때 매칭이 통째로 멈추는 시간 |
| 실패 메시지가 `NOREPLICAS` 인가 | 맞으면 `DataAccessException` → `GlobalExceptionHandler` → 503. **INV-10 의 fail-closed 와 방향이 같다** |
| `master_repl_offset` 과 replica offset 의 차 | `min-replicas-max-lag` 를 얼마로 잡아야 하는지의 근거 |

**이 조각만으로도 결정을 하나 내릴 수 있다** — "락 안전성을 위해 이 설정을 켤 것인가"
가 아니라 "**켰을 때 우리가 감당할 수 있는 가용성 손실인가**". 후자가 먼저다.

---

## 실험 5 — 스플릿 브레인 / 네트워크 파티션

> **미실행** — 사용자가 건너뛰기로 했다.
>
> **근거 둘.**
> 1. **축소판을 이미 우연히 관측했다.** 실험 3 중에 죽인 노드를 `docker start` 로
>    되살렸더니 자기 설정 파일 때문에 **약 2초 동안 자기를 master 라고 믿었다**(실측).
>    Sentinel 이 `REPLICAOF` 로 강등시킨다. "격리된 구 master 는 자기가 죽은 줄 모른다"
>    라는 이 실험의 전제가 그 2초 안에 그대로 들어 있다.
> 2. **별도 토폴로지(T-B)를 다시 깔아야 한다.** T-A(호스트 IP announce)에서는
>    `docker network disconnect` 로 master 를 떼면 **아무도** 그 master 에 못 붙는다 —
>    노드끼리도 호스트 IP 를 거치기 때문이다(`README.md` §2). 스플릿 브레인의 전제가
>    사라진다. T-B 로 다시 깔고, 앱도 컨테이너로 올리고(`bootJar` → `--profile app`),
>    관측을 전부 `docker exec` 로 바꿔야 한다. **그 비용에 비해 새로 얻는 결론이
>    "유실된다" 하나뿐이다.**
>
> 아래 절차는 그대로 남긴다. `min-replicas-to-write` 를 실제로 켜기로 결정하면
> 그 방어가 효과를 보이는 유일한 판이 여기이므로 그때 돌린다.

### 가설

master 를 네트워크에서 격리하면 Sentinel 은 replica 를 승격시킨다.
그런데 **격리된 구 master 는 자기가 죽은 줄 모르고 계속 master 로 행세한다.**
그쪽에 붙어 있던 클라이언트의 쓰기는 받아들여지고, 구 master 가 복귀해서
강등되는 순간 **전부 사라진다.**

### 절차

**T-B 토폴로지로만 가능하다.** 이유는 `README.md` §2 에 적었다.

```bash
./scripts/reset.sh
NETPART=1 ./scripts/up.sh
docker exec -i qm-ha-master redis-cli < ../seed/gameconfig.redis
```

```bash
# (1) 격리 전 상태를 남긴다
NETPART=1 ./scripts/status.sh
docker exec qm-ha-master redis-cli INFO replication | grep master_repl_offset

# (2) master 를 네트워크에서 뗀다
NETPART=1 ./scripts/partition-master.sh cut

# (3) 격리된 master 에 계속 쓴다.
#     docker exec 는 네트워크가 아니라 프로세스에 직접 붙으므로 아직 된다.
for i in $(seq 1 50); do
  docker exec qm-ha-master redis-cli SET "qm:split:orphan:$i" "written-to-old-master"
done
docker exec qm-ha-master redis-cli DBSIZE
docker exec qm-ha-master redis-cli INFO replication | grep -E '^role:|connected_slaves'

# (4) 그 사이 남은 쪽에서는 페일오버가 끝났다
NETPART=1 ./scripts/status.sh
NETPART=1 ./scripts/failover-timeline.sh

# (5) 새 master 에도 쓴다. 이제 두 개의 master 가 각자 다른 데이터를 갖는다
docker exec qm-ha-replica1 redis-cli SET "qm:split:newside:1" "written-to-new-master"

# (6) 구 master 를 복귀시킨다
NETPART=1 ./scripts/partition-master.sh heal

# (7) 유실 확인
docker exec qm-ha-master   redis-cli --scan --pattern 'qm:split:orphan:*' | wc -l
docker exec qm-ha-replica1 redis-cli --scan --pattern 'qm:split:orphan:*' | wc -l
```

앱을 같이 띄우고 싶으면 (`matching/backend` 에서 `./gradlew bootJar` 먼저):

```bash
docker compose -f docker-compose.yml -f docker-compose.netpart.yml --profile app up -d
```

### 측정 지표

| 지표 | 정의 | 어떻게 |
|---|---|---|
| `이중 master 기간` | `cut` 부터 `heal 후 강등` 까지 | `partition-master.sh` 가 role 을 0.5초 간격으로 찍는다 |
| `구 master 가 받은 쓰기` | 격리 중 성공한 SET 개수 | (3) 의 결과 |
| `유실된 쓰기` | 복귀 후 사라진 키 개수 | (7) 의 두 값 차이 |
| `강등 지연` | `heal` 부터 `role:slave` 로 바뀌기까지 | `partition-master.sh heal` 출력 |
| `재동기화 방식` | full sync 인가 partial 인가 | `docker logs qm-ha-master \| grep -i sync` |

### 예상 결과 (미실행 — 아래는 전부 검증되지 않은 예상이다)

- (3) 의 SET 은 **전부 성공한다.** 격리된 master 는 자기가 소수파인지 모른다.
  `connected_slaves:0` 이 되고 `role:master` 는 그대로다.
- (4) 에서 Sentinel 3대는 서로 보이므로 정족수가 살아 있다. 정상 페일오버가 난다.
- (7) 에서 구 master 의 `qm:split:orphan:*` 는 **0 개가 된다.**
  강등되면서 새 master 로부터 full sync 를 받아 자기 데이터셋을 통째로 버린다.
  새 master 쪽에도 0 개다. **50건이 흔적 없이 사라진다.**
- `min-replicas-to-write 1` 을 켜 두면 (3) 이 실패한다. 이게 이 실험과
  실험 4 를 잇는 지점이다.

### 해석 포인트

1. **이게 `WAIT` 로도 못 막는 경우다.** 실험 4 의 방어는 "복제가 안 따라왔을 때"
   를 막는다. 여기서는 격리된 master 가 **replica 를 아예 못 보므로**
   `WAIT` 를 넣었다면 그 자리에서 실패한다 — 즉 `WAIT` 는 이 경우 잘 막는다.
   `min-replicas-to-write` 도 같다.
   → **실험 4 와 5 를 같이 돌려 보면 `min-replicas-to-write` 의 값어치가 드러난다.**
   실험 4 에서는 확률만 낮추지만, 실험 5 에서는 실제로 막는다.

2. 이 프로젝트에서 유실되는 것이 무엇인지 구체적으로 보자.

   | 키 | 유실되면 | 근거 |
   |---|---|---|
   | `qm:user:active-request:{userId}` | 사용자가 대기 중인 줄 알았는데 서버는 모른다. 다시 요청하면 통과한다(409 가 아니라 201) | `claim-request.lua:15-19` |
   | `qm:party:{partyId}` | 파티가 통째로 사라진다. 다른 멤버의 active-request 에는 `partyId` 가 남아 유령 참조가 된다 | `create-or-check-party-untiered.lua:81-89` |
   | `qm:party:open:...:needs:{keyValue}` | 색인만 사라지면 파티는 있는데 아무도 못 찾는다. 정원이 영원히 안 찬다 | `create-or-check-party-untiered.lua:95-102` |
   | `qm:lock:pool:*` | 실험 4 |  |

   **파티 HASH 와 needs 색인이 따로 유실되면 정합성이 깨진다.**
   같은 Lua 안에서 원자적으로 썼어도, 복제 스트림이 중간에 끊기면
   `HSET` 은 갔는데 `ZADD` 는 안 간 상태가 될 수 있다.
   → **원자성은 복제를 건너뛰지 않는다.** 이게 Lua 만 믿으면 안 되는 이유다.

3. 클라이언트 쪽 관측도 같이 해라. Lettuce 는 Sentinel pub/sub 로
   `+switch-master` 를 받아 새 master 로 옮겨 간다. 그런데 **격리된 쪽에 있던
   클라이언트는 그 pub/sub 도 못 받는다.** T-B 에서 앱을 같이 격리시키면
   (`docker network disconnect qm-ha qm-ha-app`) 앱이 구 master 에 계속
   쓰는 상황이 재현된다. 이게 실무에서 스플릿 브레인이 무서운 이유다.

---

## 실험 6 — 재시도·서킷 검증

> **실행 완료 (2026-09-09)** — 다만 **계획과 다른 축을 흔들었다.**
> 원래 계획은 복구 계기만 바꾼 세 판(T / T-fast / E)의 비교였다. 그 비교는 **미실행**이다.
> 대신 **기능 자체가 무엇을 하는지**를 먼저 검증했다 (OFF ↔ ON, 그리고 판 C ↔ 판 B).
> 왜 축을 바꿨는지는 아래 "판 C 에서는 결정적이지 못했다" 에 적었다.

> **전제**: 이 실험은 `queuemate.failover.retry.enabled=true` 일 때만 성립한다.
> 기능·프로퍼티·삭제 절차는 `docs/failover-retry-guide.md` 를 보라.
> **이 기능은 실험이 끝나면 걷어낼 임시 기능이다.**

### 원래 가설

**배정 실패를 되살릴 때, OPEN → HALF_OPEN 전환을 "시간으로 추측" 하는 것보다
"인프라 이벤트(`+switch-master`)로 아는" 편이 두 방향 모두에서 낫다.**

| 추측이 | 무슨 일이 나나 | 어느 지표에 나타나나 |
|---|---|---|
| **너무 이르면** | 아직 안 끝난 페일오버 중에 죽은 master 를 계속 때린다 | `deadMasterAttempts` 가 는다 |
| **너무 늦으면** | 새 master 가 이미 떴는데 큐가 안 빈다. 그동안 60초 TTL 이 흐른다 | `withinTtl / drained` 비율이 떨어진다 |

부가 가설: **실험 2 의 클라이언트 몫이 크면 이벤트의 이점도 그만큼 줄어든다.**
이 실험은 실험 2 판 C 위에서 돌려야 의미가 있다.

### 실제로 돌린 절차

**단일 스레드 프로브로는 이 기능을 검증할 수 없다는 것을 먼저 발견했다.**
장애 구간에 요청 하나가 2초씩 매달리면 60초에 몇 건 못 보낸다. 그러면
"claim 은 성공했는데 배정만 실패한" 요청 자체가 거의 안 생겨 재시도 큐가 할 일이 없다.
그래서 동시 프로브를 새로 만들었다 (`scripts/http-probe-concurrent.py`).

세 판 모두 **동시 10스레드, 50초, `t0 = +10초` 에 kill** 이다.

```bash
# 앱을 띄우는 줄은 전부 backend/ 에서 돌린다 (cd <repo>/backend)

# (1) 기준선 : 판 C + 플래그 OFF
./gradlew bootRun --args='--spring.profiles.active=sentinel'

# (2) 판 C + 플래그 ON
FAILOVER_RETRY_ENABLED=true FAILOVER_LETTUCE_ENABLED=true \
  ./gradlew bootRun --args='--spring.profiles.active=sentinel'

# (3) 판 B + 플래그 ON  <- 결정적 검증
QUEUEMATE_LOCK_SENTINEL_NODES="" \
FAILOVER_RETRY_ENABLED=true FAILOVER_LETTUCE_ENABLED=true \
  ./gradlew bootRun --args='--spring.profiles.active=sentinel'

# 각 판마다 (아래 세 줄은 redis-ha-lab/ 에서)
./scripts/http-probe-concurrent.py 50 out/e6-X.csv X 10   # 터미널 1
./scripts/kill-master.sh                                   # 터미널 2, +10초에
./scripts/verify-assign.py out/e6-X.csv .last-kill-ms
grep '\[failover\]' logs/X.log
```

### 실측 결과 (1) — 판 C: OFF ↔ ON

| | **OFF (기준선)** | **ON** |
|---|---|---|
| 총 요청 | 2,070건 | 2,320건 |
| HTTP 201 | 2,040건 (98.6%) | 1,960건 (84.5%) |
| HTTP 503 | 30건 (1.4%) | 360건 (15.5%) |
| 배정 성공 | 2,034건 | 1,960건 |
| 유실 | 36건 | 360건 (**전부 503**) |
| **HTTP 성공인데 배정 실패** | **6건** (t0−2ms ~ t0+3ms) | **0건** |
| `비동기 작업 실패` 로그 | 7건 | **0건** |
| 서킷 | (기능 없음) | **한 번도 안 열림. 큐 0건** |

**조용한 유실 6건이 0건이 됐다.** 그런데 **그건 재시도의 공이 아니다.**
서킷은 한 번도 열리지 않았고 큐에 들어간 것도 0건이다. 즉 **배정 단계 실패 자체가
없었다.** 유실 360건은 전부 503 이다 — 사용자가 실패를 **알고** 받은 것이다.

503 이 30 → 360 으로 는 것은 같은 장애 구간에서 요청이 **더 빨리 실패했다**는 뜻이다
(총 처리량도 2,070 → 2,320 으로 늘었다. 매달리는 요청이 줄어서다).
`DisconnectedBehavior.REJECT_COMMANDS` 의 효과로 보이지만 **미확인**이다 —
두 플래그(`retry` / `lettuce`)를 함께 켰기 때문에 어느 쪽 효과인지 분리하지 않았다.

**확인된 것 하나가 더 있다.** 로그에 이 두 줄이 나왔다.

```
[failover] +switch-master 수신 ...
[failover] 복구 신호 수신 source=pubsub
```

`docs/failover-retry-guide.md` §8-7 이 "**Sentinel 포트에 Spring Data 의 standalone
커넥션으로 붙는 것을 실기로 검증하지 못했다**" 로 남겨 두었던 항목이다.
**주 경로가 실제로 작동한다.** 백업 경로(폴링)로 떨어지지 않았다.

### 실측 결과 (2) — 판 B + 플래그 ON (결정적 검증)

판 C 에서는 재시도가 할 일이 없었으므로, **재시도가 노리는 상황을 실제로 만들었다.**
판 B(Lettuce 만 Sentinel)가 정확히 그 상황이다 — claim 은 성공하고 배정만 실패한다.

| | 값 |
|---|---|
| 총 요청 | 2,050건 |
| HTTP 201 | 1,681건 (82.0%) |
| HTTP 503 | 360건 (17.6%) |
| 연결 실패 | 9건 |
| 배정 성공 | 400건 (19.5%) |
| 유실 | 1,650건 (80.5%) |
| **HTTP 성공인데 배정 실패** | **1,281건 (62.5%)** — 첫 건 t0+9,296ms, 끝 건 t0+39,848ms |
| **큐 등록** | **1,290건** |
| **`deadMasterAttempts`** | **28회** |
| `비동기 작업 실패` 익명 ERROR 로그 | **0건** |
| **재배정 성공** | **0건** |
| 재시도 상한 초과로 버림 | 1건 (`attempts=6 age=76,137ms`) |

서킷 전이 로그는 이렇게 반복됐다.

```
[failover] 서킷 CLOSED -> OPEN (연속 실패 3회: RedisException) deadMasterAttempts=3
[failover] 서킷 OPEN -> HALF_OPEN (복구 신호(open-timeout))
[failover] 서킷 HALF_OPEN -> OPEN (프로브 실패: RedisException)
   ... 반복 ...
```

**이 표에서 읽어야 할 것은 두 줄이다.**

1. **`1,290건 → 28회`.** 큐에 들어온 1,290건 중 실제로 죽은 Redis 를 때린 것은 28회뿐이다.
   서킷이 나머지를 전부 막았다. 이것이 서킷의 효과다.
2. **재배정 성공 0건.** 판 B 에서는 Redisson 이 끝까지 죽은 6379 만 보므로,
   몇 번을 재시도해도 배정은 성공할 수 없다.
   **재시도·서킷은 장애를 복구하지만 설정 실수는 고치지 못한다.**

그리고 **익명 ERROR 로그 0건**. 플래그 OFF 였던 실험 2 판 B 에서는 `비동기 작업 실패`
가 101건 찍혔다(그쪽은 단일 스레드 프로브라 건수를 1:1 로 비교할 수는 없다).
켜면 그 실패들이 **`[failover]` 접두사가 붙은, 지표가 딸린 로그**로 바뀐다.
`deadMasterAttempts` / `dropped` / `attempts` / `age` 가 붙는다.

### 판 C 에서는 결정적이지 못했다 — 그래서 판 B 로 갔다

원래 계획(T / T-fast / E)은 **재시도 큐에 요청이 쌓인다는 전제** 위에 있다.
큐가 비면 `drainMs` 도 `withinTtl / drained` 도 정의되지 않는다.
판 C + ON 에서 큐는 **0건**이었다. 세 판을 비교할 재료가 없다.

**왜 큐가 비었나.** 정상 설정에서는 Redis 가 죽으면 **claim(1단계)이 먼저 fail-closed 로
막아 503 을 낸다.** 배정(2단계)까지 도달하는 요청이 애초에 없다.
재시도 큐가 노리는 "claim 성공 + 배정 실패" 는 **두 클라이언트가 서로 다른 것을 볼 때**
생기고, 그건 판 B 같은 **반쪽 설정**이다.

→ 그래서 이 기능의 실제 가치는 "페일오버를 더 빨리 복구한다" 가 아니라
**"조용한 실패를 진단 가능한 실패로 바꾼다"** 쪽에 있다.
`docs/failover-retry-guide.md` §9 에 같은 내용을 적어 두었다.

### 이 실험이 찾아낸 설계 결함 — `SentinelRecoveryWatcher` 의 잘못된 결합

판 B 를 만들려고 `QUEUEMATE_LOCK_SENTINEL_NODES=""` 로 비웠더니 이 로그가 나왔다.

```
[failover] Sentinel 노드가 없어 +switch-master 구독을 건너뛴다.
           복구는 open-timeout-ms 시간 폴백만으로 이뤄진다
```

감시자가 Sentinel 주소를 **Redisson 과 같은 프로퍼티**로 읽기 때문이다
(`FailoverRetryConfig.java:54`).

```java
@Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}") String sentinelNodes
```

**잘못된 결합이다.** 복구 신호를 받는 것과 **락 클라이언트가 Sentinel 을 쓰는지**는
아무 상관이 없다. Lettuce 가 Sentinel 을 쓰고 있으면 `+switch-master` 는 실재하고,
감시자는 그것을 들을 수 있어야 한다.

결과적으로 **판 B 의 재시도는 pub/sub 없이 `open-timeout` 폴백만으로 돌았다.**
위 서킷 전이 로그에 `복구 신호(open-timeout)` 만 찍힌 이유가 이것이다.

고치는 방법과 "다시 붙일 때 확인할 것" 은 `docs/failover-retry-guide.md` §8·§6 에 적었다.
**코드는 고치지 않았다** — 이 폴더의 규칙이고, 판단은 사용자 몫이다.

> **이 실험이 아니었으면 못 찾았을 문제다.** 판 B 는 "일부러 반쪽으로 만든 판" 이고,
> 그 판을 만드는 과정에서 **전혀 다른 기능의 설정까지 같이 꺼진다**는 것이 드러났다.

### 안 돌린 것 (미실행)

| | 왜 |
|---|---|
| **판 T / T-fast / E 비교** (`sentinel-poll-interval-ms` × `open-timeout-ms`) | 판 C 에서 큐가 0건이라 비교 재료가 없다. 판 B 에서는 큐가 차지만 **재배정이 원리적으로 성공할 수 없어** `withinTtl / drained` 가 전부 0 이다. 두 판 다 이 축을 재기에 맞지 않는다 |
| `drainMs` / `withinTtl` / `drained` 실측 | 위와 같은 이유. **미측정** |

> **위 두 줄은 실험 당시의 계획 기록이다. 그 뒤 코드에서 사라진 것이 있다.**
> `sentinel-poll-interval-ms`(Sentinel 폴링 백업 경로)는 **제거됐다** — 4회 실행 전부에서
> 신호를 한 번도 못 냈기 때문이다. `drainMs` / `withinTtl` / `drained` / `dropped` 지표를
> 내던 `[failover] 배출 완료` 로그와 `request-ttl-seconds` 프로퍼티도 같이 걷어냈다.
> 지금 남은 복구 계기 축은 `open-timeout-ms` 하나(+ `+switch-master` 이벤트)이고,
> 남은 지표는 서킷 전이 로그의 `deadMasterAttempts` 와 버림 로그의 `누적버림` 이다.
> 근거와 다시 붙일 때의 조건은 `docs/failover-retry-guide.md` §6.

| `Δ_signal` (`t_signal − t_switch`) | 판 C 에서 `source=pubsub` 이 온 것은 확인했지만 `t_switch` 와의 차를 ms 로 재지는 않았다. **미측정** |

이 축을 제대로 재려면 **claim 은 성공하고 배정만 실패하는 판**이 필요한데, 그것을
정상 설정에서 만들려면 배정 경로에만 장애를 주입해야 한다. 지금 구조로는 두 클라이언트가
같은 Redis 를 보므로 그런 판을 만들 수 없다. **INV-4/5(proposal)가 붙어 배정이 여러
단계가 되면 그때 다시 잰다.**

### 해석 포인트

1. **"재시도가 좋은가" 가 아니라 "무엇을 해결하는가" 가 먼저였다.**
   판 C 에서 이 기능은 아무 일도 하지 않았다. 아무 일도 하지 않는 것이 정상이었다 —
   claim 이 먼저 막기 때문이다. 이 사실을 모른 채 판 T/T-fast/E 를 돌렸으면
   **"세 판이 다 똑같이 나왔다" 는 무의미한 표**를 얻었을 것이다.

2. **서킷의 값은 `1,290 → 28` 이라는 한 쌍의 숫자다.** 죽은 서버를 때리는 것을
   1/46 로 줄였다. 이것은 재시도가 성공하든 말든 성립하는 효과다 —
   장애 중에 애꿎은 부하를 안 만드는 것.

3. **`deadMasterAttempts` 와 `withinTtl` 을 같이 봐야 한다**는 원래 계획의 논지는
   유효하지만, **이 프로젝트에서는 아직 잴 판이 없다.** 억지로 재지 않았다.

4. **프로브로 PING 을 쓰지 않은 것이 옳았다는 것도 간접적으로 확인됐다.**
   판 B 에서 Redisson 이 보는 6379 는 죽어 있었고 프로브는 실제 배정을 시도해
   `RedisException` 으로 정확히 실패했다. PING 프로브였다면 서킷이 닫혔다 열렸다를
   반복하는 대신 **닫힌 채로 전부 실패**했을 것이다.

---

## 실험을 마친 뒤 — 무엇이 정해졌나

| 결정할 것 | 근거가 되는 실험 | 지금 상태 |
|---|---|---|
| `down-after` / `quorum` / `failover-timeout` 값 | 실험 3 | **정해졌다. `down-after=5000` 유지.** 3.42초로 줄일 수 있지만 claim TTL 60초 예산에서 실익이 없고 3초 정지에 오탐을 산다 |
| 클라이언트(Lettuce / Redisson) 설정 | 실험 2 | **정해졌다. 판 C(둘 다 Sentinel) 말고는 답이 없다.** 판 B 는 모든 겉보기 지표가 최고로 나오면서 매칭이 하나도 안 되는 판이다 |
| 락을 Redis 에 둘 것인가 | 실험 4·5 + `docs/lock-safety-analysis.md` | **보류.** 실험 4·5 를 건너뛰었다. 임계 구역(INV-4/5/6)이 완성돼야 피해를 잴 수 있다 |
| 배정 실패의 복구 계기를 타이머로 둘 것인가 `+switch-master` 로 둘 것인가 | 실험 6 + `docs/failover-retry-guide.md` | **보류.** 판을 만들 수 없어 축을 못 흔들었다. 다만 `+switch-master` 구독이 **실제로 작동한다**는 것은 확인했다 |
| `SentinelRecoveryWatcher` 의 프로퍼티 결합을 끊을 것인가 | 실험 6 | **사용자 판단 대기.** 코드는 고치지 않았다. `docs/failover-retry-guide.md` §6 (A) / §8-8 |
| `FailoverRetryCoordinator` 의 `assigner` 콜백을 `PartyAssigner` 빈으로 뺄 것인가 | 실험 6 뒤 코드 정리 | **사용자 판단 대기.** 프로덕션에 새 클래스가 생겨 "failover 폴더만 지우면 원복" 이 깨진다. `docs/failover-retry-guide.md` §6 (B) |
| Sentinel 폴링(복구 신호 백업 경로)을 남길 것인가 | 실험 6 | **제거했다.** 4회 실행 전부 신호 0건(`readMasterAddress()` 가 항상 null). `docs/failover-retry-guide.md` §6 (C) |
| `min-replicas-to-write` 를 켤 것인가 | 실험 4 의 "지금 할 수 있는 조각" | **미측정.** 가용성 비용부터 재야 한다 |

`docs/11_DECISION_LOG.md` 의 형식(결정을 지우지 않고 취소선 + "개정됨 → #번호")
을 따라 위 결정들을 옮기면, 이 실험이 포트폴리오의 근거로 남는다.
숫자만 옮길 표는 `RESULTS.md` 에 있다.
