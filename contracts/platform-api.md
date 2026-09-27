# platform 계약 — 이 폴더에서 정한 것

> **지위.** 계약 원본(queueMate 본 저장소 `feature/frontend` 의 `contracts/`)이 이 컴퓨터에 없어 **여기에 먼저 적는다**(`CLAUDE.md` §3.1).
> 2026-09-21 에 소유자가 "네가 만들어 봐라"고 맡겼고, 아래는 Claude 가 정해 구현한 것이다 — **소유자가 아직 항목별로 검토하지 않았다.**
> **2026-09-22 에 소유자가 직접 정한 것이 둘 있다** — **모든 테이블의 PK 를 `bigint identity` 로 하고 `userId`(사용자 번호)와 `loginId`(로그인 아이디)를 가른 것**(P-11. 2026-09-19 의 결정을 개정한다 — **`loginId` 쪽 절반은 2026-09-26 에 없어졌다**, P-24)과 **스키마별 DB 롤을 두지 않는 것**(아래 "차단" — **2026-09-26 에 스키마가 `public` 하나가 되며 물음째 없어졌다**, P-23). 그 둘은 "소유자 결정"이라고 적었다.
> **2026-09-23 에 소유자가 정한 것이 셋 더 있다** — **LoL 의 전적을 Riot API 에서 긁는 것**(P-13. 아래 "게임 프로필" 의 "전적을 긁는 것" — 비동기 · 평점은 넣지 않는다. **그날 정한 "긁는 시점 둘" 은 2026-09-24 에 하나로 줄었다** — 바로 아래) · **게시판 목록의 페이지 나누기(커서 방식)**(P-14. 아래 "모집 글 · 목록" 의 "목록의 페이지 나누기") · **refresh 토큰**(P-15. 아래 "refresh 토큰" — access 가 `PT15M` 으로 줄고 `TEMP-NO-REFRESH` 가 없어졌다).
> **2026-09-24 에 소유자가 정한 것이 여섯 더 있다** — ① **`mode` 와 `tier` 의 값을 `matching` 의 gameconfig(Redis)에서 읽어 검증하는 것**(P-16. 아래 "gameconfig 를 읽는 것" — 읽는다 · Redis 를 못 읽으면 통과시킨다(fail-open) · `mode` 가 필수가 됐다 · `tier` 도 같이 본다)
> ② **모집 글을 쓸 때 전적을 긁던 것을 없앤 것**(**P-13 의 개정이다 — 새 번호를 두지 않는다.** 아래 "전적을 긁는 것" — 긁는 시점이 하나가 되고 신선도 장치가 없어졌다)
> ③ **"전적 갱신" 요청을 둔 것**(P-17. 아래 "전적을 긁는 것" 의 "전적 갱신" — `POST …/game-accounts/{game}/refresh` · **동기** · 쿨타임 2분 · 상한 30초. ②로 낡은 채 남게 된 전적을 사용자가 직접 갱신하는 길이라 **긁는 시점이 다시 둘이 됐다**).
> ④ **게시판 목록의 정렬과 커서를 `id` 하나로 한 것**(**P-14 의 개정이다 — 새 번호를 두지 않는다.** 아래 "모집 글 · 목록" 의 "목록의 정렬" 과 "목록의 페이지 나누기" — 전날 정한 커서의 속을 고친다. **정렬 키가 변하면 커서가 중복을 낸다**).
> ⑤ **글 한 줄에서 `filledPositions`(찾는 포지션 가운데 이미 채워진 것의 강조)를 없앤 것**(P-18. 아래 "모집 글 · 목록" 의 "글 한 줄" — **주 포지션은 그 방에서 할 포지션이 아니다.** **docs/11 D-20 의 ③ 을 개정한다**).
> ⑥ **방에 방장 말고 누가 있으면 모집 글을 고칠 수 없게 한 것**(P-19. 아래 "모집 글 · 목록" 의 `PATCH` — 409 `ROOM_HAS_OTHER_MEMBERS`. **조건이 바뀌는데 방 안 사람에게 알릴 길이 없다**).
> **2026-09-25 에 소유자가 정한 것이 셋 더 있다** — ① **게시판 목록에서 "만료 · 확정된 글은 10분만 보여 준다" 는 보존 기간을 없앤 것**(P-20. 아래 "목록의 정렬" — **모집 중 · 확정 · 만료를 전부 `id` 내림차순으로 보여 주고 끝난 글도 계속 남는다.** **P-5 가 정했던 10분 보존을 걷어낸다**)
> ② **커서의 base64url 한 겹을 없애고 글 번호를 그대로 쓰는 것**(**P-14 의 개정이다 — 새 번호를 두지 않는다.** 아래 "목록의 페이지 나누기" — **`cursor` 와 `nextCursor` 가 숫자다.** 감싸도 얻는 것이 없었다).
> ③ **게시판 목록의 `game` 을 필수로 만든 것**(P-21. 아래 "모집 글 · 목록" 의 "목록의 `game` 은 필수다" — **게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다.** 안 보내면 400 이다).
> **2026-09-25 에 소유자가 하나를 더 정했다 — `room` 앱을 이 앱에 합친다**(P-22). **1단계(옮겨서 돌게 하기) · 2단계(두 앱을 전제로 한 경계 장치 걷어내기)가 됐다.**
> 방의 요청(`/api/v1/rooms/**`)을 이 앱(8082)이 받고 인증이 `qm_access` 쿠키다. **방의 계약은 아래 "방" 절이다**(옛 `contracts/room-api.md` 를 합치고 그 파일은 지웠다).
> 2단계의 소유자 결정 셋 — ① **입장권을 없애고 입장 경로는 그대로 둔 채 그 안에서 글을 검사한다** ② (C) **글 쓰기가 방을 같이 만들고, 방을 못 만들면 글도 되돌린다** ③ **방과 글은 같이 산다** — 확정 전에는 방장이 나가도 글을 지워도 둘 다 끝난다.
> 같은 날 **`status` 필터는 두지 않고 · 오래된 글은 운영에서 소유자가 직접 지우고 · `verified` 는 당분간 자기신고를 믿는다**고 정했다(미정 목록에서 뺐다).
> 그래서 **입장권(`POST …/ticket`) · 방 만들기 요청(`POST /rooms/{roomId}`) · 확정 기록 요청(`POST /posts/{postId}/confirm`) · `room_seen_at` 이 없어졌다**(아래 "모집 글 · 목록" · "방").
> **2026-09-26 에 소유자가 하나를 더 정했다 — DB 스키마 셋(`account` · `social` · `party`)을 `public` 하나로 합치고 테이블 사이의 JOIN · FK 를 허용한다**(P-23).
> 테이블 이름은 그대로이고, 사용자 번호를 담는 칸에 `users(id)` FK(`ON DELETE CASCADE`)가 걸렸다 — **없는 사용자는 그 FK 의 위반으로 404 `USER_NOT_FOUND` 가 된다**(경로 · 본문 · 에러 코드는 바뀌지 않았다).
> 마이그레이션은 `V1__schema.sql` 하나로 다시 썼다 — 이 파일에 남은 옛 파일 이름(`party/V7__…` · `party/V8__…` 등)은 **그 변경이 들어온 때의 기록**이고 지금은 그 파일이 없다.
> **2026-09-26 에 소유자가 하나를 더 정했다 — 직접 가입 · 비밀번호 로그인을 없애고 소셜 로그인(카카오 · 디스코드)만 남긴다**(P-24).
> **`POST /auth/signup` · `POST /auth/login` · 비밀번호 · `credentials` 테이블 · 로그인 실패 제한(P-10) · `loginId` 가 없어졌다.** 식별자는 사용자 번호(`userId`) 하나이고 보여 주는 이름은 닉네임 하나다.
> 소셜로 처음 온 사람은 **닉네임만** 정한다(아래 "계정" · "소셜 로그인"). **왜** — 비밀번호 관리 · 이메일 인증 같은 부담을 지지 않는다. 테이블은 13개가 됐다.
> **2026-09-26 에 소유자가 하나를 더 정했다 — 확정된 방이 없어지면 파티가 닫히고, 그 파티원끼리 서로를 "최근 함께한 사람" 에 적는다**(P-25. 아래 "방" 의 "파티 닫힘").
> 게시판 파티는 `PartyClosed.fifo`(SQS)를 거치지 않고 이 앱 안에서 닫힌다. 글은 `CONFIRMED` 그대로이고 응답에 새 칸은 없다.
> **2026-09-27 에 소유자가 하나를 더 정했다 — LoL 게임 계정은 `gameNickname`(이름#태그)만 받고 `tier` · `mainPosition` 은 Riot 에서 채운다**(P-26. 아래 "계정" · "게임 프로필" · "전적을 긁는 것").
> LoL 의 `PUT` 은 **동기**가 됐다 — 저장하기 전에 Riot 을 긁고, 응답에 `tier` · `mainPosition` · `stats` 가 바로 들어 있다. 이름#태그가 Riot 에 없으면 404 `RIOT_ID_NOT_FOUND`(새 코드)다. **VALORANT · PUBG 는 지금대로 자기신고다.**
> **P-8 · P-13 · P-17 을 개정한다**(새 번호를 두었다 — 게임 계정의 요청 모양이 바뀌는 새 결정이라서다).
> **결정 로그(2026-09-26)** — 소유자가 정한 것 가운데 **P-2 · P-11 ~ P-24 가 `matching` 의 docs/11 에 D-24 ~ D-35 로, P-25 가 D-36 으로 올라갔다**(어느 P 가 어느 D 인지는 맨 아래 표의 비고). **P-26(2026-09-27)은 D-37 이다**(docs/11 D-27 을 개정한다).
> Claude 가 정한 것(P-1 ~ P-10 과 각 행의 "Claude 가 정한 것")을 **소유자가 항목별로 검토하지 않았다는 지위는 그대로다** — 결정 로그에 올라간 것은 소유자의 결정이다.
> 원본과 합칠 때 맨 아래 "원본에 올려야 할 것" 표를 들고 간다. ERD 는 <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq>, 테이블의 원본은 `backend/src/main/resources/db/migration/V1__schema.sql` 이다(2026-09-26 부터 파일 하나 · 스키마 `public` 하나).

## 공통

- 경로는 전부 `/api/v1/**`. 본문은 JSON(`application/json`), 시각은 ISO-8601 UTC.
- **모든 테이블의 PK 는 `bigint GENERATED ALWAYS AS IDENTITY` 다 — 식별자는 전부 숫자다**(2026-09-22 소유자 결정. 2026-09-19 의 "사용자 id 는 가입할 때 정한 로그인 아이디(문자열)"를 개정한다 — `CLAUDE.md` §3.5 · 아래 P-11).
  **사용자의 식별자는 `userId` 하나다**(2026-09-22 에는 로그인 아이디 `loginId` 를 따로 두어 둘로 갈랐다 — `loginId` 는 2026-09-26 에 없어졌다, P-24) — **`userId`** 는 사용자 번호(`users.id`)이고 **밖으로 나가는 모든 자리**가 이것이다(JWT 의 `sub` · 알림 채널 `qm:pubsub:push:{userId}` · 방 키와 멤버 SET 의 `{userId}` · URL 의 `{userId}` · 요청과 응답 본문의 `userId` · 다른 테이블의 `*_id` 컬럼 전부 — 2026-09-26 부터는 전부 `users(id)` 로 가는 FK 다).
  보여 주는 이름은 **닉네임**(`nickname`) 하나다 — 바꿀 수 있는 값이라 그것으로 사람을 가리키지 않는다.
- **경로 변수의 id 는 숫자다**(`/blocks/{userId}` · `/friends/{userId}` · `/posts/{postId}` · `/friend-requests/{requestId}`) — 숫자가 아니면 **400 `VALIDATION_FAILED`** 다(`details` 에 그 이름 한 줄).
- **본문의 id 칸은 문자열로 받아 앱이 판다**(`common/web/Ids` — 이 방식은 Claude 가 정했다. `Long` 으로 받으면 숫자가 아닌 값이 "본문을 읽을 수 없다"로 떨어져 어느 필드가 틀렸는지 말해 줄 수 없다. 클라이언트가 JSON 숫자로 보내도 받는다).
  **`userId` · `targetUserId` 가 숫자가 아니면 없는 사용자와 글자까지 같은 404 `USER_NOT_FOUND`** 다 — 400 으로 갈라 주면 "있을 수 있는 번호"와 아닌 것이 새어 나간다. `contextId` 는 사람을 가리키는 값이 아니라 400 `VALIDATION_FAILED` 다.
- **에러 본문은 `matching` 과 같다** — `{"code": "…", "message": "…", "details": ["…"]}`. **`details` 는 문자열의 배열이다**(`matching` 의 `ErrorResponse` 가 `List<String>` 이다). 없으면 `[]`. **방의 요청도 같은 본문 · 같은 코드 한 벌이다**(2026-09-25 2단계 — 아래 "방" 의 "공통 에러").
- **인증** — access 토큰은 쿠키 **`qm_access`** 로 주고받는다(`CLAUDE.md` §5.1). 쿠키가 없거나 검증에 실패하면 **401 `UNAUTHENTICATED`**.
  인증이 필요 없는 요청은 `/api/v1/auth/**`(**재발급** · 로그아웃 · 소셜 로그인 — 직접 가입 · 로그인은 2026-09-26 에 없어졌다, P-24) · `/health/**` · `/info` 뿐이다. 로그인하지 않은 채 모르는 경로를 부르면 404 가 아니라 401 이다.
- **`Origin` 검사** — POST/PUT/PATCH/DELETE 에 `Origin` 헤더가 있고 허용 목록(`ALLOWED_ORIGINS`, 쉼표로 구분. 기본값 `http://localhost:5173,http://localhost:3000`)에 없으면 **403 `ORIGIN_NOT_ALLOWED`**.
  `Origin` 이 없는 요청(curl · 서버 사이)은 통과한다 — 브라우저는 교차 출처 POST 에 `Origin` 을 반드시 단다. **상태를 바꾸는 GET 을 만들지 않는다.**
- 공통 에러 — 400 `VALIDATION_FAILED`(`details` 에 `"필드: 사유"` 꼴로 필드마다 한 줄) · 401 `UNAUTHENTICATED` · 403 `ORIGIN_NOT_ALLOWED` · 404 `NOT_FOUND` · 500 `INTERNAL_ERROR`.

### access 토큰 (JWT · RS256)

| 항목 | 값 |
|---|---|
| 헤더 | `alg: RS256`, `kid`: 환경변수 `JWT_KEY_ID`(기본값 `dev-1`) |
| 클레임 | `iss` = `queuemate-platform` · **`sub` = 사용자 번호를 십진 문자열로 찍은 것**(`"42"` — 로그인 아이디가 아니다. 2026-09-22 소유자 결정) · `iat` · `exp` · `jti`(UUID) · **`token_use` = `access`** |
| 수명 | 환경변수 `ACCESS_TOKEN_TTL`(기본값 **`PT15M`** — 2026-09-23 소유자 결정으로 `PT24H`(`TEMP-NO-REFRESH`)에서 줄었다. 이어 주는 것은 아래 "refresh 토큰" 이다) |
| 키 | RSA 2048. 개인 키 `JWT_PRIVATE_KEY`(PKCS#8 PEM) · 공개 키 `JWT_PUBLIC_KEY`(X.509 PEM). **둘 다 비어 있으면 개발용 키를 `backend/.dev-keys/` 에 만들어 다시 쓴다**(git 에 올리지 않는다. 경고 로그를 남긴다) — 옆 서비스는 그 폴더의 `public.pem` 을 읽어 검증한다 |
| 쿠키 | `qm_access` · `HttpOnly` · `SameSite=Lax` · `Path=/` · `Domain` 없음 · `Max-Age` = 토큰 수명 · `Secure` 는 `COOKIE_SECURE`(기본값 `false`, 운영은 `true`) |

- **`token_use` 로 쓰임새를 가른다 — 값은 `access`(access 토큰)와 `social_signup`(소셜 가입 대기 토큰 — 아래 "소셜 로그인") 둘이다.** 검증하는 쪽은 서명 · `iss` · `exp` 에 더해 **`token_use` 가 기대한 값인지** 반드시 본다 — 같은 키로 서명하기 때문에 이것을 안 보면 한쪽을 다른 쪽으로 쓸 수 있다.
  **입장권(`room_ticket`)도 같은 키였는데 2026-09-25 2단계로 없어졌다**(P-22 — 입장이 같은 앱 안에서 글을 검사한다). **`token_use` 클레임 자체는 남는다** — 옆 서비스(`matching` · `notification`)와의 약속이다.
  JOSE 헤더의 `typ` 을 쓰지 않은 이유 — Spring Security 의 기본 디코더가 `typ` 이 `JWT` 가 아니면 거절해서 검증하는 옆 서비스가 전부 설정을 바꿔야 한다.
- **검증하는 쪽은 `sub` 가 사용자 번호(숫자 문자열)인지도 본다** — `^[0-9]{1,19}$`(원본 상수는 `common/security/TokenClaims.SUBJECT_PATTERN`. 이 앱은 `JwtConfig#jwtDecoder` 에서 그렇게 한다).
  아니면 컨트롤러에 닿기 전에 401 `UNAUTHENTICATED` 다 — 토큰이 이상한 것이지 서버가 고장 난 것이 아니다. 옆 서비스가 붙일 검증도 같은 모양이다(이 검사 자체는 Claude 가 정했다).

### refresh 토큰 (불투명 UUID · Redis — 2026-09-23 소유자 결정 · P-15)

access 가 짧아진 만큼(15분) 그것을 이어 주는 것이 refresh 다. **access 는 denylist 를 두지 않으므로**(`CLAUDE.md` §5.1 (라)) **서버가 무효화할 수 있는 것은 refresh 쪽 하나다** — 계정이 털렸을 때 끊을 수 있는 창이 24시간에서 15분으로 줄어든다.

| 항목 | 값 |
|---|---|
| 형식 | **JWT 가 아니다** — 불투명한 **UUID**(`UUID.randomUUID()` · `SecureRandom`). 값에 아무 뜻이 없다(사용자 정보를 담지 않는다) |
| 저장 | Redis **`qm:auth:refresh:{uuid}`** → 값은 **사용자 번호**. 수명이 곧 토큰의 수명이다 — 앱은 stateless 다. `qm:auth:` 는 이 앱의 접두사다(`matching` 의 `qm:user:*` · 방의 `qm:room:*` 와 겹치지 않는다) |
| 수명 | 환경변수 **`REFRESH_TOKEN_TTL`**(기본값 **`P7D`** — 7일) |
| 쿠키 | **`qm_refresh`** · `HttpOnly` · `SameSite=Lax` · `Domain` 없음 · `Max-Age` = 토큰 수명 · `Secure` 는 `COOKIE_SECURE`. **`Path` 는 `/api/v1/auth/refresh` 하나다** — access 쿠키(`Path=/`)와 다른 것은 `Path` 와 `Max-Age` 뿐이고, 그래서 이 값은 다른 요청에 실려 가지 않는다 |
| 어디서 나오나 | 소셜 로그인의 콜백(이미 연결된 사람) · 소셜 가입 · 재발급. (비밀번호 로그인 · 직접 가입은 2026-09-26 에 없어졌다 — P-24) |
| 기기 수 | 제한하지 않는다 — 토큰마다 키 하나다. 한 사용자의 refresh 를 전부 찾는 기능은 **없다**(`KEYS`/`SCAN` 을 쓰지 않는다) |

- **`POST /api/v1/auth/refresh`** — 본문이 없고 **`qm_refresh` 쿠키로만** 받는다. **인증이 필요 없다**(`/api/v1/auth/**` 아래다).
  성공은 **200** + 소셜 가입과 **같은 본문**(`{userId, nickname}` — 2026-09-26 에 `loginId` 가 빠졌다) + `Set-Cookie` **둘**(새 access · 새 refresh).
- **rotation 은 필수다** — 쓴 값은 즉시 버리고 새것을 준다. 읽기와 지우기는 **`GETDEL` 한 번**으로 한다(`조회 → 판단 → 삭제` 가 아니다 — 그 틈에 들어온 두 요청이 둘 다 통과해 한 값으로 세션이 둘 생긴다).
  **옛 값을 다시 쓰면 그냥 401 이다** — 탈취 감지(토큰 계보 추적)는 넣지 않는다(`CLAUDE.md` §5.1 (마)).
- **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN` 이다** — 쿠키가 없든 · UUID 꼴이 아니든 · Redis 에 없든 · 이미 쓴 값이든 · 그 사용자가 사라졌든 · Redis 를 못 읽었든 **본문이 글자까지 같다.**
  어느 쪽인지 알려 주면 그 값이 살아 있는지가 새어 나간다. **실패할 때도 refresh 쿠키를 지운다**(`Max-Age=0`) — 못 쓰는 값을 브라우저가 계속 들고 있게 두지 않는다. **access 쿠키는 건드리지 않는다**(아직 살아 있을 수 있다).
- (로그인 실패 제한(429)은 이 요청에 걸지 않았다 — **그 제한 자체가 2026-09-26 에 없어졌다**, P-24.)
- **로그아웃(`POST /auth/logout`)은 둘을 지운다** — ① Redis 의 `qm:auth:refresh:{uuid}` ② 쿠키 둘(`qm_access` · `qm_refresh`, 각각 `Max-Age=0`). **쿠키가 없어도 · Redis 가 죽어 있어도 204** 다(그때 그 refresh 는 수명이 다할 때까지 살아 있다).
  access 는 서버에 지울 것이 없다 — **남는 최대 15분은 감수한다**(`CLAUDE.md` §5.1 (라)).
- **Redis 가 죽었을 때** — 소셜 로그인 · 소셜 가입은 **그대로 성공한다**(access 만 나가고 refresh 쿠키가 없다. 로그인이 Redis 에 묶이지 않게 한다 — 없어진 로그인 실패 제한이 Redis 장애에 통과시키던 것과 같은 원칙이다). **재발급은 401 이다**(fail-closed — 확인하지 못한 값을 통과시키면 폐기된 토큰도 통과한다). 로그아웃은 204.
  **어느 경우에도 예외를 밖으로 내보내지 않는다 — 로그만 남긴다.** **토큰 값은 어느 로그에도 찍지 않는다**(사용자 번호까지만).

## gameconfig 를 읽는 것 — `mode` · `tier` 의 값 검증 (2026-09-24 **소유자 결정** · P-16)

**이 앱이 `qm:gameconfig:*` 를 읽어 `mode`(모집 글)와 `tier`(게임 계정)가 있는 값인지 본다.** 값의 원본은 **`matching/seed/gameconfig.redis`** 이고 **이 앱은 읽기만 한다.**
(2026-09-27 부터 요청으로 받는 `tier` 는 VALORANT · PUBG 뿐이다 — LoL 의 `tier` 는 Riot 에서 채우고 그 이름을 이 사다리에서 옮긴다. P-26 · 아래 "전적을 긁는 것".)

| 무엇 | 키 | 자료형 | 이 앱이 보는 법 |
|---|---|---|---|
| 그 게임에 그 모드가 있나 | `qm:gameconfig:{GAME}:{MODE}`(모드별 설정 HASH) | HASH | **`EXISTS` 하나.** 내용(`targetPartySize` · `tierRule` 등)은 읽지 않는다 — 이 앱은 그 뜻을 모른다 |
| 그 게임에 그 티어가 있나 | `qm:gameconfig:{GAME}:tier`(티어 사다리) | ZSET | **`ZSCORE`** — `null` 이면 없는 티어다. `score`(몇 단계 차이인가)는 매칭의 것이라 읽지 않는다 |

- **모드 목록 SET 은 없다 — 원본 seed 가 일부러 없앴다**(목록을 따로 두면 모드를 하나 고칠 때 두 곳이 어긋난다). 그래서 모드가 있는지는 **HASH 의 `EXISTS` 가 답한다.**
  **`qm:gameconfig:{GAME}:tier-range:{MODE}`(티어별 허용 범위)는 읽지 않는다** — 매칭의 판정 규칙이라 이 앱과 무관하다.
- **Redis 를 못 읽으면 통과시킨다(fail-open — 소유자 결정).** 검증만 건너뛰고 글 쓰기 · 게임 계정 연결은 성공한다. 목록 조회가 이미 "Redis 를 못 읽으면 방 정보를 비운 채 글만 내려 준다"는 fail-open 이라 결을 맞춘 것이다.
  **대가 — Redis 가 죽은 동안에는 이상한 모드가 들어올 수 있다.** WARN 한 줄을 남긴다. **fail-open 은 `common/gameconfig/GameConfigReader` 한 곳에만 있다**(Claude 가 정한 자리 — 모드는 `party`, 티어는 `account` 가 쓰므로 `common` 이다).
- **gameconfig 가 아예 안 심긴 Redis 에서도 통과시킨다**(Claude 가 정한 세부) — 검증할 원본이 없는 것과 값이 틀린 것은 다르다. 가르는 열쇠는 **티어 사다리 키가 있는가**다(세 게임 모두 사다리가 있고, 모드에는 목록 키가 없어 "하나도 없다"를 물을 데가 없다).
- **왜 MSA 위반이 아닌가.** gameconfig 는 `matching` 이 **쓰는 상태가 아니다** — 원본이 seed 파일이고 그 머리가 "앱은 부팅 시 설정을 밀어넣지 않고 Redis 에서 읽기만 한다"고 적었다. **쓰는 앱이 없고 `matching` 도 읽는 쪽이다.**
  운영자가 배포 때 심는 공유 설정이라(Parameter Store · ConfigMap 이 있을 자리다) 여러 서비스가 읽어도 된다. 가르는 기준은 **"바뀌는 계기가 사용자의 행동인가, 운영자의 배포인가"** 다 — 방 키(사용자의 행동으로 실시간으로 바뀌는 상태 — 이제 이 앱이 쓴다, `CLAUDE.md` §3.3)와는 성질이 다르다.
  **이것은 `CLAUDE.md` §2 · §11 의 "매칭 Redis 키(… `qm:gameconfig:*` …) 접근 — 예외가 없다"와 docs/11 #15 를 개정한다** — docs/11 **D-29** 로 남겼다(2026-09-26).
- **이 앱은 seed 를 심지 않는다.** 심게 만들면 모드를 하나 추가할 때마다 이 앱을 재배포해야 한다 — 설정을 데이터로 뺀 뜻이 사라진다(seed 머리가 그 이유를 적었다).
- 거절은 **400 `VALIDATION_FAILED`** 이고 `details` 에 한 줄이다 — `"mode: {GAME} 에 없는 모드입니다"` · `"tier: {GAME} 의 티어가 아닙니다"`(글귀는 Claude 가 정했다).
- **`PATCH` 에서 `mode` 의 검증은 방장 · 상태 검사보다 먼저 일어난다**(글의 게임을 읽어야 검증할 수 있어서다 — Claude 가 정한 세부) — 남의 글이나 만료된 글에 없는 모드를 주면 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING` 이 아니라 **400** 이다. 없는 글은 그대로 404 다.
- **`mode` · `tier` 의 값 목록을 이 앱에 상수로 베껴 두지 않는다** — seed 와 조용히 어긋난다.

## 계정 — `auth` · `users`

**가입 · 로그인은 소셜로만 한다**(2026-09-26 **소유자 결정** — P-24. 아래 "소셜 로그인"). **`POST /api/v1/auth/signup` · `POST /api/v1/auth/login` 이 없어졌다** —
비밀번호(`{bcrypt}`) · `credentials` 테이블 · 로그인 실패 제한 · `loginId` · 409 `LOGIN_ID_TAKEN` · 401 `INVALID_CREDENTIALS` 도 같이 없어졌다. **왜** — 비밀번호 관리 · 이메일 인증(비밀번호를 되찾는 길) 같은 부담을 지지 않는다.

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/auth/refresh` | — (쿠키 `qm_refresh`) | 200 `{userId, nickname}` + `Set-Cookie` **둘**(새 `qm_access` · 새 `qm_refresh`) | 401 `INVALID_REFRESH_TOKEN`(이유를 가르지 않는다. 그때도 refresh 쿠키를 지운다) |
| `POST /api/v1/auth/logout` | — | 204 + **쿠키 둘 제거**(`Max-Age=0`) + Redis 의 refresh 폐기. 쿠키가 없어도 · Redis 가 죽어 있어도 204 | — |
| `GET /api/v1/users/me` | — | 200 `{userId, nickname, createdAt, socialProviders: ["KAKAO"], gameAccounts: [게임 프로필…]}`(2026-09-26 에 `loginId` · `hasPassword` 가 빠졌다) | 401 |
| `PATCH /api/v1/users/me` | `{nickname}` | 200 (`GET` 과 같은 모양) | 409 `NICKNAME_TAKEN` |
| `PUT /api/v1/users/me/game-accounts/{game}` (`{game}` 은 **`LOL` · `VALORANT` · `PUBG` — 대문자 enum.** 모르는 이름 · 소문자는 형 변환에서 400 `VALIDATION_FAILED`, `"game: 올바른 값이 아닙니다"` — 2026-09-27. 그 전에는 서비스가 문자열을 팠고 글귀가 달랐다. `refresh` · `DELETE` 도 같다) | **게임마다 다르다**(2026-09-27 **소유자 결정** · P-26) — **LOL `{gameNickname}` 하나**(`tier` · `mainPosition` · `server` 를 보내면 400) · VALORANT `{gameNickname, tier, mainPosition}` · PUBG `{gameNickname, tier, server}` | 200 **게임 프로필**(아래 "게임 프로필") (없으면 만들고 있으면 바꾼다). **LOL 은 저장하기 전에 Riot 을 긁어(동기 · 상한 30초) 응답에 `tier` · `mainPosition` · `stats` 가 바로 들어 있다** | 400 `VALIDATION_FAILED` · **LOL 만** — 404 `RIOT_ID_NOT_FOUND`(이름#태그가 Riot 에 없다 — **저장하지 않는다**) · 503 `GAME_STATS_UNAVAILABLE`(Riot 이 죽었다 · 시간 초과 · 키 없음 — **저장하지 않는다**) |
| `POST /api/v1/users/me/game-accounts/{game}/refresh` | — | 200 **게임 프로필** — `PUT` 과 **같은 모양이고 방금 긁은 `stats` 가 들어 있다**(2026-09-24 소유자 결정. 아래 "전적을 긁는 것") | 429 `TOO_MANY_STATS_REFRESHES` + `Retry-After` · 404 `GAME_ACCOUNT_NOT_FOUND` · 409 `GAME_STATS_NOT_SUPPORTED` · 503 `GAME_STATS_UNAVAILABLE` |
| `DELETE /api/v1/users/me/game-accounts/{game}` | — | 204 (없어도 204) | — |

- **`userId`** — **사용자 번호다**(`users.id` · bigint identity). 요청 본문에 넣는 값이 아니다 — **DB 가 매기고 응답 · 토큰 · 채널 · URL 이 그것을 쓴다**(2026-09-22 소유자 결정).
- ~~`loginId`~~ · ~~`password`~~ — **2026-09-26 에 없어졌다**(P-24). 로그인할 일이 없어 쓰는 데가 없다 — 식별자는 `userId` 하나, 보여 주는 이름은 `nickname` 하나다.
  `users.login_id` 와 그 UNIQUE · 형식 CHECK, 비밀번호 해시를 담던 `credentials` 테이블이 마이그레이션에서 빠졌다(`V1__schema.sql` 을 그 자리에서 고쳤다 — 운영 DB 가 없다).
  (옛 모양 — `loginId` 는 `^[a-z0-9_]{4,20}$` 이고 중복은 409 `LOGIN_ID_TAKEN`, `password` 는 8~72자 · 72바이트 이하 · `{bcrypt}` 접두사였다.)
- **`nickname`** — 2~16자, 앞뒤 공백 없음. **유일하다**(방 안 사람 카드에서 서로를 구분하는 이름이다). **대소문자를 구별한다** — `Faker` 와 `faker` 는 다른 닉네임이다(2026-09-21 소유자 확정).
- ~~로그인 실패 제한~~(2026-09-21 — 계정 단위로 15분 안에 5번 틀리면 잠그고 두 배씩 · 429 `TOO_MANY_LOGIN_ATTEMPTS` · Redis `qm:auth:login-fail:{loginId}` · `qm:auth:login-lock:{loginId}` · 설정 `platform.auth.login-throttle.*` — P-10) —
  **2026-09-26 에 통째로 없어졌다**(P-24 — 틀릴 비밀번호가 없다). IP 단위의 제한을 앞단(CloudFront/WAF)의 일로 둔 것은 그대로다.
- **중복 가입은 DB 가 막는다** — `조회 → 판단 → 삽입`이 아니라 INSERT 의 제약 위반을 409 로 옮긴다(`CLAUDE.md` §5). 2026-09-26 부터 걸리는 제약은 닉네임의 UNIQUE 와 `social_identities` 의 PK 다(아래 "소셜 로그인").
- **`game`** — `LOL` · `VALORANT` · `PUBG`. 게임마다 계정 하나(`UNIQUE (user_id, game)`). **`tier`** 는 **VALORANT · PUBG 에서** 자기신고 문자열(`^[A-Z0-9_]{1,20}$`, 없어도 된다 — 안 적을 수 있다). **값이 있으면 그 게임의 티어 사다리에 있는 이름이어야 한다**(2026-09-24 소유자 결정 — 위 "gameconfig 를 읽는 것". 없는 이름 · 다른 게임의 티어는 400 `VALIDATION_FAILED`).
  형식 `@Pattern` 은 사다리 검사보다 넓지만 **남겨 두었다** — Redis 를 못 읽어 검증을 건너뛸 때(fail-open) DB 칸(`varchar(20)`)에 들어갈 수 없는 값을 막는 것이 이것뿐이다(Claude 가 정한 세부).
  **`mainPosition`** — LOL 은 `TOP` `JUNGLE` `MID` `ADC` `SUPPORT`, VALORANT 는 `DUELIST` `INITIATOR` `CONTROLLER` `SENTINEL`(이름은 `matching` 의 `LolPosition` · `ValorantRole` 과 같다), PUBG 는 없다(`null` 만). **요청으로 받는 것은 VALORANT 뿐이다.**
- **LOL 은 `tier` · `mainPosition` 을 요청으로 받지 않는다 — Riot 에서 채운다**(2026-09-27 **소유자 결정** · P-26. 그 전에는 세 게임 모두 자기신고였다).
  `tier` 는 `league-v4` 의 솔로랭크 줄을 gameconfig 사다리의 이름으로 옮긴 것이고 **언랭이면 `null`**, `mainPosition` 은 최근 경기에서 가장 많이 간 포지션이다(옮기는 표 · 동률 처리는 코드 참조). 둘 다 아래 "전적을 긁는 것" 의 `PUT` · 전적 갱신이 같이 갱신한다.
  **왜** — Riot 에서 티어 · 포지션을 이미 받아 오면서 저장하지 않았다(계정(2026-09-21)이 Riot 연동(2026-09-23)보다 먼저 만들어져 요청 모양을 안 고쳤다). "포지션의 출처는 프로필의 주 포지션"(D-20)은 그대로다 — LoL 의 그 값이 자기신고에서 Riot 으로 바뀌었을 뿐이다.

### 게임 프로필 — 게임 계정 하나를 밖에 보여 주는 모양 (`users/me` · 목록의 카드가 같이 쓴다)

```json
{
  "game": "LOL", "gameNickname": "달콤한 인생#KR7", "verified": false,
  "tier": "EMERALD_4", "mainPosition": "MID", "server": null,
  "stats": null
}
```

- **`server`** — PUBG 만(`STEAM` · `KAKAO`). 다른 게임은 `null` 만 받는다. **`verified`** · **`stats`** 는 읽기 전용이다 — 요청 본문으로 바꿀 수 없다.
- **자기신고 칸이 게임마다 다르다**(2026-09-27 **소유자 결정** · P-26) — **LOL: 이름#태그(`gameNickname`)만**(`tier` · `mainPosition` 은 Riot 에서 채운 **읽기 전용**이다) · **VALORANT: `gameNickname` + `tier` · `mainPosition`** · **PUBG: `gameNickname` + `tier` · `server`**. VALORANT · PUBG 는 게임사 API 가 없어 지금대로 자기신고다. **응답의 모양은 세 게임이 같다.**
- **`stats` 는 게임사 API 에서 가져온 전적의 스냅숏이다**(`game_account_stats`). **LoL 은 채워진다**(2026-09-23 — 아래 "전적을 긁는 것". **갱신되는 때는 둘이다** — 게임 계정을 저장할 때와 **사용자가 전적 갱신을 누를 때**(`POST …/game-accounts/{game}/refresh`) — 2026-09-24. 언제 긁은 것인지는 `syncedAt` 이다).
  **VALORANT · PUBG 는 아직 늘 `null` 이고 화면은 "정보 없음"으로 그린다.** 목록을 그릴 때 게임사 API 를 부르지 않는다 — 이 테이블만 읽는다. 채워지면 이 모양이다:

```json
{
  "games": 364, "wins": 180, "losses": 184, "winRate": 49, "winStreak": 3,
  "avgKills": 10.6, "avgDeaths": 5.7, "avgAssists": 5.8, "kda": 2.88,
  "detail": {}, "syncedAt": "…"
}
```

**세 게임이 이 테이블 하나를 같이 쓴다 — 진짜 공통인 것만 컬럼이고 나머지는 비는 칸이다**(2026-09-22 소유자 결정). PUBG 는 100명 중 순위 싸움이라 "승"이 치킨이고, 어시스트 · 연승을 보여 주지 않는다 —
`wins` · `losses` 를 `NOT NULL` 로 두면 **데이터가 거짓말을 하게 된다.** **검토하고 버린 것** — ① 게임마다 테이블 하나(모양은 DB 가 강제하지만 프로필 한 번 읽는 데 테이블 셋을 봐야 한다. 목록이 핫 패스다)
② 전부 jsonb(DB 가 검증도 못 하고 나중에 SQL 로 정렬도 못 한다).

| 칸 | 값 | 언제 비나 |
|---|---|---|
| `games` | 정수 | **세 게임 모두에 있다 — 늘 값이 있다**(DB 에서도 `NOT NULL`) |
| `wins` · `losses` | 정수 또는 `null` | PUBG 는 둘 다 `null`. **둘은 같이 있거나 같이 없다**(DB 의 CHECK) |
| `winRate` | 정수 퍼센트 또는 `null` | **`wins` · `losses` 가 둘 다 있고 그 합이 0 보다 클 때만** 값이 있다. 분모는 `games` 가 아니라 `wins + losses` 다 |
| `winStreak` | 정수 또는 `null` | PUBG 는 `null`(연승 개념이 약하다). **값을 안 주면 0 이 아니라 `null` 이다** |
| `avgAssists` | 소수 또는 `null` | PUBG 는 `null`(K/D 만 보여 준다) |
| `kda` | 소수 또는 `null` | `(킬 + 어시스트) / 데스`. 평균이 하나라도 없거나 데스가 0 이면 `null` — **그래서 PUBG 는 늘 `null`** 이다 |

- **비는 칸은 JSON 에서 빠지지 않고 `null` 로 나간다**(칸 자체는 있다) — 화면이 "정보 없음"으로 그린다. 전적 줄 자체가 없으면 `stats` 가 통째로 `null` 이다.
- 칸 순서 — `games, wins, losses, winRate, winStreak, avgKills, avgDeaths, avgAssists, kda, detail, syncedAt`.
- DB 의 제약 — `game_account_stats_counts_check`(`games >= 0`, 나머지 셋도 있으면 `>= 0`) · `game_account_stats_wins_losses_together_check`(`(wins IS NULL) = (losses IS NULL)`) · `game_account_stats_source_check` · PK · FK.

- `winRate` · `kda` 는 이 앱이 계산해 내려 준다(위 표). **`detail` 은 게임마다 다르다** —
  LOL `{"mostChampions": [{"championId", "games", "winRate"}] (3개까지)}` ·
  VALORANT `{"mostAgents": [{"agentId", "games", "winRate"}] (3개까지), "mainWeapon", "mainWeaponKills", "headshotRate"}` ·
  PUBG `{"seasonMode", "avgDamage", "kd", "top1Rate"}`.
- **OP.GG 의 "MVP · Ace" 배지와 평점은 넣지 않았다** — OP.GG 가 스스로 계산한 값이라 Riot API 에 없다(**소유자가 2026-09-23 에 다시 확인했다** — 평점은 넣지 않는다).
- **미정(`CLAUDE.md` §7 "게임 계정 연동")** — VALORANT · PUBG 의 전적(VALORANT 의 전적 API 는 Riot 의 **별도 승인**이 필요하다), Riot(RSO) 인증으로 `verified` 를 켜는 법(**지금은 자기신고를 믿는다** — 2026-09-25 소유자 결정).
  **LoL 의 "가져오는 방법" 은 정해졌다 — 바로 아래.** **"주기" 는 없다** — 게임 계정을 저장할 때와 **사용자가 전적 갱신을 누를 때만** 긁고(2026-09-24), 스스로 주기적으로 갱신할지는 미정이다.

### 전적을 긁는 것 — Riot API · LoL 만 (2026-09-23 소유자 결정 · P-13. **2026-09-24 에 시점이 하나로 줄었다가 "전적 갱신" 이 붙어 둘이 됐다 — P-17. 2026-09-27 에 `PUT` 쪽이 동기가 되고 `tier` · `mainPosition` 도 채운다 — P-26**)

**긁는 시점은 둘이다.** ① LoL 게임 계정을 연결 · 수정할 때(**저장하기 전에 동기로** — 2026-09-27 **소유자 결정** · P-26) ② **사용자가 전적 갱신을 누를 때**(2026-09-24 소유자 결정 — 동기).
**둘 다 동기이고 같은 길이다**(상한 30초). **긁어 오는 것은 `stats` · `tier` · `mainPosition` 셋이다**(2026-09-27 — 그 전에는 `stats` 하나였다).
(옛 모양 — ① 은 **커밋된 뒤에 비동기로** 돌았다. Riot 을 20여 회 부르는 데 수 초가 걸려 응답을 붙잡지 않으려던 것이다. 응답의 `stats` 는 예전 값이었고 실패해도 게임 계정 저장은 성공이었다. **2026-09-27 에 없어졌다** — LoL 은 동기가 됐고 VALORANT · PUBG 는 긁을 것이 없다.)

| 시점 | 무엇을 보나 | 기다리나 |
|---|---|---|
| `PUT /api/v1/users/me/game-accounts/LOL` — **저장하기 전** | 요청의 이름#태그 — **쿨타임 없이 무조건 긁는다**(닉네임이 바뀌었을 수 있어 옛 전적을 그대로 두면 안 된다) | **기다린다**(2026-09-27 소유자 결정) — 다 긁은 뒤 저장하고 **200 + `tier` · `mainPosition` · `stats` 가 든 게임 프로필**이다. 상한 **30초**. Riot 에 없는 이름#태그는 404 `RIOT_ID_NOT_FOUND`, 가져오지 못하면 503 `GAME_STATS_UNAVAILABLE` — **둘 다 저장하지 않는다** |
| **`POST /api/v1/users/me/game-accounts/{game}/refresh`** | 그 게임 계정 — **쿨타임(2분) 안이 아니면 신선도를 보지 않고 긁는다**("최근에 긁었어도 사용자가 원하면 긁는다") | **기다린다**(소유자 결정) — 다 긁은 뒤 **200 + 갱신된 게임 프로필**이다(2026-09-27 부터 `tier` · `mainPosition` 도 갱신된다). 상한은 **30초** |

- **`POST /api/v1/posts` 는 긁지 않는다**(2026-09-24 소유자 결정 — 2026-09-23 에 정한 시점 하나를 되물렸다). **(그때) 긁는 것이 비동기라 방금 쓴 글의 응답에 반영되지 않는데** 대가가 Riot 호출 21번(puuid · 소환사 · 리그 · 경기 id · 경기 20)이고, 개발용 키의 한도가 2분당 100회다 — 그 시점이 사 주는 것은 "몇 초 뒤에 남이 목록을 받을 때 조금 더 최신" 하나뿐이라 수지가 맞지 않는다.
  **`platform.riot.freshness`(30분)도 같이 없앴다** — 신선도를 보는 곳이 이 시점 하나였다. **되살리지 않는다** — 전적 갱신은 "최근에 긁었어도 원하면 긁는다"가 요점이고, 남용은 신선도가 아니라 **쿨타임**이 막는다.
- **감수하는 것 — 저절로 갱신되지는 않는다.** 오래 전에 연결하고 안 건드린 사람의 `stats` 는 **낡은 채로 남는다**(`syncedAt` 이 언제인지는 응답에 있다). 이제 갱신하는 길은 **전적 갱신을 누르는 것**(또는 게임 계정을 다시 저장하는 것)이다.
  **미정 — 주기적으로 스스로 갱신할지, 그렇다면 주기를 얼마로 둘지는 정해지지 않았다**(`CLAUDE.md` §7 "게임 계정 연동"). 정하기 전에 임의로 만들지 않는다. **전적 갱신은 사용자가 누르는 것이라 그 미정과 별개다.**

#### 전적 갱신 — `POST /api/v1/users/me/game-accounts/{game}/refresh` (2026-09-24 **소유자 결정** · P-17)

> **2026-09-27 — LoL 의 전적 갱신은 `tier` · `mainPosition` 도 같이 갱신한다**(소유자 결정 · P-26). 응답 모양 · 쿨타임 · 상한 · 에러 코드의 갈래는 그대로다. 긁는 사이에 이름#태그가 Riot 에서 사라진 경우의 응답은 코드 참조.

**로그인한 본인의 것만이다** — 경로에 사용자 번호가 없어 남의 게임 계정을 가리킬 길이 없다(`users/me` 아래 — 위 "계정"). `{game}` 의 표기는 다른 게임 계정 경로와 같다(`LOL` · `VALORANT` · `PUBG`).

| 항목 | 값 | 누가 정했나 |
|---|---|---|
| 경로 · 본문 | `POST …/game-accounts/{game}/refresh`. 본문 없음 | **소유자**(경로의 존재) |
| 성공 | **200 + 게임 프로필**(`PUT` 과 **같은 모양** — 프런트가 그대로 갈아 끼운다). 다 긁은 뒤에 **DB 에서 다시 읽어** 내려 준다 | **소유자**(동기 · 같은 응답) |
| 쿨타임 | **2분**(`platform.riot.refresh-cooldown`). 같은 게임 계정이 기준이다 | **소유자** |
| 상한 | **30초**(`platform.riot.refresh-timeout`). 넘으면 실패로 다룬다 | **소유자** |
| 쿨타임을 찍는 때 | **긁기를 시작할 때 — 실패해도 소모된다**(실패만 무제한으로 다시 할 수 있으면 Riot 한도를 그대로 태운다) | **소유자** |
| 429 의 에러 코드 · 쿨타임 키 | **`TOO_MANY_STATS_REFRESHES`**(로그인 실패 제한의 `TOO_MANY_LOGIN_ATTEMPTS` 와 결을 맞췄다 — 그 코드는 2026-09-26 에 없어졌다) · **`qm:riot:refresh:{gameAccountId}`** | Claude |
| 그 밖의 에러 코드 | 404 `GAME_ACCOUNT_NOT_FOUND` · 409 `GAME_STATS_NOT_SUPPORTED` · 503 `GAME_STATS_UNAVAILABLE` | Claude |
| 30초를 재는 방법 | 전용 풀에 던지고 `Future.get(30초)` — 요청 스레드에서 긁으면 자를 수 없다 | Claude |

- **거절의 갈래.**
  - **429 `TOO_MANY_STATS_REFRESHES` + `Retry-After`(초)** — 쿨타임 안에 또 불렀다. **누가 이미 같은 계정을 긁고 있을 때도 같은 429 다**(소유자 결정 — 자물쇠 `qm:riot:sync:{gameAccountId}` 를 그대로 쓴다. 게임 계정을 저장한 직후에 누르면 이쪽이다). 그때 `Retry-After` 는 **방금 찍은 쿨타임 그대로**다 — 자물쇠가 풀리는 시각보다 늦지만 두 429 가 같은 말을 하게 뒀다(Claude 가 정한 세부).
  - **404 `GAME_ACCOUNT_NOT_FOUND`** — 그 게임 계정을 연결하지 않았다(긁는 사이에 연결을 끊은 경우도 같다). **쿨타임을 소모하지 않는다.**
  - **409 `GAME_STATS_NOT_SUPPORTED`** — **그 게임은 긁는 구현이 없다**(VALORANT · PUBG). **200 을 주면 거짓말이다** — 아무것도 갱신되지 않는다. 요청이 잘못된 것이 아니라 서버가 못 하는 것이라 400 이 아니다. **쿨타임을 소모하지 않는다.**
  - **503 `GAME_STATS_UNAVAILABLE`** — 지금 가져올 수 없다. **전적 줄은 건드리지 않는다**(옛 값이 남는다 — `PUT` 쪽 실패와 같은 원칙). **이유를 가르지 않는다** — Riot 이 4xx/5xx 로 거절 · 응답이 없다 · **30초를 넘겼다** · **`RIOT_API_KEY` 가 없다** · **게임 닉네임이 `이름#태그` 가 아니어서 물어볼 수도 없다** · 전용 풀에 자리가 없다가 전부 이것이다. 사용자가 할 수 있는 것은 "잠시 뒤 다시" 또는 "게임 닉네임을 고친다" 둘뿐이라 갈래마다 코드를 두지 않았다(Claude 가 정했다). 키가 없는 것과 구현이 없는 것은 **쿨타임을 소모하지 않는다.**
- **거르는 순서** — ① 그 게임을 긁을 수 있나(409) ② 그 게임 계정이 있나(404) ③ 키가 있나(503) ④ 쿨타임(429) ⑤ 긁는다. **게임사 API 를 부르기 전에, 그리고 쿨타임을 찍기 전에** 거절할 것을 다 거절한다(Claude 가 정한 세부 — ③이 ②보다 뒤인 것은 없는 계정에는 404 가 더 쓸모 있어서다).
- **30초를 넘겼을 때 뒤에서 돌던 갱신은 자르지 않는다** — 잠시 뒤에 끝나면 전적은 갱신되고 다음 조회에서 보인다(자물쇠가 그동안 중복을 막는다). **요청만 끊는다.**
- **Redis 가 죽으면 쿨타임 없이 통과시킨다** — 로그인 실패 제한("Redis 가 죽으면 제한 없이 통과시킨다" — 2026-09-26 에 그 제한은 없어졌다)과 같은 원칙이고, 자물쇠도 Redis 에 묻지 못하면 락 없이 진행한다. 그동안은 같은 계정을 여러 번 긁을 수 있다(저장이 upsert 라 결과는 같다).
- **쿨타임 키는 자물쇠 키와 다른 키다 — 뜻이 다르다.** 자물쇠(`qm:riot:sync:*` · 60초)는 "지금 돌고 있다"이고 쿨타임(`qm:riot:refresh:*` · 2분)은 "최근에 했다"다. 하나로 합치면 갱신이 끝나 자물쇠가 풀리는 순간 다시 누를 수 있게 된다. 접두사 `qm:riot:*` 는 둘 다 이 앱의 것이다.
- **`PUT` 쪽에는 쿨타임이 없다** — 쿨타임 없이 무조건 긁는다. **응답을 기다리지 않던 것은 2026-09-27 에 바뀌었다** — LoL 의 `PUT` 도 이제 동기다(위 표 · P-26).
- ~~**실패해도 게임 계정 저장은 성공이다**~~ — **2026-09-27 에 뒤집혔다**(P-26). LoL 의 `PUT` 은 긁기에 실패하면 **저장하지 않는다** — Riot 에 없는 이름#태그는 404 `RIOT_ID_NOT_FOUND`, 가져오지 못하면(Riot 4xx/5xx · 시간 초과 · 키 없음) 503 `GAME_STATS_UNAVAILABLE` 이다. **이미 저장된 게임 계정과 그 `stats` 줄은 그대로 남는다**(바꾸는 `PUT` 이 실패해도 옛 값이 남는다). 태그 없는 닉네임을 어떻게 거절하는지는 코드 참조.
  (옛 모양 — 키가 없거나 · 태그가 없거나 · Riot 이 거절하거나 · 타임아웃이면 로그만 남기고 조용히 끝냈고 게임 계정 저장은 성공이었다.)
- **429(rate limit)는 재시도하지 않는다** — 개발용 키는 2분당 100회라 되풀이해도 소용이 없다. 그 자리에서 포기하고 다음 갱신 때 다시 긁는다.
- **LoL 만 긁는다.** VALORANT · PUBG 계정을 연결해도 아무것도 하지 않는다(자기신고를 그대로 저장한다) — 게임별 구현은 인터페이스(`account.stats.GameStatsProvider`) 뒤에 있어 게임이 늘면 구현 하나를 더한다.
- **부르는 것** — `account-v1`(Riot ID → `puuid`) · `summoner-v4`(`puuid` → 소환사) · `league-v4`(**`RANKED_SOLO_5x5` 줄**) · `match-v5`(최근 경기 id, 경기 하나마다 한 번). 키는 헤더 **`X-Riot-Token`** 으로 보낸다.
  **`gameNickname` 은 `이름#태그` 여야 한다** — 2026-09-27 부터 LoL 의 `PUT` 에서 `#` 이 없는 닉네임은 저장되지 않는다(그 응답 코드는 코드 참조. 그 전에는 경고 로그만 남기고 긁지 않았다).
- **채우는 값** — `games` 는 실제로 읽은 경기 수(`match-count` 이하), `avgKills` · `avgDeaths` · `avgAssists` 는 그 경기들의 평균(소수 첫째 자리),
  **`wins` · `losses` 는 솔로랭크의 시즌 누적**(읽은 경기의 승패가 아니다. 솔로랭크 줄이 없으면 **둘 다 `null`**), `winStreak` 은 가장 최근 경기부터의 연승(최근 경기가 패면 0, 경기를 하나도 못 읽었으면 `null`), `source` 는 `API`.
  **`tier` · `mainPosition`(게임 계정의 칸 — 2026-09-27 · P-26)** — `tier` 는 `league-v4` 의 솔로랭크 줄을 gameconfig 사다리의 이름으로 옮긴다(솔로랭크 줄이 없으면 `null`), `mainPosition` 은 읽은 경기에서 가장 많이 간 포지션이다(옮기는 표 · 동률 · 경기가 없을 때는 코드 참조).
  `detail` 은 `{"mostChampions": [{"championId", "games", "winRate"}]}` 로 **판 수 많은 순 → 승률 높은 순 → 이름순으로 셋까지**다(`championId` 의 값은 Riot 의 `championName` — `"Samira"`).
- **`external_id` 에 `puuid` 를 적는다**(응답에 싣지 않는다). **`verified` 는 켜지 않는다** — 식별자를 알아낸 것은 본인 확인이 아니다(켜는 길은 아직 없다).
- **같은 게임 계정을 동시에 여러 번 긁지 않는다** — Redis 락 **`qm:riot:sync:{gameAccountId}`**(`SET … NX EX 60`). **못 잡으면 줄 서지 않고 건너뛴다.**
  **Redis 가 죽으면 락 없이 진행한다** — 전적 때문에 기능이 멈추면 안 된다(저장이 upsert 라 두 번 긁혀도 결과는 같다). 접두사 `qm:riot:*` 는 이 앱의 것이다(`qm:auth:*` 와 같은 자리).
- **설정**(`platform.riot.*`) — **`RIOT_API_KEY`**(비어 있으면 **긁는 일 자체를 하지 않는다.** 기동은 정상이고 시작할 때 INFO 한 줄을 남긴다. **키를 로그에 찍지 않는다**) ·
  **`RIOT_REGIONAL_BASE_URL`**(계정 · 경기 — 대륙 주소) · **`RIOT_PLATFORM_BASE_URL`**(소환사 · 리그 — 플랫폼 주소) · `match-count`(기본 20) · `connect-timeout` · `read-timeout`(각 `PT3S`) ·
  **`refresh-cooldown`**(기본 `PT2M`) · **`refresh-timeout`**(기본 `PT30S` — 둘 다 전적 갱신 요청의 것이다. 2026-09-24). **`freshness` 는 없다** — 2026-09-24 에 함께 없앴다.
  **뒤의 넷은 환경변수가 없다** — 값을 바꿀 일이 운영 환경마다 다르지 않아 `application.yaml` 에 값만 적어 두었다(키 · 주소만 환경변수다. 그 파일의 기존 방식을 따랐다).
  **값은 이 문서에 적지 않는다** — 환경변수로 넣는다(운영은 Secrets Manager).

## 소셜 로그인 — 카카오 · 디스코드 (2026-09-21 소유자 지시)

**가입 · 로그인은 이것뿐이다**(2026-09-26 **소유자 결정** — P-24. 그 전에는 직접 가입 · 비밀번호 로그인이 따로 있고 소셜은 곁의 길이었다). 사용자 번호는 가입할 때 DB 가 매긴다(`CLAUDE.md` §3.5).
소셜로 **처음** 들어온 사람은 **닉네임만 정하는 한 단계**를 거치고(2026-09-26 전에는 로그인 아이디와 닉네임이었다), 두 번째부터는 바로 로그인된다.
**비밀번호는 누구에게도 없다**(`credentials` 테이블째 없어졌다 — 전에는 소셜로만 가입한 사람이 `hasPassword: false` 였고 그 칸도 `users/me` 에서 빠졌다).
**그래서 실제 카카오 · 디스코드 키가 이제 필수다** — 제공자가 하나도 설정돼 있지 않으면 아무도 로그인할 수 없다(아래 설정. 지금까지 가짜 제공자로만 테스트했다).

| 요청 | 하는 일 | 결과 |
|---|---|---|
| `GET /api/v1/auth/oauth/{provider}/start` (**`KAKAO` · `DISCORD` — 대문자 enum.** 2026-09-26 소유자 지시로 소문자 이름을 없앴다. 제공자에 등록하는 Redirect URI 도 `…/oauth/KAKAO/callback` 이다) | `state`(무작위)를 쿠키 `qm_oauth_state`(`HttpOnly` · `SameSite=Lax` · `Path=/api/v1/auth/oauth` · 10분)에 넣고 제공자의 동의 화면으로 보낸다 | 302. 그 제공자가 설정돼 있지 않으면(클라이언트 id 가 비었다) 404 `OAUTH_PROVIDER_NOT_CONFIGURED` |
| `GET /api/v1/auth/oauth/{provider}/callback?code=…&state=…` | `state` 가 쿠키와 같은지 본다 → `code` 를 토큰으로 바꾼다 → 제공자 쪽 회원 번호를 얻는다 | **이미 연결된 사람** → `qm_access` 를 주고 `FRONT_BASE_URL` + `/` 로 302. **처음 온 사람** → 쿠키 `qm_social_signup`(아래)을 주고 `FRONT_BASE_URL` + `/signup/social` 로 302. **실패**(`state` 불일치 · 사용자가 거절 · 제공자 오류) → `FRONT_BASE_URL` + `/login?error=OAUTH_FAILED` 로 302 |
| `GET /api/v1/auth/social/pending` | 가입 화면이 미리 채울 값을 준다 | 200 `{provider, suggestedNickname}` · 401 `NO_PENDING_SOCIAL_SIGNUP` |
| `POST /api/v1/auth/social/signup` `{nickname}`(2026-09-26 에 `loginId` 가 빠졌다) | 사용자와 소셜 연결을 **한 트랜잭션으로** 만든다. **곧바로 로그인시킨다**(`qm_access`) — 다음부터는 같은 제공자로 로그인한다. `qm_social_signup` 은 지운다 | 201 `{userId, nickname}` · 409 `NICKNAME_TAKEN` · 409 `SOCIAL_ALREADY_LINKED` · 401 `NO_PENDING_SOCIAL_SIGNUP` · 400 |

- **`qm_social_signup`** — JWT(같은 키 · **`token_use` = `social_signup`** · 10분 · 클레임 `provider` · `provider_user_id` · `suggested_nickname`). `HttpOnly` · `SameSite=Lax` · `Path=/api/v1/auth/social`. 서버가 기억하는 것이 없다 — stateless 그대로다.
- **Spring 의 oauth2-client 를 쓰지 않는다** — 기본값이 인가 요청을 HTTP 세션에 넣는다(`CLAUDE.md` §5 "stateless"). 인가 코드 흐름을 `RestClient` 로 직접 짠다.
- **콜백은 "상태를 바꾸는 GET 을 만들지 않는다"의 유일한 예외다** — OAuth 가 GET 을 강제한다. `state` 검증이 그 자리를 지킨다.
- **제공자에게서 받는 것은 회원 번호와 닉네임뿐이다.** 이메일은 받지 않는다. 카카오 scope `profile_nickname`, 디스코드 scope `identify`.
  제공자 쪽 회원 번호는 `social_identities`(PK `(provider, provider_user_id)` · `user_id` 는 **사용자 번호**)에만 있고 **사용자 번호가 되지 않는다.**
- 설정(환경변수) — `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET`(카카오는 없어도 된다) · `DISCORD_CLIENT_ID` · `DISCORD_CLIENT_SECRET` · `OAUTH_REDIRECT_BASE_URL`(기본값 `http://localhost:8082` — 제공자에 등록하는 Redirect URI 는 이 값 + `/api/v1/auth/oauth/{provider}/callback`) · `FRONT_BASE_URL`(기본값 `http://localhost:5173`).
  제공자의 주소 셋(인가 · 토큰 · 사용자 정보)도 설정으로 받는다(기본값은 실제 주소. 테스트는 가짜 제공자 서버를 가리킨다).
- 모르는 제공자 이름 · 소문자(`kakao`)는 `start` · `callback` 둘 다 **400 `VALIDATION_FAILED`** 다(경로 변수를 enum 으로 받아 스프링의 형 변환이 거절한다 — 게시판 목록의 `game` 과 같다. 2026-09-26 전에는 소문자만 받고 모르는 이름은 404 였고 `callback` 은 302 였다). `callback` 은 **무슨 일이 있어도 302** 다. `pending` 의 `provider` 는 대문자(`KAKAO`)이고 `suggestedNickname` 은 `null` 일 수 있다(16자로 자른 값).
- **`state` 는 서버에서 일회용이 아니다**(stateless 라 쿠키를 지우는 것까지만 한다) — 제공자의 `code` 가 일회용이라 감수한다.
- **하지 않은 것** — 이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기(미정 그대로다 — 그래서 한 사람이 카카오와 디스코드로 따로 오면 사용자가 둘 생긴다). ~~소셜 가입자가 비밀번호를 만드는 것~~은 2026-09-26 에 물음째 없어졌다(비밀번호가 없다 — P-24).

## 차단 — `blocks`

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/blocks` | `{userId}`(차단할 사람의 **사용자 번호**) | 201 `{userId, nickname, createdAt}` | 409 `ALREADY_BLOCKED` · 400 `CANNOT_BLOCK_SELF` · 404 `USER_NOT_FOUND` |
| `DELETE /api/v1/blocks/{userId}` | — | 204 (차단한 적 없어도 204) | — |
| `GET /api/v1/blocks` | — | 200 `{blocks: [{userId, nickname, createdAt}]}` — **내가 차단한 사람만.** 나를 차단한 사람은 보여 주지 않는다 | — |

- 목록은 새로 차단한 사람이 먼저다. `POST` 의 `userId` 가 **숫자가 아니어도 404 `USER_NOT_FOUND`** 다(위 "공통" — 있을 수 없는 사용자다).
- 테이블은 `matching` 이 읽는 모양 그대로다(`CLAUDE.md` §3.5). `UNIQUE (blocker_id, blocked_id)` 가 같은 사람 두 번 차단을 막는다.
- **`blocker_id` · `blocked_id` 가 `varchar(20)` 에서 `bigint` 가 됐다**(2026-09-22 소유자 결정). **`matching` 의 `block/Block.java` 도 두 칸을 `Long` 으로 읽게 바뀌었다**(2026-09-26 — 그 폴더에서 했다. 그 전에는 `String` 이라 이 테이블을 읽다가 런타임에 깨질 참이었다).
  docs/11 D-4("`matching` 이 이미 `String` 으로 다룬다")를 개정하는 것이다 — P-11 · docs/11 D-25.
- **롤 · GRANT 는 없다.** 앱 하나가 DB 계정 하나로 붙고, `matching` 은 별도 롤 없이 `blocks` 를 읽는다. **2026-09-26 에 스키마가 `public` 하나가 됐다**(P-23) — 옛 모양은 `social.blocks` 였고 "스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다"라고 적었는데 그것이 풀렸다. 2026-09-22 의 "스키마별 DB 롤을 두지 않는다"는 스키마가 하나가 되며 물음째 없어졌다. **`matching` 의 `block/Block.java` 에서 `@Table` 의 `schema = "social"` 을 뺐다**(2026-09-26 — 위의 `Long` 과 같이 그 폴더에서 했다. 이제 `public.blocks` 를 읽는다). `matching` 이 읽는 테이블이 `blocks` 하나라는 약속은 그대로다.
- **`blocker_id` · `blocked_id` 는 `users(id)` 로 가는 FK 다**(`ON DELETE CASCADE` — 2026-09-26). 없는 사용자를 차단하면 그 FK 의 위반이 404 `USER_NOT_FOUND` 가 된다(에러 코드는 전과 같다 — 가려내는 자리가 앱의 조회에서 DB 로 옮겼다. 세부는 코드 참조).
- 차단해도 이미 맺은 친구 관계 · 이미 같은 방에 있는 상태는 건드리지 않는다(미정 그대로 — `CLAUDE.md` §7.1).

## 모집 글 · 목록 — `posts`

**`roomId` 는 글의 id 다**(`recruit_posts.id` — **bigint identity.** DB 가 매긴다. 2026-09-22 소유자 결정 전에는 UUID 였다). **글을 쓰면 그 번호의 방이 같은 요청에서 생긴다**(2026-09-25 **소유자 결정 C** · P-22 — 아래 "글 쓰기가 방을 만든다") — 브라우저가 방 만들기를 따로 부르지 않는다.
방 키에는 숫자가 십진 문자열로 들어간다(`qm:room:123:host` — 아래 "방" 의 "Redis 키").
**자동 매칭 파티의 `roomId` 는 정해지지 않았다** — `matching` 은 `partyId` 를 UUID 문자열로 내려 주는데 `parties.id` 는 bigint 다. 그 값을 어디에 둘지는 **6단계(SQS 배선)에 닿을 때 묻는다**(`CLAUDE.md` §7 · §7.2 (나)). 예전에 적었던 "자동 매칭 파티는 `roomId = partyId` 다" 한 줄은 이 미정 위에 서 있다.

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/posts` | `{game, mode, title, description, voice, purpose, conditions, wantedPositions: []}` | 201 글 한 줄(아래) — **방이 같이 생겨 `members` 에 방장이 있고 `memberCount` 는 1 이다** | 409 `ALREADY_RECRUITING`(모집 중인 글은 한 사람에 하나) · **409 `ALREADY_QUEUED`**(자동 매칭 중) · **409 `IN_OTHER_ROOM`**(이미 다른 방에 들어가 있다) · 409 `ROOM_ALREADY_EXISTS`(정상이면 나지 않는다) · **503 `ROOM_STATE_UNAVAILABLE`**(방을 못 만들었다) · 400 |
| `PATCH /api/v1/posts/{postId}` | `{mode, title, description, voice, purpose, conditions, wantedPositions}` (준 것만 바꾼다) | 200 글 한 줄 | 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING` · **409 `ROOM_HAS_OTHER_MEMBERS`**(방에 방장 말고 누가 있다 — 아래) · **503 `ROOM_STATE_UNAVAILABLE`**(방 안을 못 읽었다) · 404 `POST_NOT_FOUND` |
| `DELETE /api/v1/posts/{postId}` | — | 204. **지우지 않고 "만료"로 바꾸고 방도 닫는다**(아래 "방과 글은 같이 산다"). 이미 만료면 그대로 204 | 403 `NOT_POST_HOST` · 409 `POST_CONFIRMED` · 404 |
| `GET /api/v1/posts?game=LOL&limit=20&cursor=123` | — | 200 `{posts: [글 한 줄…], nextCursor: 104 또는 null}`(**`nextCursor` 는 숫자다**) | 400 `VALIDATION_FAILED`(**`game` 이 없다** · `game` 이 세 게임의 이름이 아니다 · `limit` 이 1~100 이 아니다 · `limit` · `cursor` 가 숫자가 아니다) |
| `GET /api/v1/posts/{postId}` | — | 200 글 한 줄 | 404 `POST_NOT_FOUND`(차단 관계로 숨겨진 글도 404 다) |

- **들어가기 · 방장 확정은 이 절에 없다** — 방의 요청이다(아래 "방" 의 "입장" · "방장 확정"). **입장권(`POST /api/v1/posts/{postId}/ticket`)과 확정 기록 요청(`POST /api/v1/posts/{postId}/confirm`)은 2026-09-25 에 없어졌다**(P-22 — 같은 앱이 된 뒤로 서명된 표를 건넬 상대도, 브라우저가 따로 알려 줄 상대도 없다).

**글 한 줄**

```json
{
  "postId": 123, "hostId": 42, "game": "LOL", "mode": "RANKED_SOLO", "title": "…", "description": "…",
  "voice": "REQUIRED | NO_VOICE", "purpose": "RANK_UP | NORMAL | FUN", "conditions": {"perspective": "TPP"},
  "wantedPositions": ["MID", "SUPPORT"],
  "status": "RECRUITING | CONFIRMED | EXPIRED", "createdAt": "…",
  "memberCount": 3, "capacity": 5, "full": false,
  "host": {"userId": 42, "nickname": "…", "host": true, "profile": {게임 프로필 또는 null}},
  "members": [{"userId": 42, "nickname": "…", "host": true, "profile": {게임 프로필 또는 null}}]
}
```

- **한 줄은 게임마다 다르게 그려진다 — 백엔드는 같은 모양에 게임별 내용을 채운다**(2026-09-21 소유자 지시. 본보기는 OP.GG 의 듀오 찾기다).
  **세 게임 모두 가로 한 줄이고, 글을 눌러 펼치지 않는다 — 방 안 전원이 글 아래에 같은 가로 줄로 바로 보인다**(그래서 `members[]` 의 카드마다 게임 프로필 전체가 실린다). LOL 의 줄은 이름#태그 · 인증 · 주 포지션 · 티어 · 찾는 포지션 · 모스트 챔피언 · 승/패 · KDA · 메모 · 등록 시각, VALORANT 는 모스트 요원 · 주 무기 · 헤드샷률 · 음성, PUBG 는 모드 · 티어 · 음성/목적 태그 · 서버/시점 · 메모 · 평균 데미지 · K/D · 치킨률이다.
  그 정보는 **`host.profile`**(글의 게임에 연결한 방장의 게임 프로필 — 위 "게임 프로필")과 글의 `voice` · `purpose` · `conditions` · `wantedPositions` · `description` 에서 나온다.
  **`host` 는 방이 아직 없거나 글이 만료 · 확정된 뒤에도 채워진다**(`members` 는 방 안에 지금 있는 사람이다).
- **`voice` · `purpose` 의 값은 `matching` 의 `VoicePreference` · `PlayPurpose` 와 같은 이름이다.** 둘 다 필수다.
- **`conditions`** — 게임별 조건을 담는 객체. PUBG 는 `{"perspective": "TPP" | "FPP"}`(필수), LOL · VALORANT 는 `{}`. 모르는 키는 400 이다. PUBG 의 서버(스팀 · 카카오)는 글이 아니라 방장의 게임 프로필(`server`)에서 온다.

- `title` 1~60자, `description` 300자까지(없어도 된다), **`mode` 는 필수이고 그 게임의 gameconfig 에 있는 모드여야 한다**(2026-09-24 소유자 결정 — 위 "gameconfig 를 읽는 것". 30자까지 — seed 의 모드 이름이 그보다 짧다. 없는 모드 · 다른 게임의 모드 · 빈 문자열은 400 `VALIDATION_FAILED`).
  **옛 글의 `mode` 는 비어 있을 수 있다**(그때는 없어도 되는 칸이었다) — 컬럼은 `NULL` 을 허용한 채로 두었고 응답의 `mode` 가 `null` 로 나갈 수 있다. **`NOT NULL` 로 조일지 · 옛 글의 빈 `mode` 를 어떻게 할지는 미정이다**(P-16). `wantedPositions` 는 그 게임의 포지션 이름(위 `mainPosition` 과 같은 목록. PUBG 는 빈 배열).
- **카드의 `profile` 은 그 글의 게임에 연결한 게임 계정에서 온다.** 게임 계정이 없으면 `profile` 이 `null` 이다.
- **`filledPositions`(찾는 포지션 가운데 이미 방 안에 채워진 것의 강조)는 없앴다**(2026-09-24 **소유자 결정** · P-18 — **docs/11 D-20 의 ③ 을 개정한다**). 그 값은 `wantedPositions ∩ 방 안 사람들의 주 포지션` 으로 계산했는데, **주 포지션은 "내가 주로 하는 것" 이지 "이 방에서 할 것" 이 아니다** —
  주 포지션이 정글인 사람이 미드를 구하는 방에 미드를 하러 들어갈 수 있는데 그 계산은 그 방의 미드 자리를 **안 찼다고** 표시했다. **틀린 정보를 자신 있게 보여 주는 것**이라 없앴다.
  **카드의 `profile.mainPosition` 은 그대로 보여 준다** — 방 안에 누가 무엇을 주로 하는지는 사실이고, 그것으로 자리가 찼는지를 **판단하는 것만** 그만둔 것이다.
  **다시 둘 것인가는 미정이다** — 소유자가 "일단" 없앴다. **입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다**(`CLAUDE.md` §7.1 — 그러면 입장이 포지션을 받아야 하고 멤버 SET 을 HASH 로 바꿔야 해서 방 키의 모양이 바뀐다).
- **목록의 `game` 은 필수다**(2026-09-25 **소유자 결정** · P-21). **게시판은 게임별로 나뉜 페이지이고 사용자는 늘 한 게임의 게시판을 본다 — "세 게임 전부" 화면이 없다.**
  그 전에는 `game` 이 없으면 세 게임을 전부 내려 주었는데 **아무도 쓰지 않는 갈래였고 쿼리만 둘 더 있게 만들었다**(게임 없이 훑는 쿼리 둘이 그것이다 — 지웠다).
  - **안 보내면 400 `VALIDATION_FAILED`** 이고 `details` 는 `["game: 필요합니다"]` 다. **빈 값(`?game=`)도 안 준 것과 같다** — enum 으로 바꾸다 `null` 이 되면 스프링이 "빠졌다"로 다룬다.
  - **모르는 이름(`?game=LOLL`)은 400 `VALIDATION_FAILED`** 이고 `details` 는 `["game: 올바른 값이 아닙니다"]` 다 — 거절하는 자리가 **형 변환**이라 `limit` · `cursor` 가 숫자가 아닐 때와 **글귀가 같다.**
  - **대소문자를 가린다 — `?game=lol` 은 400 이다**(위와 같은 본문). 계약의 이름은 대문자이고(`LOL` · `VALORANT` · `PUBG`) **소문자를 받아 주는 변환기를 두지 않았다** — 게임 계정 · 글의 본문에서 `Game` 을 읽는 자리도 이미 대소문자를 가린다(`Game#fromName`). 두려면 그것부터가 결정이다.
  - **목록 하나에만 걸린다** — 단건 조회 · 입장 · 방장 확정 · 글 쓰기 · 고치기 · 지우기는 바뀌지 않았다(글 쓰기의 `game` 은 전부터 필수인 **본문 필드**다).
- **목록의 정렬 — `id` 내림차순 하나(= 최신순)다**(2026-09-24 **소유자 결정** · P-14). **`id` 가 `bigint GENERATED ALWAYS AS IDENTITY` 라 넣은 순서대로 커지고, 겹치지 않고, 변하지 않는다** — 그래서 "들어온 순서" 가 곧 `id` 순서이고 tiebreaker 가 필요 없다.
  **글의 상태도 `createdAt` 도 정렬에 쓰지 않는다.** 그날 아침까지는 `(모집 중인가, createdAt desc, id)` 였다 — **왜 뒤집었는지는 아래 "목록의 페이지 나누기" 에 적었다**(정렬 키가 변하면 커서가 중복을 낸다).
  `createdAt` 컬럼과 응답의 `createdAt` 은 **그대로 있다** — 화면의 "몇 분 전" 이 그 값이다. 정렬과 커서에서만 안 쓴다.
  **글을 상태로 가리지 않는다 — 모집 중 · 확정 · 만료가 전부 그 순서로 나오고 끝난 글도 계속 남는다**(2026-09-25 **소유자 결정** · P-20. `status` 로 구분해 보여 준다. 끝난 글은 멤버를 비우고 `host` 는 채운다). 만석인 방은 `full: true` 로 목록에 남는다.
  - **보존 기간을 없앤 것이다**(2026-09-25 **소유자 결정** · P-20 — 그 전에는 **만료 · 확정된 뒤 10분 동안만** 남았다. 설정 `platform.board.closed-retention` 도 같이 없앴다). **왜 둘이다.**
    ① **쿼리가 쓸데없이 무거웠다** — 조건이 `status = RECRUITING` **또는** `confirmedAt > :closedAfter` **또는** `expiredAt > :closedAfter` 라는 **세 컬럼에 걸친 `OR` 셋**이라 `(game, id DESC)` 인덱스(옛 `party/V7__board_order_index.sql` — 지금은 `V1__schema.sql`)를 깨끗하게 타지 못했다.
    조건이 `game` 하나만 남은 지금은 **그 인덱스를 순서대로 훑어 내려가면 끝이다**(커서의 `id < ?` 도 같은 인덱스를 탄다).
    ② **활동을 보여 주지 못했다** — 끝난 글이 10분 만에 사라지면 사용자가 "이 서비스에서 모집이 얼마나 활발한가" 를 볼 수 없다.
  - **만료 · 확정된 글이 목록 위쪽에 섞여 나온다**(제자리다 — 맨 아래로 내려가지 않는다). **감수하는 것이다** — 응답에 `status` 가 있으니 화면이 흐리게 그리거나 "모집 끝" 배지를 붙이면 된다.
    **1쪽은 그래도 대개 모집 중인 글일 것이다** — 글은 **모집 중으로 태어나** 나중에 끝나고 정렬이 `id` 내림차순이라 **새 글일수록 아직 살아 있을 가능성이 높다.** 끝난 글이 1쪽을 채우는 것은 글이 아주 뜸할 때인데,
    **그때는 애초에 들어갈 방이 없는 상태라** 그 글들이 위 ②("활동을 보여 준다") 쪽으로 쓰인다.
  - **행이 영원히 쌓인다** — 앱에는 끝난 글을 지우거나 옮기는 정리 작업이 없다. **오래된 글은 운영에서 소유자가 직접 지운다**(2026-09-25 소유자 결정).
  - **`status` 로 거르는 필터는 두지 않는다**(2026-09-25 소유자 결정 — 필요 없다. 끝난 글을 다 보여 주기로 한 것과 같은 맥락이다). 필터는 `game` 하나다.
- **목록의 페이지 나누기 — 커서 방식**(2026-09-23 **소유자 결정** · P-14). 게시판은 `BOARD_CHANGED` 신호가 올 때마다 목록을 다시 받으므로 전부 내려 주면 그 큰 응답이 몇 초마다 되풀이된다.
  - **`limit`** — 한 페이지에 보여 줄 글의 수. 없으면 **20**, **최대 100**. 1 미만이거나 100 초과면 **400 `VALIDATION_FAILED`**(`details` 에 `limit`). 숫자가 아니면 형 변환에서 같은 400 이다. **상한으로 잘라 주지 않는다** — 조용히 100개를 주면 클라이언트가 "다 받았다"고 읽는다.
  - **`cursor`** — 없으면 맨 위부터. **마지막으로 읽은 글의 번호다**(응답의 `nextCursor` 를 그대로 보낸다. 숫자다 — 감싸지 않는다).
    **숫자가 아니면 400 `VALIDATION_FAILED`** 인데, 거절하는 자리가 **형 변환 한 곳이다** — 컨트롤러에 닿기 전에 떨어져 `limit` 이 숫자가 아닐 때와 **글자까지 같은 본문**이 나간다:
    `{"code": "VALIDATION_FAILED", "message": "요청 형식이 올바르지 않습니다", "details": ["cursor: 올바른 값이 아닙니다"]}`. **커서를 검사하는 코드는 따로 없다.**
    **빈 값(`&cursor=`)은 안 준 것과 같다.** **0 · 음수 · 맨 끝을 넘은 번호는 에러가 아니라 빈 페이지다**(`nextCursor` 는 `null`) — `id < :cursor` 가 아무것도 고르지 못하는 것이고, **맨 끝 글의 번호를 커서로 준 것과 구별되지 않아** 에러로 가를 수 없다.
  - **base64url 한 겹을 없앴다**(2026-09-25 **소유자 결정** — P-14 의 개정이다. 새 번호를 두지 않는다). 그 전에는 "불투명한 문자열이니 뜯어보지 말고 받은 그대로 보내라" 였다.
    **없앤 이유는 얻는 것이 없어서다.** ① **보안 값이 0 이다** — 서명하지 않았고, 위조해도 남의 글이 보이지 않는다(차단 거르기를 페이지마다 다시 한다). ② **숨기는 것도 없다** — **글 번호는 응답의 `postId` 에 그대로 다 나간다.** 커서만 감싸도 가려지는 값이 아니었다.
    ③ 남는 근거는 "형식이 바뀔 수 있으니 뜯어보지 마라" 는 계약 하나뿐인데 **커서가 이제 `id` 하나라 바뀔 여지도 작다.** 대가는 코드와 문서가 길어지는 것이었다. **그래서 문자열을 숫자로 바꾸는 일을 스프링에 맡겼다**(`Long` 파라미터 — 위 400 이 그 자리에서 난다).
  - **커서가 담는 것은 글 번호 하나다**(2026-09-24 **소유자 결정** — P-14 의 개정이다. 정할 때는 **정렬에 쓰는 값 셋**(모집 중인가 · `createdAt` · `postId`)을 담았다).
    **왜 바꿨나 — 정렬 키가 변하면 커서가 중복을 낸다.** 커서 페이지 나누기는 정렬 키가 ① 순서대로 커지고 ② 겹치지 않고 ③ **변하지 않는다**는 전제 위에 선다. 그런데 글의 상태는 변하고 **그것도 목록 조회 자신이 바꾼다**(방장 키가 사라진 글을 그 자리에서 만료로, 확정 표시 키가 있는 글을 확정으로 옮겨 적는다 — 아래 "만료").
    1쪽에 모집 중으로 나간 글이 그 사이 만료되면 2쪽의 조건("다음 묶음")에 **다시 걸려 같은 글이 두 번 보였다.**
    **`createdAt` 도 뺐다** — `id` 가 identity 라 위 셋을 혼자 다 만족하는데 `createdAt` 을 같이 쓰면 같은 뜻의 값을 둘로 들고 다니게 되고, 앱이 넣는 값(`createdAt`)과 DB 가 매기는 값(`id`)이라 동시에 들어온 둘에서 **서로 어긋날 수 있다.** 조건도 `id < ?` 한 줄로 줄었다.
    **옛 형식과의 호환은 두지 않았다**(프런트가 아직 없다) — 칸이 셋인 옛 커서든 base64url 로 감싼 커서든 숫자가 아니니 위와 같은 **400** 이다.
  - **`nextCursor`** — 더 볼 것이 있으면 **글 번호(숫자)**, 없으면 `null`. 다음이 있는지는 **보여 줄 것보다 한 개 더 읽어** 안다.
  - **`offset` 이 아닌 이유** — 1페이지를 보는 동안에도 글이 올라온다. `offset` 이면 그만큼 줄이 밀려 2페이지에 같은 글이 또 나오거나 사이의 글이 빠진다.
  - **신호(`BOARD_CHANGED`)를 받았을 때 프런트는 커서를 쓰지 않는다** — **펼친 만큼을 `limit` 으로 맨 위부터 다시 받는다**(`GET /api/v1/posts?game=LOL&limit=60`). 커서는 "더 보기"에만 쓴다.
  - **차단으로 모자라면 채운다** — 차단 거르기는 방 안에 누가 있는지를 Redis 에서 읽은 **뒤에** 하므로 `limit` 만큼 읽어도 보이는 것이 그보다 적을 수 있다. 그러면 **그 뒤를 더 읽어 채운다**(`platform.board.max-refills`, 기본 **3번**까지. 채우기 한 번이 목록 조립 한 벌이라 상한이 있다). 다 써도 모자라면 **있는 만큼**(빈 목록일 수도 있다) 내려 주고 `nextCursor` 로 이어 받게 한다.
  - **`nextCursor` 는 "마지막으로 읽은 줄"이다** — 마지막으로 **보여 준** 줄이 아니다. 숨겨진 글을 다음 페이지에서 또 읽지 않게 하려는 것이다.
  - **만료 · 확정 옮겨 적기는 읽은 글에만 걸린다**(아래 "만료") — 이제 목록이 글 전부를 읽지 않으므로 **깊은 곳의 글은 누가 그 페이지를 볼 때(또는 단건을 볼 때) 만료된다.** 받아들인 대가다 — 입장은 방의 Lua 가 방장 키 · 확정 표시 키를 직접 보므로 "목록에 안 보이지만 들어갈 수 있는 죽은 방"은 생기지 않는다.
  - **필터는 `game` 하나 그대로이고 2026-09-25 에 필수가 됐다**(위 · P-21 — 페이지도 그 게임 안에서만 이어진다). 페이지는 정렬(위 — `id` 내림차순)을 자른 것뿐이다.
- **방에 방장 말고 누가 있으면 글을 고칠 수 없다**(2026-09-24 **소유자 결정** · P-19). 고치기(`PATCH`)만 막는다 — **409 `ROOM_HAS_OTHER_MEMBERS`**.
  - **왜** — 고칠 수 있는 칸에 `mode` · `voice` · `purpose` · `conditions` 가 있다. **`NO_VOICE` 를 보고 들어와 앉아 있는 사람 앞에서 `REQUIRED` 로 바꿀 수 있고**, "즐겜"(`FUN`)을 "승급"(`RANK_UP`)으로 바꿀 수 있다.
    그런데 **방 안 사람에게 바뀌었다고 알려 줄 길이 없다** — 게시판 채널 신호(`BOARD_CHANGED`)는 목록을 보는 사람에게 가고, 방 안 알림(`ROOM_*`)에는 "글이 바뀌었다" 가 없다(그런 알림의 이름과 `payload` 가 미정이다 — 아래 "방" 의 "알림"). 그래서 **아예 막는다.**
  - **어느 칸을 바꾸든 막는다** — `title` 하나만 고치는 것도 막는다. 칸마다 가르면 무엇을 잠글지가 또 미정이 되고 규칙이 복잡해진다.
  - **방장 혼자 있을 때는 고칠 수 있다** — 바뀐 조건을 보고 들어온 사람이 없다. 방이 사라졌을 때(방 안에 아무도 없다)도 이 검사는 막지 않는다 — 그 글은 곧 만료로 옮겨진다(아래 "만료"). (2026-09-25 전에는 "글만 써 두고 방을 아직 안 만든 글" 도 고칠 수 있다고 적었다 — 글 쓰기가 방을 같이 만들어 그런 글이 없어졌다.)
  - **방 키를 못 읽으면 막는다 — 503 `ROOM_STATE_UNAVAILABLE` + `Retry-After: 5`**(2026-09-25 2단계부터 이 헤더가 붙는다 — 방의 503 과 한 벌이 됐다. 입장과 **같은 코드 · 같은 이유**다. 누가 방에 있는지 확인이 안 되는데 고치게 하면 이 규칙이 없는 것과 같다. 사유가 똑같아서 코드를 새로 두지 않았다).
  - **거르는 순서** — 없는 글은 404, **`mode` 검증이 400, 방장이 아니면 403 `NOT_POST_HOST`, 모집 중이 아니면 409 `POST_NOT_RECRUITING`, 그 다음이 이 검사다.** 남의 글이나 끝난 글에 대고 "방에 사람이 있다"를 알려 주면 그 자체가 새는 정보다.
  - **방 키는 글의 줄을 잠그는 트랜잭션 밖에서 읽는다**(그 안에서 Redis 를 기다리면 DB 커넥션을 붙잡는다 — `party/service/PostService` 와 `PostStore` 를 나눈 이유다). **그래서 "읽은 뒤 저장하기 전"에 누가 들어오는 경쟁이 남는다** —
    그 창은 짧고 막으려는 것이 "사람이 있는데 조건이 바뀌는 것" 이라 **감수한다**(방장 · 상태 검사는 잠금 안에서 한 번 더 한다 — 그쪽이 최종이다).
  - **`DELETE`(만료로 바꾸기) · 입장 · 방장 확정 · 목록 · 단건 조회는 막지 않는다** — 방에 사람이 있어도 그대로 된다. **방 안 사람에게 글이 바뀐 것(또는 지워진 것)을 알릴지는 미정이다**(`PARTY_*` 알림의 이름과 `payload` 가 통째로 미정이다 — `CLAUDE.md` §7).
- **차단 거르기** — 방 안(멤버 SET)이나 방장 가운데 나와 차단 관계(어느 방향이든)인 사람이 한 명이라도 있으면 **그 글을 목록에서 빼고 단건도 404 이고 입장도 막는다**(`CLAUDE.md` §7.1 · D-20 — 입장은 아래 "방" 의 "입장"). **내가 쓴 글은 숨기지 않는다.**
- **Redis 를 못 읽으면** — 목록 · 단건은 방 정보를 비운 채 글만 내려 주고 **만료 판정을 하지 않는다**(못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다 — fail-open). 고치기 · 입장은 **503 `ROOM_STATE_UNAVAILABLE`**(차단 대조 · 방 안 사람 확인을 못 했는데 통과시킬 수 없다 — fail-closed). 글 쓰기는 방을 못 만들어 **503 이고 글도 되돌려진다**.
  **어느 쪽으로 갈지는 부르는 쪽이 정한다** — 방 안을 읽는 창구(`room/service/RoomService#states`)는 못 읽으면 `RoomStateUnavailableException` 을 던질 뿐이다.
- **`postId` · `hostId` · 카드의 `userId` 는 전부 숫자다**(JSON 숫자로 나간다 — 문자열이 아니다).
- **방 안을 읽는 창구는 `room/service/RoomService#states` 하나다**(2026-09-25 2단계 — 그 전에는 게시판이 방 키를 Redis 로 직접 읽었다. `party/room/RedisRoomStateReader` 가 없어졌다). 글이 몇 개든 **파이프라인 한 번**으로 글마다 `EXISTS qm:room:{roomId}:host` · `SMEMBERS …:members` · `EXISTS …:confirmed` 를 읽는다. **쓰는 명령이 없다.** 방장 키의 **값은 읽지 않는다**(확정한 방에서는 바뀔 수 있다 — D-23).
- **멤버 SET 에 사용자 번호로 팔 수 없는 값**(숫자가 아닌 문자열 · 0 이하)이 있으면 그 창구가 **건너뛴다**(WARN 한 줄 · `room/domain/RoomMemberIds`) — 카드 · 파티원 · `memberCount` 어디에도 들지 않는다. 방에는 로그인한 사용자만 들어오지만(글 쓰기 · 입장이 access 토큰의 사용자 번호를 적는다) 손으로 넣은 값까지 막을 수는 없다.
  **숫자이지만 가입하지 않은 번호는 카드에서 그대로 둔다** — `nickname: null` · `profile: null` 로 남는다(`memberCount` 와 어긋나지 않게). **파티원으로는 기록되지 않는다**(2026-09-26 — `party_members.user_id` 의 `users(id)` FK 때문이다. 그 전에는 기록됐다 — P-23).
- **글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정되면 게시판 채널 `qm:pubsub:board` 에 `BOARD_CHANGED`(`payload` 는 `{}`)를 발행한다**(`CLAUDE.md` §3.2). 발행은 트랜잭션이 커밋된 뒤에 하고, 실패해도 본 작업을 뒤집지 않는다. 한 트랜잭션 안에서 여러 번 불려도(글과 방이 같이 생길 때 등) **커밋 뒤에 한 번**이다.

### 글 쓰기가 방을 만든다 (2026-09-25 **소유자 결정 C** · P-22)

**`POST /api/v1/posts` 한 요청이 글을 쓰고 그 글의 방을 만든다. 방을 못 만들면 글도 되돌린다.** 쓴 사람이 방장이고 곧바로 방에 들어와 있다(정원 5명에 방장이 포함된다).

- **순서** — 트랜잭션 안에서 글을 INSERT(여기서 DB 가 매긴 번호가 `roomId` 가 된다) → **커밋 전에** 방 만들기 Lua(`room/service/RoomService#create`) → 커밋(`party/service/PostStore#create`). "모집 중인 글은 한 사람에 하나"(409 `ALREADY_RECRUITING`)는 INSERT 에서 DB 가 먼저 거른다.
- **Lua 가 거절하면 그 코드로 409 이고 글은 안 써진다** — **409 `ALREADY_QUEUED`**(자동 매칭을 돌리는 중이다 — `matching` 의 활성 요청 키가 있다. D-19) · **409 `IN_OTHER_ROOM`**(이미 다른 방에 들어가 있다 — 자기가 방장인 다른 방 포함). **그래서 "이미 방에 있으면 글을 못 쓴다" 가 성립한다.**
  **409 `ROOM_ALREADY_EXISTS`** 는 그 번호의 방이 이미 있을 때인데 **번호는 DB 가 방금 새로 매긴 것이라 정상이면 나지 않는다**(누가 손으로 키를 넣었을 때뿐이다 — 방어용 갈래. "이미 내가 만든 방이다" 라는 결과도 같은 까닭으로 있을 수 없어 이 409 로 합쳤다).
- **Redis 에 닿지 못하면 503 `ROOM_STATE_UNAVAILABLE`**(+ `Retry-After: 5`)이고 글도 되돌려진다.
- **Lua 는 성공했는데 커밋이 실패하면**(드물다) 방이 Redis 에 **고아로 남는다** — 방장 키 · 멤버 SET · 쓴 사람의 입장 표시 키. **감수한다** — 수명(600초)이 다하면 저절로 사라지고, 그동안 그 사람은 나가기(`DELETE /api/v1/rooms/{roomId}/members/me`)로 풀 수 있다. 글이 없으니 목록에도 입장에도 걸리지 않는다(입장은 글부터 본다).
- **"트랜잭션 안에서 Redis 를 기다리지 않는다" 의 예외다** — 이 요청과 방장 확정 둘뿐이다. 둘 다 **Lua 한 번(밀리초)**이고, 글과 방이 한쪽만 남지 않게 하려는 것이라 커넥션을 그만큼 더 붙잡는 것을 받아들인다. 목록처럼 되풀이되는 읽기는 여전히 트랜잭션 밖에서 한다(`PostService` 와 `PostStore` 를 나눈 이유).
- **응답의 `members` 에 방장이 들어 있다** — 방금 만든 방이라 방 키를 다시 읽지 않는다. 방을 만들 때는 개인 알림이 없다(방에 아직 아무도 없다). 게시판 신호는 글의 것과 합쳐 커밋 뒤에 한 번 나간다.
- **방 만들기 요청(`POST /api/v1/rooms/{roomId}`)은 없어졌다.** 게시판의 방은 글 쓰기만 만든다. **자동 매칭 파티의 방을 어떻게 만들지는 미정 그대로다**(`CLAUDE.md` §7.2 (다)).
- 누가 정했나 — **글과 방을 한 요청에서 만들고 못 만들면 글도 되돌린다(C)는 소유자 결정**이다. INSERT 뒤 커밋 전에 Lua 를 부르는 순서 · 고아 방을 감수하는 것 · 트랜잭션 안의 Lua 를 예외로 둔 것 · `ROOM_ALREADY_EXISTS` 를 409 로 남긴 것은 **Claude 가 정했다.**

### 방과 글은 같이 산다 (2026-09-25 **소유자 결정** · P-22)

**확정 전에는 방이 끝나면 글도 끝나고, 글이 끝나면 방도 끝난다.** 글 쓰기가 방을 같이 만드는 것(위)의 짝이다.

- **방장이 나가면**(`DELETE /api/v1/rooms/{roomId}/members/me` — 확정하지 않은 방) 방이 닫히고(방장 키 · 멤버 SET · 남아 있던 전원의 **입장 표시 키**가 지워지고 `ROOM_CLOSED` 가 나간다 — 아래 "방" 의 "나가기") **글도 그 자리에서 `EXPIRED` 가 되고 게시판 신호가 나간다.**
- **방장이 글을 지우면**(`DELETE /api/v1/posts/{postId}` — 모집 중인 글) 글이 `EXPIRED` 가 되고 **방도 닫힌다** — 방 안에 있던 사람들에게 `ROOM_CLOSED` 가 가고 그들의 입장 표시 키가 지워진다(방장이 나가서 방이 닫힐 때와 같은 모양이다). 게시판 신호가 나간다.
- **확정 뒤에는 둘이 따로 간다** — 글은 `CONFIRMED` 로 고정이고(`DELETE` 는 지금처럼 409 `POST_CONFIRMED`), 방은 방장이 나가도 남은 멤버가 이어받는다(D-23 그대로).
- **이것으로 없어진 구멍 둘** — ① 방장이 글을 지워도 방에 남아 있어 그 방에서 나가기 전까지 새 글이 409 `IN_OTHER_ROOM` 이던 것 ② 방장이 방을 떠났는데 아무도 목록 · 단건을 보지 않아 글이 모집 중으로 남아 새 글이 409 `ALREADY_RECRUITING` 이던 것.
- **말없이 사라진 방장**(연결 끊김 — 방의 수명 만료)은 이 앱의 코드가 도는 순간이 없어 그 자리에서 글을 만료시킬 수 없다 — 그 경우는 여전히 목록 · 단건이 방장 키가 없는 것을 보고 옮겨 적는다(아래 "만료").
- **구현(Claude 가 정했다)** — 방을 닫는 것은 **`RoomService#leave(roomId, userId, whenClosed)`** 하나다(나가기 Lua `leave-room.lua` 를 그대로 쓴다 — 맨손 `DEL` 은 없다). **글 지우기**는 `PostStore#expireByHost` 가 글을 만료시킨 **뒤** 같은 트랜잭션 안에서 `roomService.leave(postId, 방장, () -> false)` 를 부른다 — 방장 나가기와 결과가 같다(방 키 셋 · 전원의 입장 표시 키 삭제 · `ROOM_CLOSED` · 게시판 신호는 글의 신호와 합쳐져 커밋 뒤 한 번). **방장 나가기**는 `RoomMemberService#leave` 가 `roomService.leave(roomId, userId, () -> expirePostOf(roomId))` 를 부르고, 방이 실제로 닫혔을 때만(`ROOM_CLOSED`) 콜백이 **`party/service/PostLifecycle#expireByRoomClosed(roomId)`** 를 불러 글을 만료시킨다(조건부 UPDATE — 이미 만료 · 확정이면 0줄). 확정한 방의 승계는 `LEFT` 라 글을 건드리지 않는다. **한쪽이 실패했을 때** — 지우기는 **만료가 먼저, 방 닫기가 뒤**다. 방 닫기가 실패하면 WARN 만 남기고 **204** 이며 글은 만료된 채다(글 쓰기의 "방을 못 만들면 글도 되돌린다" 와 반대인 이유 — 지우기는 이미 끝난 것을 정리하는 일이라 절반만 돼도 해가 없다). 나가기 쪽에서 글 만료가 실패해도 나가기는 성공이다(WARN · 방의 게시판 신호를 대신 낸다). 어느 쪽이든 남은 어긋남은 목록 · 단건의 자가 치유가 옮겨 적는다. **이미 만료된 글을 다시 지우면** 방 닫기만 다시 시도한다(앞선 닫기가 실패했을 때 방장이 한 번 더 눌러 정리할 수 있게) — 방이 없으면 조용히 204. **Redis 가 죽었을 때** 지우기는 204 다(글은 만료됐다). **남는 드문 경우** — 확정이 Redis 에서는 됐는데 DB 기록의 커밋이 실패한 뒤 방장이 지우거나 모두 나가면, 글은 만료되고 확정 기록은 남지 않는다. 새로 생긴 구멍은 아니다(전에도 `observe` 가 만료로 옮겨 적었다). 막으려면 `leave-room.lua` 에 결과 코드를 새로 둬야 해 하지 않았다. 테스트는 `party/PostRoomFlowTest`(지우기 → 방 닫힘 · 방장 나가기 → 글 만료 · 확정된 방의 승계는 글 유지 · 확정된 글 지우기 409 · 죽은 방의 글 지우기 204 두 번 · 방 닫기 실패해도 204)와 `room/service/RoomMemberServiceLeaveTest#onlyClosingExpiresThePost` 다.

### 만료 — 방장 키가 없으면 사라진 방이다

**모집 중인 글에 방장 키(`qm:room:{roomId}:host`)가 없으면 방이 사라진 것이다 → 글을 "만료" 로 바꾼다.** 글 쓰기가 방을 같이 만들므로(위) 글이 있으면 그 방은 한 번은 반드시 있었다 — **"아직 안 만들어진 방" 이 없다.**

- **판정** — 목록 · 단건이 방 안을 읽을 때 모집 중인 글마다 ① **확정 표시 키가 있으면** → 확정을 기록한다(자가 치유 — 아래 "방" 의 "방장 확정". 방장 키가 없어도 그렇다 — 확정한 방은 방장 키만 잠깐 없을 수 있다, D-23) ② 그렇지 않고 **방장 키가 없으면** → 만료.
- **확정된 글은 방장 키가 없어도 만료시키지 않는다** — 끝까지 `CONFIRMED` 다. 만료 판정은 모집 중인 글에만 한다.
- **Redis 를 못 읽으면 판정하지 않는다**(위 — 못 읽은 것을 "방이 없다" 로 읽지 않는다).
- **목록 조회(GET)가 글을 만료 · 확정으로 옮겨 적는다**(2026-09-26 부터는 파티 닫힘도 — 아래) — 방은 수명이 다하면 Redis 에서 저절로 사라져 그 순간 돌아가는 코드가 없다. 전부 조건부 UPDATE 라 멱등하다. **입장 검사는 옮겨 적지 않는다** — 방장 키가 없으면 곧이어 방의 Lua 가 404 `ROOM_NOT_FOUND` 로, 확정 표시 키가 있으면 409 `ROOM_CONFIRMED` 로 거절해 결과가 같다(`party/service/PostEntryGate`).
- **`room_seen_at` 과 `platform.board.room-grace`(10분)는 없어졌다**(2026-09-25 2단계 — 마이그레이션 `party/V8__drop_room_seen_at.sql` 이 컬럼을 지웠다. 2026-09-26 에 다시 쓴 `V1__schema.sql` 에는 처음부터 그 칸이 없다. P-5 의 그 절반을 걷어냈다). 두 앱이던 때 브라우저가 글을 쓴 뒤 방 만들기를 따로 불러서, 방장 키가 없는 글이 "방금 쓴 글" 인지 "방이 사라진 글" 인지 알 수 없었다 — 그것을 가르던 칸이다.
- **방장이 나가거나 글을 지우면 그 자리에서 만료된다**(위 "방과 글은 같이 산다"). 방장 키로 가르는 이 판정은 **방장이 말없이 사라진 경우의 받침**이다 — 그 방은 방의 수명(최대 10분)만큼 늦게 만료된다.
- **확정된 방이 통째로 사라졌으면 파티를 닫는다**(2026-09-26 **소유자 결정** · P-25 — 아래 "방" 의 "파티 닫힘"). 확정된 글의 방에 방장 키 · 멤버 SET · 확정 표시 키가 **전부** 없으면 그 글의 파티를 `CLOSED` 로 옮겨 적는다. **글은 `CONFIRMED` 그대로다.**
  **방장 키만 없는 것은 승계 중이라 닫지 않는다**(D-23). Redis 를 못 읽으면 판정하지 않는다(위와 같다).

### 구현하며 채운 빈 곳 (2026-09-21 · 2026-09-25 에 고쳤다)

- `PATCH` — `null` 이나 없는 칸은 그대로 둔다. `description` 은 빈 문자열이면 비우고 `wantedPositions` 는 `[]` 면 비운다. `title` 을 비우는 것은 400 이다.
  **`mode` 를 비우는 길은 없어졌다**(2026-09-24 — 모든 글이 모드 하나를 갖는다). `null` 은 그대로 두고 **빈 문자열은 400** 이다 — 바꾸려면 그 게임의 다른 모드 이름을 준다.
- 확정 · 만료된 글은 목록 · 단건에서 `members` 가 비고 `memberCount` 가 0 이다(`host` 는 채운다). 파티원은 DB(`party_members`)에 있지만 글 한 줄에 싣지 않는다 — 확정 순간의 파티원은 `ROOM_CONFIRMED` 알림의 `members` 로 간다.
- 남의 글을 `PATCH` · `DELETE` 하면 차단 관계여도 403 `NOT_POST_HOST` 다.
- `wantedPositions` 는 늘 그 게임의 정해진 순서(TOP · JUNGLE · MID …)로 나간다.
- ~~로그인 실패의 잠금 키 `qm:auth:login-lock:{loginId}`~~ — 2026-09-26 에 로그인 실패 제한째 없어졌다(P-24).
- 설정 — `platform.board.max-refills`(3 — 목록의 차단 채우기) · **`platform.room.ttl-seconds`**(환경변수 `ROOM_TTL_SECONDS`, 기본 600 — 방 키와 입장 표시 키의 수명. 아래 "방"). 목록의 페이지 크기(기본 20 · 상한 100)와 정원(5)은 코드의 상수다(`party/service/BoardProperties`).
  **2026-09-26 에 `platform.auth.login-throttle.*` 가 빠졌다**(P-24). **2026-09-25 2단계로 둘이 빠졌다** — `platform.board.room-grace`(글 쓰기가 방을 같이 만들어 기다려 줄 방이 없다)와 `room-ticket-ttl`(환경변수 `ROOM_TICKET_TTL` — 입장권이 없어졌다).

## 방 — `rooms` (2026-09-25 에 `room` 앱을 합쳤다 · P-22)

**방 안의 일이다** — 입장 · 나가기 · 강퇴 · 방장 확정 · 접속 확인 · 방 안 사람 목록 · 내 방 찾기 · 시그널 보내기, 여덟 가지다(**방 만들기는 요청이 따로 없다 — 글 쓰기가 한다**, 위 "글 쓰기가 방을 만든다").
구현은 `room` 패키지이고 **상태는 Redis 에만 있다**(DB 가 없다). 이 절은 옛 `room` 앱의 계약(`room/contracts/room-api.md` — 옆 폴더에 옛 모습 그대로 남아 있다)을 옮겨 와 합친 것이다.
**구현돼서 굳은 것만** 적는다. 계약 원본(queueMate 본 저장소)에 아직 없다 — 합칠 때 올려야 한다(P-22).

### 공통

- 에러 본문은 위 "공통" 과 같다. 클라이언트는 **`code` 로 갈래를 정한다**(`message` 는 사람이 읽는 글이라 바뀔 수 있다).
- **"부른 사람" 은 access 토큰(`qm_access` 쿠키)의 사용자다.** 쿠키가 없거나 틀리면 401 `UNAUTHENTICATED` 다. `POST` · `DELETE` 는 `Origin` 검사를 받는다(위 "공통").
- **`{roomId}` 는 글의 번호다**(위 "모집 글 · 목록"). 방 키 · 알림의 `userId` · `roomId` 는 **숫자를 십진 문자열로** 적은 것이다(`"42"` · `"123"` — P-11).
- **방을 바꾸는 것은 전부 Lua 스크립트 하나 안에서 끝난다**(정원 · 입장 표시 키 · 활성 요청 키의 `EXISTS` 가 한 스크립트 안에 있어야 불변식이 선다). **거절되면 서버에는 아무것도 쓰이지 않는다.**
- **입장권은 없다**(2026-09-25 2단계 — 소유자 결정 ①). 두 앱이던 때 입장권 발급이 보던 것(글이 모집 중인가 · 차단)은 입장 요청 안에서 본다(아래 "입장").

### 입장 — `POST /api/v1/rooms/{roomId}/members`

요청 본문은 없다. (2026-09-19 정함. 2026-09-25 에 글의 검사가 이 요청 안으로 들어왔다 — 소유자 결정 ①: **경로는 그대로 두고 그 안에서 검사한다**)

**검사 순서** — ① **글의 검사**(`party/service/PostEntryGate#check`: 글을 읽는다(없으면 404) → 방 안을 읽는다(못 읽으면 503) → 숨겨진 글이면 404 → 모집 중이 아니면 409) → ② **방의 Lua**(`lua/enter-room.lua`). ① 이 거절하면 ② 를 부르지 않는다.

| HTTP | `code` | 언제 | 클라이언트가 할 일 |
|---|---|---|---|
| 201 | — (본문 없음) | 방에 들어왔다 | 방 화면으로. 방에 있는 사람 목록은 목록 조회로 받는다 |
| 200 | — (본문 없음) | **이미 이 방에 들어와 있다** — 새로고침 · 재시도. 서버에서 바뀐 것은 없다 | 201 과 같다 |
| 404 | `POST_NOT_FOUND` | ① **그런 글이 없다**(`roomId` 가 숫자가 아닌 것도 여기다) — 또는 **차단 관계로 숨겨진 글이다.** 둘을 가르지 않는다 | 목록으로 돌아간다 |
| 409 | `POST_NOT_RECRUITING` | ① **글이 모집 중이 아니다**(만료 · 확정) | 목록으로 돌아간다 |
| 503 | `ROOM_STATE_UNAVAILABLE` | ① 방 안에 누가 있는지 읽지 못했다 — 차단 대조를 못 했는데 들여보낼 수 없다(`Retry-After: 5`) | 잠시 뒤 다시 |
| 409 | `ALREADY_QUEUED` | ② 자동 매칭을 돌리는 중이다 — `matching` 의 활성 요청 키가 있다 (docs/11 D-11 9번 · D-19) | 매칭을 취소해야 들어올 수 있다. 상태는 `matching` 의 `GET /api/v1/match-requests` 로 복구한다 |
| 409 | `ROOM_FULL` | ② 방이 가득 찼다 — 방장 포함 5명 (D-11 10번) | 목록으로 돌아간다 |
| 409 | `IN_OTHER_ROOM` | ② 다른 방에 들어가 있다 — 입장 표시 키의 값이 이 방이 아니다 | 그 방에서 나와야 한다 |
| 404 | `ROOM_NOT_FOUND` | ② 그런 방이 없다 — **방장이 말없이 사라져 방의 수명이 다했다**(글은 아직 모집 중으로 적혀 있다 — 다음 목록 · 단건이 만료로 옮긴다) | 목록으로 돌아간다 |
| 409 | `ROOM_CONFIRMED` | ② **방장이 확정한 방이다.** 새 사람은 못 들어온다 — 자리가 비어 있어도 마찬가지다(아래 "방장 확정") | 목록으로 돌아간다 |

- **① 의 순서 — 숨김(404)을 상태(409)보다 먼저 본다.** 차단 관계인 사람에게는 "모집이 끝났다" 도 알려 주지 않는다(입장권 발급 때와 같다). 방 안을 읽는 것(503)은 숨김 판정보다 앞이다 — 방 안 사람과의 차단을 봐야 숨김을 알 수 있다.
- **① 의 차단 대조는 방장 + 그 순간 방 안 전원과 한다**(D-20 그대로 — 목록에서만 숨기고 입장은 되면 의미가 없다). 목록의 숨김과 **같은 판정**을 쓴다(`PostService#isHidden`). 방 안은 `RoomService#states` 로 읽는다.
- **이미 그 방에 들어와 있는 사람은 ① 을 통과한다**(Claude 가 정한 세부) — ② 가 "이미 들어와 있다"(200)로 답하게 둔다. 확정된 방의 파티원이 새로고침하면 글이 `CONFIRMED` 라 409 가 되고, 방 안에서 나중에 차단이 생긴 두 사람은 404 가 되기 때문이다. 새 사람을 막으려는 검사가 이미 안에 있는 사람의 재시도를 거절할 이유가 없다.
- **① 은 글에 옮겨 적지 않는다** — 만료 · 확정 옮겨 적기는 목록 · 단건이 한다(위 "만료"). 그래서 입장은 DB 에 쓰는 일이 없고 트랜잭션 없이 돈다.
- **① 과 ② 사이는 원자적이지 않다** — 그 사이에 나와 차단 관계인 사람이 먼저 들어오는 경쟁이 남는다. **입장권 60초였던 창이 밀리초로 줄었을 뿐 없어진 것은 아니다**(D-20 "아직 미정" — `CLAUDE.md` §7.1).
- 방장은 글을 쓸 때 이미 들어와 있으므로 방장이 입장을 부르면 200 이다.
- 짝이 되는 `matching` 쪽 거절 — 방에 있는 사용자가 매칭을 요청하면 `matching` 이 409 `IN_ROOM` 을 준다(`matching/contracts/README.md` A-10).
- 구현: `party/service/PostEntryGate#check` → `lua/enter-room.lua` 의 반환값 ↔ `domain/EnterResult` ↔ `service/RoomMemberService#enter` ↔ `controller/RoomMemberController#enter`.
- 누가 정했나 — 입장권을 없애고 **경로를 그대로 둔 채 그 안에서 검사하는 것(①)은 소유자 결정**이다. 검사를 `party` 의 창구 하나(`PostEntryGate` — `PostService` 를 물지 않는 따로 선 빈. 빈 순환을 피하려는 것이다)로 둔 것 · 순서 · 이미 들어와 있는 사람을 통과시키는 것 · 503 을 쓰는 것은 **Claude 가 정했다.**

### 나가기 — `DELETE /api/v1/rooms/{roomId}/members/me`

요청 본문은 없다. 나가는 쪽은 항상 본인이라 주소가 `me` 다. (2026-09-19 정함. 확정한 방의 방장 넘기기는 2026-09-21 — docs/11 D-23)

| HTTP | 언제 | 서버에서 일어난 일 |
|---|---|---|
| 204 | 방에서 나갔다 | 멤버 SET 에서 빠지고 입장 표시 키가 지워졌다 |
| 204 | **확정하지 않은 방의 방장이 나갔다 — 방이 없어졌다** | 방장 키 · 멤버 SET · **남아 있던 전원의 입장 표시 키**가 한 번에 지워졌다. 방에 다른 사람이 있어도 마찬가지다(docs/11 D-11 11번 · D-21). **글도 그 자리에서 `EXPIRED` 가 된다**(2026-09-25 소유자 결정 — 위 "방과 글은 같이 산다") |
| 204 | **확정한 방의 방장이 나갔다 — 방은 이어지고 방장이 바뀌었다** | 방장이 멤버 SET 에서 빠지고 방장의 입장 표시 키가 지워졌다. **남은 멤버 가운데 한 명이 방장을 넘겨받았다** — 방장 키의 값이 그 사람의 `userId` 로 바뀌었다(docs/11 D-23) |
| 204 | 확정한 방의 방장이 나갔는데 **넘겨받을 사람이 없다 — 방이 없어졌다** | 멤버 SET · 방장 키 · 확정 표시 키가 지워졌다(D-23). **그 자리에서 파티가 닫힌다**(2026-09-26 소유자 결정 — 아래 "파티 닫힘") |
| 204 | 이 방에 없는 사람이다 — 이미 나갔거나, 늦게 도착한 나가기이거나, 없는 방이다 | 아무것도 지우지 않았다 |

- **전부 204 다.** 나가기는 몇 번을 불러도 결과가 같고, 클라이언트가 할 일도 같다(방 화면을 닫는다).
- 늦게 도착한 나가기가 **그 사이 들어간 다른 방의 입장 표시를 지우지 않는다** — 이 방의 멤버일 때만, 그리고 입장 표시가 이 방을 가리킬 때만 지운다.
- 방이 없어지면 남아 있던 사람들에게 `ROOM_CLOSED` 알림이 간다(아래 "알림"). 알림을 놓친 사람은 다음 요청에서 404 `ROOM_NOT_FOUND` 로 알게 된다.
- **확정한 방은 방장이 나가도 없어지지 않는다 — 방장 자리를 넘긴다(D-23).** 확정은 "이 사람들로 파티가 성립했다" 는 뜻이라, 그 뒤에 방장 한 명이 나갔다고 나머지 전원을 내보내는 것은 지나치다.
  확정 전에는 방 = 모집 글이고 글의 주인이 방장이므로 방장이 나가면 방도 끝난다.
  - 넘겨받는 사람은 **입장 표시 키가 이 방을 가리키는(살아 있는) 멤버**여야 한다 — 멤버 SET 에 이름만 남은 유령에게 넘기면 방장 없는 방이 된다. **누가 넘겨받는지는 정해져 있지 않다**(그 조건을 만족하는 아무나. 고르는 기준을 정할지는 미정 — `CLAUDE.md` §7.1).
  - **방장 키의 수명은 그대로 둔다**(`SET … KEEPTTL`). 새 방장의 접속 확인이 곧 늘린다.
  - **새 반환값 · 새 알림 · 새 `payload` 필드는 없다.** 남은 사람들에게 `ROOM_MEMBER_LEFT` 가 간다. **새 방장이 누구인지는 알림에 싣지 않는다** — 클라이언트는 `ROOM_MEMBER_LEFT` 의 `userId` 가
    자기가 알던 방장이면 **방 안 사람 목록(`GET …/members`)을 다시 조회하고, 응답의 `hostId` 가 새 방장이다.** (`payload` 에 `newHostId` 를 싣는 안은 받지 않았다 — 계약의 필드를 늘리지 않는다.)
  - **글의 `hostId` 는 바뀌지 않는다** — 글을 쓴 사람이다. 파티원의 `is_host` 도 글의 `hostId` 로 정한다(아래 "방장 확정").
  - 방장이 나가기를 부르지 않고 **말없이 사라진 경우**는 아래 "접속 확인" 에 있다.
- 구현: `lua/leave-room.lua` 의 반환값 ↔ `domain/LeaveResult` ↔ `controller/RoomMemberController#leave`.

### 강퇴 — `DELETE /api/v1/rooms/{roomId}/members/{targetUserId}`

**방장이 방에 들어와 있는 사람을 내보낸다**(docs/11 D-11 8번). 요청 본문은 없다. 주소의 `targetUserId` 가 내보낼 사람이고 부른 사람은 access 토큰의 사용자다. (2026-09-20 정함)

**방장만 할 수 있다.** "부른 사람이 방장인가" 는 스크립트 안에서 방장 키(`qm:room:{roomId}:host`)의 값과 비교한다 — 방장은 계정의 권한(role)이 아니라
**방마다 다른 Redis 의 상태**라서 권한 애너테이션으로 가르지 않는다.

| HTTP | `code` | 언제 | 클라이언트가 할 일 |
|---|---|---|---|
| 204 | — (본문 없음) | 강퇴했다 — 대상이 멤버 SET 에서 빠지고 대상의 입장 표시 키가 지워졌다 | 없다. 목록은 `ROOM_MEMBER_KICKED` 알림으로 고친다(방장도 받는다) |
| 404 | `ROOM_NOT_FOUND` | 그런 방이 없다 — 방장 키가 없다 | 방 화면을 닫는다 |
| 403 | `NOT_HOST` | 부른 사람이 이 방의 방장이 아니다 — 이 방의 멤버여도, 방 밖의 사람이어도, 다른 방의 방장이어도 마찬가지다 | — (방장이 아니면 강퇴 버튼을 보여 주지 않는다) |
| 400 | `CANNOT_KICK_SELF` | 방장이 자기 자신을 강퇴하려 한다 | 방장이 나가려면 **나가기**를 쓴다 — 확정하지 않은 방이면 방이 닫히고, 확정한 방이면 방장이 넘어간다 |
| 404 | `TARGET_NOT_IN_ROOM` | 대상이 이 방의 멤버가 아니다 — 그 사이에 나갔거나, 이미 강퇴됐거나, 들어온 적이 없다 | 목록에서 그 사람을 지운다 |

- 확인은 위 표의 순서다 — 방이 있는가 → 방장인가 → 자기 자신인가 → 대상이 멤버인가. 그래서 방장이 아닌 사람은 대상이 누구든 403 이다.
- 대상의 입장 표시 키는 **값이 이 방일 때만** 지운다(나가기와 같다). 다른 방을 가리키고 있다면 그 표시는 남의 것이다.
- **수명을 건드리지 않는다.** 강퇴는 방의 수명도 남은 사람의 수명도 늘리지 않는다 — 그것은 접속 확인의 일이다.
- 강퇴된 본인은 자기가 부른 요청이 아니라서 **`ROOM_MEMBER_KICKED` 알림으로 안다**(아래 "알림"). 알림을 놓쳤으면 다음 접속 확인 · 목록 조회의 403 `NOT_IN_ROOM` 으로 알게 된다.
- **강퇴당한 사람의 재입장은 막지 않는다 — 미정이다**(`CLAUDE.md` §7.1). 지금은 강퇴된 사람이 입장을 다시 부르면 201 로 들어온다. 막으려면 강퇴 목록 키가 필요하다.
- `DELETE …/members/me` 는 **언제나 나가기다** — 글자 그대로의 경로가 `{targetUserId}` 보다 먼저 잡힌다. (사용자 번호는 숫자라 `me` 인 사람은 없다.) `targetUserId` 는 글자 그대로 받는다 — 숫자가 아니어도 400 이 아니라 404 `TARGET_NOT_IN_ROOM` 이다.
- 구현: `lua/kick-room.lua` 의 반환값 ↔ `domain/KickResult` ↔ `service/RoomMemberService#kick` ↔ `controller/RoomMemberController#kick`.
  강퇴했을 때 스크립트는 `{1, 방에 남은 사람들…}` 을 돌려준다.

### 방장 확정 — `POST /api/v1/rooms/{roomId}/confirm`

요청 본문은 없다. **방장만** 부를 수 있다. 확정한 **그 순간 방에 있던 전원(방장 포함)이 파티원**이 된다 — 원치 않는 사람은 그 전에 강퇴한다.
**확정되면 새 사람이 못 들어오고, 되돌릴 수 없다.** (2026-09-20 정함. **2026-09-25 2단계로 한 요청이 방의 확정과 확정의 기록을 같이 한다**)

> **프런트가 할 일** — 되돌릴 수 없으므로 확정 버튼을 누르면 "확정하면 되돌릴 수 없습니다" 를 띄우고 **한 번 더 수락을 눌러야** 이 요청을 보낸다.
> 서버는 그 확인을 강제할 수 없다. 서버 쪽 안전장치는 아래의 200 하나다 — 버튼을 두 번 눌러도 아무 일이 없다.

| HTTP | `code` | 언제 | 클라이언트가 할 일 |
|---|---|---|---|
| 204 | — | 확정했다 — 방이 확정되고 글이 `CONFIRMED` 가 되고 파티가 기록됐다 | 없다. 파티원은 `ROOM_CONFIRMED` 알림의 `members` 로 온다(방장도 받는다) |
| 200 | — | **이미 확정된 방이다** — 재시도 · 두 번 누름. 서버에서 바뀐 것은 없다(아래 "자가 치유" 의 경우만 빼고) | 204 와 같다 |
| 404 | `ROOM_NOT_FOUND` | 방이 없다 — **그 번호의 글이 없거나**(`roomId` 가 숫자가 아닌 것 포함) 방장 키가 없다 | 방 화면을 닫는다 |
| 409 | `POST_NOT_RECRUITING` | **글이 만료됐다** — 방장이 글을 지웠는데 방은 아직 살아 있는 경우다. 그 방을 확정하면 파티를 적을 글이 없다 | — |
| 403 | `NOT_HOST` | 부른 사람이 방장이 아니다(방 밖의 사람 포함). 방장인지는 방장 키와 비교해 스크립트 안에서 본다 | 방장이 아니면 확정 버튼을 보여 주지 않는다 |
| 409 | `NOT_ENOUGH_MEMBERS` | 혼자서는 확정할 수 없다 — 방장 포함 2명 이상이어야 한다 | — |
| 503 | `ROOM_STATE_UNAVAILABLE` | Redis 에 닿지 못했다(`Retry-After: 5`). 아무것도 기록하지 않았다 | 잠시 뒤 다시 |

- **한 요청에서 Redis 와 DB 를 같이 쓴다**(`party/service/PostStore#confirmRoom`) — ① 글의 줄을 `SELECT … FOR UPDATE` 로 잠근다 → ② 만료된 글이면 409 → ③ 확정 Lua(`room/service/RoomService#confirm`) → ④ `CONFIRMED` 면 **그 Lua 가 돌려준 확정 순간의 멤버**로 기록한다 → 커밋. **Lua 가 거절하면 기록 없이 그 코드로 답하고 트랜잭션은 되돌린다.**
- **기록** — 글을 `CONFIRMED` 로, `parties`(`source` = `BOARD` · **`post_id` 가 글의 id(= `roomId`)**. `id` 는 DB 가 매기는 파티 자신의 번호다)와 `party_members`(확정 순간의 전원)를 만든다.
  **멱등을 지키는 것은 `UNIQUE (post_id)` 다.** 글을 `CONFIRMED` 로 바꾸는 조건부 UPDATE 가 한 호출만 통과시키고, 파티 · 파티원의 INSERT 도 `ON CONFLICT (post_id) DO NOTHING` · `ON CONFLICT DO NOTHING` 이다 — **이 요청과 자가 치유가 동시에 와도 파티는 하나, 파티원은 한 벌이다.**
  파티원의 `is_host` 는 방장 키의 값이 아니라 **글의 `hostId`** 로 정한다(확정한 방은 방장이 바뀔 수 있다 — D-23).
- **자가 치유 — Lua 는 성공했는데 커밋이 실패하면** "확정된 방인데 글은 모집 중" 이 남는다. 둘이 고친다 — ① **목록 · 단건**이 방 안을 읽다 **"DB 에는 모집 중인데 확정 표시 키가 있는 글"** 을 보면 그 자리에서 같은 기록을 한다(위 "만료" — 두 앱이던 때의 "길 ②" 가 이 자리로 남았다) ② 같은 사람이 **다시 누르면** Lua 가 "이미 확정"(200)으로 답하고, 그때 글이 아직 모집 중이면 지금의 멤버 SET 을 읽어 기록한다(못 읽으면 넘어간다 — ① 이 한다).
  **자가 치유가 읽는 멤버 SET 은 확정한 그 순간과 다를 수 있다**(확정 뒤에 나간 사람이 빠진다) — **감수한다.** 커밋 실패는 드물고 확정 순간의 멤버는 `ROOM_CONFIRMED` 알림으로 이미 나갔다.
- **"트랜잭션 안에서 Redis 를 기다리지 않는다" 의 예외다** — 글 쓰기와 이 요청 둘뿐이다(위 "글 쓰기가 방을 만든다"). Lua 한 번(밀리초)이다.
- **`POST /api/v1/posts/{postId}/confirm`(두 앱이던 때의 "길 ①" — 브라우저가 방의 확정 뒤에 따로 부르던 것)은 없어졌다**(2026-09-25 2단계). 409 `ROOM_NOT_CONFIRMED` 도 같이 없어졌다.
- **확정된 방.** 입장이 409 `ROOM_CONFIRMED` 로 거절된다 — **멤버가 나가 자리가 비어도**, 나간 파티원이 되돌아오려 해도 마찬가지다. 이미 들어와 있는 사람의 재입장(새로고침)은 그대로 200 이다(위 "입장").
  강퇴 · 시그널 · 일반 멤버의 나가기는 확정 전과 똑같다. **달라지는 것은 방장이 나갔을 때다(docs/11 D-23).**
- **확정한 방은 방장이 나가도(또는 말없이 사라져도) 없어지지 않는다 — 방장 자리를 남은 멤버에게 넘긴다.** 방장이 나가기를 부른 경우는 위 "나가기", 말없이 사라진 경우는 아래 "접속 확인" 에 있다.
  넘겨받을 사람이 없을 때만 방이 없어진다. **글은 끝까지 `CONFIRMED` 다**(위 "만료").
- **확정은 저절로 풀리지 않는다.** 확정 표시 키에도 수명이 있지만 방장의 접속 확인이 같이 늘린다 — **확정한 방에서는 일반 멤버의 접속 확인도 늘린다**(D-23). 방이 없어질 때 같이 지워진다.
- **확정과 입장은 둘 다 방의 Lua 스크립트라서** 확정하는 순간에 누가 끼어드는 일이 없다. `ROOM_CONFIRMED` 의 `members` 가 곧 확정된 파티원이다.
- 구현: `controller/RoomController#confirm` → `party/service/PostService#confirmRoom` → `PostStore#confirmRoom` → `room/service/RoomService#confirm` ↔ `lua/confirm-room.lua` ↔ `domain/ConfirmResult`. 입장 쪽은 `lua/enter-room.lua` 의 `KEYS[5]` · `EnterResult.ROOM_CONFIRMED`.
- 누가 정했나 — 확정을 한 요청으로 줄인 것은 **소유자 결정(합치기)의 뒤따름**이고, 글의 줄을 잠근 채 Lua 를 부르는 순서 · 커밋 실패를 자가 치유에 맡기는 것 · 만료된 글을 409 로 막는 것 · `roomId` 가 숫자가 아니면 404 `ROOM_NOT_FOUND` 인 것은 **Claude 가 정했다.**

### 파티 닫힘 — 확정된 방이 없어질 때 (2026-09-26 **소유자 결정** · P-25)

**확정된 방이 없어지면 그 방의 파티가 닫힌다.** `parties` 의 그 줄을 `status = 'CLOSED'` 로 바꾸고 `closed_at` 을 적고,
**그 순간 `party_members` 에 있는 사람끼리 서로를 `recent_players`(최근 함께한 사람)에 적는다.**

- **적는 법** — **방향마다 한 줄이다**(A 의 목록에 B, B 의 목록에 A). **다시 만나면 새 줄을 만들지 않고 시각만 갱신한다**(`(user_id, other_user_id)` PK 위의 UPSERT). 칸의 세부(`last_party_id` 등)는 코드 참조.
  파티원은 확정 순간의 전원이다(위 "방장 확정" — 가입하지 않은 번호는 파티원으로 기록되지 않았으니 여기에도 없다).
- **길이 둘이다.**
  ① **마지막 사람이 나가기를 눌러 방 키가 지워질 때** — 그 자리에서 닫는다(위 "나가기" 의 "넘겨받을 사람이 없다 — 방이 없어졌다").
  ② **전원이 말없이 사라져 방 키가 수명(600초)으로 없어졌을 때** — 그 순간 이 앱의 코드가 도는 일이 없으므로 **목록 · 단건이 방 키를 읽다가 발견해서** 닫는다(위 "만료" 의 자가 치유와 같은 방식이다).
  **방장 키 · 멤버 SET · 확정 표시 키가 전부 없을 때만**이다 — **방장 키만 없는 것은 승계 중이다**(D-23 — 늦어도 1분 안에 남은 멤버가 넘겨받는다).
- **한 번만 닫힌다** — `ACTIVE` 인 줄만 바꾸는 **조건부 UPDATE** 라 두 길이 겹쳐도(나가기와 목록이 같은 순간에 와도) 닫는 것도 `recent_players` 를 적는 것도 한 번이다.
- **확정 전에 방이 없어지면 파티가 없다** — 글만 만료된다(위 "방과 글은 같이 산다" · "만료" — 그대로다).
- **바뀌지 않는 것** — 글은 `CONFIRMED` 그대로다. **응답에 새 칸이 없다**(글 한 줄에 파티의 상태를 싣지 않는다). 경로 · 에러 코드도 그대로다. 최근 함께한 사람은 `GET /api/v1/recent-players` 로 읽는다(아래 "친구 · 신고 · 최근 함께한 사람").
- **알림은 내지 않는다** — `PARTY_*` 의 이름과 `payload` 는 여전히 미정이다(아래 "이 앱이 내는 알림").
- **`PartyClosed.fifo`(SQS)는 게시판 파티에 필요 없어졌다** — 닫는 쪽도 `recent_players` 를 적는 쪽도 이 앱이라 같은 앱 안에서 끝난다(`CLAUDE.md` §3.4). **자동 매칭 파티(`source = 'MATCH'` — 6단계)를 어떻게 닫는지는 그때 다시 본다.**
- **왜** — `parties.status` 가 `ACTIVE` 로 박힌 채 아무도 바꾸지 않았고, "파티가 닫혔다" 가 미정이라 `recent_players` 를 채우는 주체가 없어 최근 함께한 사람이 늘 빈 목록이었다.
- 구현(닫는 자리 · 쿼리 · 테스트)은 코드 참조.

### 접속 확인 — `POST /api/v1/rooms/{roomId}/heartbeat`

**브라우저가 방에 있는 동안 1분마다 부른다 — "나 아직 이 방에 있다".** 요청 본문은 없다. 방 키와 입장 표시 키에는 **수명(기본 600초, `ROOM_TTL_SECONDS`)**이
걸려 있고 이 요청이 그 수명을 다시 건다. 신호가 끊기면 수명이 다해 **저절로 사라진다** — 나가기를 못 누르고 탭을 닫아도, 이 앱이 죽어 있어도 마찬가지다.
SSE 의 `heartbeat` 이벤트(서버 → 브라우저, `notification` 이 보낸다)와는 **방향도 용도도 다른 것**이다. (2026-09-20 정함. 확정한 방의 예외는 2026-09-21 — docs/11 D-23)

| HTTP | `code` | 언제 | 클라이언트가 할 일 |
|---|---|---|---|
| 204 | — | 수명을 늘렸다 | 없다. 1분 뒤에 또 보낸다 |
| 403 | `NOT_IN_ROOM` | 이 방에 없는 사람이다 — 신호가 끊겨 있던 동안 수명이 다해 빠졌거나, 이미 나갔다 | **방 화면을 닫는다.** 다시 들어가려면 입장부터 한다 |
| 404 | `ROOM_NOT_FOUND` | 방이 없어졌다 — **확정하지 않은 방인데 방장의 신호가 끊겨 방의 수명이 다했다.** 이 사람의 입장 표시는 이 요청이 지웠다. 이때 게시판 채널 신호를 보낸다(아래 "게시판 채널 신호") | 방 화면을 닫는다 |

- **204 면 계속 있고 4xx 면 방 화면을 닫는다** — 상태 코드만 보고 정할 수 있다.
- **확정하지 않은 방 — 방의 수명(방장 키 · 멤버 SET)은 방장의 신호만 늘린다.** 일반 멤버의 신호는 자기 입장 표시만 늘린다. 그래서 **방장이 말없이 사라지면 방이 저절로 없어진다**
  (방장의 연결 끊김 = 방장이 나간 것. docs/11 D-11 11번). 남아 있던 사람은 자기 다음 신호에서 404 를 받는다 — **이때는 `ROOM_CLOSED` 알림이 가지 않는다.**
  수명이 다하는 순간에는 이 앱의 코드가 돌지 않고, 그 뒤에는 누가 있었는지(멤버 SET)도 남아 있지 않다.
- **확정한 방은 예외다 — 방장이 말없이 사라져도 방을 이어 간다(docs/11 D-23).**
  - 확정한 방에 한해 **일반 멤버의 신호도 멤버 SET 과 확정 표시 키의 수명을 늘린다.** 세 키가 방장의 마지막 신호에서 같은 수명을 받으므로, 안 그러면 방장 키가 만료되는 순간 멤버 SET 과 확정 표시 키도 같이 사라져 이어 갈 방이 남지 않는다.
  - **방장 키는 늘리지 않는다** — 방장 키가 만료돼야 다음 사람이 넘겨받는다. 방장 키의 수명은 어느 방에서든 방장의 신호만 늘린다.
  - 방장 키가 만료된 뒤 **처음 접속 확인을 보낸 멤버가 방장이 된다**(`SET 방장 키 EX 수명`). 그 신호는 그대로 방장의 신호로 처리된다 — 방의 수명을 늘리고, 입장 표시가 만료된 옛 방장을 유령으로 빼서
    남은 사람들에게 `ROOM_MEMBER_LEFT` 를 보낸다(바로 아래). 응답은 204 그대로다. 클라이언트는 그 `userId` 가 자기가 알던 방장이면 방 안 사람 목록을 다시 조회한다 — 응답의 `hostId` 가 새 방장이다(위 "나가기").
  - **그 사이에 방장 키가 잠깐 없다.** 방장 키가 만료된 때부터 다음 멤버의 접속 확인까지(늦어도 1분) 멤버 SET 과 확정 표시 키는 있는데 방장 키가 없다 —
    그동안 방 안 사람 목록 · 강퇴 · 확정은 404 로 답한다(다음 접속 확인 뒤에 다시 부르면 된다). 그리고 방장 키가 만료되기 **전까지**는 방장 키의 값이
    이미 사라진 방장이라 강퇴와 유령 빼기를 할 사람이 없다. 스크립트를 읽고 판단한 것이고 실험으로 확인하지는 않았다 (docs/11 D-23 "감수하는 것")
- **방장의 신호는 유령도 뺀다.** 말없이 사라진 멤버는 입장 표시가 만료된 뒤에도 멤버 SET 에 이름이 남는다(SET 의 원소에는 수명을 걸 수 없다).
  방장의 신호가 입장 표시가 이 방을 가리키지 않는 사람을 SET 에서 빼고, **남은 사람들(방장 포함)에게 그 사람마다 `ROOM_MEMBER_LEFT` 를 보낸다.**
  그래서 유령은 길어야 "수명 + 방장의 신호 주기" 만큼 목록과 정원에 남는다. 그동안 게시판의 카드에도 남고, **그 사람과 차단 관계인 사용자에게는 이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(D-20 "감수하는 것").
- 기본값의 대가 — 방장이 말없이 사라진 (확정하지 않은) 방은 **최대 10분** 목록에 살아 있는 것처럼 보인다(명시적 나가기는 즉시 닫는다). 줄이려면 `ROOM_TTL_SECONDS` 를 줄인다.
  1분 주기인 이유 — 브라우저는 백그라운드 탭의 타이머를 분당 1회 정도로 늦춘다.
- 구현: `lua/heartbeat-room.lua` ↔ `domain/HeartbeatResult` ↔ `service/RoomMemberService#heartbeat` ↔ `controller/RoomMemberController#heartbeat`.
  방장의 신호는 `{1, 뺀 사람 수 N, 뺀 사람 N명…, 남은 사람들…}` 을 돌려준다. 확정한 방에서 방장 자리를 넘겨받은 신호도 이 모양이다.

### 방 안 사람 목록 — `GET /api/v1/rooms/{roomId}/members`

**알림을 놓친 클라이언트가 지금 상태를 다시 맞추는 조회다.** 방 화면을 열 때(새로고침 포함)와 SSE 가 (다시) 연결될 때마다 부르고, 그 뒤로는 알림으로 고쳐 나간다. (2026-09-20 정함)

| HTTP | 본문 | 언제 |
|---|---|---|
| 200 | `{"roomId": "123", "hostId": "42", "members": ["42", "57", "63"]}` | 묻는 사람이 그 방에 있다 |
| 403 | `code: NOT_IN_ROOM` | 묻는 사람이 그 방에 없다 — 아무 방에도 없거나, 다른 방에 있거나, 이미 나갔거나, **방이 없다** |
| 404 | `code: ROOM_NOT_FOUND` | 지금 구현으로는 나오지 않는다(아래) |

- **값은 전부 문자열이다**(방 키에 적힌 글자 그대로 — 게시판의 `postId` · `userId` 가 JSON 숫자인 것과 다르다. 두 앱이던 때의 모양 그대로 두었다).
- **`members` 에는 방장도 들어 있다.** 방에 있는 사람 전부이고 인원수는 이 목록의 크기다. 방장은 `hostId` 로 구분한다. **확정한 방에서는 `hostId` 가 바뀔 수 있다**(docs/11 D-23 — 위 "나가기" · "접속 확인").
- **`members` 의 순서에는 뜻이 없다**(SET 이다). 부를 때마다 달라질 수 있다.
- **방 밖의 사람에게는 그 방이 있는지도 알려 주지 않는다** — 없는 방을 물어도 404 가 아니라 403 이다. 스크립트가 묻는 사람의 입장 표시부터 보고,
  방이 없어질 때는 그 방 사람들의 입장 표시도 같이 지워지기 때문이다. 404 는 방어용 갈래로만 남아 있다.
- **방 밖에서 방 안을 보는 공개 창구는 게시판 목록이다** — 차단을 거르는 곳이 거기라서다(D-20). 이 요청은 방 안의 사람만 본다.
- 방장과 멤버는 **같은 순간의 것**이다(스크립트 하나에서 읽는다). 아무것도 쓰지 않는다.
- 구현: `lua/members-room.lua` ↔ `service/RoomMemberService#members` ↔ `domain/RoomMembersResult` ↔ `controller/RoomMemberController#members`.

### 내 방 찾기 — `GET /api/v1/rooms/me`

**`roomId` 를 모르는 클라이언트가 부른다** — 방에 들어간 채로 탭을 닫았다가 앱을 새로 연 경우다. 이 조회가 없으면 그 사용자는 매칭을 눌러 409 `IN_ROOM`,
다른 방을 눌러 409 `IN_OTHER_ROOM`, 글을 쓰려다 409 `IN_OTHER_ROOM` 을 이유도 모른 채 받는다. `matching` 의 `GET /api/v1/match-requests` 와 같은 자리의 조회다. (2026-09-20 정함)

| HTTP | 본문 | 언제 |
|---|---|---|
| 200 | `{"roomId": "123"}` | 그 방에 들어가 있다(방장 포함) |
| 200 | `{"roomId": null}` | 아무 방에도 없다 — **에러가 아니라 정상 상태라 404 가 아니다.** `roomId` 칸은 언제나 있다 |

- 입장 표시 키 `qm:user:active-room:{userId}` 의 값을 그대로 돌려준다(문자열이다).
- 구현: `service/RoomService#myRoom` ↔ `controller/RoomController#myRoom`. `/me` 는 글자 그대로의 경로라 `/{roomId}` 보다 먼저 잡힌다.

### 시그널 보내기 — `POST /api/v1/rooms/{roomId}/signals`

**WebRTC 시그널(offer · answer · ICE 후보)을 같은 방의 상대에게 보낸다.** WebSocket 은 없다 — 보내는 길은 이 `POST`, 받는 길은 SSE 의 `WEBRTC_SIGNAL` 이다
(docs/11 D-9 · `room/docs/CONTRACTS.md` "`WEBRTC_SIGNAL` 의 전달"). **서버는 우체부다** — 보낸 사람과 받는 사람이 같은 방에 있는지만 확인하고,
내용은 해석하지도 저장하지도 않는다. (2026-09-20 정함)

요청 본문:

```json
{ "toUserId": "57", "signal": { "kind": "description", "description": { "type": "offer", "sdp": "v=0\r\n…" } } }
```

- `toUserId` — 받는 사람의 사용자 번호(문자열). 필수. **글자 그대로 받는다** — 숫자가 아니어도 400 이 아니라 404 `TARGET_NOT_IN_ROOM` 이다.
- `signal` — **모양은 클라이언트끼리의 약속이다. 서버는 JSON 값이기만 하면 받고, 열어 보지 않고, 글자 그대로 상대에게 싣는다.**
  종류(offer · answer · candidate), SDP 나 candidate 본문, 재협상 시도를 구분할 식별자(D-9 가 정하라고 한 항목들)는 전부 이 안에 넣는다. 필수(`null` 은 안 된다).

| HTTP | `code` | 언제 | 클라이언트가 할 일 |
|---|---|---|---|
| **202** | — (본문 없음) | 받는 사람의 채널에 발행했다. **도착을 뜻하지 않는다** | 답이 없으면 다시 보낸다(아래) |
| 403 | `NOT_IN_ROOM` | 보낸 사람이 이 방에 없다(없는 방 포함) | 방 화면을 닫는다 |
| 404 | `TARGET_NOT_IN_ROOM` | 받는 사람이 이 방에 없다 — 그 사이에 나갔거나 다른 방에 있다 | 그 사람과의 연결을 정리한다 |
| 400 | `VALIDATION_FAILED` | `toUserId` · `signal` 이 없다, 본문이 JSON 이 아니다(**2026-09-25 2단계로 `INVALID_REQUEST` 에서 바뀌었다** — 아래 "공통 에러") | — |

받는 쪽에 가는 알림의 `payload`:

```json
{ "roomId": "123", "fromUserId": "42", "signal": { …보낸 그대로… } }
```

- **순서를 보장하지 않는다.** 2026-09-20 에 offer 다음에 ICE 후보를 보냈는데 받는 쪽 SSE 에는 **후보가 먼저** 찍혔다. 받는 쪽은 offer 가 오기 전에 도착한 후보를 모아 둬야 한다.
  ICE 후보마다 `POST` 가 나가므로 HTTP/2 를 전제한다.
- **놓친 시그널은 다시 오지 않고 서버에 남아 있지도 않다.** 복구는 클라이언트의 일이다 — 답이 없으면 offer 를 다시 보내고, SSE 가 재연결되면
  방 안 사람 목록과 실제 peer 연결을 비교해 빠진 상대와 재협상하고, 연결이 `failed` 면 ICE restart 를 한다.
- **구독자 수는 응답에 싣지 않는다**(D-9 의 제안이었다). 발행은 예외도 값도 밖으로 내보내지 않는다 — 발행 실패가 요청을 실패로 뒤집지 않게 하려는 구조다.
- 기준은 "파티원" 이 아니라 **"방에 들어와 있는 사람"** 이다 — 확정 전에도 둘러보는 사람이 음성에 붙어 말을 걸 수 있어야 한다(D-11).
- 구현: `lua/signal-room.lua`(두 사람의 입장 표시가 이 방을 가리키는지. 읽기만 한다) ↔ `domain/SignalResult` ↔ `service/RoomSignalService` ↔ `controller/RoomSignalController`.

#### `signal` 의 권장 모양 — 서버는 강제하지 않는다

**이 절은 클라이언트끼리의 약속이다.** 서버는 `signal` 을 열어 보지 않으므로 아래를 어겨도 202 를 돌려준다 — 받는 쪽 브라우저가 못 알아들을 뿐이다.
보내는 브라우저와 받는 브라우저가 **같은 프런트 코드**를 돌리므로 사실상 그 코드가 약속이고, 여기에는 헷갈리지 않게 적어 둔다.

모양의 대부분은 새로 정하는 것이 아니라 **브라우저의 WebRTC API 가 내주는 객체 그대로**다. 프런트가 정하는 것은 "무엇이 들었나" 를 가르는 겉포장(`kind`)뿐이다.

| `kind` | 나머지 칸 | 보내는 쪽이 얻는 곳 | 받는 쪽이 넘기는 곳 |
|---|---|---|---|
| `"description"` | `description` — `{ "type": "offer" \| "answer", "sdp": "v=0\r\n…" }` | `pc.createOffer()` / `pc.createAnswer()` 의 결과(`pc.localDescription`) | `pc.setRemoteDescription(signal.description)` |
| `"candidate"` | `candidate` — `{ "candidate": "candidate:1 1 UDP …", "sdpMid": "0", "sdpMLineIndex": 0, "usernameFragment": "…" }` | `pc.onicecandidate` 의 `event.candidate` (STUN 으로 알아낸 주소가 이 객체로 나온다) | `pc.addIceCandidate(signal.candidate)` |

```json
{ "toUserId": "57", "signal": { "kind": "description", "description": { "type": "offer", "sdp": "v=0\r\n…" } } }
{ "toUserId": "57", "signal": { "kind": "candidate", "candidate": { "candidate": "candidate:1 1 UDP 2122 192.0.2.1 5000 typ host", "sdpMid": "0", "sdpMLineIndex": 0 } } }
```

- **프런트는 SDP 도 ICE 후보도 해석하지 않는다.** 브라우저가 준 객체를 통째로 보내고, 받은 객체를 통째로 브라우저 API 에 넘긴다. 해석은 브라우저의 WebRTC 엔진이 한다.
- **SDP 는 글자 하나(줄바꿈 `\r\n` 포함)만 달라져도 깨진다.** 서버가 `signal` 을 글자 그대로 옮기는 이유이고, `RoomSignalServiceTest` 가 그것을 지킨다.
- **후보가 offer 보다 먼저 도착할 수 있다**(위 "순서를 보장하지 않는다"). `setRemoteDescription` 이 끝나기 전에 온 후보는 모아 두었다가 그 뒤에 `addIceCandidate` 한다.
- **동시에 서로 offer 를 보내는 충돌**을 피하는 규칙을 프런트가 둔다 — 예: `userId` 를 비교해 작은 쪽만 offer 를 보낸다, 또는 perfect negotiation 의 polite / impolite (`room/docs/CONTRACTS.md`).
- 재협상을 구분할 번호가 필요해지면 `signal` 안에 칸을 더한다(예: `negotiationId`). 서버는 고칠 것이 없다.
- 음성은 방 안의 **사람마다 따로** 연결한다(메시). 누구와의 연결에 넣을 시그널인지는 알림 `payload` 의 `fromUserId` 로 고른다.

### 공통 에러 — 방의 모든 요청

| HTTP | `code` | 언제 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 필수 칸이 없다, 본문을 읽을 수 없다. `details` 에 칸별 사유(위 "공통" 과 같은 꼴) |
| 503 | `ROOM_STATE_UNAVAILABLE` | Redis 에 닿지 못했다. `Retry-After: 5`. 방의 상태는 Redis 에만 있어서 확인이 안 되면 통과시키지 않는다 |
| 401 | `UNAUTHENTICATED` | 로그인하지 않았다 — `qm_access` 쿠키가 없거나 만료됐거나 틀렸다 |
| 403 | `ORIGIN_NOT_ALLOWED` | `POST` · `DELETE` 의 `Origin` 이 허용 목록에 없다 |

> **바뀐 코드 둘(2026-09-25 2단계 — 에러 코드를 한 벌로 합쳤다).** **`INVALID_REQUEST` → `VALIDATION_FAILED`**, **`ROOM_UNAVAILABLE` → `ROOM_STATE_UNAVAILABLE`**(상태 · `Retry-After: 5` 는 그대로다).
> 두 앱이던 때 방의 요청만 옛 이름을 지켰다(1단계에서는 방의 컨트롤러로 범위를 좁힌 `RoomExceptionHandler` 가 지켰다 — 그 처리기가 없어졌다). **프런트가 아직 없어 옛 이름과의 호환은 두지 않는다.**
> Redis 장애를 503 으로 옮기는 자리는 **Redis 를 부르는 그 자리**다(`room/service/RoomRedis`) — 전역 처리기에서 `DataAccessException` 을 받으면 DB 오류까지 "방의 상태를 확인할 수 없다" 가 되고, 글 쓰기처럼 방의 요청이 아닌 곳에서도 방의 Lua 가 불리기 때문이다.
> 코드를 합친 것 · 옮기는 자리는 **Claude 가 정했다**(P-22).

### 알림 — 방이 내는 것 (2026-09-19 정함)

Redis `PUBLISH qm:pubsub:push:{받는 사람 userId}` 에 네 칸 봉투(`type` · `eventId` · `occurredAt` · `payload`)를 JSON 문자열로 보낸다(`CLAUDE.md` §3.2).
`notification` 이 그대로 SSE 로 배달한다 — `data:` 에 봉투, `id:` 에 `eventId`.

| `type` | 언제 | 받는 사람 | `payload` |
|---|---|---|---|
| `ROOM_MEMBER_ENTERED` | 누가 방에 들어왔다(입장 201) | 방에 **이미 있던** 사람들. 들어온 본인은 받지 않는다 | `{"roomId": "123", "userId": "들어온 사람"}` |
| `ROOM_MEMBER_LEFT` | 누가 방에서 나갔다 — 나가기를 눌렀거나, **신호가 끊겨 방장의 접속 확인이 뺐다.** **확정한 방의 방장이 나간 것도 이것이다**(방은 이어지고 방장이 바뀐다 — D-23) | 방에 **남은** 사람들. 나간 본인은 받지 않는다 | `{"roomId": "123", "userId": "나간 사람"}` |
| `ROOM_CLOSED` | 방이 없어졌다 — 확정하지 않은 방의 방장이 나갔거나, **방장이 모집 글을 지웠다**(2026-09-25 — 위 "방과 글은 같이 산다". 확정한 방은 넘겨받을 사람이 없을 때만 없어지고, 그때는 받을 사람이 남아 있지 않다) | 방에 **있던** 사람들. 방장 본인은 받지 않는다 | `{"roomId": "123"}` |
| `ROOM_MEMBER_KICKED` | 방장이 누군가를 강퇴했다(강퇴 204) | 방에 **남은** 사람들(방장 포함) **+ 강퇴된 본인** | `{"roomId": "123", "userId": "강퇴된 사람"}` |
| `ROOM_CONFIRMED` | 방장이 파티를 확정했다 | 그 순간 방에 있던 **전원(방장 포함)** — 방장의 응답(204)에는 파티원이 없어서 방장도 알림으로 받는다 | `{"roomId": "123", "members": ["42", "57", …]}` — 이 사람들이 파티원이다 |
| `WEBRTC_SIGNAL` | 같은 방의 누가 시그널을 보냈다(위 "시그널 보내기") | 받는 사람 한 명 | `{"roomId", "fromUserId", "signal": 보낸 그대로}` |

- **`payload` 의 id 는 문자열이다**(방 키에 적힌 글자 그대로) — 이 앱의 `FRIEND_*` 알림의 id 가 JSON 숫자인 것과 다르다(아래 "이 앱이 내는 알림"). 두 앱이던 때의 모양 그대로 두었다.
- **본인에게는 보내지 않는다** — 본인은 REST 응답으로 이미 안다. **강퇴만 예외다** — 강퇴된 본인은 자기가 부른 요청이 아니라서 알림이 아니면 알 길이 없다.
  강퇴를 부른 방장도 받는다(다른 알림과 모양을 맞춰 남은 사람 전원에게 보낸다).
- **새 방장이 누구인지는 알림에 싣지 않는다(docs/11 D-23).** 확정한 방에서 `ROOM_MEMBER_LEFT` 의 `userId` 가 자기가 알던 방장이면 **방 안 사람 목록을 다시 조회한다** — 응답의 `hostId` 가 새 방장이다.
- **아무에게도 가지 않는 것** — 방이 생길 때(글 쓰기 — 방에 아직 아무도 없다), 거절된 입장(409 · 404), 이미 들어와 있는 방의 재입장(200), 이 방에 없는 사람의 나가기, 거절된 강퇴(400 · 403 · 404).
- **글이 바뀌거나 지워진 것은 방 안에 알리지 않는다** — 그런 알림의 이름과 `payload` 가 미정이다(`CLAUDE.md` §7.1). 그래서 방에 다른 사람이 있으면 글을 고칠 수 없게 막았다(위 `PATCH` · P-19).
- **알림은 놓칠 수 있다.** SSE 가 끊겨 있던 동안의 것은 다시 오지 않고, 발행이 실패해도 입장 · 나가기는 그대로 성립한다(서버는 로그만 남긴다).
  클라이언트는 SSE 를 (다시) 연결한 직후 **방 안 사람 목록을 조회해서** 맞춘다(위 "방 안 사람 목록").
- **트랜잭션 안에서 발행하면 커밋 뒤에 나가고 되돌려지면 나가지 않는다** — 방장 확정의 `ROOM_CONFIRMED` 가 그렇다(글의 기록이 되돌려지면 알림도 나가지 않는다).
- `PARTY_*` 를 다시 쓰지 않고 새 이름을 지은 이유 — 그 7종의 이름과 payload 가 이 컴퓨터의 문서에 없어 같은 뜻인지 확인할 수 없었다. 계약 원본과 합칠 때 맞춘다.
- 구현: `room/service/RoomNotifier` → `common/push/PushPublisher` · `common/push/PushEventType`. 받는 사람 목록은 Lua 가 돌려준다 —
  `enter-room.lua` 는 `{1, 이미 있던 사람들…}`, `leave-room.lua` 는 `{1, 남은 사람들…}`(확정한 방의 방장이 나가 방장이 바뀐 것도 이것이다) / `{2, 있던 사람들…}`(방이 없어지면 멤버 SET 도 지워지므로 지우기 전에 읽는다),
  `kick-room.lua` 는 `{1, 남은 사람들…}`(강퇴된 본인은 서비스가 더한다).

### 게시판 채널 신호 — 방 쪽 (docs/11 D-20 · D-22 · D-23)

**위 알림과 채널이 다르다.** 위는 사용자 한 명의 채널이고, 이것은 **게시판 목록을 보고 있는, 누구인지 모르는 다수**에게 알리려는 **게시판 채널**이다(`CLAUDE.md` §3.2 "게시판 채널").
게시판 목록의 한 줄이 방 안의 인원과 사람들의 카드를 보여 주므로, 방의 인원이 바뀌면 보고 있는 화면이 F5 없이 갱신돼야 한다. **글 쪽의 신호는 위 "모집 글 · 목록" 에 있다 — 같은 채널이다.**

| 항목 | 값 |
|---|---|
| 채널 | `PUBLISH qm:pubsub:board` — **게임을 구분하지 않는 하나다**(D-22) |
| 봉투 | 위와 같은 네 칸. **`type` 은 `BOARD_CHANGED`, `payload` 는 빈 객체 `{}`** — `roomId` 도 `game` 도 싣지 않는다 |
| 언제 | 방의 인원이 바뀔 때 — 방이 생길 때(글 쓰기 — 글의 신호와 합쳐 커밋 뒤 한 번) · 입장(201) · 나가기(나갔을 때 · 방이 없어졌을 때) · 강퇴(204) · 방장 확정(204) · 접속 확인이 유령을 뺐을 때(몇 명을 뺐든 한 번) · **접속 확인이 "방이 없어졌다"(404 `ROOM_NOT_FOUND`)를 돌려줄 때**(D-23). **아무것도 안 바뀐 경우에는 보내지 않는다** — 재입장(200) · 거절 |
| 받는 사람 | **`notification` 에 살아 있는 모든 SSE 연결.** `notification` 이 이 채널 하나를 구독해 서버에서 거르지 않고 그대로 보낸다 — **`topics` 파라미터는 없앴다**(D-22) |
| 받은 클라이언트가 할 일 | **거르는 것은 클라이언트다** — 게시판 페이지(어느 게임이든)를 보고 있으면 목록을 `GET` 으로 다시 요청하고, 게시판 페이지가 아니면 무시한다. **재요청을 묶어서**(예: 몇 초에 최대 1번. 간격은 미정) 한 사람의 재요청이 간격당 1번을 넘지 않게 한다 |

- **이 신호는 "다시 받아라" 일 뿐이다.** 데이터와 차단 거르기는 목록 응답에서 온다. **`roomId` 도 싣지 않는다** — 방송은 사람별로 거를 수 없어서,
  차단 때문에 그 방이 숨겨진 사용자에게 "그 방이 바뀌었다" 는 사실이 새어 나간다.
- **방 쪽은 방이 어느 게임의 것인지 몰라도 된다(D-22)** — 채널이 하나이고 `payload` 가 `{}` 라서다.
- 발행 실패가 입장 · 나가기를 뒤집지 않는다는 것은 위 알림과 같다. 신호는 놓칠 수 있다 — 클라이언트는 SSE 를 (다시) 연결한 직후 목록을 다시 받는다.
- **수명이 다해 없어진 방은 없어지는 순간에는 신호를 내지 못한다** — 그 순간에는 이 앱의 코드가 돌지 않는다. **그 방에 남아 있던 멤버의 다음 접속 확인**(늦어도 1분 뒤)이
  404 를 받을 때 처음 알게 되고, **그때 신호를 보낸다(D-23).** 남은 사람이 여럿이면 각자 한 번씩 보내게 되지만 해가 없다.
  - **남는 한계 — 방장 혼자 있던 방.** 접속 확인을 보낼 사람이 없어 신호를 못 낸다. 다른 방의 신호나 새로고침으로 목록을 다시 받을 때 방장 키가 없는 것을 보고 그 글을 만료로 옮긴다(위 "만료").
  - Redis 키 만료 이벤트로 막는 방법은 받지 않았다 — 구독을 해야 하고, 인스턴스가 여럿이면 중복 발행이 되고, 앱이 죽어 있던 동안의 만료는 놓친다.
- **미정** — 채널 이름의 **원본 상수를 어느 서비스에 둘지**(이 앱은 `party/board/BoardChannels.BOARD_CHANNEL`, `notification` 은 자기 `redisKeys/BoardChannels.BOARD_CHANNEL` 에 적어 두었다 — **두 값이 같아야 한다**), 프런트가 재요청을 묶는 간격.
- 구현: `party/board/BoardChannels.BOARD_CHANNEL` · `party/board/BoardSignalPublisher`(방 쪽은 `room/service/RoomNotifier#boardChanged` 로 부른다). 보내는 곳은 `room/service/RoomService`(방이 생길 때 · 방장 확정)와 `room/service/RoomMemberService`(입장 · 나가기 · 강퇴 · 접속 확인)다.

### Redis 키 — 이 앱이 쓴다

**원본 상수는 `room/redisKeys/RoomKeys.java` 다.** 방 키 셋은 **이 앱의 것이다** — 이 앱 안에서도 **방을 바꾸려면 Lua 스크립트를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거친다**(맨손으로 `SADD` · `SET` 하면 정원 · 입장 표시 · 활성 요청 확인이 한 스크립트에 묶인 불변식이 깨진다). 게시판은 `RoomService#states` 로 읽기만 한다.

| 키 | 자료형 | 값 | 뜻 |
|---|---|---|---|
| `qm:room:{roomId}:host` | STRING | 방장의 `userId` | **이 키가 있다 = 방이 있다.** 글 쓰기가 방을 만들 때 쓰고, 방이 없어질 때 함께 지운다. **확정한 방에서는 값이 바뀔 수 있다** — 방장이 나가면 남은 멤버가 넘겨받는다(docs/11 D-23) |
| `qm:room:{roomId}:members` | SET | 방에 있는 사람의 `userId`(방장 포함) | `SCARD` 가 현재 인원이다. 정원은 5. 게시판은 `SMEMBERS` 로 `userId` 목록을 읽어 프로필을 붙이고 차단을 대조한다(docs/11 D-20). **원소는 `userId` 뿐이다** — 포지션 · 프로필을 담지 않는다 |
| `qm:room:{roomId}:confirmed` | STRING | 그 방의 `roomId` | **확정 표시 키. 이 키가 있다 = 방장이 확정한 방이다.** 방장 확정이 쓴다 |
| `qm:user:active-room:{userId}` | STRING | 들어가 있는 방의 `roomId` | **입장 표시 키. `matching` 과의 약속이다(docs/11 D-19)** — 이 앱이 쓰고 지우며 `matching` 은 `EXISTS` 로만 본다 |

- **`matching` 과의 약속(D-19)** — "자동 매칭 대기와 방은 한 번에 하나만" 을 키 둘로 지킨다. **활성 요청 키(`qm:user:active-request:{userId}`)는 `matching` 이 쓰고 이 앱은 `EXISTS` 로만 본다**(글 쓰기 · 입장의 Lua 가 409 `ALREADY_QUEUED` 로 거절한다). **입장 표시 키는 이 앱이 쓰고 `matching` 은 `EXISTS` 로만 본다**(매칭 요청이 409 `IN_ROOM`). 키 이름의 원본은 `matching` 의 `SharedKeys` 다(이 앱의 사본은 `room/redisKeys/SharedKeys`).
- **네 키 모두 수명이 있다**(기본 600초 — 설정 `platform.room.ttl-seconds`, 환경변수 `ROOM_TTL_SECONDS`). 방장 키 · 멤버 SET · 확정 표시 키는 **방장의 접속 확인만**, 입장 표시 키는 그 사람의 접속 확인이 늘린다(위 "접속 확인").
  **확정한 방은 예외다(docs/11 D-23)** — 일반 멤버의 접속 확인도 멤버 SET 과 확정 표시 키의 수명을 늘린다(방장 키는 늘리지 않는다).
  멤버 SET 에는 유령이 잠깐 남을 수 있다 — 인원수는 길어야 수명만큼 부풀 수 있다.
- **확정하지 않은 방은 방장이 나가면 방이 사라진다**(방에 다른 사람이 있어도 마찬가지다. 명시적 나가기든 연결 끊김이든) — 방장 키, 멤버 SET, **남아 있던 전원의 입장 표시 키**가 함께 지워진다.
  그래서 **방장 키가 없으면 방이 없어진 것**이고 게시판은 그 글을 "만료" 로 바꾼다(위 "만료" · docs/11 D-11 11번).
- **확정한 방은 방장이 나가도 방이 이어진다(D-23)** — 방장 키의 값이 남은 멤버의 `userId` 로 바뀐다. **"방장 키가 있다 = 방이 있다" 는 그대로 성립한다.** 넘겨받을 사람이 없을 때만 방이 없어진다 — 방장 키 · 멤버 SET · 확정 표시 키가 함께 지워진다.
  **단, 확정한 방은 방장 키만 잠깐 없을 수 있다**(위 "접속 확인") — 그래서 확정된 글은 방장 키가 없어도 만료시키지 않는다.

## 친구 · 신고 · 최근 함께한 사람 — `friend-requests` · `friends` · `reports` · `recent-players`

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/friend-requests` | `{userId}`(상대의 **사용자 번호**) | 201 친구 요청 한 줄 | 409 `FRIEND_REQUEST_ALREADY_SENT` · 409 `FRIEND_REQUEST_ALREADY_RECEIVED`(상대가 이미 나에게 보냈다 — 그것을 수락하면 된다) · 409 `ALREADY_FRIENDS` · 400 `CANNOT_FRIEND_SELF` · 404 `USER_NOT_FOUND`(없는 사용자 · **어느 방향이든 차단 관계** — 차단당한 사실이 새지 않게 같은 404 다) |
| `GET /api/v1/friend-requests?direction=RECEIVED` (`RECEIVED` 기본 · `SENT` — **대문자 그대로**) | — | 200 `{requests: [친구 요청 한 줄…]}` — **대기 중인 것만**, 새것이 먼저 | 400 `VALIDATION_FAILED`(모르는 값 · **소문자** — `"direction: 올바른 값이 아닙니다"`) |
| `POST /api/v1/friend-requests/{requestId}/accept` | — | 200 `{userId, nickname, since}`(새 친구) | 404 `FRIEND_REQUEST_NOT_FOUND`(없거나 · 내가 받은 것이 아니거나 · 이미 처리됐다) |
| `POST /api/v1/friend-requests/{requestId}/decline` | — | 204 | 404 `FRIEND_REQUEST_NOT_FOUND` |
| `DELETE /api/v1/friend-requests/{requestId}` | — | 204 (보낸 사람이 거둔다) | 404 `FRIEND_REQUEST_NOT_FOUND` |
| `GET /api/v1/friends` | — | 200 `{friends: [{userId, nickname, since}]}` 닉네임순 | — |
| `DELETE /api/v1/friends/{userId}` | — | 204 (친구가 아니어도 204) | — |
| `POST /api/v1/reports` | `{targetUserId, reason, detail, contextId}` | 201 `{reportId, createdAt}` | 400 `CANNOT_REPORT_SELF` · 404 `USER_NOT_FOUND` · 400 |
| `GET /api/v1/recent-players` | — | 200 `{players: [{userId, nickname, lastPartyId, lastPlayedAt}]}` 최근순 · 50명까지. **나와 차단 관계인 사람은 뺀다** | — |

**친구 요청 한 줄** — `{requestId, requester: {userId, nickname}, receiver: {userId, nickname}, createdAt}`. **`requestId` 와 `userId` 는 숫자다.**

- **같은 방향의 대기 중 요청은 하나 — DB 가 막는다**(partial unique index `WHERE status = 'PENDING'`). 친구는 `(user_low_id, user_high_id)` 로 정규화한 한 줄이고 PK 가 중복을 막는다. 수락은 **조건부 UPDATE**(`… WHERE id = ? AND receiver_id = ? AND status = 'PENDING'`) → 친구 INSERT 를 한 트랜잭션으로 한다 — 두 번 눌러도 친구는 한 줄이다.
- **친구 목록은 사람을 찾아보는 기능이 아니다** — **상대의 사용자 번호를 정확히 알아야** 요청을 보낼 수 있다(번호는 방 안 사람 카드 · 친구 목록 같은 응답에서 온다). 번호 · 아이디 · 닉네임으로 검색하는 API 는 만들지 않는다(공개 사용자 탐색 금지 — `CLAUDE.md` §1).
- 친구를 끊어도 · 차단해도 서로에게 알리지 않는다. **차단은 친구 관계를 건드리지 않는다**(미정 그대로).
- **`reason`** — `ABUSE`(욕설 · 비매너) · `CHEATING`(핵 · 대리) · `SPAM`(도배 · 광고) · `NO_SHOW`(잠수 · 탈주) · `OTHER`. `detail` 은 1000자까지(없어도 된다. `OTHER` 면 필수). `contextId` 는 글의 id(**숫자**, 없어도 된다 — 있는지 확인하지 않는다. 숫자가 아니면 400 `VALIDATION_FAILED` 다). **접수만 받는다**(`status` = `RECEIVED`) — 처리 화면 · 제재는 없다. 같은 사람을 여러 번 신고할 수 있다.
- **최근 함께한 사람은 확정된 파티가 닫힐 때 채워진다**(2026-09-26 **소유자 결정** · P-25 — 위 "방" 의 "파티 닫힘"). 그 전에는 읽는 쪽만 있고 늘 빈 목록이었다(채울 계기인 "파티가 닫혔다" 가 미정이었다).
  **지금 채워지는 것은 게시판으로 확정된 파티뿐이다** — 자동 매칭 파티는 6단계(SQS)에 매여 있다.

- 구현하며 채운 빈 곳 — `direction` 은 `RECEIVED` · `SENT` 만(**2026-09-26 소유자 결정 — 대문자 그대로 받는 enum 이다.** 그 전에는 소문자 `received` · `sent` 였다. 그 밖의 값과 소문자는 형 변환에서 400 — 게시판 목록의 `game` 과 같은 본문이다). `reason` 은 대문자 그대로만 받고, 공백뿐인 `detail` 은 없는 것으로 친다. **신고는 차단 관계를 보지 않는다**(나를 차단한 사람도 신고할 수 있어야 한다). 친구 목록의 닉네임순은 대소문자를 가리지 않고 같으면 사용자 번호순이다. 닉네임을 찾지 못한 줄은 목록에서 뺀다. 수락 응답의 `since` 는 DB 에서 읽은 값이다.
- **`friendships` 는 두 사용자 번호를 (작은 쪽, 큰 쪽)으로 정규화한 한 줄이다.** "작은 쪽"은 앱(자바의 `Long` 비교)이 고르고 DB 의 `CHECK (user_low_id < user_high_id)` 가 그것을 지킨다 — **숫자라 어디서 비교해도 같다.**
  문자열이던 때에 두 칸에 걸어 두었던 **`COLLATE "C"` 는 없어졌다**(2026-09-22 — 환경마다 다른 collation 때문에 멀쩡한 친구가 거절될까 봐 둔 것이었다).
- **알고 둔 것** — 양방향 대기 중 요청을 두 사람이 같은 순간에 서로 수락하면 DB 가 교착을 풀며 한쪽이 500 이 된다(데이터는 안 깨진다 — 친구는 한 줄이다). 요청을 받은 뒤에 차단이 생겨도 그 요청은 목록에 보이고 수락된다. 목록에 상한 · 페이지가 없고, 친구 요청 · 신고에 rate limit 이 없다.

### 이 앱이 내는 알림 (`qm:pubsub:push:{userId}` — 봉투는 `CLAUDE.md` §3.2)

| `type` | 받는 사람 | `payload` | 언제 |
|---|---|---|---|
| `FRIEND_REQUEST_RECEIVED` | 요청을 받은 사람 | `{"requestId": 12, "fromUserId": 42}` | 친구 요청이 저장된 뒤 |
| `FRIEND_REQUEST_ACCEPTED` | 요청을 보냈던 사람 | `{"requestId": 12, "userId": 42}`(수락한 사람) | 수락이 저장된 뒤 |

- 거절 · 거두기 · 친구 끊기 · 차단은 알리지 않는다. **알림은 "다시 조회하라"는 신호다** — 닉네임 같은 데이터는 싣지 않고, 받은 쪽이 `GET /friend-requests` · `GET /friends` 를 다시 부른다.
- **`payload` 의 id 는 JSON 숫자다**(`requestId` · `fromUserId` · `userId` — 2026-09-22). **채널 이름의 `{userId}` 도 사용자 번호다** — `qm:pubsub:push:42`. `notification` 은 access 토큰의 `sub` 로 채널을 여므로 **같은 글자가 된다**(그쪽 코드는 문자열을 그대로 다뤄 바꿀 것이 없다).
- 커밋된 뒤에 발행하고, 발행 실패가 본 작업을 뒤집지 않는다. 채널 접두사의 원본은 `matching` 의 `SharedKeys.PUSH_CHANNEL_PREFIX` 다.
- `PARTY_*` 는 아직 내지 않는다 — **이름과 `payload` 가 미정이고 6단계(SQS)에 묶여 있다**: 낼 계기가 자동 매칭 파티라 `matching` 쪽 발행(`ProposalConfirmed.fifo`)이 먼저다.

## 원본에 올려야 할 것

| # | 무엇 | 비고 |
|---|---|---|
| P-1 | 위 엔드포인트 전부(경로 · 스키마 · 에러 코드) | 원본 `openapi.yaml` 에 platform 엔드포인트가 이미 있으면 **그쪽과 맞춰야 한다** — 이 컴퓨터에서는 볼 수 없었다 |
| P-2 | access 토큰의 클레임 · `token_use` · 쿠키 이름 `qm_access` | docs/11 **D-24** 로 남겼다(2026-09-26 — `CLAUDE.md` §5.1) |
| P-3 | ~~입장권의 형식~~ — **2026-09-25 에 입장권이 없어졌다(P-22)** | 올릴 것이 없어졌다 — 입장 요청 안의 검사로 바뀌었다(위 "방" 의 "입장") |
| P-4 | `roomId` = 글의 id(**숫자** — P-11 로 UUID 에서 바뀌었다). **2026-09-25 부터는 글 쓰기가 그 번호의 방을 같이 만든다(P-22)** | 어느 문서에도 없던 것이다(`START_HERE.md` §4 E-2) |
| P-5 | ~~`room_seen_at` 으로 "아직 안 만들어진 방"을 가르는 법~~ · ~~만료 · 확정 글의 10분 보존~~ — **10분 보존은 2026-09-25 에 소유자가 없앴고(P-20) `room_seen_at` 도 같은 날 없어졌다(P-22 — 글 쓰기가 방을 같이 만들어 "아직 안 만들어진 방" 이 없다. 마이그레이션 `party/V8__drop_room_seen_at.sql`)** | 올릴 것이 없어졌다. 남은 규칙은 "모집 중인 글에 방장 키가 없으면 만료" 하나다(위 "만료") |
| P-6 | ~~방장 확정을 길 둘로 기록하는 것~~ — **2026-09-25 에 한 요청이 됐다(P-22).** 두 앱이던 때의 "길 ②"(목록 · 단건이 확정 표시 키를 보면 기록)는 **자가 치유**로 남았다 | 올릴 것은 P-22 에 합쳤다(위 "방" 의 "방장 확정") |
| P-7 | 소셜 로그인(카카오 · 디스코드)과 "처음 오면 로그인 아이디를 정한다" — **2026-09-26 에 "닉네임만 정한다" 가 됐고 소셜이 유일한 가입 · 로그인이 됐다(P-24)** | docs/00 의 계정 정의에 걸린다. 본 저장소의 v2 설계(Discord 로그인)와는 다른 모양이다 |
| P-12 | **전적 스냅숏은 세 게임이 한 테이블** — 판 수(`games`)만 공통 컬럼이고 승/패 · 연승 · 어시스트는 게임에 따라 비는 칸이다(소유자 결정 2026-09-22). PUBG 에 승/패를 우겨 넣지 않는다 | 게임마다 테이블을 나누는 안과 전부 jsonb 로 두는 안을 버렸다. docs/11 **D-27** 에 같이 남겼다(2026-09-26) |
| P-9 | 친구 · 신고 · 최근 함께한 사람의 경로와 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` | 원본 `events.md` 의 `FRIEND_*` 이름과 맞춰야 한다 — 이 컴퓨터에는 이름이 없었다 |
| P-10 | ~~로그인 실패 제한(429 `TOO_MANY_LOGIN_ATTEMPTS`)~~ — **2026-09-26 에 물음째 없어졌다(P-24)** | 올릴 것이 없어졌다 — 비밀번호 로그인이 없다 |
| P-8 | 게임 프로필 · 전적 스냅숏 · 글의 `voice` · `purpose` · `conditions` | 전적을 가져오는 법은 미정이다(`CLAUDE.md` §7 "게임 계정 연동"). **낡음 — 2026-09-27 에 P-26 이 개정했다**: 자기신고 칸이 게임마다 달라져 LoL 은 `gameNickname` 만 받는다 |
| P-17 | **(일부 낡음 — 2026-09-27 P-26: 전적 갱신이 `tier` · `mainPosition` 도 갱신한다)** **전적 갱신 요청**(2026-09-24 **소유자 결정** — 위 "전적을 긁는 것" 의 "전적 갱신"). **`POST /api/v1/users/me/game-accounts/{game}/refresh`** · **동기다**(다 긁을 때까지 기다린다) · 성공은 **200 + `PUT` 과 같은 게임 프로필** · **쿨타임 2분**(429 + `Retry-After`. 긁기를 시작할 때 찍고 **실패해도 소모된다**) · **이미 긁고 있으면 같은 429**(자물쇠 `qm:riot:sync:*` 를 그대로 쓴다) · **상한 30초**(넘으면 실패) · 라이엇 실패 · 시간 초과는 **503 이고 전적 줄을 건드리지 않는다** | 소유자가 직접 정한 것이다 — docs/11 **D-30** 으로 남겼다(2026-09-26. **P-13 을 뒤집지 않는다 — 긁는 시점이 하나에서 둘로 늘어난 것이다**). **Claude 가 정한 것** — 에러 코드의 이름 넷(`TOO_MANY_STATS_REFRESHES` · `GAME_ACCOUNT_NOT_FOUND` · `GAME_STATS_NOT_SUPPORTED` · `GAME_STATS_UNAVAILABLE`) · 쿨타임 키 `qm:riot:refresh:{gameAccountId}`(자물쇠와 **다른 키**다) · **구현이 없는 게임(VALORANT · PUBG)은 409** · **키(`RIOT_API_KEY`)가 없으면 503** · 실패의 갈래를 503 하나로 합친 것(태그 없는 닉네임도 여기다) · 거르는 순서(409 → 404 → 503 → 429) · **30초를 전용 풀 + `Future.get` 으로 재고 큐가 꽉 찼으면 던지기 전에 503 으로 끊는 것** · Redis 가 죽으면 쿨타임 없이 통과시키는 것. **남은 것** — 전적의 **자동 갱신 주기**는 여전히 미정이다(이 요청은 사용자가 누르는 것이다) |
| P-13 | **(일부 낡음 — 2026-09-27 P-26: "게임 계정을 저장할 때 커밋 뒤 비동기 · 실패해도 저장은 성공" 이 "LoL 은 저장 전에 동기 · 실패하면 저장하지 않는다" 가 됐다)** **전적 동기화(Riot API · LoL 만)** — **긁는 시점은 하나다(게임 계정을 연결 · 수정할 때. 2026-09-24 에 "전적 갱신" 이 붙어 둘이 됐다 — P-17)** · 비동기이고 실패해도 게임 계정 저장은 성공 · `external_id` 는 `puuid` · `verified` 는 켜지 않는다 · **평점은 넣지 않는다**(소유자 결정 2026-09-23. 위 "전적을 긁는 것") | docs/11 **D-27** 로 남겼다(2026-09-26 — 2026-09-24 의 개정까지 한 항목에). **2026-09-23 에 정한 것의 절반을 2026-09-24 에 소유자가 되물렸다 — 새 번호를 두지 않고 이 항목을 개정한다.** 그날 정했던 두 번째 시점(**모집 글을 쓸 때** 긁는 것)과 **신선도 30분**(`platform.riot.freshness`)이 없어졌다 — 긁는 것이 비동기라 글 쓰기 응답에 반영되지 않는데 대가가 Riot 호출 21번이고, 신선도를 보는 곳이 그 시점 하나뿐이라 죽은 코드가 됐다. **그래서 전적은 게임 계정을 저장할 때만 갱신된다**(낡은 채로 남는 것을 감수한다. 자동 갱신 주기는 **미정**). VALORANT · PUBG 는 아직 없다(VALORANT 의 전적 API 는 Riot 의 별도 승인이 필요하다). Redis 락 키 `qm:riot:sync:{gameAccountId}` 가 늘었다 — 이 앱의 접두사다 |
| P-14 | **게시판 목록의 페이지 나누기(커서 방식)** — `limit`(기본 20 · 최대 100 · 벗어나면 400) · `cursor`(**글 번호 · 숫자다** — 못 읽으면 400) · `nextCursor`(**마지막으로 읽은 줄** 기준) · 차단으로 모자라면 최대 3번 더 읽어 채우는 것 · 신호가 왔을 때는 커서 없이 맨 위부터 `limit` 만큼 다시 받는 것 (소유자 결정 2026-09-23. 위 "목록의 페이지 나누기"). **2026-09-24 에 정렬과 커서가 `id` 하나가 됐고**(소유자 결정 — 위 "목록의 정렬") **2026-09-25 에 커서의 base64url 한 겹이 없어졌다**(소유자 결정 — 위 "목록의 페이지 나누기") | docs/11 **D-28** 로 남겼다(2026-09-26 — P-20 · P-21 과 한 항목이다). **2026-09-23 에 정한 것을 2026-09-24 · 2026-09-25 에 소유자가 고쳤다 — 새 번호를 두지 않고 이 항목을 개정한다.** 정렬이 `(모집 중인가, createdAt desc, id)` 에서 **`id` 내림차순 하나(= 최신순)** 가 되고 커서가 **글 번호 하나**가 됐다. **왜 — 정렬 키가 변하면 커서가 중복을 낸다**(상태는 변하고 그것도 목록 조회 자신이 바꾼다. 1쪽에 모집 중으로 나간 글이 그 사이 만료돼 2쪽에 다시 걸렸다). `createdAt` 까지 뺀 것은 `id` 가 identity 라 순증가 · 유일 · 불변을 혼자 만족하고, 둘을 같이 쓰면 앱이 넣는 값과 DB 가 매기는 값이 어긋날 수 있어서다. **새로 감수하는 것 — 만료 · 확정된 글이 목록 위쪽에 섞여 나온다**(제자리다 — 맨 아래로 내려가지 않는다. 응답의 `status` 로 화면이 가른다. **2026-09-25 로 그 글들이 목록에 영원히 남게 됐다 — P-20**). `status` 필터는 **두지 않는다**(2026-09-25 소유자 결정). 마이그레이션 `party/V7__board_order_index.sql`(`(game, id DESC)` 를 만들고 옛 `(game, status, created_at DESC)` 를 지운다) · 옛 커서와의 호환을 두지 않은 것(400)은 Claude 가 정한 세부다. 채우기의 상한 · `nextCursor` 를 "읽은 줄"로 잡은 것도 그렇다. **2026-09-25 에 커서의 base64url 한 겹이 없어졌다**(**소유자 결정** — `cursor` 와 `nextCursor` 가 **숫자**다). **왜 — 감싸도 얻는 것이 없었다**: 서명하지 않아 보안 값이 0 이고(위조해도 남의 글이 보이지 않는다), **글 번호는 응답의 `postId` 로 이미 다 나가며**, 커서가 `id` 하나라 형식이 바뀔 여지도 작다. 대가는 코드와 문서가 길어지는 것이었다. 그래서 **문자열을 숫자로 바꾸는 일을 스프링에 맡겼다**(`Long` 파라미터) — **숫자가 아닌 커서는 형 변환에서 400** 이고 `limit` 이 숫자가 아닐 때와 **본문이 글자까지 같다**(`details` 가 `"cursor: 올바른 값이 아닙니다"`. 그 전에는 커서만의 글귀였다). **커서를 검사하는 클래스(`BoardCursor`)가 없어졌다.** **0 · 음수 · 맨 끝을 넘은 번호는 400 이 아니라 빈 페이지다**(그 전에는 `1` 미만을 400 으로 막았다 — 맨 끝 글의 번호를 준 것과 구별되지 않아 에러로 가를 수 없다는 판단이고, 그 판단은 **Claude** 가 했다). **마이그레이션 · 인덱스 · 정렬 · 채우기 · `?game=` 필터는 바뀌지 않았다.** **만료 · 확정 옮겨 적기가 읽은 글에만 걸리게 됐다** — 목록 깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다 |
| P-15 | **refresh 토큰**(2026-09-23 **소유자 결정**) — 불투명 UUID · Redis `qm:auth:refresh:{uuid}` → 사용자 번호 · `P7D` · 쿠키 `qm_refresh`(`Path=/api/v1/auth/refresh`) · `POST /api/v1/auth/refresh` · **rotation 필수**(`GETDEL` 한 번) · 실패는 전부 같은 401 `INVALID_REFRESH_TOKEN` · 로그아웃이 Redis 의 줄과 쿠키 둘을 지운다 · **access 가 `PT15M` 으로 줄고 `TEMP-NO-REFRESH` 가 없어졌다**(위 "refresh 토큰") | 소유자가 직접 정한 것이다 — docs/11 **D-26** 으로 남겼다(2026-09-26 · `CLAUDE.md` §5.1 (라) · (마)가 설계로만 적어 둔 것이 구현됐다. #16 의 access denylist 개정과 같은 묶음이다). **옆 서비스에는 걸리지 않는다** — 서명 · 검증이 달라지지 않고 access 의 수명만 짧아진다(`matching` · `notification` 은 공개 키로 검증만 한다. 옛 `room` 은 2026-09-25 에 이 앱에 합쳤다). 넣지 않은 것 — 탈취 감지(토큰 계보 추적) · 기기 수 제한 · 한 사용자의 refresh 를 한꺼번에 끊는 길 |
| P-16 | **`mode` 와 `tier` 의 값을 gameconfig(Redis)에서 읽어 검증하는 것**(2026-09-24 **소유자 결정** — 위 "gameconfig 를 읽는 것"). 읽는 키 둘(`qm:gameconfig:{GAME}:{MODE}` 의 `EXISTS` · `:tier` 의 `ZSCORE`) · **Redis 를 못 읽으면 통과시킨다(fail-open)** · **`mode` 가 필수가 됐다**(`PATCH` 에서 빈 문자열로 비우는 길이 없어졌다) · `tier` 는 값이 있을 때만 본다 | 소유자가 직접 정한 것이다 — **`CLAUDE.md` §2 · §11 의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15 를 개정한다.** docs/11 **D-29** 로 남겼다(2026-09-26). **미정 — `recruit_posts.mode` 를 `NOT NULL` 로 조일지, 옛 글의 빈 `mode` 를 어떻게 할지**(값의 목록이 Redis 에 있어 DB 가 강제할 수 없는 종류다 — 마이그레이션을 새로 만들지 않았다). Claude 가 정한 것 — 클래스의 자리(`common/gameconfig/GameConfigKeys` · `GameConfigReader`) · 안 심긴 gameconfig 도 통과시키는 것과 그것을 티어 사다리 키로 가르는 것 · 에러의 글귀 · `tier` 의 `@Pattern` 을 남긴 것 |
| P-11 | **모든 테이블의 PK 는 `bigint GENERATED ALWAYS AS IDENTITY` 이고, `userId` 는 사용자 번호다. 로그인 아이디는 `loginId` 로 따로 둔다**(2026-09-22 **소유자 결정** — 2026-09-19 의 "사용자 id 는 가입할 때 정한 로그인 아이디(문자열)"를 개정한다. **docs/11 D-4 와 얽힌다**). **낡음 — `loginId` 쪽 절반은 2026-09-26 에 없어졌다(P-24).** PK 가 bigint identity 이고 `userId` 가 사용자 번호인 것은 그대로다 | 다른 항목과 달리 **소유자가 직접 정한 것**이다 — docs/11 **D-25** 로 남겼다(2026-09-26 — `loginId` 절반이 없어진 것은 D-35). 걸리는 것 — ① **`matching` 의 `block/Block.java` 를 `Long` 으로 바꿨다**(`blocker_id` · `blocked_id` 가 bigint 가 됐다. 2026-09-26 에 그 폴더에서 했고 `schema = "social"` 도 같이 뺐다 — P-23) ② Redis 채널 `qm:pubsub:push:{userId}` · 방 키와 멤버 SET 의 `{userId}` · `{roomId}` 는 **숫자의 문자열**이 된다(`matching` · `notification` 은 그 값을 문자열로 다뤄 코드 변경이 없다) ③ 자동 매칭 파티(`source='MATCH'`)가 `matching` 의 UUID `partyId` 를 어디에 두는지는 **미정이다** — 6단계에서 정한다 |
| P-18 | **글 한 줄에서 `filledPositions` 를 없앤 것**(2026-09-24 **소유자 결정** — 위 "글 한 줄"). 응답에 그 칸이 없다. **글의 `wantedPositions` 와 카드의 `profile.mainPosition` 은 그대로다** | 소유자가 직접 정한 것이다 — **docs/11 D-20 의 ③("글의 '찾는 포지션' 가운데 이미 방 안에 있는 포지션의 강조")을 개정한다.** docs/11 **D-31** 로 남겼다(2026-09-26). **D-20 의 ①②④(인원 · 방 안 사람들의 카드 · F5 없이 갱신)는 그대로 유효하다.** 왜 — **주 포지션은 그 방에서 할 포지션이 아니라 틀린 정보였다**(위 "글 한 줄"). 테이블 · 컬럼은 바뀌지 않았다(마이그레이션 없음). **다시 둘 것인가는 미정이다** — 입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다(`CLAUDE.md` §7.1) |
| P-19 | **방에 방장 말고 누가 있으면 모집 글을 고칠 수 없는 것**(2026-09-24 **소유자 결정** — 위 `PATCH` 의 규칙. **409 `ROOM_HAS_OTHER_MEMBERS`** · 방이 없거나 방장 혼자면 고쳐진다 · **방 키를 못 읽으면 503 `ROOM_STATE_UNAVAILABLE`**) | 소유자가 직접 정한 것이다 — docs/11 **D-32** 로 남겼다(2026-09-26 · **개정하는 옛 D-항목은 없다** — D-11 · D-20 에 글 고치기 이야기가 없다. 새 규칙이다). 왜 — 고칠 수 있는 칸에 `mode` · `voice` · `purpose` · `conditions` 가 있는데 **방 안 사람에게 바뀌었다고 알려 줄 길이 없다**(게시판 신호는 목록을 보는 사람에게 가고 `ROOM_*` 에는 "글이 바뀌었다" 가 없다). **Claude 가 정한 것** — 에러 코드의 이름 · 503 을 입장권 발급(2026-09-25 부터는 입장)과 같은 코드로 재사용한 것 · 검사 순서(400 → 403 → 409 `POST_NOT_RECRUITING` → 이 검사) · 방 키를 **잠금 밖에서** 읽어 "읽은 뒤 저장하기 전"의 경쟁을 감수한 것. **막는 것은 `PATCH` 하나다** — `DELETE` · 입장 · 방장 확정 · 조회는 그대로다. **남은 것 — 방 안 사람에게 글이 바뀐 것을 알릴지는 미정이다**(`PARTY_*` 가 미정이다) |
| P-20 | **게시판 목록의 보존 기간을 없앤 것**(2026-09-25 **소유자 결정** — 위 "목록의 정렬"). **글을 상태로 가리지 않는다** — 모집 중 · 확정 · 만료가 전부 `id` 내림차순으로 나오고 **끝난 글도 계속 남는다.** 설정 `platform.board.closed-retention` 이 없어졌다 | 소유자가 직접 정한 것이다 — docs/11 **D-28** 로 남겼다(2026-09-26 — P-14 · P-21 과 한 항목이다). **P-5 가 정했던 "만료 · 확정 글의 10분 보존"을 걷어낸다**(새 번호를 둔 이유 — 10분 보존은 **Claude 가 정한 것**이고 이번 것은 **소유자 결정**이라 성질이 다르다. P-5 의 `room_seen_at` 쪽은 그때 그대로였다가 2026-09-25 에 P-22 가 걷어냈다). **P-14 의 정렬 · 커서는 바뀌지 않았다**(`id` 내림차순 하나 · 커서는 글 번호 하나). **왜 둘** — ① 옛 조건이 `status = RECRUITING or confirmedAt > ? or expiredAt > ?` 라는 **세 컬럼에 걸친 `OR` 셋**이라 `(game, id DESC)` 인덱스를 깨끗하게 타지 못했다(조건이 `game` 하나면 그 인덱스를 순서대로 훑어 내려가면 끝이다) ② 끝난 글이 10분 만에 사라지면 **"모집이 얼마나 활발한가" 를 보여 주지 못한다.** **감수하는 것** — 끝난 글이 목록 위쪽에 섞여 나오고(제자리다. `status` 로 화면이 가른다) **행이 DB 에 영원히 쌓인다 — 앱에 정리 작업은 없고 **오래된 글은 운영에서 소유자가 직접 지운다**(2026-09-25 소유자 결정).** 다만 글은 모집 중으로 태어나고 정렬이 `id` 내림차순이라 **1쪽은 대개 모집 중인 글일 것이다.** **마이그레이션은 없다** — `expired_at` · `confirmed_at` 컬럼은 CHECK 제약과 "언제 끝났는지" 로 그대로 쓰고, 인덱스 `(game, id DESC)` 는 이 변경으로 더 잘 맞는다 |
| P-21 | **게시판 목록의 `game` 을 필수로 만든 것**(2026-09-25 **소유자 결정** — 위 "목록의 `game` 은 필수다"). 안 보내면 400 `VALIDATION_FAILED`(`details` 는 `"game: 필요합니다"`) · 모르는 이름 · **소문자**는 같은 400 에 `"game: 올바른 값이 아닙니다"` | 소유자가 직접 정한 것이다 — docs/11 **D-28** 로 남겼다(2026-09-26 — P-14 · P-20 과 한 항목이다 · **개정하는 옛 D-항목은 없다** — D-11 · D-20 · D-22 에 목록의 필터 이야기가 없다. 새 규칙이다). **왜 — 게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다.** 쓰지 않는 갈래인데 **게임 없이 훑는 쿼리 둘**을 더 있게 만들었다 — 그 둘을 지웠고, 남은 둘은 `game` 이 늘 등호 조건이라 **`(game, id DESC)` 인덱스를 언제나 그대로 탄다.** **Claude 가 정한 것** — 빠진 파라미터의 글귀(`"game: 필요합니다"` — 글의 `title` · `mode` 가 비었을 때와 같은 말이다)와 그것을 내는 핸들러를 새로 붙인 것(`GlobalExceptionHandler#handleMissingParam` — 없으면 `details` 가 빈 채로 나가 어느 파라미터가 빠졌는지 알 수 없었다), 모르는 이름을 **이미 있던 형 변환 핸들러**에 맡겨 `limit` · `cursor` 와 글귀가 같아진 것, **소문자를 받아 주지 않는 것**(스프링의 기본 enum 변환이고 `Game#fromName` 과 결이 같다 — 받아 주려면 그것부터가 결정이다). **목록 하나에만 걸린다** — 단건 · 입장(그때는 입장권) · 방장 확정(그때는 확정 기록) · 글 쓰기 · 고치기 · 지우기는 그대로다. **마이그레이션 · 인덱스 · 정렬 · 커서 · 차단 거르기와 채우기는 바뀌지 않았다.** **`status` 필터는 두지 않는다**(2026-09-25 소유자 결정). 다른 필터를 더 둘지는 그대로 미정이다 |
| P-22 | **`room` 앱을 이 앱에 합친 것**(2026-09-25 **소유자 결정**). **1단계 — 옮겨서 돌게 하기(됐다).** 코드가 `com.queuemate.platform.room` 패키지로 왔다(`account` · `party` · `social` 과 나란한 도메인 — 안은 원본 그대로 `controller` · `service` · `domain` · `dto` · `redisKeys`. Lua 스크립트는 `resources/lua/`). 바뀐 것 — **경로는 그대로**(`/api/v1/rooms/**`) · **포트 8083 이 없어졌다**(전부 8082) · **`?userId=` → `qm_access` 쿠키**(`@CurrentUserId`) · **`TEMP-NO-PLATFORM` 이 없어졌다** · 이 앱의 공통 규칙(401 `UNAUTHENTICATED` · 403 `ORIGIN_NOT_ALLOWED`)이 방의 요청에도 걸린다. 겹치던 것은 이 앱의 것을 쓴다 — 에러 본문 · 예외 처리(`common/error/`), 개인 알림(`common/push/PushPublisher` — `PushEventType` 에 `ROOM_*` 다섯과 `WEBRTC_SIGNAL` 을 더했다), 게시판 신호(`party/board/BoardSignalPublisher`), 방 키 상수(`room/redisKeys/RoomKeys` — 게시판의 사본을 지웠다). **2단계 — 두 앱을 전제로 만들었던 경계 장치를 걷어냈다(됐다).** 소유자 결정 셋 — **① 입장권을 없애고, 입장 경로(`POST /api/v1/rooms/{roomId}/members`)는 그대로 둔 채 그 안에서 글을 검사한다** · **C 글 쓰기가 방을 같이 만들고, 방을 못 만들면 글도 되돌린다** · **방과 글은 같이 산다 — 확정 전에는 방장이 나가도 글을 지워도 둘 다 끝난다**(위 "방과 글은 같이 산다" — 확정 뒤에는 글은 `CONFIRMED` 고정, 방은 승계). 그래서 — 입장권(`POST /api/v1/posts/{postId}/ticket` · `token_use=room_ticket` · 클레임 `room_id` · `host_id` · 설정 `ROOM_TICKET_TTL`)이 없어졌다(**`token_use` 클레임과 `access` · `social_signup` 은 남는다**) · 방 만들기 요청(`POST /api/v1/rooms/{roomId}`)이 없어졌다 · `room_seen_at`(마이그레이션 `party/V8__drop_room_seen_at.sql`)과 `platform.board.room-grace` 가 없어졌다 — **모집 중인 글에 방장 키가 없으면 무조건 만료다** · 확정 기록 요청(`POST /api/v1/posts/{postId}/confirm` · 409 `ROOM_NOT_CONFIRMED`)이 없어지고 **`POST /api/v1/rooms/{roomId}/confirm` 한 요청이 방의 확정(Redis)과 확정의 기록(DB)을 같이 한다** · 게시판이 방 키를 Redis 로 직접 읽지 않고 **`room/service/RoomService#states` 를 부른다**(`party/room/` 패키지가 없어졌다) · **에러 코드가 한 벌이 됐다 — `INVALID_REQUEST` → `VALIDATION_FAILED`, `ROOM_UNAVAILABLE` → `ROOM_STATE_UNAVAILABLE`**(`RoomExceptionHandler` 가 없어졌다. 프런트가 없어 호환은 두지 않았다) · `contracts/room-api.md` 를 이 파일의 "방" 절로 합쳤다. **그대로인 것** — 나가기 · 강퇴 · 접속 확인 · 시그널 · 방 안 사람 목록 · 내 방 찾기의 경로와 동작, 방장 승계(D-23), 방 키 수명 600초(`ROOM_TTL_SECONDS` → `platform.room.ttl-seconds`), 게시판 신호, 알림의 `type` 과 `payload`, 정렬 · 커서 · `game` 필수 · `mode` 검증, 방에 다른 사람이 있으면 `PATCH` 를 막는 것. 근거 — 목록을 그릴 때마다 게시판이 방의 Redis 를 읽어야 했다(Azure 의 "서로 chatty 하면 같은 서비스" 기준), 확정 · 방 키 · 입장권을 두 앱이 같이 바꿔야 했다(design-time coupling), 팀이 한 사람이다, 먼저 한 덩어리로 만들고 경계가 드러나면 가른다(Fowler "MonolithFirst" · Azure 의 coarse-grained 서비스). 입장권(Valet Key 패턴)은 나눠 놨을 때의 표준이었지 나눠야 할 이유는 아니었다. **`notification`(SSE 연결 보유)과 `matching`(매칭 엔진)은 그대로 따로 둔다** | 소유자가 직접 정한 것이다(합치기 · ① · C · 방과 글은 같이 산다) — docs/11 **D-33** 으로 남겼다(2026-09-26). **docs/11 의 D-16 · D-19 · D-20 · D-21 · D-22 · D-23 이 전부 "두 앱"을 전제로 쓰여 있던 것도 D-33 이 개정했다**(그 항목들의 머리에 "낡음 — D-33" 이 붙었다). **앱 사이의 약속은 그대로다** — D-19 의 두 키는 `matching` 과 이 앱의 약속이다: **활성 요청 키(`qm:user:active-request:*`)는 `matching` 이 쓰고 이 앱은 `EXISTS` 만 하고, 입장 표시 키(`qm:user:active-room:*`)는 이 앱이 쓰고 `matching` 은 `EXISTS` 만 한다.** 이 앱 안에서 지키는 것은 보통의 계층 규칙 하나다 — **방을 바꾸려면 Lua 스크립트를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거친다**(원자성). **Claude 가 정한 것** — (1단계) 받는 사람의 `userId` 가 숫자가 아니면 알림을 건너뛰는 것(`room/service/RoomNotifier`) · 강퇴 대상 `{targetUserId}` · 시그널의 `toUserId` 를 글자 그대로 받는 것 · 되돌리기용 태그 `pre-room-merge`. (2단계) **입장 검사의 창구 `party/service/PostEntryGate`**(`PostService` 를 물지 않는 따로 선 빈 — 빈 순환을 피한다)와 **순서**(없는 글 · 숨겨진 글 404 `POST_NOT_FOUND` → 모집 중이 아니면 409 `POST_NOT_RECRUITING` → 방의 Lua. 방 안을 못 읽으면 503) · **이미 그 방에 있는 사람은 검사를 통과시키는 것** · **트랜잭션 안에서 Lua 를 부르는 것을 글 쓰기 · 확정 두 곳의 예외로 둔 것**(Lua 한 번 · 밀리초. 글 쓰기의 Lua 성공 뒤 커밋 실패는 고아 방이 TTL 로 죽는 것을 감수한다) · **확정의 커밋 실패를 자가 치유**(목록 · 단건이 확정 표시 키를 보면 기록 · 같은 확정을 다시 누르면 기록)에 맡기는 것 · 만료된 글의 방 확정을 409 `POST_NOT_RECRUITING` 으로 막는 것 · 글 쓰기의 Lua 거절을 그 코드 그대로 409 로 내는 것(`ALREADY_QUEUED` · `IN_OTHER_ROOM` · `ROOM_ALREADY_EXISTS`) · **에러 코드를 한 벌로 합친 것**과 Redis 장애를 503 으로 옮기는 자리(`room/service/RoomRedis`). **방과 글을 같이 닫는 구현** — `RoomService#leave(roomId, userId, whenClosed)` 하나로 두 길을 다 처리하고(`leave-room.lua` 재사용), 방장 나가기의 콜백이 `party/service/PostLifecycle#expireByRoomClosed` 를 부른다(위 "방과 글은 같이 산다"). **남은 것** — 자동 매칭 파티의 방을 어떻게 만드는가(`CLAUDE.md` §7.2 (다) — 방 만들기 요청이 없어져 그때 새로 정해야 한다), 입장 검사와 방의 Lua 사이의 차단 경쟁(D-20 미정 — 창이 밀리초로 줄었다), 강퇴당한 사람의 재입장 |
| P-23 | **DB 스키마 셋(`account` · `social` · `party`)을 `public` 하나로 합치고, 테이블 사이의 JOIN · FK 를 허용한 것**(2026-09-26 **소유자 결정** — `CLAUDE.md` §3.5). ① **스키마는 `public` 하나다** — 테이블 이름은 그대로(14개 — 2026-09-26 P-24 로 `credentials` 가 빠져 13개가 됐다)이고 `db/migration/<schema>/` 폴더 나누기가 없어졌다 ② **JOIN · FK 를 허용한다** — 사용자 번호를 담는 칸(`blocks.blocker_id/blocked_id` · `friend_requests.requester_id/receiver_id` · `friendships.user_low_id/user_high_id` · `reports.reporter_id/target_user_id` · `recent_players.user_id/other_user_id` · `recruit_posts.host_id` · `party_members.user_id`)에 **`users(id)` FK `ON DELETE CASCADE`**, `recent_players.last_party_id` → `parties(id)` **`ON DELETE SET NULL`**. 이름은 `<table>_<column>_fkey` ③ **마이그레이션을 `V1__schema.sql` 하나로 다시 썼다**(옛 V1~V8 의 최종 모양 + 위 둘) — 운영 DB 가 없고 로컬 · 테스트 DB 가 `--rm` 컨테이너라 매번 빈 채로 뜨기 때문이다. **"이미 적용된 마이그레이션은 고치지 않는다"는 운영이 생긴 뒤부터 걸린다** ④ **패키지 나누기(`account` · `social` · `party` · `room` · `common`)는 그대로다** — 바뀐 것은 DB 쪽이고, "남의 스키마를 JOIN 하지 않는다"가 "JOIN 은 된다"로 풀렸다. **경로 · 본문 · 에러 코드는 바뀌지 않았다**(없는 사용자는 여전히 404 `USER_NOT_FOUND` — 가려내는 자리가 FK 위반이 됐다) | 소유자가 직접 정한 것이다 — docs/11 **D-34** 로 남겼다(2026-09-26 — 2026-09-22 의 "스키마별 DB 롤을 두지 않는다" 도 이 항목에 접혔다). **개정하는 옛 결정** — docs/11 #17(schema-per-service · 스키마별 롤) · D-1(`matching` 롤에 GRANT) · 2026-09-22 의 "스키마별 DB 롤을 두지 않는다"(스키마가 하나가 되며 물음째 없어졌다) · docs/WHY_POSTGRESQL §3 의 스키마 배치. **왜** — DB 를 보는 앱이 사실상 이 앱 하나인데(`matching` 이 `blocks` 를 직접 읽는 것 하나뿐) 스키마를 나누고 JOIN · FK 를 금지한 탓에 코드가 쓸데없이 복잡했다(닉네임을 따로 읽어 자바에서 정렬 · 사용자 존재를 앱이 조회로 확인 · 도메인 사이 "읽는 창구"). 합치며 JOIN 한 쿼리 + `ORDER BY` 와 FK 로 바꿨고, 창구는 JOIN 이 대신 못 하는 것만 남는다(Redis 에서 온 id 목록으로 묻는 것 등 — **어느 창구가 남았는지는 코드 참조**). **`matching` 에 걸렸다** — `block/Block.java` 의 `@Table(schema = "social", name = "blocks")` 에서 `schema` 를 뺐다(2026-09-26 — P-11 의 `String → Long` 과 같이 그 폴더에서 했다). **`matching` 이 읽는 테이블이 `blocks` 하나라는 약속은 그대로다** **코드에서 Claude 가 정한 세부 셋** — ① `parties.post_id` 의 FK 도 `ON DELETE CASCADE`(사용자를 지울 때 글에서 멈추지 않게. 방장을 지우면 그 글의 파티 · 파티원 기록이 같이 지워진다 — 탈퇴를 만들 때 다시 본다) ② **가입하지 않은 번호는 파티원으로 기록하지 않는다**(`INSERT … WHERE EXISTS (users)` — FK 와 양립하지 않아 "파티원으로도 기록한다" 를 뒤집었다. 카드의 `null` 은 그대로) ③ **"나" 의 칸**(`blocker_id` · `requester_id` · `reporter_id` · `host_id`)의 FK 위반은 401 `UNAUTHENTICATED`(토큰은 멀쩡한데 DB 에 없다) · 남의 칸은 404 `USER_NOT_FOUND`. 그 밖에 — `recent_players.last_party_id` 가 nullable 이 됐다(`parties(id) ON DELETE SET NULL`), `UserReader` 창구가 없어졌다(JOIN 이 대신한다), 남은 창구는 `GameProfileReader` · `BlockReader`(둘 다 Redis 의 멤버 SET 에서 온 번호로 물어 JOIN 할 짝이 없다). 친구 목록의 정렬이 자바의 `CASE_INSENSITIVE_ORDER` 에서 DB 의 `lower(nickname)` 으로 옮겨 가 collation 에 따라 문장부호가 든 닉네임의 순서가 드물게 다를 수 있다 |
| P-24 | **직접 가입 · 비밀번호 로그인을 없애고 소셜 로그인(카카오 · 디스코드)만 남긴 것**(2026-09-26 **소유자 결정**). ① **가입 · 로그인은 소셜로만** — `POST /auth/signup` · `POST /auth/login` · 비밀번호(`{bcrypt}`) · `credentials` 테이블이 없어졌다(테이블은 13개) ② **`loginId` 를 없앴다** — `users.login_id` · 409 `LOGIN_ID_TAKEN` · 응답의 `loginId`(소셜 가입 · 재발급 · `users/me`)가 없다. 식별자는 사용자 번호 하나, 보여 주는 이름은 닉네임 하나다(친구 요청도 사용자 번호로 한다) ③ 소셜로 처음 온 사람은 **닉네임만** 정한다(`GET /auth/social/pending` → `POST /auth/social/signup {nickname}` 은 그대로). 딸려 없어진 것 — 로그인 실패 제한 전부(429 `TOO_MANY_LOGIN_ATTEMPTS` · `qm:auth:login-fail:*` · `qm:auth:login-lock:*` · `platform.auth.login-throttle.*`) · 401 `INVALID_CREDENTIALS` · `users/me` 의 `hasPassword`. **그대로인 것** — 소셜 로그인의 흐름(P-7) · refresh(P-15) · 로그아웃 · `users/me` · 쿠키 둘 · `token_use`(`access` · `social_signup`) · `Origin` 검사 · 사용자 번호(`userId` · JWT 의 `sub`) | 소유자가 직접 정한 것이다. **왜** — 비밀번호 관리 · 이메일 인증 같은 부담을 지지 않는다. **개정하는 것** — P-7 의 "아이디 · 닉네임을 정한다" · **P-10 은 통째로 물음째 없어졌다** · P-11 의 `loginId` 절반(사용자 번호는 그대로다) · docs/00 의 계정 정의("회원가입/로그인/로그아웃") · docs/11 D-25 의 절반(`userId` 와 `loginId` 를 가른 결정의 `loginId` 쪽). docs/11 **D-35** 로 남겼다(2026-09-26). **걸리는 것** — **실제 카카오 · 디스코드 키 등록이 이제 필수다**(그것 없이는 아무도 로그인할 수 없다 — 가짜 제공자로만 테스트했다). 이미 가입한 계정에 소셜 계정을 잇기 · 끊기는 **여전히 미정이다** |
| P-25 | **확정된 방이 없어질 때 파티가 닫힌다**(2026-09-26 **소유자 결정** — 위 "방" 의 "파티 닫힘"). `parties.status = 'CLOSED'` · `closed_at` 을 적고, 그 순간 `party_members` 의 사람끼리 서로를 **`recent_players`** 에 적는다(방향마다 한 줄 · 다시 만나면 시각만 갱신). 길이 둘 — ① 마지막 사람이 나가기를 눌러 방 키가 지워질 때 그 자리에서 ② 전원이 말없이 사라져 키가 수명으로 없어진 경우는 목록 · 단건이 방 키를 읽다가 발견해서(방장 키 · 멤버 SET · 확정 표시 키가 **전부** 없을 때만 — 방장 키만 없는 것은 승계 중이다, D-23). 조건부 UPDATE 라 두 길이 겹쳐도 한 번이다. **글은 `CONFIRMED` 그대로이고 응답에 새 칸은 없다** | 소유자가 직접 정한 것이다 — **docs/11 에 D-항목으로 남겨야 한다**(아직 없다 — `matching` 폴더의 일). **왜** — `parties.status` 가 `ACTIVE` 로 박힌 채 아무도 바꾸지 않았고, "파티가 닫혔다" 가 미정이라 `recent_players` 를 채우는 주체가 없어 최근 함께한 사람이 늘 빈 목록이었다. **걸리는 것** — **`PartyClosed.fifo`(SQS)는 게시판 파티에 필요 없어졌다**(같은 앱 안에서 끝난다 — docs/11 #21 · D-13 이 그린 "발행 + 소비" 는 자동 매칭 파티(6단계)에서 다시 본다). 확정 전에 방이 없어지면 파티가 없으니 글만 만료된다(그대로다). **남은 것** — 알림(`PARTY_*`)은 여전히 미정이라 내지 않는다 · 자동 매칭 파티를 닫는 법(6단계) **docs/11 에 D-36 으로 남겼다(2026-09-26).** 코드에서 Claude 가 정한 세부 — ① 길 ① 에서 "확정된 방이었나" 는 Lua 가 아니라 DB 가 가른다(글 만료 UPDATE 가 0줄이면 파티 닫기 UPDATE 를 시도 — 한 글이 두 상태일 수 없어 하나만 걸린다. `PostLifecycle#endByRoomClosed`) ② 접속 확인이 `ROOM_CLOSED` 를 받을 때도 같은 콜백이라 **확정 전 방이면 글 만료도 그 자리에서** 된다(전에는 목록이 볼 때까지 기다렸다) ③ 목록 · 단건이 `CONFIRMED` 글 가운데 파티가 `ACTIVE` 인 것만 골라 방 키를 더 읽는다(쿼리 하나 `findActivePartyPostIds`) ④ `recent_players` 는 `party_members` 자기 JOIN 한 번의 `INSERT … SELECT … ON CONFLICT DO UPDATE` 다(`social/service/RecentPlayerRecorder` 창구) ⑤ 파티만 닫힐 때는 게시판 신호를 내지 않는다(글 한 줄이 안 바뀐다). **감수** — 확정한 방에서 방장이 말없이 사라지고 남은 사람이 승계 전에 다 나가기를 누르면 확정 키가 남아 곧바로 안 닫히고 수명 뒤 길 ② 가 닫는다(Lua 를 안 고쳤다). 컬럼 · 마이그레이션은 바뀌지 않았다(`closed_at` 은 원래 있던 칸) |
| P-26 | **LoL 게임 계정은 `gameNickname`(이름#태그)만 받고 `tier` · `mainPosition` 은 Riot 에서 채운다**(2026-09-27 **소유자 결정** — 위 "계정" · "게임 프로필" · "전적을 긁는 것"). `PUT …/game-accounts/LOL` 의 본문은 `{gameNickname}` 하나 — `tier` · `mainPosition` · `server` 를 보내면 400 · **동기다**(저장하기 전에 긁는다 · 상한 30초 — 전적 갱신과 같은 길) · `tier` 는 `league-v4` 솔로랭크(gameconfig 사다리의 이름으로 · 언랭이면 `null`) · `mainPosition` 은 최근 경기에서 가장 많이 간 포지션 · **응답에 `tier` · `mainPosition` · `stats` 가 바로 들어 있다** · 이름#태그가 Riot 에 없으면 **404 `RIOT_ID_NOT_FOUND`**(새 코드) · Riot 장애 · 시간 초과 · 키 없음은 503 `GAME_STATS_UNAVAILABLE` — **둘 다 저장하지 않는다** · 전적 갱신(`POST …/LOL/refresh`)도 `tier` · `mainPosition` 을 같이 갱신한다 · **VALORANT · PUBG 는 지금대로 자기신고**(`tier` · `mainPosition` · PUBG 의 `server`) · 저장 뒤 비동기로 긁던 길이 없어졌다 | 소유자가 직접 정한 것이다 — **docs/11 에 아직 없다**(D-27 을 개정하는 것이라 `matching` 폴더에서 남겨야 한다). **개정하는 것** — P-13(비동기 → LoL 은 저장 전에 동기) · P-8(자기신고 칸 — LoL 은 `gameNickname` 만) · P-17(전적 갱신이 티어 · 포지션도 갱신). **왜** — Riot 에서 티어 · 포지션을 이미 받아 오면서 저장을 안 했다(계정(09-21)이 Riot 연동(09-23)보다 먼저 만들어져 요청 모양을 안 고쳤다). "포지션의 출처는 프로필의 주 포지션"(D-20)은 그대로다 — LoL 의 그 값이 자기신고에서 Riot 으로 바뀌었을 뿐이다. **Claude 가 정한 세부**(티어를 사다리 이름으로 옮기는 표 · 포지션 동률 · 태그 없는 닉네임의 응답 등)는 코드 참조 **docs/11 에 D-37 로 남겼다(2026-09-27).** **코드에서 Claude 가 정한 세부** — ① 티어 매핑: 솔로랭크(`RANKED_SOLO_5x5`)의 `tier`+`rank` 를 사다리 이름으로(`GOLD`+`II` → `GOLD_2` · `MASTER` · `GRANDMASTER` · `CHALLENGER` 는 단 없이 그대로 · 언랭은 **`null`**(`UNRANKED` 로 적지 않는다) · 모르는 단이나 사다리에 없는 이름은 `null` + WARN · Redis 를 못 읽으면 만든 이름을 그대로) ② 포지션 매핑: 최근 20판 `teamPosition` 의 최빈값(`TOP`→`TOP` · `JUNGLE`→`JUNGLE` · `MIDDLE`→`MID` · `BOTTOM`→`ADC` · `UTILITY`→`SUPPORT` · 빈 값 · `Invalid` 는 안 센다 · 동률이면 더 최근 것 · 경기가 없으면 `null`) ③ 이름#태그 꼴이 아니면 Riot 을 부르지 않고 400 `VALIDATION_FAILED`(`gameNickname`) ④ 이미 있는 LoL 계정을 누가 긁는 중이면 **429 `TOO_MANY_STATS_REFRESHES`**(`Retry-After` 60 — 락 수명. 쿨타임은 보지 않는다) · 처음 연결에는 락을 잡지 않는다(번호가 아직 없다 — 동시에 와도 UPSERT 가 한 줄만 남긴다) ⑤ 상한(30초)을 넘겨 늦게 끝난 긁기는 **버린다**(503 을 받았는데 연결돼 있는 일이 없게 — 전적 갱신은 늦게 끝나도 저장하는 것과 다르다) ⑥ `refresh` 도중 이름#태그가 Riot 에서 사라졌으면 404 가 아니라 기존대로 503 ⑦ Riot 응답에 `puuid` 가 없으면 503 ⑧ `GameStatsSync`(커밋 뒤 비동기) · `@EnableAsync` 를 지웠다 · `StatsSnapshot` 에 `tier` · `mainPosition` 칸 |

표에 없던 것 — **스키마별 DB 롤을 두지 않는 것**(2026-09-22 소유자 결정. 위 "차단"). 앱 하나가 롤 하나로 붙고 `matching` 은 별도 롤 없이 `blocks` 를 읽는다 — docs/11 #17 의 "스키마별 DB 롤" 대목과 D-1 의 GRANT 를 개정한다. — **2026-09-26 에 스키마가 `public` 하나가 되며 이 물음은 P-23 에 흡수됐고**(스키마별 롤이라는 물음 자체가 없어졌다), **docs/11 D-34 에 같이 접혀 남았다**(2026-09-26).
