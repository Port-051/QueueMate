# 왜 PostgreSQL(관계형 DB)인가

> **이 문서를 먼저 어떻게 읽어야 하는지.**
>
>    ⚠ **1번은 2026-09-11 에 사실이 아니게 됐다. 아래 갱신본을 읽어라.**
>
> 1. ~~**이 저장소(`matching/`)는 지금 DB를 쓰지 않는다.** 의존성은 Redis 하나뿐이고~~
>    ~~`data-jpa`/`jdbc`/`postgresql`이 없다, 설정에도 datasource가 없다.~~
>
>    **갱신 (2026-09-11).** DB 의존성이 들어왔다 — `backend/build.gradle` 에
>    `spring-boot-starter-data-jpa` + `com.h2database:h2` + `org.postgresql:postgresql`,
>    `application.yaml` 에 `spring.datasource`(기본 H2 인메모리, `MODE=PostgreSQL`) +
>    `spring.jpa`(`ddl-auto: none`, `open-in-view: false`). **다만 쓰는 곳은 하나뿐이다** —
>    INV-6 차단 조회(`matching/block/BlockRepository.java`, docs/11 D-1 · D-3).
>    매칭 상태는 여전히 Redis가 원본이고 DB로 옮기지 않는다 (CLAUDE.md §3).
>    스키마도 아직 없다 — Flyway 미도입이라 `social.blocks` 테이블이 실제로는 없고,
>    테스트만 `backend/src/test/resources/schema.sql` 로 흉내를 낸다.
>    인증은 여전히 없다 (`spring-boot-starter-security` 없음).
> 2. 따라서 이 문서는 **"이 프로젝트가 지금 쓰는 것"의 설명이 아니다.**
>    `matching`이 proposal **확정(confirm)** 단계까지 구현하게 되면 —
>    `match_proposals` + `proposal_members` + `outbox`를 단일 트랜잭션으로 쓰는 그 지점 —
>    비로소 필요해지는 근거다. 현재 미구현임은 저장소 자신이 이미 기록해 두었다
>    (`docs/11_DECISION_LOG.md:433-445` "색인된 결정 중 이 저장소가 아직 구현하지 않은 것").
> 3. **"왜 관계형인가"를 논증한 결정 항목은 존재하지 않는다.** 아래 §0을 반드시 읽어라.
>    이 문서의 근거는 전부 **스키마와 문서에서 역으로 도출한 것**이다.

---

## 결론

**확정된 매칭 결과를 영속화하는 저장소는 관계형이어야 한다. 이유는 성능도 친숙함도 아니라 다음 셋이다.**

| # | 근거 | 관계형이 아니면 무엇이 무너지나 |
|---|---|---|
| 1 | **불변식을 앱이 아니라 DB가 강제한다** | INV-9의 exclusion constraint, INV-1의 partial unique index를 앱 코드로 옮기면 "조회 후 삽입" 사이에 race 창이 생긴다 |
| 2 | **transactional outbox에 단일 트랜잭션이 필요하다** | `match_proposals` + `proposal_members` + `outbox`가 한 트랜잭션이어야 한다. 트랜잭션 없는 저장소로는 이 패턴 자체가 성립하지 않는다 |
| 3 | **스키마별 롤로 경계를 런타임이 막는다** | 크로스 스키마 접근을 코드리뷰가 아니라 `permission denied`가 막는다는 설계 전체가 사라진다 |

부수적이지만 실제로 무게가 있는 것 둘:

| # | 근거 |
|---|---|
| 4 | **데이터가 실제로 관계형이다.** 친구·차단·파티멤버·게임계정이 전부 다대다이고, 정합성을 `ON DELETE CASCADE`가 유지한다 |
| 5 | **역할 분담이 명확하다.** "DB는 성사된 매칭만 안다." 진행 중 상태는 Redis가 source of truth다 |

---

## 0. 정직하게 먼저 적을 것 — 이 문서의 지위

### 0-1. "왜 관계형인가"를 논증한 결정 항목이 없다

찾아본 결과는 이렇다.

| 문서 | 위치 | 실제로 다루는 것 |
|---|---|---|
| `docs/11_DECISION_LOG.md` #4 | `matching/docs/11_DECISION_LOG.md:25` | *"PostgreSQL을 영속 DB로 사용한다."* — **한 줄 선언뿐.** 근거·영향·기각 대안이 전부 없다 |
| `docs/14` §3 "DB를 **몇 개로** 할까" | queueMate `feature/frontend:docs/14_ARCHITECTURE_RATIONALE.md:43-57` | database-per-service vs 인스턴스 1개 + 스키마 분리. **관계형이라는 전제 위에서 개수를 고른다** |
| `docs/14` §19 "매칭 요청을 **어디에** 둘까" | `matching/docs/14_ARCHITECTURE_RATIONALE.md:198-231` | 전부 DB vs Redis-only + 확정만 DB. **무엇을 넣을지**를 고른다 |
| `docs/11` #27 | `matching/docs/11_DECISION_LOG.md:222-247` | 위 §19의 결정 기록 |

즉 **"MongoDB 대신 PostgreSQL"류의 비교가 기록된 적이 없다.**
"PostgreSQL을 쓴다"는 #1~#13과 나란히 놓인 **초기 전제(fixed decision)**로 출발했고,
그 뒤의 모든 결정이 그 위에서 이루어졌다.

**따라서 아래 §1~§5는 사후 정당화(post-hoc rationale)다.**
확정된 스키마와 확정된 문서를 읽고 **"이것들은 관계형이 아니면 성립하지 않는다"**를
역으로 도출한 것이지, 당시에 그렇게 논증해서 고른 기록이 아니다.
그래도 근거로서는 유효하다 — 지금 스키마가 실제로 관계형 기능에 **의존**하고 있기 때문이다.

### 0-2. 브랜치 간 모순: `match_requests` 테이블

| 어디 | 무엇 |
|---|---|
| queueMate `feature/frontend-v2` (현재 체크아웃) | `backend/src/main/resources/db/migration/V1__init_schema.sql:105-127`에 **`match_requests` 테이블이 실재한다** |
| queueMate `feature/frontend` `docs/11` #27 | *"`match_requests` 테이블을 만들지 않는다"*, *"영향: `match_requests` 테이블 삭제"* (`matching/docs/11_DECISION_LOG.md:222-223`, `:242`) |

