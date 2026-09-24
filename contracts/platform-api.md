# platform 계약 — 이 폴더에서 정한 것

> **지위.** 계약 원본(queueMate 본 저장소 `feature/frontend` 의 `contracts/`)이 이 컴퓨터에 없어 **여기에 먼저 적는다**(`CLAUDE.md` §3.1).
> 2026-09-21 에 소유자가 "네가 만들어 봐라"고 맡겼고, 아래는 Claude 가 정해 구현한 것이다 — **소유자가 아직 항목별로 검토하지 않았다.**
> **2026-09-22 에 소유자가 직접 정한 것이 둘 있다** — **모든 테이블의 PK 를 `bigint identity` 로 하고 `userId`(사용자 번호)와 `loginId`(로그인 아이디)를 가른 것**(P-11. 2026-09-19 의 결정을 개정한다)과 **스키마별 DB 롤을 두지 않는 것**(아래 "차단"). 그 둘은 "소유자 결정"이라고 적었다.
> **2026-09-23 에 소유자가 정한 것이 셋 더 있다** — **LoL 의 전적을 Riot API 에서 긁는 것**(P-13. 아래 "게임 프로필" 의 "전적을 긁는 것" — 비동기 · 평점은 넣지 않는다. **그날 정한 "긁는 시점 둘" 은 2026-09-24 에 하나로 줄었다** — 바로 아래) · **게시판 목록의 페이지 나누기(커서 방식)**(P-14. 아래 "모집 글 · 목록 · 입장권" 의 "목록의 페이지 나누기") · **refresh 토큰**(P-15. 아래 "refresh 토큰" — access 가 `PT15M` 으로 줄고 `TEMP-NO-REFRESH` 가 없어졌다).
> **2026-09-24 에 소유자가 정한 것이 여섯 더 있다** — ① **`mode` 와 `tier` 의 값을 `matching` 의 gameconfig(Redis)에서 읽어 검증하는 것**(P-16. 아래 "gameconfig 를 읽는 것" — 읽는다 · Redis 를 못 읽으면 통과시킨다(fail-open) · `mode` 가 필수가 됐다 · `tier` 도 같이 본다)
> ② **모집 글을 쓸 때 전적을 긁던 것을 없앤 것**(**P-13 의 개정이다 — 새 번호를 두지 않는다.** 아래 "전적을 긁는 것" — 긁는 시점이 하나가 되고 신선도 장치가 없어졌다)
> ③ **"전적 갱신" 요청을 둔 것**(P-17. 아래 "전적을 긁는 것" 의 "전적 갱신" — `POST …/game-accounts/{game}/refresh` · **동기** · 쿨타임 2분 · 상한 30초. ②로 낡은 채 남게 된 전적을 사용자가 직접 갱신하는 길이라 **긁는 시점이 다시 둘이 됐다**).
> ④ **게시판 목록의 정렬과 커서를 `id` 하나로 한 것**(**P-14 의 개정이다 — 새 번호를 두지 않는다.** 아래 "모집 글 · 목록 · 입장권" 의 "목록의 정렬" 과 "목록의 페이지 나누기" — 전날 정한 커서의 속을 고친다. **정렬 키가 변하면 커서가 중복을 낸다**).
> ⑤ **글 한 줄에서 `filledPositions`(찾는 포지션 가운데 이미 채워진 것의 강조)를 없앤 것**(P-18. 아래 "모집 글 · 목록 · 입장권" 의 "글 한 줄" — **주 포지션은 그 방에서 할 포지션이 아니다.** **docs/11 D-20 의 ③ 을 개정한다**).
> ⑥ **방에 방장 말고 누가 있으면 모집 글을 고칠 수 없게 한 것**(P-19. 아래 "모집 글 · 목록 · 입장권" 의 `PATCH` — 409 `ROOM_HAS_OTHER_MEMBERS`. **조건이 바뀌는데 방 안 사람에게 알릴 길이 없다**).
> 원본과 합칠 때 맨 아래 "원본에 올려야 할 것" 표를 들고 간다. ERD 는 <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq>, 테이블의 원본은 `backend/src/main/resources/db/migration/` 이다.

## 공통

- 경로는 전부 `/api/v1/**`. 본문은 JSON(`application/json`), 시각은 ISO-8601 UTC.
- **모든 테이블의 PK 는 `bigint GENERATED ALWAYS AS IDENTITY` 다 — 식별자는 전부 숫자다**(2026-09-22 소유자 결정. 2026-09-19 의 "사용자 id 는 가입할 때 정한 로그인 아이디(문자열)"를 개정한다 — `CLAUDE.md` §3.5 · 아래 P-11).
  **사용자의 식별자는 둘로 갈린다** — **`userId`** 는 사용자 번호(`account.users.id`)이고 **밖으로 나가는 모든 자리**가 이것이다(JWT 의 `sub` · 알림 채널 `qm:pubsub:push:{userId}` · 입장권의 `sub` · `host_id` · URL 의 `{userId}` · 요청과 응답 본문의 `userId` · 다른 스키마의 `*_id` 컬럼 전부).
  **`loginId`**(가입할 때 정한 로그인 아이디)는 **가입 · 로그인 본문과 `users/me` 의 응답에만** 나온다 — 그것으로 사람을 가리키지 않는다.
- **경로 변수의 id 는 숫자다**(`/blocks/{userId}` · `/friends/{userId}` · `/posts/{postId}` · `/friend-requests/{requestId}`) — 숫자가 아니면 **400 `VALIDATION_FAILED`** 다(`details` 에 그 이름 한 줄).
- **본문의 id 칸은 문자열로 받아 앱이 판다**(`common/web/Ids` — 이 방식은 Claude 가 정했다. `Long` 으로 받으면 숫자가 아닌 값이 "본문을 읽을 수 없다"로 떨어져 어느 필드가 틀렸는지 말해 줄 수 없다. 클라이언트가 JSON 숫자로 보내도 받는다).
  **`userId` · `targetUserId` 가 숫자가 아니면 없는 사용자와 글자까지 같은 404 `USER_NOT_FOUND`** 다 — 400 으로 갈라 주면 "있을 수 있는 번호"와 아닌 것이 새어 나간다. `contextId` 는 사람을 가리키는 값이 아니라 400 `VALIDATION_FAILED` 다.
- **에러 본문은 `matching` · `room` 과 같다** — `{"code": "…", "message": "…", "details": ["…"]}`. **`details` 는 문자열의 배열이다**(두 서비스의 `ErrorResponse` 가 `List<String>` 이다). 없으면 `[]`.
- **인증** — access 토큰은 쿠키 **`qm_access`** 로 주고받는다(`CLAUDE.md` §5.1). 쿠키가 없거나 검증에 실패하면 **401 `UNAUTHENTICATED`**.
  인증이 필요 없는 요청은 `/api/v1/auth/**`(가입 · 로그인 · **재발급** · 로그아웃 · 소셜 로그인) · `/health/**` · `/info` 뿐이다. 로그인하지 않은 채 모르는 경로를 부르면 404 가 아니라 401 이다.
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

- **`token_use` 로 access 토큰과 입장권을 가른다.** 검증하는 쪽은 서명 · `iss` · `exp` 에 더해 **`token_use` 가 기대한 값인지** 반드시 본다 — 같은 키로 서명하기 때문에 이것을 안 보면 입장권을 access 토큰으로(또는 그 반대로) 쓸 수 있다.
  JOSE 헤더의 `typ` 을 쓰지 않은 이유 — Spring Security 의 기본 디코더가 `typ` 이 `JWT` 가 아니면 거절해서 검증하는 세 서비스가 전부 설정을 바꿔야 한다.
- **검증하는 쪽은 `sub` 가 사용자 번호(숫자 문자열)인지도 본다** — `^[0-9]{1,19}$`(원본 상수는 `common/security/TokenClaims.SUBJECT_PATTERN`. 이 앱은 `JwtConfig#jwtDecoder` 에서 그렇게 한다).
  아니면 컨트롤러에 닿기 전에 401 `UNAUTHENTICATED` 다 — 토큰이 이상한 것이지 서버가 고장 난 것이 아니다. 옆 서비스가 붙일 검증도 같은 모양이다(이 검사 자체는 Claude 가 정했다).

### refresh 토큰 (불투명 UUID · Redis — 2026-09-23 소유자 결정 · P-15)

access 가 짧아진 만큼(15분) 그것을 이어 주는 것이 refresh 다. **access 는 denylist 를 두지 않으므로**(`CLAUDE.md` §5.1 (라)) **서버가 무효화할 수 있는 것은 refresh 쪽 하나다** — 비밀번호를 바꾸거나 계정이 털렸을 때 끊을 수 있는 창이 24시간에서 15분으로 줄어든다.

| 항목 | 값 |
|---|---|
| 형식 | **JWT 가 아니다** — 불투명한 **UUID**(`UUID.randomUUID()` · `SecureRandom`). 값에 아무 뜻이 없다(사용자 정보를 담지 않는다) |
| 저장 | Redis **`qm:auth:refresh:{uuid}`** → 값은 **사용자 번호**. 수명이 곧 토큰의 수명이다 — 앱은 stateless 다. `qm:auth:` 는 이 앱의 접두사다(`matching` 의 `qm:user:*` · `room` 의 `qm:room:*` 와 겹치지 않는다) |
| 수명 | 환경변수 **`REFRESH_TOKEN_TTL`**(기본값 **`P7D`** — 7일) |
| 쿠키 | **`qm_refresh`** · `HttpOnly` · `SameSite=Lax` · `Domain` 없음 · `Max-Age` = 토큰 수명 · `Secure` 는 `COOKIE_SECURE`. **`Path` 는 `/api/v1/auth/refresh` 하나다** — access 쿠키(`Path=/`)와 다른 것은 `Path` 와 `Max-Age` 뿐이고, 그래서 이 값은 다른 요청에 실려 가지 않는다 |
| 어디서 나오나 | 로그인 · 소셜 로그인의 콜백(이미 연결된 사람) · 소셜 가입 · 재발급. **가입(`POST /auth/signup`)은 쿠키를 주지 않는다**(로그인시키지 않는다) |
| 기기 수 | 제한하지 않는다 — 토큰마다 키 하나다. 한 사용자의 refresh 를 전부 찾는 기능은 **없다**(`KEYS`/`SCAN` 을 쓰지 않는다) |

