# 14. API 요청·응답 실물 계약

`contracts/openapi.yaml`이 **정본**이다. 이 문서는 그 계약이 실제 HTTP에서
**어떤 헤더·어떤 바디로 오가는지**를 더미 데이터로 보여 준다.
프론트, 더미 서버, 백엔드 테스트는 전부 이 예시를 그대로 쓴다.

- 계약 정본: `contracts/openapi.yaml` (v2.0.0)
- 테스트 케이스: `docs/15_CRUD_TEST_CASES.md`
- 강제 장치: `backend/src/test/java/com/queuemate/api/` + `.github/workflows/ci.yml`

> **2026-09-05 정리** — v1 계약서와 구현이 13군데 어긋나 있었다. 전부 어느 쪽이 옳은지
> 정하고 한쪽으로 맞췄다. 결정 내역은 §11에 있다. 이제 세 곳(계약서·구현·더미 서버)이
> 같은 말을 하고, CI가 merge 전에 그걸 확인한다.

---

## 0. 공통 규약

### 0.1 Base URL

| 환경 | 값 |
|---|---|
| 로컬 | `http://localhost:8080/api/v1` |
| 문서 표기 | 이하 경로는 `/api/v1`을 생략한 상대 경로로 적는다 |

WebSocket만 `/ws`로 `/api/v1` 밖에 있다 (`contracts/events.md`).

### 0.2 공통 요청 헤더

| 헤더 | 필수 | 값 | 비고 |
|---|---|---|---|
| `Authorization` | 인증 필요 엔드포인트 전부 | `Bearer <accessToken>` | §0.4의 공개 엔드포인트만 예외 |
| `Content-Type` | 바디가 있는 요청 | `application/json;charset=UTF-8` | 없으면 415 |
| `Accept` | 선택 | `application/json` | 생략해도 JSON을 준다 |

**요청 헤더에 없는 것** — API 키, 커스텀 `X-` 헤더, 쿠키를 쓰지 않는다.
CSRF 토큰도 없다. `SecurityConfig`가 CSRF를 껐고 JWT는 쿠키에 담기지 않는다.

### 0.3 공통 응답 헤더

| 헤더 | 값 | 비고 |
|---|---|---|
| `Content-Type` | `application/json` | 204 응답에는 없다 |

**응답 헤더에 없는 것** — `Location`을 주지 않는다. 201 응답도 본문에 `id`를 실어
보내는 방식이고 `Location` 헤더는 붙지 않는다. 클라이언트는 헤더가 아니라 본문의
`id`를 읽어야 한다.

### 0.4 인증이 필요 없는 엔드포인트

`SecurityConfig`가 `permitAll`로 연 것만이다.

```
POST /api/v1/auth/signup
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout      (refresh token 자체가 자격 증명이다)
GET  /actuator/health
GET  /actuator/info
     /error
     /ws            (handshake에서 subprotocol로 따로 인증한다)
```

**그 외 전부 인증 필요.** 토큰이 없거나 깨졌으면 컨트롤러에 닿기 전에 401이고,
**본문은 다른 4xx와 같은 `{code: "UNAUTHORIZED", message}` 형태다.**
토큰은 유효하지만 권한이 없는 경우는 403이 아니라 **404**로 내려
존재 여부를 흘리지 않는다(제안·파티·예약).

### 0.5 직렬화 규칙

Spring Boot 기본 Jackson 설정을 그대로 쓴다. 커스텀 `ObjectMapper`가 없다.

| 항목 | 규칙 | 예시 |
|---|---|---|
| 필드명 | camelCase | `externalGameId` |
| enum | 대문자 문자열 | `"LOL"`, `"QUEUED"` |
| UUID | 하이픈 포함 소문자 문자열 | `"11111111-1111-4111-8111-111111111111"` |
| 날짜 | `OffsetDateTime` → ISO-8601 문자열 | `"2026-09-05T12:00:00Z"` |
| null 필드 | **응답에서 생략하지 않고 `null`로 실린다** | `"avatarUrl": null` |
| 숫자 | 따옴표 없는 JSON number | `"expiresIn": 900` |

날짜의 오프셋은 서버가 저장한 값을 그대로 낸다. `Z`와 `+09:00`이 섞여 나올 수 있으니
클라이언트는 오프셋 파싱을 반드시 해야 한다. 문자열 비교로 시각을 다루면 깨진다.

### 0.6 에러 응답 공통 형태

4xx·5xx는 전부 아래 두 필드다. `GlobalExceptionHandler`가 만든다.

```json
{
  "code": "ALREADY_BLOCKED",
  "message": "이미 차단한 사용자다"
}
```

| 필드 | 성질 | 클라이언트 취급 |
|---|---|---|
| `code` | 안정된 값. 바뀌지 않는다 | **분기 조건으로 써도 된다** |
| `message` | 사람이 읽는 한국어 설명. 바뀔 수 있다 | 로깅·표시용. 분기에 쓰면 안 된다 |

**예외가 없다.** 인증 필터가 막은 401도 컨트롤러가 만든 401과 같은 형태다.
클라이언트는 4xx를 **한 가지 방법으로만** 처리하면 된다.
실패 원인(토큰 없음·만료·위조)은 구분해 주지 않는다. 알려 주면 공격자에게 정보가 된다.

### 0.7 에러 코드 전수 카탈로그

구현에서 실제로 던지는 코드 전부다. 이 목록에 없는 `code`가 오면 그건 버그다.

