# redis-ha-lab — Sentinel 기반 HA 실험 환경

`app:matching` 은 진행 중인 매칭 상태를 Redis 하나에만 둔다 (CLAUDE.md §3).
그 Redis 가 죽으면 INV-10 에 따라 새 매칭을 fail-closed 로 거절한다
(`GlobalExceptionHandler.java:44-51`). 지금 구성은 단일 노드이므로
**Redis 한 대가 죽으면 매칭 전체가 멈춘다.**

`docs/11_DECISION_LOG.md` #29 의 재검토 트리거가 이렇게 적혀 있다.

> 재검토 트리거: 매칭 대기 시간이 길어져 유실 비용이 커질 때. 그때는 요청 DB 복제가
> 아니라 **Redis 자체의 이중화(replica + failover)** 를 먼저 본다.

이 폴더는 그 "먼저 볼 것" 을 노트북에서 실제로 돌려 보는 판이다.
**기존 저장소의 소스는 한 줄도 건드리지 않는다.** 앱 설정을 바꿔야 하는 부분은
`docs/app-config-snippets.md` 에 스니펫으로만 적어 두었다.

- **실험 결과 요약(먼저 볼 것)**: `RESULTS.md`
- 실험 시나리오 6개와 절차·해석: `EXPERIMENTS.md`
- 지금 락 구현이 어디서 깨지는지: `docs/lock-safety-analysis.md`
- 앱을 Sentinel 에 붙이는 법: `docs/app-config-snippets.md`
- 페일오버 재시도(임시 기능)를 켜고 끄고 지우는 법: `docs/failover-retry-guide.md`

**실험 1·2·3·6 은 2026-09-09 에 실제로 돌렸다.** 실험 4·5 는 건너뛰었다
(근거는 `EXPERIMENTS.md` 의 해당 절 맨 앞).

---

## 1. 구동 절차

### 1-1. 사전 준비 — Docker 가 WSL 에서 안 잡힐 때

`docker: command not found` 가 나면 대개 **Docker Desktop 이 꺼져 있는 것**이다.
WSL 통합은 Docker Desktop 이 떠 있을 때만 켜진다. WSL 셸에서 바로 띄울 수 있다.

```bash
"/mnt/c/Program Files/Docker/Docker/Docker Desktop.exe" &
# 30초쯤 기다리면 WSL 통합이 자동으로 붙는다
docker version    # Server 까지 나오면 된 것이다
```

Docker Desktop 설정에서 이 배포판의 WSL 통합이 켜져 있어야 한다
(Settings → Resources → WSL Integration).

전제: Docker, python3, bash. `redis-cli` 는 **호스트에 없어도 된다** —
스크립트는 전부 컨테이너 안의 `redis-cli` 를 `docker exec` 로 부른다.

### 1-2. Redis + Sentinel 띄우기

```bash
cd redis-ha-lab

# (1) 환경 변수. HOST_IP 는 WSL2 eth0 주소다. 재부팅하면 바뀐다.
cp .env.example .env
./scripts/host-ip.sh >> .env
tail -1 .env                      # HOST_IP=172.22.x.x 가 마지막 줄이어야 한다

# (2) 띄운다. 정족수가 찰 때까지 기다렸다가 상태를 찍어 준다.
./scripts/up.sh

# (3) 앱 시드. 기존 절차와 같다 (START_HERE.md).
docker exec -i qm-ha-master redis-cli < ../seed/gameconfig.redis
docker exec qm-ha-master redis-cli ZCARD qm:gameconfig:LOL:tier      # 32 가 나와야 한다
# (2026-09-15 이전에는 모드 목록 SET 을 SCARD 로 봤으나 그 키는 없앴다)

# (4) 첫 페일오버. master 를 죽이고 승격을 지켜본다.
./scripts/kill-master.sh
sleep 20
./scripts/failover-timeline.sh
./scripts/status.sh

# (5) 판을 새로 깐다
./scripts/reset.sh
```