- **`POST /api/v1/auth/refresh`** — 본문이 없고 **`qm_refresh` 쿠키로만** 받는다. **인증이 필요 없다**(`/api/v1/auth/**` 아래다).
  성공은 **200** + 로그인과 **같은 본문**(`{userId, loginId, nickname}`) + `Set-Cookie` **둘**(새 access · 새 refresh).
- **rotation 은 필수다** — 쓴 값은 즉시 버리고 새것을 준다. 읽기와 지우기는 **`GETDEL` 한 번**으로 한다(`조회 → 판단 → 삭제` 가 아니다 — 그 틈에 들어온 두 요청이 둘 다 통과해 한 값으로 세션이 둘 생긴다).
  **옛 값을 다시 쓰면 그냥 401 이다** — 탈취 감지(토큰 계보 추적)는 넣지 않는다(`CLAUDE.md` §5.1 (마)).
- **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN` 이다** — 쿠키가 없든 · UUID 꼴이 아니든 · Redis 에 없든 · 이미 쓴 값이든 · 그 사용자가 사라졌든 · Redis 를 못 읽었든 **본문이 글자까지 같다.**
  어느 쪽인지 알려 주면 그 값이 살아 있는지가 새어 나간다. **실패할 때도 refresh 쿠키를 지운다**(`Max-Age=0`) — 못 쓰는 값을 브라우저가 계속 들고 있게 두지 않는다. **access 쿠키는 건드리지 않는다**(아직 살아 있을 수 있다).
- **로그인 실패 제한(429)을 걸지 않는다** — 그것은 로그인 아이디 단위로 세는 것이고 이 요청에는 아이디가 없다.
- **로그아웃(`POST /auth/logout`)은 둘을 지운다** — ① Redis 의 `qm:auth:refresh:{uuid}` ② 쿠키 둘(`qm_access` · `qm_refresh`, 각각 `Max-Age=0`). **쿠키가 없어도 · Redis 가 죽어 있어도 204** 다(그때 그 refresh 는 수명이 다할 때까지 살아 있다).
  access 는 서버에 지울 것이 없다 — **남는 최대 15분은 감수한다**(`CLAUDE.md` §5.1 (라)).
- **Redis 가 죽었을 때** — 로그인 · 소셜 로그인은 **그대로 성공한다**(access 만 나가고 refresh 쿠키가 없다. 로그인 실패 제한이 Redis 장애에 통과시키는 것과 같은 원칙이다). **재발급은 401 이다**(fail-closed — 확인하지 못한 값을 통과시키면 폐기된 토큰도 통과한다). 로그아웃은 204.
  **어느 경우에도 예외를 밖으로 내보내지 않는다 — 로그만 남긴다.** **토큰 값은 어느 로그에도 찍지 않는다**(사용자 번호까지만).

## gameconfig 를 읽는 것 — `mode` · `tier` 의 값 검증 (2026-09-24 **소유자 결정** · P-16)

**이 앱이 `qm:gameconfig:*` 를 읽어 `mode`(모집 글)와 `tier`(게임 계정)가 있는 값인지 본다.** 값의 원본은 **`matching/seed/gameconfig.redis`** 이고 **이 앱은 읽기만 한다.**

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
  운영자가 배포 때 심는 공유 설정이라(Parameter Store · ConfigMap 이 있을 자리다) 여러 서비스가 읽어도 된다. 가르는 기준은 **"바뀌는 계기가 사용자의 행동인가, 운영자의 배포인가"** 다 — `room` 의 방 키(실시간으로 쓰이는 상태, `CLAUDE.md` §3.3)와는 성질이 다르다.
  **이것은 `CLAUDE.md` §2 · §11 의 "매칭 Redis 키(… `qm:gameconfig:*` …) 접근 — 예외가 없다"와 docs/11 #15 를 개정한다** — `matching` 폴더에서 docs/11 에 D-항목으로 남겨야 한다.
- **이 앱은 seed 를 심지 않는다.** 심게 만들면 모드를 하나 추가할 때마다 이 앱을 재배포해야 한다 — 설정을 데이터로 뺀 뜻이 사라진다(seed 머리가 그 이유를 적었다).
- 거절은 **400 `VALIDATION_FAILED`** 이고 `details` 에 한 줄이다 — `"mode: {GAME} 에 없는 모드입니다"` · `"tier: {GAME} 의 티어가 아닙니다"`(글귀는 Claude 가 정했다).
- **`PATCH` 에서 `mode` 의 검증은 방장 · 상태 검사보다 먼저 일어난다**(글의 게임을 읽어야 검증할 수 있어서다 — Claude 가 정한 세부) — 남의 글이나 만료된 글에 없는 모드를 주면 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING` 이 아니라 **400** 이다. 없는 글은 그대로 404 다.
- **`mode` · `tier` 의 값 목록을 이 앱에 상수로 베껴 두지 않는다** — seed 와 조용히 어긋난다.