**v2 백엔드 스키마가 #27(2026-09-01) 결정 이전 스펙이기 때문이다.**
증거가 하나 더 있다 — #27은 같은 자리에서 세 가지 후속 변경을 지시하는데
(`matching/docs/11_DECISION_LOG.md:242-245`), V1 스키마에는 셋 다 반영돼 있지 않다.

| #27이 지시한 것 | V1 스키마의 실제 |
|---|---|
| `match_requests` 삭제 | `V1__init_schema.sql:105`에 존재 |
| `proposal_members.acceptance` 컬럼 삭제 | `V1__init_schema.sql:167`에 존재 |
| `match_proposals.condition_snapshot_json` 신설 | 없음 (`grep condition_snapshot` → 0건) |

**합칠 때 정리가 필요하다.** 이 문서는 그 모순을 숨기지 않는다.
다만 §1에서 `match_requests`의 partial unique index를 근거로 인용하는 것은 여전히 유효하다 —
**그 테이블이 남든 사라지든, "부분 조건에 유니크를 건다"는 기법 자체가 논점**이고,
같은 기법이 `friend_requests`(`V1__init_schema.sql:240-241`)와
`reservations`(`:147-150`)에도 그대로 쓰이기 때문이다.

### 0-3. 문서화됐지만 아직 어디에도 구현되지 않은 것

정직을 위해 함께 적는다. **§3(스키마별 롤)과 §2(outbox)는 문서에만 있고 코드에 없다.**

| 항목 | 문서 | 코드 |
|---|---|---|
| 7스키마 분리 | queueMate `feature/frontend:docs/06_DATA_MODEL.md:7-26` | `V1__init_schema.sql`에 `CREATE SCHEMA` **0건**. 모든 테이블이 기본 스키마에 있다 |
| 스키마별 롤 / GRANT | 같은 문서 `:42-43` | `V1`/`V2`에 `GRANT` **0건** |
| ~~`shared_read.blocked_pairs` 뷰~~ | 같은 문서 `:28-52` | **만들지 않기로 했다** (docs/11 D-1). §3-1 참고 |
| `outbox` 테이블 | 같은 문서 `:210-235` | `V1`/`V2`에 없음 |
| `infra/postgres/init.sql` | queueMate `feature/frontend` | **2줄뿐이다.** `CREATE EXTENSION pgcrypto` + *"Schema migrations should be moved to Flyway by Member 3."* |

---

## 1. 불변식을 앱이 아니라 DB가 강제한다

### 1-1. INV-9 — exclusion constraint

> INV-9: 사용자는 시간이 겹치는 활성 예약을 중복 등록할 수 없다. (`CLAUDE.md` §4)

queueMate 저장소(`feature/frontend-v2`)의
`backend/src/main/resources/db/migration/V1__init_schema.sql:145-150`:

```sql
-- INV-9: 시간이 겹치는 활성 예약을 중복 등록할 수 없다.
-- 30분 경계 정렬은 docs/06에 따라 service layer가 검증한다.
CONSTRAINT reservations_no_active_overlap EXCLUDE USING gist (
    user_id WITH =,
    tstzrange(available_from, available_to) WITH &&
) WHERE (status IN ('ACTIVE', 'PROPOSED'))
```

이게 성립하려면 확장이 먼저 켜져 있어야 한다 — `V1__init_schema.sql:7`:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

`btree_gist`가 필요한 이유는 이 제약이 **등호 비교(`user_id WITH =`)와
범위 겹침(`&&`)을 하나의 GiST 인덱스에서 섞기** 때문이다. GiST는 기본적으로 범위·기하 연산자만
다루므로, 스칼라 `=`를 같은 인덱스에 넣으려면 `btree_gist`가 그 연산자 클래스를 공급해야 한다.

**이걸 앱에서 하면 무엇이 깨지는가.**

```java
// 이렇게 짜면 틈이 생긴다
boolean overlaps = repo.existsActiveOverlapping(userId, from, to);   // ── (A) 조회
if (overlaps) throw new ConflictException();
repo.save(new Reservation(userId, from, to));                        // ── (B) 삽입
```

같은 사용자의 요청 두 개가 동시에 들어오면 순서는 이렇게 될 수 있다.

```
스레드 1: (A) 겹침 없음 ─────────────┐
스레드 2:        (A) 겹침 없음 ──────┤   ← 둘 다 아직 아무것도 안 넣었으므로 둘 다 "없음"
스레드 1:                (B) INSERT ─┤
스레드 2:                    (B) INSERT   ← INV-9 위반. 겹치는 활성 예약 2개
```

**(A)와 (B) 사이의 창이 문제의 전부다.** 앱 락으로 막으려면 인스턴스가 1개여야 하고,
분산 락으로 막으려면 락 저장소(Redis)의 가용성에 예약 등록이 종속된다.
exclusion constraint에는 그 창이 없다 — **(A)를 아예 없애고 (B) 하나만 남기기 때문이다.**
두 번째 INSERT는 제약 위반으로 실패하고, 앱은 그 예외를 409로 번역하기만 하면 된다.

동시에, 이 제약이 **왜 이 위치에 있는지**도 명확하다. 이건 "빠른 조회"가 아니라
**"동시에 들어와도 참인 사실"**이고, 그건 인덱스가 아니라 제약의 일이다.

`WHERE (status IN ('ACTIVE','PROPOSED'))`가 붙은 것도 정확하다 —
취소·만료된 예약은 겹쳐도 상관없으므로 제약의 적용 범위 자체를 좁힌다.
관계형 DB 밖에서 이 세 가지(등호 + 범위겹침 + 상태 조건부)를 한 원자 단위로 표현할 방법은 없다.

### 1-2. INV-1 — partial unique index

> INV-1: 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다. (`CLAUDE.md` §4)

`V1__init_schema.sql:120-122`:

```sql
-- INV-1: 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다.
CREATE UNIQUE INDEX match_requests_one_active_per_user_idx
    ON match_requests (user_id) WHERE status IN ('QUEUED', 'PROPOSED');
```

논리 구조가 §1-1과 정확히 같다. **"활성일 때만 사용자당 1행"**이라는 조건부 유일성이고,
`WHERE` 절 없는 전체 유니크로는 표현할 수 없다 — 그러면 한 번 매칭한 사용자가
영원히 재요청을 못 한다. 취소·완료 이력은 얼마든지 쌓이고, 활성만 하나여야 한다.