`up.sh` 가 `.env` 의 `HOST_IP` 와 지금 eth0 주소가 다르면 거부한다.
그냥 띄우면 Sentinel 이 죽은 주소를 감시하다 5초 뒤에 페일오버를 시작해서,
아무것도 안 했는데 토폴로지가 뒤집힌 상태로 실험을 시작하게 된다.

> **`reset.sh` 를 한 뒤에는 시드를 다시 넣어야 한다** (`down -v` 로 볼륨까지 지운다).
> 그리고 **시드는 "지금 master" 에 넣어야 한다** — 페일오버가 한 번 났으면
> `qm-ha-master` 는 이미 master 가 아니다. 어느 노드가 master 인지는
> `./scripts/status.sh` 의 "각 노드의 role" 로 확인한다.

### 1-3. 앱을 Sentinel 에 붙여서 띄우기 (실험 2·6)

**`application.yaml` 에 `spring.data.redis.sentinel` 블록을 두면 안 된다.**
Spring Boot 4.1.1 의 `DataRedisConnectionConfiguration#determineMode()` 는
`getSentinelConfig() != null` 만 보고 Sentinel 모드를 확정한다 — **`nodes` 가 비었는지는
보지 않는다.** 그래서 값을 비워 둬도 Sentinel 없이는 앱이 아예 뜨지 않는다.

```
Cannot build a RedisURI. One of the following must be provided Host, Socket or Sentinel
```

그래서 Sentinel 설정을 **`backend/src/main/resources/application-sentinel.yaml` 프로파일로
분리**했다. 분리한 뒤 프로파일 없이 띄우면 오류가
`Connection refused: localhost/127.0.0.1:6379` 로 바뀐다 — **단일 노드로 정상 동작한다**는
뜻이다. (그 파일은 이 폴더 밖이라 여기서 관리하지 않는다. 내용은
`docs/app-config-snippets.md` §1 과 같은 판이다.)

```bash
cd ../backend     # 스프링 프로젝트는 저장소 루트의 backend/ 에 있다
./gradlew bootRun --args='--spring.profiles.active=sentinel'
```

**앱을 띄우는 데 실제로 필요했던 환경 변수 전부.**

```bash
export DB_URL='jdbc:h2:mem:matching;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS social'
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
export REDIS_SENTINEL_NODES=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381
```

| 왜 필요한가 | |
|---|---|
| `INIT=CREATE SCHEMA IF NOT EXISTS social` | 없으면 `blocks` 테이블 DDL 이 `Schema "social" not found` 로 실패한다. 이 앱이 DB 를 치는 유일한 곳이 INV-6 의 `social.blocks` 다 (`CLAUDE.md` §3) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop` | Flyway 가 없어 스키마를 만들 사람이 없다 |
| `REDIS_SENTINEL_NODES` | 프로파일의 기본값이 `localhost:26379,...` 라 로컬 실험에서는 생략해도 된다. 값을 **빈 문자열로 주면 판 B** 가 된다 (§7) |

**프로브가 쓰는 모드는 `NORMAL_5` 다.** 처음에 `RANKED_FLEX_5` 로 쐈다가 전부 튕겼다 —
그 모드는 `tierRule=WINDOW` 라 티어가 필수다. `NORMAL_5` 는 `tierRule=NONE` 이다.
(2026-09-14 이후 `tierRule` 값이 `NONE`/`EXIST` 로 바뀌어 이 서술은 당시 기준이다.
`RANKED_FLEX_5` 가 티어를 요구한다는 결론과 `NORMAL_5` 를 쓴다는 선택은 그대로다.)

> `bootRun` 으로 띄웠으면 **반드시 종료해라.** 포트 8080 이 물리면 다음 판이 실패한다
> (`CLAUDE.md` §7).

### 1-4. 처음 돌릴 때 걸렸던 것들 (전부 해결됨)

| 증상 | 원인 | 지금은 |
|---|---|---|
| `docker` 가 인자를 엉뚱하게 받는다 | `scripts/lib.sh` 가 `COMPOSE_FILES` 를 **따옴표 없이 펼쳐서**, 경로에 공백이 있으면(`OneDrive/바탕 화면/...`) 단어가 쪼개졌다 | **수정 완료.** `COMPOSE_ARGS` **배열**로 바꿨다 |
| `ERR DEBUG command not allowed` | Redis 7 부터 `enable-debug-command` 기본값이 `no` 다. `CONFIG SET` 으로도 못 바꾼다 | `scripts/debug-sleep.sh` 대신 **`scripts/false-positive-test.sh`(`docker pause`)** 를 쓴다 |
| 앱이 `Cannot build a RedisURI` 로 안 뜬다 | `application.yaml` 의 sentinel 블록 | `application-sentinel.yaml` 프로파일로 분리 (§1-3) |
| `Schema "social" not found` | H2 에 `social` 스키마가 없다 | `DB_URL` 에 `INIT=CREATE SCHEMA ...` (§1-3) |
| 프로브가 전부 400 | `RANKED_FLEX_5` 는 티어 필수 | `NORMAL_5` 로 쏜다 (§1-3) |
| zsh 에서 `for s in $SENTINELS` 가 안 돈다 | **zsh 는 변수 확장 때 단어 분리를 안 한다** (bash 와 다르다) | lab 스크립트는 전부 `#!/usr/bin/env bash` 라 무관하다. 다만 **셸에서 `lib.sh` 를 `source` 해서 쓸 때는 주의해야 한다** |

