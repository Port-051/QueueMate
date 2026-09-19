# db-design/ — 초기 설계 자료 (2026-09-19 개정)

## 이 폴더가 무엇인가

프로젝트 **초기(2026-08-22 ~ 23)의 설계 자료**다. 기능 명세서, DB 선정, ERD 명세, PostgreSQL 스키마, dbml, ERD 그림,
Redis 요약, 아키텍처 리뷰가 들어 있다. 당시 설계는 **LoL 전용 · Discord 로그인 · Discord 채널 · Web Push · 매칭 요청과
제안을 DB 에 저장 · 5서비스(Core API + SSE / 즉시 매칭 / 예약 RunTask / Notification Worker / Discord Worker)** 였다.

그 뒤 방향이 크게 바뀌었는데 이 폴더만 그대로 남아 현재 결정과 전면 충돌했다. **지금 하고 있는 것이 우선이다** —
그래서 2026-09-19 에 이 폴더를 현재 결정에 맞춰 개정했다. 다른 어떤 문서도 이 폴더를 근거로 인용하지 않는다.

**이 폴더는 여전히 기준 문서가 아니다.** 개정은 "틀린 말을 남겨 두지 않는다"가 목적이지 이 폴더를 새 기준으로 세우는 것이
아니다. 기준은 아래 "현재 기준은 어디인가"의 문서들이다. 어긋나면 그쪽이 맞다.

### 개정의 원칙

- 현재 결정으로 **확정된 사실만** 옮겨 적었다. 정해지지 않은 것을 지어내지 않았다.
- 고친 자리에는 `> 개정 이력: 예전 판은 …라고 적었다. 2026-09-19 에 바꿨다 (출처)` 를 **절 단위로** 달았다
  (`contracts/events.md` 와 같은 방식). 통째로 폐기된 절은 본문을 지우고 폐기 사유만 남겼다. 옛 본문은 git 이력에 있다
  (개정 직전 커밋: `302a04e`).
- 현재 결정에 **대응하는 답이 없는** 옛 서술은 고치지 않고 그대로 뒀다. 맞다고도 틀리다고도 하지 않았다.
  그 목록이 아래 "확인되지 않은 채 남은 서술"이다. **`개정 이력` 이 없다는 것이 "현재도 맞다"는 뜻은 아니다.**
- 출처 표기(`docs/…` · `contracts/…` · `CLAUDE.md`)는 전부 저장소 루트(`matching/`) 기준이다.
  `../platform/CLAUDE.md` · `../notification/CLAUDE.md` 는 옆 폴더(같은 저장소의 다른 브랜치, `git worktree`)다.

---

## 현재 기준은 어디인가