같은 기법이 세 곳에 더 쓰인다.

| 위치 | 무엇 |
|---|---|
| `V1__init_schema.sql:240-241` | `friend_requests_one_pending_idx` — 같은 방향 PENDING 요청은 하나만 |
| `V1__init_schema.sql:102-103` | `match_proposals_pending_expiry_idx` — PENDING만 만료 스캔 대상 (제약이 아니라 인덱스) |
| `V2__party_play_state.sql:20-21` | `parties_ready_at_idx` / `parties_played_at_idx` — 전이 대상만 훑는다 |

### 1-3. 그 밖에 DB가 직접 강제하는 불변식

`V1`/`V2`에서 **주석에 불변식 번호가 명시된** 제약만 모으면 이렇다.

| 불변식 | 강제 수단 | 위치 |
|---|---|---|
| INV-1 | partial unique index | `V1:120-122` |
| INV-3 | `CHECK (target_party_size > 1)` | `V1:76-77` |
| INV-3 | `CHECK (target_size > 1)` | `V1:201` |
| INV-4 | `UNIQUE (proposal_id)` on `parties` — proposal 하나에서 party 하나만 | `V1:197-198` |
| INV-4 / INV-5 | `CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))` | `V1:97-99` |
| INV-7 | `PRIMARY KEY (proposal_id, user_id)` | `V1:170-171` |
| INV-7 | `PRIMARY KEY (party_id, user_id)` | `V1:213-214` |
| INV-9 | exclusion constraint | `V1:145-150` |

INV-4/INV-5의 `CHECK ((status='CONFIRMED') = (confirmed_at IS NOT NULL))`는
등가(`=`) 형태라 **양방향**을 한 줄로 잡는다 — CONFIRMED인데 시각이 비었거나,
CONFIRMED가 아닌데 시각이 찍혀 있으면 둘 다 거부된다.
"확정 상태와 확정 시각이 절대 어긋나지 않는다"는 걸 앱 코드 리뷰가 아니라 DB가 보증한다.

**한계도 스키마가 직접 적어 두었다** — `V1__init_schema.sql:176-178`:

```sql
-- INV-2는 Redis atomic claim + service layer가 강제한다.
-- proposal status가 다른 테이블에 있어 partial unique index로는 표현할 수 없다.
CREATE INDEX proposal_members_user_idx ON proposal_members (user_id);
```

INV-2("한 사용자는 동시에 하나의 활성 proposal에만 속한다")는 상태가
`match_proposals`에 있고 멤버십은 `proposal_members`에 있어, partial index의
`WHERE` 절에 다른 테이블 컬럼을 쓸 수 없다. **DB가 모든 걸 하지는 못한다.**
그래서 §5의 역할 분담이 필요해진다.

---

## 2. transactional outbox에 단일 트랜잭션이 필요하다

`docs/14` §19의 결론 표는 확정 지점을 이렇게 못박는다
(`matching/docs/14_ARCHITECTURE_RATIONALE.md:211`, `docs/11` #27도 동일 —
`matching/docs/11_DECISION_LOG.md:231`):

> | 확정된 것 | DB — `match_proposals` + `proposal_members` + `outbox`, **단일 트랜잭션** |

그리고 §19가 밝히는 이유 (`matching/docs/14_ARCHITECTURE_RATIONALE.md:215`):

> 원자성이 정말 필요한 지점은 **확정 하나뿐**이고, ②에서도 그 하나는 여전히 단일 DB 트랜잭션이다.

outbox 테이블의 구조와 발행 경로는 queueMate `feature/frontend:docs/06_DATA_MODEL.md:210-235`:

```text
matching.outbox   → ProposalConfirmed.fifo   (MessageGroupId = proposalId)
party.outbox      → PartyClosed.fifo         (MessageGroupId = partyId)
social.outbox     → BlockChanged.fifo        (MessageGroupId = 정규화 차단 쌍)
```

> 상태 변경과 같은 트랜잭션에 기록하고, relay가 SQS FIFO로 발행한다. (`docs/06_DATA_MODEL.md:223`)

**이 패턴이 왜 트랜잭션 없이는 성립하지 않는가.**

transactional outbox의 존재 이유는 오직 하나다 —
**"상태를 바꿨는데 이벤트가 안 나갔다" 또는 "이벤트는 나갔는데 상태가 안 바뀌었다"를 불가능하게 만드는 것.**
그걸 성립시키는 유일한 수단이 **상태 변경과 이벤트 기록이 같은 트랜잭션에 들어가는 것**이다.

```
BEGIN;
  INSERT INTO match_proposals (...) ;        -- 확정 상태
  INSERT INTO proposal_members (...) x N ;   -- 참가자들
  INSERT INTO outbox (...) ;                 -- ProposalConfirmed 이벤트
COMMIT;                                       -- 셋이 함께 남거나, 셋 다 없다
-- 이후: relay가 outbox를 읽어 SQS FIFO로 발행 (at-least-once)
```

트랜잭션이 없는 저장소라면 이 세 쓰기가 **부분 적용**될 수 있다.
그러면 "확정됐는데 아무도 모르는 매칭"이나 "존재하지 않는 매칭의 확정 알림"이 생긴다.
이걸 대신 막으려면 결국 **분산 트랜잭션이나 사가**를 도입해야 하는데,
그건 지금 이 시스템이 감당할 복잡도가 아니다 — 애초에 브로커조차 관리형으로 제한했다
(`CLAUDE.md` §3: Kafka/RabbitMQ 추가 금지, SQS만 예외).

동일 트랜잭션 안에서 읽을 수 있다는 성질은 INV-6 검증에서도 그대로 쓰인다.
`docs/11` #19 (`matching/docs/11_DECISION_LOG.md:101-103`):

> REST 재검증은 검증과 claim 사이에 창이 다시 생겨 문제를 풀지 못하고,
> **뷰는 atomic claim과 같은 트랜잭션 안에서 읽을 수 있다.**

§1-1의 "조회와 삽입 사이의 창"과 정확히 같은 논리다.
**트랜잭션은 그 창을 없애는 도구**이고, 그래서 두 곳 모두에서 관계형이 답이 된다.

---

## 3. 스키마별 롤로 경계를 런타임이 막는다