---

## 2. 토폴로지를 왜 이렇게 잡았나

### 문제

Sentinel 은 **자기가 아는 주소를 클라이언트에게 그대로 알려 준다.**
`SENTINEL get-master-addr-by-name mymaster` 가 돌려주는 값이 곧 클라이언트가
접속할 주소다. 그래서 announce 주소를 잘못 잡으면 이런 증상이 난다.

| announce 값 | 컨테이너 안에서 | 호스트(IDE 실행 앱)에서 | 증상 |
|---|---|---|---|
| 컨테이너 IP (172.31.31.11) | 붙는다 | **못 붙는다** | Sentinel 은 정상, 앱만 연결 실패 |
| 컨테이너 이름 (qm-ha-master) | 붙는다 | **못 붙는다** | 호스트가 DNS 를 해석 못 한다 |
| 127.0.0.1 | **못 붙는다** (자기 자신) | 붙는다 | replica 가 자기를 master 로 알고 복제가 안 붙는다 |

세 번째가 특히 고약하다. 처음에는 잘 돌다가 **페일오버 순간에만** 깨진다 —
Sentinel 이 살아남은 replica 에게 `REPLICAOF 127.0.0.1 6379` 를 보내는데,
그 replica 컨테이너 안에서 127.0.0.1 은 자기 자신이다.

### 선택: T-A (호스트 IP announce) 를 기본으로 한다

**컨테이너 안과 호스트 양쪽에서 같은 값으로 도달 가능한 주소는 WSL2 의 eth0 IP 뿐이다.**
그래서 기본 토폴로지는 이렇게 잡았다.

| 노드 | 컨테이너 안 포트 | 호스트 게시 포트 | announce |
|---|---|---|---|
| qm-ha-master | 6379 | `${HOST_IP}:6379` | `${HOST_IP}:6379` |
| qm-ha-replica1 | 6379 | `${HOST_IP}:6380` | `${HOST_IP}:6380` |
| qm-ha-replica2 | 6379 | `${HOST_IP}:6381` | `${HOST_IP}:6381` |
| qm-ha-sentinel1 | 26379 | `${HOST_IP}:26379` | `${HOST_IP}:26379` |
| qm-ha-sentinel2 | 26380 | `${HOST_IP}:26380` | `${HOST_IP}:26380` |
| qm-ha-sentinel3 | 26381 | `${HOST_IP}:26381` | `${HOST_IP}:26381` |

컨테이너 → `${HOST_IP}:포트` 가 되는 것은 이미 기존 부하 테스트가 증명하고 있다 —
`load-test/run.sh:5` 의 `BASE_URL=http://172.22.149.244:8080` 이 정확히 그 경로다
(k6 컨테이너에서 WSL 호스트의 앱으로).