| HTTP | code | 발생 지점 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation 위반, 그리고 매칭/예약/게임설정의 잘못된 조건 값 |
| 401 | `UNAUTHORIZED` | 자격 증명 실패, 토큰 무효, 인증 필터 차단 — **전부 같은 형태** |
| 404 | `USER_NOT_FOUND` | 대상 사용자가 없다 |
| 404 | `UNKNOWN_GAME` | 활성 모드가 없는 게임 |
| 404 | `UNKNOWN_GAME_MODE` | 그 게임에 없는 모드 |
| 404 | `MATCH_REQUEST_NOT_FOUND` | 내 매칭 요청이 아니거나 없다 |
| 404 | `PROPOSAL_NOT_FOUND` | 제안이 없거나 참가자가 아니다 |
| 404 | `PARTY_NOT_FOUND` | 파티가 없거나 멤버가 아니다 |
| 404 | `RESERVATION_NOT_FOUND` | 내 예약이 아니거나 없다 |
| 404 | `FRIENDSHIP_NOT_FOUND` | 친구가 아니다 |
| 404 | `BLOCK_NOT_FOUND` | 차단하지 않은 사용자다 |
| 404 | `GAME_ACCOUNT_NOT_FOUND` | 내 게임 계정이 아니거나 없다 |
| 404 | `FRIEND_REQUEST_NOT_FOUND` | 내 친구 요청이 아니거나 없다 |
| 409 | `EMAIL_ALREADY_IN_USE` | 가입 이메일 중복 |
| 409 | `NICKNAME_ALREADY_IN_USE` | 가입·수정 닉네임 중복 |
| 409 | `EMAIL_OR_NICKNAME_ALREADY_IN_USE` | 동시 가입 경합에서 DB 제약이 먼저 터진 경우 |
| 409 | `GAME_ACCOUNT_ALREADY_LINKED` | 이미 연결된 게임 계정 |
| 409 | `ACTIVE_MATCH_REQUEST_EXISTS` | 진행 중인 매칭 요청이 있다 |
| 409 | `MATCH_REQUEST_NOT_CANCELLABLE` | 제안 중이라 취소 불가 |
| 409 | `PROPOSAL_EXPIRED` | 제안 만료 |
| 409 | `PROPOSAL_NOT_PENDING` | 이미 확정·거절·취소된 제안 |
| 409 | `PROPOSAL_NOT_CONFIRMED` | 확정되지 않은 제안으로 파티를 만들려 함 |
| 409 | `PROPOSAL_NOT_FULLY_ACCEPTED` | 전원이 수락하지 않았다 |
| 409 | `PROPOSAL_MEMBER_MISMATCH` | 제안 멤버와 파티 멤버가 다르다 |
| 409 | `PARTY_SIZE_MISMATCH` | 파티 정원 불일치 |
| 409 | `PARTY_CLOSED` | 종료된 파티 |
| 409 | `PARTY_PLAYING` | 이미 게임이 시작된 파티 |
| 409 | `ALREADY_LEFT` | 이미 나갔거나 종료된 파티 |
| 409 | `BLOCKED_MEMBERS` | 차단 관계인 참가자가 섞였다 |
| 409 | `RESERVATION_NOT_EDITABLE` | PROPOSED 등 수정 불가 상태 |
| 409 | `RESERVATION_NOT_CANCELLABLE` | 취소 불가 상태 |
| 409 | `OVERLAPPING_RESERVATION` | 같은 시간대에 이미 예약이 있다 (INV-9) |
| 409 | `SELF_FRIEND_REQUEST` | 자기 자신에게 친구 요청 |
| 409 | `SELF_BLOCK` | 자기 자신을 차단 |
| 409 | `SELF_REPORT` | 자기 자신을 신고 |
| 409 | `ALREADY_FRIENDS` | 이미 친구 |
| 409 | `REQUEST_ALREADY_PENDING` | 내가 보낸 요청이 이미 대기 중이다 |
| 409 | `INVERSE_REQUEST_PENDING` | 상대가 보낸 요청이 대기 중이다 |
| 409 | `FRIEND_REQUEST_NOT_PENDING` | 대기 중이 아닌 요청에 응답 |
| 409 | `BLOCKED_RELATION` | 차단 관계라 친구 요청 불가 |
| 409 | `ALREADY_BLOCKED` | 이미 차단함 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type이 `application/json`이 아니다 |
| 429 | `SIGNUP_RATE_EXCEEDED` | IP 기준 가입 속도 제한 |
| 429 | `LOGIN_ATTEMPTS_EXCEEDED` | 이메일·IP 기준 로그인 시도 제한 |
| 503 | `MATCHING_UNAVAILABLE` | Redis 장애. 새 매칭은 fail-closed (INV-10) |

> 예약 중복 코드는 `RESERVATION_OVERLAP`이 아니라 **`OVERLAPPING_RESERVATION`**이다
> (`ReservationService.OVERLAP_CODE`). 매칭 중복은 `MatchRequestService.DUPLICATE_CODE`가
> `ACTIVE_MATCH_REQUEST_EXISTS`다. 둘 다 상수로 숨어 있으니 grep으로 찾으면 안 나온다.
> 이 목록은 `contracts/openapi.yaml`의 `ErrorResponse.code` enum과 같아야 하고,
> `CrossCuttingApiContractTest`의 TC-X-07이 그걸 강제한다.

### 0.8 고정 더미 데이터 (모든 예시가 이 값을 쓴다)

| 이름 | id | email | nickname |
|---|---|---|---|
| 알파(요청 주체) | `11111111-1111-4111-8111-111111111111` | `alpha@queuemate.dev` | `알파` |
| 브라보(상대) | `22222222-2222-4222-8222-222222222222` | `bravo@queuemate.dev` | `브라보` |
| 찰리(제3자) | `33333333-3333-4333-8333-333333333333` | `charlie@queuemate.dev` | `찰리` |

| 리소스 | id |
|---|---|
| 게임 계정 | `a0000000-0000-4000-8000-000000000001` |
| 매칭 요청 | `b0000000-0000-4000-8000-000000000001` |
| 제안 | `c0000000-0000-4000-8000-000000000001` |
| 파티 | `d0000000-0000-4000-8000-000000000001` |
| 예약 | `e0000000-0000-4000-8000-000000000001` |
| 친구 요청 | `f0000000-0000-4000-8000-000000000001` |
| 존재하지 않는 id (404 유도용) | `00000000-0000-4000-8000-000000000000` |

토큰 더미 — 길이만 줄인 형태다. 실제 JWT는 훨씬 길다.

```
ACCESS_A  = eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEifQ.SIG_A
ACCESS_B  = eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyMjIyMjIyMi0yMjIyLTQyMjItODIyMi0yMjIyMjIyMjIyMjIifQ.SIG_B
REFRESH_A = eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJ0eXAiOiJSRUZSRVNIIn0.SIG_R
```

기준 시각은 전부 `2026-09-05T12:00:00Z`로 고정한다.

---

## 1. Auth

### 1.1 `POST /auth/signup` — 가입

**요청**