`docs/14` §3 "DB를 몇 개로 할까" — queueMate `feature/frontend:docs/14_ARCHITECTURE_RATIONALE.md:43-57`:

> **후보** ① 서비스마다 DB 인스턴스(database-per-service) ② 인스턴스 1개에 스키마 분리
>
> **선택: ②**
>
> - 스키마 7개 + **스키마별 DB 롤**로 권한을 격리한다.
>   크로스 스키마 접근이 코드 리뷰가 아니라 **런타임 permission denied**로 막힌다.
> - 그래서 나중에 진짜로 DB를 쪼갤 때 "몰래 자란 JOIN"이 없다.

**이게 관계형 DB를 고른 이유가 되는 지점은 여기다.**
"경계를 지켜라"는 규율을 **문서나 리뷰가 아니라 저장소 자신이 집행**한다.
`matching` 롤로 접속한 앱이 `social.blocks`를 SELECT하면 배포 전에 실패한다.
스키마·롤·GRANT라는 개념이 없는 저장소에서는 이 강제력을 살 수 없다.

배치는 `feature/frontend:docs/06_DATA_MODEL.md:11-18`:

```text
account      users, credentials/refresh_tokens, game_accounts
gameconfig   game_mode_configs
matching     match_proposals, proposal_members, outbox
reservation  reservations
party        parties, party_members, outbox
social       friend_requests, friendships, blocks, reports, recent_players, outbox
shared_read  blocked_pairs (view)
```

> **위 배치 중 `shared_read`는 이 저장소에서 폐기했다** (docs/11 D-1).
> 스키마 6개로 가고, `matching` 롤에 `social.blocks`의 SELECT만 예외로 준다. §3-1 참고.

### 3-1. `social.blocks` — 유일한 승인 예외

**이 절은 이 저장소에서 원안을 바꾼 지점이다** (docs/11 D-1).

원안은 `social`이 소유하는 읽기 전용 뷰 `shared_read.blocked_pairs`를 두고,
`matching` 롤에 그 뷰의 SELECT만 주는 것이었다
(`feature/frontend:docs/06_DATA_MODEL.md:28-52`).

```sql
-- 폐기된 원안
CREATE SCHEMA shared_read;
CREATE VIEW shared_read.blocked_pairs AS
SELECT LEAST(blocker_id, blocked_id)    AS user_low_id,
       GREATEST(blocker_id, blocked_id) AS user_high_id
FROM social.blocks;
```

**현재는 `social.blocks`를 직접 읽는다.**

```sql
GRANT USAGE  ON SCHEMA social         TO qm_matching;
GRANT SELECT ON social.blocks         TO qm_matching;
```

바꾼 이유는 세 가지다.

1. **층이 하나 더 생기는 값이 크지 않다.** 뷰가 하는 일은 방향 정규화 하나뿐인데,
   조회를 양방향으로 쓰면 그 정규화가 애초에 필요 없다.
2. **정규화가 오히려 함정을 만든다.** 뷰의 `LEAST`/`GREATEST`는 PostgreSQL의 uuid
   바이트 순 비교를 따르는데, Java `UUID.compareTo`는 부호 있는 long 비교라 결과가
   다르다 (`V1__init_schema.sql:247-248`의 경고). 호출부가 정규화를 흉내 내면 조용히
   틀린다. 방향을 그대로 두고 양쪽을 다 보는 편이 틀릴 여지가 없다.
3. **스키마 하나와 뷰 하나를 유지할 비용이 남는다.** 마이그레이션·권한·문서가 늘어난다.

**바뀌지 않은 것.** 스키마 분리와 크로스 스키마 금지는 그대로다. 예외는 이 테이블
하나뿐이고 늘리지 않는다. `matching` 롤은 `social`의 나머지 테이블
(`friendships`, `reports`, `friend_requests` …)을 여전히 읽지 못한다.

**대가로 받아들인 것.** `social`의 내부 테이블 구조에 결합된다. `blocks`의 컬럼이
바뀌면 이 저장소도 같이 고쳐야 한다. 뷰가 제공하던 "database view as API"와
훗날 DB를 물리 분리할 때의 추출 지점 표시도 없어진다. 그 시점이 오면 그때 뷰를
다시 세우는 편이, 오지 않을 수도 있는 시점을 위해 지금 층을 유지하는 것보다 낫다고 봤다.

**INV-6 검증은 한 겹이다.**

| 층 | 무엇 | 성질 |
|---|---|---|
| 1 | 파티 확정 직전 `social.blocks` 동기 SELECT | 결정적. 확정 트랜잭션 안에서 읽는다 |
| (보류) | Redis read model (`qm:block:{userId}`) 선필터 | 정확성이 아니라 **반복 충돌을 줄이는 최적화**. 필요해지면 붙인다 |

원안의 2단계 중 Redis 선필터를 뺀 이유는, 그것이 정확성을 책임지지 않기 때문이다.
차단된 두 사람이 같은 후보 풀에서 반복해서 부딪히는 일이 실제로 관측되면 그때 얹는다.

**주의:** `social.blocks` 자체는 마이그레이션에 있지만(`V1:259-265`), 스키마 분리도 롤도
GRANT도 아직 없다 (§0-3). 이 저장소의 `block/Block.java`는 `social` 스키마를 전제하므로
Flyway가 들어오기 전까지는 부르면 실패한다.

---

## 4. 데이터가 실제로 관계형이다

"관계형 DB를 쓴다"가 정당하려면 데이터가 실제로 관계형이어야 한다. 확인한 결과:

| 관계 | 테이블 | 형태 | 위치 |
|---|---|---|---|
| 사용자 ↔ 게임계정 | `game_accounts` | 1:N, 복합 UNIQUE | `V1:41-53` |
| 사용자 ↔ 사용자 (친구) | `friendships` | **N:M 자기참조**, `(low, high)` 정규화 | `V1:249-255` |
| 사용자 ↔ 사용자 (차단) | `blocks` | **N:M 자기참조**, 방향 있음 | `V1:259-265` |
| 사용자 ↔ 사용자 (친구요청) | `friend_requests` | N:M + 상태 | `V1:225-241` |
| proposal ↔ 사용자 | `proposal_members` | **N:M 조인 테이블** | `V1:163-174` |
| party ↔ 사용자 | `party_members` | **N:M 조인 테이블** | `V1:207-216` |
| proposal ↔ party | `parties.proposal_id` | 1:1 (UNIQUE로 강제) | `V1:189`, `:197-198` |

