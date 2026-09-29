# CLAUDE.md — platform 서비스 규칙 (Non-Negotiable)

작업 전에 `START_HERE.md`(지금 어디까지 됐나 · 만드는 순서 · 다음에 닿기 전에 물어야 하는 것) → 이 파일 → `README.md` → **`contracts/platform-api.md`**(이 폴더에서 정한 계약 — 경로 · 스키마 · 에러 코드 · 토큰 · 방) 순으로 읽어라.

이 폴더는 QueueMate의 **API 서버 배포 단위 하나**다. 문서에서 **`app:platform`**이라고 부르는 것이 이것이다
(docs/11 #15). 매칭 엔진 규칙은 옆 폴더 `matching`의 `CLAUDE.md`에, 알림 배달 규칙은 `notification`의 것에 있고, 이 파일은 **계정·파티·소셜 REST와 방 안의 일(입장·강퇴·시그널 — 2026-09-25 에 `room` 앱을 합쳤다)** 을 담는다(예약은 `app:reservation`으로 빠졌다 — docs/11 D-15).
옆 폴더 `room`(옛 `app:room`)의 `CLAUDE.md`는 합치기 전의 기록이다 — **읽기만 하고 근거로 쓰지 마라**(방의 규칙은 이 파일 §3.3 과 `contracts/platform-api.md` "방"이다).

> **출처 표기.** `docs/…` · `contracts/…` · `HANDOFF.md`처럼 폴더 이름 없이 적은 것은 전부 옆 폴더
> `matching` 기준이다(절대 경로는 §10). **출처가 안 붙은 사실은 정해지지 않은 것이다** — §7로 보낸다.
> **`contracts/platform-api.md`만은 이 폴더의 것이다**(옆 폴더의 계약은 `contracts/events.md` · `contracts/openapi.yaml` · `contracts/README.md`처럼 다른 파일 이름이다).

> **지금 상태와 그 지위(2026-09-21).** 빈 뼈대에서 **1단계(계정 · 인증 · 소셜 로그인 · 게임 프로필) · 2단계(차단) · 3단계(모집 글 · 목록 · 게시판 채널 신호) · 5단계(방장 확정의 기록) · 7단계의 일부(친구 · 신고 · 최근 함께한 사람의 읽기 · 알림 둘)가 구현됐다**(`START_HERE.md` §1). ~~**6단계(SQS)는 여전히 막혀 있다.**~~ → **2026-09-27 소유자 결정으로 6단계(SQS)가 없어졌다**(docs/11 **D-42** — outbox · `ProposalConfirmed.fifo` · `PartyClosed.fifo` 를 두지 않는다. 확정된 파티는 `matching` 이 Redis 파티 HASH `qm:party:{partyId}` 에 자기완결로 적고 **이 앱이 그것을 읽어** 파티와 방을 만든다 — §3.3 · §3.4). **자동 매칭 파티의 방은 `POST /api/v1/match-parties/{partyId}/room` 으로 만든다**(없으면 만들고 있으면 들어간다 · `roomId = partyId` — **P-30**, `contracts/platform-api.md` "자동 매칭 파티의 방". **경로 · 응답 · 에러 코드 · DB 칸은 Claude 가 정한 세부이고 소유자가 검토하지 않았다**). **2026-09-23 소유자 결정 셋이 붙었다** — **refresh 토큰**(access 15분 · refresh 7일 — §5.1 (라) · (마) · `contracts/platform-api.md` "refresh 토큰" · P-15) · **LoL 전적 동기화**(Riot API — §7 "게임 계정 연동" · `contracts/platform-api.md` "전적을 긁는 것" · P-13) · **게시판 목록의 페이지 나누기**(커서 방식 — §7.1 · `contracts/platform-api.md` "목록의 페이지 나누기" · P-14). **VALORANT · PUBG의 전적과 `verified`를 켜는 길은 아직 없다.** **2026-09-24 소유자 결정 다섯이 더 붙었다** — ① **`mode` · `tier`의 값을 `matching`의 gameconfig(Redis)에서 읽어 검증한다**(§3.6 · `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16. **§2 · §11의 "`qm:gameconfig:*` 접근 — 예외가 없다"를 개정한다**) ② **모집 글을 쓸 때 전적을 긁던 경로를 없앴다**(§7 "게임 계정 연동" · `contracts/platform-api.md` "전적을 긁는 것" — **P-13의 "긁는 시점은 둘"을 하나로 고치는 것이다.** 신선도 장치도 같이 없어졌다) ③ **"전적 갱신" 요청을 두었다**(`POST /api/v1/users/me/game-accounts/{game}/refresh` — **동기** · 쿨타임 2분 · 상한 30초. ②로 낡은 채 남게 된 전적을 사용자가 직접 갱신하는 길이라 **긁는 시점이 다시 둘이 됐다.** §7 "게임 계정 연동" · `contracts/platform-api.md` "전적을 긁는 것" 의 "전적 갱신" · **P-17**) ④ **글 한 줄에서 `filledPositions`(찾는 포지션 가운데 이미 방 안에 채워진 것의 강조)를 없앴다**(§7.1 · `contracts/platform-api.md` "글 한 줄" · **P-18**. **주 포지션은 "내가 주로 하는 것"이지 "이 방에서 할 것"이 아니라 틀린 정보였다.** **docs/11 D-20의 ③을 개정한다** — 그 D-항목의 ①②④는 그대로 유효하다). ⑤ **방에 방장 말고 누가 있으면 모집 글을 고칠 수 없다**(§7.1 · `contracts/platform-api.md` "모집 글 · 목록" 의 `PATCH` · **P-19**. 409 `ROOM_HAS_OTHER_MEMBERS` — **조건이 바뀌는데 방 안 사람에게 알릴 길이 없다.** 개정하는 옛 D-항목은 없다 — 새 규칙이다(docs/11 D-32)). **2026-09-25 소유자 결정 셋이 더 붙었다** — ① **게시판 목록의 보존 기간(만료 · 확정된 글을 10분만 보여 주던 것)을 없앴다**(§7.1 · `contracts/platform-api.md` "목록의 정렬" · **P-20**. 끝난 글도 목록에 계속 남고 설정 `platform.board.closed-retention`이 없어졌다 — **P-5가 정했던 10분 보존을 걷어낸다.** 정렬 · 커서 · 마이그레이션은 그대로다) ② **목록 커서의 base64url 한 겹을 없애고 글 번호를 그대로 쓴다**(§7.1 · `contracts/platform-api.md` "목록의 페이지 나누기" — **P-14의 개정이다.** `cursor` · `nextCursor`가 숫자가 되고 `BoardCursor` 클래스가 없어졌다. **감싸도 얻는 것이 없었다** — 서명하지 않아 보안 값이 0이고 글 번호는 `postId`로 이미 다 나간다) ③ **게시판 목록의 `game`을 필수로 만들었다**(§7.1 · `contracts/platform-api.md` "목록의 `game` 은 필수다" · **P-21**. **게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다** — 안 보내면 400 `VALIDATION_FAILED`다. 게임 없이 훑던 쿼리 둘이 없어졌다. 개정하는 옛 D-항목은 없다 — 새 규칙이다(docs/11 D-28)).
> 소유자가 "네가 platform을 만들어 봐라"고 맡겼고, 이 파일이 "미정 — 임의로 정해 구현하지 마라"로 묶어 두었던 것들을 **Claude가 정해 구현했다. 소유자는 아직 항목별로 검토하지 않았다.**
> 그렇게 정한 것의 원본은 **`contracts/platform-api.md`**다(머리의 "지위" 문단 · 맨 아래 "원본에 올려야 할 것" **P-1~P-30**). **P-11 ~ P-29는 소유자가 직접 정한 것이고, P-30(자동 매칭 파티의 방 — 2026-09-27)은 소유자 결정 D-42 위에 Claude 가 정한 세부다**(P-22 의 세부 가운데 Claude 가 정한 것은 그 행에 가려 적었다) — P-11은 2026-09-22(바로 아래)이고 **P-15는 refresh 토큰**(2026-09-23 — §5.1 (라) · (마)), **P-16은 gameconfig 를 읽는 것 · P-17은 전적 갱신 요청 · P-18은 `filledPositions` 를 없앤 것이다**(셋 다 2026-09-24 — §3.6 · §7 "게임 계정 연동" · §7.1). **소유자가 정한 것은 2026-09-26 에 `matching` 폴더에서 docs/11 D-24 ~ D-35로 남겼다**(P-2 → D-24 · P-11 → D-25 · P-15 → D-26 · P-12 · P-13 → D-27 · P-14 · P-20 · P-21 → D-28 · P-16 → D-29 · P-17 → D-30 · P-18 → D-31 · P-19 → D-32 · P-22 → D-33 · P-23 → D-34 · P-24 → D-35 · P-25 → D-36. **P-26 → D-37**(D-27을 개정한다 — 2026-09-27) · **P-27 → D-38 · P-29 → D-39 · P-28 → D-40**(2026-09-27)). Claude가 정한 것은 docs/11에 없다.
> 이 파일에서 출처가 `contracts/platform-api.md`인 것은 **전부 그 지위다** — 소유자가 검토하며 뒤집을 수 있다. 소유자가 직접 정한 것은 "소유자 확정" · "소유자 지시"라고 따로 적었다.
> **"미정이니 묻고 정하라"는 규칙은 남은 미정(§7 · §7.1 · §7.2)에 대해 그대로 유효하다** — 이번에 맡긴 것이 다음에도 임의로 정해도 된다는 뜻은 아니다.

> **2026-09-22 소유자 결정 둘 — 이것은 소유자가 직접 정했다.**
> ① **모든 테이블의 PK를 `bigint GENERATED ALWAYS AS IDENTITY`로 하고, 사용자의 식별자를 둘로 가른다** — **`users.id`(사용자 번호)가 `userId`**이고 **로그인 아이디는 `login_id` · `loginId`로 따로** 둔다(§3.5). — **`loginId` 쪽은 2026-09-26 에 없어졌다**(아래 "2026-09-26" 둘째 블록 · P-24. 사용자 번호는 그대로다).
> **2026-09-19의 "사용자 id는 가입할 때 정한 로그인 아이디(문자열)"를 개정하는 것이고 docs/11 D-4와 얽힌다.** 코드와 마이그레이션은 전부 이 모양이다 — 계약은 `contracts/platform-api.md`(P-11).
> **`matching`의 `block/Block.java`를 `Long`으로 바꿔야 했다 — 2026-09-26 에 그 폴더에서 바꿨다**(`schema = "social"`도 같이 뺐다).
> ② **스키마별 DB 롤을 두지 않는다**(§3.5 — docs/11 #17 · D-1을 개정한다). **①은 docs/11 D-25, ②는 D-34에 접혀 남았다(2026-09-26).** — **②는 2026-09-26 에 스키마가 `public` 하나가 되며 물음째 없어졌다**(바로 아래 "2026-09-26").

> **2026-09-24 소유자 결정 하나 — 이것도 소유자가 직접 정했다.**
> **이 앱이 `qm:gameconfig:*`를 읽어 `mode`(모집 글)와 `tier`(게임 계정)가 있는 값인지 검증한다**(§3.6 — 값의 원본은 `matching/seed/gameconfig.redis`다. **읽기만 한다**).
> **Redis를 못 읽으면 검증만 건너뛰고 통과시킨다(fail-open)**, **`mode`가 필수가 됐다**(고치기에서 빈 문자열로 비우는 길이 없어졌다), `tier`는 값이 있을 때만 본다. 코드와 계약은 이 모양이다 — `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16.
> **§2 · §11의 "매칭 Redis 키(… `qm:gameconfig:*` …) 접근 — 예외가 없다"와 docs/11 #15를 개정하는 것이다** — docs/11 **D-29**로 남겼다(2026-09-26).
> **나머지 넷(`qm:party:*` · `qm:user:*` · `qm:proposal:*` · `qm:lock:*`)의 금지는 그대로다.** — **2026-09-27 D-42 로 `qm:party:{partyId}` 읽기가 둘째 예외가 됐다**(확정된 자동 매칭 파티를 방으로 만들 때 `HGETALL` · `HEXISTS` 만. 쓰지 않는다 — §3.3 · §3.4. `qm:user:*` · `qm:proposal:*` · `qm:lock:*` 은 그대로 금지다).

> **2026-09-25 소유자 결정 하나 — 이것도 소유자가 직접 정했다.**
> **게시판 목록에서 "만료 · 확정된 글은 10분만 보여 준다"는 보존 기간을 없앴다** — **모집 중 · 확정 · 만료를 전부 `id` 내림차순으로 보여 주고 끝난 글도 계속 남는다**(§7.1 · `contracts/platform-api.md` "목록의 정렬" · **P-20**).
> **왜 둘** — ① 옛 조건이 `status = RECRUITING or confirmedAt > ? or expiredAt > ?`라는 **세 컬럼에 걸친 `OR` 셋**이라 `(game, id DESC)` 인덱스를 깨끗하게 타지 못했다(조건이 `game` 하나면 그 인덱스를 훑어 내려가면 끝이다) ② 끝난 글이 10분 만에 사라지면 **"모집이 얼마나 활발한가"를 보여 주지 못한다.**
> **설정 `platform.board.closed-retention`이 없어졌다. 정렬 · 커서는 바뀌지 않았고 마이그레이션도 없다** — `expired_at` · `confirmed_at` 컬럼은 CHECK 제약과 "언제 끝났는지"로 그대로 쓴다.
> **P-5가 정했던 10분 보존을 걷어내는 것이다**(그쪽은 Claude가 정했고 이번 것은 소유자 결정이라 **새 번호를 두었다** — P-5의 나머지 절반도 같은 날 합치기 2단계로 없어졌다. 아래 블록). docs/11 **D-28**로 남겼다(2026-09-26).

> **2026-09-25 소유자 결정 — `room` 앱을 이 앱에 합쳤다**(`contracts/platform-api.md` **P-22** · 이 파일 §3.3 의 회고). **`notification`(SSE 연결 보유)과 `matching`(매칭 엔진)은 그대로 따로 둔다.**
> **1단계(옮겨서 돌게 하기)** — 코드가 `com.queuemate.platform.room` 패키지로 왔고(`account` · `party` · `social` 과 나란한 도메인. DB 가 없고 상태는 Redis 에만 있다),
> 방의 요청(`/api/v1/rooms/**` — 경로 그대로)을 이 앱(8082)이 받는다. **포트 8083 은 없어졌다.** `?userId=` 가 없어지고 `qm_access` 쿠키의 사용자가 "나"다.
> **2단계(두 앱을 전제로 만든 경계 장치 걷어내기)** — 소유자 결정 둘: **① 입장 경로(`POST /api/v1/rooms/{roomId}/members`)는 그대로 두고 그 안에서 글을 검사한다 · C 글 쓰기가 방을 같이 만들고, 방을 못 만들면 글도 되돌린다.**
> **같은 날 소유자가 하나 더 정했다 — 방과 글은 같이 산다**(확정 전에는 방장이 나가도 글을 지워도 둘 다 끝난다 — §3.3 · §7.1).
> 그래서 — 방 만들기 요청(`POST /api/v1/rooms/{roomId}`) · 확정 기록 요청(`POST /api/v1/posts/{postId}/confirm`)이 없어졌다 · **방장 확정은 `POST /api/v1/rooms/{roomId}/confirm` 한 요청이 Redis 와 DB 를 같이 쓴다** ·
> 모집 중인 글에 방장 키가 없으면 무조건 만료다(마이그레이션 `party/V8` — 2026-09-26 에 `V1__schema.sql`로 합쳤다) · 게시판이 방 키를 Redis 로 직접 읽지 않고 `room/service/RoomService#states` 를 부른다 · **에러 코드가 한 벌이 됐다**(`INVALID_REQUEST` → `VALIDATION_FAILED`, `ROOM_UNAVAILABLE` → `ROOM_STATE_UNAVAILABLE`).
> 방의 계약은 **`contracts/platform-api.md` "방"** 절이다(옛 `contracts/room-api.md` 를 합치고 지웠다). 맞물리는 법은 §3.3 이다.
> **`matching` 과의 약속(D-19)은 그대로다** — 활성 요청 키는 `matching` 이 쓰고 이 앱은 `EXISTS` 만, 입장 표시 키는 이 앱이 쓰고 `matching` 은 `EXISTS` 만 한다.
> **docs/11 의 D-16 · D-19 ~ D-23 이 "두 앱"을 전제로 쓰여 있던 것은 2026-09-26 에 docs/11 D-33 이 개정했다**(이 합치기가 D-33 이다). 되돌리기용 태그는 `pre-room-merge` 다.

> **2026-09-26 소유자 결정 — DB 스키마 셋(`account` · `social` · `party`)을 `public` 하나로 합치고, 테이블 사이의 JOIN · FK 를 허용한다**(§3.5 · `contracts/platform-api.md` **P-23**).
> 사용자 번호를 담는 칸에 전부 `users(id)` FK(`ON DELETE CASCADE`)를 걸었고, 마이그레이션을 `db/migration/V1__schema.sql` 하나로 다시 썼다(운영 DB 가 없어서다 — "이미 적용된 파일은 고치지 않는다"는 이 파일부터 다시 걸린다).
> **왜** — DB 를 보는 앱이 사실상 이 앱 하나인데 스키마를 나누고 JOIN · FK 를 막은 탓에 코드가 쓸데없이 복잡했다(닉네임을 따로 읽어 자바에서 정렬 · 사용자 존재를 조회로 확인 · 도메인 사이 "읽는 창구"). **패키지 나누기는 그대로다.**
> **`matching`에 걸렸다** — `block/Block.java`의 `schema = "social"`을 뺐다(2026-09-26 — 그 폴더에서 했다). docs/11 #17 · D-1 · WHY_POSTGRESQL §3 을 개정하고 **docs/11 D-34로 남겼다**(2026-09-26).

> **2026-09-26 소유자 결정 — 직접 가입 · 비밀번호 로그인을 없애고 소셜 로그인(카카오 · 디스코드)만 남긴다**(§2 · §3.5 · §5.1 · `contracts/platform-api.md` **P-24**).
> ① 가입 · 로그인은 소셜로만 — **왜** 비밀번호 관리 · 이메일 인증 같은 부담을 지지 않는다. `POST /api/v1/auth/signup` · `POST /api/v1/auth/login` · 비밀번호 · `credentials` 테이블 · **로그인 실패 제한 전부**(P-10)가 없어졌다(테이블은 13개).
> ② **`loginId`를 없앴다** — 식별자는 사용자 번호(`userId`) 하나, 보여 주는 이름은 닉네임 하나다. 친구 요청도 사용자 번호로 한다. ③ 소셜로 처음 온 사람은 **닉네임만** 정한다(`/social/pending` → `POST /social/signup {nickname}`은 그대로).
> **바뀌지 않은 것** — 사용자 번호 · JWT의 `sub` · refresh(P-15) · 로그아웃 · 쿠키 · `token_use` · `Origin` 검사. **실제 카카오 · 디스코드 키 등록이 이제 필수다**(§7 "소셜 로그인에 남은 것"). 마이그레이션은 V1을 그 자리에서 고쳤다(운영 DB 없음).
> P-7 · P-10 · P-11의 `loginId` 절반 · docs/00의 계정 정의 · docs/11 D-25의 절반을 개정한다 — **docs/11 D-35로 남겼다**(2026-09-26).

> **2026-09-26 소유자 결정 — 확정된 방이 없어질 때 파티가 닫힌다**(§3.3 "파티 닫힘" · `contracts/platform-api.md` **P-25**). `parties.status = 'CLOSED'` · `closed_at`을 적고, 그 순간의 파티원(`party_members`)끼리 서로를 `recent_players`에 적는다 — **최근 함께한 사람이 이제 채워진다.**
> 게시판 파티는 `PartyClosed.fifo`(SQS) 없이 이 앱 안에서 닫는다(자동 매칭 파티는 6단계에서 다시 본다 — **→ 2026-09-27 D-42 · P-30 으로 같은 나가기 콜백으로 닫는다**). 글은 `CONFIRMED` 그대로 · 응답에 새 칸 없음 · `PARTY_*` 알림은 여전히 내지 않는다.
> **왜** — `parties.status`가 `ACTIVE`로 박힌 채 아무도 바꾸지 않았고 "파티가 닫혔다"가 미정이라 최근 함께한 사람이 늘 빈 목록이었다. **새 규칙이다 — docs/11 D-36으로 남겼다**(2026-09-26).
> **같은 날 결정 로그가 따라잡았다** — 위 블록들이 "docs/11에 D-항목이 없다"고 적었던 소유자 결정(P-2 · P-11 ~ P-24)은 `matching`의 docs/11 **D-24 ~ D-35**로 남았다(대응은 머리 "지금 상태와 그 지위" · `contracts/platform-api.md` 맨 아래 표). `matching`의 `Block.java`도 같은 날 `Long` · `public.blocks`로 고쳐졌다. **`notification` · `matching`의 `?userId=` → 쿠키 전환은 2026-09-27 에 둘 다 했다**(§5.1 (아) — `notification` 은 그 폴더 `CLAUDE.md` §5.1, `matching` 은 `HANDOFF.md` §0-5. 개발용 스위치는 어느 쪽도 두지 않았다).

> **2026-09-27 소유자 결정 — LoL 게임 계정의 `tier`는 Riot에서 채운다(전적과 함께). `mainPosition`은 자기신고 그대로다**(→ **`mainPosition`은 2026-09-29 에 칸째 없어졌다 — 아래 P-35 블록**)(§7 "게임 계정 연동" · `contracts/platform-api.md` **P-26**).
> 처음에는 "`gameNickname`만 받고 `tier` · `mainPosition`을 Riot에서 채운다"였다 — **같은 날 소유자가 주 포지션 절반을 되물렸다: 주 포지션은 "이번에 같이 할 때 맡을 자리"라 사용자가 정한다**(최근 경기의 최빈값은 그 뜻이 아니다).
> `PUT /api/v1/users/me/game-accounts/LOL`의 본문은 `{gameNickname, mainPosition}`이다(포지션은 선택 · `tier` · `server`를 보내면 400). **동기다** — 저장하기 전에 Riot을 긁고(상한 30초 — 전적 갱신과 같은 길) 응답에 `tier` · `stats`가 바로 들어 있다(`mainPosition`은 요청 값).
> 이름#태그가 Riot에 없으면 404 `RIOT_ID_NOT_FOUND`(새 코드), Riot 장애 · 시간 초과 · 키 없음은 503 `GAME_STATS_UNAVAILABLE` — **둘 다 저장하지 않는다.** 전적 갱신도 `tier`를 같이 갱신한다(`mainPosition`은 건드리지 않는다). **VALORANT · PUBG는 지금대로 자기신고다.** 저장 뒤 비동기로 긁던 길은 없어졌다.
> **왜** — Riot에서 티어를 이미 받아 오면서 저장하지 않았다(계정(09-21)이 Riot 연동(09-23)보다 먼저 만들어져 요청 모양을 안 고쳤다). P-8 · P-13 · P-17을 개정하고 docs/11 D-27에 걸린다 — **docs/11 D-37로 남겼다**(2026-09-27).

> **2026-09-27 소유자 결정 셋 더.** ① **소셜 계정 잇기 · 끊기**(P-27 · docs/11 **D-38** · **구현됐다**) — 로그인한 채 다른 제공자로 오면 **같은 사용자에 잇고**, `DELETE /api/v1/users/me/social/{provider}`로 끊는다(마지막 하나는 409 `LAST_SOCIAL_IDENTITY`). 이메일을 받지 않으니 **자동으로 중복을 잡지는 않는다**(§2 · §7 "소셜 로그인에 남은 것").
> ② **자동 매칭이 게시판 방에 먼저 합류하는 길의 세부 넷**(P-28 · docs/11 **D-40**) — 이 앱의 요청 하나(없으면 404 → **프런트가** `matching`을 부른다) · 조건(`game` · `mode` · `voice` · PUBG 시점 · 티어 범위 `tier-range` · 주 포지션) · 가장 오래된 방부터 · 활성 요청 키를 만들지 않는다. **2026-09-28 소유자가 경로(`POST /api/v1/posts/auto-join`) · 본문(`matching` 의 매칭 요청과 같은 모양) · 없을 때(404 `NO_MATCHING_POST`) · 정원(모드의 `targetPartySize`) · "자동 합류 허용" 칸 없음을 정하고 Claude 에게 맡겨 구현됐다**(§7 그 행 · §3.6 · `contracts/platform-api.md` 그 절).
> ③ **모집 글에서 `purpose`를 없앴다**(P-29 · docs/11 **D-39**) — 게시판에 플레이 목적까지 넣지 않는다. `voice`는 남고 `matching`의 `PlayPurpose`는 그대로다(§7.1 "글에 담는 것").

> **2026-09-27 소유자 결정 — 확정된 파티는 이 앱이 `matching` 의 Redis 파티 HASH `qm:party:{partyId}` 를 직접 읽어 만든다. outbox → SQS `ProposalConfirmed.fifo` 는 두지 않는다**(docs/11 **D-42** — #18 · #21 · #27 의 DB 절반 · D-13 을 개정한다. §3.3 · §3.4 · `contracts/platform-api.md` "자동 매칭 파티의 방").
> `matching` 이 확정 직후 그 HASH 를 자기완결로 채우고(`status=CONFIRMED` · `confirmedAt`(epoch ms) · `game` · `modeKey` · `voicePreference` · `playPurpose` · `target` · `member:{userId}=keyValue` · 티어 모드의 `tierLo`/`tierHi` — **수명 600초**) 파티원 전원에게 `MATCH_CONFIRMED {partyId}` 를 보낸다. 확정된 사용자의 활성 요청(`status=PARTY`)은 **60초 뒤 저절로 사라진다** — 그 뒤 "한 번에 하나만"(D-11 · D-19)은 이 앱의 입장 표시 키가 맡는다(`claim-request.lua` 가 이미 `KEYS[2]` 로 본다). **6단계(SQS)가 없어졌고 §7 "SQS 메시지 본문 3종 · 자동 매칭 파티의 id" · "확정된 사용자를 푸는 길" · §7.2 (나) · (다) · (라) 가 닫혔다.**
> **§2 · §3.6 · §11 의 "매칭 Redis 키 접근 — 예외 없음" 을 개정한다** — `qm:gameconfig:*` 읽기(D-29)에 이어 **`qm:party:{partyId}` 읽기가 둘째 예외다**(`HGETALL` · `HEXISTS` 만. 쓰지도 지우지도 `EXPIRE` 를 걸지도 않는다). 나머지(`qm:user:*` · `qm:proposal:*` · `qm:lock:*`)의 금지는 그대로다.
> **이 앱 쪽 세부는 Claude 가 정했고 소유자가 검토하지 않았다 — P-30**(`contracts/platform-api.md` 맨 아래) — **`POST /api/v1/match-parties/{partyId}/room`**("없으면 만들고 있으면 들어간다" · 201/200 `{roomId}` · 404 `MATCH_PARTY_NOT_FOUND` · 403 `NOT_PARTY_MEMBER` · 409 `IN_OTHER_ROOM` · 409 `ROOM_FULL` · 503 `ROOM_STATE_UNAVAILABLE` — HASH 를 못 읽으면 **fail-closed**) · **`roomId = partyId`(UUID)** 로 방 키 · 방 요청을 그대로 쓴다 · **처음부터 확정된 방**(D-23 승계) · **처음 부른 사람이 방장** · 정원은 HASH 의 `target` · 게시판 신호 없음 · 전용 Lua `enter-match-room.lua` 는 활성 요청 키를 보지 않는다(자격은 `member:{userId}` 의 `HEXISTS`) · 마이그레이션 **V2** `parties.match_party_id varchar(36) UNIQUE` + `CHECK ((source='MATCH') = (match_party_id IS NOT NULL))` · **DB 먼저 커밋, Lua 는 트랜잭션 밖**(트랜잭션 안 Redis 의 예외를 늘리지 않는다) · 닫힘은 게시판 파티와 같은 나가기 콜백(`match_party_id` 로 찾는다). **남은 것** — ~~전원이 말없이 사라진 자동 매칭 파티는 닫히지 않는다~~(2026-09-28 소유자 결정 — 그 게임의 목록 GET 이 `match_party_id` 로 골라 방 키를 읽어 닫는다, §3.3 "파티 닫힘") · ~~`playPurpose` 는 `parties` 에 칸이 없다~~(2026-09-28 소유자 결정 — 담지 않는다) · `PARTY_*` 알림은 여전히 미정 · **입장 표시 키가 부른 사람에게만 찍힌다**(D-42 4번은 "전원에게" 라고 적었다 — 소유자 검토 항목).

> **2026-09-29 소유자 결정 — 강퇴 · 나가기 뒤 10분 동안 그 방에 다시 못 들어온다**(`contracts/platform-api.md` **P-32** · "방" 의 "입장" · "나가기" · "강퇴" · "Redis 키" · §3.3 · docs/11 **D-46** 으로 남긴다). **코드도 소유자가 짰다**(`0879d01`).
> **강퇴당한 사람**은 그 방에 10분 동안 **직접 입장도(403 `KICKED_RECENTLY`) 게시판 방 먼저 합류도** 안 된다. **스스로 나간 사람**은 10분 동안 **자동 합류에서만** 그 방을 건너뛴다 — 직접 입장은 막지 않는다. 키 둘 `qm:room:no-entry:{userId}` · `qm:room:no-auto-join:{userId}`(사용자별 ZSET · 원소 `roomId` · score 는 풀리는 시각 · 수명 600초 — 원본 상수 `room/redisKeys/RoomKeys`) 를 **강퇴 · 나가기 스크립트가 방에서 빼는 것과 한 번에 적는다.** `enter-room.lua` 가 `no-entry` 를 맨 먼저 읽고, `no-auto-join` 은 `RoomService#noAutoJoinRooms` 를 통해 `AutoJoinService` 가 읽는다(`party` 는 방 키를 직접 읽지 않는다 — §3.3).
> **자동 매칭 파티의 방(`enter-match-room.lua`)은 `no-entry` 를 보지 않는다 — 막을지는 미정이다**(§7.1). Claude 가 정한 세부 — 403 인 것 · 코드 이름 · 키 이름 둘 · 자동 합류에서 그 거절은 다음 방 · 10분이 코드 상수인 것(계약 P-32). 같은 날 자동 합류의 후보 쿼리가 **음성 · 내 글 제외**를 SQL 에서 거르게 됐다(`bbaf567` — 자바에서 거르면 안 맞는 글이 후보 상한을 잡아먹었다).

> **2026-09-29 소유자 결정 둘 더 — ① 구글 로그인 ② 개발용 로그인(임시)**(`contracts/platform-api.md` **P-33 · P-34**).
> ① **구글을 소셜 제공자에 더했다** — 카카오 · 디스코드와 같은 흐름 · 콜백 갈래 · 잇기 · 끊기 · 에러 코드(`/api/v1/auth/oauth/GOOGLE/…` · 설정 `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET`). scope `openid profile`(이메일 없음) · 회원 번호는 userinfo 의 `sub`(문자열) · 추천 닉네임은 `name`. 마이그레이션 **V3** 가 `provider` CHECK 에 `GOOGLE` 을 더하고 `provider_user_id` 를 255 로 넓혔다(§3.5). **실제 구글로는 붙여 보지 않았다**(§7 "소셜 로그인에 남은 것").
> ② **개발용 로그인 `POST /api/v1/auth/dev-login` 을 임시로 열었다** — 소셜 앱 키가 없어 아무도 로그인할 수 없는 동안 프런트와 붙여 보려는 것이다. 닉네임의 사용자로 **진짜 쿠키**(access · refresh)를 준다 · **`DEV_LOGIN_ENABLED` 기본 `false` — 꺼지면 없는 경로와 같은 404** · 코드 · 설정 · 테스트에 **`TEMP-DEV-LOGIN`** 표식(§5.1 끝 · §11). 세부(scope · 필드 · V3 · 요청의 모양 · 닉네임 기본값)는 **Claude 가 정했다 — 검토 항목.**

> **2026-09-29 소유자 결정 — 게임 계정의 주 포지션(`mainPosition` · `main_position`)을 없앤다**(`contracts/platform-api.md` **P-35**). 소유자의 말 — "주 포지션, 주 역할군은 게시판에 글 쓸 때 하는 거니까 계정 연동에서 할 이유가 없다."
> `PUT …/game-accounts/{game}` 이 받지 않고(LoL `{gameNickname}` · VALORANT `{gameNickname, tier?}` · PUBG `{gameNickname, tier?, server}`) **게임 프로필에서 그 칸이 빠졌다 — 게시판 카드에 사람별 포지션이 없다**(docs/11 D-20 ② 의 주 포지션 절반을 개정한다). 컬럼은 마이그레이션 **V4** 가 지웠다(§3.5). 글의 `wantedPositions` 는 그대로다.
> **P-26(주 포지션은 자기신고 그대로) · P-8 · D-37 을 개정한다 — docs/11 **D-47** 로 남겼다**(`matching` 브랜치 `4d95602`). **Claude 가 정한 세부**(검토 항목) — `mainPosition` 이 오면 조용히 버리지 않고 **400 `VALIDATION_FAILED`**(`@Null` — 어느 게임이든 서비스 · Riot 전 · `null` 은 통과) · 그 칸만 검증하던 `Game#allowsPosition` 을 지웠다. ~~**글을 쓸 때 방장이 자기 포지션을 고르는 칸은 미정이다 — 지어내지 마라**(§7.1).~~ → **2026-09-30 에 정해졌다 — P-38**(아래 블록).

> **2026-09-29 소유자 결정 셋 — 티어를 사다리(랭크 큐)마다 저장하고, PUBG 의 전적 · 티어를 PUBG API 로 채운다**(`contracts/platform-api.md` **P-36** · docs/11 **D-48** — `matching` 폴더가 쓰는 중). ① "모드별 티어를 무조건 저장한다" ② "PUBG 티어는 API 로 채우고 LoL 처럼 동기로" ③ 저장 모양은 **jsonb 칸 하나**(`game_accounts.tiers` — 마이그레이션 **V5**, §3.5).
> 사다리 키 LoL `SOLO` · `FLEX` / VALORANT `COMPETITIVE` / PUBG `RANKED`(하나 — 시즌 36 부터 모드에 걸쳐 통합) · 게임 프로필의 `tier` 가 **`tiers`**(그 게임의 사다리 키 전부 · 없으면 `null`)가 됐다 · PUBG 의 `PUT` 은 `{gameNickname, server}`(`tier` 는 400)이고 저장 전에 동기로 긁는다(404 `PUBG_PLAYER_NOT_FOUND`) · 전적 갱신도 PUBG 가 된다 · LoL 은 솔로 · 자유 둘을 채운다 · **어느 모드가 어느 사다리인지는 gameconfig 모드 HASH 의 `tierLadder`** 다(읽기만 — §3.6) · 자동 합류의 방장 티어는 그 모드의 사다리 값이다.
> **왜** — 계정에 티어를 하나만 적어 LoL 자유랭크 매칭이 솔로랭크 티어로 돌았다. **자기신고는 VALORANT 하나가 남았다.** 같은 날 **실제 키로 불러 보고** LoL 의 리그를 `puuid` 로 부르게 고쳤다(소환사 응답에 `id` 가 없었다). Claude 가 정한 세부 · 확인됨 · 미확인은 계약의 P-36 행이다.

> **2026-09-29 소유자 결정 — LoL 전적은 최근 10판만 긁는다**(20판에서 줄였다 — "20판 긁어오는 거 10판만 긁어오도록 바꾸자". `contracts/platform-api.md` **P-13 의 개정** · "전적을 긁는 것"). 설정 `platform.riot.match-count` 의 기본값이 10 이 됐고 경로 · 본문 · 에러 코드는 그대로다.
> **왜** — Riot 개발용 키의 한도가 지역마다 2분에 100회인데 한 번에 **24번**(대륙 22 · 플랫폼 2)이라 2분에 **4명**이 한계였다 → **14번**(대륙 12 · 플랫폼 2) · **8명**. 평균 K/D/A · 모스트 챔피언 · 연승과 `stats.games` 가 최근 10판 기준이 되고, **승 · 패 · 승률은 그대로 솔로랭크 시즌 누적**(`league-v4`)이다.

> **2026-09-30 소유자 결정 — 모집 글에 방장 자신의 포지션(`hostPosition`)을 담는다**(`contracts/platform-api.md` **P-38** · "모집 글 · 목록" 의 "방장 포지션" · docs/11 **D-50** 으로 남겨야 한다 — `matching` 폴더의 일). P-35 가 "미정" 으로 남긴 칸이다.
> 소유자가 정한 것 셋 — ① 글에 저장하고 게시판에서 방장의 카드에 보여 준다(응답은 **글 한 줄의 `hostPosition`** — 카드의 모양은 그대로다) ② **포지션이 있는 모드면 필수이고 없는 모드에는 없다** ③ **찾는 포지션(`wantedPositions`)에 들 수 없다.** 컬럼은 마이그레이션 **V6**(`recruit_posts.host_position` — §3.5).
> **Claude 가 정한 세부**(검토 항목) — "포지션이 있는 모드" = 게임에 포지션이 있고 gameconfig 모드 HASH 의 **`positionUniqueness` 가 `"true"`**(이 앱이 그 필드를 읽기 시작했다 — §3.6 · 읽기만) · 거절은 전부 400 `VALIDATION_FAILED` 에 `"hostPosition: …"` 한 줄 · 포지션이 없는 모드에 값이 오면 조용히 버리지 않고 400 · **fail-open**(gameconfig 를 못 읽으면 필수도 거절도 없다) · `PATCH` 는 고친 뒤의 모양을 본다(포지션이 없는 모드로 바꾸면 비운다 · 비우는 길은 따로 없다). **게시판 방 먼저 합류(P-28)는 바뀌지 않았다** — 방장과 같은 포지션인 사람을 건너뛸지는 미정이다(§7.1).

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다 — "조건은 사용자가 정하고, 사람 선택은
시스템이 한다"(docs/00 §1). 이 서비스는 매칭의 **앞(계정)과 뒤(모집 글·확정된 파티·친구·차단·신고)**를 맡는다.
자동 매칭이 **기본 경로**이고 **파티 모집 게시판이 두 번째 경로**다 (docs/11 D-11). **이 서비스는 오래 남는 것(PostgreSQL)을
다루고, 지금 방에 누가 있는지처럼 금방 사라지는 것은 Redis 에 둔다 — 그 일(방 안의 일)은 이 앱의 `room` 패키지다**(2026-09-25 에 합쳤다 — docs/11 D-16 의 "`app:room`"은 D-33 이 개정했다).

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (public 하나 — 2026-09-26)
                                 │  ▲
                                 │  └── 읽기만 ◀── Redis qm:party:{partyId} ◀── matching 이 확정 때 쓴다   (자동 매칭 파티의 방을 만들 때 HGETALL — D-42 · §3.3. SQS 는 없다)
                                 │      (차단도 SQS 로 알리지 않는다 — matching 이 blocks 를 직접 읽는다)
                                 ├──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
                                 ├──── PUBLISH qm:pubsub:board (BOARD_CHANGED · {}) ──▶ Redis ──▶ notification ──SSE──▶ 모든 연결
                                 ├──── Lua 로 쓰고 읽는다 ──▶ Redis qm:room:{roomId}:host · :members · :confirmed · qm:user:active-room:{userId}
                                 │                              (방 안의 일 — 이 앱의 room 패키지. 입장 표시 키는 matching 이 EXISTS 로 본다 — §3.3)
                                 ├──── EXISTS 만 ◀── Redis qm:user:active-request:{userId}          (matching 이 쓴다. 대기와 방은 한 번에 하나 — D-19)
                                 ├──── 읽기만 ◀── Redis qm:gameconfig:{GAME}:{MODE} · :tier        (운영자가 seed 로 심는 공유 설정. mode · tier 검증 — §3.6)
                                 └──── 인가 코드 흐름 ──▶ 카카오 · 디스코드 · 구글   (소셜 로그인 — 회원 번호와 닉네임만 받는다)
```

- 지원 게임은 **LoL, VALORANT, PUBG 셋뿐**이다 (docs/11 #8). **상대팀/VS/대전 상대를 만들거나 보여주지 않는다** (#9).
- 공개 사용자 탐색, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다
  (`matching/CLAUDE.md` §1 · docs/11 #14 · docs/00 §6).
  - **예외 하나 — 파티 모집 게시판은 허용된다** (docs/11 D-11이 #14와 docs/00 §6의 "게시판/LFG 글 작성"을 개정했다).
    **글·목록·방장 확정과 방 안의 일이 전부 이 서비스다**(`party` · `room` 패키지 — 2026-09-25 에 합쳤다. D-16 은 D-33 이 개정했다). 정해진 것과 정할 것은 §7.1.
  - 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이다. 사람을 검색하고 둘러보는
    공개 사용자 탐색은 여전히 금지다.
  - "공개 채팅방"은 **파티 모집과 무관한 잡담용 공개방**을 뜻한다. 모집 글에 딸린 방은 그 예외다.
- 프리미엄/과금 기능을 구현하지 않는다. 친구 / 차단 / 최근 함께한 사람 / 신고는 필수다 (docs/11 #13·#14).
- **매칭 로직이 하나도 없다.** 매칭·제안·수락·확정은 전부 `matching`의 일이다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 출처 |
|---|---|
| **계정** — **가입 · 로그인은 소셜로만**(2026-09-26 소유자 결정 — P-24. 직접 가입 · 비밀번호 로그인은 없다), 로그아웃, 기본 프로필, 게임 계정 연결/해제. **인증 토큰 발급** — access는 **쿠키로 주고받는 JWT**, refresh는 **Redis에 저장하는 불투명 UUID**(재발급·폐기도 이 앱). **서명은 RS256이고 개인 키는 이 앱만 갖는다** — 옆 서비스(`matching` · `notification`)는 공개 키로 검증만 한다. **access는 15분이고 refresh는 7일이다**(2026-09-23 소유자 결정 — §5.1 (라) · (마)). 재발급은 `POST /api/v1/auth/refresh`다. **식별자는 사용자 번호 하나다(§3.5)** — `userId`는 DB가 매기는 **사용자 번호**(bigint identity — 2026-09-22 소유자 결정)이고 보여 주는 이름은 닉네임 하나다(2026-09-22에 따로 두었던 로그인 아이디 `loginId`는 2026-09-26 소유자 결정으로 없어졌다). **소셜 로그인(카카오 · 디스코드 — 2026-09-21 소유자 지시 · 구글 — 2026-09-29 소유자 결정 · P-33)** — 소셜로 **처음** 온 사람은 **닉네임만** 정하는 한 단계를 거친다(한 단계를 두는 방식은 Claude가 정했다. "닉네임만"은 2026-09-26 소유자 결정 — 전에는 로그인 아이디 · 닉네임이었다). **소셜 계정 잇기 · 끊기** — 로그인한 채 다른 제공자로 오면 같은 사용자에 잇고, `DELETE /api/v1/users/me/social/{provider}`로 끊는다(마지막 하나는 끊을 수 없다 — 2026-09-27 소유자 결정 · P-27 · **구현됐다**). **게임 프로필** — 게임 계정(**받는 칸이 게임마다 다르다** — LoL: 이름#태그 하나이고 티어는 Riot에서 채운다(2026-09-27 소유자 결정 · P-26) · VALORANT: 게임 닉네임 · 티어(자기신고) · PUBG: 게임 닉네임 · 서버이고 티어는 PUBG API 에서 채운다(2026-09-29 소유자 결정 · P-36). **티어는 사다리(랭크 큐)마다 따로다 — 응답은 `tiers`**(LoL `SOLO` · `FLEX` / VALORANT `COMPETITIVE` / PUBG `RANKED` — P-36). **주 포지션은 어느 게임에도 없다** — 2026-09-29 소유자 결정 · P-35. `mainPosition` 을 보내면 400)에 읽기 전용 `verified` · `stats`(전적 스냅숏)를 붙여 밖에 보여 주는 모양. `users/me`와 목록의 카드가 같이 쓴다. **구현됐다(2026-09-21. refresh는 2026-09-23)** — 단, 소셜 로그인은 **가짜 제공자로만 테스트했다** — **소셜이 유일한 로그인이 되어 실제 키 등록이 필수다**(§7 — 키가 없는 동안은 개발용 로그인을 켠다, §5.1 끝 · P-34). **LoL의 전적은 Riot API에서 긁는다**(2026-09-23 소유자 결정 — **한 시점은 게임 계정을 연결 · 수정할 때다.** ~~커밋된 뒤에 비동기로 돌고 실패해도 게임 계정 저장은 성공이다~~ → **2026-09-27부터 LoL은 저장하기 전에 동기로 긁고, 실패하면 저장하지 않는다**(P-26). **모집 글을 쓸 때도 긁던 것은 2026-09-24 소유자 결정으로 없앴다.** 같은 날 **전적 갱신**이 붙어 긁는 시점이 다시 둘이 됐다 — **`POST /api/v1/users/me/game-accounts/{game}/refresh`**(소유자 결정. **이쪽은 동기다** — 다 긁을 때까지 기다렸다가 갱신된 게임 프로필을 준다. 같은 게임 계정은 **2분에 한 번**이고 상한은 30초다. §7 "게임 계정 연동" · P-17). §7 "게임 계정 연동" · P-13). **PUBG의 전적 · 티어는 PUBG API 에서 긁는다**(2026-09-29 소유자 결정 — LoL 과 같은 길 · 같은 쿨타임 · 자물쇠 · 30초. §7 "게임 계정 연동" · P-36). **VALORANT의 `stats`는 아직 늘 `null`이고 `verified`를 켜는 길도 없다**(§7). **`tier`는 값이 있으면 그 게임의 gameconfig 티어 사다리에 있는 이름이어야 한다**(2026-09-24 소유자 결정 — §3.6. 요청으로 받는 것은 **VALORANT 뿐이다**(2026-09-29 — PUBG 도 API 로 바뀌었다) — LoL · PUBG는 게임사 API 의 티어를 그 사다리 이름으로 옮긴다) | docs/00 §5 · docs/11 #16 · D-14 · docs/AWS_ARCHITECTURE §3 · §5.1 · `contracts/platform-api.md` "계정" · "게임 프로필" · "소셜 로그인" |
| **파티** — 확정된 파티와 파티원의 기록. **자동 매칭 파티는 `matching` 의 Redis 파티 HASH `qm:party:{partyId}` 를 읽어 DB 에 만든다**(2026-09-27 소유자 결정 — docs/11 D-42. `ProposalConfirmed.fifo` 를 소비하던 원안은 없어졌다 — §3.4). 만드는 자리는 **`POST /api/v1/match-parties/{partyId}/room`** 이다 — 프런트가 `MATCH_CONFIRMED {partyId}` 를 받아 부르면 없으면 만들고(파티 + 방) 있으면 들여보낸다(P-30 · §3.3 · `contracts/platform-api.md` "자동 매칭 파티의 방"). **파티가 닫히면 파티원끼리 "최근 함께한 사람"(`recent_players`)에 적는다** — **게시판 파티는 확정된 방이 없어질 때 이 앱 안에서 닫는다**(2026-09-26 소유자 결정 — §3.3 "파티 닫힘" · P-25. SQS 를 거치지 않는다). `PartyClosed.fifo` 는 만들지 않는다 — 자동 매칭 파티도 같은 나가기 콜백으로 닫는다(2026-09-27 — D-42 · P-30). 파티룸의 **나가기·지금 누가 있나**는 이 앱의 `room` 패키지다(2026-09-25 합침 — D-16 은 D-33 이 개정했다). **구현된 것** — 방장 확정으로 생기는 파티(`parties`의 `source` = `BOARD`)와 파티원, 그 파티의 닫힘(2026-09-26), 그리고 **자동 매칭 파티(`source` = `MATCH` · `match_party_id` = `partyId`)와 그 방**(2026-09-27 — P-30. 소유자 검토 필요). **`ProposalConfirmed.fifo` · `PartyClosed.fifo` · outbox 테이블은 두지 않는다**(D-42 — §3.4) | docs/11 #21 · D-13 · D-16 · **D-42** · docs/00 §5 · `matching/CLAUDE.md` §9 · `contracts/platform-api.md` "방장 확정의 기록" · "파티 닫힘"(P-25) · "자동 매칭 파티의 방"(P-30) |
| **파티 모집 게시판(오래 남는 쪽)** — 모집 글 쓰기·수정, 게시판 목록, **차단 관계 거르기**(`blocks`가 같은 앱에 있다. **방 안의 누구와든** 본다 — D-20), 글의 상태(모집 중/확정/만료), **글 쓰기가 방을 같이 만든다**(2026-09-25 소유자 결정 C — 방을 못 만들면 글도 되돌린다. 이미 방에 있으면 글을 못 쓴다 · §3.3), **방장 확정의 기록 — 글의 상태를 "확정"으로 바꾸고 파티원을 기록한다**(`POST /api/v1/rooms/{roomId}/confirm` 한 요청이 방의 확정과 같이 한다 — §3.3), **입장의 글 검사**(글이 모집 중이고 **방 안의 누구와도** 차단 관계가 아닐 때만 들여보낸다 — 입장 요청 안에서 `PostEntryGate`가 본다, 소유자 결정 ① · §3.3). **목록의 한 줄은 이 앱이 전부 조립한다**(D-20) — 방 안에 몇 명인가, **방 안 사람들의 카드**(닉네임·티어·전적 등 — 프로필은 이 앱의 DB에 있다. **주 포지션은 2026-09-29 에 카드에서 빠졌다** — 게임 계정에 없다, P-35. 입장할 때 포지션을 고르지 않는다). 목록·입장·**고치기** 때 방 안을 `RoomService#states`로 **읽는다**(§3.3). **목록의 한 줄은 게임마다 다른 정보를 보여 준다**(2026-09-21 소유자 지시 — 본보기는 OP.GG의 듀오 찾기. §7.1). **구현됐다(2026-09-21 · 2026-09-25 에 합치기 2단계로 고쳤다)** — 글 쓰기(방까지) · 고치기 · 지우기(만료로 바꾸고 방도 닫는다 — 2026-09-25 소유자 결정 "방과 글은 같이 산다") · 목록 · 단건 · 입장 검사 · 방장 확정의 기록. **글의 `mode`는 필수가 됐고 그 게임의 gameconfig에 있는 모드여야 한다**(2026-09-24 소유자 결정 — §3.6). **글의 "찾는 포지션" 가운데 이미 방 안에 채워진 것을 강조하던 `filledPositions`는 없앴다**(2026-09-24 소유자 결정 — §7.1 · P-18. **docs/11 D-20의 ③을 개정한다** — 카드의 주 포지션은 그때 그대로 두었다가 2026-09-29 에 칸째 없어졌다, P-35). **방에 방장 말고 누가 있으면 글을 고칠 수 없다**(2026-09-24 소유자 결정 — §7.1 · P-19. 409 `ROOM_HAS_OTHER_MEMBERS` · 방 안을 못 읽으면 503. **막는 것은 `PATCH` 하나다** — 지우기·입장·확정·조회는 그대로다). **목록은 글을 상태로 가리지 않는다 — 끝난 글도 계속 남는다**(2026-09-25 소유자 결정 — §7.1 · P-20. 만료 · 확정된 글을 10분만 보여 주던 보존 기간을 없앴다). **목록의 `game`은 필수다**(2026-09-25 소유자 결정 — §7.1 · P-21. **게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다.** **목록 하나에만 걸린다** — 단건 · 입장 · 확정 · 글 쓰기 · 고치기 · 지우기는 그대로다) | docs/11 D-11 · D-16 · **D-20** · **D-21** · `contracts/platform-api.md` "모집 글 · 목록" · P-22 |
| **방 안의 일**(2026-09-25 에 `room` 앱을 합쳤다 — 소유자 결정 · P-22) — 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인과 방장 이탈 감지 · 방장 승계(확정한 방 — D-23) · 방 안 사람 목록 · 내 방 찾기, 정원 5명 검사, 입장 표시 키 쓰고 지우기(활성 요청 키는 `EXISTS`로 보기만 한다 — D-19), 방 알림(`ROOM_*`) 발행, 시그널 `POST` 받기와 `WEBRTC_SIGNAL` 발행. **이 앱의 `room` 패키지**가 한다 — 금방 사라지는 상태라 Redis에만 둔다(DB가 없다). **방을 바꾸려면 Lua 스크립트를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거친다** — 정원 검사 · 입장 표시 키 · 활성 요청 키의 `EXISTS`가 한 스크립트 안에 있어서다. 방 만들기 요청은 따로 없다 — 글 쓰기가 만든다. 글(`party`)과 맞물리는 법은 §3.3 | docs/11 D-9 · D-11 · D-16 · D-19 ~ D-23(전부 두 앱을 전제로 쓰였다 — D-33 이 개정했다) · D-33 · `contracts/platform-api.md` "방" · P-22 |
| **소셜** — 친구 요청/수락/거절/삭제, 차단/해제, 신고, 최근 함께한 사람. **`blocks`의 소유자**다. 차단은 **DB에 저장하는 것으로 끝낸다** — `BlockChanged.fifo`는 만들지 않는다(§3.4). **차단은 구현됐다(2026-09-21)** — `blocks` · API 셋(롤 · `GRANT`는 없다 — §3.5). **친구(요청 · 수락 · 거절 · 거두기 · 목록 · 끊기) · 신고(접수만) · 최근 함께한 사람(읽기)도 같은 날 들어왔다**(옛 `social/V6__friends_reports_recent_players.sql` — 지금은 `V1__schema.sql`에 합쳤다. 통과 여부는 `START_HERE.md` §1). **최근 함께한 사람은 확정된 파티가 닫힐 때 채워진다**(2026-09-26 소유자 결정 — §3.3 "파티 닫힘" · P-25. 그 전에는 채우는 주체가 없어 늘 빈 목록이었다. **자동 매칭 파티도 2026-09-27 부터 같은 길로 닫힐 때 채워진다**(P-30 — 전원이 말없이 사라진 자동 매칭 파티는 그 게임의 목록 GET 이 방 키를 읽어 닫는다, 2026-09-28 소유자 결정 — §3.3)). **친구 목록은 사람을 찾아보는 기능이 아니다** — 상대의 사용자 번호를 정확히 알아야 요청을 보낼 수 있고 검색 API는 만들지 않는다(§1) | docs/00 §5 · D-1 · D-2 · D-12 · `contracts/platform-api.md` "차단" · "친구 · 신고 · 최근 함께한 사람" |
| **알림 발행** — ~~`PARTY_*`·`FRIEND_*` 7종~~ → `FRIEND_*` 2종(`PARTY_*` 는 2026-09-28 소유자 결정으로 두지 않는다 — P-31). **`FRIEND_*` 둘의 이름과 `payload`가 정해졌다** — `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`(`contracts/platform-api.md` "이 앱이 내는 알림"). **이 둘의 발행은 구현됐다**(2026-09-21 — `common/push/`). **`PARTY_*` 는 두지 않는다**(2026-09-28 소유자 결정 — P-31 · docs/11 D-44). 자동 매칭 파티가 생기는 것도 닫히는 것도 알리지 않는다 — 생기는 계기는 프런트가 `MATCH_CONFIRMED` 를 받아 스스로 방을 부르는 것이고 방 안의 일은 `ROOM_*` 가 맡는다. **방 입장·퇴장·방 닫힘·강퇴·방장 확정 알림(`ROOM_CONFIRMED`)과 `WEBRTC_SIGNAL`은 이 앱의 `room` 패키지가 발행한다**(2026-09-25 합침) — `PARTY_*`를 다시 쓰지 않고 새 이름(`ROOM_MEMBER_ENTERED` 등)을 지었다(§3.3 · `contracts/platform-api.md` "방" 의 "알림"). **게시판 채널에 "바뀌었다" 신호 발행** — 글이 생기거나 사라지거나 상태가 바뀔 때(§3.2 "게시판 채널" — 채널 `qm:pubsub:board`(게임을 구분하지 않는 하나), `type`은 `BOARD_CHANGED`, `payload`는 `{}`. **구현됐다(2026-09-21)** — 글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정되면 커밋 뒤에 발행하고, 방 쪽은 인원이 바뀔 때 발행한다, D-20 · D-22 · D-23) | contracts/events.md · docs/11 D-16 · D-20 · D-21 · D-22 · `contracts/platform-api.md` "이 앱이 내는 알림" · "방" 의 "알림" |

platform 소관 자원(contracts/openapi.yaml 머리말): `auth` `users` `parties` `friends` `blocks` `recent-players` `reports`. **경로와 스키마는 거기 없다**(§3.1) — **이 폴더에서 정한 것이 `contracts/platform-api.md`에 있다**(`parties` 자원은 아직 없다 — 글은 `posts`, 친구 요청은 `friend-requests`라는 이름을 새로 지었다). 머리말은 `reservations`도 platform 소관으로 적지만 D-15로 `app:reservation`의 것이 됐다.

| 안 한다 | 왜 / 누가 |
|---|---|
| 매칭·제안·수락·확정, 매칭 Redis 키(`qm:party:*` `qm:user:*` `qm:proposal:*` `qm:lock:*`) 접근. **예외 둘(아래) 말고는 없다** — "한 번에 하나만"은 키 둘로 지키고 활성 요청 키(`qm:user:active-request:{userId}`)는 `matching`만, 입장 표시 키(`qm:user:active-room:{userId}`)는 **이 앱**만 쓰고 지우며 서로 상대 키를 `EXISTS`로만 본다(D-19가 D-11 16번과 D-16의 해당 대목을 개정. 입장 표시 키의 주인은 2026-09-25 에 `room` 을 합쳐 이 앱이 됐다 — **활성 요청 키는 이 앱이 쓰지 않는다**). **예외가 둘이다** — ① **`qm:gameconfig:*` 읽기**(2026-09-24 소유자 결정 · docs/11 D-29 — §3.6. 운영자가 배포 때 심는 공유 설정이고 쓰는 앱이 없다 — `matching`도 읽는 쪽이다) ② **`qm:party:{partyId}` 읽기**(2026-09-27 소유자 결정 · docs/11 D-42 — 확정된 자동 매칭 파티를 방으로 만들 때 `HGETALL` · `HEXISTS` 만. **쓰지도 지우지도 `EXPIRE`를 걸지도 않는다** — §3.3 · §3.4 · `contracts/platform-api.md` "자동 매칭 파티의 방"). **`qm:user:*`(활성 요청 키) · `qm:proposal:*` · `qm:lock:*` 에는 "예외가 없다"가 그대로다.** | `matching`의 일이다. 진행 중 매칭 상태의 원본은 Redis이고 그 주인은 `matching`이다 (docs/11 #27) |
| 브라우저 연결 보유 — `SseEmitter` / WebSocket | `notification`의 일이다. 이 앱은 **stateless REST**로 남아야 무중단 교체가 자유롭다 (docs/11 #15 · docs/14 §6). WebSocket은 어느 앱에도 없다 (D-9) |
| **예약 전부** — 예약 REST(`/api/v1/reservations` 등록·조회·수정·취소, INV-9 검증), 짝 찾기 배치, `RESERVATION_*` 발행 | **`app:reservation`(AWS Lambda)** 의 일이다 (docs/11 D-15 — #24의 "예약 REST는 `app:platform`"과 `app:reservation-batch`를 대체한다). 예약은 이 앱의 다른 모듈과 같은 트랜잭션으로 묶일 일이 없다 |
| TURN 단기 credential 발급, 음성·텍스트 채팅 중계/저장 | TURN은 Cloudflare 관리형이고 발급 주체는 `app:realtime`이다 (docs/11 #25). 음성·텍스트는 브라우저 직결(WebRTC audio + DataChannel)이라 서버를 거치지 않는다 (#6). (참고: 지금은 공개 STUN만으로 개발을 시작한다) |
| gameconfig(게임 모드 설정) **모듈** — 모드 설정을 정하고 · 심고 · 해석하는 것(`targetPartySize` · `tierRule` · 티어별 허용 범위) | `app:matching`의 모듈이다 (docs/11 #15). **이 앱은 그 값이 있는지만 읽는다**(2026-09-24 소유자 결정 — §3.6): 모드별 설정 HASH는 `EXISTS`, 티어 사다리는 `ZSCORE`이고 **seed를 심지 않는다.** 2026-09-28 부터 게시판 방 먼저 합류(P-28)가 모드 HASH 의 `tierRule` · `targetPartySize`(2026-09-29 부터 `tierLadder` 도 — P-36) · 사다리 score · `:tier-range:` 를 **읽기만** 한다 — 값의 뜻을 정하거나 해석 규칙을 갖는 것이 아니다(§3.6). 모듈을 갖는 것이 아니다 |

## 3. 계약

### 3.1 Contract first — platform 계약의 원본은 이 컴퓨터에 없고, 여기서 정한 것은 `contracts/platform-api.md`에 있다

- 계약 원본은 **queueMate 본 저장소(`feature/frontend` 브랜치)의 `contracts/`**이고 **이 컴퓨터에 없다.** `matching/contracts/`는
  `matching`이 노출하는 부분만의 발췌 사본이라 **platform 엔드포인트가 통째로 빠져 있다** (contracts/openapi.yaml 머리말).
- 그러므로 **경로·요청/응답 스키마·에러 코드·payload 필드를 지어내지 마라.** 사용자에게 묻는다.
  원본을 받아 올 수 있으면 그것이 먼저다.
- 여기서 정한 것은 **이 폴더의 `contracts/`**에 적고 "원본에 올려야 할 것" 표를 같이
  남긴다. 본보기는 contracts/README.md의 "이 사본이 원본보다 앞서간 변경"(A-1~A-4) 표다.
- **이제 `contracts/platform-api.md`가 있다(2026-09-21).** 공통(에러 본문 · 인증 · `Origin` 검사 · access 토큰) · 계정 · 게임 프로필 · 소셜 로그인 · 차단 · 모집 글/목록(글 쓰기가 방을 만든다 · 만료) · **방**(입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 시그널 · 방 알림 · Redis 키 — 2026-09-25 에 옛 `room` 의 계약을 합쳤다) ·
  친구/신고/최근 함께한 사람 · 이 앱이 내는 알림 · **refresh 토큰** · **gameconfig를 읽는 것**(§3.6), **자동 매칭 파티의 방**(`POST /api/v1/match-parties/{partyId}/room` · 파티 HASH 의 필드 계약 — 2026-09-27), 그리고 맨 아래 **"원본에 올려야 할 것" 표(P-1~P-30)**다.
  **지위** — 소유자가 맡겨 Claude가 정해 구현한 것이고 **소유자가 아직 항목별로 검토하지 않았다**(그 파일 머리의 "지위"). **원본에 platform 엔드포인트가 이미 있으면 그쪽과 맞춰야 한다**(P-1 — 이 컴퓨터에서는 볼 수 없었다).
- **코드와 그 파일은 같이 바뀐다** — 경로 · 스키마 · 에러 코드 · 클레임을 바꾸면 같은 작업에서 `contracts/platform-api.md`를 고친다(커밋은 나눈다 — §8). 거기 없는 것을 새로 정할 때는 **여전히 먼저 묻는다.**
- **에러 본문은 `matching`과 같다** — `{"code", "message", "details": [문자열]}`(`contracts/platform-api.md` "공통"). **방의 요청도 같은 코드 한 벌이다**(2026-09-25 2단계 — `INVALID_REQUEST` → `VALIDATION_FAILED`, `ROOM_UNAVAILABLE` → `ROOM_STATE_UNAVAILABLE`. 프런트가 없어 호환은 두지 않았다 — `contracts/platform-api.md` "방" 의 "공통 에러").

### 3.2 알림 발행 — `matching`의 `PushPublisher`와 같은 방식

Redis `PUBLISH qm:pubsub:push:{userId}`에 **JSON 문자열 하나**를 보낸다. `notification`은 열어 보지
않고 SSE `data:`에 그대로 싣는다 — **새 종류를 추가해도 `notification`은 재배포하지 않는다.**

| 항목 | 값 | 원본 (`matching` 기준) |
|---|---|---|
| 채널 | `qm:pubsub:push:{userId}` — **`{userId}`는 사용자 번호다**(`qm:pubsub:push:42`. 2026-09-22 — §3.5. `notification`은 access 토큰의 `sub`로 채널을 여므로 같은 글자가 된다) | `redisKeys/SharedKeys.java`의 `PUSH_CHANNEL_PREFIX` + `pushChannel(userId)` |
| 봉투 | `{type, eventId, occurredAt, payload}` 네 칸 고정 | `notification/PushPublisher.java`의 `record Envelope` · contracts/events.md "Envelope" |
| `eventId` | 매번 새 UUID 문자열 (SSE `id:`가 된다. 클라이언트 중복 제거용) | `PushPublisher#publish()` |
| `occurredAt` | ISO-8601 UTC, 밀리초 (`Instant.truncatedTo(MILLIS)`) | 같음 |
| `payload` | 객체. 담을 것이 없어도 `null`이 아니라 `{}` | 같음 |

> **채널 접두사는 이 서비스가 정하지 않는다.** 원본은 `matching`의 `SharedKeys.PUSH_CHANNEL_PREFIX`다.
> 따로 바꾸거나 오타를 내면 **컴파일도 테스트도 통과한 채로 알림이 전부 끊긴다** — 발행 쪽은 구독자
> 0명을 실패로 보지 않기 때문이다. 이 서비스에서도 접두사는 **상수 한 곳에만** 둔다 — **`common/push/PushChannels.PUSH_CHANNEL_PREFIX`**다(2026-09-21. 봉투는 `PushEnvelope`, `type`은 enum `PushEventType`, 발행은 `PushPublisher` — 게시판 채널 신호도 같은 봉투를 쓴다).

- **발행 실패가 본 작업을 뒤집으면 안 된다.** 발행은 어떤 예외도 밖으로 내보내지 않는다 — 알림은 휘발성이고
  놓치면 클라이언트가 REST 재조회로 복구한다(`PushPublisher#publish()` 주석). 대가로 발행이 틀려도 조용하니
  **구독해서 확인하는 테스트**를 둔다. `type`은 오타를 컴파일에서 막도록 enum으로 다룬다(`PushEventType.java` 주석).
- **7종의 이름과 15종 전부의 `payload` 스키마는 이 컴퓨터의 `matching` 쪽 문서에 없다** (contracts/events.md "미해결 계약 구멍"). **그 가운데 둘을 이 폴더에서 정했다** —
  `FRIEND_REQUEST_RECEIVED`(`payload` `{requestId, fromUserId}`) · `FRIEND_REQUEST_ACCEPTED`(`payload` `{requestId, userId}`). 커밋된 뒤에 발행하고, 닉네임 같은 데이터는 싣지 않는다(`contracts/platform-api.md` "이 앱이 내는 알림" · P-9 — 원본 `events.md`의 `FRIEND_*` 이름과 맞춰야 한다).
  **거절 · 거두기 · 친구 끊기 · 차단은 알리지 않는다. 나머지 다섯(`PARTY_*`)은 두지 않는다**(2026-09-28 소유자 결정 — P-31).
- 클라이언트는 알림을 "다시 조회하라"는 신호로 다룬다(contracts/events.md "순서 보장 범위"). 그러므로
  알림이 가리키는 상태는 **REST로 조회할 수 있어야 한다.**

**게시판 채널 — 목록을 F5 없이 갱신하는 신호 (docs/11 D-20 · D-22 · D-23. **구현됐다** — `contracts/platform-api.md` "모집 글 · 목록" · "방" 의 "게시판 채널 신호").**

- 위 알림은 사용자 한 명의 채널로 간다. 게시판 목록을 보는 사람은 **"누구인지 모르는 다수"**라서 사용자 채널로는 보낼 수 없다. 그래서 **게시판 채널**을 새로 둔다.
  **채널은 `qm:pubsub:board`** — **게임을 구분하지 않는 하나다**(D-22가 D-20의 게임별 세 채널 `qm:pubsub:board:{game}`을 고쳤다). **`type`은 `BOARD_CHANGED`**, 봉투 네 칸은 위와 같고 **`payload`는 빈 객체 `{}`**다.
  **글 쪽(`party`)과 방 쪽(`room`) 둘 다 같은 채널 하나에 `{}`를 발행한다 — 발행하는 쪽이 게임을 알 필요가 없다**(발행기는 `party/board/BoardSignalPublisher` 하나다).
  글 쪽은 **글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정될 때**(**트랜잭션이 커밋된 뒤에** 한 번 — 글과 방이 같이 생기는 글 쓰기도 한 번이다. 목록 조회가 글을 만료 · 확정으로 옮겨 적을 때도 나간다), 방 쪽은 **방의 인원이 바뀔 때** 그 채널에 발행한다. `notification`이 그 채널을 구독해
  **살아 있는 모든 SSE 연결에** 그대로 흘려보낸다(`notification` 쪽은 구현됐다. **`topics` 파라미터는 없앴다 — D-22.** 서버에서 거르지 않는다).
  **거르는 것은 클라이언트다** — 게시판 페이지(어느 게임이든)를 보고 있으면 목록을 다시 요청하고, 게시판 페이지가 아니면 무시한다. 다른 게임의 방이 바뀐 신호에도 재요청이 나가므로 프런트가 재요청을 묶는다(간격은 미정).
  **이 신호는 "다시 받아라"일 뿐이다** — 받은 프런트가 **이 앱의 목록을 `GET`으로 다시 요청**하고(몇 초에 한 번으로 묶어서), 데이터와 차단 거르기는 그 응답에서 온다.
- **신호에는 데이터를 싣지 않는다.** 방송에 데이터를 실으면 사람별로 거를 수 없다 — 차단(§7.1)이 성립하지 않는다. 데이터는 거르는 곳인 **이 앱의 목록 조회**에서만 나간다.
  **`roomId`도 `game`도 싣지 않는다** — "뭔가 바뀌었다"만 보낸다. `roomId`는 나에게 숨겨진 방이 바뀌었다는 사실이 새어 나가지 않게 하려는 것이다.
- **왜 서버에서 거르지 않나(D-22).** 거르려면 `notification`이 연결마다 주제를 기억하고 `topics` 파라미터·주제→연결 맵·표기 규칙을 가져야 한다. 클라이언트가 무시하면 전부 필요 없다.
  대가는 둘이다 — 다른 게임의 변화에도 재요청이 나가고(묶기로 상한을 둔다), 게시판을 안 보는 연결(방 안에서 음성 중인 사람 등)에도 작은 이벤트가 간다. MVP 규모에서 받아들인다.
  **다시 볼 조건** — 부담이 되면 `payload`에 `game`을 싣거나(그러면 방 쪽이 방의 게임을 알아야 한다) `topics`를 되살린다. `payload`에 `game`을 싣는 안은 같은 날 먼저 정했다가 물렸다.
- **채널 이름은 위 채널 접두사와 같은 위험이다** — 발행하는 앱(이 앱)과 구독하는 앱(`notification`)이 어긋나도 컴파일·테스트가 통과한 채로 목록이 조용히 갱신되지 않는다.
  상수 한 곳에만 두고 원본이 어디인지 주석에 적는다(**원본 상수를 어느 서비스에 둘지는 미정**이다 — 지금은 **두 앱이 각자 같은 값을 적어 두었다**: 이 앱의 `party/board/BoardChannels.BOARD_CHANNEL`, `notification`의 `redisKeys/BoardChannels.BOARD_CHANNEL`. **두 값이 같아야 한다.** 옛 `room` 앱의 사본은 합치며 없어졌다). **발행 실패가 본 작업(글 쓰기 등)을 뒤집으면 안 된다**는 규칙도 위와 같다 — **구독해서 확인하는 테스트**가 있다(`BoardSignalTest`).
- **미정** — 채널 이름의 **원본 상수를 어느 서비스에 둘지**(알림 채널 접두사는 `matching`의 `SharedKeys`가 원본이다 — 같은 방식으로 갈지 정해지지 않았다), 프런트가 재요청을
  묶는 간격 → §7.1(`topics` 표기와 "방 쪽이 어느 게임의 채널에 발행할지를 어떻게 아는가"의 미정은 D-22로 물음째 없어졌다). **지어내지 마라.**
- **게시판은 실시간으로 바뀌어야 한다**(인원 · 새 글 — 2026-09-21 소유자 지시) — 이 신호로 한다. (신호 없이 프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다고 적어 두었던 것은, 발행과 `notification`의 구독이 다 된 지금은 필요 없다.)

### 3.3 방 안의 일(`room` 패키지)과 글(`party` 패키지)이 맞물리는 법 (2026-09-25 합치기 2단계 · `contracts/platform-api.md` "모집 글 · 목록" · "방" · P-22)

> **이 절의 모양은 2026-09-25 에 정해졌다.** 합치기(소유자 결정)와 2단계의 소유자 결정 둘 — **① 입장 경로(`POST /api/v1/rooms/{roomId}/members`)는 그대로 두고 그 안에서 글을 검사한다** ·
> **C 글 쓰기가 방을 같이 만들고, 방을 못 만들면 글도 되돌린다.** 창구의 이름 · 검사 순서 · 자가 치유 · 에러 코드 통일 · 트랜잭션 안 Lua 예외는 **Claude 가 정했고 소유자가 항목별로 검토하지 않았다**(P-22).
> **docs/11 의 D-16 · D-19 ~ D-23 은 두 앱을 전제로 쓰였다 — 2026-09-26 에 docs/11 D-33 이 개정했다.**

**두 패키지는 서비스 메서드로 맞물린다 — 도메인 사이의 "읽는 창구"(§4) 규칙 그대로다**(방은 Redis 에 있어 JOIN 으로 대신할 수 없다 — 2026-09-26 에 JOIN 이 풀린 뒤에도 이 둘은 창구다). `party` → `room`: `RoomService#create`(글 쓰기) · `RoomService#confirm`(방장 확정) · `RoomService#states`(방 안 읽기). `room` → `party`: `party/service/PostEntryGate#check`(입장 검사) 하나.
`PostEntryGate`는 `PostService`를 물지 않는 따로 선 빈이다 — `PostService`가 `RoomService`를 물고 `RoomMemberService`가 `PostEntryGate`를 물어서, 한쪽이 거꾸로 물면 빈 순환이 생긴다. **`RoomService`는 `party`를 부르지 않는다.**

- **`roomId`는 글의 id다**(`recruit_posts.id` — **bigint identity. DB가 매긴다**. 방 키에는 **숫자가 십진 문자열로** 들어간다 — `qm:room:123:host`. P-4).
  **자동 매칭 파티의 `roomId` = `partyId`(`matching` 이 만든 UUID 문자열)다**(2026-09-27 — P-30 · `contracts/platform-api.md` "자동 매칭 파티의 방"). 방 키에 UUID 가 그대로 들어간다(`qm:room:3f2a…:host`) — 방 키 · 방의 요청(접속 확인 · 나가기 · 강퇴 · 목록 · 시그널 · 내 방 찾기)은 게시판 방과 같은 것을 쓴다. DB 에서는 `parties.match_party_id`(varchar(36) UNIQUE — V2)에 담고 `parties.id` 는 그대로 bigint 다. 방은 **`POST /api/v1/match-parties/{partyId}/room`** 이 만든다(아래).
- **글 쓰기 = 방 만들기**(소유자 결정 C). `POST /api/v1/posts` 한 요청이 트랜잭션 안에서 글을 INSERT(여기서 번호 = `roomId`)하고 **커밋 전에** 방 만들기 Lua(`RoomService#create`)를 부른 뒤 커밋한다(`party/service/PostStore#create`). 쓴 사람이 방장이고 곧바로 방에 들어와 있다 — 응답의 `members`에 방장이 있다.
  **Lua가 거절하면 그 코드로 409이고 글은 되돌려진다** — 409 `ALREADY_QUEUED`(자동 매칭 중) · 409 `IN_OTHER_ROOM`(이미 다른 방에 있다 — **그래서 "이미 방에 있으면 글을 못 쓴다"**) · 409 `ROOM_ALREADY_EXISTS`(정상이면 나지 않는다). Redis에 못 닿으면 503 `ROOM_STATE_UNAVAILABLE`이고 역시 되돌린다.
  **Lua 성공 뒤 커밋이 실패하면 방이 Redis에 고아로 남는다 — 감수한다**(수명 600초로 죽고, 그동안 나가기로 풀 수 있고, 글이 없어 목록 · 입장에 걸리지 않는다).
  **방 만들기 HTTP 요청(`POST /api/v1/rooms/{roomId}`)은 없어졌다.** **자동 매칭 파티의 방은 `POST /api/v1/match-parties/{partyId}/room` 이 만든다**(2026-09-27 — docs/11 D-42 위에 Claude 가 정한 세부 · P-30 · 계약 "자동 매칭 파티의 방"). 프런트가 `MATCH_CONFIRMED {partyId}` 를 받아 부르면 이 앱이 `matching` 의 파티 HASH `qm:party:{partyId}` 를 읽어(`status == CONFIRMED` · 부른 사람이 `member:{userId}` 에 있는가 — `HEXISTS`) **없으면 만들고 있으면 들여보낸다**(201/200 `{roomId}`). **이미 이 방에 들어와 있는 사람은 입장 표시 키로 먼저 판정해 HASH 가 수명(600초)으로 사라진 뒤에도 200 이고, HASH 는 방에 없는 사람의 자격에만 쓴다**(2026-09-28 — 소유자 지적 "방에 있는 사람은 방 키가 원본". 순서는 Lua 머리 · 계약 그 절). **처음부터 확정된 방**이라(`:confirmed` 를 만들 때 같이 쓴다) D-23 의 승계가 걸리고, **처음 부른 사람이 방장**이며(`matching` 에는 방장이 없다) 정원은 HASH 의 `target` 이다. 글이 없어 **게시판 신호를 내지 않는다.** 전용 Lua `enter-match-room.lua` 는 **활성 요청 키를 보지 않는다** — 확정 뒤 60초 동안 `status=PARTY` 로 남는 그 키가 곧 이 파티라서다(읽지도 않으므로 D-19 의 "`EXISTS` 만" 은 그대로다). **DB(`parties` `source='MATCH'` · `match_party_id` · `party_members` = HASH 의 `member:*` 가운데 `users` 에 있는 번호, `is_host` 는 처음 부른 사람)를 먼저 커밋하고 Lua 는 트랜잭션 밖에서 돈다** — 아래 "예외가 둘" 을 늘리지 않는다. 파티는 `matching` 이 이미 확정한 사실이라 Lua 가 실패해 파티 줄만 남아도 틀린 것이 아니다(다음 호출이 방을 만든다 — `ON CONFLICT (match_party_id) DO NOTHING`). **HASH 를 못 읽으면 503 `ROOM_STATE_UNAVAILABLE`(fail-closed)** — gameconfig 의 fail-open 과 다르다. 세부와 에러 코드는 계약 그 절.
- **"트랜잭션 안에서 Redis를 기다리지 않는다"의 예외가 둘이다** — 글 쓰기와 방장 확정. 둘 다 **Lua 한 번(밀리초)**이고 글과 방이 한쪽만 남지 않게 하려는 것이다. 그 밖(목록 · 단건 · 고치기 · 입장 검사)은 여전히 트랜잭션 밖에서 방 안을 읽는다(`PostService`와 `PostStore`를 나눈 이유).
- **입장의 검사 순서**(소유자 결정 ①). `POST /api/v1/rooms/{roomId}/members` 안에서 ① **글의 검사**(`PostEntryGate#check`) — 글이 없거나 **차단으로 숨겨진 글이면 404 `POST_NOT_FOUND`**(**상태보다 먼저** — 차단 관계인 사람에게는 "모집이 끝났다"도 알려 주지 않는다) → **모집 중이 아니면 409 `POST_NOT_RECRUITING`** → 방 안을 못 읽으면 503 `ROOM_STATE_UNAVAILABLE` →
  ② **방의 Lua**(`ROOM_NOT_FOUND` · `ROOM_FULL` · `ROOM_CONFIRMED` · `ALREADY_QUEUED` · `IN_OTHER_ROOM` · 이미 들어와 있음 200 — 원본 그대로. **2026-09-29 부터 입장 금지 목록 `qm:room:no-entry:{userId}` 를 맨 먼저 본다 — 강퇴당한 지 10분이 안 된 방이면 403 `KICKED_RECENTLY`**(소유자 결정 · P-32. 스스로 나간 사람은 걸리지 않는다 — 아래 방 키 표)).
  **차단 대조는 방장 + 그 순간 방 안 전원이다**(D-20 그대로 — 목록에서만 숨기고 입장은 되면 의미가 없다. 목록의 숨김과 같은 판정 `PostService#isHidden`을 쓴다). **이미 그 방에 들어와 있는 사람은 ① 을 통과한다**(재시도가 200이 되게 — Claude가 정한 세부).
  **① 과 ② 사이는 원자적이지 않다** — 그 사이에 나와 차단 관계인 사람이 먼저 들어오는 경쟁이 남는다(창은 밀리초다 — §7.1).
- **방장 확정은 한 길이다.** `POST /api/v1/rooms/{roomId}/confirm` 하나가 글의 줄을 `FOR UPDATE`로 잠그고 → 확정 Lua(`RoomService#confirm` — 방장만 · 2명 이상 · **되돌릴 수 없다**) → 성공하면 **같은 트랜잭션에서** 글을 `CONFIRMED`로, `parties`(`source` = `BOARD` · `post_id` = 글의 id)와 `party_members`(Lua가 돌려준 **확정 순간의 전원**)를 적는다(`PostStore#confirmRoom`). Lua가 거절하면 되돌린다.
  **멱등은 `UNIQUE (post_id)`가 지킨다** — `INSERT … ON CONFLICT (post_id) DO NOTHING`(`party/repository/PartyRecordRepository`) · 글을 바꾸는 조건부 UPDATE가 한 호출만 통과시킨다. 파티원의 `is_host`는 방장 키의 값이 아니라 **글의 `hostId`**로 정한다(D-23).
  만료된 글의 방은 확정하지 않는다(409 `POST_NOT_RECRUITING` — 파티를 적을 글이 없다). 확정 뒤에는 방의 Lua가 새 입장을 409 `ROOM_CONFIRMED`로 거절한다 — 확정과 입장이 둘 다 Lua라 확정 순간에 끼어들 수 없다.
  **`POST /api/v1/posts/{postId}/confirm`(두 앱이던 때의 "길 ①")은 없어졌다.**
- **자가 치유 — Lua 성공 뒤 커밋이 실패하면** "확정된 방인데 글은 모집 중"이 남는다. **목록 · 단건이 방 안을 읽다 "DB에는 모집 중인데 확정 표시 키가 있는 글"을 보면 그 자리에서 같은 기록을 한다**(두 앱이던 때의 "길 ②"가 이 자리로 남았다). 같은 확정을 다시 눌러도(Lua는 "이미 확정" 200) 그때 글이 모집 중이면 기록한다.
  자가 치유가 읽는 멤버 SET은 확정한 그 순간과 다를 수 있다 — **감수한다**(커밋 실패가 드물고, 확정 순간의 파티원은 `ROOM_CONFIRMED` 알림으로 이미 나갔다).
- **방과 글은 같이 산다**(2026-09-25 **소유자 결정**) — 확정 전에는 **방장이 나가면 글도 그 자리에서 `EXPIRED`**, **방장이 글을 지우면(`DELETE /api/v1/posts/{postId}`) 방도 닫힌다**(`ROOM_CLOSED` · 남아 있던 전원의 입장 표시 키가 지워진다). 확정 뒤에는 글은 `CONFIRMED` 고정 · 방은 승계(D-23). 구현 — `RoomService#leave(roomId, userId, whenClosed)` 하나가 두 길을 다 맡고(`leave-room.lua` 재사용), 방장 나가기의 콜백이 `party/service/PostLifecycle#expireByRoomClosed` 를 부른다. 지우기는 **만료가 먼저 · 방 닫기가 뒤**이고 방 닫기가 실패해도 204 다(계약 "방과 글은 같이 산다").
- **만료 판정 — 모집 중인 글에 방장 키가 없으면 무조건 만료다**(위의 즉시 만료가 닿지 않는 경우 — 방장이 말없이 사라져 방의 수명이 다한 경우 — 의 받침이다). 글이 있으면 그 방은 한 번은 있었으므로 "아직 안 만들어진 방"이 없다. 확정 표시 키가 있으면 방장 키가 없어도 만료가 아니라 확정으로 기록한다(위 자가 치유). **확정된 글은 방장 키가 없어도 만료시키지 않는다 — 끝까지 `CONFIRMED`다**(확정한 방은 방장 키만 잠깐 없을 수 있다 — D-23).
  두 앱이던 때 "아직 안 만들어진 방"을 가르던 칸과 10분 유예 설정은 없어졌다(옛 마이그레이션 V8 — 지금은 `V1__schema.sql`에 합쳤다, §3.5). **Redis를 못 읽으면 판정하지 않는다** — 못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다.
- **파티 닫힘 — 확정된 방이 없어지면 파티가 닫힌다**(2026-09-26 **소유자 결정** · `contracts/platform-api.md` "방" 의 "파티 닫힘" · P-25). `parties.status = 'CLOSED'` · `closed_at`을 적고, **그 순간 `party_members`의 사람끼리 서로를 `recent_players`(최근 함께한 사람)에 적는다**(방향마다 한 줄 · 다시 만나면 시각만 갱신 — UPSERT).
  **길이 둘이다** — ① 마지막 사람이 나가기를 눌러 방 키가 지워질 때 그 자리에서 ② 전원이 말없이 사라져 키가 수명(600초)으로 없어진 경우는 **목록 · 단건이 방 키를 읽다가 발견해서**(위 만료 판정 · 자가 치유와 같은 방식이다). **방장 키 · 멤버 SET · 확정 표시 키가 전부 없을 때만** 닫는다 — **방장 키만 없는 것은 승계 중이다**(D-23).
  `ACTIVE`인 줄만 바꾸는 조건부 UPDATE라 두 길이 겹쳐도 한 번이다. **확정 전에 방이 없어지면 파티가 없으니 글만 만료된다**(위 — 그대로). **글은 `CONFIRMED` 그대로이고 응답에 새 칸은 없다.** 알림(`PARTY_*`)은 여전히 미정이라 내지 않는다.
  **`PartyClosed.fifo`(SQS)는 두지 않는다** — 닫는 쪽도 `recent_players`를 적는 쪽도 이 앱이라 같은 앱 안에서 끝난다(§3.4). **자동 매칭 파티도 같은 나가기 콜백으로 닫는다**(2026-09-27 — P-30. `post_id` 대신 `match_party_id` 로 파티를 찾는다). **② 의 길도 있다 — 목록 GET(`GET /api/v1/posts?game=`)이 그 게임의 `ACTIVE` 자동 매칭 파티를 `match_party_id` 로 골라 방 키를 같이 읽고 셋 다 없으면 닫는다**(2026-09-28 **소유자 결정** — 글이 없어 글에서 출발하는 읽기에는 잡히지 않아 따로 고른다. 한 번에 200개 · 단건은 보지 않는다 · 못 읽으면 닫지 않는다. `PostService#closeVanishedMatchParties`). 닫는 자리 · 쿼리 · 테스트는 코드 참조.
  **왜** — `parties.status`가 `ACTIVE`로 박힌 채 아무도 바꾸지 않았고, "파티가 닫혔다"가 미정이라 `recent_players`를 채우는 주체가 없어 최근 함께한 사람이 늘 빈 목록이었다.
- **방 안을 읽는 창구는 `RoomService#states` 하나다** — 글이 몇 개든 **파이프라인 한 번**으로 글마다 `EXISTS host` · `SMEMBERS members` · `EXISTS confirmed`를 읽는다. **쓰는 명령이 없다.** 방장 키의 **값은 읽지 않는다.**
  못 읽으면 `RoomStateUnavailableException`을 던지고 **fail-open(목록 · 단건 — 방 정보를 비운 채 글만) / fail-closed(고치기 · 입장 — 503 `ROOM_STATE_UNAVAILABLE`)는 부르는 쪽이 정한다.** 두 앱이던 때의 `party/room/RedisRoomStateReader`가 이 메서드가 됐다.
  **글을 고칠 때도 이것으로 멤버를 읽는다**(방에 방장 말고 누가 있으면 409 `ROOM_HAS_OTHER_MEMBERS` — 2026-09-24 소유자 결정 · §7.1 · P-19).
- **목록 조회(GET)가 글을 만료 · 확정으로 옮겨 적는다**(2026-09-26 부터는 파티 닫힘도 — 위) — 방은 수명이 다하면 Redis에서 저절로 사라져 그 순간 돌아가는 코드가 없으니 방 키를 볼 때 스스로 옮겨 적는다. 전부 조건부 UPDATE라 멱등하다(`party/service/PostService`의 머리 주석이 "허용된 부수 효과"라고 적었다). 입장 검사는 옮겨 적지 않는다(곧이어 Lua가 같은 이유로 거절한다).
  **§5.1 (다)의 "상태를 바꾸는 GET을 만들지 않는다"와 어떻게 같이 서는지는 어느 문서도 말하지 않는다** — 사용자의 뜻으로 바뀌는 것이 아니라 방 키에서 읽은 사실을 옮겨 적는 것이고 누가 불러도 결과가 같다는 것이 구현한 쪽의 판단이다. **소유자가 검토할 것이다**(`START_HERE.md` §4).
- **방을 바꾸려면 Lua를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거친다 — 원자성 때문이다.** 정원 검사 · 입장 표시 키 · 활성 요청 키의 `EXISTS`가 한 스크립트 안에 있어서, 이 앱 안이라도 맨손으로 `SADD` · `SET` 하면 그 불변식이 깨진다.
  **거절되면 Redis에 아무것도 쓰이지 않는다.** Redis 장애는 **부르는 그 자리에서** 503 `ROOM_STATE_UNAVAILABLE`로 옮긴다(`room/service/RoomRedis` — 전역 처리기에서 `DataAccessException`을 받으면 DB 오류까지 그렇게 된다).
- **방 키 — 이제 이 앱의 것이다.** 원본 상수는 **`room/redisKeys/RoomKeys.java`** 하나다(게시판이 들고 있던 사본 `party/room/RoomKeys`는 1단계에서 지웠다). 계약은 `contracts/platform-api.md` "방" 의 "Redis 키".

  | 키 | 자료형 | 값 | 뜻 |
  |---|---|---|---|
  | `qm:room:{roomId}:host` | STRING | 방장의 `userId`(사용자 번호의 십진 문자열) | **이 키가 있다 = 방이 있다.** 글 쓰기가 방을 만들 때 쓰고 방이 없어질 때 지운다. **확정한 방에서는 값이 바뀔 수 있다**(D-23) |
  | `qm:room:{roomId}:members` | SET | 방에 있는 사람의 `userId`(방장 포함 — **숫자로 팔 수 없는 값은 읽는 자리에서 건너뛴다**, 아래) | `SCARD`가 현재 인원이다. 정원은 5. 게시판은 `SMEMBERS`로 받아 DB에서 프로필을 붙이고 차단을 대조한다(D-20). **원소는 `userId`뿐이다** — 닉네임·티어·포지션은 이 SET에 없다 |
  | `qm:room:{roomId}:confirmed` | STRING | 그 방의 `roomId` | **확정 표시 키. 이 키가 있다 = 방장이 확정한 방이다**(D-21). 방장 확정의 Lua가 쓴다 |
  | `qm:user:active-room:{userId}` | STRING | 들어가 있는 방의 `roomId` | **입장 표시 키 — `matching`과의 약속이다**(D-19. 아래) |
  | `qm:room:no-entry:{userId}` | ZSET | 원소 `roomId` · score 는 풀리는 시각(epoch ms) | **입장 금지 목록**(2026-09-29 소유자 결정 — P-32). 강퇴가 쓴다(`kick-room.lua`) · `enter-room.lua` 가 **맨 먼저** 읽어 403 `KICKED_RECENTLY`. 스스로 나간 사람은 들지 않는다. `enter-match-room.lua` 는 읽지 않는다(미정) |
  | `qm:room:no-auto-join:{userId}` | ZSET | 모양은 위와 같다 | **자동 합류 건너뛰기 목록**(같은 결정). 강퇴 · 나가기가 쓴다(`kick-room.lua` · `leave-room.lua` — 나가기는 일반 멤버가 나가는 길에서만) · 게시판 방 먼저 합류가 **`RoomService#noAutoJoinRooms`** 로 읽는다(`party` 는 방 키를 직접 읽지 않는다). **사람의 키라** 방이 없어져도 같이 지워지지 않고 수명 600초(설정이 아니라 상수)로 사라진다 |

  **`qm:party:{partyId}` 는 이 표에 없다 — `matching` 의 키이고 이 앱은 읽기만 한다**(2026-09-27 — docs/11 D-42. `HGETALL` · `HEXISTS` 만. 쓰지도 지우지도 `EXPIRE` 를 걸지도 않는다). 필드 계약(`status` · `confirmedAt` · `game` · `modeKey` · `voicePreference` · `playPurpose` · `target` · `member:{userId}` · `tierLo`/`tierHi` · 수명 600초)은 `contracts/platform-api.md` "자동 매칭 파티의 방" 의 "파티 HASH 의 계약" 이다. 접두사 `qm:party:` 의 원본은 `matching` 의 `redisKeys/SharedKeys.PARTY_PREFIX` 이고 이 앱의 사본은 `party/match/MatchPartyKeys.PARTY_PREFIX` 다(`SharedPrefixTest` 가 대조한다 — 어긋나면 컴파일 · 테스트가 통과한 채로 모든 자동 매칭 파티가 404 다). 자동 매칭 방의 `{roomId}` 는 이 UUID 그대로다.

  - **`matching`과의 키 약속(D-19)은 그대로다** — "자동 매칭 대기와 방은 한 번에 하나만"을 키 둘로 지킨다. **활성 요청 키(`qm:user:active-request:{userId}`)는 `matching`이 쓰고 이 앱은 `EXISTS`만 한다**(글 쓰기 · 입장의 Lua가 409 `ALREADY_QUEUED`). **입장 표시 키는 이 앱이 쓰고 지우며 `matching`은 `EXISTS`만 한다**(매칭 요청이 409 `IN_ROOM`). 키 이름의 원본은 `matching`의 `SharedKeys`다(사본 `room/redisKeys/SharedKeys`). **활성 요청 키는 영구적이지 않다** — 대기 중인 요청은 클라이언트의 접속 확인(`matching` 의 `POST /api/v1/match-requests/heartbeat`, 30초 주기)이 끊기면 90초 안에 `matching` 이 스스로 취소하고(docs/11 **D-43**, 2026-09-28), 확정된 요청(`status=PARTY`)은 60초 뒤 만료된다(D-42). 그래서 이 앱의 409 `ALREADY_QUEUED` 가 영원히 남는 일은 없다.
  - 모든 키가 수명 600초(설정 `platform.room.ttl-seconds` · 환경변수 `ROOM_TTL_SECONDS`)이고 브라우저가 1분마다 보내는 접속 확인(`POST …/heartbeat`)이 늘린다. **확정하지 않은 방의 수명(방장 키 · 멤버 SET)은 방장의 신호만 늘린다** — 방장이 명시적으로 나가든 말없이 사라지든 방장 키가 없어지고, 방에 다른 사람이 있어도 방을 통째로 없앤다(D-21).
    대가 — 방장이 말없이 사라진 방은 최대 10분 살아 있는 것처럼 보인다.
  - **방장 승계 — 확정한 방은 방장이 나가도 방이 이어진다(D-23).** 남은 멤버 가운데 한 명이 방장을 넘겨받는다 — 방장이 나가기를 부르면 그 자리에서, 말없이 사라지면 방장 키가 만료된 뒤 처음 접속 확인을 보낸 멤버가(확정한 방에 한해 일반 멤버의 접속 확인도 멤버 SET · 확정 표시 키의 수명을 늘린다). 넘겨받을 사람이 없을 때만 세 키가 함께 없어진다.
    **그래서 확정한 방은 방장 키만 잠깐 없을 수 있다**(늦어도 1분) — 확정된 글을 만료시키지 않는 이유다(위 "만료 판정"). 새 방장이 누구인지는 알림에 싣지 않는다 — 클라이언트는 방 안 사람 목록을 다시 조회한다.
  - 멤버 SET에는 말없이 사라진 사람의 이름이 잠깐 남을 수 있다(방장의 접속 확인이 뺀다) — **인원수는 길어야 수명만큼 부풀 수 있고**, 카드에도 그 사람이 남고, **그 사람과 차단 관계인 사용자에게는 이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(D-20 "감수하는 것").
  - 방에는 로그인한 사용자만 들어오지만(글 쓰기 · 입장이 access 토큰의 사용자 번호를 적는다) 손으로 넣은 값까지 막을 수는 없다 — **숫자가 아닌 값은 읽는 자리에서 건너뛴다**(WARN 한 줄. 카드에도 파티원에도 들지 않고 `memberCount`에서도 빠진다 — `room/domain/RoomMemberIds`). **숫자이지만 가입하지 않은 번호는 카드에서 빼지 않는다** — `nickname: null` · `profile: null`로 내보낸다(`memberCount`와 어긋나지 않게). **파티원으로는 기록하지 않는다**(2026-09-26 — `party_members.user_id`에 `users(id)` FK가 생겨 넣을 수 없다. `INSERT … SELECT … WHERE EXISTS (users)`로 걸러 위반 없이 지나간다 — `party/repository/PartyRecordRepository`. 그 전에는 파티원으로도 기록했다).
  - **방 밖에서 방 안을 보는 공개 창구는 게시판 목록이다** — 방 안 사람 목록(`GET /api/v1/rooms/{roomId}/members`)은 방 안의 사람만 볼 수 있다(403 `NOT_IN_ROOM`). 차단을 거르는 곳이 목록이라서다.
- **방이 내는 것** — 방 알림(`ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED`)과 WebRTC 시그널(`POST …/signals` → `WEBRTC_SIGNAL`)은 **이 앱의 `room` 패키지가 낸다**(`room/service/RoomNotifier` → `common/push/PushPublisher`). `payload`는 `contracts/platform-api.md` "방" 의 "알림"이다. 방의 인원이 바뀌면 게시판 채널에도 신호를 낸다(§3.2).
- **gameconfig의 접두사(§3.6 — `common/gameconfig/GameConfigKeys`)는 알림 채널 접두사와 같은 위험이고 fail-open이라 더 조용하다** — 어긋나면 목록이 틀리는 것이 아니라 검증이 통째로 꺼진다. (방 키는 이제 이 앱 안의 상수 하나라 두 앱이 어긋날 일이 없다. 어긋날 수 있는 것은 `matching`과 나눠 가진 D-19의 두 키 이름과, 2026-09-27 부터 읽는 파티 HASH 의 접두사 `qm:party:` · 필드 이름이다 — 접두사는 `SharedPrefixTest` 가 대조하지만 **필드 이름은 테스트가 없다**, D-42.)

**회고 — `room`을 따로 뗐던 것을 2026-09-25에 합쳤다.** 이유는 셋이다 — 목록을 그릴 때마다 게시판이 `room`의 상태(Redis)를 읽어야 했고(서로 chatty 하면 같은 서비스라는 Azure의 기준), 확정 · 방 키 · 입장권을 두 앱이 같이 바꿔야 했고(design-time coupling), 팀이 한 사람이다.
**입장권(Valet Key 패턴)은 나눠 놨을 때의 표준이었지 나눠야 할 이유는 아니었다.** 두 앱이던 때는 게시판이 남의 앱이 쓰는 Redis 키를 읽는 것을 "교과서대로면 DB 공유로 통합하기 — 알고 감수한다"고 적고 대안 셋(API 호출 · 이벤트로 사본 유지 · 프런트 조합)을 검토해 버렸는데, 합친 지금은 그 물음 자체가 없다.
**docs/11의 D-16 · D-19 ~ D-23은 두 앱을 전제로 쓰였고 2026-09-26 에 docs/11 D-33 이 개정했다.** **`notification`(SSE 연결 보유)과 `matching`(매칭 엔진)은 그대로 따로 둔다.**

### 3.4 서버 간 이벤트 — 큐가 없다 (docs/11 #21 의 outbox + SQS FIFO 를 **D-42** 가 개정했다, 2026-09-27)

> **2026-09-27 소유자 결정(docs/11 D-42)** — `matching` 의 outbox · `ProposalConfirmed.fifo` 를 두지 않는다. 확정된 파티는 `matching` 이 Redis 파티 HASH `qm:party:{partyId}` 를 자기완결로 채우고, **이 앱이 그것을 읽어** 파티와 방을 만든다(§3.3 · `contracts/platform-api.md` "자동 매칭 파티의 방" · P-30). **이 앱이 내거나 받는 큐는 0개다.** `matching` 은 같은 날 Flyway + outbox 표를 넣었다가 되돌렸다.

| 큐 | 지위 | 왜 |
|---|---|---|
| ~~`ProposalConfirmed.fifo`~~ | **만들지 않는다**(D-42). 원안(docs/11 #21)은 `matching` 이 outbox 로 발행하고 이 앱이 소비해 파티를 DB 에 만드는 것이었다 | 이 앱이 `qm:party:{partyId}` 를 읽는다. SQS 를 고른 이유(#18 · #21 — 앱별 스키마라 DB 로 대화할 수 없었던 것 · `BlockChanged` 의 순서 보장)가 D-34 · D-12 로 둘 다 사라졌고, 파티당 한 번 나가는 이벤트라 지킬 순서가 없다. DB · Redis 를 공유하는 두 앱이 AWS 를 한 바퀴 도는 것은 장치만 늘린다(D-42 "근거") |
| ~~`PartyClosed.fifo`~~ | **만들지 않는다**(D-36 · D-42). 원안은 이 앱이 발행 + 소비(소비자는 이 앱 하나 — D-13) | 게시판 파티(D-36 · P-25)도 자동 매칭 파티(D-42 · P-30)도 **같은 앱 안에서** 나가기 콜백으로 닫고 `recent_players` 를 적는다(§3.3 "파티 닫힘"). `matching` 은 이 큐를 읽지 않는다(D-13 그대로 — 읽을 큐 자체가 없어졌다) |
| ~~`BlockChanged.fifo`~~ | **만들지 않는다**(docs/11 D-12) | 원안(docs/11 #21)은 이 앱이 발행하고 `matching`이 받아 Redis 선필터를 만드는 것이었으나, `matching`은 **배정 때 선필터로** `blocks`를 직접 읽는 한 겹으로 INV-6을 지킨다(D-1·D-2 — 확정 직전 검증은 두지 않는다, docs/11 **D-41**, 2026-09-27). 차단/해제는 DB 트랜잭션으로 끝난다 |

- **outbox 테이블 · relay · AWS SDK 를 들이지 않는다.** `parties.source` 의 `MATCH` 는 P-30(`POST /api/v1/match-parties/{partyId}/room`)이 채운다 — 2026-09-21 에 `backend/build.gradle` 에 남긴 "SQS 를 넣을 자리" 주석은 낡았다.
- **Kafka/RabbitMQ/Redis Streams 로 바꾸지 마라**(docs/11 #21 · #26) — 큐가 없어진 것이지 다른 큐로 바꾼 것이 아니다. 서비스별 DB 로 진짜 갈라지는 날 `matching` 의 확정 자리(`ProposalService`)에 큐를 넣는다(D-42 "근거").
- 다른 앱에 시킬 일이 **새로** 생기면 여전히 transactional outbox → SQS FIFO 가 원안이고(§5 — at-least-once · 소비는 멱등 · DLQ), 그 전에 **먼저 묻는다.** 동기 HTTP 로 잇지 않는다.
- **감수하는 것(D-42)** — 확정 뒤 10분 안에 아무도 이 앱을 부르지 않으면 파티가 증발한다(확정하고 아무도 안 들어온 파티라 잃어도 된다). 이 앱이 파티 HASH 를 읽지 못하면(Redis 장애) 파티가 안 만들어진다 — 503 으로 거절한다(fail-closed, §3.3).

### 3.5 DB — 스키마 하나(`public`)와 `blocks`

> **2026-09-26 소유자 결정으로 이 절을 다시 썼다**(`contracts/platform-api.md` P-23). **옛 모양** — PostgreSQL 인스턴스 1개에 **schema-per-service**, 이 앱이 `account` · `social` · `party` 세 스키마를 갖고 **크로스 스키마 FK·JOIN 금지**, 마이그레이션은 `db/migration/<schema>/`로 나눴다(docs/11 #17 · docs/WHY_POSTGRESQL §3이 인용한 원본 docs/06 배치).
> **개정하는 옛 결정** — docs/11 #17(schema-per-service · 스키마별 롤) · D-1(`matching` 롤에 GRANT) · 2026-09-22의 "스키마별 DB 롤을 두지 않는다"(스키마가 하나가 되며 물음째 없어졌다) · docs/WHY_POSTGRESQL §3의 스키마 배치. **docs/11 D-34로 남겼다(2026-09-26).**

- **스키마는 `public` 하나다.** 테이블 이름은 그대로다(`users` · `game_accounts` · `game_account_stats` · `social_identities` · `blocks` · `friend_requests` · `friendships` · `reports` · `recent_players` · `recruit_posts` · `recruit_post_positions` · `parties` · `party_members` — 13개. 합칠 때는 `credentials`까지 14개였고 2026-09-26 P-24로 빠졌다).
  **테이블 사이의 JOIN · FK를 허용한다.** **`parties.post_id → recruit_posts(id)`도 `ON DELETE CASCADE`가 됐다**(Claude가 정한 세부 — 그대로 두면 사용자를 지울 때 글에서 멈춘다. 대가: 방장을 지우면 그 글의 파티 · 파티원 기록이 남의 것까지 같이 지워진다. 회원 탈퇴가 아직 없어 지금은 걸리지 않는다 — 탈퇴를 만들 때 다시 본다). **"나"의 칸**(`blocks.blocker_id` · `friend_requests.requester_id` · `reports.reporter_id` · `recruit_posts.host_id`)의 FK 위반은 404가 아니라 **401 `UNAUTHENTICATED`**다(토큰은 멀쩡한데 그 사용자가 DB에 없다 — 전에는 친구 요청만 그렇게 했고 차단 · 신고 · 글 쓰기는 그냥 성공했다. Claude가 정한 세부). 사용자 번호를 담는 칸(`blocks.blocker_id/blocked_id` · `friend_requests.requester_id/receiver_id` · `friendships.user_low_id/user_high_id` · `reports.reporter_id/target_user_id` · `recent_players.user_id/other_user_id` · `recruit_posts.host_id` · `party_members.user_id`)에 **`users(id)` FK `ON DELETE CASCADE`**를 걸었고, `recent_players.last_party_id` → `parties(id)`는 **`ON DELETE SET NULL`**이다. FK 이름은 `<table>_<column>_fkey`다.
  **컬럼은 `matching` 쪽 문서에 없다 — 이 폴더에서 정했고, 원본은 마이그레이션이다**(아래).
- **왜(소유자 결정)** — DB를 보는 앱이 사실상 이 앱 하나다(`matching`이 `blocks`를 직접 읽는 것 하나뿐). 스키마를 나누고 JOIN · FK를 막은 탓에 코드가 쓸데없이 복잡했다 — 닉네임을 따로 읽어 자바에서 정렬하고, 사용자가 있는지를 앱이 조회로 확인하고(FK를 못 걸어서), 도메인 사이에 "읽는 창구"(`UserReader` 등)를 두었다.
  **합치며 그것을 걷어냈다** — JOIN 한 쿼리 + `ORDER BY`, 없는 사용자는 FK 위반 → 404 `USER_NOT_FOUND`. 창구는 JOIN이 대신 못 하는 것만 남는다(예 — Redis에서 온 id 목록으로 묻는 `BlockReader`). **어느 창구가 남았는지는 코드 참조**(§4 "패키지").
- **패키지 나누기(`common` · `account` · `social` · `party` · `room`)는 그대로다** — 바뀐 것은 DB 쪽이다(§4).
- **롤 · `GRANT`는 없다.** 앱 하나가 DB 계정 하나로 붙는다 — 스키마가 하나라 스키마별 롤이라는 물음 자체가 없다. `matching`은 별도 롤 없이 `blocks`를 읽는다. 뷰(`shared_read.blocked_pairs`)도 만들지 않는다.
  이 앱이 붙는 DB 계정은 환경변수(`DB_USER` · `DB_PASSWORD`)로 받는다 — 운영의 계정은 미정이다(§7 "운영의 DB 롤").
- `reservation`의 테이블은 **이 앱이 소유하지 않는다**(D-15 — `app:reservation`의 것). 그 마이그레이션을 누가 실행하는지는 미정이다(§7).
- **`matching`이 읽는 이 앱의 테이블은 `blocks` 하나다 — 늘리지 않는다**(권한이 아니라 약속으로 지킨다. 스키마가 하나가 된 뒤에도 그대로다).
  `blocks(id bigint identity PK — 채번은 DB, blocker_id bigint, blocked_id bigint)`. 방향이 있는 한 줄이고 `matching`은 양방향으로 조회한다. **`(blocker_id, blocked_id)` UNIQUE는 이 앱이 건다.** 바꾸면 `matching`이 런타임에 깨진다(`block/Block.java` · docs/11 D-4).
  **`matching` 쪽에 고칠 것이 둘 쌓였었다 — 2026-09-26 에 그 폴더에서 둘 다 했다**(여기서 고치지 않는다 — §9) — ① 두 칸이 `varchar(20)`에서 `bigint`가 됐으니(2026-09-22) `Block.java`를 `String`에서 `Long`으로 ② `@Table(schema = "social", name = "blocks")`의 **`schema`를 뺀다**(2026-09-26 — `public.blocks`가 됐다). 그 전까지는 `matching`이 그 테이블을 읽다가 런타임에 깨질 참이었다.
- **모든 테이블의 PK는 `bigint GENERATED ALWAYS AS IDENTITY`이고, 사용자의 식별자는 사용자 번호 하나다**(PK는 2026-09-22 **소유자 결정**. Spring에서는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`다).
  **2026-09-19의 "사용자 id는 가입할 때 정한 로그인 아이디(문자열)다"를 개정한 것이다 — docs/11 D-4와 얽힌다.** 원본은 마이그레이션과 `contracts/platform-api.md`(P-11 · P-24)다.
  - **`users.id`(bigint)가 `userId`다** — JWT의 `sub`(숫자를 문자열로 — `"42"`), 알림 채널 `qm:pubsub:push:{userId}`, 방 키 · 멤버 SET의 `{userId}`, URL의 `{userId}`, 요청·응답 본문의 `userId`, 다른 테이블의 `*_id` 컬럼이 전부 이것이다. **보여 주는 이름은 `nickname` 하나다**(유일하지만 바뀔 수 있다 — 사람을 가리키는 값으로 쓰지 않는다).
  - **로그인 아이디(`users.login_id` · `loginId`)는 2026-09-26에 없어졌다**(**소유자 결정** — P-24. 비밀번호 로그인이 없으니 쓰는 데가 없다). 2026-09-22에는 식별자를 둘로 갈라 `login_id`(`varchar(20) UNIQUE` · CHECK `^[a-z0-9_]{4,20}$` · 중복은 409 `LOGIN_ID_TAKEN`)를 로그인할 때만 썼다.
    그때 적은 "로그인 실패 제한의 Redis 키만 로그인 아이디 기준이다"도 그 제한째 없어졌다(§5.1).
  - **옛 결정의 근거였던 "`matching`이 이미 `String`으로 다룬다"(docs/11 D-4)는 뒤집혔다** — 위 "`matching` 쪽에 고칠 것" ①이고 2026-09-26 에 고쳤다(docs/11 D-25).
    **`matching` · `notification`이 받는 `userId` 파라미터와 Redis 채널은 문자열을 그대로 다루므로 `"42"`가 들어가도 그쪽 코드 변경이 없다**(방 키 · 멤버 SET은 2026-09-25 부터 이 앱의 것이다 — §3.3).
  - docs/WHY_POSTGRESQL의 `uuid` 서술은 여전히 이 앱의 모양이 아니다 — 이제는 bigint다.
  - **소셜 로그인이 유일한 가입 · 로그인이다**(2026-09-26 — P-24) — 소셜로 처음 온 사람이 **닉네임만** 정하고(2026-09-21에는 로그인 아이디도 정했다), 제공자의 회원 번호는 `social_identities`에만 있어 사용자 번호가 되지 않는다.
- **테이블과 컬럼의 원본은 `backend/src/main/resources/db/migration/` 의 `V1__schema.sql`(+ 2026-09-27 의 **`V2`** — `parties.match_party_id`, P-30 · 2026-09-29 의 **`V3`** — `social_identities` 에 구글, P-33 · 같은 날의 **`V4`** — `game_accounts.main_position` 을 지운다, P-35 · 같은 날의 **`V5`** — `game_accounts.tier` 를 사다리별 `tiers`(jsonb)로 옮기고 지운다, P-36 · 2026-09-30 의 **`V6`** — `recruit_posts.host_position` 을 더한다, P-38)이다**(2026-09-21에 "원본은 마이그레이션"으로 정했다 — `contracts/platform-api.md` 머리. 파일이 하나가 된 것은 2026-09-26 소유자 결정). 그림은 ERD(§10)에 있다(**스키마 셋으로 그려져 있다면 그림이 낡은 것이다 — 마이그레이션이 맞다**).
  - **V1 하나로 다시 썼다** — 옛 `V1__baseline.sql`(주석뿐) · `account/V2`·`V3` · `social/V4`·`V6` · `party/V5`·`V7`·`V8`의 **최종 모양(V8까지 적용한 결과)을 한 파일에 담고**, 다른 점은 스키마(`public` 하나)와 FK(위) 둘이다. 이 문서들에 남은 옛 파일 이름(`party/V8__drop_room_seen_at.sql` 등)은 **그 변경이 들어온 때의 기록**이고 지금은 그 파일이 없다.
    **운영 DB가 없고 로컬 · 테스트 DB가 `--rm` 컨테이너라 매번 빈 채로 뜨므로** 적용된 파일을 갈아 끼울 수 있었다.
  - **"이미 적용된 파일은 고치지 않는다"(체크섬이 달라져 기동이 막힌다)는 운영 DB가 생긴 뒤부터 다시 걸린다** — 그 뒤로 바꿀 것은 새 버전으로 쓴다(**V2 는 2026-09-27 에 자동 매칭 파티의 `match_party_id` 로 먼저 생겼다 · V3 는 2026-09-29 에 구글 — `provider` CHECK 에 `GOOGLE` · `provider_user_id` 를 255 로 · V4 는 같은 날 게임 계정의 주 포지션 칸을 지운다(`DROP COLUMN main_position` — CHECK · 인덱스가 없던 칸이다) · V5 도 같은 날 — `tiers jsonb NOT NULL DEFAULT '{}'` + `game_accounts_tiers_check`(`jsonb_typeof(tiers) = 'object'`)를 더하고, 옛 `tier` 를 LOL → `{"SOLO": …}` · VALORANT → `{"COMPETITIVE": …}` 로 옮기고 **PUBG 는 버린 뒤**(자기신고였다 — API 로 다시 채운다) `tier` 칸을 지운다 · V6 은 2026-09-30 — 모집 글의 방장 포지션 `host_position varchar(20)`(`NULL` 허용 · CHECK 없음 — 값의 목록은 앱과 gameconfig 에 있다)** — P-30 · P-33 · P-35 · P-36 · P-38). 운영이 생기기 전에 V1을 또 갈아 끼울지는 그때 묻는다.
  - 담긴 것(자세한 것은 파일 참조) — `users`(`id` = **사용자 번호** · `nickname` UNIQUE — `login_id`는 2026-09-26에 빠졌다) · (`credentials`(비밀번호 해시)는 2026-09-26에 없어졌다 — P-24) · `game_accounts`(`UNIQUE (user_id, game)` · `external_id` · `verified` · `server`(PUBG만) · **`tiers`**(jsonb — `{사다리: 티어 이름}` · 키는 `account/domain/Game#tierLadders()` · 값이 있는 사다리만 적는다 · DB 는 "객체다" 만 건다 — 2026-09-29 V5, P-36) — `main_position` 은 2026-09-29 에 V4 가, `tier` 는 V5 가 지웠다, P-35 · P-36) ·
    `game_account_stats`(전적 스냅숏 — 게임 계정과 1:1. **세 게임이 한 테이블을 쓰고 판 수 `games`만 공통 컬럼이다** — `wins` · `losses` · `win_streak` · `avg_assists`는 PUBG에 없어 비는 칸이고 `(wins IS NULL) = (losses IS NULL)`을 CHECK가 지킨다. 소유자 결정 2026-09-22 · P-12. 게임마다 다른 지표는 `detail` jsonb) · `social_identities`(PK `(provider, provider_user_id)`) ·
    `blocks` · `friend_requests`(같은 방향의 대기 중 요청은 하나 — 부분 UNIQUE 인덱스 `WHERE status = 'PENDING'`) · `friendships`(PK `(user_low_id, user_high_id)`로 정규화한 한 줄) · `reports`(UNIQUE 없음 — 여러 번 신고할 수 있다) · `recent_players`(PK `(user_id, other_user_id)` — **2026-09-26 부터 파티 닫힘이 채운다**, §3.3 · P-25) ·
    `recruit_posts`(`id` = `roomId` · "모집 중인 글은 한 사람에 하나"의 부분 UNIQUE 인덱스 · 게시판 목록의 인덱스 `(game, id DESC)` — 2026-09-24 소유자 결정, 옛 V7 · "아직 안 만들어진 방"을 가르던 칸은 없다 — 2026-09-25, 옛 V8) · `recruit_post_positions` · `parties`(`id`는 파티 자신의 번호이고 **`post_id`에 `UNIQUE`** — 그것이 방장 확정 기록의 멱등을 지킨다. **`match_party_id varchar(36) UNIQUE`**(V2 — 2026-09-27 · P-30)에 자동 매칭 파티의 `partyId`(UUID)를 담고 `CHECK ((source = 'MATCH') = (match_party_id IS NOT NULL))` 이 게시판 파티(`post_id`)와 가른다 — 그것이 자동 매칭 파티의 멱등을 지킨다. `status` · `closed_at`은 파티 닫힘이 적는다 — §3.3, 칸의 세부는 마이그레이션 참조) · `party_members`.
  - **제약에 전부 이름을 붙였다** — 앱이 제약 위반을 그 이름으로 갈라 에러 코드로 옮긴다(`users_nickname_key` → `NICKNAME_TAKEN` · 사용자 번호로 가는 FK → `USER_NOT_FOUND` 등 — `common/error/ConstraintViolations`). 이름을 바꾸면 앱의 상수도 같이 바꾼다. §5 "불변식은 DB가 강제한다"가 이렇게 지켜진다.
  - **`refresh_tokens` 테이블은 만들지 않았다** — refresh는 Redis에 둔다(D-14 · §5.1 (마)). outbox 테이블은 두지 않는다(2026-09-27 D-42 — §3.4).
  - (옛 규칙 — 2026-09-26에 없어졌다) `users` 밖의 스키마는 사용자 id에 FK를 걸지 않고, 있는 사용자인지는 앱이 `account`의 창구 `UserReader`로 확인했다.
- 옆 폴더 `matching/db-design/`은 **다른 설계의 흔적이다**(LoL 전용·Discord 로그인·차단 제외). 근거로 쓰지 마라.

### 3.6 gameconfig를 읽는다 — `mode` · `tier`의 값 검증 (2026-09-24 **소유자 결정** · `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16)

> **이 절은 §2 · §11의 "매칭 Redis 키(… `qm:gameconfig:*` …) 접근 — 예외가 없다"와 docs/11 #15를 개정한다.** 소유자가 직접 정했고 **docs/11 D-29로 남겼다**(2026-09-26).
> **2026-09-27 에 둘째 예외가 생겼다 — `qm:party:{partyId}` 읽기**(docs/11 D-42 — 확정된 자동 매칭 파티를 방으로 만들 때. §3.3 · `contracts/platform-api.md` "자동 매칭 파티의 방". gameconfig 와 달리 **fail-closed** 다 — 못 읽으면 503). **나머지(`qm:user:*` · `qm:proposal:*` · `qm:lock:*`)에는 "예외가 없다"가 그대로다** — 활성 요청 키는 이 앱이 여전히 `EXISTS` 만 한다(D-19 · §2).

**이 앱이 `qm:gameconfig:*`를 읽어 `mode`(모집 글)와 `tier`(게임 계정)가 있는 값인지 본다.** 값의 원본은 **`matching/seed/gameconfig.redis`**이고 **이 앱은 읽기만 한다.** 읽는 키는 셋이고 읽는 법은 넷이다(2026-09-28 — P-28 이 둘을 더했다). **2026-09-29 부터 모드 HASH 의 `tierLadder` 필드도 읽는다**(P-36 — 아래 표).

| 키 | 자료형 | 이 앱이 보는 법 |
|---|---|---|
| `qm:gameconfig:{GAME}:{MODE}` (모드별 설정 HASH) | HASH | `mode` 검증은 **`EXISTS` 하나** — 이 키가 있다 = 그 게임에 그 모드가 있다. **게시판 방 먼저 합류(P-28 · 2026-09-28)는 `tierRule` · `targetPartySize` 두 필드(2026-09-29 부터 `tierLadder` 까지 셋 — P-36. 그 모드가 보는 티어 사다리 `SOLO` · `FLEX` · `COMPETITIVE` · `RANKED` · 방장 티어를 그 사다리에서 읽는다. 옛 seed 처럼 없으면 티어를 보는 글을 건너뛴다 + WARN. **모드 이름으로 사다리를 가르지 않는다**)를 `HMGET` 으로 읽는다** — 티어를 보는 모드인가 · 그 모드의 정원. **모집 글의 방장 포지션(P-38 · 2026-09-30)은 `positionUniqueness` 를 `HGET` 으로 읽는다** — `"true"` 면 그 모드에 포지션이 있다(방장 포지션이 필수), HASH 는 있는데 아니면 없다. `mode` · `tier` 검증처럼 **fail-open**(`GameConfigReader#modePositions`). 그 밖의 필드는 읽지 않는다 |
| `qm:gameconfig:{GAME}:tier` (티어 사다리 ZSET) | ZSET | `tier` 검증은 **`ZSCORE`** — `null`이면 없는 티어다. **게시판 방 먼저 합류는 score 도 읽는다**(`ZMSCORE` — 허용 범위 `MIN:MAX` 를 단계 번호로 옮겨 비교한다). 키의 `EXISTS` 는 "심겼는가" 를 겸한다 |
| `qm:gameconfig:{GAME}:tier-range:{MODE}` (티어별 허용 범위 HASH) | HASH | **게시판 방 먼저 합류만** — `HMGET` 으로 내 티어의 줄과 방장 티어의 줄(`MIN:MAX` 또는 `SOLO_ONLY`)을 읽는다(2026-09-28 — P-16 개정). `mode` · `tier` 검증은 읽지 않는다 |

- **모드 목록 SET은 없다 — 원본 seed가 일부러 없앴다**("목록을 따로 두면 모드 하나를 고칠 때 두 곳이 어긋난다"). 그래서 모드가 있는지는 **HASH의 `EXISTS`가 답한다.**
  **`qm:gameconfig:{GAME}:tier-range:{MODE}`(티어별 허용 범위)는 2026-09-28 부터 게시판 방 먼저 합류가 읽는다**(2026-09-27 소유자 결정 P-28 · docs/11 D-40 이 정했고 2026-09-28 에 구현됐다 — "내 티어가 그 모드의 허용 범위 안인가"를 본다. `common/gameconfig/GameConfigKeys#tierRange`). **`mode` · `tier` 검증은 여전히 읽지 않는다** — 매칭의 판정 규칙이라서다. **그 요청만 fail-closed 다**(`GameConfigReader` 의 `seeded` · `modeConfig` · `tierScores` · `tierRanges` — Redis 를 못 읽으면 503 `ROOM_STATE_UNAVAILABLE`. 방에 넣는 일이라 어차피 Redis 없이 끝낼 수 없다. 안 심긴 Redis 는 여기서도 통과 — 티어 · 정원 검사를 건너뛴다). 아래 fail-open 은 `hasMode` · `hasTier` 의 것이다.
- **왜 §2의 금지를 어기는 것이 아닌가.** gameconfig는 `matching`이 **쓰는 상태가 아니다** — 원본이 seed 파일이고 그 머리가 **"이 파일이 MVP의 사실상 원본(source of truth)이다 · 앱은 부팅 시 설정을 밀어넣지 않고 Redis에서 읽기만 한다"**고 적었다. **쓰는 앱이 없고 `matching`도 읽는 쪽이다.**
  운영자가 배포 때 심는 **공유 설정**이라(Parameter Store · ConfigMap이 있을 자리다) 여러 서비스가 읽어도 된다. **가르는 기준은 "바뀌는 계기가 사용자의 행동인가, 운영자의 배포인가"다.**
  그래서 방 키(§3.3)와 **성질이 다르다** — 방 키는 사용자의 행동(입장 · 나가기 등)으로 실시간으로 바뀌는 상태이고, 2026-09-25 부터는 이 앱이 직접 쓴다. (파티 HASH `qm:party:{partyId}` 는 이 기준으로는 `matching` 이 사용자의 행동으로 쓰는 상태다 — 그래서 D-42 는 이 근거로 풀린 것이 아니라 **소유자가 따로 정한 예외**다. 읽는 것은 확정이라는 **끝난 사실**이고 `matching` 이 더 고치지 않는다 — 수명만 있다.)
- **이 앱은 seed를 심지 않는다 — 쓰는 명령이 없다.** 심게 만들면 모드를 하나 더할 때마다 이 앱을 재배포해야 한다(설정을 데이터로 뺀 뜻이 사라진다 — seed 머리가 그 이유를 적었다). **`matching`을 HTTP로 부르지도 않는다**(경계를 넘는 새 동기 호출 금지 — §5).
- **Redis를 못 읽으면 통과시킨다(fail-open — 소유자 결정).** 검증만 건너뛰고 **글 쓰기 · 게임 계정 연결은 성공한다** — 목록 조회가 이미 fail-open인 것과 결을 맞춘 것이다(§3.3). **WARN 한 줄**을 남긴다.
  대가 — **Redis가 죽은 동안에는 이상한 모드가 들어올 수 있다.** fail-open은 **`common/gameconfig/GameConfigReader` 한 곳에만** 있다(부르는 쪽은 "있는 값인가"만 묻는다 — Claude가 정한 자리다. 모드는 `party`, 티어는 `account`가 써서 `common`이다).
  **gameconfig가 아예 안 심긴 Redis도 통과시킨다**(Claude가 정한 세부 — 검증할 원본이 없는 것과 값이 틀린 것은 다르다. 가르는 열쇠는 **티어 사다리 키가 있는가**다. 세 게임 모두 사다리가 있고, 모드에는 목록 키가 없어 "하나도 없다"를 물을 데가 없다).
- **접두사는 상수 한 곳에만 둔다 — `common/gameconfig/GameConfigKeys`다.** **원본은 `matching`의 `redisKeys/SharedKeys`(`GAMECONFIG_PREFIX`)와 seed 파일이고 그것을 주석에 적었다.** 바꿀 때는 `matching`과 같이 바꾼다.
  알림 채널 접두사(§3.2) · 방 키(§3.3)와 같은 위험인데 **fail-open이라 더 조용하다** — 한 글자만 어긋나도 "gameconfig가 안 심겼다"로 읽혀 **검증이 통째로 꺼진 채 컴파일도 테스트도 통과한다.** 그래서 **실제로 읽어서 확인하는 테스트**를 둔다(`common/gameconfig/GameConfigReaderTest`).
- **`mode`가 필수가 됐다** — 옛날에는 "30자까지의 자유 문자열(없어도 된다)"이었다. **고치기(`PATCH`)에서 빈 문자열로 모드를 비우는 길이 없어졌다**(빈 문자열은 400. `description`은 그대로 빈 문자열로 비운다). **`tier`는 여전히 없어도 되고 값이 있을 때만 본다.**
  거절은 **400 `VALIDATION_FAILED`**이고 `details`에 `"mode: …"` · `"tier: …"` 한 줄이다. **Claude가 정한 세부** — 에러의 글귀, 클래스의 자리, `tier`에 `@Pattern(^[A-Z0-9_]{1,20}$)`을 남긴 것(fail-open일 때 DB 칸 `varchar(20)`을 넘는 값을 막는 것이 그것뿐이다), **`PATCH`에서 모드 검증이 방장 · 상태 검사보다 먼저 일어나는 것**(남의 글 · 만료된 글에 없는 모드를 주면 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING`이 아니라 **400**이다. 없는 글은 404 그대로).
- **왜 하는가** — 2026-09-23에 정한 "자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길"(§7 그 행)은 **글의 `mode`와 매칭 요청의 `mode`를 맞춰 봐야** 성립한다. 자유 문자열이면 판정할 수 없다.
- **값의 목록을 이 앱에 상수로 베껴 두지 않는다** — seed와 조용히 어긋난다.
- **미정** — **`recruit_posts.mode`를 `NOT NULL`로 조일지와 옛 글의 빈 `mode`를 어떻게 할지**(마이그레이션을 새로 만들지 않았다 — 컬럼은 `NULL` 허용 그대로이고(2026-09-26 에 다시 쓴 `V1__schema.sql`도 그대로다) 응답에 `mode: null`이 나갈 수 있다. 값의 목록이 Redis에 있어 **DB로는 강제할 수 없는 종류**다 — §5). 계약의 P-16에 적혀 있다. **지어내지 마라.**

## 4. 기술 스택 (결정됨)

| 항목 | 값 |
|---|---|
| 언어 | **Java 21** (`matching/backend/build.gradle`의 `JavaLanguageVersion.of(21)`) |
| 프레임워크 | **Spring Boot 4.1.1** (MVC, 서블릿) — `matching`·`notification`과 같은 버전(옛 `room`도 같았다). 한 사람이 여러 서비스를 같이 다루므로 의존성·설정 감각을 한 벌로 유지한다 |
| 빌드 | Gradle (`io.spring.dependency-management` 1.1.7), **단일 모듈**, 앱은 `backend/` 아래 |
| 저장소 | **PostgreSQL** (docs/11 #4, 근거 docs/WHY_POSTGRESQL.md) + Flyway. Redis는 알림 발행(§3.2)·refresh 토큰(키 `qm:auth:refresh:{uuid}` → 사용자 번호 — 접두사 `qm:auth:*`, §5.1 (마). **2026-09-23부터 쓴다**)·**방 안의 일**(방 키 `qm:room:*` · 입장 표시 키 `qm:user:active-room:*`를 Lua로 쓰고 읽는다 — 2026-09-25 에 `room`을 합쳤다, §3.3. 활성 요청 키 `qm:user:active-request:*`는 `EXISTS`만)·(~~로그인 실패 제한~~ `qm:auth:login-fail:*` · `qm:auth:login-lock:*` — **2026-09-26에 없어졌다**, P-24)·**전적 동기화의 락**(`qm:riot:sync:{gameAccountId}` — 접두사 `qm:riot:*`도 이 앱의 것이다. **2026-09-23부터 쓴다**, §7 "게임 계정 연동")·**전적 갱신의 쿨타임**(`qm:riot:refresh:{gameAccountId}` — 2분. **락과 다른 키다**: 락은 "지금 돌고 있다"(60초), 쿨타임은 "최근에 했다". **2026-09-24부터 쓴다**, §7)·**gameconfig 읽기**(`qm:gameconfig:{GAME}:{MODE}`의 `EXISTS` · `qm:gameconfig:{GAME}:tier`의 `ZSCORE` — `matching`의 seed로 운영자가 심는 공유 설정이고 **쓰지 않는다.** **2026-09-24부터 읽는다**, §3.6)·**자동 매칭 파티 HASH 읽기**(`qm:party:{partyId}` 의 `HGETALL` · `HEXISTS` — `matching` 이 확정 때 쓴다. **쓰지 않는다.** **2026-09-27부터 읽는다**, D-42 · §3.3)·**PUBG 현재 시즌 캐시**(`qm:pubg:season:{shard}` → 시즌 id · 수명 30일 — 접두사 `qm:pubg:*` 도 이 앱의 것이다. PUBG 의 전적 · 티어 동기화가 시즌 목록을 한 달에 한 번 넘게 부르지 않으려는 것 — **2026-09-29부터 쓴다**, P-36 · §7. PUBG 의 자물쇠 · 쿨타임은 LoL 과 같은 `qm:riot:*` 키를 게임 계정 번호로 쓴다) — 그 밖의 용도는 §7 |
| 인증 | **Spring Security `oauth2-resource-server`(Nimbus)** — RS256. `NimbusJwtEncoder`로 서명한다. jjwt 등을 따로 들이지 않는다(§5.1 (가)). **들어 있다(2026-09-21)** — Boot 4의 스타터 이름은 `spring-boot-starter-security-oauth2-resource-server`다. **소셜 로그인에 Spring의 `oauth2-client`는 쓰지 않는다** — 기본값이 인가 요청을 HTTP 세션에 넣는다(§5 "stateless"). 인가 코드 흐름을 `RestClient`로 직접 짰다(`contracts/platform-api.md` "소셜 로그인") |
| 기본 포트 | **8082 (확정 — 2026-09-21)** — `matching` 8080, `notification` 8081과 로컬에서 같이 띄우기 위해(`room`의 8083은 2026-09-25 에 합쳐 없어졌다 — 방의 요청도 8082 다). `backend/`의 `application.yaml`이 이 값을 기본값으로 쓴다(`SERVER_PORT`). 소셜 로그인의 Redirect URI 기본값(`OAUTH_REDIRECT_BASE_URL` = `http://localhost:8082`)도 이 값에 묶여 있다 |
| 패키지 | **도메인을 먼저 나눈다**(2026-09-25 까지는 "도메인 = DB 스키마"였다 — 2026-09-26 에 스키마가 `public` 하나가 됐고 **패키지 나누기는 그대로다**, §3.5) — `common` · `account` · `social` · `party` · **`room`**(방 안의 일 — 2026-09-25 에 `room` 앱을 합쳤다. DB 가 없고 Redis 에만 있다. 안은 원본 그대로 `controller` · `service` · `domain` · `dto` · `redisKeys` 이고 Lua 스크립트는 `resources/lua/`. **방을 바꾸려면 Lua 를 부르는 서비스를 거친다**). 안에서 `controller` · `service` · `domain` · `repository` · `dto`로 나눈다. **`common`은 도메인에 속하지 않는 것이다** — `error` · `web` · `security` · `push` · **`gameconfig`**(운영자가 심는 공유 설정을 **읽는** 곳 — `mode` · `tier`가 있는 값인지 본다. 2026-09-24 소유자 결정. 도메인 둘(`party`의 모드 · `account`의 티어)이 같이 쓰므로 여기 있다. **쓰지 않는다** — §3.6). **도메인 사이는 "창구"로 잇는다**(`room`의 `RoomService`(`create` · `confirm` · `states`), `party`의 `PostEntryGate` — 2026-09-25 2단계, §3.3 — 와 `social`의 `BlockReader` 등) — 남의 리포지토리를 직접 쓰지 않는다(`backend/…/platform/package-info.java`. Claude가 정했다). **"남의 스키마를 JOIN하지 않는다"는 2026-09-26 소유자 결정으로 풀렸다 — JOIN은 된다**(§3.5 · P-23). 그래서 창구는 **JOIN이 대신 못 하는 것만** 남는다(Redis에서 온 id 목록으로 묻는 것 등) — 옛 `account`의 `UserReader` · `GameProfileReader` 가운데 무엇이 남았는지는 **코드 참조** |

## 5. 설계 규칙

- **stateless다.** 프로세스 로컬 상태(메모리 세션, 로컬 캐시에 의존한 판정)를 두지 않는다. 설정은 환경변수 +
  기본값으로 받고, `/health/live`·`/health/ready`, SIGTERM graceful shutdown, stdout JSON 로그를 지킨다 (docs/11 #15·#20).
  **readiness에 `db`를 넣었다 — Redis는 넣지 않았다**(Redis가 죽어도 로그인은 된다. `application.yaml`의 주석). **JSON 로그는 기본값으로 켜지 않았다** — 운영에서 환경변수 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`로 켠다(형식이 `ecs`여야 하는지는 정해진 적이 없다 — §7).
- **경계를 넘는 동기 호출을 새로 만들지 않는다.** 다른 앱에 시킬 일은 outbox → SQS, 사용자에게 알릴 일은
  Pub/Sub이다. 둘을 섞지 마라 — 놓치면 데이터가 어긋나는 것이 SQS, 놓쳐도 조회로 복구되는 것이 알림이다 (docs/14 §7). **지금 이 앱이 내거나 받는 큐는 없다**(2026-09-27 D-42 — §3.4) — 확정된 파티는 `matching` 이 Redis 에 쓴 HASH 를 이 앱이 읽는 것으로 받고, `matching` 을 HTTP 로 부르지도 않는다.
- **불변식은 DB가 강제한다.** `조회 → 애플리케이션 판단 → 삽입`으로 지키지 마라. "같은 사람 두 번 차단
  금지"는 UNIQUE다 — **중복 가입 · 중복 차단 · "모집 중인 글은 한 사람에 하나" · 파티 기록의 멱등이 전부 이렇게 구현됐다**(INSERT의 제약 위반을 409로 옮긴다 — §3.5). **2026-09-26 부터는 "있는 사용자인가"도 DB가 본다** — 사용자 번호로 가는 FK의 위반을 404 `USER_NOT_FOUND`로 옮긴다(스키마가 하나가 되며 FK를 걸 수 있게 됐다 — §3.5 · P-23). 그것이 정상 경로라서 Hibernate의 제약 위반 WARN 로거(`org.hibernate.orm.jdbc.error`)를 껐다(`application.yaml`의 주석) (docs/WHY_POSTGRESQL §1. 같은 절의 INV-9 exclusion constraint는 이제 `app:reservation`의 일이다 — D-15).
  **H2는 PostgreSQL의 제약(부분 UNIQUE 인덱스 등)을 그대로 재현하지 못하고**(docs/11 D-3) exclusion constraint도 없다 — 이 검증은 PostgreSQL에서만 의미가 있다.
- **인증 — access 토큰은 JWT이고 쿠키로 주고받는다**(`Authorization` 헤더에 싣지 않는다). **refresh 토큰은 JWT가 아니라 불투명 UUID이고
  Redis에 저장한다**(UUID → 사용자. 폐기는 지우면 끝) (docs/11 #16 · D-14). **rotation 필수** — 재발급 때 옛 UUID를 지우고 새 UUID를 준다.
  **세부는 정해졌다(2026-09-21) — §5.1.** 서명은 RS256, CSRF는 `SameSite=Lax` + `Origin` 검사, **access denylist는 두지 않는다.** **access 15분 · refresh 7일이고 둘 다 구현됐다**(2026-09-23 소유자 결정 — §5.1 (라) · (마)).
- **Spring Security가 들어 있다(2026-09-21)** — `oauth2-resource-server`(Nimbus)다(§5.1 (가)). 세션을 만들지 않고(`STATELESS`) Spring의 CSRF 필터는 끈다 — CSRF는 `Origin` 검사 필터가 맡는다(§5.1 (다)).
  **인증이 필요 없는 요청은 `/api/v1/auth/**` · `/health/**` · `/info`뿐이다** — 로그인하지 않은 채 모르는 경로를 부르면 404가 아니라 401 `UNAUTHENTICATED`다(`contracts/platform-api.md` "공통").

### 5.1 인증 세부 (2026-09-21 소유자 확정 · **구현됐다.** **docs/11 D-24 · D-26으로 남겼다** — 2026-09-26)

> **결정 로그에 올렸다(2026-09-26).** 아래는 시스템 전체에 걸리는 결정이다 — 특히 (가) RS256, (다) CSRF, (라)의 **denylist를 두지 않는 것(= docs/11 #16 개정)**.
> `matching` 폴더에서 docs/11 **D-24**(인증 세부)로 남겼다 — 2026-09-21 에는 D-번호가 없어 출처를 "§5.1"로 적었다. 그 파일은 여기서 고치지 않는다(§9).
> **같은 처지이던 결정 다섯도 같은 날 남겼다**(전부 소유자 결정. ⑤는 2026-09-24, ③ · ④는 2026-09-23, 나머지는 2026-09-22다) — ① **스키마별 DB 롤을 두지 않는 것**(§3.5. docs/11 #17의 "스키마별 DB 롤" 대목과 D-1의 GRANT를 개정한다. **2026-09-26 에 스키마가 `public` 하나가 되며 P-23 에 흡수됐다** — 롤의 물음째 없어졌다. docs/11 **D-34**)
> ② **모든 PK를 `bigint identity`로 하고 `userId`(사용자 번호)와 `loginId`를 가른 것**(§3.5. **`loginId` 쪽은 2026-09-26에 없어졌다 — P-24.** **docs/11 D-4와 얽히고 2026-09-19의 결정을 개정한다.** `sub`가 숫자 문자열이 되므로 검증하는 옆 서비스에도 걸린다 — 아래 (가). docs/11 **D-25**, `loginId` 절반은 **D-35**).
> ③ **자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길을 둔다**(§7 그 행 — 두 경로를 다 두고 대기열 매칭은 살린다. `matching`과 D-19 에 걸린다). **방향만 정해졌고 세부는 미정이다** — 제 D-항목은 없고 docs/11 **D-29의 "아직 미정"**에 방향만 적혔다.
> ④ **refresh 토큰을 붙인 것**(아래 (라) · (마) — access가 `PT15M`으로 줄고 `TEMP-NO-REFRESH`가 없어졌다. `contracts/platform-api.md` "refresh 토큰" · P-15. #16의 access denylist 개정과 같은 묶음이다). **옆 서비스에는 걸리지 않는다** — 서명 · 검증이 달라지지 않고 access의 수명만 짧아진다. docs/11 **D-26**.
> ⑤ **이 앱이 gameconfig를 읽어 `mode` · `tier`를 검증하는 것**(§3.6 · `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16 — **§2 · §11의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15를 개정한다**). **옆 서비스에는 걸리지 않는다** — 읽기만 하고 seed를 심지도 않는다. docs/11 **D-29**.
> `matching` · `notification`에 걸리는 것(검증 · `Origin` 검사 · 전환)의 **적용은 각 폴더의 일이다**(옛 `room`은 2026-09-25 에 이 앱에 합쳐 끝났다).

**전제 — 브라우저가 보기에 네 서비스는 같은 출처다.** 운영은 CloudFront 한 도메인 아래에서 `/api/**` · `/events`를 ALB가 경로로 나눠 각 서비스로 보낸다(docs/AWS_ARCHITECTURE의 연결 표).
(나) · (다) · (사)가 전부 이 전제에 서 있다.

**(가) 서명 방식 — RS256.**
- **서명(개인 키)은 이 앱만 한다.** `matching` · `notification`은 **공개 키로 검증만** 한다. HS256이면 비밀 키를 여러 서비스가 다 가져, 어느 하나만 뚫려도 아무 사용자의 토큰을 만들 수 있다.
- **키 전달** — 공개 키 PEM을 각 서비스의 **환경변수**로 넣는다(운영은 Secrets Manager). **JWKS 엔드포인트는 두지 않는다** — 서비스 간 동기 호출이 생긴다(위 "경계를 넘는 동기 호출을 새로 만들지 않는다").
  나중의 키 교체를 위해 JWT 헤더에 **`kid`**를 넣는다.
- **라이브러리** — Spring Security의 `oauth2-resource-server`(Nimbus). 이 앱은 `NimbusJwtEncoder`로 서명하고, 검증하는 서비스는 `NimbusJwtDecoder.withPublicKey()` +
  **쿠키에서 토큰을 꺼내는 `BearerTokenResolver`**를 쓴다. jjwt 등을 따로 들이지 않는다.
- **클레임** — `sub`(**사용자 번호를 십진 문자열로** — `"42"`. 로그인 아이디가 아니다, 2026-09-22 소유자 결정 · §3.5) · `iss` · `iat` · `exp` · `jti` **만**(여기에 쓰임새를 가르는 `token_use`가 더해졌다 — 아래). 닉네임처럼 바뀌는 값은 넣지 않는다. `jti`는 나중에 denylist를 두게 될 때 쓸 자리다((라) — 지금은 두지 않는다).
- **클레임이 하나 늘었다 — `token_use`**(2026-09-21 소유자 확정). 값은 access 토큰이 `access`, 소셜 가입 대기 토큰이 `social_signup` **둘**이다(원본 상수는 `common/security/TokenClaims`. 2026-09-25 합치기 2단계로 값 하나가 없어졌다 — P-22). **같은 키로 서명하기 때문에 검증하는 쪽은 서명 · `iss` · `exp`에 더해 `token_use`가 기대한 값인지 반드시 본다** —
  안 보면 한쪽을 다른 쪽으로 쓸 수 있다. **`token_use` 클레임 자체는 옆 서비스와의 약속이라 남는다.** JOSE 헤더의 `typ`을 쓰지 않은 이유 — Spring Security의 기본 디코더가 `typ`이 `JWT`가 아니면 거절해서 검증하는 옆 서비스가 전부 설정을 바꿔야 한다(`contracts/platform-api.md` "access 토큰").
- **검증하는 쪽은 `sub`가 사용자 번호(숫자 문자열)인지도 본다** — `^[0-9]{1,19}$`(원본 상수 `common/security/TokenClaims.SUBJECT_PATTERN`. 이 앱은 `JwtConfig#jwtDecoder`에서 한다). 아니면 컨트롤러에 닿기 전에 401이다. 옆 서비스가 붙일 검증도 같은 모양이다(이 검사 자체는 Claude가 정했다).

**(나) 쿠키 속성.**
- `HttpOnly` 켠다. `Secure`는 **환경변수로 켜고 끈다**(운영은 켠다). **`SameSite=Lax`** — `Strict`면 외부 링크로 들어온 첫 화면이 로그아웃 상태로 보인다. `Path=/`.
- **`Domain`은 지정하지 않는다**(host-only). 한 도메인이라 충분하고, 로컬에서는 쿠키가 포트를 가리지 않아 8080~8083에 전부 간다.
- access 쿠키의 수명은 **토큰 수명과 같다.** **refresh 쿠키(`qm_refresh`)의 `Path`는 재발급 경로 하나로 좁혔고**(`/api/v1/auth/refresh`) `Max-Age`는 토큰 수명(7일)이다 — 나머지 속성은 access와 같다((마)).

**(다) CSRF — `SameSite=Lax` + `Origin` 헤더 검사.**
- **POST/PUT/PATCH/DELETE에서 `Origin` 헤더를 검사한다.** `SameSite`만으로는 모자라다 — 출처가 아니라 **사이트 단위**라 서브도메인을 못 막는다.
- **CSRF 토큰은 쓰지 않는다** — stateless인 네 서비스가 각자 발급 · 검증해야 하고 프런트도 매번 실어야 한다. `Origin` 검사는 필터 하나라 네 서비스에 똑같이 들어간다.
- **전제 — 상태를 바꾸는 GET을 만들지 않는다.** 이 전제가 깨지면 `SameSite=Lax`도 `Origin` 검사도 그 요청을 막지 못한다.
- 옆 서비스에도 걸리는 결정이다(매칭 요청 등) — 그쪽 적용은 그 폴더의 일이다. (시그널 `POST` · 방 입장은 2026-09-25 부터 이 앱의 요청이라 이미 걸려 있다.)

**(라) 수명과 denylist — access 15분 · refresh 7일이다**(2026-09-23 소유자 결정 · **구현됐다**).
- **access 수명은 `ACCESS_TOKEN_TTL` 기본값 `PT15M`이고, 그것을 이어 주는 것이 refresh(`REFRESH_TOKEN_TTL` 기본값 `P7D` — 7일)다**((마)).
  access 만으로 돌던 때의 개발 기본값 24시간과 그 **`TEMP-NO-REFRESH` 표식은 없어졌다** — `grep -rn "TEMP-NO-REFRESH" backend/src`가 **0건**이다(`contracts/platform-api.md` "access 토큰" · "refresh 토큰" · P-15).
- **access denylist는 두지 않는다 — docs/11 #16("Redis denylist, 조회 실패 시 fail-closed")을 개정하는 것이다.** 두면 네 서비스의 모든 요청이 Redis를 조회하고, fail-closed라
  Redis가 죽으면 전부 401이 되며, JWT를 스스로 검증하는 이점이 사라진다. **로그아웃은 refresh 삭제 + 쿠키 제거**이고 **남는 최대 15분은 감수한다** — 전에는 24시간이었다. **그 창이 줄어든 것이 이번 변경의 이득이다.**
- **한 사용자의 refresh를 한꺼번에 끊는 길은 없다** — 사용자별 토큰 집합을 두지 않았고 `KEYS`/`SCAN`을 쓰지 않는다(`contracts/platform-api.md` "refresh 토큰"). 그래서 계정이 털렸을 때
  **모든 기기를 로그아웃시킬 수 없다.** 둘지부터가 미정이다 → §7.
- **프런트가 access 만료 전에 재발급을 불러야 한다 — 서버 쪽 장치는 없다**((바)와 같은 방식이다). 프런트가 이 컴퓨터에 없어 **맞춰 본 적이 없다** → §7.

**(마) refresh의 Redis 키와 재발급** (2026-09-23 소유자 결정 · **구현됐다.** 원본은 `contracts/platform-api.md` "refresh 토큰" · P-15).
- 키 **`qm:auth:refresh:{uuid}`** → 값 `userId`(사용자 번호). 접두사 `qm:auth:*`는 매칭의 `qm:user:*`와 겹치지 않는다. 접두사는 **상수 한 곳에만** 둔다 — **`common/security/RefreshTokens`**다.
- **JWT가 아니라 불투명 UUID다** — 값에 아무 뜻이 없고 Redis의 줄이 사라지면 그 자리에서 못 쓴다. **서버가 무효화할 수 있는 것은 이쪽 하나다**((라) — access는 denylist가 없다).
  **쿠키 이름 `qm_refresh`의 상수는 `TokenClaims`가 아니라 `RefreshTokens.COOKIE`에 있다** — 옆 서비스와의 약속이 아니라 이 앱만 읽는 값이기 때문이다(`SocialSignupTokens.COOKIE`와 같은 자리다).
- rotation은 **`GETDEL` 한 번**으로 원자적으로 한다(`조회 → 판단 → 삭제`가 아니다). 기기 수는 제한하지 않는다(토큰마다 키 하나).
- **재발급은 `POST /api/v1/auth/refresh`다** — 본문 없이 `qm_refresh` 쿠키로만 받고, 성공하면 소셜 가입과 **같은 본문**(`{userId, nickname}` — 2026-09-26에 `loginId`가 빠졌다)에 **새 쿠키 둘**을 싣는다.
  **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN`이다** — 쿠키가 없든 · 꼴이 아니든 · Redis에 없든 · 이미 쓴 값이든 · 그 사용자가 사라졌든 · Redis를 못 읽었든 **본문이 글자까지 같다**(어느 쪽인지 알려 주면 그 값이 살아 있는지가 새어 나간다).
  **실패할 때도 refresh 쿠키를 지워 준다**(`Max-Age=0`) — access 쿠키는 건드리지 않는다(아직 살아 있을 수 있다). 옛 값을 다시 쓰면 **그냥 401이다** — 탈취 감지(토큰 계보 추적)는 넣지 않는다.
  (경로의 이름 · 실패를 401 하나로 합친 것 · 실패에도 쿠키를 지우는 것은 **Claude가 정했다** — P-15.)
- **로그아웃은 Redis의 그 줄과 쿠키 둘(`qm_access` · `qm_refresh`)을 지운다.** **쿠키가 없어도 · Redis가 죽어 있어도 204다** — 그때 그 refresh는 수명이 다할 때까지(최대 7일) 살아 있다.
- **Redis가 죽었을 때** — 소셜 로그인 · 소셜 가입은 **그대로 성공하고 access만 나간다**(로그인이 Redis에 묶이지 않게 한다 — 없어진 로그인 실패 제한이 Redis 장애에 통과시키던 것과 같은 원칙이다. 그 사람은 access 15분이 끝나면 다시 로그인해야 한다).
  **재발급은 401이다**(fail-closed — 확인하지 못한 값을 통과시키면 폐기된 토큰도 통과한다). 로그아웃은 204. (이 갈림도 **Claude가 정했다** — P-15.)
- **"로그인시킨다 = 쿠키 둘"은 한 곳에서 한다** — `common/security/SessionCookies`다(소셜 로그인 · 소셜 가입 · 재발급이 같이 쓴다). **refresh 저장이 실패하면 access 하나만 준다.**

**(바) SSE와 토큰 만료.** `notification`은 **연결할 때만** 검증한다. 이미 열린 연결은 토큰이 만료돼도 끊지 않는다. 재접속이 401로 멈추면(`EventSource`는 200이 아닌 응답에 재접속을 멈춘다)
프런트가 `onerror`에서 `readyState === CLOSED`를 보고 **재발급한 뒤 `EventSource`를 새로 만든다.** 서버 쪽 장치는 두지 않는다.

**(사) 로컬 CORS — 서비스에 CORS 설정을 넣지 않는다.** **프런트 개발 서버의 프록시**가 경로별로 8080~8083에 나눠 보낸다 — 운영이 같은 출처라서다. (다)의 `Origin` 검사도 로컬과 운영이 같은 모양이 된다.
프런트가 이 컴퓨터에 없어 개발 서버가 무엇인지는 모른다.

**(아) 임시 식별(`?userId=`)에서의 전환.** 1단계가 끝나 **로그인이 도는 것을 본 뒤**, 서비스별로 따로 옮긴다. 순서는 **`notification` → `matching`**이다(맨 앞이던 `room`은 2026-09-25 에 이 앱에 합치며 쿠키로 바뀌었다 — P-22). **→ 2026-09-27 에 둘 다 했다**(`notification` 은 그 폴더 `CLAUDE.md` §5.1, `matching` 은 `HANDOFF.md` §0-5 — docs/11 D-24 의 적용. 개발용 스위치는 두지 않았다).
각 서비스에 "쿠키가 없으면 `userId` 파라미터를 받는" 개발용 스위치를 잠깐 남겨도 된다 — **임시 처리로 표시하고 운영에서는 끈다.** 바꾸는 작업은 각 폴더에서 한다(§9).

**이 절의 "남은 것"은 정해졌다(2026-09-21 소유자 확정. 원본은 `contracts/platform-api.md` "공통" · "access 토큰" · P-2 — docs/11 D-24로 남겼다, 2026-09-26).**

| 남아 있던 것 | 정해진 값 |
|---|---|
| 쿠키 이름 | **`qm_access`** |
| `iss` 값 | **`queuemate-platform`** |
| RSA 키 길이 | **2048** |
| 키를 담는 환경변수 | 개인 키 **`JWT_PRIVATE_KEY`**(PKCS#8 PEM — 이 앱만) · 공개 키 **`JWT_PUBLIC_KEY`**(X.509 PEM — 옆 서비스도 받는다). 하나만 주면 기동하지 않는다 |
| `kid` 값을 매기는 법 | 환경변수 **`JWT_KEY_ID`**(기본값 `dev-1`). 키를 바꿀 때 값을 올린다 |
| `Origin` 허용 목록을 받는 설정 | **`ALLOWED_ORIGINS`**(쉼표로 구분. 기본값 `http://localhost:5173,http://localhost:3000`). 허용 목록에 없으면 403 `ORIGIN_NOT_ALLOWED`. **`Origin`이 없는 요청(curl · 서버 사이)은 통과한다** |
| access 수명 | **`ACCESS_TOKEN_TTL`** 기본값 **`PT15M`** — 2026-09-23 소유자 결정으로 `PT24H`(`TEMP-NO-REFRESH`)에서 줄었다. 이어 주는 것은 refresh다((마)) |
| (2026-09-23에 정해진 것) refresh의 수명과 쿠키 | **`REFRESH_TOKEN_TTL`** 기본값 **`P7D`**(7일) · 쿠키 **`qm_refresh`**(`Path=/api/v1/auth/refresh` · `Max-Age` = 토큰 수명) · 재발급은 **`POST /api/v1/auth/refresh`** · 실패는 401 **`INVALID_REFRESH_TOKEN`** |
| 토큰의 쓰임새를 가르는 법 | **`token_use` 클레임**((가) — 값은 `access` · `social_signup`) |
| (같이 정해진 것) `Secure`를 켜고 끄는 환경변수 | **`COOKIE_SECURE`**(기본값 `false`, 운영은 `true`) |

- **개발용 키.** `JWT_PRIVATE_KEY` · `JWT_PUBLIC_KEY`가 **둘 다 비어 있으면** 개발용 키를 **`backend/.dev-keys/`**에 만들어 다시 쓴다(경고 로그를 남긴다. `.gitignore`에 있다 — **개인 키가 들어 있으니 절대 올리지 않는다**).
  **옆 서비스는 그 폴더의 `public.pem`으로 검증한다.** 운영에서는 반드시 둘 다 넣는다(Secrets Manager). 테스트는 임시 폴더를 쓴다.
- **"상태를 바꾸는 GET을 만들지 않는다"의 예외가 하나 생겼다** — 소셜 로그인의 콜백이다. OAuth가 GET을 강제한다. `state` 검증이 그 자리를 지킨다(`contracts/platform-api.md` "소셜 로그인"). 목록 조회의 옮겨 적기는 §3.3.
- ~~**로그인 실패 제한**~~(2026-09-21 — 계정 단위 · 15분 안에 5번 틀리면 잠그고 두 배씩 · 429 `TOO_MANY_LOGIN_ATTEMPTS` · 세는 열쇠는 로그인 아이디 — P-10) — **2026-09-26에 통째로 없어졌다**(소유자 결정 — P-24. 틀릴 비밀번호가 없다). IP 단위의 제한을 앞단(CloudFront/WAF)의 일로 둔 것은 그대로다.
- **refresh 토큰이 붙었다(2026-09-23 소유자 결정 · 구현됐다).** (라) · (마)가 설계로만 적어 두었던 것이 코드가 됐고 **`TEMP-NO-REFRESH` 표식이 없어졌다**(`grep -rn "TEMP-NO-REFRESH" backend/src`가 0건이다). **남은 것** — 한 사용자의 refresh를 한꺼번에 끊는 길(모든 기기 로그아웃)과 프런트의 재발급 흐름이다(§7 · `START_HERE.md` §4 A).
- **옆 서비스의 전환((아))은 끝났다** — `notification` · `matching`도 2026-09-27 에 `?userId=`를 버리고 쿠키 `qm_access`를 이 앱의 공개 키로 검증한다(각 폴더에서 했다 — `notification` `CLAUDE.md` §5.1 · `matching` `HANDOFF.md` §0-5). (옛 `room`은 이 앱에 합치며 쿠키로 바뀌었다 — P-22.)
- **개발용 로그인(임시 — `TEMP-DEV-LOGIN`) — 2026-09-29 소유자 결정**(`contracts/platform-api.md` "개발용 로그인" · P-34). 소셜 앱 키가 없어 아무도 로그인할 수 없는 동안 `POST /api/v1/auth/dev-login {nickname?}`(없으면 `dev-tester`)이 **그 닉네임의 사용자로(없으면 만들어서) 진짜 쿠키 둘**을 준다 — `SessionCookies` 를 거치는 소셜 가입 · 재발급과 같은 길이다. 소셜 연결은 만들지 않는다.
  **설정 `platform.auth.dev-login-enabled`(환경변수 `DEV_LOGIN_ENABLED`) — 기본 `false`.** 꺼져 있으면 컨트롤러 빈이 없어 **없는 경로와 같은 404 `NOT_FOUND`** 다. POST 라 `Origin` 검사를 그대로 받는다.
  **(아)의 "쿠키가 없으면 `userId`를 받는" 개발용 스위치와 다르다** — 그것은 검증하는 쪽이 토큰 대신 파라미터를 믿게 하는 것이고(루트 규칙이 금지한다), 이것은 **발급하는 요청 하나**다. 검증하는 쪽(이 앱의 필터 · `matching` · `notification`)은 여전히 서명된 진짜 토큰만 받는다.
  **대가 — 켜 두면 누구든 아무 닉네임(남의 계정 포함)으로 로그인한다. 운영에서 켜지 않는다**(§11). 걷어낼 때는 `grep -rn TEMP-DEV-LOGIN` 이 가리키는 것(컨트롤러 · 서비스 · 본문 · 설정 한 줄 · 테스트 설정 한 줄 · 테스트 둘)을 지운다 — 시점은 소유자가 정한다.

## 6. 배포 기준

배포 기준은 **Stage 2(ECS Fargate)**다. Stage 1(단일 EC2 + Docker Compose)은 적용하지 않는다 (docs/11 D-18이 #20을 개정). **k8s / HPA / sticky session을 전제한 구현 금지**는 그대로다.

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라. 정해지면 이 표에서 빼고 해당 절로 옮긴다.

> **2026-09-21에 이 표에서 많은 것이 빠졌다** — 소유자가 "네가 만들어 봐라"고 맡겨 Claude가 정해 구현했기 때문이다(머리의 "지금 상태와 그 지위"). 빠진 것의 원본은 `contracts/platform-api.md`이고 **소유자가 항목별로 검토하지 않았다**(Claude가 정한 것이라 docs/11에도 없다 — 2026-09-26 에 docs/11 D-24 ~ D-35로 올라간 것은 소유자가 정한 것이다).
> **아래 남은 것에는 위 규칙이 그대로 걸린다 — 묻고 정한다.** 다음에 닿기 전에 물을 것을 추린 목록은 `START_HERE.md` §4다.

| 항목 | 상황 |
|---|---|
| **소유자의 검토 — `contracts/platform-api.md` P-1~P-30** | 미정은 아니지만 **확정도 아니다.** P-1~P-10은 Claude가 정해 구현한 것 전부다(엔드포인트 · 토큰 · `roomId` · 만석 표시 등. **P-3 · P-5 · P-6 은 2026-09-25 에 걷어냈다** — 10분 보존은 P-20, 나머지는 `room` 합치기 2단계(P-22) · 소셜 로그인의 "처음 오면 로그인 아이디를 정한다" · 게임 프로필과 글의 `voice`/`purpose`/`conditions`(`purpose`는 2026-09-27에 없어졌다 — P-29) · 친구/신고/알림 둘 · 로그인 실패 제한(P-10 — **2026-09-26에 물음째 없어졌다**, P-24)). **P-11 ~ P-29는 소유자가 직접 정한 것이고, 2026-09-26 · 2026-09-27 에 docs/11 D-24 ~ D-40으로 남겼다**(대응 — P-2 → D-24 · P-11 → D-25 · P-15 → D-26 · P-12 · P-13 → D-27 · P-14 · P-20 · P-21 → D-28 · P-16 → D-29 · P-17 → D-30 · P-18 → D-31 · P-19 → D-32 · P-22 → D-33 · P-23 → D-34 · P-24 → D-35. 각 항목 — P-11 식별자 · P-12 전적 스냅숏의 한 테이블 · P-13 전적 동기화(**2026-09-24에 절반이 개정됐다** — 모집 글을 쓸 때 긁던 시점과 신선도를 없앴다. D-27에 그 사실까지 같이 적혔다) · P-14 목록의 페이지 나누기 · **P-15 refresh 토큰** · **P-16 gameconfig를 읽어 `mode` · `tier`를 검증하는 것** · **P-17 전적 갱신 요청**(2026-09-24 — 동기 · 쿨타임 2분 · 상한 30초. P-13을 뒤집지 않고 긁는 시점을 둘로 늘린다) · **P-18 `filledPositions`를 없앤 것**(2026-09-24 — **docs/11 D-20의 ③을 개정한다.** ①②④는 그대로다) · **P-19 방에 방장 말고 누가 있으면 글을 고칠 수 없는 것**(2026-09-24 — **개정하는 D-항목이 없다. 새 규칙이다**) · **P-20 게시판 목록의 보존 기간을 없앤 것**(2026-09-25 — **P-5가 정했던 10분 보존을 걷어낸다.** 끝난 글이 목록에 계속 남고 설정 `closed-retention`이 없어졌다) · **P-21 게시판 목록의 `game`을 필수로 만든 것**(2026-09-25 — **게시판은 게임별로 나뉜 페이지다.** 개정하는 D-항목이 없다 — 새 규칙이다) · **P-22 `room` 앱을 합친 것**(2026-09-25 — 합치기 · ① 입장 경로 안에서 글을 검사 · C 글 쓰기가 방을 만든다는 소유자 결정이고, 창구 이름 · 순서 · 자가 치유 · 에러 코드 통일 · 트랜잭션 안 Lua 예외는 **Claude가 정해 검토가 남았다.** **docs/11 D-16 · D-19 ~ D-23을 개정한다** — §3.3) · **P-23 DB 스키마를 `public` 하나로 합치고 JOIN · FK를 허용한 것**(2026-09-26 — **docs/11 #17 · D-1 · 2026-09-22의 "스키마별 DB 롤을 두지 않는다" · WHY_POSTGRESQL §3을 개정한다.** `matching`의 `Block.java`에서 `schema = "social"`을 뺐다 — 2026-09-26 그 폴더에서 · §3.5) · **P-24 직접 가입 · 비밀번호 로그인을 없애고 소셜 로그인만 남긴 것**(2026-09-26 — `loginId` · 비밀번호 · `credentials` · 로그인 실패 제한이 없어졌다. **P-7의 "아이디 · 닉네임" · P-10 전부 · P-11의 `loginId` 절반 · docs/00의 계정 정의 · docs/11 D-25의 절반을 개정한다**) · **P-25 확정된 방이 없어질 때 파티가 닫히는 것**(2026-09-26 — 새 규칙이다. `PartyClosed.fifo` 없이 `recent_players`를 채운다. docs/11 **D-36** — §3.3) · **P-26 LoL 게임 계정의 티어를 Riot에서 채우는 것**(2026-09-27 — 처음에는 주 포지션도였다가 같은 날 되물렸다 · 주 포지션은 사용자가 정한다 · `PUT`이 동기가 됐다. P-8 · P-13 · P-17을 개정한다. docs/11 **D-37** · §7 "게임 계정 연동") · **P-27 소셜 계정 잇기 · 끊기**(2026-09-27 — D-38 · 구현됐다) · **P-28 자동 매칭이 게시판 방에 먼저 합류하는 길**(2026-09-27 세부 넷 — D-40 · **2026-09-28 소유자가 경로 · 본문 · 없을 때 · 정원 · 칸 없음을 정하고 Claude 가 구현했다** — `POST /api/v1/posts/auto-join`. Claude 가 정한 세부는 계약 그 절 — 검토 항목) · **P-29 글에서 `purpose`를 없앤 것**(2026-09-27 — D-39 · P-8을 개정한다) · **P-30 자동 매칭 파티의 방**(2026-09-27 — **Claude 가 정한 세부다.** 소유자 결정 D-42(SQS 없음 · 이 앱이 파티 HASH 를 읽는다) 위에 경로 `POST /api/v1/match-parties/{partyId}/room` · 응답 · 에러 코드 · `roomId = partyId` · V2 `match_party_id` · DB 먼저 Lua 뒤 · fail-closed 를 정했다 — **검토가 남았다**). **§2 · §11의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15를 개정한다** — §3.6). **P-11**(PK는 bigint identity · `userId`는 사용자 번호 · `loginId`는 따로 — `loginId`는 2026-09-26에 없어졌다)은 2026-09-19의 결정 · D-4를 개정하는 것이다 — `matching`의 `Block.java`도 그 폴더에서 `Long`으로 바꿨다(2026-09-26). 소유자가 뒤집으면 코드 · 계약 · 이 파일을 같이 고친다. **계약 원본(본 저장소 `feature/frontend`)에 platform 엔드포인트가 이미 있으면 그쪽과 맞춰야 한다**(P-1). **docs/11에 올리는 것은 `matching` 폴더의 일이고 2026-09-27 까지 전부 했다**(D-40까지) |
| **자동 매칭이 게시판 방에 합류하는 길**(2026-09-23 소유자 결정으로 방향이 · **2026-09-27 소유자 결정으로 세부 넷이** · **2026-09-28 소유자 결정으로 경로 · 본문 · 없을 때 · 정원 · 칸 없음이 정해져 Claude 가 구현했다** — `POST /api/v1/posts/auto-join`. `contracts/platform-api.md` "자동 매칭이 게시판 방에 먼저 합류하는 길" · P-28 · docs/11 **D-40**) | **두 경로를 다 둔다** — "매칭 시작"을 누르면 ① **조건이 맞는 열린 게시판 방이 있으면 거기에 넣고** ② 없으면 기존 대기열 매칭으로 간다(`matching`의 대기열·제안·수락·확정은 **그대로 살린다**). 이유는 **콜드 스타트**다. **정해진 세부 넷(2026-09-27)** — ① **경로** — `platform`에 "게시판 방에 먼저 합류" 요청 하나. 맞는 방이 있으면 넣고 그 방 번호를 준다, 없으면 404 → **프런트가** `matching`의 기존 매칭 요청을 부른다(서버가 `matching`을 부르지 않는다 — §5 경계를 넘는 동기 호출 금지) ② **조건** — 글의 `game` · `mode` · `voice` · PUBG `conditions.perspective`가 요청과 같고 · 내 티어가 그 모드의 티어 범위(gameconfig `qm:gameconfig:{GAME}:tier-range:{MODE}` — **이 앱이 읽는 gameconfig 가 늘었다**, §3.6 · P-16 개정. **방장의 티어**의 줄로 본다) 안이고 · 글의 `wantedPositions`에 내 주 포지션이 있어야 한다(빈 배열이면 통과). **`purpose`는 보지 않는다**(글에서 없어졌다 — P-29) ③ **여럿이면** 가장 오래된 방(`id` 작은 순). 들어가다 만석 · 확정이면 다음 방 ④ **활성 요청 키를 만들지 않는다** — "매칭 시작"을 누른 순간 **한 번만** 게시판을 본다. 그 뒤 대기열에 들어간 사람은 새로 열리는 게시판 방을 모른다(대기열은 `matching`의 키라 `platform`이 못 뺀다 — D-19 그대로). 놓친 방은 사용자가 게시판에서 직접 본다. **2026-09-28 소유자 결정(구현됐다)** — 경로 **`POST /api/v1/posts/auto-join`** · 본문은 `matching` 의 `CreateMatchRequestCommand` 와 같은 모양(`playPurpose` 는 받되 무시 · 티어 · 포지션은 본문의 자기신고 — 티어의 400 은 `matching` 과 같은 순서 · 티어가 없는 사람은 `EXIST` 모드면 400 · 포지션이 `NONE` 이거나 없으면 빈 `wantedPositions` 만) · 200 `{postId, roomId}` · 없으면 404 `NO_MATCHING_POST` · `IN_OTHER_ROOM` · `ALREADY_QUEUED` 는 409 로 끊는다 · Redis 를 못 읽으면 503(fail-closed) · **정원은 그 모드의 `targetPartySize`** · **"자동 합류 허용" 칸은 두지 않는다.** Claude 가 정한 세부(방장 티어만 · PUBG `PLATFORM` 값을 방장 `server` 와 대조하지 않음(**미정**) · 내 글 제외 · 후보 상한 `platform.board.auto-join-scan` 50 등)는 계약 그 절 — 소유자 검토 항목 |
| **파티 모집 게시판에 남은 세부** | 하는 것·방의 규칙(docs/11 D-11 · D-16), 방 키(§3.3), 목록과 차단의 범위(D-20), 방장 확정의 규칙(D-21)에 이어 **`roomId` · 확정된 글에서 방장 키가 없을 때 · ~~만료/확정 글의 보존(10분)~~(**그 10분은 2026-09-25 소유자 결정으로 없어졌다 — 끝난 글이 목록에 계속 남는다. P-20**) · 만석 표시 · 글의 내용과 정렬 · 카드에 담는 것**도 정해졌고, **목록의 페이지 나누기(커서 방식)**가 더해졌다(2026-09-23 소유자 결정 · P-14 — `contracts/platform-api.md` "목록의 페이지 나누기" · §7.1 "정해진 것"). **2026-09-25 에 `room`을 합치며 글 쓰기 = 방 만들기 · 입장 안의 글 검사 · 한 요청의 확정이 됐다**(P-22 · §3.3). **남은 것** — ~~글을 지우거나 방장이 나갈 때 방과 글을 같이 닫을지~~(2026-09-25 소유자 결정으로 정해졌다 — "방과 글은 같이 산다", §7.1), 확정된 방의 기능(Ready 등)(~~"최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가~~ — **2026-09-26 소유자 결정으로 정해졌다**: 확정된 방이 없어질 때 파티가 닫히고 그 순간의 파티원끼리 적는다, §3.3 "파티 닫힘" · P-25), **게시판 채널 이름의 원본을 둘 곳·재요청을 묶는 간격**(D-20 · D-22), 차단에 남은 경쟁, 목록의 필터(지금은 `game` 하나이고 **2026-09-25로 필수가 됐다** — P-21. **페이지 나누기는 정해져 구현됐다**), 도배 대응, 자동 매칭 파티의 방(§7.2). 해당 지점에 닿으면 그때 묻는다 |
| ~~`PARTY_*` 등 알림 다섯의 이름과 `payload`, `parties` 자원~~ — **2026-09-28 소유자 결정으로 닫혔다(P-31 · docs/11 D-44)** | `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` 둘은 정했다(§3.2). **`PARTY_*` 는 두지 않는다 · 확정된 파티를 조회하는 경로(`parties`)도 두지 않는다** — 파티가 생기는 계기는 프런트가 `MATCH_CONFIRMED` 를 받아 스스로 방 만들기를 부르는 것이라 따로 알릴 것이 없고, 방 안의 일은 `ROOM_*` 5종이 맡고, 닫힘은 받을 사람이 방에 없다. 조회 경로는 읽을 화면이 없다 — 프런트 화면이 정해지면 그 모양대로 그때 만든다 |
| ~~SQS 메시지 본문 3종 · SQS 배선 시점~~ · ~~**자동 매칭 파티의 id**~~ — **2026-09-27 에 정해졌다(docs/11 D-42 · P-30)** | **SQS 가 없어져 메시지 본문 · 배선 시점 · AWS SDK · 로컬 흉내 · outbox 의 물음이 통째로 없어졌다**(§3.4). **자동 매칭 파티의 id** — `matching` 의 UUID `partyId` 를 새 칸 **`parties.match_party_id`(varchar(36) UNIQUE · V2)** 에 담는다. `parties.id` 는 그대로 bigint 이고 **`roomId = partyId`** 다(§3.3 · `contracts/platform-api.md` "자동 매칭 파티의 방"). "파티는 비동기로 생기므로 그 직후 조회는 비어 있을 수 있다" 는 사정도 없어졌다 — 파티는 프런트가 이 앱을 부르는 그 호출에서 생긴다. ~~**파티가 "닫혔다"를 무엇으로 판단하는가** — 미정~~ → 게시판 파티(2026-09-26 — P-25)도 자동 매칭 파티(2026-09-27 — P-30)도 확정된 방이 없어질 때 나가기 콜백으로 닫는다. **남은 것** — ~~전원이 말없이 사라져 키만 만료된 자동 매칭 파티는 닫히지 않는다~~(2026-09-28 소유자 결정 — 그 게임의 목록 GET 이 `match_party_id` 로 골라 방 키를 읽어 닫는다, §3.3 "파티 닫힘") · ~~`playPurpose` 를 `parties` 에 담을지~~(2026-09-28 소유자 결정 — 담지 않는다. 칸을 두지 않는다) |
| ~~**확정된 사용자를 푸는 길**~~ — **2026-09-27 에 정해졌다(docs/11 D-42)** | **아무도 지우지 않는다.** `matching` 이 확정 때 활성 요청(`status=PARTY`)과 수락자 SET 에 **60초** 수명을 걸고(파티 HASH 는 600초), 그 안에 사용자가 이 앱의 `POST /api/v1/match-parties/{partyId}/room` 으로 방에 들어오면 **입장 표시 키가 자물쇠를 이어받는다**(`claim-request.lua` 가 이미 `KEYS[2]` 로 본다 — D-19). 60초 뒤 사용자는 새 매칭을 걸 수 있다. §7.2 (라) 의 방향 그대로다. 이 앱은 여전히 활성 요청 키를 쓰지도 지우지도 않고, 이 요청의 Lua 는 그 키를 **읽지도 않는다**(P-30 — 그 키가 곧 이 파티라 `ALREADY_QUEUED` 로 거절하면 아무도 못 들어온다). 후보 셋 가운데 "긴 TTL" 이 아니라 "짧은 TTL + 입장 표시 키" 다. **감수** — 60초 안에 입장 표시 키가 찍히지 않은 사람은 그 사이 새 매칭을 걸 수 있다(창이 작다). **입장 표시 키는 부른 사람에게만 찍힌다**(D-42 4번의 "전원에게" 와 다르다 — 소유자 검토 항목, P-30) |
| **운영의 DB 롤**(예전 이름 "테이블 컬럼, DB 롤" — 코드의 주석이 그 이름으로 가리킨다) | 테이블 컬럼은 정해졌다(§3.5 — 원본은 마이그레이션). **스키마별 DB 롤은 두지 않는다**(2026-09-22 소유자 결정. 롤을 만드는 마이그레이션도 `CREATEROLE` 문제도 없어졌다. **2026-09-26 에 스키마가 `public` 하나가 되며 스키마별 롤이라는 물음 자체가 없어졌다** — §3.5 · P-23). **남은 것** — 이 앱이 운영에서 붙는 DB 계정의 이름과 권한(로컬은 `postgres` 슈퍼유저다. `matching`도 별도 롤 없이 같은 방식으로 붙는다 — 그 계정은 `matching` 폴더의 일이다), 마이그레이션 계정과 앱 계정을 나눌지 |
| **옆 서비스의 전환** · refresh에 남은 것 | 인증 세부와 그 "남은 것"은 전부 정해졌고 구현됐다(§5.1). **refresh 토큰도 붙었다**(2026-09-23 소유자 결정 — §5.1 (라) · (마) · P-15). **남은 것** — ① `notification` → `matching`의 전환(각 폴더의 일 — 둘 다 안 됐다. `room`은 2026-09-25 에 이 앱에 합치며 끝났다)과 `Origin` 검사를 두 서비스에 언제 넣는가 ② **한 사용자의 refresh를 한꺼번에 끊는 길**(사용자별 토큰 집합을 두지 않았고 `KEYS`/`SCAN`을 쓰지 않는다 — 계정이 털렸을 때 **모든 기기를 로그아웃시킬 수 없다.** 둘지부터가 미정이다) ③ **프런트의 재발급 흐름**(access가 만료되기 전에 프런트가 `POST /api/v1/auth/refresh`를 불러야 하고 **서버 쪽 장치는 없다.** 프런트가 이 컴퓨터에 없어 맞춰 본 적이 없다). **docs/11에는 2026-09-26 에 남겼다** — §5.1은 시스템 전체에 걸리고 #16(access denylist)을 개정한다 — D-24. refresh(P-15)는 D-26이다 |
| **소셜 로그인에 남은 것** | 넣는다는 것은 소유자 지시다(2026-09-21). **2026-09-26부터는 유일한 가입 · 로그인이다**(소유자 결정 — P-24. 직접 가입 · 비밀번호 로그인이 없다). 흐름은 `contracts/platform-api.md` "소셜 로그인"(Claude가 정했다 — P-7. docs/00의 계정 정의에 걸린다 — P-24가 그 정의를 개정한다). **가짜 제공자로만 테스트했다 — 실제 키로는 붙여 보지 않았다.** 카카오 · 디스코드 · **구글**(2026-09-29 — P-33)의 앱 등록 · 키 · Redirect URI 등록은 **소유자가 해야 하고, 이제 필수다 — 그것 없이는 아무도 로그인할 수 없다**(로컬에서 손으로 해 볼 때는 **개발용 로그인**을 켜면 된다 — `DEV_LOGIN_ENABLED=true` · 임시 · §5.1 끝 · `START_HERE.md` §6). 구글의 Redirect URI 는 로컬 기본값이면 `http://localhost:8082/api/v1/auth/oauth/GOOGLE/callback` 이다(대문자 — 다른 제공자와 같다). **구글도 가짜 제공자로만 테스트했다.** ~~**하지 않은 것** — 이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기(할지부터가 미정이다 — 그래서 한 사람이 두 제공자로 따로 오면 사용자가 둘 생긴다)~~ → **2026-09-27 소유자 결정으로 정해져 구현됐다**(P-27 · docs/11 D-38 · `contracts/platform-api.md` "소셜 로그인" 의 "잇기 · 끊기") — 로그인한 채 다른 제공자로 오면 같은 사용자에 잇고 `DELETE /api/v1/users/me/social/{provider}`로 끊는다(마지막 하나는 409 `LAST_SOCIAL_IDENTITY`). **자동으로 중복을 잡지는 않는다** — 로그인하지 않은 채 다른 제공자로 오면 여전히 새 사용자이고, 이미 따로 생긴 두 사용자를 합치는 길은 없다. ~~소셜 가입자가 비밀번호를 만드는 것~~은 2026-09-26에 물음째 없어졌다(비밀번호가 없다). 프런트의 경로(`/` · `/signup/social` · `/login?error=OAUTH_FAILED` · 잇기의 `/settings?linked=…` · `/settings?error=…`)는 프런트와 맞춘 적이 없다 |
| **게임 계정 연동 — 게임사 API** | **자기신고와 그것을 담을 자리까지 됐다**(2026-09-21) — 게임 계정(게임 닉네임 · 티어 · PUBG의 서버)은 사용자가 적는다. **2026-09-27 소유자 결정으로 LoL은 이름#태그 · 주 포지션만 적는다**(P-26 — 아래. 티어는 Riot에서 온다). **2026-09-29 소유자 결정으로 주 포지션은 어느 게임에서도 없어졌다**(P-35 — LoL 은 이름#태그 하나다. `mainPosition` 을 보내면 400). **LoL의 전적을 Riot API에서 긁는 것은 정해져 구현됐다**(2026-09-23 소유자 결정 · `contracts/platform-api.md` "전적을 긁는 것" · P-13) — **한 시점은 게임 계정을 연결 · 수정할 때**(`PUT /api/v1/users/me/game-accounts/{game}`)다. 그때는 **커밋된 뒤에 비동기로** 돌았고 실패해도 게임 계정 저장은 성공이었다.
**2026-09-27 에 바뀌었다(소유자 결정 · P-26)** — **LoL의 `PUT`은 본문이 `{gameNickname, mainPosition}`이고(포지션은 선택 · `tier` · `server`를 보내면 400 — **2026-09-29 부터 `{gameNickname}` 하나다**, P-35) 저장하기 전에 동기로 긁는다**(상한 30초 — 전적 갱신과 같은 길). 긁어 오는 것이 `stats`(최근 선호 챔피언 `mostChampions` 포함)에 더해 **`tier`(`league-v4` 솔로랭크 → gameconfig 사다리의 이름 · 언랭이면 `null`)**이고 응답에 바로 들어 있다(`mainPosition`은 요청 값 — 처음에는 최근 경기에서 가장 많이 간 포지션으로 채웠다가 **같은 날 소유자가 되물렸다**. 주 포지션은 "이번에 맡을 자리"라 사용자 의사다). **이름#태그가 Riot에 없으면 404 `RIOT_ID_NOT_FOUND`, Riot 장애 · 시간 초과 · 키 없음은 503 `GAME_STATS_UNAVAILABLE` — 둘 다 저장하지 않는다.** 전적 갱신도 `tier`를 같이 갱신한다(`mainPosition`은 건드리지 않는다). **VALORANT · PUBG는 API가 없어 지금대로 자기신고**이고 긁지 않는다 — 그래서 **저장 뒤 비동기로 긁던 길은 없어졌다.** **긁는 시점은 여전히 둘이다** — LoL의 `PUT`과 전적 갱신, 둘 다 동기다. **왜** — Riot에서 티어를 이미 받아 오면서 저장하지 않았다(계정(09-21)이 Riot 연동(09-23)보다 먼저 만들어져 요청 모양을 안 고쳤다). 티어를 옮기는 표 · 태그 없는 닉네임의 응답은 **코드 참조**.
**2026-09-29 에 또 바뀌었다(소유자 결정 셋 · P-36 · docs/11 D-48)** — **티어가 사다리(랭크 큐)마다 따로다**(게임 계정의 `tiers` jsonb · 응답의 `tiers` — LoL `SOLO` · `FLEX` / VALORANT `COMPETITIVE` / PUBG `RANKED`). **LoL 은 솔로 · 자유 두 줄을 다 채운다**(자유랭크 줄을 버리던 탓에 자유랭크 매칭이 솔로랭크 티어로 돌았다). **PUBG 는 PUBG API 로 채운다 — LoL 처럼 동기로**(`PUT …/PUBG` 는 `{gameNickname, server}` · `server` 필수 · `tier` 를 보내면 400 · 저장하기 전에 긁고 실패하면 저장하지 않는다 · 그 서버에 그 닉네임이 없으면 404 **`PUBG_PLAYER_NOT_FOUND`** · 전적 갱신도 된다). 호출은 플레이어 → 현재 시즌(Redis `qm:pubg:season:{shard}` 30일 캐시) → 이번 시즌 랭크 전적 → (랭크 0 판이면) 일반 시즌 전적, **한 번에 2 ~ 4번**이다. **키 하나에 분당 10회**(없는 닉네임의 404 도 센다) — 429 는 재시도하지 않고 503 + `Retry-After` 다. 티어는 사다리 하나(`RANKED` — 시즌 36 부터 모드에 걸쳐 통합) · 전적은 랭크 모드 합산(P-12 의 PUBG 칸 규칙 그대로 · `detail` = `seasonMode` · `avgDamage` · `kd` · `top1Rate`). 쿨타임 · 자물쇠 · 30초는 LoL 의 장치를 그대로 쓴다(`qm:riot:*` 키 — 게임 계정 번호가 게임을 가른다). **`verified` 는 켜지 않는다**(닉네임만으로 찾는다 — 공식 FAQ: 닉네임 ↔ Steam ID 조회 불가 · OAuth 없음). **`PUBG_API_KEY` 가 없으면 PUBG 게임 계정을 저장할 수 없다**(503 — 기동은 정상). **자기신고는 VALORANT 하나가 남았다.** 같은 날 **실제 키로 불러 보고** LoL 의 리그를 `league-v4` `entries/by-puuid` 로 부르게 고쳤다 — 소환사 응답에 `id` 가 없어 옛 `entries/by-summoner` 길은 늘 비었다(소환사 호출이 빠져 Riot 호출은 경기 20판에 **24번**이 됐고, 같은 날 소유자 결정으로 **최근 10판만 읽어 14번**이다 — `match-count` 기본 20 → 10 · 2분에 4명 → 8명, 위 머리의 블록). 확인된 응답 모양 · 미확인 · Claude 가 정한 세부는 계약 "전적을 긁는 것" 의 "PUBG" · P-36.
**모집 글을 쓸 때도 긁던 것은 2026-09-24 소유자 결정으로 없앴다 — P-13의 "긁는 시점은 둘"을 하나로 고치는 것이다**(같은 날 아래의 "전적 갱신"이 붙어 다시 둘이 됐다). 긁는 것이 비동기라 방금 쓴 글의 응답에는 반영되지 않는데 대가가 Riot 호출 25번(계정 · 소환사 · 리그 · 경기 id · 경기 20 · 숙련도 — 2026-09-27 에 세어 보니 "21번" 은 틀린 수였다. 2026-09-29 에 소환사 호출이 빠져 24번, 같은 날 경기를 10판으로 줄여 14번이다)이고 개발용 키의 한도가 2분에 100회다 — 수지가 맞지 않는다.
**`synced_at`이 신선하면 건너뛰던 장치(`platform.riot.freshness` 기본 30분)도 같이 지웠다** — 신선도를 보는 곳이 그 시점 하나뿐이어서 죽은 코드가 됐기 때문이다(그 판단은 Claude가 했다. 남은 시점은 원래부터 신선도를 보지 않는다).
**그래서 같은 날 "전적 갱신" 요청이 붙었다(소유자 결정 — `contracts/platform-api.md` "전적을 긁는 것" 의 "전적 갱신" · P-17).** **`POST /api/v1/users/me/game-accounts/{game}/refresh`**(로그인한 본인 것만)이고 **이쪽은 동기다** — 다 긁을 때까지 기다렸다가 **200 + 갱신된 게임 프로필**(`PUT`과 같은 모양)을 준다. **같은 게임 계정은 2분에 한 번**(429 `TOO_MANY_STATS_REFRESHES` + `Retry-After`. **이미 긁고 있을 때도 같은 429**다 — 자물쇠를 그대로 쓴다)이고 **상한은 30초**(넘으면 요청만 503으로 끊고 **뒤에서 돌던 갱신은 그대로 둔다**). **라이엇 실패 · 시간 초과 · 키 없음은 503이고 전적 줄을 건드리지 않는다**(옛 값이 남는다). **쿨타임은 긁기를 시작할 때 찍고 실패해도 소모된다** — 실패만 무제한으로 다시 할 수 있으면 Riot 한도를 그대로 태운다. **신선도 장치는 되살리지 않는다** — 이 요청은 "최근에 긁었어도 사용자가 원하면 긁는다"가 요점이고 남용은 쿨타임이 막는다. 세부는 **Claude가 정했다** — 에러 코드 넷의 이름(`TOO_MANY_STATS_REFRESHES` · `GAME_ACCOUNT_NOT_FOUND` · **구현이 없는 게임(VALORANT — PUBG 는 2026-09-29 부터 된다)은 409 `GAME_STATS_NOT_SUPPORTED`** · `GAME_STATS_UNAVAILABLE`) · 쿨타임 키 `qm:riot:refresh:{gameAccountId}`(§4) · 실패의 갈래를 503 하나로 합친 것 · 30초를 **전용 풀 + `Future.get`**으로 재는 것(요청 스레드에서 긁으면 자를 수 없다) · Redis가 죽으면 쿨타임 없이 통과시키는 것(로그인 실패 제한과 같은 원칙이었다 — 그 제한은 2026-09-26에 없어졌다).
**감수하는 것 — 저절로 갱신되지는 않는다.** 오래 전에 연결하고 안 건드린 사람의 `stats`는 낡은 채로 남고(언제 긁은 것인지는 `syncedAt`이다), 갱신하려면 **전적 갱신을 누르거나 게임 계정을 다시 저장해야 한다.** **주기적으로 스스로 갱신할지와 그 주기는 여전히 미정이다 — 지어내서 만들지 마라**(아래 "남은 것". 전적 갱신은 사용자가 누르는 것이라 그 미정과 별개다). **`external_id`에 `puuid`를 적지만 `verified`는 켜지 않는다**(식별자를 알아낸 것은 본인 확인이 아니다). **키(`RIOT_API_KEY`)가 없으면 긁는 일 자체를 하지 않는다** — 기동은 정상이다(그때 `stats`가 `null`로 남던 것은 2026-09-27에 바뀌었다 — **키가 없으면 LoL 게임 계정을 저장할 수 없다**, 503). 세부는 **Claude가 정했다** — 같은 게임 계정을 동시에 긁지 않게 하는 Redis 락 **`qm:riot:sync:{gameAccountId}`**(`SET … NX EX 60`. 못 잡으면 줄 서지 않고 건너뛰고, **Redis가 죽으면 락 없이 진행한다.** 접두사 `qm:riot:*`는 이 앱의 것이다 — §4) · 게임별 구현을 인터페이스(`account.stats.GameStatsProvider`) 뒤에 둔 것 · 429를 재시도하지 않는 것. **남은 것** — **VALORANT의 전적**(구현이 없어 `stats`는 늘 `null`이다. **VALORANT의 전적 API는 Riot의 별도 승인**이다. PUBG 는 2026-09-29 에 붙었다 — P-36), Riot(RSO) 인증으로 **`verified`를 켜는 법**(켜는 길이 없다. **지금은 자기신고를 믿는다** — 2026-09-25 소유자 결정. PUBG 는 켤 길 자체가 없다), **운영의 API 키**(Riot의 승인이 필요하다 · **PUBG 도 `PUBG_API_KEY` 를 소유자가 받아야 한다** — developer.pubg.com. 기본 한도 분당 10회를 올리려면 **MY APPS → "I NEED A HIGHER LIMIT"**(동작하는 샘플 · 사용량 · 캐시 설명을 요구한다)), **전적(2026-09-27부터 LoL의 티어도)을 주기적으로(사용자가 누르지 않아도) 갱신할지와 그 주기**(2026-09-24에 새로 생긴 미정이다 — 긁는 시점이 게임 계정 저장 하나로 줄었고, 같은 날 붙은 전적 갱신은 **사용자가 누르는 것이라 이 미정을 닫지 않는다**). OP.GG의 "MVP · Ace" 배지와 평점은 Riot API에 없어 넣지 않았다(**소유자가 2026-09-23에 다시 확인했다 — 평점은 넣지 않는다.** `contracts/platform-api.md` "게임 프로필"). `matching`의 티어도 지금 자기신고다 (`matching/CLAUDE.md` §2 — 이 앱의 LoL 티어가 Riot에서 오게 된 것(P-26)과 별개다). **`tier` · `mode`의 값 목록은 여전히 `matching`의 gameconfig가 원본이고, 2026-09-24 소유자 결정으로 이 앱이 그것을 Redis에서 읽어 검증한다**(§3.6 · `contracts/platform-api.md` "gameconfig 를 읽는 것" · P-16 — 이 미정은 닫혔다. **값 목록을 이 앱에 베껴 두지 않는다** — 읽기만 하고, Redis를 못 읽으면 검증을 건너뛴다) |
| `reservation` 스키마의 마이그레이션 | 예약은 `app:reservation`(Lambda)으로 빠졌다(D-15). Spring/Flyway가 없는 Lambda가 스스로 마이그레이션하기 어렵다 — **이 앱이 대신 갖는지 별도 절차인지 미정이다.** 정해지기 전에 이 앱에 `reservation` 마이그레이션을 넣지 않는다 |
| Redis의 다른 용도 | 파티 presence/ready(`qm:party:presence:*`·`qm:party:ready:*`), rate limit은 원본 docs/07의 **키 이름만** 있다. 누가 쓰는지(presence는 D-16으로 방 쪽 성질이 됐다 — 이제 이 앱의 `room` 패키지), `matching`의 `qm:party:*`와 접두사가 겹치는 것을 어떻게 할지. (로그인 실패 제한은 그 rate limit과 별개로 이 앱의 접두사 `qm:auth:*`에 두었다가 2026-09-26에 없앴고, 전적 동기화의 락은 `qm:riot:*`에 두었다 — §4. **`qm:gameconfig:*`는 이 앱의 접두사가 아니다 — 남이 심는 것을 읽을 뿐이고 이 미정과 무관하다**, §3.6) |
| 뼈대의 임시값 가운데 남은 것 | **정해진 것** — 포트 8082, 패키지를 나누는 법, readiness에 `db`를 넣고 Redis는 넣지 않는 것(§4 · §5). **남은 것** — 로컬 DB 이름 `queuemate` · 계정 `postgres`(위 "운영의 DB 롤"), Flyway 기록 테이블의 자리(기본값 `public`), **JSON 로그 형식**(운영에서 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`로 켜는 것으로 해 두었다 — `ecs`여야 하는지는 정해진 적이 없다). (테스트용 PostgreSQL은 `docker.exe`로 **5433**에, Redis는 **6380**에 따로 띄운다 — `START_HERE.md` §6. 5432 · 6379면 테스트가 건너뛴다) |

### 7.1 파티 모집 게시판과 방 (docs/11 D-11 · D-16 · D-20 · D-21 · D-23). 방 안의 규칙은 `contracts/platform-api.md` "방"

**정해진 것.**
- 모집 글을 올리면 **그것이 곧 파티방**이다. 자동 매칭 뒤에 생기는 파티방과 **같은 개념의 방**을 쓴다. 목적은 **모집의 응답성**이다.
- **글·목록·글의 상태·방장 확정의 기록·파티원 기록은 `party` 패키지**, 방 안의 일(입장·나가기·강퇴·정원·접속 확인·입장 표시 키·방 알림·시그널)은 **`room` 패키지**다 — **둘 다 이 앱이다**(2026-09-25 에 `room` 앱을 합쳤다 — P-22. D-16 · D-19가 두 앱으로 나눴던 것은 docs/11 D-33 이 개정했다). 맞물리는 법은 §3.3.
- **차단 관계가 있으면 그 방은 목록에 아예 보이지 않는다**(막는 것이 아니라 보이지 않게). **어느 쪽이 차단했든** 같다. 목록을 만들 때 이 앱이 거른다.
- **차단을 보는 범위는 "방 안의 누구와든"이다**(2026-09-20, D-20). 방 안에 나와 차단 관계(어느 방향이든)인 사람이 **한 명이라도** 있으면 **그 방 자체를 내 목록에서 뺀다** —
  들어가면 음성으로 바로 마주치기 때문이다. 검토하고 버린 것 — 방장과의 사이에서만 본다 / 방은 보이되 그 사람의 카드만 가린다. **입장할 때도 같은 규칙이다**(입장 요청 안의 글 검사 — §3.3).
  대가 — 목록을 그릴 때마다 글마다 방 안 전원과 요청자의 차단 관계를 대조해야 하고, 멤버 SET의 유령 때문에 **이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(§3.3).
- **목록의 한 줄이 보여 주는 것**(D-20) — ① 방 안에 몇 명인가 ② **방 안 사람들의 카드**(닉네임·티어·~~주 포지션~~ 등 — 주 포지션은 2026-09-29 에 빠졌다, 아래 "카드에 사람별 포지션이 없다") ~~③ 글의 "찾는 포지션" 가운데 이미 방 안에 있는 포지션의 강조~~
  ④ **F5 없이 갱신된다.** 목록의 단위는 여전히 **모집 글(방)**이다 — 카드는 방에 딸려 나오고, 사람을 검색하거나 방과 무관하게 둘러보는 기능은 여전히 금지다(§1).
  - **③은 2026-09-24 소유자 결정으로 없앴다 — `filledPositions`가 응답에서 빠졌다**(아래 "2026-09-24에 없앤 것" · `contracts/platform-api.md` "글 한 줄" · P-18). **docs/11 D-20의 ③을 개정하는 것이고 ①②④는 그대로 유효하다** — docs/11 D-31로 남겼다(2026-09-26).
- **카드에 사람별 포지션이 없다 — 게임 계정의 주 포지션을 없앴다**(2026-09-29 **소유자 결정** · `contracts/platform-api.md` "글 한 줄" · **P-35**. 소유자의 말 — "주 포지션, 주 역할군은 게시판에 글 쓸 때 하는 거니까 계정 연동에서 할 이유가 없다").
  **docs/11 D-20 ② 의 "카드(닉네임 · 티어 · 주 포지션 등)" 가운데 주 포지션 절반을 개정한다** — 카드의 나머지(닉네임 · 티어 · 전적 · 인증)는 그대로다. 글의 `wantedPositions`(찾는 포지션)는 그대로다.
  **입장할 때 고르는 방식은 여전히 하지 않는다** — 그러면 입장이 포지션을 받아야 하고 멤버 SET을 HASH로 바꿔야 해서 방 키의 모양이 바뀐다.
  ~~**글을 쓸 때 방장이 자기 포지션을 고르는 칸은 두지 않았다 — 미정이다**~~ → **2026-09-30 소유자 결정으로 정해졌다 — 글의 칸 `hostPosition`**(`contracts/platform-api.md` "방장 포지션" · P-38 · 머리의 블록). 포지션이 있는 모드면 필수 · 없는 모드에는 없다 · 찾는 포지션에 들 수 없다. 카드가 아니라 글 한 줄에 실리고 방장의 카드에 붙여 그리는 것은 화면이다.
  **남은 미정** — 게시판 방 먼저 합류(P-28)가 `wantedPositions` 가 빈 글에 방장과 같은 포지션인 사람을 건너뛸지(지금은 방장 포지션을 보지 않는다 — **지어내지 마라**).
  (옛 모양 — "포지션의 출처는 프로필의 주 포지션이다" · 세 게임 모두 자기신고였다. 2026-09-27에 LoL의 주 포지션을 Riot의 최근 경기에서 채우기로 했다가 같은 날 되물렸다 — P-26. 2026-09-24 에 `filledPositions` 를 없앨 때도 카드의 주 포지션은 남겼었다 — P-18.)
- **목록의 데이터는 전부 이 앱이 조립한다** — 멤버 SET의 `SCARD`·`SMEMBERS` → 이 앱 DB에서 프로필 붙이기 → 차단 대조. 방 안은 `RoomService#states`로 읽고, 방 키에는 `userId`만 있다(§3.3).
- **갱신은 게시판 채널의 "바뀌었다" 신호로 한다**(§3.2 "게시판 채널") — 채널 `qm:pubsub:board`(게임을 구분하지 않는 하나 — D-22), `type` `BOARD_CHANGED`, `payload` `{}`(**`roomId`도 `game`도 싣지 않는다**),
  **`topics` 파라미터는 없앴다(D-22)** — `notification`이 모든 연결에 그대로 보내고 클라이언트가 거른다(게시판 페이지를 보고 있을 때만 묶어서 다시 받는다). 신호는 "다시 받아라"일 뿐이고 받은 프런트가 이 앱의 목록을 `GET`으로 다시 요청한다. **구현됐다**(글 쪽 2026-09-21 · 방 쪽 D-23). **게시판은 실시간으로 바뀌어야 한다**(인원 · 새 글 — 2026-09-21 소유자 지시)는 이것으로 지킨다.
- **파티원은 방장이 확정한다.** 확정하면 **더 이상 새 사람이 들어올 수 없다** — 확정된 방의 입장을 방의 Lua가 409 `ROOM_CONFIRMED`로 거절하고, 입장 요청 안의 글 검사도 모집 중이 아닌 글(409 `POST_NOT_RECRUITING`)을 막는다.
- **방장 확정의 규칙이 정해졌다**(2026-09-20, D-21 · `contracts/platform-api.md` "방" 의 "방장 확정"). 확정 요청(`POST /api/v1/rooms/{roomId}/confirm`)은 방장만 · 2명 이상이다. 대상은 **그 순간 방에 있는 전원**(방장 포함)이다 — 방장이 고르지
  않고, 원치 않는 사람은 그 전에 강퇴한다. **되돌릴 수 없다** — 확정 뒤 빈자리가 생겨도 다시 모집을 열 수 없다. **확정한 방은 방장이 나가도 없어지지 않는다 — 남은 멤버가 방장을 넘겨받는다(D-23. 방장 키의 값이 바뀔 수 있다 — §3.3).**
  **한 요청이 방의 확정(Lua)과 글의 확정 · 파티원 기록(DB)을 같이 한다**(2026-09-25 2단계 — §3.3). 커밋이 실패해 남은 "확정된 방인데 글은 모집 중"은 목록 · 단건의 자가 치유가 고친다.
- **방장이 나가면 글은 지우지 않고 "만료"로 표시한다.** 목록에 남지만 들어갈 수 없다. 방장 이탈은 방의 Lua가 판단하고(명시적 나가기와 **연결 끊김 둘 다** — 확정하지 않은 방의 수명은
  방장의 접속 확인만 늘린다. **확정한 방은 방장이 나가도 방이 이어지므로 이 규칙에 걸리지 않는다** — D-23), **방장이 나가기를 부르면 글도 그 자리에서 만료된다**(아래 "방과 글은 같이 산다"). **말없이 사라진 경우는 목록 · 단건이 방장 키(`qm:room:{roomId}:host`)가 사라진 것을 보고 글을 만료로 바꾼다** — 방이 없어지는 순간에 도는 코드가
  없어서다(수명 만료). 만료가 즉시는 아니지만 목록을 그리는 순간 걸러지므로 보이는 차이는 없다(수명이 다할 때까지 최대 10분 늦는다 — §3.3).
- **방과 글은 같이 산다**(2026-09-25 **소유자 결정** · `contracts/platform-api.md` "방과 글은 같이 산다" · P-22). **확정 전에는 방장이 나가든(`DELETE …/members/me`) 글을 지우든(`DELETE /api/v1/posts/{postId}`) 방과 글이 둘 다 끝난다** — 방은 닫히고(방 키와 남아 있던 전원의 입장 표시 키가 지워지고 `ROOM_CLOSED`가 나간다) 글은 그 자리에서 `EXPIRED`가 되고 게시판 신호가 나간다.
  **확정 뒤에는** 글은 `CONFIRMED`로 고정이고(`DELETE`는 409 `POST_CONFIRMED`) 방은 방장이 나가도 승계된다(D-23) — **그 방이 끝내 없어지면 파티가 닫힌다**(2026-09-26 소유자 결정 — §3.3 "파티 닫힘" · P-25). 이것으로 두 구멍이 없어졌다 — 방장이 글을 지워도 방에 남아 새 글이 409 `IN_OTHER_ROOM`이던 것, 방장이 나갔는데 목록이 안 돌아 글이 모집 중으로 남아 새 글이 409 `ALREADY_RECRUITING`이던 것. **말없이 사라진 방장**(수명 만료)만 여전히 목록 · 단건이 만료로 옮긴다(위). **구현됐다** — `RoomService#leave(…, whenClosed)` · `PostLifecycle#expireByRoomClosed`(계약 "방과 글은 같이 산다").
- **방 키의 이름·구조·수명**이 정해졌다(§3.3 · `contracts/platform-api.md` "방" 의 "Redis 키"). **방은 글 쓰기가 만든다**(2026-09-25 소유자 결정 C — 방 만들기 요청이 따로 없다). 입장은 방을 만들지 않는다(없는 방은 404).
- **방이 내는 알림이 정해졌다** — `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · `WEBRTC_SIGNAL`(전부 구현돼 있다 — D-21. 이제 이 앱의 `room` 패키지가 낸다).
  `PARTY_*`를 다시 쓰지 않고 새 이름을 지었다 — 그 7종의 이름과 `payload`가 이 컴퓨터의 문서에 없어서다(`contracts/platform-api.md` "방" 의 "알림").
- 입장 승인 없음·둘러보는 상태·강퇴·최대 5명·음성 제어(브라우저에서 끝난다)는 방(`room` 패키지)과 프런트 쪽 규칙이다. "자동 매칭 대기와 방은 한 번에 하나만"은 이 앱과 `matching`이
  키 둘로 지킨다(D-19 — 입장 표시 키는 이 앱이 쓰고 활성 요청 키는 `EXISTS`만 본다. §3.3).

**정해진 것 — 출처는 `contracts/platform-api.md` "모집 글 · 목록" · "방" · "목록의 페이지 나누기".** 아래는 **2026-09-21에 정한 것**이고 첫 항목만 소유자 지시이며, **나머지는 Claude가 정해 구현했으며 소유자가 항목별로 검토하지 않았다**(P-4 · P-8).
**"목록의 페이지 나누기"는 2026-09-23 소유자 결정이다**(P-14). **글 쓰기 = 방 만들기 · 입장 안의 글 검사 · 한 요청의 확정은 2026-09-25 합치기 2단계다**(P-22 · §3.3). Claude가 정한 것은 docs/11에 없다 — 소유자 결정인 페이지 나누기는 D-28, 합치기는 D-33으로 2026-09-26 에 남겼다.
- **목록의 한 줄은 게임마다 다른 정보를 보여 준다**(2026-09-21 소유자 지시. 본보기는 OP.GG의 듀오 찾기) — LoL: 이름#태그 · 인증 · ~~주 포지션~~(2026-09-29 에 빠졌다 — P-35) · 티어 · 찾는 포지션 · 모스트 챔피언 · 승/패 · KDA · 메모,
  VALORANT: 모스트 요원 · 주 무기 · 헤드샷률 · 음성, PUBG: 모드 · 티어 · 음성 태그(본보기의 목적 태그는 2026-09-27에 `purpose`와 함께 빠졌다 — P-29) · 서버/시점 · 평균 데미지 · K/D · 치킨률. **세 게임 모두 가로 한 줄이고, 글을 눌러 펼치지 않고 방 안 전원을 그 자리에서 다 보여 준다** —
  그래서 **목록 응답의 `members[]`에 게임 프로필 전체가 실린다**. **백엔드는 같은 모양에 게임별 내용을 채운다** — 글 한 줄의 모양은 세 게임이 같고 `host.profile` · `members[].profile`(그 글의 게임에 연결한 게임 계정)과 글의 `voice` · `purpose` · `conditions` · `wantedPositions` · `description`이 게임마다 다르게 채워진다.
  **LoL의 전적(`stats`)은 채워진다**(2026-09-23 소유자 결정 — §7 "게임 계정 연동" · P-13). **VALORANT · PUBG는 아직 늘 `null`이라** 모스트 요원 · 평균 데미지 같은 칸은 화면이 "정보 없음"으로 그린다.
- **`roomId` = 글의 id**(2026-09-22 소유자 결정으로 **bigint identity**가 됐다 — 정할 때는 UUID였다) — §3.3. **글을 쓰면 그 번호의 방이 같이 생긴다**(2026-09-25 소유자 결정 C).
- **모집 중인 글에 방장 키가 없으면 무조건 만료다**(2026-09-25 2단계 — "아직 안 만들어진 방"이 없어졌다. §3.3). **확정된 글은 방장 키가 없어도 만료시키지 않는다 — 끝까지 `CONFIRMED`다**(§3.3).
- **방장 확정은 한 요청이다 — `POST /api/v1/rooms/{roomId}/confirm`이 Lua와 DB 기록을 같이 하고, 커밋이 실패하면 목록 · 단건의 자가 치유가 고친다** — §3.3. 자가 치유가 읽는 멤버 SET이 확정 순간과 다를 수 있는 것은 **감수한다.**
- **글에 담는 것** — `game` · **`mode`(필수. 그 게임의 gameconfig에 있는 모드여야 한다 — 2026-09-24 소유자 결정 · §3.6. 30자까지이고, 값의 원본은 `matching/seed/gameconfig.redis`다. 옛날에는 "없어도 되는 자유 문자열"이었다)** · `title`(1~60자) · `description`(300자까지) · `voice`(`REQUIRED` · `NO_VOICE` — `matching`의 `VoicePreference`와 같은 이름) · ~~`purpose`~~(**2026-09-27 소유자 결정으로 없앴다** — P-29 · docs/11 D-39. 게시판에 플레이 목적까지 넣지 않는다. 컬럼 · CHECK · 요청 · 응답 · `party`의 `PlayPurpose` enum이 빠졌고 `V1__schema.sql`을 그 자리에서 고쳤다. `matching`의 `PlayPurpose`는 그대로다) ·
  `conditions`(게임별 조건 — PUBG는 `{"perspective": "TPP" | "FPP"}` 필수, LoL · VALORANT는 `{}`) · **`wantedPositions`(그 게임의 포지션 이름의 배열. PUBG는 빈 배열)**. **모집 중인 글은 한 사람에 하나**다(DB의 부분 UNIQUE 인덱스 — 409 `ALREADY_RECRUITING`).
- **카드에 담는 것** — `{userId, nickname, host, profile}`이고 `profile`은 게임 프로필 전체다(게임 계정이 없으면 `null`).
  `host`는 글이 만료 · 확정된 뒤에도 채워진다. `members`는 방 안에 지금 있는 사람이다.
- **정렬 — `id` 내림차순 하나(= 최신순)다**(2026-09-24 소유자 결정 — 그날 아침까지는 "모집 중인 글이 먼저, 그 안에서는 새 글이 먼저"였다). **`id`가 `bigint identity`라 넣은 순서대로 커지고, 겹치지 않고, 변하지 않는다** — 커서 페이지 나누기가 정렬 키에 요구하는 셋을 혼자 만족한다.
  **글의 상태도 `createdAt`도 정렬에 쓰지 않는다.** **왜 — 정렬 키가 변하면 커서가 중복을 낸다**: 상태는 변하고 **그것도 목록 조회 자신이 바꾼다**(방장 키가 사라진 글을 그 자리에서 만료로 옮겨 적는다 — §3.3). 1쪽에 모집 중으로 나간 글이 그 사이 만료되면 2쪽에 **다시 걸렸다.**
  `createdAt` 컬럼과 응답의 `createdAt`은 **그대로다** — 화면의 "몇 분 전"이 그 값이고, 정렬과 커서에서만 안 쓴다. 필터는 `?game=` 하나이고 **2026-09-25 소유자 결정으로 필수가 됐다**(아래 "목록의 `game`은 필수다" · P-21 — 그 전에는 없으면 세 게임 전부였다).
  **글을 상태로 가리지 않는다 — 모집 중 · 확정 · 만료가 전부 그 순서로 나오고 끝난 글도 계속 남는다**(2026-09-25 **소유자 결정** · `contracts/platform-api.md` "목록의 정렬" · **P-20**. `status`로 구분해 보여 준다. 끝난 글은 멤버를 비우고 `host`는 채운다). **만석인 방은 `full: true`로 목록에 남는다.**
  - **보존 기간을 없앤 것이다**(2026-09-25 소유자 결정 — 그 전에는 **만료 · 확정된 뒤 10분 동안만** 남았고 설정 `platform.board.closed-retention`이 그 값이었다. 그 설정도 같이 없앴다). **왜 둘이다.**
    ① **쿼리가 쓸데없이 무거웠다** — 조건이 `status = RECRUITING` 또는 `confirmedAt > :closedAfter` 또는 `expiredAt > :closedAfter`라는 **세 컬럼에 걸친 `OR` 셋**이라 `(game, id DESC)` 인덱스(옛 `party/V7__board_order_index.sql` — 지금은 `V1__schema.sql`)를 깨끗하게 타지 못했다. 조건이 `game` 하나만 남은 지금은 **그 인덱스를 순서대로 훑어 내려가면 끝이다.**
    ② **활동을 보여 주지 못했다** — 끝난 글이 10분 만에 사라지면 사용자가 "이 서비스에서 모집이 얼마나 활발한가"를 볼 수 없다.
  - **감수하는 것 — 만료 · 확정된 글이 목록 위쪽에 섞여 나온다**(**제자리다** — 맨 아래로 내려가지 않는다). 응답에 `status`가 있으니 화면이 흐리게 그리면 된다.
    **1쪽은 그래도 대개 모집 중인 글일 것이다** — 글은 **모집 중으로 태어나** 나중에 끝나고 정렬이 `id` 내림차순이라 **새 글일수록 아직 살아 있을 가능성이 높다.** 끝난 글이 1쪽을 채우는 것은 글이 아주 뜸할 때인데, **그때는 애초에 들어갈 방이 없는 상태라** 그 글들이 위 ②("활동을 보여 준다") 쪽으로 쓰인다.
  - **행이 영원히 쌓인다 — 앱에는 끝난 글을 지우거나 옮기는 정리 작업이 없다. 오래된 글은 운영에서 소유자가 직접 지운다**(2026-09-25 소유자 결정 — 앱에 정리 작업을 만들지 마라).
    **`expired_at` · `confirmed_at` 컬럼은 그대로 쓴다** — CHECK 제약과 "언제 끝났는지"를 보여 주는 값이다. **마이그레이션은 없다**(인덱스 `(game, id DESC)`는 이 변경으로 **더 잘 맞는다**).
    **`status`로 거르는 필터는 두지 않는다**(2026-09-25 소유자 결정 — 필요 없다. 끝난 글을 다 보여 주기로 한 것과 같은 맥락이다).
- **글을 지우면(`DELETE`) 지우지 않고 "만료"로 바꾼다.** 확정된 글은 지울 수 없다(409 `POST_CONFIRMED`). 차단 관계로 숨겨진 글은 단건 · 입장에서도 **없는 글과 같은 404 `POST_NOT_FOUND`**다.
- **방에 방장 말고 누가 있으면 글을 고칠 수 없다**(2026-09-24 **소유자 결정** · `contracts/platform-api.md` "모집 글 · 목록" 의 `PATCH` · P-19) — **409 `ROOM_HAS_OTHER_MEMBERS`**.
  **왜** — 고칠 수 있는 칸에 `mode` · `voice` · `conditions`가 있다(정할 때는 `purpose`도 — 2026-09-27에 없어졌다, P-29). `NO_VOICE`를 보고 들어와 앉아 있는 사람 앞에서 `REQUIRED`로 바꿀 수 있는데,
  **방 안 사람에게 바뀌었다고 알려 줄 길이 없다** — 게시판 신호는 목록을 보는 사람에게 가고 방 안 알림(`ROOM_*`)에는 "글이 바뀌었다"가 없다(그 알림의 이름과 `payload`가 미정이다). 그래서 **칸을 가리지 않고 아예 막는다**(`title`만 고치는 것도 막는다).
  **방장 혼자면 고칠 수 있다.** **방 안을 못 읽으면 막는다 — 503 `ROOM_STATE_UNAVAILABLE` + `Retry-After: 5`**(입장과 같은 코드 · 같은 이유다).
  **막는 것은 `PATCH` 하나다** — `DELETE`(만료로 바꾸기) · 입장 · 방장 확정 · 목록 · 단건 조회는 그대로다.
  **거르는 순서** — `mode` 검증(400) → 방장이 아니면 403 → 모집 중이 아니면 409 `POST_NOT_RECRUITING` → 이 검사다. 남의 글이나 끝난 글에 "방에 사람이 있다"를 알려 주면 그 자체가 새는 정보다.
  **방 키는 글의 줄을 잠그는 트랜잭션 밖에서 읽는다**(그 안에서 Redis를 기다리면 DB 커넥션을 붙잡는다) — 그래서 **"읽은 뒤 저장하기 전"에 누가 들어오는 경쟁이 남고, 감수한다**(창이 짧다. 세부는 Claude가 정했다 — 에러 코드의 이름 · 503의 재사용 · 순서 · 이 경쟁).
- **목록의 `game`은 필수다**(2026-09-25 **소유자 결정** · `contracts/platform-api.md` "목록의 `game` 은 필수다" · **P-21**). **게시판은 게임별로 나뉜 페이지이고 사용자는 늘 한 게임의 게시판을 본다 — "세 게임 전부" 화면이 없다.** 그 전에는 없으면 전부 내려 주었는데 **아무도 쓰지 않는 갈래인데 게임 없이 훑는 쿼리 둘을 더 있게 만들었다**(그 둘을 지웠다 — 남은 둘은 `game`이 늘 등호 조건이라 `(game, id DESC)` 인덱스를 언제나 그대로 탄다).
  **안 보내면 400 `VALIDATION_FAILED`**(`details`는 `"game: 필요합니다"`. **빈 값 `?game=`도 안 준 것과 같다**), **모르는 이름과 소문자(`?game=lol`)는 같은 400**에 `"game: 올바른 값이 아닙니다"`다(거절하는 자리가 **형 변환**이라 `limit` · `cursor`가 숫자가 아닐 때와 글귀가 같다). **소문자를 받아 주는 변환기를 두지 않았다** — 두려면 그것부터가 결정이다.
  **목록 하나에만 걸린다** — 단건 조회 · 입장 · 방장 확정 · 글 쓰기 · 고치기 · 지우기는 그대로다(글 쓰기의 `game`은 전부터 필수인 **본문 필드**다). 마이그레이션 · 인덱스 · 정렬 · 커서 · 차단 거르기와 채우기는 바뀌지 않았다.
- **목록의 페이지 나누기 — 커서 방식**(2026-09-23 **소유자 결정** · `contracts/platform-api.md` "목록의 페이지 나누기" · P-14. 전부 내려 주면 `BOARD_CHANGED`마다 그 큰 응답이 되풀이된다). **`limit`은 없으면 20 · 최대 100이고 벗어나면 400 `VALIDATION_FAILED`다 — 상한으로 잘라 주지 않는다**(조용히 100개를 주면 클라이언트가 "다 받았다"고 읽는다). **`cursor`는 마지막으로 읽은 글의 번호**이고 응답의 **`nextCursor`**로 이어 받는다(더 볼 것이 없으면 `null` — **둘 다 숫자다**). **`offset`이 아닌 이유** — 보는 동안 글이 올라와 줄이 밀리면 같은 글이 두 번 나오거나 사이의 글이 빠진다. **신호(`BOARD_CHANGED`)를 받은 프런트는 커서를 쓰지 않는다 — 펼친 만큼을 `limit`으로 맨 위부터 다시 받는다**(커서는 "더 보기"에만 쓴다). 필터는 `game` 하나 그대로이고(2026-09-25로 **필수가 됐다** — 아래 · P-21. 페이지도 그 게임 안에서만 이어진다), 페이지는 정렬(위 — `id` 내림차순)을 자른 것뿐이다.
  **커서가 담는 것은 글 번호 하나다**(2026-09-24 **소유자 결정** — P-14의 개정이다. 정할 때는 정렬에 쓰는 값 셋 `모집 중인가` · `createdAt` · `postId`였다). **왜 — 정렬 키가 변하면 커서가 중복을 낸다**(위 "정렬"). `id`가 identity라 순증가 · 유일 · 불변을 혼자 만족하므로 `createdAt`도 같이 들 필요가 없다.
  **커서를 base64url로 감싸던 것은 2026-09-25 소유자 결정으로 없앴다**(P-14의 개정이다 — `contracts/platform-api.md` "목록의 페이지 나누기"). **감싸도 얻는 것이 없었다** — 서명하지 않아 보안 값이 0이고(위조해도 남의 글이 보이지 않는다), **글 번호는 응답의 `postId`로 이미 다 나가며**, 커서가 `id` 하나라 형식이 바뀔 여지도 작다. 대가는 코드와 문서가 길어지는 것이었다.
  그래서 **문자열을 숫자로 바꾸는 일을 스프링에 맡긴다**(`Long` 파라미터) — **숫자가 아닌 커서는 컨트롤러에 닿기 전에 400 `VALIDATION_FAILED`**이고 `limit`이 숫자가 아닐 때와 **본문이 글자까지 같다**(`details`가 `"cursor: 올바른 값이 아닙니다"` — `GlobalExceptionHandler#handleTypeMismatch`). **커서를 검사하는 클래스(`BoardCursor`)가 없어졌고 커서를 검사하는 코드가 따로 없다.** **0 · 음수 · 맨 끝을 넘은 번호는 400이 아니라 빈 페이지다**(`id < :cursor`가 아무것도 고르지 못하는 것이고 맨 끝 글의 번호를 준 것과 구별되지 않는다 — 그 판단은 Claude가 했다).
  세부는 **Claude가 정했다** — **옛 커서와의 호환을 두지 않은 것**(칸이 셋인 값이든 base64url로 감싼 값이든 숫자가 아니니 그냥 400이다 — 프런트가 아직 없다) · **차단으로 숨겨진 글 때문에 모자라면 그 뒤를 더 읽어 채우는 것**(`platform.board.max-refills` 기본 3번. 다 써도 모자라면 있는 만큼 내려 준다) · **`nextCursor`는 마지막으로 "읽은" 줄이다**(보여 준 줄이 아니다 — 숨겨진 글을 다음 페이지에서 또 읽지 않게).
  **대가 하나** — **만료 · 확정 옮겨 적기(§3.3)가 읽은 글에만 걸린다.** 목록이 이제 글 전부를 읽지 않으므로 **깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다.** 입장은 방의 Lua가 방장 키 · 확정 표시 키를 직접 보므로 "목록에 안 보이지만 들어갈 수 있는 죽은 방"은 생기지 않는다.

**2026-09-24에 없앤 것 — `filledPositions`**(**소유자 결정** · `contracts/platform-api.md` "글 한 줄" · P-18. **docs/11 D-20의 ③을 개정한다 — docs/11 D-31로 남겼다(2026-09-26)**).
- **응답에서 그 칸을 뺐다.** `wantedPositions` ∩ 방 안 사람들의 주 포지션으로 계산하던 것이다.
- **왜 — 주 포지션은 "내가 주로 하는 것"이지 "이 방에서 할 것"이 아니다.** 주 포지션이 정글인 사람이 미드를 구하는 방에 미드를 하러 들어갈 수 있는데 그 계산은 그 방의 미드 자리를 **안 찼다고** 표시했다. **틀린 정보를 자신 있게 보여 주는 것**이라 없앴다.
- **그대로인 것** — 글의 `wantedPositions`(쓸 때 고르는 "찾는 포지션")와 그 검증, 카드의 `profile.mainPosition`(→ **2026-09-29 에 칸째 없어졌다** — P-35), 테이블 `recruit_post_positions`. **마이그레이션은 없다** — 컬럼도 테이블도 바뀌지 않았다.
- **다시 둘 것인가는 미정이다** — 소유자가 "일단" 없앴다. **입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다**(위 "카드에 사람별 포지션이 없다"(옛 "포지션의 출처") — 방 키 약속이 바뀐다). **다른 방식을 지어내 만들지 마라.**

**정할 것 — 개발하면서 정한다.** 구현하다 해당 지점에 닿으면 **그때 묻고 정한다.** 임의로 정해 구현하지 마라.
- **방장 확정에 남은 것** — 확정된 방이 자동 매칭 파티방과 **같은 기능(Ready 등)**을 갖는가. **"최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가는 2026-09-26 에 정해졌다**(소유자 결정 · P-25 — 확정된 방이 없어질 때 파티가 닫히고 그 순간의 `party_members`끼리 서로를 `recent_players`에 적는다. §3.3 "파티 닫힘"). (두 앱이던 때의 "둘 다 빠지고 방이 사라지는 구멍"은 한 요청이 되며 물음째 없어졌다 — §7.2 (가).)
- **차단에 남은 것**(범위는 정해졌다 — 위) — 입장 요청 안의 글 검사와 방의 Lua 사이에 나와 차단 관계인 사람이 먼저 그 방에 들어온 경우의 경쟁(D-20 "아직 미정" — 두 단계가 원자적이지 않다. 창은 밀리초다). 목록을 본 뒤 차단이 생긴 경우, 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우(D-11의 미정 그대로). 차단이 이미 맺은 친구 관계를 끊는가(지금은 건드리지 않는다 — `contracts/platform-api.md` "차단").
- **게시판 채널에 남은 것**(§3.2. 이름·`type`·`payload`는 정해졌고 `topics`는 없앴다 — 위) — 채널 이름의 **원본 상수를 어느 서비스에 둘지**(지금은 이 앱과 `notification`이 각자 적어 두었다), 프런트가 재요청을 묶는 간격(D-20 · D-22 "아직 미정").
  **`topics` 표기와 "방 쪽이 어느 게임의 채널에 발행할지를 어떻게 아는가"의 미정은 D-22로 물음째 없어졌다** — 채널이 하나라 발행하는 쪽이 게임을 알 필요가 없다.
- ~~**자동 매칭으로 확정된 파티의 방은 어떻게 생기는가**~~ — **2026-09-27 에 정해졌다**(소유자 결정 docs/11 **D-42** 위에 Claude 가 정한 세부 **P-30** · `contracts/platform-api.md` "자동 매칭 파티의 방"). 프런트가 `MATCH_CONFIRMED {partyId}` 를 받아 **`POST /api/v1/match-parties/{partyId}/room`** 을 부르면 이 앱이 `matching` 의 파티 HASH 를 읽어 없으면 만들고 있으면 들여보낸다 — `roomId = partyId`(UUID) · 처음부터 확정된 방 · 처음 부른 사람이 방장 · 정원은 HASH 의 `target` · 게시판 신호 없음 · 전용 Lua 는 활성 요청 키를 보지 않는다(§3.3). 확정된 사용자의 `status=PARTY` 는 `matching` 이 60초 뒤 만료시키므로 "그대로는 방 입장이 거절된다" 는 물음도 없어졌다(D-42 — §7 "확정된 사용자를 푸는 길"). 글이 없는 방이라 입장 검사(글을 본다)를 거치지 않고 전용 요청으로만 들어온다. **남은 것** — 전원이 말없이 사라진 자동 매칭 파티의 닫힘 · `PARTY_*` 이름 · 소유자의 P-30 검토. 강퇴당한 사람의 재입장 — **게시판 방은 2026-09-29 에 정해졌다**(10분 동안 못 들어온다 — 소유자 결정 · P-32 · §3.3) · **자동 매칭 방만 미정이다**(`enter-match-room.lua` 는 금지 목록을 보지 않는다 — 강퇴당한 파티원이 그 요청을 다시 부르면 들어온다. 지어내 막지 마라).
- 목록의 **필터를 더 둘지**(지금은 `game` 하나다. **페이지 나누기는 정해져 구현됐다** — 위 "목록의 페이지 나누기". **`status` 필터는 두지 않기로 했다** — 2026-09-25 소유자 결정). **도배 글 대응**과 신고(docs/11 #13)의 연결 — 지금 있는 것은 "모집 중인 글은 한 사람에 하나"뿐이다.
- **방 안 사람에게 "글이 바뀌었다 · 지워졌다"를 알릴지는 미정이다**(2026-09-24에 생겼다 — 위 "방에 방장 말고 누가 있으면"). 지금은 알릴 길이 없어서 **고치기를 막는 것으로 끝냈다.**
  알리려면 이 앱이 낼 알림의 이름과 `payload`를 정해야 하는데 **`PARTY_*` 는 2026-09-28 소유자 결정으로 두지 않는다(P-31)** — 알리려면 `ROOM_*` 에 새 `type` 을 더해야 하고 그것은 여전히 미정이다. **지어내지 마라.**
- **알림 종류** — 방 입장·퇴장·방 닫힘·강퇴·**방장 확정**(`ROOM_CONFIRMED` — `payload`의 `members`가 파티원이다)은 방(`room` 패키지)이 새 이름으로 내는 것으로 정해졌다(위). **이 앱이 낼 알림** 가운데 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`는 정했고(§3.2) **`PARTY_*`의 이름과 `payload`는 여전히 미정이다 — 2026-09-27 부터는 SQS 에 묶이지 않는다**(D-42 — §7). 계약 원본과 합칠 때 `ROOM_*`과 `PARTY_*`가 같은 뜻인지 맞춰야 한다.

### 7.2 2026-09-21에 검토한 방향 — (가)는 받았다가 2026-09-25에 물음째 없어졌다 · (나)(다)(라)는 2026-09-27 에 정해졌다(docs/11 D-42 / P-30)

> **(가)는 2026-09-21에 받아 구현했고, 2026-09-25 `room` 합치기로 물음 자체가 없어졌다**(P-22) — 방장 확정을 한 요청이 하므로 "두 앱에 무엇을 어떤 순서로 부르는가"가 없다.
> **(나) · (다) · (라)는 2026-09-27 에 정해졌다** — 소유자 결정 docs/11 **D-42**(SQS 를 없애고 이 앱이 파티 HASH 를 읽는다 · `status=PARTY` 는 60초 수명)와 그 위에 Claude 가 정한 이 앱 쪽 세부 **P-30**(`POST /api/v1/match-parties/{partyId}/room`). 아래 원문은 **기록**이다 — 어느 방향이 그대로 결정이 됐고 어느 것이 물음째 바뀌었는지를 "→ 2026-09-27" 줄이 적는다. 정해진 것의 원본은 §3.3 · §3.4 · `contracts/platform-api.md` "자동 매칭 파티의 방" 이다.
> 원문은 옛 `room` 앱의 `CLAUDE.md` §7 "`status=PARTY` 해제" 행이다(`git show origin/room:CLAUDE.md` — **폴더는 2026-09-27 에 지웠다**, 브랜치만 남아 있다). 여기는 그것을 이 앱의 눈으로 옮긴 것이다. 이미 정해진 것은 "(정해진 것)"이라고 따로 표시했다.

**(가) 게시판 방의 방장 확정을 이 앱이 아는 법 — 합쳐서 물음 자체가 없어졌다(2026-09-25 · §3.3 "방장 확정은 한 길이다").**

- 두 앱이던 때는 "브라우저가 `room`의 확정 뒤에 이 앱의 확정 기록 요청을 부른다(길 ①)"와 "목록이 확정 표시 키를 보면 기록한다(길 ②)"의 두 길로 받쳤다(2026-09-21 — Claude가 받았다. P-6).
- **지금은 `POST /api/v1/rooms/{roomId}/confirm` 한 요청이 Lua 확정과 DB 기록을 같이 한다.** 길 ①은 없어졌고, 길 ②는 **커밋 실패를 고치는 자가 치유**로 남았다. 멱등은 `UNIQUE (post_id)`가 그대로 지킨다.
- 옛 "남는 구멍"(둘 다 빠지고 방이 사라지는 경우)은 확정과 기록이 한 요청이 되어 없어졌다 — 남은 것은 "Lua 성공 뒤 커밋 실패 + 자가 치유 전에 방이 사라지는" 드문 경우뿐이고, 그런 글은 만료가 된다. 막는 장치는 넣지 않았다.

**(나) 자동 매칭 확정을 이 앱이 아는 법.**

- (정해진 것이었다 — docs/11 #21. **→ 2026-09-27 D-42 가 뒤집었다**, §3.4) `matching` → outbox → SQS `ProposalConfirmed.fifo` → 이 앱이 파티를 DB에 만든다. SQS는 같은 메시지를 두 번 줄 수 있으므로 **소비는 멱등**이어야 한다.
- (검토한 방향) 멱등의 기준을 **`partyId`**로 둔다(`matching`이 클라이언트에 내려 주는 `partyId`는 `proposalId`와 같다 — §7 "SQS 메시지 본문 3종". 파티 id를 그것과 같게 둘지부터가 미정이다).
  (`parties`는 이 방향을 받을 수 있는 모양으로 만들어 두었다 — `id`가 PK이고 `source`에 `MATCH`의 자리가 있다. **만드는 코드는 없다.** 계약이 "자동 매칭 파티는 `roomId = partyId`다"라고 적은 한 줄도 이 미정 위에 서 있다.)
- (검토한 방향) **`matching` → `room` → 이 앱으로 잇는 안은 받지 않았다** — 옛 `room`은 DB가 없어 "절대 안 잃는" 발행을 할 수 없었다. **2026-09-25 에 방이 이 앱 안으로 들어와 이 물음은 없어졌다.** 서버 간 이벤트는 `matching` → 이 앱의 SQS 하나로 둔다.
- → **2026-09-27 정해졌다(docs/11 D-42) — SQS 가 없다.** 이 앱이 `qm:party:{partyId}` 를 읽어 파티를 만든다(§3.4). 멱등의 기준은 검토한 대로 **`partyId`** 다 — `parties.match_party_id UNIQUE`(P-30). "서버 간 이벤트는 `matching` → 이 앱의 SQS 하나로 둔다" 도 없어졌다 — 큐가 0개다. `parties` 의 `source = MATCH` 자리를 P-30 이 채운다.

**(다) 자동 매칭 파티의 방** — §7.1 "자동 매칭으로 확정된 파티의 방은 어떻게 생기는가"에 대한 방향이다.

- (2026-09-21 에 검토한 방향) **방은 서버끼리 연락해서 만들지 않는다** — 클라이언트가 `MATCH_CONFIRMED`의 `partyId`를 들고 방에 "없으면 만들고 있으면 입장"을 한 번에 부르는 안이었다.
  **2026-09-25 합치기로 방 만들기 요청이 없어지고 입장이 글을 검사하게 돼(§3.3) 이 안은 그대로 쓸 수 없다 — 다시 정해야 한다.** 그 방을 처음부터 확정된 방으로 둘지 · 정원 · 강퇴 · 게시판 신호를 안 내는 것도 그때 묻는다.
- (2026-09-21 에 검토한 방향) **파티 방에 들어가도 된다는 허가는 이 앱이 아니라 `matching`이 서명해 주는 쪽이 낫다** — 이 앱은 SQS 지연 때문에 **확정 직후에는 그 파티를 모른다**(§7 "SQS 메시지 본문 3종"의 "그 직후 조회는 비어 있을 수 있다"와 같은 사정이다. 합친 뒤에도 이 사정은 그대로다 — **→ D-42 로 이 사정 자체가 없어졌다**: 파티 HASH 는 확정 순간에 있다).
  `matching`이 서명하게 되면 §5.1 (가)의 "서명은 이 앱만 한다"와 부딪힌다. 다른 안으로는 입장 검사가 활성 요청 키의 `status` · `partyId`를 **읽어** 통과시키는 것이 있었다(D-19의 "`EXISTS`만"을 완화해야 한다 — `matching`과 같이 정할 결정이다). **둘 다 미정이다.**
- → **2026-09-27 정해졌다(P-30 — docs/11 D-42 위에 Claude 가 정한 세부)** — **첫 방향 그대로다.** 클라이언트가 `MATCH_CONFIRMED` 의 `partyId` 를 들고 **`POST /api/v1/match-parties/{partyId}/room`** 을 부르면 "없으면 만들고 있으면 입장" 을 한 번에 한다. 합친 앱에 맞게 옮긴 것 — 입장은 글을 검사하는 `POST /rooms/{roomId}/members` 가 아니라 **전용 요청 · 전용 Lua(`enter-match-room.lua`)** 다. 그때 묻기로 한 것도 정했다 — **처음부터 확정된 방**(D-23 승계) · 정원은 HASH 의 `target` · 강퇴는 그대로 · **게시판 신호를 내지 않는다** · 처음 부른 사람이 방장. **둘째 방향(허가를 `matching` 이 서명 / 활성 요청 키의 `status` 를 읽기)은 받지 않았다** — 허가는 파티 HASH 의 `member:{userId}` 를 `HEXISTS` 로 보는 것으로 족하고, "SQS 지연 때문에 확정 직후 그 파티를 모른다" 는 사정 자체가 없어졌다(HASH 는 확정 순간에 있다). 활성 요청 키는 읽지 않는다 — D-19 그대로.

**(라) `status=PARTY` 해제** — §7 "확정된 사용자를 푸는 길"에 대한 방향이다.

- **아무도 지우지 않는다.** 확정 때 `matching`이 자기 활성 요청 키에 **짧은 수명**을 걸고, 그 안에 사용자가 파티방에 들어오면 입장 표시 키(이 앱이 쓴다 — 2026-09-25 합침)가 자물쇠를 이어받는다(`matching`의 `claim-request.lua`가 이미 입장 표시 키를 본다 — D-19).
  활성 요청 키는 수명이 다해 저절로 사라진다. D-19를 어기지 않는다 — 각자 자기 키만 쓴다.
- **이 방향이면 이 앱이 푸는 주체가 되는 안(파티가 닫힐 때 `PartyClosed.fifo`를 계기로 푼다)은 필요 없어진다.** 이 앱은 여전히 활성 요청 키를 쓰지 않는다(§2 — `EXISTS`로 볼 뿐이다).
  `PartyClosed.fifo` 자체(닫힌 파티의 멤버로 `recent_players`를 만든다 — §3.4 · D-13)는 이 검토와 무관하다(게시판 파티는 2026-09-26 부터 이 큐 없이 닫는다 — P-25).
- ~~**미정이다.**~~ → **2026-09-27 정해졌다(docs/11 D-42) — 이 방향 그대로다.** `matching` 이 확정 때 활성 요청 · 수락자 SET 에 **60초**, 파티 HASH 에 **600초** 수명을 건다. 이 앱은 활성 요청 키를 쓰지 않고, 자동 매칭 방의 Lua 는 그 키를 읽지도 않는다(P-30). `PartyClosed.fifo` 는 D-42 로 자동 매칭 파티에도 없어졌다(§3.4). §7 의 그 행은 닫혔다.

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `platform` 브랜치**다. 이력이 없는 별도 브랜치에서 시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       matching 브랜치
├── notification/   notification 브랜치
(room/ 폴더는 2026-09-27 에 지웠다 — 옛 app:room 은 2026-09-25 에 이 앱에 합쳤고 기록은 브랜치 origin/room 에만 있다)
└── platform/       platform 브랜치 (이 폴더)
    ├── START_HERE.md            시작 안내 — 지금 어디까지 됐나 · 만드는 순서 · 다음에 닿기 전에 물어야 하는 것
    ├── CLAUDE.md · README.md    규칙 / 짧은 소개
    ├── contracts/
    │   └── platform-api.md      **이 폴더에서 정한 계약** — 경로 · 스키마 · 에러 코드 · 토큰 · 모집 글 · 방 · 자동 매칭 파티의 방 · 알림 · gameconfig 를 읽는 것 + "원본에 올려야 할 것"(P-1~P-30) (§3.1)
    └── backend/                 스프링 앱. matching/backend/ 와 같은 모양이다
        ├── build.gradle · settings.gradle · gradlew · gradle/wrapper/
        ├── .dev-keys/           개발용 JWT 키(private.pem · public.pem). 앱이 만든다. **git 에 올리지 않는다** — 옆 서비스는 public.pem 으로 검증한다 (§5.1)
        └── src/
            ├── main/java/com/queuemate/platform/    PlatformApplication · package-info(패키지를 나누는 법 — 먼저 읽는다)
            │   ├── common/      error(에러 본문 · 제약 위반을 에러 코드로) · web(Origin 검사) · security(토큰 서명과 검증 · 보안 설정 · TokenClaims) · push(알림 봉투 · 채널 접두사 · 발행) · gameconfig(운영자가 심는 공유 설정을 읽는 곳 — mode · tier 검증. 쓰지 않는다 — §3.6)
            │   ├── account/     재발급 · 로그아웃 · 프로필 · 게임 계정(게임 프로필) · oauth/(소셜 로그인 — 가입 · 로그인은 이것뿐이다. 제공자와 주고받는 것) · stats/(LoL 전적 · 티어를 Riot API 에서, PUBG 전적 · 티어를 PUBG API 에서(2026-09-29 — P-36) 긁는 곳 — 게임 계정 연결의 동기 긁기와 사용자가 누르는 동기 갱신 · Redis 락 · 쿨타임 · PUBG 시즌 캐시)
            │   ├── social/      차단 · 친구 요청과 친구 · 신고 · 최근 함께한 사람(읽기)
            │   ├── party/       모집 글(쓰면 방도 만든다) · 목록 · 입장의 글 검사(service/PostEntryGate) · 방장 확정의 기록. board/(게시판 채널 신호 발행). match/(matching 의 파티 HASH qm:party:{partyId} 를 읽는 곳 — MatchPartyKeys(접두사 사본) · MatchPartyReader. 자동 매칭 파티의 방 POST /api/v1/match-parties/{partyId}/room — 2026-09-27 · P-30)
            │   └── room/        방 안의 일 — 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 시그널 · 방 알림(2026-09-25 합침). service/RoomService(방 만들기 · 확정 · states — party 가 부른다) · RoomMemberService · RoomRedis(Redis 장애 → 503) · redisKeys/RoomKeys(방 키의 원본) · RoomErrors. Lua 는 main/resources/lua/
            ├── main/resources/application.yaml      환경변수 + 기본값 (포트 8082 · PostgreSQL 5433 · Redis 6380 · JWT · Origin · 게시판 · 방 · 소셜 로그인)
            ├── main/resources/db/migration/         Flyway. V1__schema.sql(스키마 public 하나 · FK 포함 — 2026-09-26 에 옛 V1~V8 과 account/ · social/ · party/ 폴더를 합쳤다) + V2(2026-09-27 — parties.match_party_id · 자동 매칭 파티, P-30) + V3(2026-09-29 — social_identities 에 GOOGLE · provider_user_id 255, P-33) + V4(2026-09-29 — game_accounts.main_position 을 지운다, P-35) + V5(2026-09-29 — game_accounts.tier → 사다리별 tiers jsonb, P-36) + V6(2026-09-30 — recruit_posts.host_position · 방장 포지션, P-38) (§3.5)
            ├── test/java/com/queuemate/platform/    ApiTestSupport(API 테스트의 바탕) · PlatformApplicationTests + 도메인별 테스트. 가짜 제공자는 account/oauth/FakeOAuthProvider
            └── test/resources/config/application.yaml   테스트에서만 덮는 설정(개발용 키를 임시 폴더에)
```

커밋은 `matching`과 같은 AngularJS commit convention을 따른다. 형식: `type(scope): subject`

- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: **`platform`** (문서만 바꾸면 `docs`, 계약은 `contracts`, 인프라 설정은 `infra`)
- subject: **한글**, 50자 이내, 끝에 마침표 없음
- body: **한글**로 무엇을/왜. 어떻게는 코드가 말한다. 3줄 이내
- type/scope 키워드만 영어를 유지한다
- footer: `BREAKING CHANGE: <설명>`, revert는 `revert: <원 subject>` + 원 commit hash

규칙:
- **커밋은 파일 경로를 명시해서 한다** (`git add <경로>` / `git commit <경로>`). IDE가 자동으로
  스테이징해 둔 엉뚱한 파일이 섞여 들어가는 일이 있다. `git add -A` / `git add .` 금지.
- 하나의 커밋은 하나의 목적만 담는다. type이 다르면 나눈다 — 의존성/설정(`build`)과 구현(`feat`)을
  섞지 않는다. 문서·계약(`CLAUDE.md`, `README.md`, `contracts/**`) 변경과 기능 구현을 한 커밋에 섞지 않는다.
- 기능과 그 기능의 테스트는 한 커밋에 담는다.
- 각 커밋 시점에서 빌드가 통과해야 한다.

## 9. 작업 방식

작업 지시를 받으면 **기본적으로 서브 에이전트를 띄워서 처리한다.** 직접 파고들지 않는다.

- 조사, 코드 탐색, 문서 정리, 여러 파일에 걸친 변경은 전부 서브 에이전트에 맡긴다
- 서브 에이전트는 이 대화를 모른다. **필요한 맥락을 프롬프트에 다 적어 준다** —
  읽어야 할 파일, 건드리면 안 되는 파일, 지금까지 정해진 결정
- 서로 겹치지 않는 일이면 **여러 개를 한 번에 띄운다**
- 돌아온 결과는 그대로 옮기지 말고 **직접 확인한 뒤** 요약해서 보고한다

예외는 하나다 — 사용자가 **묻기만 한 것**(설명, 확인, 의견)은 서브 에이전트 없이 바로 답한다.

**무엇부터 만들지는 `README.md` "만드는 순서"를 따른다**(지금 어디까지 됐는지 · 단계마다 끝났는지 확인한 것은 `START_HERE.md` §1 · §3, **다음에 닿기 전에 물어야 하는 것**은 §4). 순서를 바꾸려면 먼저 묻는다.
**경로 · 스키마 · 에러 코드 · 클레임을 바꾸면 `contracts/platform-api.md`를 같이 고친다**(§3.1).

완료 조건(`matching/CLAUDE.md` §6): happy path / 실패·중복·timeout 처리 / 테스트 / 로그·metric 포인트 / 계약 불일치 없음(또는 `contracts/`에 기록) / 불변식 검증.

운영 규칙:
- **포트 6379, 5432와 `queuemate-v2-*` 컨테이너(`queuemate-v2-redis-1`, `queuemate-v2-postgres-1`)는 다른 프로젝트 것이다.
  절대 건드리지 마라.** 테스트용 Redis/PostgreSQL은 **다른 포트로 따로 띄우고, 끝나면 종료한다.**
- 이 컴퓨터는 WSL(mirrored 네트워크)이고 Docker는 Windows의 Docker Desktop이다. WSL에서는 **`docker.exe`**로 부른다.
- `bootRun`으로 앱을 띄웠으면 **반드시 종료해라.** 안 죽이면 포트가 물려 다음 검증이 실패한다.
  끝나면 `./gradlew --stop`도 한다. 빌드·테스트는 `backend/` 안에서 돌린다.
- **옆 폴더 `matching`, `notification`, `room`의 파일은 여기서 고치지 않는다.** 읽기만 한다. 고칠 것은 그 폴더에서 따로 작업한다.
- **Claude가 파일을 고친 뒤에는 IntelliJ에서 `Ctrl+Alt+Y`(디스크에서 다시 읽기)를 눌러야 한다.** 안 누르고 그 파일을 계속 치면 다음 저장 때 IntelliJ가 메모리의
  옛 버전으로 덮어써 Claude의 변경이 사라진다. 반대로 소유자가 IntelliJ에서 **저장하지 않은 코드는 Claude가 볼 수 없다.**
- **셸은 zsh다.** 함수나 스크립트에서 `qm:room:$1:host`처럼 쓰면 `$1:h`가 특수 문법으로 해석돼 문자열이 깨진다 — 변수는 `${1}`처럼 **중괄호로 감싼다.**
- **`docker.exe` 출력에는 `\r`이 섞인다.** 값을 비교하기 전에 `tr -d '\r'`.
- `platform` · `room` · `notification` 폴더는 git worktree이고 WSL에서 만들어서 `.git` 파일에 `/mnt/c/…` 경로가 적혀 있다 — Windows의 git(IntelliJ)은 이 폴더를
  저장소로 인식하지 못해 **IntelliJ에 Commit 탭이 뜨지 않는다. 커밋은 WSL에서 한다.**
- `notification`을 띄운 직후 첫 SSE는 Redis 구독이 걸리기까지 **7초쯤** 걸렸고 그 사이의 알림은 오지 않았다(2026-09-20 측정. 원인은 확인하지 않았다).
  SSE로 도착을 확인할 때는 `PUBSUB NUMSUB`으로 **구독자가 1이 된 것을 본 뒤에** 움직인다.
- 위 함정은 전부 `room`을 만들며 실제로 겪은 것이다. 명령과 그 밖의 환경 함정(`pkill -f` 금지, 느린 빌드)은 이 폴더의 `docs/LOCAL_ENV_LESSONS.md`에 있다(옛 `room/docs/NOTIFICATION_LESSONS.md` 를 2026-09-27 에 옮겨 왔다 — `room` 폴더를 지우면서).

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| **시작 안내** — 지금 어디까지 됐나, 옆 서비스(`notification` · `matching`)의 임시 식별(`?userId=` — 2026-09-27 에 둘 다 쿠키로 끝났다)과 이 앱이 줄 수 있는 것, 만드는 순서와 단계별 확인, 다음에 닿기 전에 물어야 하는 것 · 소유자가 검토해야 하는 것, 로컬에서 띄우는 법 | `START_HERE.md` (이 폴더) |
| **이 폴더에서 정한 계약** — 공통(에러 · 인증 · `Origin`) · access 토큰 · 계정 · 게임 프로필 · 소셜 로그인 · 차단 · 모집 글/목록(글 쓰기가 방을 만든다 · 만료) · **방**(입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 시그널 · 방 알림 · Redis 키 — 옛 `room`의 계약을 합쳤다) · 친구/신고/최근 함께한 사람 · 알림 · refresh 토큰 · **gameconfig를 읽는 것**(§3.6) · **자동 매칭 파티의 방**(`POST /api/v1/match-parties/{partyId}/room` · 파티 HASH 의 필드 계약 — 2026-09-27) · **"원본에 올려야 할 것" P-1~P-30.** Claude가 정했고 소유자가 항목별로 검토하지 않았다(P-11 ~ P-29는 소유자가 직접 정한 것이고 P-30은 D-42 위에 Claude가 정한 세부다) | `contracts/platform-api.md` (이 폴더) |
| **ERD**(테이블의 원본은 `backend/src/main/resources/db/migration/V1__schema.sql`이다 — 그림이 어긋나면 마이그레이션이 맞다. **2026-09-26 에 스키마가 `public` 하나가 되고 FK가 생겼다** — 그림이 스키마 셋이면 낡은 것이다) | <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq> |
| 매칭 엔진 규칙 (제품 경계·INV·Contract first의 원형) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 배달 규칙 (받는 쪽이 메시지를 어떻게 다루나) / 옛 `room` 앱의 규칙(**2026-09-25 에 합치기 전의 기록 — 참고만. 방의 규칙은 이 파일 §3.3 과 `contracts/platform-api.md` "방"이다**) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/notification/CLAUDE.md` · `git show origin/room:CLAUDE.md`(옛 `room` 앱 — **폴더는 2026-09-27 에 지웠다.** 기록은 브랜치 `origin/room` 에 남아 있다 — `git show origin/room:<경로>` 로 본다) |
| 옛 `room` 앱의 계약 · 결정 기록(합치기 전의 모습 그대로 남아 있다 — **참고만**. 이 폴더의 계약 "방" 절이 그것을 옮겨 와 고친 것이다) | `git show origin/room:contracts/room-api.md` · `git show origin/room:docs/DECISIONS.md`(옛 `room` 앱 — **폴더는 2026-09-27 에 지웠다.** 기록은 브랜치 `origin/room` 에 남아 있다 — `git show origin/room:<경로>` 로 본다) |
| 로컬 환경 함정 (띄우고 죽이기 · IntelliJ · worktree · 테스트) | `docs/LOCAL_ENV_LESSONS.md` (이 폴더 — 옛 `room/docs/NOTIFICATION_LESSONS.md` 를 옮겨 왔다) |
| 결정 로그 — #13~#17 · #20~#26 · D-1~D-4 · **D-9** · **D-11**~**D-16** · **D-18** · **D-19**(D-11 16번과 D-16의 활성 요청 키 대목을 개정 — 파일 머리의 "낡은 항목 주의"로 걸러 읽는다) · **D-20**(게시판 목록 — 방 안 사람 카드·이 앱이 조립·게시판 채널 신호·차단은 방 안의 누구와든. D-11 14번의 범위를 정하고 D-16의 방 키 읽기 범위를 늘렸다) · **D-21**(`room`의 방 안의 규칙과 계약 — 방 키 · 수명 · 방장 확정. **확정 표시는 `room`이 쓰고 이 앱은 읽는다.** D-16의 "닫힘 표시" 가능성은 받지 않았다) · **D-22**(게시판 채널은 게임을 구분하지 않는 `qm:pubsub:board` 하나 · `topics`를 없애고 거르기는 클라이언트가 한다 — D-20 (다)를 개정) · **D-23**(확정한 방은 방장이 나가도 없어지지 않고 방장 자리를 넘긴다 — **확정한 방에서는 방장 키의 값이 바뀔 수 있다.** `room`의 게시판 채널 신호 발행이 구현됐다 — D-21 · D-20을 개정). **D-16 · D-19 ~ D-23은 두 앱을 전제로 쓰였다 — 2026-09-25 에 `room`을 합쳐(P-22 · §3.3) D-33이 개정했다** · **D-24 ~ D-35**(2026-09-26 — 이 앱에서 소유자가 정한 것. P-2 · P-11 ~ P-25와의 대응은 `contracts/platform-api.md` 맨 아래 표 — P-25는 D-36) · **D-36 ~ D-40**(2026-09-26 · 2026-09-27 — P-25 → D-36 · P-26 → D-37 · P-27 → D-38 · P-29 → D-39 · P-28 → D-40) · **D-42**(2026-09-27 — outbox · SQS `ProposalConfirmed.fifo` 를 두지 않고 이 앱이 파티 HASH `qm:party:{partyId}` 를 읽어 파티 · 방을 만든다. #18 · #21 · D-13 을 개정 — §3.4 · P-30) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/11_DECISION_LOG.md` |
| 알림 계약 (봉투·발행 주체·`WEBRTC_SIGNAL`·SQS FIFO(D-42 로 큐 0개 — 그 파일의 SQS 절은 기록이다)·계약 구멍) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/events.md` |
| 계약 사본의 지위와 앞서간 변경을 적는 법 / platform 소관 자원 목록(머리말) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/README.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/openapi.yaml` |
| 봉투를 만드는 코드(본보기) / 채널 접두사 원본(`PUSH_CHANNEL_PREFIX`) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` |
| `matching`이 읽는 `blocks`의 모양 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/test/resources/schema.sql` |
| 왜 PostgreSQL인가·스키마 배치(**§3의 배치는 2026-09-26 에 개정됐다 — 이 앱은 `public` 하나다**, §3.5)·outbox·INV-9 제약 / 배포 그림에서 platform의 자리 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/WHY_POSTGRESQL.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/AWS_ARCHITECTURE.md` |
| 제품 정의와 non-goals / 예약 규칙 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/00_PRODUCT_SPEC.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/04_RESERVATION_MATCHING_SPEC.md` |
| 버전 맞추기, 설정 본보기 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/build.gradle` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/resources/application.yaml` |
| `matching`이 platform을 기다리던 일 (① 파티 풀기 ③ outbox) — **2026-09-27 D-42 로 둘 다 닫혔다**(① 은 60초 TTL + 이 앱의 입장 표시 키, ③ 은 outbox 대신 이 앱이 파티 HASH 를 읽는다). 그 뒤의 상태는 §0-6 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/HANDOFF.md` §0-6 |

## 11. 이 저장소에서 하지 말 것 (요약)

- 매칭 로직, 매칭 Redis 키(`qm:party:*` · `qm:user:*` · `qm:proposal:*` · `qm:lock:*`) 접근(**예외 둘 말고는 없음** — 활성 요청 키는 `matching`만 쓰고 이 앱은 `EXISTS`만 한다. 입장 표시 키는 이 앱이 쓰고 `matching`이 `EXISTS`로 본다 — D-19. 2026-09-25 에 `room` 을 합쳐 입장 표시 키의 주인이 이 앱이 됐다) — `matching`의 일이다. **예외는 둘이다 — `qm:gameconfig:*` 읽기**(2026-09-24 소유자 결정 — §3.6. 키 둘을 `EXISTS` · `ZSCORE`로 읽을 뿐이고 **쓰지 않는다**)**와 `qm:party:{partyId}` 읽기**(2026-09-27 소유자 결정 — docs/11 D-42 · §3.3. 확정된 자동 매칭 파티를 방으로 만들 때 `HGETALL` · `HEXISTS` 만. **쓰지도 지우지도 `EXPIRE`를 걸지도 않는다.** 그렇다고 `qm:user:*` · `qm:proposal:*` · `qm:lock:*` 의 금지가 풀린 것이 아니다 — 활성 요청 키는 여전히 `EXISTS` 만이고, 자동 매칭 방의 Lua 는 그 키를 읽지도 않는다). `SseEmitter` / WebSocket / 연결 보유 — `notification`의 일이다
- **방 안의 일**은 2026-09-25 부터 이 앱의 `room` 패키지가 한다(P-22). **방을 바꾸려면 Lua 스크립트를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거쳐라** — 맨손으로 `SADD` · `SET` 하면 정원 · 입장 표시 · 활성 요청 확인이 한 스크립트에 묶인 불변식이 깨진다. **글 쪽(`party`)이 방 키를 Redis로 직접 읽거나 쓰지 마라** — `RoomService`(`create` · `confirm` · `states`)를 부른다(§3.3). **글 쓰기와 방장 확정 밖에서 트랜잭션 안에 Redis 호출을 넣지 마라**(그 둘만 예외다 — Lua 한 번). **게시판의 방은 글 쓰기가(소유자 결정 C), 자동 매칭 파티의 방은 `POST /api/v1/match-parties/{partyId}/room` 이(2026-09-27 — D-42 · P-30) 만든다 — 그 밖의 방 만들기 요청을 두지 마라.** 자동 매칭 파티의 방에 입장 `POST /rooms/{roomId}/members`(글을 검사한다) · 방장 확정(이미 확정된 방이다)을 쓰지 마라. **`qm:party:{partyId}` 에 쓰거나 지우거나 `EXPIRE` 를 걸지 마라 — 읽기만이다**(D-42). **자동 매칭 방의 Lua 가 활성 요청 키를 보게 만들지 마라**(확정 뒤 60초 동안 그 키가 곧 그 파티라 아무도 못 들어온다 — P-30). **자동 매칭 파티의 방을 만들 때 DB 를 트랜잭션 안에서 Lua 와 묶지 마라**(DB 먼저 커밋 · Lua 는 밖 — 예외는 여전히 글 쓰기 · 방장 확정 둘이다). **확정된 글을 방장 키가 없다고 만료시키지 마라**(§3.3 · D-23). **방 키를 못 읽은 것을 "방이 없다"로 읽지 마라**
- **입장 금지 · 자동 합류 건너뛰기 목록(`qm:room:no-entry:*` · `qm:room:no-auto-join:*` — 2026-09-29 소유자 결정 · P-32)을 Lua 밖 자바에서 쓰기**(강퇴 · 나가기 스크립트가 방에서 빼는 것과 한 번에 적는다 — 따로 쓰면 뺐는데 금지가 안 남는 틈이 생긴다) · **`party` 가 그 목록을 Redis 로 직접 읽기**(`RoomService#noAutoJoinRooms` 를 부른다) · 스스로 나간 사람의 직접 입장을 막기(자동 합류만 건너뛴다) · 자동 매칭 방(`enter-match-room.lua`)이 금지 목록을 보게 만들기(**미정**이다 — 묻는다)
- 예약(REST·짝 찾기·`RESERVATION_*` — `app:reservation`, D-15), TURN credential 발급, **gameconfig 모듈**(모드 설정을 정하고 · 심고 · 해석하는 것 — `app:matching`의 것이다. **이 앱은 읽기만 한다** — 검증은 값이 있는지만 보고, 게시판 방 먼저 합류가 모드 HASH 의 `tierRule` · `targetPartySize` · `tierLadder`(2026-09-29) 와 `:tier-range:` 를, 모집 글의 방장 포지션이 `positionUniqueness`(2026-09-30 · P-38)를 읽는다. 뜻을 정하거나 seed를 심지 않는다, §3.6) — 각각 다른 배포 단위의 일이다
- 엔드포인트 경로·스키마·payload 필드·테이블 컬럼을 **지어내기** — 묻고, 정한 것은 `contracts/platform-api.md`에 적는다. 2026-09-21에 Claude가 정해 구현한 것은 **소유자가 맡겨서** 한 것이다 — 남은 미정(§7)에 같은 방식을 되풀이하지 마라. **코드만 바꾸고 `contracts/platform-api.md`를 안 고치기**, 이미 적용된 마이그레이션 파일 고치기(§3.5 — **운영 DB가 생긴 뒤부터다.** 2026-09-26 에는 운영 DB가 없어 옛 V1~V8을 `V1__schema.sql` 하나로 갈아 끼웠다)
- 공개 사용자 탐색(사람 검색·둘러보기)·길드·피드·팔로우·좋아요·모집과 무관한 공개 채팅방 — 여전히 금지다(§1 · D-11)
- 게시판 모집의 **남은** 미정 사항(§7.1 "정할 것")을 **임의로 정해 구현하기** — 구현하다 그 지점에 닿으면 그때 사용자에게 물어라
- 채널 접두사를 `matching`과 따로 바꾸기, 방 키 상수를 `room/redisKeys/RoomKeys` 밖에 또 적기 · D-19 의 두 키 이름을 `matching`과 따로 바꾸기, **gameconfig 접두사를 `matching`의 `SharedKeys` · seed와 따로 바꾸기**(§3.6 — fail-open이라 검증이 조용히 꺼진다) · **`mode` · `tier`의 값 목록을 이 앱에 상수로 베껴 두거나 seed를 이 앱이 심게 만들기** · **gameconfig를 읽으려고 `matching`을 HTTP로 부르기**, 알림 실패로 본 작업을 실패시키기, 모집 중이 아니거나 차단 관계인 글의 방에 들여보내기(입장 요청 안의 글 검사를 건너뛰기 — **차단은 방장만이 아니라 방 안의 전원과 본다** — D-20)
- 게시판 채널 신호에 **데이터(`roomId`·프로필·방 안 사람 등) 싣기** — 방송은 사람별로 거를 수 없어 차단이 뚫린다(D-20 · D-22). 입장이 포지션을 받게 하기(방 키의 모양이 바뀐다), 목록을 그리려고 방 안 사람 목록 요청(`GET …/members`)을 부르기(방 안의 사람만 보는 창구다 — D-20)
- **LoL · PUBG 게임 계정의 `tier`를 요청으로 받기**(2026-09-27 · 2026-09-29 소유자 결정 — P-26 · P-36. 게임사 API 에서 채운다. 자기신고는 VALORANT 하나다) · **티어를 사다리 없이 한 칸으로 되돌리기**(P-36 — `tiers` jsonb 가 원본이다) · **모드 이름으로 사다리를 가르는 코드**(gameconfig 모드 HASH 의 `tierLadder` 만 본다 — 없으면 방장 티어를 모르는 것이다) · **PUBG 시즌 캐시를 프로세스 로컬에 두기**(stateless — Redis `qm:pubg:season:*` 다) · PUBG 의 429 를 재시도하기(한도가 분당 10회다), LoL 게임 계정을 긁기에 실패했는데 저장하기. **게임 계정의 주 포지션(`mainPosition` · `main_position`)을 되살리거나, 오는 `mainPosition` 을 조용히 버리게 바꾸기**(2026-09-29 소유자 결정 — P-35. 값이 있으면 400 이다), **모집 글의 방장 포지션(`hostPosition` — 2026-09-30 · P-38)을 카드(`MemberCard`)로 옮기거나 · 포지션이 없는 모드에서 오는 값을 조용히 버리거나 · 모드 이름으로 "포지션이 있는 모드" 를 가르기**(gameconfig 의 `positionUniqueness` 만 본다) · **게시판 방 먼저 합류가 방장 포지션을 보게 지어내 붙이기**(미정 — §7.1), Riot 의 최근 경기에서 포지션을 뽑기
- **없앤 글의 `purpose`를 되살리기**(2026-09-27 소유자 결정 — P-29. `matching`의 `PlayPurpose`는 그쪽 것이라 건드리지 않는다). **게시판 방 먼저 합류(`POST /api/v1/posts/auto-join` — 2026-09-28 · P-28)에 활성 요청 키를 만들거나 · `matching` 을 부르거나 · 글에 "자동 합류 허용" 칸을 두거나 · 정원을 방 정원 5 로 두기**(정원은 그 모드의 `targetPartySize` 다) · 그 요청의 gameconfig 읽기를 fail-open 으로 바꾸기 · PUBG `PLATFORM` 값을 방장 `server` 와 대조하는 것을 지어내 붙이기(**미정**). **소셜 계정을 이메일 등으로 자동으로 합치기**(P-27 — 잇기는 로그인한 채로만 한다)
- **없앤 `filledPositions`를 되살리거나 그 대안을 지어내 만들기**(2026-09-24 소유자 결정 — §7.1 "2026-09-24에 없앤 것". **다시 둘 것인가는 미정이고, 입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다**)
- **파티 닫힘을 SQS로 돌리거나, 확정된 방의 방장 키만 없다고 파티를 닫기**(2026-09-26 소유자 결정 — §3.3 "파티 닫힘" · P-25. 게시판 파티는 같은 앱 안에서 닫고, 방장 키만 없는 것은 승계 중이다 — D-23), 파티 닫힘 · 자동 매칭 파티의 방 만들기에 `PARTY_*` 알림을 지어내 붙이기(이름과 `payload`가 미정이다), **outbox · SQS · AWS SDK 를 들이기**(2026-09-27 D-42 — 큐가 0개다, §3.4)
- `blocks`의 모양을 `matching`과 상의 없이 바꾸기, 스키마를 다시 나누거나 DB 롤 · `GRANT`를 두기(§3.5 — 2026-09-26 에 `public` 하나가 됐다. **테이블 사이의 JOIN · FK는 이제 된다** — 옛 "크로스 스키마 FK·JOIN 금지"가 풀렸다), `matching`이 읽는 테이블을 `blocks` 밖으로 늘리기
- **사용자 번호 말고 다른 것을 사용자의 식별자로 쓰기**(§3.5 — 2026-09-22 소유자 결정) — JWT의 `sub`·알림 채널·방 키·URL·요청과 응답 본문의 `userId`·다른 테이블의 `*_id`는 전부 **사용자 번호**다. 닉네임은 보여 주는 이름일 뿐이다. **없앤 직접 가입 · 비밀번호 로그인 · `loginId` · 로그인 실패 제한을 되살리기**(2026-09-26 소유자 결정 — P-24). 새 테이블에 PK를 `bigint identity` 말고 다른 것으로 두기, 본문의 id가 숫자가 아닌 것을 400으로 갈라 주기(**없는 사용자와 같은 404다**)
- `조회 → 판단 → 삽입`으로 불변식 지키기, H2로 제약·권한을 검증했다고 치기, 경계를 넘는 새 동기 호출
- 인증(§5.1)에서 — HS256으로 비밀 키 나눠 갖기, JWKS 엔드포인트, access denylist, CSRF 토큰, 서비스에 CORS 설정 넣기, **상태를 바꾸는 GET**, access 토큰에 닉네임처럼 바뀌는 값 싣기, jjwt 등 다른 JWT 라이브러리 들이기, Spring의 `oauth2-client` 들이기(세션을 쓴다), **`token_use`를 안 보고 토큰 받기**, `TokenClaims`의 값(`iss` · 쿠키 이름 · `token_use`)을 옆 서비스와 따로 바꾸기, `backend/.dev-keys/`를 git에 올리기, **refresh를 JWT로 바꾸기**(불투명 UUID이고 원본은 Redis의 줄이다 — §5.1 (마)), **`KEYS`/`SCAN`으로 한 사용자의 refresh를 훑기**, 재발급 실패를 이유별로 갈라 알려 주기(전부 같은 401 `INVALID_REFRESH_TOKEN`이다)
- **개발용 로그인(`POST /api/v1/auth/dev-login` — 2026-09-29 · P-34)을 운영에서 켜기**(`DEV_LOGIN_ENABLED` 를 운영 설정에 넣기 — 켜면 누구든 아무 닉네임으로 로그인한다) · **그것을 `TEMP-DEV-LOGIN` 표식 없이 늘리거나 다른 요청에서 부르기**(걷어낼 때 `grep` 한 번으로 다 찾혀야 한다) · 그것을 "쿠키가 없으면 `userId`" 처럼 **검증하는 쪽을 느슨하게 하는 스위치로 바꾸기**
- Kafka/RabbitMQ/Redis Streams, k8s/HPA/sticky session 전제 구현
- 포트 6379·5432 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`, 옆 폴더 파일 수정
