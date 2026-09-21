# START_HERE — platform 을 처음 여는 세션과 사람을 위한 안내

> **이 파일은 일이 진행되면 갱신한다.** 단계가 끝나면 §1 "지금 어디까지 됐나"와 §3 의 표를 고쳐라. 정해진 미정 사항은 §3 · §4 에서 지우고
> `CLAUDE.md` §7 에서도 뺀다(해당 절로 옮긴다).

**먼저 읽을 것 — 이 순서로.**

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **이 파일** | 이 서비스가 어디까지 됐고, 무엇을 어떤 순서로 만들며, 단계마다 무엇을 **먼저 물어야** 하는지 |
| 2 | `CLAUDE.md` | **규칙.** 하는 일 / 안 하는 일(§2), 계약(§3), 설계 규칙(§5), 미정 사항(§7 · §7.1 · §7.2), 일하는 방식과 운영 규칙(§9) |
| 3 | `README.md` | 이 서비스가 하는 일의 짧은 소개와 "만드는 순서"의 원본 |
| 4 | `docs/ROOM_CONTRACT.md` | 이 앱이 읽는 `room` 의 Redis 키와, `room` 계약 가운데 이 앱에 걸리는 부분. **3단계에 들어가기 전에** 읽는다 |
| 필요할 때 | 옆 폴더의 문서 (§5 문서 지도) | 전체 그림 · 결정의 이유 · 알림 봉투 · 로컬 환경 함정 |

> **출처 표기.** `docs/11 D-…` · `contracts/events.md` · `HANDOFF.md` 처럼 폴더 이름 없이 적은 것은 옆 폴더 `matching` 기준이다(`CLAUDE.md` 머리와 같다).
> **출처가 안 붙은 사실은 정해지지 않은 것이다.**

---

## 1. 지금 어디까지 됐나 (2026-09-21)

**platform 은 0 이다 — 빈 뼈대만 있다.** 기능이 하나도 없다.

| 순서 | 만들 것 | `README.md` 의 단계 | 상태 |
|---|---|---|---|
| 0 | 뼈대 — Spring Boot 4.1.1 · Java 21 · PostgreSQL + Flyway · Redis · 헬스 엔드포인트 | (1단계의 "기반" 가운데 일부) | ✅ 2026-09-21 |
| 1 | 최소한의 계정 — 가입 · 로그인 · JWT 발급. 스키마 `account` | 1 | ⬜ **다음 할 일.** 시작하기 전에 §4 를 묻는다 |
| 2 | 차단 — `social.blocks` | 2 | ⬜ |
| 3 | 모집 글 · 목록(방 안 사람 카드 · 차단 거르기) · 입장권 발급 · 게시판 채널 신호 발행 | 3 | ⬜ (`room` 몫은 입장권 검증을 빼고 돼 있다) |
| 4 | (이 앱의 몫이 없다 — 시그널 `POST` 는 `room`, 음성은 프런트) | 4 | — (`room` 의 시그널 `POST` 는 돼 있다) |
| 5 | 방장 확정의 기록 — 글을 "확정"으로 · 파티원 기록 | 5 | ⬜ (`room` 의 확정 표시는 돼 있다) |
| 6 | 자동 매칭 파티 — `ProposalConfirmed.fifo` 소비(SQS · outbox) | 6 | ⬜ (`matching` 쪽 발행도 없다) |
| 7 | 친구 · 신고 · 최근 함께한 사람(`PartyClosed.fifo`) | 7 | ⬜ |

- **뼈대가 돈다(2026-09-21 에 실행해서 확인했다).** `backend/` 에서 `./gradlew build` 가 통과하고(테스트 3건 — 앱이 뜬다 · Flyway 가 PostgreSQL 에 돌았다 · Redis 가 답한다),
  `bootRun` 으로 띄워 `/health/live` · `/health/ready` 가 200 `{"status":"UP"}` 인 것, Flyway 가 주석뿐인 `V1__baseline.sql` 을 적용해 기록 테이블에 한 줄이 생긴 것,
  SIGTERM 에 graceful shutdown 로그를 남기고 내려가는 것을 봤다. 테스트용 PostgreSQL 은 5433, Redis 는 6380 이었다(§6).
- **뼈대의 기준은 Spring Initializr 다.** `start.spring.io` 에 Boot 4.1.1 · Java 21 · Gradle 로 `web, validation, data-jpa, postgresql, flyway, data-redis, actuator, lombok` 을 넣어 받은 것과
  `build.gradle`(주석을 뺀 본문) · `PlatformApplication` · wrapper(Gradle 9.7.1 — `room/backend` 와 같은 파일)가 **글자까지 같다.** 다르게 한 곳 —
  `application.properties` 대신 `application.yaml`(이 프로젝트의 관례), `HELP.md` 를 두지 않았다, `.gitignore` 는 `backend/` 가 아니라 폴더 루트의 것을 쓴다(`room` 과 같다),
  `V1__baseline.sql` · `package-info.java` 를 더했다, 테스트가 5432 · 6379 면 건너뛰고 Flyway · Redis 를 한 번씩 확인한다.