```http
POST /api/v1/auth/signup HTTP/1.1
Host: localhost:8080
Content-Type: application/json;charset=UTF-8
```
```json
{
  "email": "alpha@queuemate.dev",
  "password": "Qm!passw0rd",
  "nickname": "알파"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `email` | string | ✅ | `@Email` 형식, 공백 불가 |
| `password` | string | ✅ | **최소 8자.** 상한 없음 |
| `nickname` | string | ✅ | 2\~16자 |

> `Authorization` 헤더를 **보내면 안 된다.** 공개 엔드포인트다.

**응답 201**

```http
HTTP/1.1 201 Created
Content-Type: application/json
```
```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "nickname": "알파",
  "avatarUrl": null
}
```

> **토큰을 주지 않는다.** 가입 직후 클라이언트는 `POST /auth/login`을 따로 호출해야 한다.
> 가입 응답에 이메일도 실리지 않는다.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `password`가 7자, `nickname`이 1자, `email`이 `not-an-email` |
| 409 | `EMAIL_ALREADY_IN_USE` | `alpha@queuemate.dev`로 두 번째 가입 |
| 409 | `NICKNAME_ALREADY_IN_USE` | 다른 이메일 + 닉네임 `알파` |
| 409 | `EMAIL_OR_NICKNAME_ALREADY_IN_USE` | 동시 요청이 DB 유니크 제약에서 충돌 |
| 429 | `SIGNUP_RATE_EXCEEDED` | 같은 IP에서 단시간·하루 한도 초과 |

```json
{ "code": "VALIDATION_FAILED", "message": "password: 크기가 8에서 2147483647 사이여야 합니다" }
```
```json
{ "code": "EMAIL_ALREADY_IN_USE", "message": "이미 사용 중인 이메일이다" }
```

> 400의 `message`는 **첫 번째 필드 오류 하나만** 담는다. 필드 3개가 전부 틀려도
> 하나만 알려준다. 프론트는 서버 메시지로 폼 전체를 표시할 수 없고,
> 클라이언트 측 검증을 따로 가져야 한다.

### 1.2 `POST /auth/login` — 로그인

**요청**

```http
POST /api/v1/auth/login HTTP/1.1
Content-Type: application/json;charset=UTF-8
```
```json
{ "email": "alpha@queuemate.dev", "password": "Qm!passw0rd" }
```

**응답 200**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEifQ.SIG_A",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJ0eXAiOiJSRUZSRVNIIn0.SIG_R",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

| 필드 | 의미 |
|---|---|
| `expiresIn` | **access token의 남은 초.** 밀리초가 아니다 |
| `tokenType` | 항상 `"Bearer"` 고정 |
| `refreshToken` | 응답 바디로만 온다. 쿠키를 쓰지 않는다. **access token과 같은 JWT 형식**이고 `typ`만 다르다 |

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `email` 형식 오류, `password` 공백 |
| 401 | `UNAUTHORIZED` | 없는 이메일 / 틀린 비밀번호 / **정지된 계정** |
| 429 | `LOGIN_ATTEMPTS_EXCEEDED` | 이메일별·IP별 시도 한도 초과 |

```json
{ "code": "UNAUTHORIZED", "message": "인증에 실패했다" }
```

> **셋 다 같은 401·같은 메시지다.** "이메일이 없다"와 "비밀번호가 틀렸다"를 구분해
> 주지 않는다. 계정 존재 여부를 흘리지 않기 위한 의도된 설계이므로,
> 프론트에서 "가입되지 않은 이메일입니다" 같은 문구를 만들면 안 된다.

### 1.3 `POST /auth/refresh` — 토큰 재발급

**요청**

```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJ0eXAiOiJSRUZSRVNIIn0.SIG_R" }
```

**응답 200** — `TokenResponse`. 형태는 1.2와 같고 **`refreshToken`도 새 값으로 바뀐다(rotation).**

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `refreshToken`이 빈 문자열 |
| 401 | `UNAUTHORIZED` | 없는 토큰 / **이미 쓴 토큰 재사용** / access token을 여기에 넣음 |

> **재사용을 감지하면 그 사용자의 토큰 전체가 무효화된다.** 한 번 쓴 refresh token을
> 재시도 로직으로 두 번 보내면 멀쩡한 세션까지 같이 죽는다.
> 클라이언트의 자동 재시도는 refresh에 걸면 안 된다.

### 1.4 `POST /auth/logout` — 로그아웃

**요청** — 바디는 1.3과 같다. `Authorization` 헤더는 필요 없다.

```http
POST /api/v1/auth/logout HTTP/1.1
Content-Type: application/json;charset=UTF-8
```
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJ0eXAiOiJSRUZSRVNIIn0.SIG_R" }
```

> **logout은 공개 엔드포인트다.** refresh token 자체가 자격 증명이므로 `Authorization`
> 헤더가 필요 없다. access token이 만료된 뒤에도 세션을 끊을 수 있어야 하기 때문이다.

**응답 204** — 본문 없음.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `refreshToken`이 빈 문자열 |
| 401 | `UNAUTHORIZED` | **`refreshToken`이 JWT로 파싱되지 않는다** |

> 넘긴 **그 refresh token 하나만** 지운다. 다른 기기의 세션은 살아 있다.
>
> **"로그아웃은 언제나 성공한다"고 가정하면 안 된다.** refresh token은 불투명 문자열이
> 아니라 JWT이고 logout이 이걸 파싱해서 주체를 꺼낸다. 아무 문자열이나 보내면 401이다.
> 반면 **서명이 유효한 토큰은 이미 로그아웃한 것이어도 204**다 — 그 범위에서만 멱등하다.
> (측정: `AuthApiContractTest` TC-AUTH-13 / TC-AUTH-13b)

---

## 2. User

모두 `Authorization: Bearer ACCESS_A` 필수.

### 2.1 `GET /users/me` — 내 프로필

**요청**

```http
GET /api/v1/users/me HTTP/1.1
Authorization: Bearer ACCESS_A
```

**응답 200**

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "nickname": "알파",
  "avatarUrl": null
}
```

**실패** — 401(본문 없음), 404 `USER_NOT_FOUND`(토큰은 유효하나 계정이 지워진 경우).

> **이메일이 실리지 않는다.** 마이페이지에 이메일을 표시하려면 응답에 필드를 추가해야 한다.

### 2.2 `PATCH /users/me` — 프로필 수정

**요청**

```http
PATCH /api/v1/users/me HTTP/1.1
Authorization: Bearer ACCESS_A
Content-Type: application/json;charset=UTF-8
```
```json
{ "nickname": "알파2", "avatarUrl": "https://cdn.queuemate.dev/avatars/alpha.png" }
```

| 필드 | 필수 | 제약 | 생략하면 |
|---|---|---|---|
| `nickname` | ❌ | 2\~16자 | 기존 값 유지 |
| `avatarUrl` | ❌ | 제약 없음 | 기존 값 유지 |

부분 수정 예 — 아바타만 지운다.
```json
{ "avatarUrl": null }
```

> **키를 생략한 것과 `null`을 보낸 것이 다르다.**
>
> | 보낸 것 | 뜻 |
> |---|---|
> | 키 없음 | 그 항목은 건드리지 않는다 |
> | `"avatarUrl": null` | **아바타를 지운다** |
> | `"nickname": null` | **400.** 닉네임은 비울 수 없다 |

**응답 200** — `UserProfileResponse` (2.1과 같은 형태, 바뀐 값).

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `nickname`이 1자 또는 17자 |
| 409 | `NICKNAME_ALREADY_IN_USE` | `브라보`가 쓰는 닉네임으로 변경 |

### 2.3 `GET /users/me/game-accounts` — 연결된 게임 계정 목록

**응답 200** — 배열. 없으면 `[]`.

```json
[
  {
    "id": "a0000000-0000-4000-8000-000000000001",
    "game": "LOL",
    "externalGameId": "Alpha#KR1",
    "region": "KR",
    "rankCode": "GOLD_2",
    "verifiedAt": "2026-09-05T12:00:00Z"
  }
]
```

> `rankCode`와 `verifiedAt`은 **요청으로 못 넣는다.** 서버가 채우는 파생 값이고
> 현재는 연결 직후 `null`이다(docs/02 §6).
>
> 중복 판정 기준은 `(userId, game, externalGameId)`다. 따라서 **같은 게임에
> 다른 계정을 여러 개 연결하는 것이 허용된다.** "게임당 1계정"을 원한다면
> 지금 구현으로는 막히지 않는다.

### 2.4 `POST /users/me/game-accounts` — 게임 계정 연결

**요청**

```json
{ "game": "LOL", "externalGameId": "Alpha#KR1", "region": "KR" }
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `game` | enum | ✅ | `LOL` \| `VALORANT` \| `PUBG` |
| `externalGameId` | string | ✅ | 최대 128자, 공백 불가 |
| `region` | string | ❌ | 최대 20자 |

**응답 201**

```json
{
  "id": "a0000000-0000-4000-8000-000000000001",
  "game": "LOL",
  "externalGameId": "Alpha#KR1",
  "region": "KR",
  "rankCode": null,
  "verifiedAt": null
}
```

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `game`이 `"OVERWATCH"`, `externalGameId`가 빈 문자열 또는 129자 |
| 409 | `GAME_ACCOUNT_ALREADY_LINKED` | `(game, externalGameId)`가 똑같은 계정을 두 번 연결 |

> `game`에 enum에 없는 값을 넣으면 Jackson 역직렬화 단계에서 깨진다.
> 이 경우도 **다른 4xx와 같은 `{code: "VALIDATION_FAILED", message}` 형태**로 나간다.
> `message`에는 내부 구조가 새지 않게 일반적인 문구만 담는다.