## 계정 — `auth` · `users`

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/auth/signup` | `{loginId, password, nickname}` | 201 `{userId, loginId, nickname}` | 409 `LOGIN_ID_TAKEN` · 409 `NICKNAME_TAKEN` · 400 |
| `POST /api/v1/auth/login` | `{loginId, password}` | 200 `{userId, loginId, nickname}` + `Set-Cookie` **둘**(`qm_access` · `qm_refresh`) | 401 `INVALID_CREDENTIALS`(아이디가 없는 것과 비밀번호가 틀린 것을 가르지 않는다) · 429 `TOO_MANY_LOGIN_ATTEMPTS` |
| `POST /api/v1/auth/refresh` | — (쿠키 `qm_refresh`) | 200 `{userId, loginId, nickname}` + `Set-Cookie` **둘**(새 `qm_access` · 새 `qm_refresh`) | 401 `INVALID_REFRESH_TOKEN`(이유를 가르지 않는다. 그때도 refresh 쿠키를 지운다) |
| `POST /api/v1/auth/logout` | — | 204 + **쿠키 둘 제거**(`Max-Age=0`) + Redis 의 refresh 폐기. 쿠키가 없어도 · Redis 가 죽어 있어도 204 | — |
| `GET /api/v1/users/me` | — | 200 `{userId, loginId, nickname, createdAt, socialProviders: ["KAKAO"], hasPassword, gameAccounts: [게임 프로필…]}` | 401 |
| `PATCH /api/v1/users/me` | `{nickname}` | 200 (`GET` 과 같은 모양) | 409 `NICKNAME_TAKEN` |
| `PUT /api/v1/users/me/game-accounts/{game}` | `{gameNickname, tier, mainPosition, server}` | 200 **게임 프로필**(아래 "게임 프로필") (없으면 만들고 있으면 바꾼다) | 400 |
| `POST /api/v1/users/me/game-accounts/{game}/refresh` | — | 200 **게임 프로필** — `PUT` 과 **같은 모양이고 방금 긁은 `stats` 가 들어 있다**(2026-09-24 소유자 결정. 아래 "전적을 긁는 것") | 429 `TOO_MANY_STATS_REFRESHES` + `Retry-After` · 404 `GAME_ACCOUNT_NOT_FOUND` · 409 `GAME_STATS_NOT_SUPPORTED` · 503 `GAME_STATS_UNAVAILABLE` |
| `DELETE /api/v1/users/me/game-accounts/{game}` | — | 204 (없어도 204) | — |

- **`userId`** — **사용자 번호다**(`account.users.id` · bigint identity). 요청 본문에 넣는 값이 아니다 — **DB 가 매기고 응답 · 토큰 · 채널 · URL 이 그것을 쓴다**(2026-09-22 소유자 결정).
- **`loginId`** — `^[a-z0-9_]{4,20}$`. 소문자 · 숫자 · 밑줄만(`users_login_id_format` CHECK 로 DB 도 같은 것을 건다). **가입 · 로그인과 `users/me` 의 응답에만 나온다.** 유일하다 — 겹치면 409 `LOGIN_ID_TAKEN` 이다(옛 `USER_ID_TAKEN` 이 이 이름이 됐다).
  **바꾸는 API 는 없다**(엔티티도 `updatable = false` 다). 다만 **이제는 바꿀 수 있는 값이다** — 채널 이름 · URL · 토큰에 로그인 아이디가 박혀 있지 않기 때문이다. 그것이 이 결정의 이득이고, 바꾸는 길을 열지는 정해지지 않았다.
- **`password`** — 8~72자 **그리고 UTF-8 로 72바이트 이하**(BCrypt 는 72바이트까지만 본다 — 한글은 24자까지다). `{bcrypt}` 접두사를 붙여 저장한다(나중에 해시 방식을 바꿀 수 있다).
- **`nickname`** — 2~16자, 앞뒤 공백 없음. **유일하다**(방 안 사람 카드에서 서로를 구분하는 이름이다). **대소문자를 구별한다** — `Faker` 와 `faker` 는 다른 닉네임이다(2026-09-21 소유자 확정).
- **로그인 실패 제한(2026-09-21 — 소유자가 사례를 찾아 정하라고 맡겼다)** — 세는 단위는 **계정**이다(IP 가 아니다 — 공격자는 IP 를 바꾼다. OWASP Authentication Cheat Sheet). **15분 안에 5번 틀리면 잠그고, 그 뒤로 틀릴 때마다 잠금이 두 배**가 된다(1분 → 2분 → 4분 → 8분 → 최대 15분. OWASP 의 exponential lockout). 잠긴 동안은 비밀번호가 맞아도 **429 `TOO_MANY_LOGIN_ATTEMPTS`** + `Retry-After`(초)다.
  로그인에 성공하면 횟수를 지운다. **영구 잠금은 없다** — 비밀번호를 되찾는 길(이메일)이 없어서 남이 내 계정을 영영 잠글 수 있게 된다(OWASP 가 경고하는 잠금 DoS). 이 방식이면 한 계정에 하루 100번쯤만 시도할 수 있다(NIST SP 800-63B §3.2.2 의 상한 100 과 같은 크기다).
  횟수는 Redis 에 둔다(**`qm:auth:login-fail:{loginId}`** — 세는 열쇠는 **로그인 아이디**다. 사용자 번호는 로그인에 성공해야 비로소 알게 되는 값이고, 없는 아이디도 세야 한다. 15분 뒤 저절로 사라진다 — 앱은 stateless 다). **없는 아이디에도 똑같이 센다**(잠기는지로 아이디의 존재가 새지 않게). 형식이 틀린 아이디는 세지 않는다. **Redis 가 죽으면 제한 없이 통과시킨다** — 로그인이 Redis 에 묶이지 않게 한다. IP 단위의 제한은 앱이 아니라 앞단(CloudFront/WAF 의 rate-based rule)의 일로 둔다.
- **중복 가입은 DB 가 막는다** — `조회 → 판단 → 삽입`이 아니라 INSERT 의 제약 위반을 409 로 옮긴다(`CLAUDE.md` §5).
- **`game`** — `LOL` · `VALORANT` · `PUBG`. 게임마다 계정 하나(`UNIQUE (user_id, game)`). **`tier`** 는 자기신고 문자열(`^[A-Z0-9_]{1,20}$`, 없어도 된다 — 안 적을 수 있다). **값이 있으면 그 게임의 티어 사다리에 있는 이름이어야 한다**(2026-09-24 소유자 결정 — 위 "gameconfig 를 읽는 것". 없는 이름 · 다른 게임의 티어는 400 `VALIDATION_FAILED`).
  형식 `@Pattern` 은 사다리 검사보다 넓지만 **남겨 두었다** — Redis 를 못 읽어 검증을 건너뛸 때(fail-open) DB 칸(`varchar(20)`)에 들어갈 수 없는 값을 막는 것이 이것뿐이다(Claude 가 정한 세부).
  **`mainPosition`** — LOL 은 `TOP` `JUNGLE` `MID` `ADC` `SUPPORT`, VALORANT 는 `DUELIST` `INITIATOR` `CONTROLLER` `SENTINEL`(이름은 `matching` 의 `LolPosition` · `ValorantRole` 과 같다), PUBG 는 없다(`null` 만). 외부 API 연동은 하지 않는다.

### 게임 프로필 — 게임 계정 하나를 밖에 보여 주는 모양 (`users/me` · 목록의 카드가 같이 쓴다)

```json
{
  "game": "LOL", "gameNickname": "달콤한 인생#KR7", "verified": false,
  "tier": "EMERALD_4", "mainPosition": "MID", "server": null,
  "stats": null
}
```

- **`server`** — PUBG 만(`STEAM` · `KAKAO`). 다른 게임은 `null` 만 받는다. **`verified`** · **`stats`** 는 읽기 전용이다 — 요청 본문으로 바꿀 수 없다.
- **`stats` 는 게임사 API 에서 가져온 전적의 스냅숏이다**(`account.game_account_stats`). **LoL 은 채워진다**(2026-09-23 — 아래 "전적을 긁는 것". **갱신되는 때는 둘이다** — 게임 계정을 저장할 때와 **사용자가 전적 갱신을 누를 때**(`POST …/game-accounts/{game}/refresh`) — 2026-09-24. 언제 긁은 것인지는 `syncedAt` 이다).
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
- **미정(`CLAUDE.md` §7 "게임 계정 연동")** — VALORANT · PUBG 의 전적(VALORANT 의 전적 API 는 Riot 의 **별도 승인**이 필요하다), Riot(RSO) 인증으로 `verified` 를 켜는 법.
  **LoL 의 "가져오는 방법" 은 정해졌다 — 바로 아래.** **"주기" 는 없다** — 게임 계정을 저장할 때와 **사용자가 전적 갱신을 누를 때만** 긁고(2026-09-24), 스스로 주기적으로 갱신할지는 미정이다.

### 전적을 긁는 것 — Riot API · LoL 만 (2026-09-23 소유자 결정 · P-13. **2026-09-24 에 시점이 하나로 줄었다가 "전적 갱신" 이 붙어 둘이 됐다 — P-17**)

**긁는 시점은 둘이다.** ① 게임 계정을 연결 · 수정할 때(**커밋된 뒤에 비동기로** — Riot 을 한 번에 20여 회 부르는 데 수 초가 걸려서 그만큼 응답을 붙잡을 수 없다) ② **사용자가 전적 갱신을 누를 때**(2026-09-24 소유자 결정 — **이쪽은 동기다**).

| 시점 | 무엇을 보나 | 기다리나 |
|---|---|---|
| `PUT /api/v1/users/me/game-accounts/{game}` 성공 직후 | 방금 저장한 그 게임 계정 — **쿨타임 없이 무조건 긁는다**(닉네임이 바뀌었을 수 있어 옛 전적을 그대로 두면 안 된다) | **아니다** — 커밋 뒤 비동기. 응답의 `stats` 는 아직 예전 값(처음이면 `null`)이고 잠시 뒤에 채워진다 |
| **`POST /api/v1/users/me/game-accounts/{game}/refresh`** | 그 게임 계정 — **쿨타임(2분) 안이 아니면 신선도를 보지 않고 긁는다**("최근에 긁었어도 사용자가 원하면 긁는다") | **기다린다**(소유자 결정) — 다 긁은 뒤 **200 + 갱신된 게임 프로필**이다. 상한은 **30초** |

- **`POST /api/v1/posts` 는 긁지 않는다**(2026-09-24 소유자 결정 — 2026-09-23 에 정한 시점 하나를 되물렸다). **긁는 것이 비동기라 방금 쓴 글의 응답에 반영되지 않는데** 대가가 Riot 호출 21번(puuid · 소환사 · 리그 · 경기 id · 경기 20)이고, 개발용 키의 한도가 2분당 100회다 — 그 시점이 사 주는 것은 "몇 초 뒤에 남이 목록을 받을 때 조금 더 최신" 하나뿐이라 수지가 맞지 않는다.
  **`platform.riot.freshness`(30분)도 같이 없앴다** — 신선도를 보는 곳이 이 시점 하나였다. **되살리지 않는다** — 전적 갱신은 "최근에 긁었어도 원하면 긁는다"가 요점이고, 남용은 신선도가 아니라 **쿨타임**이 막는다.
- **감수하는 것 — 저절로 갱신되지는 않는다.** 오래 전에 연결하고 안 건드린 사람의 `stats` 는 **낡은 채로 남는다**(`syncedAt` 이 언제인지는 응답에 있다). 이제 갱신하는 길은 **전적 갱신을 누르는 것**(또는 게임 계정을 다시 저장하는 것)이다.
  **미정 — 주기적으로 스스로 갱신할지, 그렇다면 주기를 얼마로 둘지는 정해지지 않았다**(`CLAUDE.md` §7 "게임 계정 연동"). 정하기 전에 임의로 만들지 않는다. **전적 갱신은 사용자가 누르는 것이라 그 미정과 별개다.**

#### 전적 갱신 — `POST /api/v1/users/me/game-accounts/{game}/refresh` (2026-09-24 **소유자 결정** · P-17)

**로그인한 본인의 것만이다** — 경로에 사용자 번호가 없어 남의 게임 계정을 가리킬 길이 없다(`users/me` 아래 — 위 "계정"). `{game}` 의 표기는 다른 게임 계정 경로와 같다(`LOL` · `VALORANT` · `PUBG`).

| 항목 | 값 | 누가 정했나 |
|---|---|---|
| 경로 · 본문 | `POST …/game-accounts/{game}/refresh`. 본문 없음 | **소유자**(경로의 존재) |
| 성공 | **200 + 게임 프로필**(`PUT` 과 **같은 모양** — 프런트가 그대로 갈아 끼운다). 다 긁은 뒤에 **DB 에서 다시 읽어** 내려 준다 | **소유자**(동기 · 같은 응답) |
| 쿨타임 | **2분**(`platform.riot.refresh-cooldown`). 같은 게임 계정이 기준이다 | **소유자** |
| 상한 | **30초**(`platform.riot.refresh-timeout`). 넘으면 실패로 다룬다 | **소유자** |
| 쿨타임을 찍는 때 | **긁기를 시작할 때 — 실패해도 소모된다**(실패만 무제한으로 다시 할 수 있으면 Riot 한도를 그대로 태운다) | **소유자** |
| 429 의 에러 코드 · 쿨타임 키 | **`TOO_MANY_STATS_REFRESHES`**(로그인 실패 제한의 `TOO_MANY_LOGIN_ATTEMPTS` 와 결을 맞췄다) · **`qm:riot:refresh:{gameAccountId}`** | Claude |
| 그 밖의 에러 코드 | 404 `GAME_ACCOUNT_NOT_FOUND` · 409 `GAME_STATS_NOT_SUPPORTED` · 503 `GAME_STATS_UNAVAILABLE` | Claude |
| 30초를 재는 방법 | 전용 풀에 던지고 `Future.get(30초)` — 요청 스레드에서 긁으면 자를 수 없다 | Claude |

- **거절의 갈래.**
  - **429 `TOO_MANY_STATS_REFRESHES` + `Retry-After`(초)** — 쿨타임 안에 또 불렀다. **누가 이미 같은 계정을 긁고 있을 때도 같은 429 다**(소유자 결정 — 자물쇠 `qm:riot:sync:{gameAccountId}` 를 그대로 쓴다. 게임 계정을 저장한 직후에 누르면 이쪽이다). 그때 `Retry-After` 는 **방금 찍은 쿨타임 그대로**다 — 자물쇠가 풀리는 시각보다 늦지만 두 429 가 같은 말을 하게 뒀다(Claude 가 정한 세부).
  - **404 `GAME_ACCOUNT_NOT_FOUND`** — 그 게임 계정을 연결하지 않았다(긁는 사이에 연결을 끊은 경우도 같다). **쿨타임을 소모하지 않는다.**
  - **409 `GAME_STATS_NOT_SUPPORTED`** — **그 게임은 긁는 구현이 없다**(VALORANT · PUBG). **200 을 주면 거짓말이다** — 아무것도 갱신되지 않는다. 요청이 잘못된 것이 아니라 서버가 못 하는 것이라 400 이 아니다. **쿨타임을 소모하지 않는다.**
  - **503 `GAME_STATS_UNAVAILABLE`** — 지금 가져올 수 없다. **전적 줄은 건드리지 않는다**(옛 값이 남는다 — `PUT` 쪽 실패와 같은 원칙). **이유를 가르지 않는다** — Riot 이 4xx/5xx 로 거절 · 응답이 없다 · **30초를 넘겼다** · **`RIOT_API_KEY` 가 없다** · **게임 닉네임이 `이름#태그` 가 아니어서 물어볼 수도 없다** · 전용 풀에 자리가 없다가 전부 이것이다. 사용자가 할 수 있는 것은 "잠시 뒤 다시" 또는 "게임 닉네임을 고친다" 둘뿐이라 갈래마다 코드를 두지 않았다(Claude 가 정했다). 키가 없는 것과 구현이 없는 것은 **쿨타임을 소모하지 않는다.**