- **넣지 않은 것** — Spring Security · JWT 라이브러리 · AWS SDK(SQS). 인증 방식의 세부와 outbox 가 미정이다(`CLAUDE.md` §7). `build.gradle` 에 넣을 자리만 주석으로 남겼다.
  **H2 도 넣지 않았다** — 스키마별 롤/GRANT 와 제약을 재현하지 못한다(`CLAUDE.md` §5 · docs/11 D-3).
- **만들지 않은 것** — 엔티티 · 컨트롤러 · 서비스 · 테이블 · 스키마. `V1__baseline.sql` 은 실행되는 SQL 이 한 줄도 없다(테이블 컬럼이 미정이다 — `CLAUDE.md` §3.5 · §7).
- **헬스 주소가 `room` 과 다르다.** `CLAUDE.md` §5 의 `/health/live` · `/health/ready` 를 맞추려고 actuator 를 루트(`management.endpoints.web.base-path: /`)에 두고 health 그룹 `live` · `ready` 를 만들었다.
  그래서 이 앱에는 `/actuator/health` 가 **없다**(404 — 확인했다). `/health` · `/health/liveness` · `/health/readiness` 도 200 으로 답한다(`/info` 는 열어 두었지만 불러 보지 않았다). `room` 은 기본값이라 `/actuator/health/liveness` 다.
  (health 그룹의 `additional-path` 로 하려 했으나 한 마디짜리 주소(`/livez` 같은)만 받아 기동이 실패했다 — 실행해서 본 것이다.)

**시스템 전체 — 무엇이 돼 있고, platform 이 무엇을 기다리게 하고 있는가**

| 서비스 | 상태 (2026-09-21) | platform 을 기다리는 것 |
|---|---|---|
| `room` | **방 안의 기능이 전부 됐다.** 요청 아홉 가지(방 만들기 · 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 방 안 사람 목록 · 내 방 찾기 · 시그널) · 알림 · 확정한 방의 방장 넘기기 · 게시판 채널 신호 발행. 테스트 120건 (docs/11 D-21 · D-23 · `../room/START_HERE.md` §1) | **인증과 입장권.** 지금은 요청의 `userId` 를 그대로 믿고 누구나 아무 `roomId` 로 방을 만든다(`TEMP-NO-PLATFORM` — 아래 §2). 글도 목록도 없으니 **방을 찾아 들어갈 화면이 없다** |
| `notification` | 개인 알림(SSE)과 **게시판 채널 구독**(기동 때 `qm:pubsub:board` 하나를 구독해 모든 연결로 전달 — D-22)이 됐다. 인증만 없다(`?userId=`) | access 토큰을 검증하는 법(`CLAUDE.md` §7 "인증 세부" ③ · ⑥ SSE 와 토큰 만료). **이 앱의 게시판 신호 발행**(글이 생기거나 사라지거나 상태가 바뀔 때)은 아직 없다 |
| `matching` | 매칭 엔진은 됐다(세 게임 · 제안 · 수락 · 확정 · 만료 · 상태 조회 · 409 `IN_ROOM`). **outbox · SQS · DB 스키마는 없다**(`HANDOFF.md` §0) | ① `social.blocks` 테이블 — 없어서 기본 실행에서 배정이 조용히 실패한다(`HANDOFF.md` §0-1 ②. INV-6 은 배포 차단 조건이다) ② `ProposalConfirmed.fifo` 를 받아 줄 소비자(③) ③ `status=PARTY` 를 누가 푸는가(① — 검토한 방향은 `CLAUDE.md` §7.2 (라)) ④ 인증 |
| `app:reservation`(Lambda) | 이 컴퓨터에 폴더가 없다 (docs/11 D-15) | `reservation` 스키마의 마이그레이션을 누가 실행하는가(`CLAUDE.md` §7 — 미정. 정해지기 전에 이 앱에 넣지 않는다) |
| 프런트 | 이 컴퓨터에 없다 (queueMate 본 저장소 `feature/frontend`) | 계약. platform 의 엔드포인트는 어느 계약에도 없다(`CLAUDE.md` §3.1) |

## 2. 옆 서비스가 platform 없이 임시로 처리해 둔 것 — `TEMP-NO-PLATFORM`

순서대로라면 platform 의 1~2단계(계정 · 차단)가 먼저였지만 **`room` 을 먼저 만들었다.** 그래서 `room` 은 platform 이 줄 것을 비워 둔 채 돈다.
아래는 `../room/START_HERE.md` §2 를 옮겨 적은 것이다(원본이 바뀌면 그쪽이 우선한다).