### 2.5 `DELETE /users/me/game-accounts/{id}` — 연결 해제

**요청**

```http
DELETE /api/v1/users/me/game-accounts/a0000000-0000-4000-8000-000000000001 HTTP/1.1
Authorization: Bearer ACCESS_A
```

**응답 204** — 본문 없음.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `GAME_ACCOUNT_NOT_FOUND` | 없는 id, **또는 브라보의 계정 id** |

> 남의 계정 id를 넣어도 403이 아니라 404다. 소유 여부를 흘리지 않는다.
> 같은 id로 두 번 지우면 두 번째는 404다. **멱등하지 않다.**

---

## 3. Game config

읽기 전용이다. 인증은 필요하지만 사용자별로 결과가 다르지 않다.

### 3.1 `GET /games` — 지원 게임

**응답 200**

```json
[
  { "game": "LOL",      "keyConditionType": "POSITION" },
  { "game": "VALORANT", "keyConditionType": "ROLE" },
  { "game": "PUBG",     "keyConditionType": "PLAY_STYLE" }
]
```

### 3.2 `GET /games/{gameKey}/modes` — 모드 목록

`gameKey`는 경로 변수이고 `LOL` \| `VALORANT` \| `PUBG` 중 하나다.

**응답 200** (`GET /games/PUBG/modes`)

```json
[
  { "modeKey": "DUO",   "targetPartySize": 2, "roleUniqueness": false },
  { "modeKey": "SQUAD", "targetPartySize": 4, "roleUniqueness": false }
]
```

현재 seed 값 전부:

| game | modeKey | targetPartySize | roleUniqueness |
|---|---|---|---|
| LOL | `SOLO_DUO_RANKED` | 2 | **true** |
| VALORANT | `COMPETITIVE` | 5 | false |
| VALORANT | `UNRATED` | 5 | false |
| PUBG | `DUO` | 2 | false |
| PUBG | `SQUAD` | 4 | false |

> `targetPartySize`는 **서버가 정한다.** 클라이언트가 정원을 요청 바디에 실어 보내는
> 필드는 어디에도 없다(docs/03 §9).

**실패** — 경로에 `OVERWATCH` 같은 enum 밖 값을 넣으면 경로 변수 변환 실패로
**400 `VALIDATION_FAILED`**다. 컨트롤러 안의 404 `UNKNOWN_GAME`은
"enum에는 있는데 활성 모드가 하나도 없는 게임"일 때만 난다.

### 3.3 `GET /games/{gameKey}/match-schema` — 조건 폼 스키마

프론트가 매칭 조건 폼을 그리는 근거다.

**응답 200** (`GET /games/LOL/match-schema`)

```json
{
  "game": "LOL",
  "modes": [
    { "modeKey": "SOLO_DUO_RANKED", "targetPartySize": 2, "roleUniqueness": true }
  ],
  "keyCondition": {
    "type": "POSITION",
    "values": ["TOP", "JUNGLE", "MID", "ADC", "SUPPORT", "ANY"]
  },
  "voicePreferences": ["REQUIRED", "OPTIONAL", "NO_VOICE"],
  "playPurposes": ["RANK_UP", "NORMAL", "FUN"]
}
```

게임별 `keyCondition.values`:

| game | type | values |
|---|---|---|
| LOL | `POSITION` | `TOP` `JUNGLE` `MID` `ADC` `SUPPORT` **`ANY`** |
| VALORANT | `ROLE` | `DUELIST` `INITIATOR` `CONTROLLER` `SENTINEL` |
| PUBG | `PLAY_STYLE` | `AGGRESSIVE` `BALANCED` `SURVIVAL` |

> **`ANY`는 LoL에만 있다.** 자리를 다투지 않겠다는 선언이고, role uniqueness 모드에서
> 중복 판정에서 빠진다. 발로란트·배그에는 대응 값이 없으므로 프론트에서
> "상관없음" 버튼을 세 게임에 똑같이 그리면 안 된다.

**실패** — 404 `UNKNOWN_GAME`.

---

## 4. Realtime matching

### 4.1 `POST /match-requests` — 실시간 매칭 시작

**요청**

```http
POST /api/v1/match-requests HTTP/1.1
Authorization: Bearer ACCESS_A
Content-Type: application/json;charset=UTF-8
```
```json
{
  "game": "LOL",
  "modeKey": "SOLO_DUO_RANKED",
  "keyCondition": { "type": "POSITION", "value": "MID" },
  "voicePreference": "OPTIONAL",
  "playPurpose": "RANK_UP"
}
```

| 필드 | 타입 | 필수 | 비고 |
|---|---|---|---|
| `game` | enum | ✅ | |
| `modeKey` | string | ✅ | **자유 문자열이다.** enum이 아니라 seed 목록과 대조한다 |
| `keyCondition.type` | string | ✅ | 자유 문자열. 대소문자 무시하고 `POSITION`/`ROLE`/`PLAY_STYLE`로 파싱 |
| `keyCondition.value` | string | ✅ | 자유 문자열. `trim` 후 대문자로 정규화 |
| `voicePreference` | enum | ✅ | |
| `playPurpose` | enum | ✅ | |

> `keyCondition`의 두 필드는 **enum이 아니라 문자열**이다. `"mid"`, `" Mid "`도 통과한다.
> 반면 `game`·`voicePreference`·`playPurpose`는 enum이라 소문자를 보내면 깨진다.
> **한 바디 안에서 대소문자 규칙이 다르다.** 클라이언트는 전부 대문자로 보내는 게 안전하다.

**응답 201**

```json
{
  "id": "b0000000-0000-4000-8000-000000000001",
  "status": "QUEUED",
  "queuedAt": "2026-09-05T12:00:00Z",
  "proposalId": null
}
```

`status` 값: `QUEUED` `PROPOSED` `MATCHED` `CANCELLED` `EXPIRED`

> `queuedAt`은 **최초 대기 시작 시각**이다. 제안을 거절하고 큐로 돌아와도 갱신되지 않는다.
> 대기 시간 표시는 이 값을 기준으로 계산한다.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `modeKey` 공백, `keyCondition.type`이 `"POSTION"`(오타) |
| 400 | `VALIDATION_FAILED` | **게임과 조건 종류 불일치** — `LOL` + `type: "ROLE"` |
| 400 | `VALIDATION_FAILED` | **게임이 모르는 값** — `LOL` + `POSITION` + `value: "JUNGLER"` |
| 404 | `UNKNOWN_GAME_MODE` | `LOL` + `modeKey: "ARAM"` |
| 409 | `ACTIVE_MATCH_REQUEST_EXISTS` | 이미 대기 중인데 또 시작 |
| 503 | `MATCHING_UNAVAILABLE` | Redis 장애 (INV-10 fail-closed) |

```json
{ "code": "VALIDATION_FAILED", "message": "LOL의 조건 종류는 POSITION다. 받은 값: ROLE" }
```
```json
{ "code": "UNKNOWN_GAME_MODE", "message": "지원하지 않는 게임 모드다: LOL/ARAM" }
```
```json
{ "code": "MATCHING_UNAVAILABLE", "message": "일시적으로 처리할 수 없다" }
```

> 503의 `message`는 원인을 알려주지 않는다. 로그에만 남는다.

### 4.2 `GET /match-requests/{id}` — 상태 조회