- **거르는 순서** — ① 그 게임을 긁을 수 있나(409) ② 그 게임 계정이 있나(404) ③ 키가 있나(503) ④ 쿨타임(429) ⑤ 긁는다. **게임사 API 를 부르기 전에, 그리고 쿨타임을 찍기 전에** 거절할 것을 다 거절한다(Claude 가 정한 세부 — ③이 ②보다 뒤인 것은 없는 계정에는 404 가 더 쓸모 있어서다).
- **30초를 넘겼을 때 뒤에서 돌던 갱신은 자르지 않는다** — 잠시 뒤에 끝나면 전적은 갱신되고 다음 조회에서 보인다(자물쇠가 그동안 중복을 막는다). **요청만 끊는다.**
- **Redis 가 죽으면 쿨타임 없이 통과시킨다** — 로그인 실패 제한("Redis 가 죽으면 제한 없이 통과시킨다" — 위 "계정")과 같은 원칙이고, 자물쇠도 Redis 에 묻지 못하면 락 없이 진행한다. 그동안은 같은 계정을 여러 번 긁을 수 있다(저장이 upsert 라 결과는 같다).
- **쿨타임 키는 자물쇠 키와 다른 키다 — 뜻이 다르다.** 자물쇠(`qm:riot:sync:*` · 60초)는 "지금 돌고 있다"이고 쿨타임(`qm:riot:refresh:*` · 2분)은 "최근에 했다"다. 하나로 합치면 갱신이 끝나 자물쇠가 풀리는 순간 다시 누를 수 있게 된다. 접두사 `qm:riot:*` 는 둘 다 이 앱의 것이다.
- **`PUT` 쪽은 바뀌지 않았다** — 거기는 지금도 쿨타임 없이 무조건 긁고 응답을 기다리지 않는다.
- **실패해도 게임 계정 저장은 성공이다** — 키가 없거나 · 닉네임에 태그가 없거나 · Riot 이 4xx/5xx 를 주거나 · 타임아웃이면 **로그만 남기고 조용히 끝낸다.** **기존 `stats` 줄을 지우지 않는다**(옛 값이라도 있는 편이 낫다). 알림 발행과 같은 원칙이다(`CLAUDE.md` §3.2).
- **429(rate limit)는 재시도하지 않는다** — 개발용 키는 2분당 100회라 되풀이해도 소용이 없다. 그 자리에서 포기하고 다음 갱신 때 다시 긁는다.
- **LoL 만 긁는다.** VALORANT · PUBG 계정을 연결해도 아무것도 하지 않는다 — 게임별 구현은 인터페이스(`account.stats.GameStatsProvider`) 뒤에 있어 게임이 늘면 구현 하나를 더한다.
- **부르는 것** — `account-v1`(Riot ID → `puuid`) · `summoner-v4`(`puuid` → 소환사) · `league-v4`(**`RANKED_SOLO_5x5` 줄**) · `match-v5`(최근 경기 id, 경기 하나마다 한 번). 키는 헤더 **`X-Riot-Token`** 으로 보낸다.
  **`gameNickname` 은 `이름#태그` 여야 한다** — `#` 이 없으면 긁지 않는다(경고 로그).
- **채우는 값** — `games` 는 실제로 읽은 경기 수(`match-count` 이하), `avgKills` · `avgDeaths` · `avgAssists` 는 그 경기들의 평균(소수 첫째 자리),
  **`wins` · `losses` 는 솔로랭크의 시즌 누적**(읽은 경기의 승패가 아니다. 솔로랭크 줄이 없으면 **둘 다 `null`**), `winStreak` 은 가장 최근 경기부터의 연승(최근 경기가 패면 0, 경기를 하나도 못 읽었으면 `null`), `source` 는 `API`.
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

**로그인 아이디는 여전히 "가입할 때 정하는 것"이다**(`CLAUDE.md` §3.5 — 사용자 번호는 그때 DB 가 매긴다). 소셜로 **처음** 들어온 사람은 **로그인 아이디와 닉네임을 정하는 한 단계**를 거치고, 두 번째부터는 바로 로그인된다.
소셜로만 가입한 사람은 비밀번호가 없다(`account.credentials` 줄이 없다 — `hasPassword: false`).

| 요청 | 하는 일 | 결과 |
|---|---|---|
| `GET /api/v1/auth/oauth/{provider}/start` (`kakao` · `discord`) | `state`(무작위)를 쿠키 `qm_oauth_state`(`HttpOnly` · `SameSite=Lax` · `Path=/api/v1/auth/oauth` · 10분)에 넣고 제공자의 동의 화면으로 보낸다 | 302. 그 제공자가 설정돼 있지 않으면(클라이언트 id 가 비었다) 404 `OAUTH_PROVIDER_NOT_CONFIGURED` |
| `GET /api/v1/auth/oauth/{provider}/callback?code=…&state=…` | `state` 가 쿠키와 같은지 본다 → `code` 를 토큰으로 바꾼다 → 제공자 쪽 회원 번호를 얻는다 | **이미 연결된 사람** → `qm_access` 를 주고 `FRONT_BASE_URL` + `/` 로 302. **처음 온 사람** → 쿠키 `qm_social_signup`(아래)을 주고 `FRONT_BASE_URL` + `/signup/social` 로 302. **실패**(`state` 불일치 · 사용자가 거절 · 제공자 오류) → `FRONT_BASE_URL` + `/login?error=OAUTH_FAILED` 로 302 |
| `GET /api/v1/auth/social/pending` | 가입 화면이 미리 채울 값을 준다 | 200 `{provider, suggestedNickname}` · 401 `NO_PENDING_SOCIAL_SIGNUP` |
| `POST /api/v1/auth/social/signup` `{loginId, nickname}` | 사용자와 소셜 연결을 **한 트랜잭션으로** 만든다. **곧바로 로그인시킨다**(`qm_access`) — 비밀번호가 없어 따로 로그인할 길이 없다. `qm_social_signup` 은 지운다 | 201 `{userId, loginId, nickname}` · 409 `LOGIN_ID_TAKEN` · 409 `NICKNAME_TAKEN` · 409 `SOCIAL_ALREADY_LINKED` · 401 `NO_PENDING_SOCIAL_SIGNUP` · 400 |

- **`qm_social_signup`** — JWT(같은 키 · **`token_use` = `social_signup`** · 10분 · 클레임 `provider` · `provider_user_id` · `suggested_nickname`). `HttpOnly` · `SameSite=Lax` · `Path=/api/v1/auth/social`. 서버가 기억하는 것이 없다 — stateless 그대로다.
- **Spring 의 oauth2-client 를 쓰지 않는다** — 기본값이 인가 요청을 HTTP 세션에 넣는다(`CLAUDE.md` §5 "stateless"). 인가 코드 흐름을 `RestClient` 로 직접 짠다.
- **콜백은 "상태를 바꾸는 GET 을 만들지 않는다"의 유일한 예외다** — OAuth 가 GET 을 강제한다. `state` 검증이 그 자리를 지킨다.
- **제공자에게서 받는 것은 회원 번호와 닉네임뿐이다.** 이메일은 받지 않는다. 카카오 scope `profile_nickname`, 디스코드 scope `identify`.
  제공자 쪽 회원 번호는 `account.social_identities`(PK `(provider, provider_user_id)` · `user_id` 는 **사용자 번호**)에만 있고 **사용자 번호가 되지 않는다.**
- 설정(환경변수) — `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET`(카카오는 없어도 된다) · `DISCORD_CLIENT_ID` · `DISCORD_CLIENT_SECRET` · `OAUTH_REDIRECT_BASE_URL`(기본값 `http://localhost:8082` — 제공자에 등록하는 Redirect URI 는 이 값 + `/api/v1/auth/oauth/{provider}/callback`) · `FRONT_BASE_URL`(기본값 `http://localhost:5173`).
  제공자의 주소 셋(인가 · 토큰 · 사용자 정보)도 설정으로 받는다(기본값은 실제 주소. 테스트는 가짜 제공자 서버를 가리킨다).
- 모르는 제공자 이름의 `start` 는 404 `NOT_FOUND`(경로의 이름은 소문자 `kakao` · `discord` 만). `callback` 은 **무슨 일이 있어도 302** 다. `pending` 의 `provider` 는 대문자(`KAKAO`)이고 `suggestedNickname` 은 `null` 일 수 있다(16자로 자른 값).
- **`state` 는 서버에서 일회용이 아니다**(stateless 라 쿠키를 지우는 것까지만 한다) — 제공자의 `code` 가 일회용이라 감수한다.
- **하지 않은 것** — 이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기, 소셜 가입자가 비밀번호를 만드는 것.