| platform 이 있으면 | 없는 지금 (`room`) | platform 이 무엇을 주면 풀리나 |
|---|---|---|
| 사용자는 **쿠키의 access 토큰**에서 꺼낸다 (docs/11 D-14) | 요청이 보낸 **`userId` 쿼리 파라미터를 그대로 믿는다**(`?userId=`) — 누구나 아무 `userId` 로 입장하고 강퇴할 수 있다 | **1단계.** JWT 발급 + 다른 서비스가 검증하는 법(`CLAUDE.md` §7 "인증 세부" ③ · ⑧). `userId` 를 받는 자리를 전부 바꾸는 것은 `room` 폴더의 일이다 |
| 입장할 때 **입장권의 서명을 검증**한다. 방 식별자 · 방장이 누구인지도 입장권이 말해 준다 (D-16) | **입장권 검증 자리가 비어 있고 `roomId` 만 받는다** | **3단계.** 입장권의 형식 · 서명 · 수명 · 담는 정보 · 서명 키를 나눠 갖는 법(`CLAUDE.md` §7.1) |
| 글이 **모집 중인지 · 차단 관계가 아닌지**는 platform 이 확인하고 입장권을 내준다 | **확인하지 않는다.** `room` 은 원래 글의 상태도 차단 관계도 모른다 | **2 · 3단계.** 입장권 검증이 붙으면 저절로 지켜진다 |
| 방장은 **글을 쓴 사람**이다 — 입장권이 "이 방의 방장은 이 사람"이라고 말해 준다 | **"방 만들기"(`POST /api/v1/rooms/{roomId}`)를 부른 사람이 방장이다. 누구나 아무 `roomId` 로 만든다** | **3단계.** 입장권이 정해지면 방 만들기가 방장임을 입장권으로 확인한다. 그 요청이 그대로 남는지 방장의 첫 입장에 합쳐지는지도 그때 다시 정한다(`../room/CLAUDE.md` §7) |

- **그 자리는 코드에 표시돼 있다.** `grep -rn "TEMP-NO-PLATFORM" ../room/backend/src` — 2026-09-21 에 돌려 보니 컨트롤러 셋(`RoomController` · `RoomMemberController` · `RoomSignalController`)에 12줄이다.
  **채우는 것은 `room` 폴더에서 한다** — 여기서 옆 폴더의 파일을 고치지 않는다(`CLAUDE.md` §9).
- `matching` · `notification` 도 같은 방식이다 — 요청의 `userId` 파라미터를 그대로 믿는다(`CLAUDE.md` §7 "인증 세부" ⑧). 그쪽에는 `TEMP-NO-PLATFORM` 표식이 없다.
- **이 앱에 임시 처리를 넣게 되면 같은 방식으로 표시한다** — 표식 문구를 담은 주석을 달아 검색 한 번으로 전부 찾을 수 있게 한다. 표시 없는 임시 처리는 구멍으로 남는다.

## 3. 만드는 순서

원본은 `README.md` "만드는 순서"다. 여기는 그 표에 **단계마다 먼저 물어야 하는 미정 사항**과 **끝났는지 확인하는 법**을 붙인 것이다.
**한 번에 한 단계만 한다.** 순서를 바꾸려면 먼저 묻는다(`CLAUDE.md` §9).

- "먼저 물을 것"은 `CLAUDE.md` §7 · §7.1 · §7.2 에서 그 단계에 걸리는 것이다 — **정하지 않고 구현하지 않는다.** 정한 것은 이 폴더의 `contracts/`(없으면 그때 만든다 — `CLAUDE.md` §3.1)에 적는다.
- "끝났는지 확인"에는 **경로를 적지 않았다** — 엔드포인트의 경로 · 스키마가 전부 미정이다. curl 에 쓰는 경로와 본문은 그 단계에서 정한 것을 쓴다.
- 검증은 **PostgreSQL 에서만** 의미가 있다 — H2 는 롤/GRANT 와 제약을 재현하지 못한다(`CLAUDE.md` §5).