**응답 200** — 4.1과 같은 형태. 제안이 잡히면 이렇게 바뀐다.

```json
{
  "id": "b0000000-0000-4000-8000-000000000001",
  "status": "PROPOSED",
  "queuedAt": "2026-09-05T12:00:00Z",
  "proposalId": "c0000000-0000-4000-8000-000000000001"
}
```

**실패** — 404 `MATCH_REQUEST_NOT_FOUND`. **브라보의 요청 id를 넣어도 404다.**

> 폴링으로 이 엔드포인트를 때리는 것은 보조 수단이다. 정상 흐름은 WebSocket
> `MATCH_PROPOSAL_CREATED` 이벤트를 받는 것이다(`contracts/events.md`).

### 4.3 `DELETE /match-requests/{id}` — 매칭 취소

**응답 204** — 본문 없음.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `MATCH_REQUEST_NOT_FOUND` | 없는 id 또는 남의 id |
| 409 | `MATCH_REQUEST_NOT_CANCELLABLE` | **`PROPOSED` 상태** — 제안 중에는 취소 못 한다 |

> 제안이 뜬 뒤에는 취소가 아니라 `POST /proposals/{id}/decline`으로 빠져야 한다.
> 프론트의 "취소" 버튼은 `status`가 `PROPOSED`로 바뀌는 순간 다른 동작으로 전환돼야 한다.

---

## 5. Proposal

### 5.1 `GET /proposals/{id}` — 제안 상세

**응답 200**

```json
{
  "id": "c0000000-0000-4000-8000-000000000001",
  "status": "PENDING",
  "expiresAt": "2026-09-05T12:00:30Z",
  "members": [
    {
      "userId": "11111111-1111-4111-8111-111111111111",
      "nickname": "알파",
      "acceptance": "ACCEPTED"
    },
    {
      "userId": "22222222-2222-4222-8222-222222222222",
      "nickname": "브라보",
      "acceptance": "PENDING"
    }
  ],
  "partyId": null
}
```

| enum | 값 |
|---|---|
| `status` | `PENDING` `CONFIRMED` `DECLINED` `EXPIRED` `CANCELLED` |
| `members[].acceptance` | `PENDING` `ACCEPTED` `DECLINED` |

> `partyId`는 **확정 전에는 `null`**이고 `CONFIRMED`가 된 뒤에만 채워진다.
> 확정된 제안을 다시 조회해도 계속 실린다.
> `expiresAt`은 절대 시각이다. 남은 초는 클라이언트가 계산한다.

**실패** — 404 `PROPOSAL_NOT_FOUND`. **참가자가 아니면 404다**(403이 아니다).

### 5.2 `POST /proposals/{id}/accept` — 수락

**요청** — 바디 없음.

```http
POST /api/v1/proposals/c0000000-0000-4000-8000-000000000001/accept HTTP/1.1
Authorization: Bearer ACCESS_B
```

**응답 200** — 전원이 수락해 확정된 순간.

```json
{
  "id": "c0000000-0000-4000-8000-000000000001",
  "status": "CONFIRMED",
  "expiresAt": "2026-09-05T12:00:30Z",
  "members": [
    { "userId": "11111111-1111-4111-8111-111111111111", "nickname": "알파",   "acceptance": "ACCEPTED" },
    { "userId": "22222222-2222-4222-8222-222222222222", "nickname": "브라보", "acceptance": "ACCEPTED" }
  ],
  "partyId": "d0000000-0000-4000-8000-000000000001"
}
```

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `PROPOSAL_NOT_FOUND` | 없는 id 또는 참가자가 아님 |
| 409 | `PROPOSAL_EXPIRED` | `expiresAt` 경과 |
| 409 | `PROPOSAL_NOT_PENDING` | 이미 `CONFIRMED`·`DECLINED`·`CANCELLED` |

> **같은 사람이 두 번 수락해도 200이고 확정은 한 번만 일어난다(INV-3).**
> 즉 이 호출은 멱등하다. 네트워크 재시도를 걸어도 안전하다.

### 5.3 `POST /proposals/{id}/decline` — 거절

**요청** — 바디 없음.

**응답 204** — 본문 없음.

**실패** — 404 `PROPOSAL_NOT_FOUND`, 409 `PROPOSAL_NOT_PENDING`.

> 한 명이 거절하면 **제안 전체가 끝나고 나머지 인원은 큐로 돌아간다.**
> 돌아간 사람의 `queuedAt`은 최초 값 그대로다.

---

## 6. Reservation

실시간 조건에 **시간 두 개 + 플레이 양** 만 더한 것이다(docs/02 §7).

### 6.1 `POST /reservations` — 예약 생성

**요청**