## 차단 — `blocks`

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/blocks` | `{userId}`(차단할 사람의 **사용자 번호**) | 201 `{userId, nickname, createdAt}` | 409 `ALREADY_BLOCKED` · 400 `CANNOT_BLOCK_SELF` · 404 `USER_NOT_FOUND` |
| `DELETE /api/v1/blocks/{userId}` | — | 204 (차단한 적 없어도 204) | — |
| `GET /api/v1/blocks` | — | 200 `{blocks: [{userId, nickname, createdAt}]}` — **내가 차단한 사람만.** 나를 차단한 사람은 보여 주지 않는다 | — |

- 목록은 새로 차단한 사람이 먼저다. `POST` 의 `userId` 가 **숫자가 아니어도 404 `USER_NOT_FOUND`** 다(위 "공통" — 있을 수 없는 사용자다).
- 테이블은 `matching` 이 읽는 모양 그대로다(`CLAUDE.md` §3.5). `UNIQUE (blocker_id, blocked_id)` 가 같은 사람 두 번 차단을 막는다.
- **`blocker_id` · `blocked_id` 가 `varchar(20)` 에서 `bigint` 가 됐다**(2026-09-22 소유자 결정). **`matching` 의 `block/Block.java` 는 아직 두 칸을 `String` 으로 읽는다 — 그 폴더에서 `Long` 으로 바꿔야 한다(아직 안 바꿨다).**
  바꾸기 전까지 `matching` 은 이 테이블을 읽다가 런타임에 깨진다. docs/11 D-4("`matching` 이 이미 `String` 으로 다룬다")를 개정하는 것이다 — P-11.
- **롤 · GRANT 는 없다** — 스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정). 앱 하나가 롤 하나로 붙고, `matching` 은 별도 롤 없이 `social.blocks` 를 읽는다. 스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다. docs/11 #17 의 "스키마별 DB 롤" 대목과 D-1 의 GRANT 를 개정하는 것이다 — `matching` 폴더에서 D-항목으로 남겨야 한다(아직 안 남겼다).
- 차단해도 이미 맺은 친구 관계 · 이미 같은 방에 있는 상태는 건드리지 않는다(미정 그대로 — `CLAUDE.md` §7.1).

## 모집 글 · 목록 · 입장권 — `posts`

**`roomId` 는 글의 id 다**(`party.recruit_posts.id` — **bigint identity.** DB 가 매긴다. 2026-09-22 소유자 결정 전에는 UUID 였다). 브라우저는 글을 쓴 뒤 그 숫자로 `room` 의 방 만들기(`POST /api/v1/rooms/{roomId}`)를 부른다 — **`room` 은 `roomId` 를 문자열로 다루므로 방 키에는 숫자가 십진 문자열로 들어간다**(`qm:room:123:host`).
**자동 매칭 파티의 `roomId` 는 정해지지 않았다** — `matching` 은 `partyId` 를 UUID 문자열로 내려 주는데 `party.parties.id` 는 bigint 다. 그 값을 어디에 둘지는 **6단계(SQS 배선)에 닿을 때 묻는다**(`CLAUDE.md` §7 · §7.2 (나)). 예전에 적었던 "자동 매칭 파티는 `roomId = partyId` 다" 한 줄은 이 미정 위에 서 있다.

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/posts` | `{game, mode, title, description, voice, purpose, conditions, wantedPositions: []}` | 201 글 한 줄(아래) | 409 `ALREADY_RECRUITING`(모집 중인 글은 한 사람에 하나) · 400 |
| `PATCH /api/v1/posts/{postId}` | `{mode, title, description, voice, purpose, conditions, wantedPositions}` (준 것만 바꾼다) | 200 글 한 줄 | 403 `NOT_POST_HOST` · 409 `POST_NOT_RECRUITING` · **409 `ROOM_HAS_OTHER_MEMBERS`**(방에 방장 말고 누가 있다 — 아래) · **503 `ROOM_STATE_UNAVAILABLE`**(방 키를 못 읽었다) · 404 `POST_NOT_FOUND` |
| `DELETE /api/v1/posts/{postId}` | — | 204. **지우지 않고 "만료"로 바꾼다.** 이미 만료면 그대로 204 | 403 `NOT_POST_HOST` · 409 `POST_CONFIRMED` · 404 |
| `GET /api/v1/posts?game=LOL&limit=20&cursor=…` | — | 200 `{posts: [글 한 줄…], nextCursor: "…" 또는 null}`. `game` 이 없으면 세 게임 전부 | 400 `VALIDATION_FAILED`(`limit` 이 1~100 이 아니다 · 읽을 수 없는 `cursor`) |
| `GET /api/v1/posts/{postId}` | — | 200 글 한 줄 | 404 `POST_NOT_FOUND`(차단 관계로 숨겨진 글도 404 다) |
| `POST /api/v1/posts/{postId}/ticket` | — | 200 `{ticket, roomId, hostId, expiresAt}` | 404 `POST_NOT_FOUND`(없는 글 · **차단 관계로 숨겨진 글**) · 409 `POST_NOT_RECRUITING` |
| `POST /api/v1/posts/{postId}/confirm` | — | 200 글 한 줄(`status: CONFIRMED`, `members` = 파티원) | 409 `ROOM_NOT_CONFIRMED`(`room` 에 확정 표시 키가 없다) · 404 |

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
  **다시 둘 것인가는 미정이다** — 소유자가 "일단" 없앴다. **입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다**(`CLAUDE.md` §7.1 — 그러면 `room` 이 포지션을 들어야 하고 멤버 SET 을 HASH 로 바꿔야 해서 방 키 약속이 바뀐다).
- **목록의 정렬 — `id` 내림차순 하나(= 최신순)다**(2026-09-24 **소유자 결정** · P-14). **`id` 가 `bigint GENERATED ALWAYS AS IDENTITY` 라 넣은 순서대로 커지고, 겹치지 않고, 변하지 않는다** — 그래서 "들어온 순서" 가 곧 `id` 순서이고 tiebreaker 가 필요 없다.
  **글의 상태도 `createdAt` 도 정렬에 쓰지 않는다.** 그날 아침까지는 `(모집 중인가, createdAt desc, id)` 였다 — **왜 뒤집었는지는 아래 "목록의 페이지 나누기" 에 적었다**(정렬 키가 변하면 커서가 중복을 낸다).
  `createdAt` 컬럼과 응답의 `createdAt` 은 **그대로 있다** — 화면의 "몇 분 전" 이 그 값이다. 정렬과 커서에서만 안 쓴다.
  **만료 · 확정된 글은 그렇게 된 뒤 10분 동안만 목록에 남는다**(`status` 로 구분해 보여 준다. 멤버는 비운다). 만석인 방은 `full: true` 로 목록에 남는다.
  - **그래서 만료 · 확정된 글이 목록 위쪽에 섞여 나올 수 있다**(보존 기간 10분 안에는 제자리다 — 맨 아래로 내려가지 않는다). **감수하는 것이다** — 응답에 `status` 가 있으니 화면이 흐리게 그리거나 "모집 끝" 배지를 붙이면 된다.
    **덤으로 원래 하려던 것이 됐다** — 10분을 남기는 이유가 "방금까지 보이던 글이 갑자기 사라지면 혼란스럽다" 인데, 옛 정렬은 만료되는 순간 그 글을 맨 아래 묶음으로 내려 결국 사라진 것과 같게 만들었다.
  - **미정 — `status` 로 거르는 필터를 둘지**(지금은 없다. 필터는 `game` 하나다).
- **목록의 페이지 나누기 — 커서 방식**(2026-09-23 **소유자 결정** · P-14). 게시판은 `BOARD_CHANGED` 신호가 올 때마다 목록을 다시 받으므로 전부 내려 주면 그 큰 응답이 몇 초마다 되풀이된다.
  - **`limit`** — 한 페이지에 보여 줄 글의 수. 없으면 **20**, **최대 100**. 1 미만이거나 100 초과면 **400 `VALIDATION_FAILED`**(`details` 에 `limit`). 숫자가 아니면 형 변환에서 같은 400 이다. **상한으로 잘라 주지 않는다** — 조용히 100개를 주면 클라이언트가 "다 받았다"고 읽는다.
  - **`cursor`** — 없으면 맨 위부터. **불투명한 문자열이다** — 클라이언트는 뜯어보지 말고 받은 그대로 보낸다(속은 **글 번호 하나**를 base64url 로 적은 것이다. 숫자로 보이면 빼고 더해서 남의 페이지를 짐작하려 들기 때문에 한 겹 씌웠고, **서명하지는 않는다** — 숨길 것이 없고 위조해도 남의 글이 보이지 않는다. 차단 거르기는 페이지마다 다시 한다). 못 읽는 값이면 **400 `VALIDATION_FAILED`**(`details` 에 `cursor`) — 500 이 아니다. 빈 값은 안 준 것과 같다.
  - **커서가 담는 것은 글 번호 하나다**(2026-09-24 **소유자 결정** — P-14 의 개정이다. 정할 때는 **정렬에 쓰는 값 셋**(모집 중인가 · `createdAt` · `postId`)을 담았다).
    **왜 바꿨나 — 정렬 키가 변하면 커서가 중복을 낸다.** 커서 페이지 나누기는 정렬 키가 ① 순서대로 커지고 ② 겹치지 않고 ③ **변하지 않는다**는 전제 위에 선다. 그런데 글의 상태는 변하고 **그것도 목록 조회 자신이 바꾼다**(방장 키가 사라진 글을 그 자리에서 만료로, 확정 표시 키가 있는 글을 확정으로 옮겨 적는다 — 아래 `room_seen_at`).
    1쪽에 모집 중으로 나간 글이 그 사이 만료되면 2쪽의 조건("다음 묶음")에 **다시 걸려 같은 글이 두 번 보였다.**
    **`createdAt` 도 뺐다** — `id` 가 identity 라 위 셋을 혼자 다 만족하는데 `createdAt` 을 같이 쓰면 같은 뜻의 값을 둘로 들고 다니게 되고, 앱이 넣는 값(`createdAt`)과 DB 가 매기는 값(`id`)이라 동시에 들어온 둘에서 **서로 어긋날 수 있다.** 조건도 `id < ?` 한 줄로 줄었다.
    **옛 형식과의 호환은 두지 않았다**(프런트가 아직 없다) — 칸이 셋인 옛 커서가 오면 위와 같은 **400** 이다.
  - **`nextCursor`** — 더 볼 것이 있으면 값, 없으면 `null`. 다음이 있는지는 **보여 줄 것보다 한 개 더 읽어** 안다.
  - **`offset` 이 아닌 이유** — 1페이지를 보는 동안에도 글이 올라온다. `offset` 이면 그만큼 줄이 밀려 2페이지에 같은 글이 또 나오거나 사이의 글이 빠진다.
  - **신호(`BOARD_CHANGED`)를 받았을 때 프런트는 커서를 쓰지 않는다** — **펼친 만큼을 `limit` 으로 맨 위부터 다시 받는다**(`GET /api/v1/posts?game=LOL&limit=60`). 커서는 "더 보기"에만 쓴다.
  - **차단으로 모자라면 채운다** — 차단 거르기는 방 안에 누가 있는지를 Redis 에서 읽은 **뒤에** 하므로 `limit` 만큼 읽어도 보이는 것이 그보다 적을 수 있다. 그러면 **그 뒤를 더 읽어 채운다**(`platform.board.max-refills`, 기본 **3번**까지. 채우기 한 번이 목록 조립 한 벌이라 상한이 있다). 다 써도 모자라면 **있는 만큼**(빈 목록일 수도 있다) 내려 주고 `nextCursor` 로 이어 받게 한다.
  - **`nextCursor` 는 "마지막으로 읽은 줄"이다** — 마지막으로 **보여 준** 줄이 아니다. 숨겨진 글을 다음 페이지에서 또 읽지 않게 하려는 것이다.
  - **만료 · 확정 옮겨 적기는 읽은 글에만 걸린다**(아래 `room_seen_at`) — 이제 목록이 글 전부를 읽지 않으므로 **깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다.** 받아들인 대가다 — 입장권 발급도 그 글을 보고 같은 판단을 하므로 "목록에 안 보이지만 들어갈 수 있는 죽은 방"은 생기지 않는다.
  - **필터는 `game` 하나 그대로다.** 페이지는 정렬(위 — `id` 내림차순)을 자른 것뿐이다.