**핵심은 조인 테이블이 세 개(`proposal_members`, `party_members`, `friendships`)라는 점이다.**
다대다 관계에 상태(`ready`, `acceptance`, `joined_at`/`left_at`)까지 얹혀 있다.
문서 저장소에 이걸 넣으면 어느 쪽에 embed할지부터 결정이 안 되고,
어느 쪽을 골라도 반대 방향 조회가 스캔이 된다.

### 4-1. `ON DELETE CASCADE` — 삭제 정합성

`V1__init_schema.sql`에서 `ON DELETE CASCADE`가 걸린 곳:

| 위치 | 참조 |
|---|---|
| `:43` | `game_accounts.user_id → users(id)` |
| `:107` | `match_requests.user_id → users(id)` |
| `:131` | `reservations.user_id → users(id)` |
| `:164` | `proposal_members.proposal_id → match_proposals(id)` |
| `:165` | `proposal_members.user_id → users(id)` |
| `:208` | `party_members.party_id → parties(id)` |
| `:209` | `party_members.user_id → users(id)` |
| `:227`, `:228` | `friend_requests.sender_id`/`receiver_id → users(id)` |
| `:250`, `:251` | `friendships`의 두 user 컬럼 |
| `:260`, `:261` | `blocks.blocker_id`/`blocked_id` |
| `:273`, `:274` | `reports.reporter_id`/`target_user_id` |

사용자 하나를 지우면 게임계정·요청·예약·멤버십·친구관계·차단·신고가 **DB 안에서 함께 사라진다.**
앱이 12개 테이블의 삭제 순서를 외울 필요가 없고, 새 테이블을 추가한 사람이
삭제 코드를 고치는 걸 잊어도 고아 행이 생기지 않는다.

세 가지 다른 동작이 의도적으로 구분돼 있는 것도 봐야 한다.

| 동작 | 위치 | 의미 |
|---|---|---|
| `ON DELETE CASCADE` | 위 표 | 소유 관계. 부모가 사라지면 자식도 의미가 없다 |
| `ON DELETE SET NULL` | `:112`, `:138`, `:275` | 참조는 끊되 행은 남긴다 (`reports.party_id` 등) |
| `ON DELETE RESTRICT` | `:189` | `parties.proposal_id` — **확정된 매칭의 근거는 지울 수 없다** |

`RESTRICT`가 단 한 곳에만 쓰였다는 게 의미심장하다.
파티가 존재하는 한 그 파티를 만든 proposal은 삭제 불가다 — 확정 이력의 보호다.

### 4-2. 표현식 제약 — 앱이 못 하는 것

관계형 제약이 잡아 주는 "이상한 데이터"들:

```sql
CONSTRAINT friendships_normalized_check CHECK (user_low_id < user_high_id)   -- V1:254
CONSTRAINT blocks_not_self_check        CHECK (blocker_id <> blocked_id)     -- V1:264
CONSTRAINT friend_requests_not_self_check CHECK (sender_id <> receiver_id)   -- V1:234
CONSTRAINT reports_not_self_check       CHECK (reporter_id <> target_user_id)-- V1:279
CONSTRAINT reservations_window_check    CHECK (available_from < available_to) -- V1:144
CONSTRAINT party_members_left_at_check  CHECK (left_at IS NULL OR left_at >= joined_at) -- V1:215
CONSTRAINT users_nickname_length_check  CHECK (char_length(nickname) BETWEEN 2 AND 16)  -- V1:34
```

`friendships_normalized_check`에는 스키마가 직접 남긴 함정 경고가 붙어 있다
(`V1__init_schema.sql:247-248`):

> 주의: 여기서의 순서는 PostgreSQL의 uuid 비교(바이트 순)다. Java `UUID.compareTo`는
> long 두 개를 부호 있는 값으로 비교해 결과가 다르므로 정규화에 그대로 쓰면 안 된다.

**이건 "DB에 제약이 있어서 잡힌" 종류의 버그다.**
앱에서만 정규화했다면 Java 쪽 순서로 저장돼도 아무도 몰랐을 것이고,
나중에 `(low, high)` 조회가 조용히 절반을 놓쳤을 것이다.

### 4-3. enum 대신 varchar + CHECK

`V1__init_schema.sql:3-4`가 이유를 적어 두었다:

> enum은 PostgreSQL enum type 대신 varchar + CHECK로 둔다.
> JPA `EnumType.STRING`과 그대로 맞고, 값 추가 시 type 변경 없이 migration 하나로 끝난다.

관계형의 기능을 무비판적으로 다 쓴 게 아니라, **마이그레이션 비용을 보고 골라 썼다**는 증거다.

---

## 5. Redis와의 역할 분담 — "DB는 성사된 매칭만 안다"

`docs/14` §19 (`matching/docs/14_ARCHITECTURE_RATIONALE.md:204`):

> **한 줄 요약: DB는 "성사된 매칭"만 안다.** 시도했다 실패한 요청은 DB를 치지 않는다.

`matching/docs/14_ARCHITECTURE_RATIONALE.md:206-211` / `docs/11` #27 (`:225-231`):

| 무엇 | 어디 |
|---|---|
| 게임 모드 설정 | Redis 캐시 (DB가 원본) |
| 매칭 요청 | **Redis만** — `match_requests` 테이블을 만들지 않는다 |
| 진행 중 proposal | Redis (`qm:proposal:{id}` HASH + TTL) |
| 수락 집계 | Redis HASH + Lua |
| 확정된 매칭 | **DB** — `match_proposals` + `proposal_members` + `outbox`, 단일 트랜잭션 |

이유 (`matching/docs/14_ARCHITECTURE_RATIONALE.md:214-217`):

- 대기 중 요청은 몇 초~몇 분 살다 사라지는 상태다. 영속 기록으로서의 가치가 낮은데
  DB에도 두면 **두 저장소가 어긋날 지점만 늘어난다.**
- 원자성이 정말 필요한 지점은 **확정 하나뿐**이다.
- INV-10이 이미 "Redis 장애 = 새 매칭 fail-closed"다. Redis가 죽었을 때
  DB로 매칭을 이어갈 계획이 애초에 없다면, DB에 요청을 복제해 둘 이유도 없다.