**근거**: 사용자는 앱을 IDE(IntelliJ)로 띄운다. `.idea/` 가 있고 부하 테스트도
호스트에서 도는 앱을 때린다. 매번 `bootJar` 를 만들어 컨테이너로 올리게 하면
실험 한 바퀴 도는 비용이 커져서 실험을 안 하게 된다. **실험 환경은 붙이는 비용이
싸야 한다.**

### 예외: T-B (컨테이너 IP announce) — 네트워크 파티션 실험 전용

> **실험 5 는 미실행이다.** 이 절은 그 실험을 돌릴 때 필요한 설명으로 남겨 둔다
> (건너뛴 근거는 `EXPERIMENTS.md` 실험 5 맨 앞).

실험 5(스플릿 브레인)만 T-A 로 성립하지 않는다. `docker network disconnect` 로
master 를 떼어내면 T-A 에서는 **아무도** 그 master 에 못 붙는다 — 노드끼리도
호스트 IP 를 거쳐 붙기 때문이다. 스플릿 브레인은 "구 master 는 살아 있고 일부
클라이언트가 거기에 계속 쓴다" 가 전제인데, 그 전제가 사라진다.

그래서 실험 5 는 오버레이를 겹쳐서 T-B 로 돌린다.

```bash
NETPART=1 ./scripts/up.sh
# = docker compose -f docker-compose.yml -f docker-compose.netpart.yml up -d
```

T-B 에서는 모든 노드가 고정 컨테이너 IP(172.31.31.11~13, .21~23)를 announce 하고,
앱도 `--profile app` 으로 같은 네트워크에 띄운다. 대신 호스트에서 IDE 로 띄운 앱은
Sentinel 이 알려 준 주소에 못 붙으므로, 관측은 전부 `docker exec` 로 한다.

### Sentinel 이 설정 파일을 고쳐 쓰는 문제

Sentinel 의 설정 파일은 **설정이 아니라 상태 저장소다.** 기동하는 순간부터
`myid`, `known-sentinel`, `known-replica`, `current-epoch` 을 계속 써 넣고,
페일오버가 나면 `sentinel monitor` 줄의 주소 자체를 새 master 로 바꾼다.
redis 노드도 마찬가지로 승격/강등 때 `CONFIG REWRITE` 로 `replicaof` 줄을 고친다.

바인드 마운트한 원본을 그대로 쓰면 두 가지가 깨진다.

1. 호스트의 `redis-ha-lab/sentinel/sentinel.conf` 가 실험할 때마다 고쳐진다.
   git 작업 트리가 더러워지고, 다음 사람이 받는 파일이 이미 오염돼 있다.
2. 컨테이너 3대가 **같은 파일 하나**를 쓴다. 서로의 `myid` 를 덮어쓴다.

그래서 이렇게 처리했다.

| 층 | 무엇 | 어디 |
|---|---|---|
| 템플릿 | `__PLACEHOLDER__` 가 든 원본. 읽기 전용 마운트 | `sentinel/sentinel.conf`, `redis/*.conf` → `/templates` |
| 생성 | 기동 시 `sed` 로 치환해서 복사 | `sentinel/entrypoint.sh`, `redis/entrypoint.sh` |
| 상태 | 컨테이너별 named volume | `/data/sentinel.conf`, `/data/redis.conf` |

entrypoint 는 **파일이 없을 때만** 만든다. 그래서:

- `docker restart qm-ha-sentinel1` → 기존 상태 유지 (페일오버 결과를 안 잊는다)
- `docker compose up -d --force-recreate` → 볼륨이 남아 있으므로 역시 유지
- `./scripts/reset.sh` (`down -v`) → 볼륨째 삭제, 완전히 새 판

**실험 조건(quorum, down-after)을 바꿀 때는 반드시 `reset.sh` 를 거쳐라.**
안 그러면 볼륨의 옛 값이 그대로 살아서 `.env` 만 바꾸고 "왜 안 바뀌지" 를 한다.

### 이미지 버전