- **방에 방장 말고 누가 있으면 글을 고칠 수 없다**(2026-09-24 **소유자 결정** · P-19). 고치기(`PATCH`)만 막는다 — **409 `ROOM_HAS_OTHER_MEMBERS`**.
  - **왜** — 고칠 수 있는 칸에 `mode` · `voice` · `purpose` · `conditions` 가 있다. **`NO_VOICE` 를 보고 들어와 앉아 있는 사람 앞에서 `REQUIRED` 로 바꿀 수 있고**, "즐겜"(`FUN`)을 "승급"(`RANK_UP`)으로 바꿀 수 있다.
    그런데 **방 안 사람에게 바뀌었다고 알려 줄 길이 없다** — 게시판 채널 신호(`BOARD_CHANGED`)는 목록을 보는 사람에게 가고, 방 안 알림(`ROOM_*`)은 `room` 이 내는데 이 앱은 `room` 을 부르지 않는다(`CLAUDE.md` §3.3). 그래서 **아예 막는다.**
  - **어느 칸을 바꾸든 막는다** — `title` 하나만 고치는 것도 막는다. 칸마다 가르면 무엇을 잠글지가 또 미정이 되고 규칙이 복잡해진다.
  - **방이 아직 없으면**(방장 키가 없다 — 글만 써 두고 `room` 의 방 만들기를 아직 안 불렀다) **고칠 수 있다.** **방장 혼자 있을 때도 고칠 수 있다** — 바뀐 조건을 보고 들어온 사람이 없다.
  - **방 키를 못 읽으면 막는다 — 503 `ROOM_STATE_UNAVAILABLE`**(입장권 발급과 **같은 코드 · 같은 이유**다. 누가 방에 있는지 확인이 안 되는데 고치게 하면 이 규칙이 없는 것과 같다. 사유가 똑같아서 코드를 새로 두지 않았다).
  - **거르는 순서** — 없는 글 · 차단으로 숨겨진 글은 404, **`mode` 검증이 400, 방장이 아니면 403 `NOT_POST_HOST`, 모집 중이 아니면 409 `POST_NOT_RECRUITING`, 그 다음이 이 검사다.** 남의 글이나 끝난 글에 대고 "방에 사람이 있다"를 알려 주면 그 자체가 새는 정보다.
  - **방 키는 글의 줄을 잠그는 트랜잭션 밖에서 읽는다**(그 안에서 Redis 를 기다리면 DB 커넥션을 붙잡는다 — `party/service/PostService` 와 `PostStore` 를 나눈 이유다). **그래서 "읽은 뒤 저장하기 전"에 누가 들어오는 경쟁이 남는다** —
    그 창은 짧고 막으려는 것이 "사람이 있는데 조건이 바뀌는 것" 이라 **감수한다**(방장 · 상태 검사는 잠금 안에서 한 번 더 한다 — 그쪽이 최종이다).
  - **`DELETE`(만료로 바꾸기) · 입장권 발급 · 방장 확정의 기록 · 목록 · 단건 조회는 바뀌지 않았다** — 방에 사람이 있어도 그대로 된다. **방 안 사람에게 글이 바뀐 것(또는 지워진 것)을 알릴지는 미정이다**(`PARTY_*` 알림의 이름과 `payload` 가 통째로 미정이다 — `CLAUDE.md` §7).
- **차단 거르기** — 방 안(멤버 SET)에 나와 차단 관계(어느 방향이든)인 사람이 한 명이라도 있으면 **그 글을 목록에서 빼고 입장권도 내주지 않는다**(`CLAUDE.md` §7.1 · D-20). 방이 아직 없는 글은 방장과의 사이를 본다.
- **Redis 를 못 읽으면** — 목록 · 단건은 방 정보를 비운 채 글만 내려 주고 **만료 판정을 하지 않는다**(못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다). 입장권은 **503 `ROOM_STATE_UNAVAILABLE`**(차단 대조를 못 했는데 내줄 수 없다).
- **`postId` · `hostId` · 카드의 `userId` 는 전부 숫자다**(JSON 숫자로 나간다 — 문자열이 아니다).
- 이 앱에 가입하지 않은 **사용자 번호**가 방 안에 있으면(`room` 이 아직 인증 없이 돈다) 카드는 `nickname: null` · `profile: null` 로 나간다 — `memberCount` 와 어긋나지 않게 빼지 않는다.
  **숫자가 아닌 값은 다르다** — 사용자 번호일 수 없어 **방 키를 읽는 자리에서 건너뛴다**(WARN 한 줄. `party/room/RedisRoomStateReader`). 카드에도 파티원 기록에도 들지 않고 **`memberCount` 에서도 빠진다.**
- **방 키 읽기 — 읽기만 한다.** `qm:room:{roomId}:host`(`EXISTS`) · `:members`(`SMEMBERS`) · `:confirmed`(`EXISTS`). 접두사의 원본은 `room` 의 `RoomKeys` 다.
- **"아직 안 만들어진 방"과 "사라진 방"을 가르는 법 — `room_seen_at`.** 방장 키를 **처음 본 순간** 글에 `room_seen_at` 을 적는다. **`room_seen_at` 이 있는데 방장 키가 없으면** 방이 사라진 것이다 → 글을 만료로 바꾼다.
  `room_seen_at` 이 없는 글(방 만들기를 아직 안 부른 글)은 **쓴 지 10분(`room` 의 방 수명과 같다)이 지나도록 방이 안 생겼을 때만** 만료시킨다.
- **확정된 글은 방장 키가 없어도 만료시키지 않는다**(D-23 — 확정한 방은 방장 키만 잠깐 없을 수 있다). 확정된 글은 끝까지 `CONFIRMED` 다.
- **글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정되면 게시판 채널 `qm:pubsub:board` 에 `BOARD_CHANGED`(`payload` 는 `{}`)를 발행한다**(`CLAUDE.md` §3.2). 발행은 트랜잭션이 커밋된 뒤에 하고, 실패해도 본 작업을 뒤집지 않는다.

### 입장권 (JWT · RS256 — access 토큰과 같은 키)

| 클레임 | 값 |
|---|---|
| `iss` · `iat` · `jti` | access 토큰과 같다 |
| `sub` | 입장하려는 사람의 `userId`(사용자 번호를 **십진 문자열로** — access 토큰과 같은 꼴이다) |
| `token_use` | **`room_ticket`** |
| `room_id` | 글의 id — **숫자를 문자열로** 찍는다(`"123"`). `room` 이 주소의 `{roomId}` 와 글자 그대로 맞춰 본다 |
| `host_id` | 글을 쓴 사람의 사용자 번호 — 역시 **숫자를 문자열로**. `room` 은 방 만들기를 부른 사람이 이 값과 같은지 본다 |
| `exp` | 발급 후 **60초**(`ROOM_TICKET_TTL`, 기본값 `PT60S`). 받은 즉시 쓰는 것이라 짧다 — 입장권을 받은 뒤 들어오기 전의 차단 경쟁(D-20 미정)의 창도 이만큼으로 줄어든다 |

- **클레임 셋은 문자열이고 응답 본문(`{ticket, roomId, hostId, expiresAt}`)의 `roomId` · `hostId` 는 숫자다** — JWT 의 클레임은 문자열이 자연스럽고, 브라우저가 `room` 의 주소에 넣는 값도 문자열이라 그렇게 맞췄다(Claude 가 정한 세부다).
- 방장도 같은 요청으로 입장권을 받는다(`sub` = `host_id`). **`room` 의 검증은 아직 없다**(`TEMP-NO-PLATFORM`) — 붙이는 것은 `room` 폴더의 일이다.
- 강퇴당한 사람의 재입장은 입장권으로 막지 않는다(`room` 쪽 미정 그대로).

### 방장 확정의 기록 (`CLAUDE.md` §7.2 (가)의 방향을 받았다)

- 확정 요청 자체는 `room` 이 받는다. **브라우저는 `room` 의 확정이 성공한 뒤 이 앱의 `POST …/confirm` 을 부른다**(길 ①). 이 앱은 그 말을 믿지 않고 **확정 표시 키와 멤버 SET 을 읽어** 검증한 뒤 기록한다. 부르는 사람은 로그인한 누구든 된다 — 읽은 것만 기록하기 때문이다.
- **길 ②** — 목록 · 단건 조회 · 입장권 발급에서 방 키를 읽다가 **"DB 에는 모집 중인데 확정 표시 키가 있는 글"**을 보면 그 자리에서 같은 기록을 한다.
- **기록** — 글을 `CONFIRMED` 로, `party.parties`(`source` = `BOARD` · **`post_id` 가 글의 id(= `roomId`)**. `id` 는 DB 가 매기는 파티 자신의 번호다)와 `party.party_members`(그 순간 멤버 SET 의 전원)를 만든다.
  **멱등을 지키는 것은 `UNIQUE (post_id)` 다**(2026-09-22 — PK 가 `roomId` 였던 때는 PK 가 지켰다). 글을 `CONFIRMED` 로 바꾸는 조건부 UPDATE 가 한 호출만 통과시키고, 파티 · 파티원의 INSERT 도 `ON CONFLICT (post_id) DO NOTHING` · `ON CONFLICT DO NOTHING` 이다 — **두 길이 동시에 와도 파티는 하나, 파티원은 한 벌이다.**
  파티원의 `is_host` 는 `room` 의 방장 키 값이 아니라 **글의 `hostId`** 로 정한다(확정한 방은 방장이 바뀔 수 있다 — D-23).