| 단계 | 만드는 것 | 이미 정해져 있는 것 | **먼저 물을 것 (미정)** | 끝났는지 확인 |
|---|---|---|---|---|
| **0** ✅ | 뼈대 | `CLAUDE.md` §4 | — | §1 에 적은 대로 실행해서 봤다 |
| **1** | 기반(스키마 `account` · Flyway) + 최소한의 계정(가입 · 로그인 · JWT 발급). **Spring Security 는 이 단계에서 인증 세부를 정하며 넣는다** | access 는 **쿠키로 주고받는 JWT**, refresh 는 **Redis 의 불투명 UUID** · rotation 필수(docs/11 #16 · D-14). **사용자 id 는 가입할 때 정한 로그인 아이디(문자열)** 이고 바꿀 수 없는 값이다(`CLAUDE.md` §3.5). 스키마 이름과 테이블 이름 `account`(users, credentials/refresh_tokens, game_accounts). 마이그레이션은 `db/migration/<schema>/`(#17). **미뤄도 되는 것** — refresh 교체와 denylist(처음에는 access 만으로 시작한다), 게임 계정 연결(`README.md`) | **§4 전부** — 인증 세부, 경로 · 스키마 · 에러 형식, 테이블 컬럼, DB 롤, 아이디의 허용 문자 · 길이, 뼈대가 임시로 둔 값 | 가입 → 로그인 → **응답의 쿠키에 access JWT 가 실려 오는지**(속성은 정한 대로). 틀린 비밀번호가 거절되는지. **같은 아이디로 두 번 가입하면 DB 제약이 막는지**(동시에 두 번 보내도 — `조회 → 판단 → 삽입`이 아니다, `CLAUDE.md` §5). 로그인 없이는 보호된 요청이 거절되는지. Flyway 가 `account` 스키마를 만들었는지(`\dn` · `\dt account.*`). 검증법(§4 A-③)이 정해졌으면 그 토큰을 `room` · `notification` 이 검증할 수 있는지는 **그 폴더에서** 본다 |
| **2** | 차단 — `social.blocks` | **테이블의 모양이 이미 정해져 있다** — `social.blocks(id 일련번호 PK — 채번은 이 앱, blocker_id 문자열, blocked_id 문자열)`, `(blocker_id, blocked_id)` UNIQUE 는 이 앱이 건다. `matching` 이 이미 이 모양으로 읽는다(`CLAUDE.md` §3.5 · docs/11 D-4). `matching` 롤에 `GRANT USAGE ON SCHEMA social` · `GRANT SELECT ON social.blocks`(D-1). 방향이 있는 한 줄이고 어느 쪽이 차단했든 같다. `BlockChanged.fifo` 는 만들지 않는다(D-12) | 롤/GRANT 를 누가 만드는가(`qm_matching` 롤을 이 앱의 마이그레이션이 만드는가 — `CLAUDE.md` §7 "테이블 컬럼, DB 롤"). 차단 · 해제의 경로 · 스키마. 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우(D-11 의 미정) | 같은 사람을 두 번 차단하면 **UNIQUE 가 막는지**. 해제하면 줄이 없어지는지. 컬럼이 `../matching/backend/src/main/java/com/queuemate/matching/block/Block.java` 와 맞는지. **`qm_matching` 롤로 붙어 `social.blocks` 는 읽히고 다른 테이블은 안 읽히는지**(PostgreSQL 에서만 볼 수 있다). 가능하면 `matching` 을 같은 DB 에 붙여 차단 관계인 두 사람이 한 파티가 안 되는지 |
| **3** | 모집 글 쓰기 · 수정, 게시판 목록(인원 · **방 안 사람 카드** · "찾는 포지션" 강조 · **차단 거르기**), **입장권 발급**, 게시판 채널 신호 발행 | 글의 상태는 모집 중 / 확정 / 만료. **방 키 넷과 읽는 법**(`CLAUDE.md` §3.3 · `docs/ROOM_CONTRACT.md` — 읽기만 한다. 접두사는 상수 한 곳에 두고 원본이 `room` 의 `RoomKeys` 임을 적는다). 차단은 **방 안의 누구와든**, 목록에서 빼고 입장권도 안 내준다(D-20). 포지션은 프로필의 주 포지션. 방장 키가 없으면 글을 만료로. 게시판 채널 `qm:pubsub:board` · `BOARD_CHANGED` · `payload` `{}`(D-22). 발행 실패가 글 쓰기를 뒤집지 않는다. `room` 을 호출하지 않는다 | **입장권**(형식 · 서명 방식 · 수명 · 담는 정보 · 서명 키를 나눠 갖는 법). **`roomId` 를 누가 어떻게 정하는가**(§4 C). **"아직 안 만들어진 방"과 "사라진 방"을 가리는 법.** 글에 담는 것 · "찾는 포지션"의 모양 · 정렬 · 필터. 카드에 담는 것("등"의 나머지) · 강조를 누가 계산하는가. 만석일 때의 표시. 만료 글 보존 기간. 게시판 채널 이름의 원본 상수를 둘 곳. 입장권을 받은 뒤 들어오기 전의 차단 경쟁. 강퇴당한 사람의 재입장을 입장권으로 막을지. 도배 대응. 급하면 신호 없이 프런트의 주기적 재요청으로 먼저 시작해도 된다(`CLAUDE.md` §3.2) | `room` 을 **같은 테스트용 Redis** 에 띄우고 방을 만들어 사람을 입장시킨 뒤 — 목록의 그 글에 **인원과 카드가 맞게 나오는지**, 방 안에 나와 차단 관계(어느 방향이든)인 사람이 있으면 **그 글이 목록에서 빠지고 입장권도 안 나오는지**, 방장이 나가 방장 키가 없어지면 글이 **만료**로 바뀌고 입장권이 안 나오는지, **방금 쓴 글(방 만들기 전)이 만료되지 않는지.** 글을 쓰면 `qm:pubsub:board` 에 `BOARD_CHANGED` 가 나가는지 — **구독해서 확인하는 테스트**로 남긴다(발행은 틀려도 조용하다). `notification` 을 같이 띄우면 SSE 로 오는 것까지 본다(`PUBSUB NUMSUB` 이 1 이 된 뒤에 — `CLAUDE.md` §9). 이 앱이 **방 키에 아무것도 쓰지 않았는지**(`MONITOR` 나 키 목록으로) |
| **4** | (이 앱의 몫이 없다) | 시그널 `POST` 와 `WEBRTC_SIGNAL` 은 `room` 의 일이고 돼 있다(D-16 · D-21). 음성은 프런트 | — | 브라우저 두 개로 음성 통화가 된다(프런트가 있어야 볼 수 있다) |
| **5** | 방장 확정의 기록 — 글을 "확정"으로, 파티원을 DB 에 | 확정 요청은 **`room` 이 받는다.** 이 앱은 확정 표시 키 `qm:room:{roomId}:confirmed` 와 멤버 SET 을 **읽어** 기록한다. 그 순간의 전원이 파티원 · 되돌릴 수 없다. `room` 의 키에 쓰지 않는다(D-21). **확정한 방은 방장이 바뀔 수 있다**(D-23) | **브라우저가 `room` 과 이 앱을 어떤 순서로 부르는가 — 검토한 방향이 있다, 정하지 않았다(`CLAUDE.md` §7.2 (가))**. `docs/ROOM_CONTRACT.md` 머리 절의 물음 둘(읽는 시점의 멤버 SET 은 확정 순간과 다를 수 있다 · 확정된 글에서 방장 키가 없을 때 — 확정한 방은 방장 키만 잠깐 없을 수 있다). 확정된 글의 목록 표시. 확정된 방의 기능(Ready 등). "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가 | `room` 에서 확정한 뒤 글이 "확정"이 되고 파티원이 기록되는지. 같은 확정을 두 번 기록하려 해도 **하나만 남는지**(정한 방식대로). 확정된 글에 입장권이 안 나오는지. **확정한 방의 방장이 나간 뒤에도 그 글이 만료로 바뀌지 않는지** |
| **6** | 자동 매칭 파티 — `ProposalConfirmed.fifo` 소비 | outbox + SQS FIFO · at-least-once · **소비는 멱등** · DLQ + `maxReceiveCount` · `MessageGroupId` 는 `proposalId`. Kafka 등으로 바꾸지 않는다(docs/11 #21 · `CLAUDE.md` §3.4) | 메시지 본문. 파티 id 를 `proposalId` 와 같게 둘지. AWS SDK 를 언제 들일지 · 로컬에서 SQS 를 무엇으로 흉내 낼지. **자동 매칭 파티의 방과 입장권을 누가 발급하는가 · `status=PARTY` 해제 — 검토한 방향이 있다, 정하지 않았다(`CLAUDE.md` §7.2 (나) · (다) · (라))**. `matching` 쪽 발행(`HANDOFF.md` §0-1 ③)이 같이 필요하다 | 같은 메시지를 **두 번** 넣어도 파티가 하나인지. 처리에 실패한 메시지가 DLQ 로 가는지. `MATCH_CONFIRMED` 직후의 파티 조회가 비어 있을 수 있다는 것을 클라이언트가 어떻게 다루는지 정했는지 |
| **7** | 친구 · 신고 · 최근 함께한 사람(`PartyClosed.fifo` 발행 + 소비) | `PartyClosed.fifo` 의 소비자는 이 앱 하나(D-13). 신고는 접수만 받는 최소 형태(`README.md`) | `PARTY_*` · `FRIEND_*` 7종의 이름과 `payload`. 경로 · 스키마 · 테이블 컬럼. 파티가 "닫혔다"를 무엇으로 판단하는가(이 폴더의 문서와 docs/11 에서 찾지 못했다) | 친구 요청 → 수락 → 목록. 알림이 상대의 채널에 나가는지(구독해서 확인). 닫힌 파티의 멤버가 서로의 "최근 함께한 사람"에 남는지 |

## 4. 첫 단계에 닿기 전에 소유자에게 물어야 하는 것

**문서에 "미정"으로 적힌 것만 모았다. 여기 적힌 선택지는 문서에 나온 것이고 추천이 아니다 — 임의로 정해 구현하지 않는다.** 정해지면 `CLAUDE.md` §7 에서 빼고 해당 절로 옮기며,
`matching` 폴더에서 docs/11 에 D-항목으로 남겨야 한다고 알린다.

**A. 인증 — 1단계의 전부가 여기에 걸린다** (`CLAUDE.md` §7 "인증 세부" ①~⑧ · docs/11 D-14)

1. **쿠키 속성** — `HttpOnly` · `Secure` · `SameSite`(Lax/Strict) · `Path` · `Domain` · 수명. refresh 쿠키의 `Path` 를 재발급 경로로 좁힐지.
2. **CSRF 대응** — `SameSite` 만인가, CSRF 토큰 · `Origin` 검사를 더하는가. 시그널 `POST` · 방 입장 · 차단 · 매칭 요청이 전부 해당한다(다른 서비스에도 걸린다).
3. **JWT 의 서명 방식과 키를 나눠 갖는 법** — HS256 비밀 키 공유 / RS256 공개 키 검증. `matching` · `notification` · `room` 이 이 토큰을 **스스로 검증**해야 한다. 클레임 구성. **JWT 라이브러리는 이것이 정해진 뒤에 고른다.**
4. **access · refresh 의 수명**, access denylist 를 둘지(docs/11 #16 은 두라고 적었고 조회 실패는 fail-closed 다 — 미뤄도 되는 것으로 분류돼 있다).
5. **refresh 의 Redis 키 이름 · 값 · TTL**(매칭 키 `qm:user:*` 와 겹치지 않는 접두사), 기기별 허용 개수, 옛 값 재사용(탈취 신호) 처리. — 처음에 access 만으로 시작하면 이것은 뒤로 간다. **그렇게 시작할지부터 묻는다.**
6. **SSE 와 토큰 만료** — `EventSource` 는 200 이 아닌 응답에 재접속을 멈춘다(`notification` 에 걸린다).
7. **로컬 개발의 CORS** — `credentials` · `withCredentials`. 프런트를 어느 주소에서 띄우는가.
8. **임시 식별에서의 전환 시점** — 세 서비스가 지금 `userId` 파라미터를 받는다(§2). 언제 · 어떤 순서로 바꾸는가.

**B. 계약 — 경로 · 스키마 · 에러 형식** (`CLAUDE.md` §3.1 · §7)

1. **계약 원본(queueMate 본 저장소 `feature/frontend` 의 `contracts/`)을 받아 올 수 있는가.** 받아 올 수 있으면 그것이 먼저다. 없으면 가입 · 로그인 · 로그아웃의 경로 · 요청/응답 · 에러 코드를 **묻고** 이 폴더의 `contracts/` 에 적는다("원본에 올려야 할 것" 표와 함께).
2. **에러 본문의 형식.** `room` 은 `matching` 과 같은 `{"code", "message", "details"}` 를 쓴다(`docs/ROOM_CONTRACT.md` "공통"). 이 앱도 같은 것을 쓰는지는 **어디에도 적혀 있지 않다.**

**C. DB** (`CLAUDE.md` §3.5 · §7 "테이블 컬럼, DB 롤")

1. **`account` 스키마의 테이블 컬럼** — 테이블 이름(users, credentials/refresh_tokens, game_accounts)만 있고 컬럼이 없다. refresh 를 Redis 에 두기로 했으므로(D-14) `refresh_tokens` 테이블이 남는지도 같이 묻는다. 비밀번호를 어떻게 저장하는지(해시 방식)는 이 폴더의 문서와 docs/11 에 없다.
2. **DB 롤** — 롤 이름(`matching` 은 `qm_matching`), 한 앱이 세 스키마를 어떤 롤로 붙는지, 롤/GRANT 를 누가 만드는지(마이그레이션인가 별도 절차인가).
3. **사용자 아이디의 허용 문자 · 길이** — 이 값이 Redis 채널 이름과 URL 에 그대로 나간다(`CLAUDE.md` §3.5).
4. **Flyway 의 버전 번호를 스키마 폴더 사이에서 어떻게 매기는가** — `db/migration/<schema>/` 로 나누는 것은 정해졌다(#17). 뼈대는 폴더를 하나로 훑으므로 번호가 전체에서 하나뿐이어야 한다 — 그대로 갈지.

**D. 뼈대가 임시로 둔 값 — 그대로 둘지 확인한다** (`CLAUDE.md` §7 "뼈대를 만들며 임시로 둔 값")

1. 기본 포트 **8082** 확정(지금까지 "제안"이다).
2. 로컬 DB 이름 `queuemate` · 계정 `postgres` / `postgres`(테스트용 컨테이너의 값이다. 환경변수 `DB_NAME` · `DB_USER` · `DB_PASSWORD` 로 덮는다).
3. **readiness 에 DB · Redis 를 넣을지** — 지금은 앱의 준비 상태만 본다(DB 가 죽어도 `/health/ready` 는 UP 일 것이다 — 실험하지는 않았다).
4. **JSON 로그 형식** — `CLAUDE.md` §5 는 "stdout JSON 로그"를 적지만 형식이 정해져 있지 않아 켜지 않았다. 지금은 사람이 읽는 기본 형식이다.
5. **패키지를 나누는 법** — `matching` · `room` 처럼 계층(`controller/` · `service/` · `domain/` …)으로 나눌지, 스키마(`account` · `party` · `social`)를 먼저 나눌지.
6. 헬스 주소를 맞추려고 actuator 를 루트로 옮긴 것(§1) — `room` 과 주소가 달라졌다. 그대로 둘지.

**E. 3단계에 닿기 전에 (지금 정할 필요는 없다 — 미리 알고 있으면 1 · 2단계의 선택이 달라질 수 있는 것)**

1. **입장권** — 형식 · 서명 방식 · 수명 · 담는 정보(D-16 은 "방 식별자, 방장이 누구인지, 만료 시각 등"이라고만 적었다) · 서명 키를 `room` 과 나눠 갖는 법. A-3(JWT 의 키를 나눠 갖는 법)과 같은 종류의 물음이라 **같이 정하면 한 번에 끝난다.** 파티 입장권을 `matching` 이 발급하는 방향(`CLAUDE.md` §7.2 (다))이면 발급하는 앱이 둘이 된다.
2. **`roomId` 를 누가 어떻게 정하는가** — **문서 어디에도 적혀 있지 않다**("미정"이라는 말조차 없다). `room` 은 주소의 `{roomId}` 를 문자열로 받기만 하고, 입장권이 "방 식별자"를 담는다고만 돼 있다. 글의 id 와 같은 값인가, 누가 만드는가, 어떤 모양인가(이 값도 Redis 키와 URL 에 나간다).
3. **글을 쓴 직후 · 방이 만들어지기 전의 글을 "방 없음"으로 읽지 않는 법** — 방장 키가 없다고 방금 쓴 글을 만료시키면 안 된다. 문서에 나온 방법의 예는 둘이다 — 글 작성 직후 일정 시간은 만료 검사를 건너뛴다 / 방이 한 번 생긴 적이 있는 글만 검사한다(`CLAUDE.md` §7.1). 입장권이 정해지면 "방 만들기"가 방장의 첫 입장에 합쳐지는지와 같이 정한다.
4. **게시판 채널 이름의 원본 상수를 어느 서비스에 둘지** — 지금 `notification` 과 `room` 이 각자 적어 두었다(두 값이 같아야 한다 — `docs/ROOM_CONTRACT.md` "게시판 채널 신호").

## 5. 문서 지도

**이 폴더**

| 파일 | 무엇인가 | 언제 읽나 |
|---|---|---|
| `START_HERE.md` | 이 파일. 진행 상황, 옆 서비스의 임시 처리, 만드는 순서, 먼저 물을 것 | 세션을 시작할 때마다. **단계가 끝나면 고친다** |
| `CLAUDE.md` | 규칙 — 경계, 계약, 설계 규칙, 미정 사항(§7 · §7.1 · §7.2), 작업 방식 · 운영 규칙, 커밋 규칙 | 작업 전에 |
| `README.md` | 하는 일 / 하지 않는 일의 짧은 소개, "만드는 순서"의 원본, "아직 정할 것" 요약 | 처음 한 번 |
| `docs/ROOM_CONTRACT.md` | `room` 계약의 발췌 사본 — 이 앱이 읽는 Redis 키, 방 만들기 · 입장 · 방장 확정 · 접속 확인, `room` 이 내는 알림, 게시판 채널 신호. 머리 절만 이 폴더에서 쓴 글이다 | 3 · 5단계에 들어갈 때. **낡는다** — 머리의 확인 명령 둘을 돌린다(2026-09-21 에 돌렸고 출력이 비어 있었다 — 커밋 `8a9c5fe` 그대로다) |
| `contracts/` | **아직 없다.** 여기서 정한 경로 · 스키마 · 알림을 적는 자리 | 처음 정할 때 만든다(`CLAUDE.md` §3.1) |
| `backend/` | Spring Boot 앱(빈 뼈대) | — |

**옆 폴더 (이 컴퓨터에 있을 때. 읽기만 한다 — 절대 경로는 `CLAUDE.md` §10)**

| 무엇 | 경로 |
|---|---|
| **전체 그림** — 서비스 다섯 개, 잇는 것, 한 번이 흘러가는 순서, 결정 한눈에(2026-09-20 기준이라 그 뒤의 것은 없다) | `../room/docs/PROJECT_OVERVIEW.md` |
| 결정 로그 원본 — 이 앱에 걸리는 항목은 `CLAUDE.md` §10 의 그 행에 있다. **파일 머리의 "낡은 항목 주의"로 걸러 읽는다** | `../matching/docs/11_DECISION_LOG.md` |
| 알림 계약(봉투 · 발행 주체 · SQS FIFO · 계약 구멍) / 봉투를 만드는 코드의 본보기 · 채널 접두사 원본 | `../matching/contracts/events.md` / `../matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `…/redisKeys/SharedKeys.java` |
| `matching` 이 읽는 `social.blocks` 의 모양 | `../matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `../matching/backend/src/test/resources/schema.sql` |
| `matching` 이 platform 을 기다리는 일(① 파티 풀기 ② 차단 스키마 ③ outbox) | `../matching/HANDOFF.md` §0-1 |
| `room` 의 계약 원본 · 방 키 상수의 원본 · 임시 처리의 원본 | `../room/contracts/room-api.md` · `../room/backend/src/main/java/com/queuemate/room/redisKeys/RoomKeys.java` · `../room/START_HERE.md` §2 |
| **2026-09-21 에 검토한 방향의 원문**(`room` 의 눈) | `../room/CLAUDE.md` §7 "`status=PARTY` 해제" 행 |
| 로컬 환경 함정(띄우고 죽이기 · IntelliJ · worktree · 테스트) | `../room/docs/NOTIFICATION_LESSONS.md` |
| 왜 PostgreSQL 인가 · 스키마 배치 · outbox | `../matching/docs/WHY_POSTGRESQL.md` |

## 6. 로컬에서 띄우는 법

**5432 · 6379 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다. 건드리지 않는다**(`CLAUDE.md` §9). 테스트용을 다른 포트로 따로 띄우고, 끝나면 내린다.

```bash
# 테스트용 PostgreSQL(5433) · Redis(6380). --rm 이라 멈추면 지워진다 — DB 가 매번 빈 채로 시작한다
docker.exe run -d --rm --name qm-platform-test-pg -p 5433:5432 -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=queuemate postgres:16-alpine
docker.exe run -d --rm --name qm-platform-test-redis -p 6380:6379 redis:7-alpine

cd backend && ./gradlew build        # 테스트 포함. 기본값이 5433 · 6380 이라 환경변수가 필요 없다. /mnt/c 아래라 느리다
cd backend && ./gradlew bootRun      # 앱은 8082
curl -s localhost:8082/health/live ; curl -s localhost:8082/health/ready

docker.exe exec qm-platform-test-pg psql -U postgres -d queuemate -c '\dn'      # psql · redis-cli 는 컨테이너 것을 빌려 쓴다
docker.exe exec qm-platform-test-redis redis-cli KEYS 'qm:*'

ss -ltnp | grep :8082      # → kill <PID> → ./gradlew --stop → docker.exe stop qm-platform-test-pg qm-platform-test-redis
                           #   (띄운 것은 반드시 내린다. pkill -f 금지. 마지막에 docker.exe ps 와 ss -ltn 으로 남은 것이 없는지 본다)
```

- **컨테이너를 안 띄우고 `./gradlew build` 를 돌리면 테스트가 실패한다**(접속 거부) — 건너뛰지 않는다. 앱이 뜨려면 Flyway 가 DB 에 붙어야 하기 때문이다.
  **건너뛰는 경우는 하나다** — `DB_PORT=5432` 이거나 `REDIS_PORT=6379` 일 때(남의 DB 에 Flyway 를 돌리지 않으려는 것이다. 둘 다 실행해서 확인했다 — 3건이 전부 skipped). **건너뛴 것을 통과로 읽지 마라.**
- `bootRun` 을 `kill` 로 내리면 Gradle 이 `BUILD FAILED`(exit value 143)를 찍는다 — SIGTERM 으로 끝났다는 뜻이고 정상이다. 앱 로그에 `Graceful shutdown complete` 가 있으면 곱게 내려간 것이다.
- `room` 을 같은 Redis 에 붙여 같이 띄우려면 `room/backend` 에서 `REDIS_PORT=6380 ./gradlew bootRun`(8083). `notification` 은 8081, `matching` 은 8080 이다.
- Claude 가 파일을 고친 뒤에는 IntelliJ 에서 `Ctrl+Alt+Y`. 그 밖의 함정은 `CLAUDE.md` §9 "운영 규칙"과 `../room/docs/NOTIFICATION_LESSONS.md`.