`.env` 의 `REDIS_IMAGE` 기본값은 `redis:7-alpine` 이다. 기존 프로젝트가 쓰는 것과
같은 태그로 맞췄다 (`START_HERE.md:100` — "`redis:7-alpine`, 컨테이너명 `qm-redis`, 포트 6379").
실험 결과를 남길 거면 `redis:7.2-alpine` 처럼 고정 태그로 바꾸는 편이 낫다 —
`7-alpine` 은 움직이는 태그라 몇 달 뒤 같은 스크립트가 다른 버전을 받는다.

> **확인 완료 (2026-09-09)**: 이 구성으로 실제로 돌렸다. `redis:7-alpine` +
> `HOST_IP` announce 로 master 1 + replica 2 + Sentinel 3 이 뜨고, `docker kill` 로
> 페일오버가 정상적으로 났다 (`EXPERIMENTS.md` 실험 1). 처음 이 문서를 쓸 때는
> WSL 에 docker CLI 가 안 잡혀(Docker Desktop WSL 통합 꺼짐) 확인하지 못했던 부분이다 —
> 켜는 방법은 §1-1 에 적었다.
>
> 다만 **이미지 태그는 여전히 움직이는 `7-alpine`** 이다. 실측값을 남긴 판을 정확히
> 재현하려면 `redis:7.2-alpine` 처럼 고정 태그로 바꾸는 편이 낫다.

---

## 3. 컨테이너 이름 정리

기존 개발용 Redis(`qm-redis`)와 이름이 겹치지 않게 전부 `qm-ha-` 접두사를 붙였다.
둘을 동시에 띄우면 **6379 포트가 겹친다.** 실험 전에 `qm-redis` 를 내려라.

```bash
docker stop qm-redis
```

| 이름 | 역할 | 호스트 포트 |
|---|---|---|
| `qm-ha-master` | 최초 master | 6379 |
| `qm-ha-replica1` | replica | 6380 |
| `qm-ha-replica2` | replica | 6381 |
| `qm-ha-sentinel1~3` | Sentinel | 26379 / 26380 / 26381 |
| `qm-ha-app` | 앱 (T-B, `--profile app` 일 때만) | 18080 |

---

## 4. 상태 확인 명령 모음

전부 `./scripts/status.sh` 한 방에 들어 있지만, 하나씩 뜯어 봐야 할 때가 온다.

### Sentinel 쪽

```bash
S=qm-ha-sentinel1

# 지금 master 로 아는 주소. 클라이언트가 받는 값과 같다.
docker exec $S redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# master 의 전체 상태. flags 가 master 면 정상, s_down/o_down 이면 죽은 것으로 본다.
docker exec $S redis-cli -p 26379 SENTINEL master mymaster

# 이 Sentinel 이 아는 replica 들. master_link_status 가 ok 여야 복제가 붙은 것이다.
docker exec $S redis-cli -p 26379 SENTINEL replicas mymaster

# 서로를 인식한 다른 Sentinel. 3대면 여기 2대가 나와야 한다 (자기는 안 나온다).
# 비어 있으면 announce-ip 가 잘못된 것이다.
docker exec $S redis-cli -p 26379 SENTINEL sentinels mymaster

# 정족수를 지금 채울 수 있나. 페일오버를 하기 전에 물어보는 값이다.
docker exec $S redis-cli -p 26379 SENTINEL ckquorum mymaster

# Sentinel 이 실제로 들고 있는 설정 (rewrite 된 결과)
docker exec $S cat /data/sentinel.conf
```

### 데이터 노드 쪽

```bash
# role:master / role:slave, 그리고 연결된 replica 목록
docker exec qm-ha-master redis-cli INFO replication

# replica 쪽에서 봐야 하는 것
#   master_link_status:up      복제가 붙어 있다
#   master_last_io_seconds_ago 마지막 통신 이후 경과 - 이게 벌어지면 곧 sdown 이다
#   slave_read_only:1          승격 전에는 1 이어야 한다
docker exec qm-ha-replica1 redis-cli INFO replication

# 복제 오프셋. master 와 replica 의 차이가 곧 유실될 수 있는 양이다.
docker exec qm-ha-master   redis-cli INFO replication | grep master_repl_offset
docker exec qm-ha-replica1 redis-cli INFO replication | grep slave_repl_offset

# 방금 쓴 것이 replica 몇 대까지 갔나. 실험 4 의 핵심 명령.
docker exec qm-ha-master redis-cli WAIT 1 100
```