- 읽는 시점의 멤버 SET 은 확정한 그 순간과 다를 수 있다(확정 뒤에 나간 사람이 빠진다) — **감수한다.** 확정 직후에 ①이 오므로 창이 짧다.

### 구현하며 채운 빈 곳 (2026-09-21)

- `PATCH` — `null` 이나 없는 칸은 그대로 둔다. `description` 은 빈 문자열이면 비우고 `wantedPositions` 는 `[]` 면 비운다. `title` 을 비우는 것은 400 이다.
  **`mode` 를 비우는 길은 없어졌다**(2026-09-24 — 모든 글이 모드 하나를 갖는다). `null` 은 그대로 두고 **빈 문자열은 400** 이다 — 바꾸려면 그 게임의 다른 모드 이름을 준다.
- 만료된 글에 `confirm` → 409 `POST_NOT_RECRUITING`. 입장권 · `confirm` 에서 방 키를 못 읽으면 503 `ROOM_STATE_UNAVAILABLE`.
- 확정 · 만료된 글은 목록 · 단건에서 `members` 가 비고 `memberCount` 가 0 이다. **`POST …/confirm` 의 응답만** DB 에 기록한 파티원을 싣는다.
- 입장권은 숨김(404)을 상태(409)보다 먼저 본다 — 차단 관계인 사람에게는 "모집이 끝났다"도 알려 주지 않는다. 남의 글을 `PATCH` · `DELETE` 하면 차단 관계여도 403 `NOT_POST_HOST` 다.
- 멤버 SET 에 **사용자 번호로 팔 수 없는 값**(숫자가 아닌 문자열 · 0 이하)이 있으면 방 키를 읽는 자리에서 **건너뛴다**(`room` 이 아직 인증 없이 돌아 아무 문자열이나 들어올 수 있다) — 카드 · 파티원 · `memberCount` 어디에도 들지 않는다. **숫자이지만 가입하지 않은 번호는 그대로 둔다**(카드에 `nickname: null` 로 남고 파티원으로도 기록된다).
- `wantedPositions` 는 늘 그 게임의 정해진 순서(TOP · JUNGLE · MID …)로 나간다.
- 로그인 실패의 잠금은 별도 키 `qm:auth:login-lock:{loginId}`(수명 = 잠금 길이)에 둔다. 잠글 때 횟수 키의 수명을 "잠금 + 15분"으로 늘린다 — 안 늘리면 잠금이 끝날 때 횟수도 사라져 잠금이 1분으로 되돌아간다.
- 설정 — `platform.board.room-grace` · `closed-retention`(둘 다 `PT10M`) · `room-ticket-ttl`(`ROOM_TICKET_TTL`) · `max-refills`(3 — 목록의 차단 채우기) · `platform.auth.login-throttle.*`. 목록의 페이지 크기(기본 20 · 상한 100)는 코드의 상수다(`party/service/BoardProperties`).

## 친구 · 신고 · 최근 함께한 사람 — `friend-requests` · `friends` · `reports` · `recent-players`