| 무엇 | 현재 기준 |
|---|---|
| 제품 정의 · 필수 기능 · non-goal | `docs/00_PRODUCT_SPEC.md`, `CLAUDE.md` §1. 단 `docs/00` §6 의 "게시판/LFG 글 작성" 금지는 `docs/11` D-11 로 뒤집혔다 |
| 결정과 그 근거 | `docs/11_DECISION_LOG.md` — 번호 결정 #1~#35, 그리고 파일 뒤쪽 "이 저장소에서 내린 결정"의 D-1~D-11 (D-9~D-11 은 2026-09-19 의 결정이다) |
| 매칭 엔진 규칙 · 불변식 INV-1~INV-10 · Redis 키 · Lua | `CLAUDE.md` §2~§4, `backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` |
| 게임 모드 설정 · 티어 | `docs/GAME_CONFIG.md`, `seed/gameconfig.redis` |
| 예약 매칭 | `docs/04_RESERVATION_MATCHING_SPEC.md`, `docs/11` #23·#24 |
| 알림(SSE) · 서버 간 이벤트(SQS FIFO) 계약 | `contracts/events.md`, `contracts/README.md` |
| 알림 배달 서비스 규칙 | `../notification/CLAUDE.md` |
| API 서버(계정·파티·소셜·예약 REST) 규칙 | `../platform/CLAUDE.md` |
| 배포 그림 | `docs/AWS_ARCHITECTURE.md` (Stage 2 목표 상태. 현재는 Stage 1 — `docs/11` #20) |

### DB 스키마의 현재 기준

**이 폴더가 아니다.** `03-schema.postgres.sql` · `04-erdcloud-import.sql` · `07-dbdiagram.dbml` · ERD 그림은 전부 옛 설계다.

| 무엇 | 어디 |
|---|---|
| 왜 PostgreSQL 인가, 스키마 배치, outbox, INV-9 제약 | `docs/WHY_POSTGRESQL.md` (단, 그 문서의 사용자 id `uuid` 서술은 낡았다 — 아래 대조표 #4) |
| schema-per-service, 크로스 스키마 FK·JOIN 금지, 스키마별 롤 | `docs/11` #17, D-1 |
| `platform` 이 소유하는 스키마와 **테이블 이름** | `../platform/CLAUDE.md` §3.5 — `account`(users, credentials/refresh_tokens, game_accounts) · `party`(parties, party_members, outbox) · `social`(friend_requests, friendships, blocks, reports, recent_players, outbox) · `reservation`(reservations) |
| `matching` 이 소유하는 테이블 | `matching.match_proposals` · `matching.proposal_members` · `matching.outbox` — **확정된 것만** 쓴다 (`docs/11` #27). 아직 구현되지 않았다 |
| 모양이 정해진 유일한 테이블 | `social.blocks` — `id` 일련번호 PK · `blocker_id` 문자열 · `blocked_id` 문자열 · `(blocker_id, blocked_id)` UNIQUE. `matching` 이 이미 이 모양으로 읽는다 (`backend/src/main/java/com/queuemate/matching/block/Block.java`, `backend/src/test/resources/schema.sql`, `docs/11` D-4) |

> **`platform` 테이블의 컬럼은 아직 정해지지 않았다** (`../platform/CLAUDE.md` §7 "테이블 컬럼, DB 롤"). Flyway 마이그레이션도
> 아직 어디에도 없다. 그래서 이 폴더의 스키마 파일을 **새 스키마로 다시 쓰지 않았다** — 쓰면 지어내는 것이 된다.
> 컬럼이 정해지면 그때 `platform` 쪽 마이그레이션이 기준이 되고, 이 폴더의 스키마 파일은 그대로 옛 자료로 남는다.

---

## 옛 서술 → 현재 결정 → 출처

| # | 옛 서술 (`db-design/`) | 현재 결정 | 출처 |
|---|---|---|---|
| 1 | 대상 게임은 LoL 하나 | LoL · VALORANT · PUBG 셋. 넷째 게임을 추가하지 않는다 | `docs/11` #8, `CLAUDE.md` §1, `docs/00` §3 |
| 2 | Discord OAuth2 로그인만. 자체 가입 없음 | 자체 계정(회원가입·로그인·로그아웃) + JWT access/refresh, refresh rotation 필수 | `docs/11` #16, `docs/00` §5 |
| 3 | 최초 로그인 시 Discord 사용자 ID 로 계정 생성 | 가입할 때 정한 **로그인 아이디(문자열)가 곧 사용자 id**. 아이디 변경 기능을 만들지 않는다 | `../platform/CLAUDE.md` §3.5 (2026-09-19 확정) |
| 4 | 사용자 PK 는 `bigserial` (옛 스키마) | 사용자 id 는 문자열. uuid 나 별도 일련번호를 사용자 식별자로 두지 않는다. `docs/WHY_POSTGRESQL.md` 의 `uuid` 서술도 이 결정으로 낡았다 | `../platform/CLAUDE.md` §3.5, `docs/11` D-4 |
| 5 | 파티 확정 시 Discord 비공개 텍스트 채널 생성, 선택적 Discord DM, Discord Worker | 폐기. 파티룸의 **자체 WebRTC 음성 + 텍스트 DataChannel**, 브라우저 직결(서버가 중계·저장하지 않는다) | `docs/11` #6·#25, `docs/00` §5 |
| 6 | 자체 음성·영상통화 제외 | 음성은 필수 기능이다. 시그널링은 보내기 = `app:platform` 의 REST `POST`, 받기 = SSE `WEBRTC_SIGNAL`. **WebSocket 은 없다.** TURN 은 Cloudflare 관리형으로 정해졌으나 나중 일이고 지금은 공개 STUN 으로 개발한다 | `docs/11` D-9·#25, `contracts/events.md`, `../platform/CLAUDE.md` §2·§3.3 |
| 7 | 매칭 요청 · 제안 · 좌석 · 수락 상태를 DB 테이블(`match_request` `match_offer` `offer_seat` `offer_participant`)에 저장. "Redis 에 두면 안 되는 것: 제안/좌석/수락 상태" | **진행 중인 실시간 매칭 상태는 Redis 에만**, 확정된 것만 DB(`match_proposals` + `proposal_members` + `outbox`, 단일 트랜잭션). `match_requests` 테이블을 만들지 않는다 | `docs/11` #27·#28, `CLAUDE.md` §3 |
| 8 | "Redis 가 통째로 날아가도 Postgres 로 복구 가능해야 한다", "`QUEUED` 요청 재적재 경로" | Redis 장애 시 새 매칭을 fail-closed 하고 **큐를 재구축하지 않는다.** 사용자가 다시 요청한다 | `docs/11` #29, `CLAUDE.md` §4 INV-10 |
| 9 | Redis 대기열(Sorted Set)에 줄 세우고, 주기적으로 정렬·순차 탐색해 후보 N명을 한 번에 선점 (결정적 FCFS) | 대기열이 없다. 대기 상태는 **"아직 안 찬 파티"**이고 파티를 "아직 필요한 핵심 조건 값"으로 역색인한다. 한 번에 한 명씩 합류한다 | `docs/11` #32·#33·#35, `CLAUDE.md` §4 |
| 10 | 거절·무응답 시 수락자를 잠그고 **빈 좌석만 대체 후보로 채운다.** 수락자는 다시 누르지 않는다. 전체 충원 제한시간, `REFILLING`, 좌석·충원 회차 | 제안이 깨지면 거절·무응답자만 빠지고 나머지는 파티에 남아 다시 기다린다. **옛 수락은 지운다** — 다시 차면 새 제안이 전원에게 열리고 전원이 다시 누른다. 좌석·회차·`REFILLING`·전체 제한시간이 없다 | `CLAUDE.md` §4 INV-4·INV-5 |
| 11 | 즉시 요청은 제안 생성 전에만 취소 가능 | 제안 도중에도 취소된다. 그 제안은 깨지고 남은 사람은 파티에 남는다 | `CLAUDE.md` §4 INV-5 (cancelled) |
| 12 | 요청 조건: 큐 · 목표 인원 · 허용 티어 범위(사용자 지정, 양방향) · 주/부 포지션 · 음성 · 목적 · 최대 대기시간 · 예상 플레이시간 | 조건은 **게임당 정확히 4개** — 게임 모드 · 핵심 조건 하나(LoL 포지션 / VALORANT 역할군 / PUBG 플랫폼) · 음성 · 목적. 티어는 조건이 아니라 자격이고 범위는 gameconfig 의 tier-range 표가 정한다. 새 조건은 `docs/12` 절차 없이는 금지 | `CLAUDE.md` §2·§4 INV-8, `docs/GAME_CONFIG.md` |
| 13 | 목표 인원을 사용자가 고른다. 자유 랭크 4인은 `CHECK` 로 차단 | 인원은 modeKey 가 정한다. 인원이 가변인 모드는 modeKey 를 인원별로 쪼개고, 금지된 인원은 modeKey 를 만들지 않는다 | `docs/11` #31 |
| 14 | 음성 `필수 / 가능 / 사용하지 않음` + 호환 규칙 | `REQUIRED` / `NO_VOICE` 둘뿐. "가능"(`OPTIONAL`)은 제거됐고 되돌리지 않는다 | `CLAUDE.md` §2, `contracts/README.md` 불일치 표 #1 |
| 15 | 플레이 목적 4종(가볍게 / 승리·랭크 / 초보 학습 / 숙련자) | `RANK_UP` / `NORMAL` / `FUN` | `CLAUDE.md` §2 (코드가 원본) |
| 16 | 사용자 차단 · 신고 · 재회 목록은 제외 기능 | 친구 / 차단 / 최근 함께한 사람 / 신고는 **필수**다. 차단 관계의 사용자는 같은 파티가 될 수 없다(INV-6) | `docs/11` #13, `docs/00` §5, `CLAUDE.md` §1·§4 |
| 17 | (옛 설계에 없음) | 차단은 DB(`social.blocks`)에 저장하는 것으로 끝난다. `matching` 은 확정 직전에 그 테이블을 **직접 조회**한다. `shared_read.blocked_pairs` 뷰도, Redis 선필터도, **`BlockChanged.fifo` 도 만들지 않는다**(2026-09-19 폐기) | `docs/11` D-1·D-2, `../platform/CLAUDE.md` §3.4 |
| 18 | 확정 후 나가기 없음, 파티 완료 상태 없음 | 파티룸에 **나가기**가 있다. 파티가 닫히면 `PartyClosed.fifo` 가 발행되고 **소비자는 `app:platform` 하나**다(2026-09-19 확정). `matching` 의 `status=PARTY` 를 누가 푸는지는 미정이다 | `docs/00` §5, `docs/11` #21, `../platform/CLAUDE.md` §3.4·§7 |
| 19 | 알림은 SSE + 브라우저 Web Push. Notification Worker 가 SQS `notification-events` 를 소비 | 알림은 **SSE 하나**, 15종. 발행 앱 → Redis Pub/Sub `qm:pubsub:push:{userId}` → `app:realtime` → SSE. 알림 이력을 저장하지 않고 놓친 알림을 재전송하지 않는다. Web Push 는 "나중에 보완으로" 미뤄져 있을 뿐이다 | `contracts/events.md`, `docs/11` #22·D-9·D-10, `../notification/CLAUDE.md` §2, `docs/WHY_SPRING_BOOT.md` §5-3 |
| 20 | SSE 재연결 시 RDS 의 현재 상태로 복구. heartbeat 형식 언급 없음 | 재연결 직후 클라이언트가 상태를 조회한다(SSE 먼저, 그다음 조회). `Last-Event-ID` 로 이어 보내지 않는다. heartbeat 는 이름 있는 이벤트이고 서버가 `retry:` 를 연결마다 흩어 내려 준다 | `contracts/events.md`, `docs/11` D-10 |
| 21 | 단일 백엔드 + 워커들(Core API + SSE / 즉시 매칭 / 예약 RunTask / Notification Worker / Discord Worker) | 배포 단위 4개 — `app:platform` / `app:matching` / `app:realtime`(폴더 이름 `notification`) / `app:reservation-batch`. 그 이상 분리하지 않는다 | `docs/11` #15·#24 |
| 22 | SQS 2개 — `notification-events`, `discord-commands` | transactional outbox + **SQS FIFO** — `ProposalConfirmed.fifo`(matching → platform), `PartyClosed.fifo`(platform → platform). 사용자 알림은 SQS 로 보내지 않는다 | `docs/11` #21·#22, `../platform/CLAUDE.md` §3.4 |
| 23 | 예약: 날짜 + 시작 시각 + 시작 가능 범위(정확/±30분/±1시간) + 예상 플레이시간. 전날 23:50 마감. 여러 후보 일정의 **묶음**(묶음 안 시간 중복 허용) | 예약 = 기존 조건 + 플레이 가능 시간(`availableFrom`~`availableTo`, 30분 단위) + 플레이할 양(`ONE_GAME`/`TWO_PLUS`). 최소 리드타임 30분. 시간이 겹치는 활성 예약은 예외 없이 거절(INV-9). 묶음 개념이 없다 | `docs/04` §1·§2·§6-1·§9, `docs/11` #11·#23 |
| 24 | 매일 KST 00시 EventBridge → RunTask 일괄 배치. **수락 없이 자동 확정.** 미성립은 `FAILED`, 당일 재배치 없음 | **1분 주기 배치 단일 경로**, 상시 1개 인스턴스(`app:reservation-batch`). 예약도 실시간과 같은 제안·수락을 거친다. 남은 시간이 줄수록 tier 를 넓힌다. EventBridge → RunTask 는 기각됐다 | `docs/11` #23·#24·#26, `docs/04` §6·§6-1·§8 |
| 25 | 모든 시간은 KST 기준 | 사용자 locale 로 입력받고 서버 저장은 UTC Instant | `docs/04` §2 |
| 26 | 조건 자동 완화 미사용 | 조건 완화는 soft condition 범위에서만 한다. 예약은 시간 기반 tier 완화가 있다. 실시간은 지금 완전 일치만 구현돼 있다(착수 범위) | `docs/00` §5, `docs/04` §6-1, `docs/11` #30 |
| 27 | 불변식 INV-1~INV-12 를 DB 제약으로 강제 (`02-erd-명세기준.md` §3) | 프로젝트의 불변식은 **INV-1~INV-10** 이고 번호가 다르다. 실시간 매칭의 불변식은 DB 가 아니라 **Redis Lua 원자 실행**이 지킨다. DB 가 강제하는 것은 확정 이후와 예약(INV-9)·소셜 쪽이다 | `CLAUDE.md` §4, `docs/WHY_POSTGRESQL.md` §1·§6 |
| 28 | 단일 스키마 + 테이블 간 FK | PostgreSQL 인스턴스 1개에 schema-per-service 6스키마. 크로스 스키마 FK·JOIN 금지, 스키마별 DB 롤. 유일한 예외는 `matching` 롤의 `social.blocks` SELECT | `docs/11` #17·D-1 |
| 29 | ECS Fargate 2 AZ, RDS Multi-AZ, ElastiCache, NAT (아키텍처 그림 · `01` §4 · 리뷰 부록의 비용 표) | 현재 배포 기준은 **Stage 1 — 단일 EC2 + Docker Compose**(PostgreSQL·Redis 도 컨테이너). Fargate 는 Stage 2. k8s/HPA/sticky session 전제 구현 금지 | `docs/11` #20, `CLAUDE.md` §3 |
| 30 | 게임 모드 · 큐를 DB enum(`queue_type`)으로, `game` 테이블로 | 게임 모드 설정은 데이터다 — 앱은 Redis `qm:gameconfig:*` 에서 **읽기만** 한다. 티어 사다리도 Redis ZSET 이다 | `CLAUDE.md` §2·§3, `docs/GAME_CONFIG.md`, `docs/11` D-8 |
| 31 | "공개 게시판 형태의 빈자리 모집은 제공하지 않는다" (명세 2.8 도입부, `02` §1 의 `RECRUITING` 삭제 근거) | **파티 모집 게시판이 제품에 들어왔다** — 자동 매칭이 기본 경로, 게시판이 두 번째 경로다. 모집 글이 곧 파티방이고 들어온 사람은 음성으로 바로 말을 건다. `app:platform` 의 일이다. 자동 매칭의 제안 단계가 게시판으로 빈자리를 채우는 것은 아니다. 공개 사용자 탐색·길드·피드·잡담용 공개 채팅방은 여전히 금지다 | `docs/11` D-11 (#14 개정, 2026-09-19), `CLAUDE.md` §1, `../platform/CLAUDE.md` §7.1 |

---

## 파일별로 무엇을 했나

| 파일 | 처분 | 내용 |
|---|---|---|
| `README.md` | **새로 만듦** | 이 문서 |
| `웹_게임_파티_자동_매칭_서비스_기능_명세서_종합본.md` | **고침** | 머리말 추가. §0 · §1.1~§1.5 · 대주제 1~3 의 책임 문단 · 중주제 1.1~1.8 · 2.1 · 2.2 · 2.6 · 2.7 · 3.2 · 3.5 · §6 · §7 · §8 을 고치고 개정 이력을 달았다. **통째로 폐기**: §1.4(예약 묶음) · 중주제 1.9 · 2.3 · 2.4 · 2.5 · 2.8 · 3.1 · 3.3 · 3.4 · 대주제 4(Notification Worker) · 대주제 5(Discord Worker) · §6.3. 표 안에서 폐기된 줄은 소주제 ID 를 남기고 `(폐기)` / `(대응 없음)` 으로 표시했다. 신설한 줄은 2.2.12(차단 관계 판정) 하나다. **손대지 않은 중주제**: 1.3(머리에 주석만) · 2.9 |
| `01-db-선정.md` | **고침** | 결론(PostgreSQL + Redis)은 그대로이고, "무엇을 어디에 두느냐"를 전 절에서 고쳤다. §5 는 폐기. §2 의 SQL 예시는 기법을 보이려고 본문 그대로 두고 주석 한 줄과 개정 이력을 달았다 |
| `02-erd-명세기준.md` | **고침 + 머리말** | §1 · §2 · §3 의 표에 "현재" 열을 달았다(옛 열은 그대로). §3 머리에 **INV 번호가 프로젝트 번호와 다르다**는 주의를 달았다. §4 의 ERD(mermaid) 본문은 **바이트 단위로 그대로**이고 그 위에 테이블별 처분 표를 달았다. §5 · §6 은 폐기 |
| `매칭-Redis-요약.md` | **전체를 다시 씀** | 옛 구조(`mq:` / `req:` / `claim:` 키, Core API 에 `PUBLISH`, 요청 메모 JSON)가 통째로 바뀌어 고칠 자리가 남지 않았다. 식당 비유와 문체는 유지했다 |
| `아키텍처_리뷰_할일.md` | **머리말만** | 그 시점의 검토 기록이다. 본문은 고치지 않고, 읽을 때 걸러야 할 곳을 머리에 적었다 |
| `review/comm-wiring.html` | **머리말만** | 위 검토의 시각 자료다. 파일 맨 위에 HTML 주석, 페이지 머리에 보이는 안내 문단 하나를 넣었다. 본문은 그대로다 |
| `03-schema.postgres.sql` | **머리말만** (`--`) | 옛 설계의 스키마라는 것, 어느 테이블/컬럼이 어떤 결정으로 폐기·변경됐는지, 지금도 참고할 만한 기법을 주석 블록으로 달았다. **SQL 본문은 한 글자도 고치지 않았다** |
| `04-erdcloud-import.sql` | **머리말만** (`--`) | 위와 같다. MySQL 문법 사본이다 |
| `07-dbdiagram.dbml` | **머리말만** (`//`) | 위와 같다 |
| `05-erd.svg` · `06-erd.png` | **손대지 않음** | 옛 설계의 ERD 그림이다(18개 테이블). **다시 그리지 않았다.** 테이블별 처분은 `02-erd-명세기준.md` §4 머리의 표를 보라 |
| `game-party-architecture-with-igw.png` | **손대지 않음** | 옛 설계의 AWS 아키텍처 그림이다 — 2 AZ ECS Fargate 위의 Core API + SSE / 즉시 매칭 / Notification Worker / Discord Worker, EventBridge 00시 RunTask, SQS `notification-events` · `discord-commands`, FCM · Web Push, Discord API, 모바일 앱 클라이언트. **다시 그리지 않았다.** 현재의 배포 그림은 `docs/AWS_ARCHITECTURE.md` 와 `docs/aws-architecture.drawio` 다 |

---

## 현재도 유효한 내용

이 폴더에서 지금 읽어도 쓸모가 있는 것. **근거로 인용할 때는 이 폴더가 아니라 괄호 안의 현재 문서를 인용하라.**

- **PostgreSQL + Redis 라는 선택 자체**와, 관계형이어야 하는 이유 — 확정 트랜잭션, DB 제약으로 강제하는 불변식 (`01-db-선정.md` §1·§2 → `docs/WHY_POSTGRESQL.md`).
- **`btree_gist` + `EXCLUDE USING gist` 로 시간 구간의 겹침을 DB 제약으로 막는 기법** (`01` §2, 스키마 파일의 `user_busy_interval`). INV-9 에 그대로 쓴다 (`docs/WHY_POSTGRESQL.md` §1-1, `../platform/CLAUDE.md` §5).
- **부분 유니크 인덱스**로 "활성인 것만 하나"를 강제하는 기법.
- **transactional outbox** — 확정 트랜잭션 안에서 이벤트를 같이 적재한다는 생각. 다만 용도는 알림이 아니라 SQS FIFO 도메인 이벤트다 (`docs/11` #21).
- **티어는 문자열이 아니라 순번으로 비교해야 한다**는 주의 (`02` §2). 지금은 Redis ZSET 의 score 가 그 순번이다.
- **Lua 로 확인과 쓰기를 한 원자 실행에 묶는다**는 원칙 (명세 2.6.3 → `CLAUDE.md` §4 "원자성 규칙").
- **정원이 차기 전에는 제안을 열지 않는다 / 전원이 수락해야 확정한다** (명세 §1.2 → INV-4).
- **부분 실패 격리** — 알림 실패로 매칭·확정을 뒤집지 않는다 (명세 옛 4.2.7 → `CLAUDE.md` §3 `PushPublisher`).
- **유사도·추천 점수를 쓰지 않는다 / 웹 전용** (`docs/03` §1, `docs/11` #1).
- 리뷰 문서의 몇 가지 결론 — 서비스 간 동기 호출을 두지 않는다, Pub/Sub 유실은 상태 재조회로 메운다, `EventSource` 는 헤더를 못 붙인다 (`docs/11` #15, `contracts/events.md`, `../notification/CLAUDE.md` §7).

---

## 확인되지 않은 채 남은 서술

현재 결정에 **대응하는 답이 없어 고치지 않고 그대로 둔** 옛 서술이다. 맞는지 틀린지 모른다. 정해지면 해당 기준 문서에
적고, 이 폴더에서는 이 목록에서 지우면 된다. (이미 "미정"이라고 기록된 것은 그 출처를 괄호에 적었다.)

### 기능 명세서

| 자리 | 남은 서술 | 무엇이 정해져야 하나 |
|---|---|---|
| §1.1 | "리그 오브 레전드 한국 서버만 지원한다" | 게임 셋 각각의 서버·지역 범위 (`docs/GAME_CONFIG.md` 의 티어 규칙은 KR 기준이라고만 적는다) |
| §1.1 · 1.1.7 · 1.1.8 | 한국 서버·한국 표준시가 서비스 기본값, 국가·지역·언어 선택 없음 | 위와 같다. 저장은 UTC 로 정해졌으나 화면 기본값은 답이 없다 |
| §1.1 · §1.5 · §8 | "일회성 예약", 반복 예약 제외 | 예약의 반복 여부 |
| §1.3 · 1.7.13 · 1.7.15 · 1.7.16 | 확정 후 확정 취소 없음 · 구성원 추가/교체 없음 · 빈자리 모집 없음 · 미접속자 자동 대체 없음 · 구성 잠금 | **나가기가 생긴 지금 이것들이 어떻게 양립하는가.** 한 명이 나가면 파티는 어떻게 되나, 언제 파티가 닫히나(`PartyClosed` 의 발행 조건). "확정 뒤 빈자리가 생기면 다시 모집을 열 수 있는가"는 게시판 쪽에서도 미정이다 (**미정** — `docs/11` D-11) |
| §1.3 · §1.5 · §8 | 플레이 종료 버튼 없음 · 파티 완료 상태 없음 | 위와 같다 (예약 상태에는 `COMPLETED` 가 있다 — `docs/04` §3) |
| §1.5 · §8 | 평가 · 신뢰도 점수 · 전체 이용 이력 · 유사도 점수 · 영상통화 제외 | 제외가 맞는지. `docs/00` §6 의 non-goal 목록에는 이 항목들이 없다 |
| 1.1.5 | 기기 간 상태 복구 | 실시간 매칭은 상태 조회로 되지만 파티·예약 쪽은 답이 없다 |
| 중주제 1.2 | 서비스 닉네임, 플레이 분위기(표시 전용), 주·부 포지션 기본값, 프로필 값을 요청 초기값으로 제공 | **게임이 셋일 때 프로필을 어떻게 두는가**(게임별 기본 조건인지). "기본 프로필"이 필수라는 것만 정해져 있다 (`docs/00` §5) |
| 중주제 1.3 전체 | Riot ID 등록·형식 검증·계정 조회, 소유 미검증 안내, 솔로/자유 랭크 조회 항목, 연결 상태, 수동·자동 갱신, 장애 시 저장 정보 사용, 동시 조회 통합, 요청 티어 스냅샷 | 게임 계정 연동 범위 (**미정** — `../platform/CLAUDE.md` §7 "게임 계정 연동"). VALORANT · PUBG 계정은 어떻게 하는가 |
| 1.4.9 | 친구와 함께 신청하는 기존 파티 단위 요청 미지원 | 그룹 단위 매칭 요청을 받는가 |
| 1.4.12 | 제한된 사용자에게 대체 큐 안내 | 화면 정책 |
| 1.4.19 · 2.2.6 · 3.2.5 | 요청 생성 시점의 티어를 스냅샷으로 고정해 쓴다 | 지금은 자기신고 티어를 요청에 싣는다. 연동이 붙은 뒤의 스냅샷 정책 |
| 1.4.20 · 1.4.21 · 2.1.9 | 신청 전 조건 요약, 현재 대기 인원 표시·집계 | 화면과 그 API (`GET /games` 도 아직 없다 — `contracts/README.md` #7) |
| 1.4.22 | 대기 중인 즉시 요청 수정 | 수정 API 를 두는가 (지금은 생성·취소·조회뿐이다) |
| 1.5.10 · 1.5.11 | 최신 상태 반환, 생성·수정·취소의 중복 API 요청 방지 | API 멱등성 정책 (지금 두 번째 생성은 `409`) |
| 1.6.1 · 1.6.2 · 1.6.10 | 제안 상세에 무엇을 보여 주나, 확정 전 게임 계정 비공개, 익명 수락 현황 | 제안 화면의 정보 범위. 수락 진행 이벤트는 계약에 없다 (**미정** — `contracts/events.md` "미해결 계약 구멍") |
| 1.6.24 | 직접 거절한 사용자는 자동 재대기시키지 않는다 | 현재 구현과 같지만(거절한 본인은 큐에서 빠진다) 정책으로 결정한 기록은 없다 |
| 중주제 1.7 (1.7.1~1.7.3 · 1.7.5~1.7.12) | 시작·종료 예정 시각, 참가자 기본정보 범위, Riot ID 공개 시점, 음성 파티 표시, 합의 조건 스냅샷, 참여 가능/늦을 예정 입력, 예약 파티 티어 재조회와 안내 | 파티룸의 구체 기능 (**미정** — `../platform/CLAUDE.md` §3.1·§7). 정해진 것은 "현재 파티원 · 게임 ID · Ready · 음성 · 텍스트 · mute · 나가기 · 친구 추가 · 차단 · 신고"라는 목록뿐이다 |
| 1.8.1 · 1.8.2 | SSE 연결 생성·인증 | SSE 인증 방식과 엔드포인트 경로 (**미정** — `../notification/CLAUDE.md` §7) |
| 1.8.6 · 1.8.16 | 익명 수락 현황 전달, 백그라운드 탭 알림 수 | 위 1.6.10 과 같다 / 화면 정책 |
| 2.1.7 · 2.1.8 | 최대 대기시간과 대기 만료 | **실시간 매칭 대기에 시한을 두는가.** 지금은 제안 시한(20초)만 있고 대기 시한은 없다 |
| 2.2.11 | 플레이 분위기는 매칭 조건이 아니다 | 플레이 분위기라는 항목 자체를 두는가 |
| 2.6.5 | 취소와 선점 중 먼저 성공한 쪽을 적용 | 방향은 현재 구현과 같다. 문구만 옛 모델 기준이다 |
| 중주제 2.9 전체 | 매칭 실패 사유 분석(조건별 후보 감소 계산) | 하는가. 매칭 "시도"는 DB 에 남지 않고 Prometheus 메트릭으로만 본다 (`docs/11` #27) |
| 3.2.10 · 3.5.1 · 3.5.2 | 예약 매칭의 결정적 정렬, 동일 입력 동일 결과, 재실행 중복 방지 | 예약 짝 찾기의 선택 규칙. 제품 정의는 "호환 후보군 안에서 랜덤 배정"이다 (`docs/00` §2) |
| 중주제 3.1 · 3.4 · 3.5.6 (폐기 문단 안) | 배치가 어떤 예약을 집는가, 언제 `EXPIRED` 가 되고 그때 알리는가, 실행 결과를 DB 에도 남기는가 | 문서에 없다 |
| 옛 1.9.7 · 4.3.3 · 4.3.10~4.3.13 (폐기 문단 안) | 예약 리마인더(24시간·1시간·10분 전), 준비 상태 입력 알림, 수락 시한 임박 알림 | 하는가, 한다면 어느 경로로(SSE 는 탭이 열려 있을 때만 닿는다). Web Push 를 언제 붙이는가 |

### 그 밖의 파일

| 파일 · 자리 | 남은 서술 | 비고 |
|---|---|---|
| `01-db-선정.md` §1 "NoSQL 을 택했을 때 무너지는 지점" | DynamoDB · MongoDB 와의 비교 | "MongoDB 대신 PostgreSQL"류의 비교를 결정으로 기록한 항목은 없다 (`docs/WHY_POSTGRESQL.md` §0-1). 논거로는 무해해서 남겼다 |
| `02-erd-명세기준.md` §1 | `party_rating` 을 걷어냄(평가 기능 없음) | 위 "평가" 항목과 같다 |
| `02-erd-명세기준.md` §4 (mermaid) · 스키마 파일 3개 · ERD 그림 2개 | 옛 18개 테이블의 컬럼 전부 | 본문을 고치지 않았다. 처분이 "미정"인 것 — `app_user` 의 프로필 컬럼과 `user_sub_position`, `riot_account` 의 랭크 컬럼, `party_member.ready_state`, `play_mood` enum, `reservation_batch_run`. **`platform` 테이블의 컬럼이 정해지면 이 목록은 저절로 해소된다** |
| `아키텍처_리뷰_할일.md` · `review/comm-wiring.html` | 본문 전체 | 검토 기록이라 고치지 않았다. §2-5 의 Riot API 타임아웃·서킷 브레이커 제안은 게임 계정 연동 범위가 정해질 때 다시 볼 만하다 |

---

## 어긋날 때 무엇을 따랐나

개정하면서 현재 결정 문서끼리 어긋난 곳을 만났다. 이 폴더는 아래와 같이 **더 나중에 정해진 쪽**을 따랐다.
어긋난 문서 자체는 이 폴더의 일이 아니라 고치지 않았다.

| 무엇 | 어긋난 곳 | 이 폴더가 따른 것 |
|---|---|---|
| `BlockChanged.fifo` | `../platform/CLAUDE.md` §3.4 는 "만들지 않는다(2026-09-19 폐기)". 그러나 `CLAUDE.md` §3("`BlockChanged.fifo` 소비를 맡는다") · `contracts/events.md` 의 SQS 표 · `contracts/README.md` 머리 표와 #12 · `docs/AWS_ARCHITECTURE.md` · `docs/11` #21 과 색인 표는 아직 소비자로 적는다 (`docs/11` D-2 는 "지금 만들지 않는다"까지만 말한다) | 폐기 |
| `PartyClosed.fifo` 의 소비자 | `../platform/CLAUDE.md` §3.4 는 "소비자는 `app:platform` 하나, `matching` 은 읽지 않는다". 그러나 `CLAUDE.md` §4 INV-2·INV-4 행 · `contracts/README.md` #12 · `HANDOFF.md` 는 `matching` 의 "`PartyClosed` 소비"를 아직 없는 것으로 적는다 | `app:platform` 하나. `status=PARTY` 를 푸는 길은 미정 |
| 사용자 id 타입 | `docs/WHY_POSTGRESQL.md` 는 `uuid`. `../platform/CLAUDE.md` §3.5 와 `docs/11` D-4 는 문자열 | 로그인 아이디 문자열 |
| 수락 집계 자료구조 | `docs/11` #28 · `docs/03` §3 · `docs/07` §5-1 은 HASH(`userId -> PENDING|ACCEPTED|DECLINED`)이고 #28 은 `SADD` + `SCARD` 를 기각 대안으로 적는다. 그러나 `CLAUDE.md` §4 INV-4 가 적는 현재 구현은 수락자 SET + `SCARD` 다. 이 차이를 기록한 결정 항목은 찾지 못했다 | 구현(`CLAUDE.md` §4). 자료구조 세부는 이 폴더에 적지 않고 "Redis + Lua 로 집계한다"까지만 적었다 |
| Redis 키 목록 | `docs/07_REDIS_DESIGN.md` §2 는 원문 그대로라 `qm:queue:*` · `qm:request:*` · `qm:block:*` · `qm:sse:lastevent:*` 가 남아 있다. `docs/11` #32 · D-2 · `contracts/events.md` "재연결"로 없어진 키들이다 | `CLAUDE.md` §3 과 `SharedKeys.java` |
| TURN credential 발급 | `docs/11` #25 와 `../platform/CLAUDE.md` §2 는 발급 주체를 `app:realtime` 이라 적는다. `../notification/CLAUDE.md` §2 의 "하는 일"에는 그 일이 없다 | 이 폴더에는 발급 주체를 적지 않았다 |