### 이벤트를 실시간으로 보기

```bash
# Sentinel 이 내보내는 모든 사건. 페일오버를 눈으로 보려면 이걸 띄워 두고 kill 해라.
docker exec qm-ha-sentinel1 redis-cli -p 26379 PSUBSCRIBE '*'

# 로그로 보기 (타임스탬프 포함)
docker logs -tf qm-ha-sentinel1
```

---

## 5. 수동 페일오버 vs 진짜 장애 주입

**둘의 결과를 같은 표에 올리면 안 된다.** 재는 것이 다르다.

| | `SENTINEL FAILOVER mymaster` | `docker kill qm-ha-master` |
|---|---|---|
| 감지 단계 | **없다.** down-after 를 안 기다린다 | 있다. `down-after-milliseconds` 만큼 기다린다 |
| 정족수 동의 | **안 구한다.** 받은 Sentinel 이 혼자 집행한다 | 구한다. `quorum` 만큼 `+odown` 에 동의해야 한다 |
| 구 master | 살아 있다. 승격 직후 강등되어 복제를 받는다 | 죽었다. 복귀할 때까지 없다 |
| 데이터 유실 | 없다 (구 master 가 새 master 를 따라잡는다) | **있을 수 있다.** 복제 안 된 쓰기는 사라진다 |
| 클라이언트가 겪는 것 | 짧은 재연결 | 타임아웃 → 재연결 → 토폴로지 갱신 |
| 재는 값의 의미 | **다운타임의 하한.** "잘 됐을 때 이만큼" | **실제 다운타임.** 장애 시 사용자가 겪는 값 |

수동 페일오버는 "Sentinel 설정이 제대로 됐나" 를 확인하는 스모크 테스트로 쓴다.
계획된 유지보수(노드 교체, 버전 업그레이드) 절차를 연습하는 데도 맞다.
**다운타임 수치를 보고하려면 반드시 `kill-master.sh` 쪽이어야 한다.**

```bash
./scripts/manual-failover.sh    # 스모크 테스트
./scripts/kill-master.sh        # 진짜 장애
```

한 가지 더. `docker stop`(SIGTERM)도 진짜 장애가 아니다. Redis 가 종료 인사를 하고
TCP 연결을 정상 종료하며 나가기 때문에, 클라이언트가 **즉시** 끊김을 알아차린다.
실제 장애(전원 차단, OOM kill, 커널 패닉)는 그런 인사가 없다. 그래서
`kill-master.sh` 는 `docker kill`(SIGKILL)만 쓴다.

---

## 6. 판 재현 — 실험 2·6 의 A / B / C

**세 판의 차이는 "어느 클라이언트가 Sentinel 을 보는가" 하나뿐이다.**
Lettuce 는 프로파일로, Redisson 은 `queuemate.lock.sentinel.nodes` 로 갈린다
(`RedissonConfig.java:41` 이 그 프로퍼티를 직접 읽는다).

| 판 | Lettuce | Redisson | 앱 실행 명령 |
|---|---|---|---|
| **A (before)** | 단일 | 단일 | 프로파일 없이 |
| **B (절반)** | Sentinel | 단일 | 프로파일 + `QUEUEMATE_LOCK_SENTINEL_NODES=""` |
| **C (after)** | Sentinel | Sentinel | 프로파일만 |

```bash
# ---- 공통 준비 ----
cd redis-ha-lab
./scripts/reset.sh && ./scripts/up.sh
docker exec -i qm-ha-master redis-cli < ../seed/gameconfig.redis

cd ../backend               # 스프링 프로젝트 (matching/backend)
export DB_URL='jdbc:h2:mem:matching;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS social'
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
export REDIS_SENTINEL_NODES=127.0.0.1:26379,127.0.0.1:26380,127.0.0.1:26381

# ---- 판 A : 둘 다 단일 노드 ----
./gradlew bootRun

# ---- 판 B : Lettuce 만 Sentinel (Redisson 은 단일) ----
QUEUEMATE_LOCK_SENTINEL_NODES="" \
  ./gradlew bootRun --args='--spring.profiles.active=sentinel'

# ---- 판 C : 둘 다 Sentinel ----
./gradlew bootRun --args='--spring.profiles.active=sentinel'
```