| 요청 | 본문 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/friend-requests` | `{userId}`(상대의 **사용자 번호**) | 201 친구 요청 한 줄 | 409 `FRIEND_REQUEST_ALREADY_SENT` · 409 `FRIEND_REQUEST_ALREADY_RECEIVED`(상대가 이미 나에게 보냈다 — 그것을 수락하면 된다) · 409 `ALREADY_FRIENDS` · 400 `CANNOT_FRIEND_SELF` · 404 `USER_NOT_FOUND`(없는 사용자 · **어느 방향이든 차단 관계** — 차단당한 사실이 새지 않게 같은 404 다) |
| `GET /api/v1/friend-requests?direction=received` (`received` 기본 · `sent`) | — | 200 `{requests: [친구 요청 한 줄…]}` — **대기 중인 것만**, 새것이 먼저 | 400 |
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
- **최근 함께한 사람은 읽는 쪽만 있다.** 채우는 것은 파티가 닫힐 때(`PartyClosed.fifo` — `CLAUDE.md` §3.4)인데 **SQS 배선이 미정이라 아직 아무도 채우지 않는다.** 지금은 늘 빈 목록이다.

- 구현하며 채운 빈 곳 — `direction` 은 `received` · `sent` 만(그 밖은 400). `reason` 은 대문자 그대로만 받고, 공백뿐인 `detail` 은 없는 것으로 친다. **신고는 차단 관계를 보지 않는다**(나를 차단한 사람도 신고할 수 있어야 한다). 친구 목록의 닉네임순은 대소문자를 가리지 않고 같으면 사용자 번호순이다. 닉네임을 찾지 못한 줄은 목록에서 뺀다. 수락 응답의 `since` 는 DB 에서 읽은 값이다.
- **`social.friendships` 는 두 사용자 번호를 (작은 쪽, 큰 쪽)으로 정규화한 한 줄이다.** "작은 쪽"은 앱(자바의 `Long` 비교)이 고르고 DB 의 `CHECK (user_low_id < user_high_id)` 가 그것을 지킨다 — **숫자라 어디서 비교해도 같다.**
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
- `PARTY_*` 는 아직 내지 않는다 — 자동 매칭 파티가 없다.

## 원본에 올려야 할 것

| # | 무엇 | 비고 |
|---|---|---|
| P-1 | 위 엔드포인트 전부(경로 · 스키마 · 에러 코드) | 원본 `openapi.yaml` 에 platform 엔드포인트가 이미 있으면 **그쪽과 맞춰야 한다** — 이 컴퓨터에서는 볼 수 없었다 |
| P-2 | access 토큰의 클레임 · `token_use` · 쿠키 이름 `qm_access` | docs/11 에 D-항목으로도 남겨야 한다(`CLAUDE.md` §5.1) |
| P-3 | 입장권의 형식 | `room` 의 계약(`room-api.md`)에도 걸린다 |
| P-4 | `roomId` = 글의 id(**숫자** — P-11 로 UUID 에서 바뀌었다) | 어느 문서에도 없던 것이다(`START_HERE.md` §4 E-2) |
| P-5 | `room_seen_at` 으로 "아직 안 만들어진 방"을 가르는 법, 만료 · 확정 글의 10분 보존 | `CLAUDE.md` §7.1 의 미정을 여기서 정했다 |
| P-6 | 방장 확정을 길 둘로 기록하는 것 | `CLAUDE.md` §7.2 (가) — 검토만 했던 방향을 받았다 |
| P-7 | 소셜 로그인(카카오 · 디스코드)과 "처음 오면 로그인 아이디를 정한다" | docs/00 의 계정 정의에 걸린다. 본 저장소의 v2 설계(Discord 로그인)와는 다른 모양이다 |
| P-12 | **전적 스냅숏은 세 게임이 한 테이블** — 판 수(`games`)만 공통 컬럼이고 승/패 · 연승 · 어시스트는 게임에 따라 비는 칸이다(소유자 결정 2026-09-22). PUBG 에 승/패를 우겨 넣지 않는다 | 게임마다 테이블을 나누는 안과 전부 jsonb 로 두는 안을 버렸다 |
| P-9 | 친구 · 신고 · 최근 함께한 사람의 경로와 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` | 원본 `events.md` 의 `FRIEND_*` 이름과 맞춰야 한다 — 이 컴퓨터에는 이름이 없었다 |
| P-10 | 로그인 실패 제한(429 `TOO_MANY_LOGIN_ATTEMPTS`) | |
| P-8 | 게임 프로필 · 전적 스냅숏 · 글의 `voice` · `purpose` · `conditions` | 전적을 가져오는 법은 미정이다(`CLAUDE.md` §7 "게임 계정 연동") |
| P-17 | **전적 갱신 요청**(2026-09-24 **소유자 결정** — 위 "전적을 긁는 것" 의 "전적 갱신"). **`POST /api/v1/users/me/game-accounts/{game}/refresh`** · **동기다**(다 긁을 때까지 기다린다) · 성공은 **200 + `PUT` 과 같은 게임 프로필** · **쿨타임 2분**(429 + `Retry-After`. 긁기를 시작할 때 찍고 **실패해도 소모된다**) · **이미 긁고 있으면 같은 429**(자물쇠 `qm:riot:sync:*` 를 그대로 쓴다) · **상한 30초**(넘으면 실패) · 라이엇 실패 · 시간 초과는 **503 이고 전적 줄을 건드리지 않는다** | 소유자가 직접 정한 것이다 — docs/11 에 D-항목으로 남겨야 한다(**P-13 을 뒤집지 않는다 — 긁는 시점이 하나에서 둘로 늘어난 것이다**). **Claude 가 정한 것** — 에러 코드의 이름 넷(`TOO_MANY_STATS_REFRESHES` · `GAME_ACCOUNT_NOT_FOUND` · `GAME_STATS_NOT_SUPPORTED` · `GAME_STATS_UNAVAILABLE`) · 쿨타임 키 `qm:riot:refresh:{gameAccountId}`(자물쇠와 **다른 키**다) · **구현이 없는 게임(VALORANT · PUBG)은 409** · **키(`RIOT_API_KEY`)가 없으면 503** · 실패의 갈래를 503 하나로 합친 것(태그 없는 닉네임도 여기다) · 거르는 순서(409 → 404 → 503 → 429) · **30초를 전용 풀 + `Future.get` 으로 재고 큐가 꽉 찼으면 던지기 전에 503 으로 끊는 것** · Redis 가 죽으면 쿨타임 없이 통과시키는 것. **남은 것** — 전적의 **자동 갱신 주기**는 여전히 미정이다(이 요청은 사용자가 누르는 것이다) |
| P-13 | **전적 동기화(Riot API · LoL 만)** — **긁는 시점은 하나다(게임 계정을 연결 · 수정할 때. 2026-09-24 에 "전적 갱신" 이 붙어 둘이 됐다 — P-17)** · 비동기이고 실패해도 게임 계정 저장은 성공 · `external_id` 는 `puuid` · `verified` 는 켜지 않는다 · **평점은 넣지 않는다**(소유자 결정 2026-09-23. 위 "전적을 긁는 것") | **2026-09-23 에 정한 것의 절반을 2026-09-24 에 소유자가 되물렸다 — 새 번호를 두지 않고 이 항목을 개정한다.** 그날 정했던 두 번째 시점(**모집 글을 쓸 때** 긁는 것)과 **신선도 30분**(`platform.riot.freshness`)이 없어졌다 — 긁는 것이 비동기라 글 쓰기 응답에 반영되지 않는데 대가가 Riot 호출 21번이고, 신선도를 보는 곳이 그 시점 하나뿐이라 죽은 코드가 됐다. **그래서 전적은 게임 계정을 저장할 때만 갱신된다**(낡은 채로 남는 것을 감수한다. 자동 갱신 주기는 **미정**). VALORANT · PUBG 는 아직 없다(VALORANT 의 전적 API 는 Riot 의 별도 승인이 필요하다). Redis 락 키 `qm:riot:sync:{gameAccountId}` 가 늘었다 — 이 앱의 접두사다 |
| P-14 | **게시판 목록의 페이지 나누기(커서 방식)** — `limit`(기본 20 · 최대 100 · 벗어나면 400) · `cursor`(불투명 · 서명하지 않는다 · 못 읽으면 400) · `nextCursor`(**마지막으로 읽은 줄** 기준) · 차단으로 모자라면 최대 3번 더 읽어 채우는 것 · 신호가 왔을 때는 커서 없이 맨 위부터 `limit` 만큼 다시 받는 것 (소유자 결정 2026-09-23. 위 "목록의 페이지 나누기"). **2026-09-24 에 정렬과 커서가 `id` 하나가 됐다**(소유자 결정 — 위 "목록의 정렬") | **2026-09-23 에 정한 것의 일부를 2026-09-24 에 소유자가 고쳤다 — 새 번호를 두지 않고 이 항목을 개정한다.** 정렬이 `(모집 중인가, createdAt desc, id)` 에서 **`id` 내림차순 하나(= 최신순)** 가 되고 커서가 **글 번호 하나**가 됐다. **왜 — 정렬 키가 변하면 커서가 중복을 낸다**(상태는 변하고 그것도 목록 조회 자신이 바꾼다. 1쪽에 모집 중으로 나간 글이 그 사이 만료돼 2쪽에 다시 걸렸다). `createdAt` 까지 뺀 것은 `id` 가 identity 라 순증가 · 유일 · 불변을 혼자 만족하고, 둘을 같이 쓰면 앱이 넣는 값과 DB 가 매기는 값이 어긋날 수 있어서다. **새로 감수하는 것 — 만료 · 확정된 글이 목록 위쪽에 섞여 나온다**(보존 기간 10분 안에는 제자리다. 응답의 `status` 로 화면이 가른다). `status` 필터를 둘지는 **미정**이다. 마이그레이션 `party/V7__board_order_index.sql`(`(game, id DESC)` 를 만들고 옛 `(game, status, created_at DESC)` 를 지운다) · 옛 커서와의 호환을 두지 않은 것(400)은 Claude 가 정한 세부다. 커서의 속 · 채우기의 상한 · `nextCursor` 를 "읽은 줄"로 잡은 것도 그렇다. **만료 · 확정 옮겨 적기가 읽은 글에만 걸리게 됐다** — 목록 깊은 곳의 글은 누가 그 페이지를 볼 때 만료된다 |
| P-15 | **refresh 토큰**(2026-09-23 **소유자 결정**) — 불투명 UUID · Redis `qm:auth:refresh:{uuid}` → 사용자 번호 · `P7D` · 쿠키 `qm_refresh`(`Path=/api/v1/auth/refresh`) · `POST /api/v1/auth/refresh` · **rotation 필수**(`GETDEL` 한 번) · 실패는 전부 같은 401 `INVALID_REFRESH_TOKEN` · 로그아웃이 Redis 의 줄과 쿠키 둘을 지운다 · **access 가 `PT15M` 으로 줄고 `TEMP-NO-REFRESH` 가 없어졌다**(위 "refresh 토큰") | 소유자가 직접 정한 것이다 — docs/11 에 D-항목으로 남겨야 한다(`CLAUDE.md` §5.1 (라) · (마)가 설계로만 적어 둔 것이 구현됐다. #16 의 access denylist 개정과 같은 묶음이다). **옆 서비스에는 걸리지 않는다** — 서명 · 검증이 달라지지 않고 access 의 수명만 짧아진다(`matching` · `notification` · `room` 은 공개 키로 검증만 한다). 넣지 않은 것 — 탈취 감지(토큰 계보 추적) · 기기 수 제한 · 한 사용자의 refresh 를 한꺼번에 끊는 길 |
| P-16 | **`mode` 와 `tier` 의 값을 gameconfig(Redis)에서 읽어 검증하는 것**(2026-09-24 **소유자 결정** — 위 "gameconfig 를 읽는 것"). 읽는 키 둘(`qm:gameconfig:{GAME}:{MODE}` 의 `EXISTS` · `:tier` 의 `ZSCORE`) · **Redis 를 못 읽으면 통과시킨다(fail-open)** · **`mode` 가 필수가 됐다**(`PATCH` 에서 빈 문자열로 비우는 길이 없어졌다) · `tier` 는 값이 있을 때만 본다 | 소유자가 직접 정한 것이다 — **`CLAUDE.md` §2 · §11 의 "`qm:gameconfig:*` 접근 — 예외가 없다"와 docs/11 #15 를 개정한다.** `matching` 폴더에서 docs/11 에 D-항목으로 남겨야 한다(아직 안 남겼다). **미정 — `party.recruit_posts.mode` 를 `NOT NULL` 로 조일지, 옛 글의 빈 `mode` 를 어떻게 할지**(값의 목록이 Redis 에 있어 DB 가 강제할 수 없는 종류다 — 마이그레이션을 새로 만들지 않았다). Claude 가 정한 것 — 클래스의 자리(`common/gameconfig/GameConfigKeys` · `GameConfigReader`) · 안 심긴 gameconfig 도 통과시키는 것과 그것을 티어 사다리 키로 가르는 것 · 에러의 글귀 · `tier` 의 `@Pattern` 을 남긴 것 |
| P-11 | **모든 테이블의 PK 는 `bigint GENERATED ALWAYS AS IDENTITY` 이고, `userId` 는 사용자 번호다. 로그인 아이디는 `loginId` 로 따로 둔다**(2026-09-22 **소유자 결정** — 2026-09-19 의 "사용자 id 는 가입할 때 정한 로그인 아이디(문자열)"를 개정한다. **docs/11 D-4 와 얽힌다**) | 다른 항목과 달리 **소유자가 직접 정한 것**이다 — docs/11 에 D-항목으로 남기는 것이 남았다. 걸리는 것 — ① **`matching` 의 `block/Block.java` 를 `Long` 으로 바꿔야 한다**(`blocker_id` · `blocked_id` 가 bigint 가 됐다. 아직 안 바꿨다 — 그 폴더의 일이다) ② Redis 채널 `qm:pubsub:push:{userId}` · `room` 의 방 키와 멤버 SET 의 `{userId}` · `{roomId}` 는 **숫자의 문자열**이 된다(`matching` · `notification` · `room` 은 그 값을 문자열로 다뤄 코드 변경이 없다) ③ 자동 매칭 파티(`source='MATCH'`)가 `matching` 의 UUID `partyId` 를 어디에 두는지는 **미정이다** — 6단계에서 정한다 |
| P-18 | **글 한 줄에서 `filledPositions` 를 없앤 것**(2026-09-24 **소유자 결정** — 위 "글 한 줄"). 응답에 그 칸이 없다. **글의 `wantedPositions` 와 카드의 `profile.mainPosition` 은 그대로다** | 소유자가 직접 정한 것이다 — **docs/11 D-20 의 ③("글의 '찾는 포지션' 가운데 이미 방 안에 있는 포지션의 강조")을 개정한다.** `matching` 폴더에서 docs/11 에 D-항목으로 남겨야 한다(아직 안 남겼다). **D-20 의 ①②④(인원 · 방 안 사람들의 카드 · F5 없이 갱신)는 그대로 유효하다.** 왜 — **주 포지션은 그 방에서 할 포지션이 아니라 틀린 정보였다**(위 "글 한 줄"). 테이블 · 컬럼은 바뀌지 않았다(마이그레이션 없음). **다시 둘 것인가는 미정이다** — 입장할 때 포지션을 고르게 하는 방식은 하지 않기로 이미 정해져 있다(`CLAUDE.md` §7.1) |
| P-19 | **방에 방장 말고 누가 있으면 모집 글을 고칠 수 없는 것**(2026-09-24 **소유자 결정** — 위 `PATCH` 의 규칙. **409 `ROOM_HAS_OTHER_MEMBERS`** · 방이 없거나 방장 혼자면 고쳐진다 · **방 키를 못 읽으면 503 `ROOM_STATE_UNAVAILABLE`**) | 소유자가 직접 정한 것이다 — docs/11 에 D-항목으로 남겨야 한다(**개정하는 D-항목은 없다** — D-11 · D-20 에 글 고치기 이야기가 없다. 새 규칙이다). 왜 — 고칠 수 있는 칸에 `mode` · `voice` · `purpose` · `conditions` 가 있는데 **방 안 사람에게 바뀌었다고 알려 줄 길이 없다**(게시판 신호는 목록을 보는 사람에게 가고 `ROOM_*` 은 `room` 이 낸다). **Claude 가 정한 것** — 에러 코드의 이름 · 503 을 입장권 발급과 같은 코드로 재사용한 것 · 검사 순서(400 → 403 → 409 `POST_NOT_RECRUITING` → 이 검사) · 방 키를 **잠금 밖에서** 읽어 "읽은 뒤 저장하기 전"의 경쟁을 감수한 것. **막는 것은 `PATCH` 하나다** — `DELETE` · 입장권 · 확정 기록 · 조회는 그대로다. **남은 것 — 방 안 사람에게 글이 바뀐 것을 알릴지는 미정이다**(`PARTY_*` 가 미정이다) |

표에 없지만 docs/11 에 D-항목으로 남겨야 하는 것 — **스키마별 DB 롤을 두지 않는 것**(2026-09-22 소유자 결정. 위 "차단"). 앱 하나가 롤 하나로 붙고 `matching` 은 별도 롤 없이 `social.blocks` 를 읽는다 — docs/11 #17 의 "스키마별 DB 롤" 대목과 D-1 의 GRANT 를 개정한다(아직 안 남겼다).