**이 분담이 §1~§4를 오히려 강화한다.** DB에 남는 게 "확정된 것"뿐이라서
DB에 요구되는 성질이 **처리량이 아니라 정확성**으로 좁혀진다.
초당 수만 건을 받는 저장소였다면 제약·트랜잭션·FK의 비용을 다시 따져야 했을 텐데,
파티 확정은 초당 수 회다 (`docs/11` #19: *"파티 확정은 초당 수 회라 동기 읽기 비용도 무시할 만하다"*,
`matching/docs/11_DECISION_LOG.md:103`).

**감수하는 것도 §19가 명시한다** (`matching/docs/14_ARCHITECTURE_RATIONALE.md:222-227`):

- Redis 장애 시 큐를 DB로부터 **재구축할 수 없다.** 복구 후 사용자가 다시 요청한다.
- 매칭 "시도" 지표(요청 수, 대기 시간 분포, 실패율)는 **DB 집계가 불가능**하다. Prometheus로만 본다.
- 확정 구간이 두 저장소에 걸친다. **DB 먼저 쓰고 Redis를 정리하는 순서**로 완화한다.

---

## 6. matching 관점 — 같은 불변식, 다른 층

이 저장소가 DB를 안 쓰는데도 이 문서가 여기 있는 이유가 이 절이다.

**INV-1을 DB는 partial unique index로, matching은 Redis Lua로 푼다. 논리 구조가 같다.**

`backend/src/main/resources/redis/shared/claim-request.lua:1-30` — 파일 자신이 그 논리를 적어 두었다:

```lua
-- 사용자의 활성 매칭 요청 자리를 원자적으로 선점한다.
--
-- "이미 대기 중인가?"를 확인하고 등록하는 두 동작 사이에 다른 요청이 끼어들면
-- 한 사용자가 활성 요청을 둘 가질 수 있다 (INV-1 위반).
-- Lua 스크립트는 통째로 하나의 원자 단위로 실행되므로 그 틈이 없다.

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('HSET', KEYS[1], unpack(ARGV))

-- …(6-14행 KEYS/ARGV/반환 설명, 21-27행 만료를 거는 이유 주석은 생략)…
redis.call('EXPIRE', KEYS[1], 60)

return 1
```

**"확인과 등록 사이의 틈"** — §1-1에서 exclusion constraint를 설명하며 쓴 말과 같다.
두 층이 같은 문제를 각자의 도구로 푼다.

### 6-1. 대비표

| | **DB — partial unique index** | **Redis — Lua 스크립트** |
|---|---|---|
| 불변식 | INV-1 | INV-1 |
| 구현 | `V1__init_schema.sql:120-122` | `redis/shared/claim-request.lua:15-28` |
| 호출부 | (미구현) | `MatchRequestService#join()` / 스크립트 등록 `RedisConfig#claimRequestScript()` |
| 원자성의 출처 | **인덱스 유일성 + 트랜잭션 격리** | **Lua 실행이 단일 스레드에서 통째로 도는 것** |
| "조회 후 삽입" 창 | 조회 자체를 없앤다. INSERT 하나만 남는다 | 조회와 저장이 한 스크립트 안이라 사이에 끼어들 수 없다 |
| "활성"의 정의 | `WHERE status IN ('QUEUED','PROPOSED')` — **상태 컬럼으로** | 키 존재 여부 — **`qm:user:active-request:{userId}`가 있으면 활성** |
| 해제 방법 | `UPDATE`로 status를 활성 밖으로 옮긴다 (행은 남는다) | `DEL userKey` (`leave-party.lua` 의 세 갈래 — 배정 전 취소 / 파티가 비어 삭제 / 파티가 남음) — **행 자체가 사라진다** |
| 위반 시 | `SQLIntegrityConstraintViolation` → 앱이 409로 번역 | 스크립트가 `0` 반환 → `MatchRequestService#join()`이 `Optional.empty()` → 409 |
| 이력 | **남는다.** 취소·완료 요청이 행으로 축적된다 | **안 남는다.** 취소하면 흔적이 없다 |
| 장애 시 | DB가 죽으면 확정이 안 된다 | Redis가 죽으면 **fail-closed** (INV-10, `GlobalExceptionHandler#handleRedisFailure()`) |
| 검증 | (미구현이라 테스트 없음) | `ActiveRequestConcurrencyTest.onlyOneRequestSucceedsPerUser` |

### 6-2. "틈이 있으면 실제로 깨진다"를 이 저장소가 이미 증명했다

§1-1에서 "조회 후 삽입은 race가 생긴다"고 주장했다.
**그건 이 저장소에서 실측으로 확인된 사실이다.**

`backend/src/test/java/com/queuemate/matching/concurrency/NaiveVsLuaComparisonTest.java:13-19`:

```java
/**
 * "왜 Lua여야 하는가"를 숫자로 남긴다.
 *
 * 같은 부하를 두 방식에 그대로 걸어 결과를 비교한다.
 *   순진한 방식 — 자바에서 EXISTS 확인 후 HSET. 명령 두 개 사이에 틈이 있다.
 *   Lua        — 확인과 저장이 한 원자 실행 안이라 틈이 없다.
 */
```

측정 결과 (`docs/CONCURRENCY_TESTS.md:66-80`).
조건: 같은 `userId`로 **100 스레드 동시 요청 × 5 라운드, 인위적 지연 없음**:

| | 순진한 방식 (자바에서 `EXISTS` 후 `HSET`) | Lua |
|---|---|---|
| round 1~5 각 통과 | 100건 (중복 99) | 1건 (중복 0) |
| **누적 중복** | **495건** | **0건** |

**인위적 지연 없이도 100건 중 99건이 통과한다.** "이론상 가능한 race"가 아니라
평범한 부하에서 거의 항상 깨진다는 뜻이다.
`naiveApproachBreaksUnderConcurrency`는 `totalDuplicates > 0`을 **단언**하고
(`NaiveVsLuaComparisonTest.java:67-70`), 실패하면 코드가 좋아진 게 아니라
비교의 전제가 무너진 것으로 읽는다 (`docs/CONCURRENCY_TESTS.md:31-33`).

**이 숫자를 DB 쪽으로 그대로 옮겨 읽으면 §1의 주장이 된다.**
INV-9 검사를 앱에서 "조회 후 삽입"으로 짜면 같은 일이 벌어진다.
exclusion constraint와 Lua는 **같은 처방을 서로 다른 층에서 쓴 것**이다 — 창을 없앤다.