**실험 6 은 여기에 플래그 두 개를 더한다** (`docs/failover-retry-guide.md` §4).

```bash
FAILOVER_RETRY_ENABLED=true FAILOVER_LETTUCE_ENABLED=true \
  ./gradlew bootRun --args='--spring.profiles.active=sentinel'
```

**측정은 판과 무관하게 같다.**

```bash
cd redis-ha-lab

# 실험 2 : 단일 스레드, 60초, +10초에 kill
./scripts/http-probe.py 60 out/e2-C.csv C      # 터미널 1
./scripts/kill-master.sh                        # 터미널 2 (10초 뒤)
./scripts/verify-assign.py out/e2-C.csv .last-kill-ms

# 실험 6 : 동시 10스레드, 50초, +10초에 kill
./scripts/http-probe-concurrent.py 50 out/e6-C.csv C 10
./scripts/kill-master.sh
./scripts/verify-assign.py out/e6-C.csv .last-kill-ms
grep '\[failover\]' app.log
```

> **판을 바꿀 때마다 `reset.sh` → `up.sh` → 시드를 다시 한다.** 이전 판의 페일오버
> 결과(누가 master 인지)가 볼륨에 남아 있으면 `t0` 기준이 판마다 달라진다.

> **판 B 를 만들 때 같이 꺼지는 것이 있다.** `QUEUEMATE_LOCK_SENTINEL_NODES=""` 는
> `SentinelRecoveryWatcher` 의 Sentinel 주소까지 비운다 — 둘이 같은 프로퍼티를 읽기
> 때문이다. 그러면 `+switch-master` 구독이 꺼지고 복구가 시간 폴백만으로 돈다.
> 이건 설계 결함이고 `docs/failover-retry-guide.md` §6 (A) / §8-8 에 적어 두었다.

---

## 7. 측정 도구 — 무엇을 언제 쓰나

실험을 돌리면서 새로 만든 것들이다. 전부 `scripts/` 에 있고 python3 만 있으면 된다.

| 도구 | 무엇을 재나 | 쓰는 법 |
|---|---|---|
| `redis-write-probe.py` | **Redis 레벨 다운타임.** 매 200ms 마다 Sentinel 에 master 를 묻고 그 주소에 `SET` 한다. 앱이 없는 기준선 | `./scripts/redis-write-probe.py <초> <csv>` |
| `analyze-probe.py` | 위 CSV 를 `t0` 기준으로 해석. 마지막 성공 / 첫 성공 / 쓰기 불가 구간 / 실패 유형 집계 | `./scripts/analyze-probe.py <csv> .last-kill-ms` |
| `http-probe.py` | **앱 API 단일 스레드 프로브.** 200ms 간격으로 매칭 신청 | `./scripts/http-probe.py <초> <csv> <태그>` |
| `http-probe-concurrent.py` | **동시 요청 프로브.** 페일오버 구간에 요청을 충분히 밀어넣어 "배정 단계 실패" 를 만들 때 필요하다 | `./scripts/http-probe-concurrent.py <초> <csv> <태그> <스레드수>` |
| `verify-assign.py` | **HTTP 결과와 실제 배정 결과의 차이.** Redis 의 `qm:user:active-request:{userId}` 에 `partyId` 가 있는지로 판정한다 | `./scripts/verify-assign.py <csv> .last-kill-ms` |
| `false-positive-test.sh` | **오탐 유발.** `docker pause` 로 master 를 N초 얼렸다 깨우고 페일오버가 났는지 본다 | `./scripts/false-positive-test.sh <초> <라벨>` |
| `failover-timeline.sh` | Sentinel 로그를 `t0` 기준 타임라인으로 접는다 | `./scripts/failover-timeline.sh` |
| `kill-master.sh` | SIGKILL 장애 주입. `t0` 를 `.last-kill-ms` 에 남긴다 | |
| `restore-master.sh` / `status.sh` / `reset.sh` / `up.sh` | 복귀 / 상태 / 초기화 / 기동 | |