```http
POST /api/v1/reservations HTTP/1.1
Authorization: Bearer ACCESS_A
Content-Type: application/json;charset=UTF-8
```
```json
{
  "condition": {
    "game": "VALORANT",
    "modeKey": "COMPETITIVE",
    "keyCondition": { "type": "ROLE", "value": "DUELIST" },
    "voicePreference": "REQUIRED",
    "playPurpose": "RANK_UP"
  },
  "availableFrom": "2026-09-05T21:00:00Z",
  "availableTo": "2026-09-05T23:00:00Z",
  "playAmount": "TWO_PLUS"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `condition` | object | ✅ | §4.1의 매칭 조건과 **완전히 같은 형태** |
| `availableFrom` | ISO-8601 | ✅ | **30분 경계.** 분이 `00` 또는 `30`, 초·나노는 `0` |
| `availableTo` | ISO-8601 | ✅ | 30분 경계이고 `availableFrom`보다 뒤 |
| `playAmount` | enum | ✅ | `ONE_GAME` \| `TWO_PLUS` |

> `21:15`, `21:00:30` 은 전부 거부된다. 프론트의 시간 선택 UI는 **30분 격자로만**
> 값을 만들어야 한다.

**응답 201**

```json
{
  "id": "e0000000-0000-4000-8000-000000000001",
  "status": "ACTIVE",
  "condition": {
    "game": "VALORANT",
    "modeKey": "COMPETITIVE",
    "keyCondition": { "type": "ROLE", "value": "DUELIST" },
    "voicePreference": "REQUIRED",
    "playPurpose": "RANK_UP"
  },
  "availableFrom": "2026-09-05T21:00:00Z",
  "availableTo": "2026-09-05T23:00:00Z",
  "playAmount": "TWO_PLUS",
  "createdAt": "2026-09-05T12:00:00Z",
  "scheduledStart": null,
  "proposalId": null
}
```

`status` 값: `ACTIVE` `PROPOSED` `MATCHED` `CANCELLED` `EXPIRED` `COMPLETED`

> `scheduledStart`는 매칭이 붙은 뒤 채워지는 **약속 시각**이고, 겹치는 구간 중
> 가장 이른 30분 슬롯이 된다.
>
> **`partyId`가 없다.** 예약이 파티까지 갔는지 알려면 `proposalId`로
> `GET /proposals/{id}`를 부른다. 실시간 매칭과 같은 규칙이다.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `availableFrom`이 `2026-09-05T21:15:00Z` |
| 400 | `VALIDATION_FAILED` | `availableTo` ≤ `availableFrom` |
| 400 | `VALIDATION_FAILED` | 조건의 게임·조건 종류 불일치 |
| 404 | `UNKNOWN_GAME_MODE` | 없는 모드 |
| 409 | `OVERLAPPING_RESERVATION` | 같은 사용자의 시간대 겹침 (INV-9) |

```json
{ "code": "VALIDATION_FAILED", "message": "availableFrom는 30분 단위여야 한다: 2026-09-05T21:15Z" }
```
```json
{ "code": "OVERLAPPING_RESERVATION", "message": "같은 시간대에 이미 예약이 있다" }
```

> 겹침 판정 규칙 세 가지 —
> 1. **게임·모드와 무관하게 시간만 본다.** 21:00\~23:00 예약이 있으면 다른 게임으로도
>    22:00\~24:00을 못 잡는다.
> 2. **반열림 구간 `[from, to)`이다.** 판정식이 `기존.from < 새.to AND 새.from < 기존.to`라
>    23:00\~24:00은 21:00\~23:00과 겹치지 않는다. 경계에 딱 붙는 예약은 통과한다.
> 3. **시간을 점유하는 상태는 `ACTIVE`·`PROPOSED`·`MATCHED` 셋뿐이다.**
>    `CANCELLED`·`EXPIRED`·`COMPLETED` 예약은 그 시간대를 막지 않는다.
>
> DB에도 `reservations_no_active_overlap` EXCLUDE 제약이 있지만 그쪽은 `ACTIVE`·`PROPOSED`만
> 막는다. `MATCHED`까지 포함한 검사는 애플리케이션이 한다. **두 층의 범위가 다르다.**

### 6.2 `GET /reservations` — 내 예약 목록

**응답 200** — `ReservationView` 배열. 없으면 `[]`.

> 쿼리 파라미터가 **하나도 없다.** 상태 필터도, 페이지네이션도, 정렬 지정도 없다.
> 취소·만료된 예약까지 전부 한 번에 온다면 클라이언트가 걸러야 한다.

### 6.3 `GET /reservations/{id}` — 예약 상세

**응답 200** — `ReservationView`.

**실패** — 404 `RESERVATION_NOT_FOUND`(없는 id 또는 남의 예약).

### 6.4 `PUT /reservations/{id}` — 예약 수정 (PATCH도 같은 동작)

```http
PUT /api/v1/reservations/e0000000-0000-4000-8000-000000000001 HTTP/1.1
Authorization: Bearer ACCESS_A
Content-Type: application/json;charset=UTF-8
```

**바디는 `POST /reservations`와 완전히 같다.**

```json
{
  "condition": {
    "game": "VALORANT",
    "modeKey": "UNRATED",
    "keyCondition": { "type": "ROLE", "value": "SENTINEL" },
    "voicePreference": "OPTIONAL",
    "playPurpose": "FUN"
  },
  "availableFrom": "2026-09-05T22:00:00Z",
  "availableTo": "2026-09-06T00:00:00Z",
  "playAmount": "ONE_GAME"
}
```

> **전체 교체다.** 4개 필드가 전부 필수라 하나라도 빠지면 400이다.
> 클라이언트는 **수정 전 값을 먼저 조회해서 통째로 다시 보낸다.**
> 그래서 메서드가 `PUT`이다. 같은 경로의 `PATCH`도 당분간 같은 동작으로 받지만,
> 새로 붙는 클라이언트는 `PUT`을 쓴다.

**응답 200** — `ReservationView`.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 필드 누락, 30분 격자 위반 |
| 404 | `RESERVATION_NOT_FOUND` | 없는 id 또는 남의 예약 |
| 404 | `UNKNOWN_GAME_MODE` | 없는 모드로 변경 |
| 409 | `RESERVATION_NOT_EDITABLE` | `PROPOSED`·`MATCHED` 등 |
| 409 | `OVERLAPPING_RESERVATION` | 바꾼 시간이 **다른** 예약과 겹침 |

> 겹침 검사에서 **자기 자신은 제외된다.** 같은 시간을 유지한 채 조건만 바꾸는 수정이
> 409로 막히지 않는다.

### 6.5 `DELETE /reservations/{id}` — 예약 취소

**응답 204** — 본문 없음.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `RESERVATION_NOT_FOUND` | 없는 id 또는 남의 예약 |
| 409 | `RESERVATION_NOT_CANCELLABLE` | 그 상태에서 `CANCELLED`로 못 감 |

> **이미 `CANCELLED`면 409가 아니라 204다.** 취소는 그 경우에 한해 멱등하다.
> `PROPOSED` 상태도 취소된다 — 제안을 먼저 파기하고 취소한다.

---

## 7. Party

파티는 **만들지 않는다.** 제안이 확정될 때 서버가 만든다. `POST /parties`가 없는 이유다.

### 7.1 `GET /parties/{id}` — 파티 상세

**응답 200**

```json
{
  "id": "d0000000-0000-4000-8000-000000000001",
  "game": "LOL",
  "modeKey": "SOLO_DUO_RANKED",
  "targetSize": 2,
  "status": "OPEN",
  "members": [
    { "userId": "11111111-1111-4111-8111-111111111111", "nickname": "알파",   "ready": false },
    { "userId": "22222222-2222-4222-8222-222222222222", "nickname": "브라보", "ready": false }
  ]
}
```

`status` 값: `OPEN`(모이는 중) `READY`(전원 준비) `PLAYING`(게임 중) `CLOSED`(종료)

> `game`은 `GameKey` enum이 아니라 **문자열 필드**다(`PartyView`의 `String game`).
> 값은 같지만 타입이 다른 자리다.
>
> `nickname`은 **`null`일 수 있다.** 사용자 조회가 비면 그대로 `null`이 실린다.
> 프론트는 이름이 없어도 화면이 죽지 않아야 한다(develop `7861193`이 고친 것).

**실패** — 404 `PARTY_NOT_FOUND`. **멤버가 아니면 404다.**

### 7.2 `POST /parties/{id}/ready` — 준비 상태 변경

**요청**

```http
POST /api/v1/parties/d0000000-0000-4000-8000-000000000001/ready HTTP/1.1
Authorization: Bearer ACCESS_A
Content-Type: application/json;charset=UTF-8
```
```json
{ "ready": true }
```

| 필드 | 타입 | 필수 |
|---|---|---|
| `ready` | boolean | ✅ (`@NotNull`) |

> 토글이 아니라 **명시적 대입**이다. 준비를 푸는 것은 `{"ready": false}`다.

**응답 200** — `PartyView`. 전원이 준비되면 `status`가 `READY`로 바뀐다.

```json
{
  "id": "d0000000-0000-4000-8000-000000000001",
  "game": "LOL",
  "modeKey": "SOLO_DUO_RANKED",
  "targetSize": 2,
  "status": "READY",
  "members": [
    { "userId": "11111111-1111-4111-8111-111111111111", "nickname": "알파",   "ready": true },
    { "userId": "22222222-2222-4222-8222-222222222222", "nickname": "브라보", "ready": true }
  ]
}
```

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `ready` 누락 또는 `null` |
| 404 | `PARTY_NOT_FOUND` | 없는 파티 또는 멤버가 아님 |
| 409 | `PARTY_CLOSED` | 종료된 파티 |
| 409 | `PARTY_PLAYING` | 이미 게임이 시작됨 |

> `PLAYING`이 되면 준비 상태를 되돌릴 수 없다. 빠지려면 파티를 나가야 한다.
> `READY` → `PLAYING`은 **시간으로 판정한다.** 서버는 게임을 관측하지 못한다.

### 7.3 `POST /parties/{id}/leave` — 파티 나가기

**요청** — 바디 없음.

**응답 204** — 본문 없음.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `PARTY_NOT_FOUND` | 없는 파티 또는 멤버가 아님 |
| 409 | `ALREADY_LEFT` | 이미 나갔거나 종료된 파티 |

> 명시적 이탈은 **유예 없이 즉시**다. 연결 끊김만 재접속 유예를 준다.
> 남은 인원이 1명이 되면 파티는 `CLOSED`가 되고 그 사람은 대기열로 복귀한다.
> 나간 사람에게는 매칭 요청이 자동으로 만들어지지 않는다.

### 7.4 파티 초대 — **계약에서 제외됐다**

`POST /parties/{id}/invite/{friendUserId}`는 v1 계약서에 있었지만 구현이 없었고,
**v2에서 계약서에서도 제거했다.** 정원 초과·차단 관계·알림 전달 설계가 따로 필요한
기능이라 계약 정리 작업에 끼울 것이 아니라고 판단했다. 필요해지면 그때 별도로 설계한다.

---

## 8. Friends

### 8.1 `GET /friends` — 친구 목록

**응답 200**

```json
[
  {
    "userId": "22222222-2222-4222-8222-222222222222",
    "nickname": "브라보",
    "avatarUrl": null,
    "friendedAt": "2026-09-05T12:00:00Z"
  }
]
```

> `userId`는 **상대방의 id**다. 내 id가 아니다. 관계 자체의 id도 노출하지 않는다.

### 8.2 `DELETE /friends/{userId}` — 친구 삭제

경로 변수는 **상대 사용자 id**다. 친구 관계 id가 아니다.

**응답 204** — 본문 없음.

**실패** — 404 `FRIENDSHIP_NOT_FOUND`.

> 삭제하면 **양방향이 함께 사라진다.** 두 번 지우면 두 번째는 404다.

### 8.3 `GET /friend-requests` — 친구 요청 목록

| 쿼리 | 타입 | 기본값 | 값 |
|---|---|---|---|
| `direction` | enum | `RECEIVED` | `RECEIVED` \| `SENT` |

```http
GET /api/v1/friend-requests?direction=SENT HTTP/1.1
Authorization: Bearer ACCESS_A
```

**응답 200**

```json
[
  {
    "id": "f0000000-0000-4000-8000-000000000001",
    "direction": "SENT",
    "counterpartUserId": "22222222-2222-4222-8222-222222222222",
    "counterpartNickname": "브라보",
    "status": "PENDING",
    "createdAt": "2026-09-05T12:00:00Z"
  }
]
```

`status` 값: `PENDING` `ACCEPTED` `DECLINED` `CANCELLED`

> **`PENDING`만 돌아온다.** 서비스가 대기 중인 것만 조회하므로 지난 요청 이력은
> 이 API로 볼 수 없다. `status` 필드는 사실상 항상 `"PENDING"`이다.
>
> `direction`에 `"received"`처럼 소문자를 넣으면 **400 `VALIDATION_FAILED`**다.

### 8.4 `POST /friend-requests` — 친구 요청 보내기

**요청**

```json
{ "targetUserId": "22222222-2222-4222-8222-222222222222" }
```

**응답 201** — `direction`이 **항상 `"SENT"`**로 실린다.

```json
{
  "id": "f0000000-0000-4000-8000-000000000001",
  "direction": "SENT",
  "counterpartUserId": "22222222-2222-4222-8222-222222222222",
  "counterpartNickname": "브라보",
  "status": "PENDING",
  "createdAt": "2026-09-05T12:00:00Z"
}
```

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `targetUserId` 누락 |
| 404 | `USER_NOT_FOUND` | 없는 사용자 |
| 409 | `SELF_FRIEND_REQUEST` | 내 id를 넣음 |
| 409 | `ALREADY_FRIENDS` | 이미 친구 |
| 409 | `REQUEST_ALREADY_PENDING` | **내가 보낸** 요청이 이미 대기 중 |
| 409 | `INVERSE_REQUEST_PENDING` | **상대가 보낸** 요청이 대기 중 |
| 409 | `BLOCKED_RELATION` | 어느 쪽이든 차단 관계 |

> 두 중복 코드가 다르다. `INVERSE_REQUEST_PENDING`을 받으면 프론트는
> "요청 보내기"가 아니라 **"수락하기"**를 띄워야 한다. 같은 409로 묶어 처리하면
> 사용자가 막다른 길에 갇힌다.
>
> `BLOCKED_RELATION`은 **내가 차단했든 상대가 차단했든 같은 코드**다.
> 어느 쪽인지 알려주지 않는다.

### 8.5 `POST /friend-requests/{id}/accept` — 수락

**요청** — 바디 없음.

**응답 200** — `FriendRequestView`가 아니라 **`FriendView`**가 온다.

```json
{
  "userId": "11111111-1111-4111-8111-111111111111",
  "nickname": "알파",
  "avatarUrl": null,
  "friendedAt": "2026-09-05T12:00:00Z"
}
```

> 수락 응답의 타입이 목록 응답과 다르다. 요청 리스트에서 항목을 갱신하려면
> 이 응답만으로는 부족하고 목록을 다시 받아야 한다.
>
> 생성 응답도 조회와 같은 값을 준다. 방금 만든 것의 시각이 비지 않는다.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 404 | `FRIEND_REQUEST_NOT_FOUND` | 없는 id, **또는 내가 받은 요청이 아님** |
| 409 | `FRIEND_REQUEST_NOT_PENDING` | 이미 처리된 요청 |

> **보낸 사람이 자기 요청을 수락할 수 없다.** 수신자 id가 나와 일치하는지까지
> 조회 조건에 넣기 때문에 404가 난다.

### 8.6 `POST /friend-requests/{id}/decline` — 거절

**응답 204**. 실패는 8.5와 동일.

### 8.7 `DELETE /friend-requests/{id}` — 보낸 요청 취소

**응답 204**.

**실패** — 404 `FRIEND_REQUEST_NOT_FOUND`(발신자가 내가 아니면 404),
409 `FRIEND_REQUEST_NOT_PENDING`.

> 8.5/8.6은 **수신자**만, 8.7은 **발신자**만 쓸 수 있다. 같은 id라도 방향이 다르면 404다.

---

## 9. Blocks

### 9.1 `GET /blocks` — 차단 목록

**응답 200**

```json
[
  {
    "userId": "33333333-3333-4333-8333-333333333333",
    "nickname": "찰리",
    "blockedAt": "2026-09-05T12:00:00Z"
  }
]
```

> `FriendView`와 달리 **`avatarUrl`이 없다.** 차단 목록에 얼굴을 띄우지 않는다.

### 9.2 `POST /blocks` — 차단

**요청**

```json
{ "targetUserId": "33333333-3333-4333-8333-333333333333" }
```

**응답 201**

```json
{
  "userId": "33333333-3333-4333-8333-333333333333",
  "nickname": "찰리",
  "blockedAt": "2026-09-05T12:00:00Z"
}
```

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `targetUserId` 누락 |
| 404 | `USER_NOT_FOUND` | 없는 사용자 |
| 409 | `SELF_BLOCK` | 내 id |
| 409 | `ALREADY_BLOCKED` | 이미 차단함 |

> 차단은 **부수효과가 크다.** 기존 친구 관계와 양방향 대기 중 친구 요청이 함께
> 지워지고, 이후 매칭 후보에서 양방향으로 즉시 제외된다(INV-6).
> 되돌려도 친구 관계는 복구되지 않는다. 프론트에 확인 단계를 둬야 한다.

### 9.3 `DELETE /blocks/{userId}` — 차단 해제

**응답 204**.

**실패** — 404 `BLOCK_NOT_FOUND`.

---

## 10. Recent players / Reports

### 10.1 `GET /recent-players` — 최근 함께한 사람

| 쿼리 | 타입 | 기본값 | 제약 |
|---|---|---|---|
| `limit` | integer | `20` | 1 이상 50 이하 |

**응답 200**

```json
[
  {
    "userId": "22222222-2222-4222-8222-222222222222",
    "nickname": "브라보",
    "avatarUrl": null,
    "lastPlayedAt": "2026-09-05T11:20:00Z",
    "playCount": 3,
    "friend": true
  }
]
```

> **완료된(=실제로 플레이한) 파티에서만** 뽑는다. 만들어졌다가 아무도 시작하지 않은
> 파티는 여기 안 나온다. 차단한 사용자도 제외된다.

**실패** — `limit=0` / `limit=51`은 **400 `VALIDATION_FAILED`**다.

### 10.2 `POST /reports` — 신고

**요청**

```json
{
  "targetUserId": "33333333-3333-4333-8333-333333333333",
  "reason": "ABUSIVE_LANGUAGE",
  "description": "파티에서 욕설을 반복했다",
  "partyId": "d0000000-0000-4000-8000-000000000001"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `targetUserId` | uuid | ✅ | |
| `reason` | enum | ✅ | `ABUSIVE_LANGUAGE` `HARASSMENT` `CHEATING` `TROLLING_OR_AFK` `INAPPROPRIATE_PROFILE` `OTHER` |
| `description` | string | ❌ | 최대 1000자 |
| `partyId` | uuid | ❌ | 맥락 표시용 |

**응답 201** — **본문이 없다.** `Content-Type` 헤더도 없다.

> 서버가 음성·채팅을 저장하지 않으므로 신고에 대화 내용을 실을 수 없다(docs/13).
> `description`은 신고자가 직접 쓴 것만 들어간다.
>
> 201인데 본문이 비어 있어 **신고 id를 클라이언트가 알 수 없다.** 중복 신고 방지나
> 처리 상태 조회를 하려면 응답 설계를 바꿔야 한다.

**실패**

| 상태 | code | 트리거 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `reason` 누락, `description` 1001자 |
| 404 | `USER_NOT_FOUND` | 없는 사용자 |
| 409 | `SELF_REPORT` | 내 id |

> **같은 사람을 몇 번이든 신고할 수 있다.** 중복 신고를 막는 코드가 없다.

---

## 11. 2026-09-05 계약 정리 — 무엇을 어떻게 정했나

v1에서 계약서와 구현이 어긋난 13건이다. **각각 어느 쪽이 옳은지 정하고 한쪽으로 맞췄다.**

| # | 무엇이 어긋났나 | 결정 | 이유 |
|---|---|---|---|
| 1 | `ReservationView.createdAt` — 계약엔 있고 구현엔 없음 | **구현에 추가** | 예약 목록을 만든 순서로 보여 주려면 필요하다 |
| 2 | `avatarUrl: null`이 무시돼 아바타를 못 지움 | **구현 수정.** 명시적 `null` = 삭제, 키 생략 = 유지 | 지우는 방법이 아예 없는 건 기능 누락이다 |
| 3 | enum 역직렬화 실패가 `code` 없는 400 | **구현 수정.** 모든 4xx가 `{code, message}` | 예외가 하나라도 있으면 클라이언트가 두 갈래 처리를 해야 한다 |
| 4 | 예약 중복 코드 이름 혼동 | **`OVERLAPPING_RESERVATION` 유지** | 구현 이름이 더 정확하다. 계약서에 명시했다 |
| 5 | `PATCH /reservations/{id}`가 전체 교체 | **`PUT`이 정본.** `PATCH`는 별칭으로 남김 | 의미가 PUT인데 이름만 PATCH면 오해한다. 먼저 붙은 클라이언트는 안 깨뜨린다 |
| 6 | `POST /parties/{id}/invite/{friendUserId}` 미구현 | **계약서에서 제거** | 정원·차단·알림 설계가 따로 필요한 기능이다. 계약 정리 작업에 끼울 것이 아니다 |
| 7 | `limit` 범위 위반이 500 | **구현 수정 → 400** | 사용자 입력 오류를 서버 장애로 보고하면 안 된다 |
| 8 | yaml에 `/parties/{id}/leave` 키가 두 번 | **중복 제거** | 파서에 따라 앞 정의가 통째로 무시된다 |
| 9 | 인증 필터의 401만 본문 없음 | **구현 수정.** 401도 `{code, message}` | #3과 같은 이유 |
| 10 | 201에 `Location` 헤더 없음 | **현행 유지.** 계약서에 명시 | 클라이언트가 본문 `id`를 읽으면 된다. 굳이 두 경로를 만들지 않는다 |
| 11 | 생성 응답의 타임스탬프가 `null` | **구현 수정.** `@Generated(INSERT)` | 방금 만든 것의 시각을 못 읽는 건 그냥 버그다 |
| 12 | `POST /auth/logout`이 인증 요구 | **공개로 전환** | refresh token이 곧 자격 증명이다. access token이 만료되면 로그아웃도 못 하는 건 막다른 길이다 |
| 13 | `ReservationView.partyId`가 항상 `null` | **필드 제거** | 예약에는 party_id 컬럼이 없다. 실시간 매칭도 `proposalId`만 준다. 두 흐름의 규칙을 같게 뒀다 |

### 곁들여 정리한 것

- **예외 처리기를 하나로 합쳤다.** `MatchingExceptionHandler`가 세 패키지에만 걸려 있어
  같은 성격의 오류가 자리에 따라 400이 되기도 500이 되기도 했다.
  `GlobalExceptionHandler` 하나로 모았다. 대가는 안다 — 진짜 내부 버그로 생긴
  `IllegalArgumentException`도 400으로 나간다. 그건 로그로 잡는다.
- **415도 `{code: "UNSUPPORTED_MEDIA_TYPE"}`을 준다.**

### 왜 이렇게 어긋났었나

`docs/08_HARNESS.md`는 검증 계층 5개를 지정했고 그중 **Contract 계층만 구현되지 않았다.**
`openapi.yaml`을 읽는 코드가 한 줄도 없었고, "1:1로 대응한다"는 자바 주석만 있었다.
그리고 §8의 CI gate가 요구한 `contract validation`을 돌릴 `.github/workflows`가 없었다.

`docs/10_TEAM_PARALLEL_PLAN.md`가 `contracts/**`를 "공유 읽기 전용 구역"으로 뒀는데,
**공유 구역에는 주인이 없다.** 각자 자기 영역은 검증했고 둘 사이는 아무도 안 봤다.

지금은 이렇게 막는다.

```
contracts/openapi.yaml         ← 정본
  ↓ 검증
backend/src/test/.../api/      ← HTTP 계층 계약 테스트
mock-server/contract-smoke.sh  ← 더미 서버 계약 테스트
  ↓ 강제
.github/workflows/ci.yml       ← merge 전 자동 실행
```