### 6-3. matching이 Redis Lua로 강제하는 불변식 전체

`docs/CONCURRENCY_TESTS.md`와 `CLAUDE.md` §4에 근거한 목록. **2026-09-15 기준 커밋된 스크립트는 8개다**
(`shared/` 1 · `lol/` 5 · `proposal/` 2. 티어를 보는 판이 늘고, 차단 검증을 끼우려고 배정이
"찾기"와 "합류"로 쪼개졌고, 제안 수락·거절이 붙었다. 작업 트리의 `redis/pubg/` 는 작성 중이라 세지 않는다).
줄 번호는 자주 어긋나므로 파일 이름과 명령으로 적는다.

| 불변식 | 스크립트 | 강제 방식 | 테스트 |
|---|---|---|---|
| INV-1 | `claim-request.lua` | `EXISTS` 확인과 `HSET`이 한 원자 단위. 뒤에 `EXPIRE 60` | `ActiveRequestConcurrencyTest.onlyOneRequestSucceedsPerUser` / `.differentUsersAllSucceed` |
| INV-1 (대조군) | — | 순진한 구현이면 실제로 깨진다 | `NaiveVsLuaComparisonTest.naiveApproachBreaksUnderConcurrency` |
| INV-3 | `join-party.lua` / `join-party-tiered.lua` | 참가자를 `HSET member:{userId}` 한 뒤 **`member:` 필드를 세어** `size >= target`이면 모든 needs 색인에서 제거한다. 인원 카운터는 두지 않는다 — Lua 는 롤백이 없어 페일오버 뒤 재시도가 `HINCRBY`를 두 번 더하면 실제 멤버 수와 어긋나지만 `HSET` + 세기는 몇 번 해도 같다. **단 세는 자리에 target 확인 분기가 없어, 초과를 막는 것은 ① 후보가 needs 색인(=아직 안 찬 파티)에서만 나온다는 것과 ② 후보 선택부터 합류까지가 `redisLock/PoolLock.java` 의 후보 풀 락 안에 있다는 것, 두 겹이다** | `PartyJoinConcurrencyTest.partyNeverExceedsTarget` |
| INV-3 / INV-8 | 〃 | `uniqueness=true`면 내 keyValue 색인에서 파티를 `ZREM` (티어판은 그 줄의 티어 전부) | `PartyJoinConcurrencyTest.positionIsUniqueWithinParty` |
| INV-7 | `create-or-check-party-untiered.lua` / `-tiered` / `join-party.lua` / `join-party-tiered.lua` | 활성 요청 HASH에 `partyId`를 기록하고, 참가자를 `member:{userId}` **필드**로 써서 한 사용자가 한 번만 들어가게 한다 | `PartyJoinConcurrencyTest.userBelongsToOnlyOneParty` |
| INV-4 | `accept-proposal.lua` | 수락자 SET `qm:proposal:accepts:{partyId}`에 `SADD` → `SCARD`를 파티 HASH의 `target`과 비교해 `count >= target`일 때만 `HSET status CONFIRMED`. 세기와 확정이 한 스크립트라 마지막 두 명이 둘 다 "내가 마지막"이 될 수 없다. `target`을 못 읽으면 확정하지 않는다(fail-closed) | `ProposalIdempotencyTest` (단일 스레드 멱등성) |
| INV-5 (declined·confirmed) | `decline-proposal.lua` / `accept-proposal.lua` / `join-party*.lua` | 거절은 `status`·`expiresAt`을 `HDEL`하고 수락자 SET을 `DEL` → 거절된 제안의 수락은 `NOT_FOUND`. 두 스크립트 모두 `status == CONFIRMED`면 쓰기 전에 돌려준다. `join-party*.lua`는 `HSETNX status PENDING`이라 재실행이 확정을 되돌리지 않는다. **expired·cancelled 갈래는 막혀 있지 않다** (§6-4) | 〃 |
| (취소 정합성) | `leave-party.lua` | 빼기(`HDEL member:{userId}` — 인원을 세어 구하므로 이것이 곧 인원 감소)·색인복원·빈파티삭제·요청삭제가 한 덩어리. 삭제는 `requestId` compare-and-delete | 동시성 테스트 없음. 취소 경로는 `PushNotificationTest.cancelNotifiesOnlyRemainingMembers` 가 단일 흐름으로 밟는다 |

> **이 절이 "Lua가 전부다"라고 읽히면 안 된다.** 배정이 두 스크립트로 쪼개지면서
> 그 사이 구간은 Lua가 아니라 Redisson 분산 락(`redisLock/PoolLock.java`)이 막는다.
> 락은 저장소가 강제하지 않으므로 — Redis에는 "이 키는 잠겨 있다"는 개념이 없다 —
> **파티 데이터를 만지는 모든 코드가 먼저 락을 잡는다는 약속**에 기대고 있다.
> 그 점에서 DB의 제약(constraint)이 주는 보증과는 여전히 성격이 다르다.

`leave-party.lua:3-6`이 왜 한 덩어리여야 하는지를 직접 적어 두었다:

> 빼기 / 인원 감소 / 색인 되돌리기 / 빈 파티 삭제 / 활성 요청 삭제가 한 덩어리여야 한다.
> 자바에서 나눠 하다 중간에 죽으면 이런 게 남는다.
> · 파티에선 빠졌는데 색인에 안 돌아감 → 그 자리에 아무도 못 들어온다
> · 활성 요청은 지웠는데 파티에 member: 필드가 남음 → 유령 인원이 자리를 먹는다

**이건 DB의 트랜잭션이 공짜로 주는 성질(§2)을 Redis에서 손으로 만든 것이다.**
`leave-party.lua:12-13`(코드는 `:87-89`)의 compare-and-delete(`HGET userKey 'requestId' ~= requestId`면 `-1`)는
관계형이라면 낙관적 락 버전 컬럼이 했을 일이다.

### 6-4. 두 층이 같은 방향을 보고 있다는 뜻