**`verify-assign.py` 가 이 실험 세트의 핵심이다.** HTTP 만 보면 판 B 가 가장 건강해
보인다(201 비율 86.7%, 처리량 1위). 실제로는 t0 이후 배정이 **0건**이다.
두 지표를 나누지 않으면 그 판을 절대 못 찾는다.

`debug-sleep.sh` 는 **Redis 7 에서 못 쓴다** (`enable-debug-command` 기본 `no`).
남겨는 뒀지만 오탐 유발은 `false-positive-test.sh` 를 쓴다.

---

## 8. 폴더 구성

```
redis-ha-lab/
├── docker-compose.yml            T-A. master 1 + replica 2 + Sentinel 3
├── docker-compose.netpart.yml    T-B 오버레이. 컨테이너 IP announce + 앱 컨테이너
├── .env.example                  HOST_IP / 이미지 / Sentinel 튜닝 값
├── redis/
│   ├── master.conf               master 템플릿
│   ├── replica.conf              replica 템플릿
│   └── entrypoint.sh             치환 후 /data/redis.conf 로 복사해서 기동
├── sentinel/
│   ├── sentinel.conf             Sentinel 템플릿 (3대가 포트만 달리해 공유)
│   └── entrypoint.sh             치환 후 /data/sentinel.conf 로 복사해서 기동
├── scripts/
│   ├── lib.sh                    공통 조각 (직접 실행 안 함)
│   ├── host-ip.sh                WSL2 eth0 주소
│   ├── up.sh / reset.sh          기동 / 볼륨까지 초기화
│   ├── status.sh                 상태 한 화면
│   ├── kill-master.sh            SIGKILL 장애 주입 (t0 를 .last-kill-ms 에 기록)
│   ├── restore-master.sh         죽인 노드 복귀 + 강등 순간 관측
│   ├── manual-failover.sh        계획된 전환 (스모크 테스트용)
│   ├── false-positive-test.sh    docker pause 로 오탐 유발 (실험 3 에서 실제로 쓴 것)
│   ├── debug-sleep.sh            DEBUG SLEEP - Redis 7 에서 못 쓴다. 위 파일을 써라
│   ├── inject-latency.sh         tc netem 지연 주입 (미사용)
│   ├── partition-master.sh       네트워크 격리 / 복구 (T-B 전용, 미사용)
│   ├── failover-timeline.sh      Sentinel 로그 → Redis 측 타임라인
│   ├── redis-write-probe.py      Redis 레벨 쓰기 프로브 (실험 1·3)
│   ├── analyze-probe.py          위 CSV 를 t0 기준으로 해석
│   ├── http-probe.py             앱 API 단일 스레드 프로브 (실험 2)
│   ├── http-probe-concurrent.py  앱 API 동시 프로브 (실험 6)
│   ├── verify-assign.py          Redis 상태로 실제 배정 성공/유실 판정
│   ├── lock-loss-repro.sh        실험 4. 락 유실 재현 (미실행)
│   ├── run-probe.sh              k6 + 장애 주입 + 집계 (미사용)
│   └── assign-gap.sh             201 은 받았는데 배정 못 간 요청 수 (verify-assign.py 로 대체)
├── k6/
│   ├── failover-probe.js         도착률 고정 프로브 (미사용 - python 프로브로 대체)
│   └── analyze.py                --out csv 를 초 단위로 접어 다운타임 산출
├── EXPERIMENTS.md                실험 시나리오 6개 + 실측 결과
├── RESULTS.md                    결과 요약본 (포트폴리오용)
└── docs/
    ├── lock-safety-analysis.md   PoolLock 이 어디서 깨지는지
    ├── app-config-snippets.md    앱을 Sentinel 에 붙이는 설정 (적용은 사용자 판단)
    └── failover-retry-guide.md   페일오버 재시도(임시 기능) 켜기/끄기/지우기
```