| | Redis 층 (지금 구현됨) | DB 층 (확정 시 필요) |
|---|---|---|
| 관장 범위 | 진행 중 — 요청, 파티 채우기, 수락 집계 | 확정된 것 — proposal, party, outbox |
| 원자성 도구 | Lua 스크립트 | 트랜잭션 + 제약 |
| 다루는 불변식 | INV-1, INV-3, INV-4, INV-5, INV-7, INV-8 | INV-1(이력), INV-3, INV-4, INV-5, INV-7, INV-9 |
| 이 저장소의 구현 | **있다.** INV-4 는 구현·테스트됨, INV-5 는 네 갈래(declined·confirmed·expired·cancelled)가 전부 막혔다 (`CLAUDE.md` §4, 2026-09-16) | **없다** (`docs/11_DECISION_LOG.md:441-442`) |

전에 여기 적혀 있던 두 구멍은 메워졌다. **expired** — `qm:proposal:pending` ZSET 과
`ProposalSweeper` + `proposal/expiry-proposal.lua` 가 시한이 지난 제안을 깬다(다만
`accept-proposal.lua` 는 여전히 `expiresAt` 을 보지 않으므로, 스위퍼가 꺼내기 전에 도착한 수락은
확정된다 — 주기만큼의 창이다). **cancelled** — `leave-party.lua` 가 멤버를 빼기 전에
`status`·`expiresAt`·수락자 SET·pending 을 먼저 지운다.

`matching`이 확정을 **DB 에 반영**하는 순간(outbox 기록 + `ProposalConfirmed.fifo` 발행 —
아직 없다), `accept-proposal.lua`가 `CONFIRMED`를 돌려주는 지점부터 §2의 단일 트랜잭션이
필요해진다. 지금 그 자리에 있는 것은 Redis 안에서 끝나는 뒷정리
(`proposal/cleanup-confirmed.lua`)와 `MATCH_CONFIRMED` 알림뿐이다. **그때 이 문서가 근거로 쓰인다.**

---

## 부록. 인용한 근거 파일

### queueMate 저장소 (읽기 전용)

| 브랜치 | 경로 |
|---|---|
| `feature/frontend-v2` (현재 체크아웃) | `backend/src/main/resources/db/migration/V1__init_schema.sql` (282줄) |
| 〃 | `backend/src/main/resources/db/migration/V2__party_play_state.sql` (21줄) |
| `feature/frontend` | `docs/14_ARCHITECTURE_RATIONALE.md` §3 (`:43-57`) |
| 〃 | `docs/06_DATA_MODEL.md` (`:3-5` 서두, `:7-26` 스키마 배치, `:28-52` 뷰, `:210-235` outbox, `:238-249` 제약) |
| 〃 | `infra/postgres/init.sql` (2줄) |
| 〃 | `CLAUDE.md` §3, §4 |

### 이 저장소 (`matching/`)

| 경로 | 인용 부분 |
|---|---|
| `docs/11_DECISION_LOG.md` | `:25` (#4), `:69-78` (#17), `:97-109` (#19), `:222-247` (#27), `:404-424` 색인표, `:433-445` 미구현 목록 |
| `docs/14_ARCHITECTURE_RATIONALE.md` | `:198-228` (§19). **§3은 이 발췌본에서 빠져 있다** (`:10-11`의 "뺀 절" 목록) |
| `docs/CONCURRENCY_TESTS.md` | `:35-45` 불변식표, `:66-80` 측정 결과 |
| `backend/src/main/resources/redis/shared/claim-request.lua` | 전체 (30줄) |
| `backend/src/main/resources/redis/lol/create-or-check-party-untiered.lua` | `:74` |
| `backend/src/main/resources/redis/proposal/accept-proposal.lua` | `CONFIRMED` 반환 분기 (§6-3, §6-4) |
| `backend/src/main/resources/redis/proposal/decline-proposal.lua` | `HDEL status expiresAt` + `DEL acceptsKey` (§6-3) |
| `backend/src/main/resources/redis/lol/join-party.lua` | `memberCount` 주석, `HSETNX status PENDING` (§6-3) |
| `backend/src/main/resources/redis/lol/leave-party.lua` | `:3-6`, `:12-13`, `:87-89`, `DEL userKey` |
| `backend/src/main/java/com/queuemate/matching/service/MatchRequestService.java` | `#join()` |
| `backend/src/main/java/com/queuemate/matching/config/redis/RedisConfig.java` | `#claimRequestScript()` |
| `backend/src/test/java/.../NaiveVsLuaComparisonTest.java` | `:13-19`, `:41-71`, `:73-98` |
| `backend/build.gradle` | `:20-45` (의존성 목록) |
| `backend/src/main/resources/application.yaml` | `:32-46` (`spring.datasource` · `spring.jpa`) |

### 확인하지 못한 것

| 항목 | 상태 |
|---|---|
| **"왜 관계형인가"의 원래 논증** | **없다.** `docs/11` #4는 선언 한 줄이고, 기각한 대안(MongoDB 등)의 기록이 어디에도 없다. 다른 저장소·과거 커밋을 다 뒤진 것은 아니므로 "존재하지 않는다"가 아니라 **"인용한 문서 범위에서 확인 못 함"**이다 |
| `docs/06_DATA_MODEL.md`의 matching 로컬 사본 | **없다.** `matching/docs/`에 06번 문서가 없어 queueMate `feature/frontend`에서 `git show`로 꺼내 인용했다 |
| 스키마 분리 / 롤 / GRANT의 실제 적용 | 어느 브랜치의 마이그레이션에도 없다 (§0-3). **문서상 설계이며 실행 여부 확인 못 함.** `shared_read` 뷰는 아예 만들지 않기로 했다 (docs/11 D-1) |
| `outbox` 테이블 DDL | 마이그레이션에 없다. `docs/06:210-220`의 컬럼 목록만 있다 |
| `match_proposals.condition_snapshot_json` | #27이 신설을 지시했으나 V1/V2 어디에도 없다 |
| `feature/frontend` 브랜치의 백엔드 마이그레이션 | 해당 브랜치에는 `.sql`이 `infra/postgres/init.sql` 하나뿐이다. 스키마 DDL은 `feature/frontend-v2`에만 있다 |
| DB 성능·용량 실측 | 이 문서의 논거는 전부 정확성이다. **처리량 근거는 인용하지 않았고 측정치도 없다** |
| `RESTRICT`/`SET NULL` 선택의 명시적 근거 | 스키마에 주석이 없다. §4-1의 해석은 **DDL을 보고 추론한 것**이다 |
