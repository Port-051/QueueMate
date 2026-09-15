# 15. CRUD 테스트 케이스

`docs/14_API_REQUEST_RESPONSE_EXAMPLES.md`가 정한 계약을 **HTTP 계층에서** 검증하는
케이스 목록이다. 더미 데이터는 14번 문서 §0.8을 그대로 쓴다.

> **구현 상태 (2026-09-05)** — 이 문서의 케이스는 코드로 존재하고 **전부 통과한다.**
>
> | 무엇 | 어디 | 결과 |
> |---|---|---|
> | 백엔드 계약 테스트 | `backend/src/test/java/com/queuemate/api/` | 컴파일 통과 · **Docker 필요, 실행 대기** |
> | 더미 서버 스모크 | `mock-server/contract-smoke.sh` | **78개 전부 통과** |
> | CI | `.github/workflows/ci.yml` | merge 전 자동 실행 |
>
> ```bash
> cd backend && ./gradlew test --tests 'com.queuemate.api.*'   # Docker 필요
> node mock-server/server.js & bash mock-server/contract-smoke.sh
> ```
>
> Docker가 없으면 계약 테스트는 실패가 아니라 **건너뛴다**
> (`@EnabledIf(DockerAvailability#isAvailable)`).

---

## 0. 왜 이 문서가 필요한가 (측정 결과)

`backend/src/test`의 테스트 38개를 전수로 확인했다.

| 검증 계층 | 파일 수 | 실제로 무엇을 검증하나 |
|---|---|---|
| 도메인·서비스 통합 (`@SpringBootTest` + 서비스 직접 호출) | 대부분 | 매칭 규칙, 상태 전이, 불변식(INV-3\~10), Redis·DB 상호작용 |
| **HTTP 계층** (`TestRestTemplate`) | **5** | 로그인·가입 rate limit 2건, 파티 이탈 1건, 로깅 1건, 메트릭 1건 |

`TestRestTemplate`을 쓰는 파일 5개:
`SignupRateLimitIntegrationTest` · `LoginRateLimitIntegrationTest` ·
`PartyDepartureIntegrationTest` · `RequestLoggingIntegrationTest` · `MetricsIntegrationTest`

**즉 user·game-account·friend·block·reservation·match-request·report의 CRUD는
HTTP를 통과하는 테스트가 하나도 없다.** 상태 코드, 응답 바디 형태, 인증 실패,
enum 역직렬화 같은 것들이 전부 미검증 구간이다.
`GameConfigControllerTest`는 컨트롤러 객체를 직접 호출하는 방식이라 HTTP를 타지 않는다.

이 문서의 케이스는 **그 빈 구간을 메우는 것**이 목적이다. 이미 두껍게 검증된
매칭 규칙·상태 전이를 다시 짜지 않는다.

---

## 1. 테스트 작성 규약

### 1.1 계층 선택

| 대상 | 방식 | 이유 |
|---|---|---|
| 상태 코드·응답 바디 형태·헤더·인증 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` | 필터 체인과 Jackson을 실제로 통과해야 의미가 있다 |
| 도메인 규칙 | 기존 서비스 통합 테스트 유지 | 이미 있다 |

**`@WebMvcTest` + `@MockBean`을 쓰지 않는다.** 이 문서가 잡으려는 버그(§11-3의
enum 역직렬화, §11-7의 500)는 전부 필터와 예외 핸들러 조합에서 나오므로,
슬라이스 테스트로는 재현되지 않는다.

### 1.2 픽스처

```
알파   = 요청 주체.        ACCESS_A
브라보 = 정상 상대.        ACCESS_B
찰리   = 제3자(권한 밖).   ACCESS_C
NONE   = 00000000-0000-4000-8000-000000000000  (존재하지 않는 id)
```

각 테스트는 자기 데이터를 직접 만들고 `@BeforeEach`에서 DB·Redis를 비운다.
기존 통합 테스트가 쓰는 정리 방식을 따른다.

### 1.3 케이스 ID

`TC-<도메인>-<번호>` 형식이다. 테스트 메서드의 `@DisplayName`에 ID를 앞에 붙여
문서와 코드가 서로 찾아지게 한다.

```java
@Test
@DisplayName("TC-USER-05 남의 게임 계정을 지우려 하면 404다")
void unlinkingOthersGameAccountIsNotFound() { ... }
```

### 1.4 모든 CRUD 리소스에 공통으로 적용하는 4축

각 리소스마다 아래 네 가지를 빠짐없이 채운다. 표에서 축 열로 표시한다.

| 축 | 뜻 |
|---|---|
| **정상** | 성공 경로. 상태 코드 + 바디 전 필드 |
| **검증** | 400. 필수 누락·형식 위반·경계값 |
| **권한** | 401(토큰 없음) / 남의 리소스 → 404 |
| **상태** | 409. 중복·상태 전이 위반 / 멱등성 |

---

## 2. Auth (TC-AUTH)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-AUTH-01 | 정상 | 없음 | `POST /auth/signup` 정상 바디 | **201**, 바디 `{id, nickname, avatarUrl:null}` 3필드. **`accessToken`이 없음을 단언한다** |
| TC-AUTH-02 | 검증 | 없음 | `password` 7자 | **400** `VALIDATION_FAILED`, `message`가 `password`로 시작 |
| TC-AUTH-03 | 검증 | 없음 | `nickname` 1자 / 17자 (경계: 2자·16자는 성공) | 각각 **400** / **201** |
| TC-AUTH-04 | 검증 | 없음 | `email: "not-an-email"` | **400** |
| TC-AUTH-05 | 상태 | 알파 가입됨 | 같은 email로 재가입 | **409** `EMAIL_ALREADY_IN_USE` |
| TC-AUTH-06 | 상태 | 알파 가입됨 | 다른 email + 같은 nickname | **409** `NICKNAME_ALREADY_IN_USE` |
| TC-AUTH-07 | 정상 | 알파 가입됨 | `POST /auth/login` 정상 | **200**, `tokenType == "Bearer"`, `expiresIn > 0`, 두 토큰이 비어 있지 않음 |
| TC-AUTH-08 | 권한 | 알파 가입됨 | 틀린 비밀번호 / 없는 이메일 | **둘 다 401** `UNAUTHORIZED`, **`message`가 서로 같음을 단언한다** |
| TC-AUTH-09 | 정상 | 로그인함 | `POST /auth/refresh` | **200**, `refreshToken`이 **이전 값과 다름** |
| TC-AUTH-10 | 상태 | TC-AUTH-09 수행 | **같은** refresh token 재사용 | **401**, 그리고 그 뒤 새 refresh token도 **401**(전체 무효화) |
| TC-AUTH-11 | 권한 | 로그인함 | refresh 자리에 **access token** 투입 | **401** |
| TC-AUTH-12 | 정상 | 로그인 2회(기기 2대) | 한쪽 refresh로 `POST /auth/logout` | **204**, 다른 쪽 refresh는 **여전히 200** |
| TC-AUTH-13 | 상태 | 없음 | 없는 refresh token으로 logout | **204** (멱등) |

> TC-AUTH-08은 **정보 유출 방지가 무너지는 것을 잡는 케이스**다. 나중에 누군가
> "친절하게" 메시지를 나누면 여기서 깨진다.

---

## 3. User / Game account (TC-USER)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-USER-01 | 정상 | 알파 로그인 | `GET /users/me` | **200**, `{id, nickname, avatarUrl}` **3필드뿐임을 단언**(email 미노출) |
| TC-USER-02 | 권한 | — | `GET /users/me` **Authorization 없이** | **401**, **본문이 비어 있음을 단언** |
| TC-USER-03 | 권한 | — | `Authorization: Bearer garbage` | **401** |
| TC-USER-04 | 정상 | 알파 | `PATCH /users/me` `{nickname:"알파2"}` | **200**, `nickname == "알파2"`, `avatarUrl` 기존 유지 |
| TC-USER-05 | 검증 | 알파 | `{nickname:"가"}` | **400** `VALIDATION_FAILED` |
| TC-USER-06 | 상태 | 브라보 존재 | `{nickname:"브라보"}` | **409** `NICKNAME_ALREADY_IN_USE` |
| TC-USER-07 | 정상 | avatarUrl 설정됨 | `{"avatarUrl": null}` | **200**, **아바타가 지워지지 않고 그대로임**(현 구현 고정. §11-2) |
| TC-USER-08 | 정상 | 계정 없음 | `GET /users/me/game-accounts` | **200**, `[]` |
| TC-USER-09 | 정상 | 알파 | `POST .../game-accounts` LOL | **201**, `rankCode == null`, `verifiedAt == null` |
| TC-USER-10 | 검증 | 알파 | `game: "OVERWATCH"` | **400**, 그리고 **`code` 필드 유무를 단언**(§11-3의 실제 동작 고정) |
| TC-USER-11 | 검증 | 알파 | `externalGameId` 129자 / 빈 문자열 | **400** |
| TC-USER-12 | 상태 | LOL `Alpha#KR1` 연결됨 | 같은 값 재연결 | **409** `GAME_ACCOUNT_ALREADY_LINKED` |
| TC-USER-13 | 상태 | LOL `Alpha#KR1` 연결됨 | LOL `Alpha#KR2` 연결 | **201** — 같은 게임 복수 계정이 허용됨을 고정 |
| TC-USER-14 | 정상 | 계정 있음 | `DELETE .../game-accounts/{id}` | **204**, 이후 목록이 `[]` |
| TC-USER-15 | 권한 | 브라보의 계정 | 알파 토큰으로 삭제 | **404** `GAME_ACCOUNT_NOT_FOUND` (**403이 아님**) |
| TC-USER-16 | 상태 | 이미 삭제함 | 같은 id 재삭제 | **404** (멱등 아님) |

---

## 4. Game config (TC-GAME)

| ID | 축 | 요청 | 기대 |
|---|---|---|---|
| TC-GAME-01 | 정상 | `GET /games` | **200**, 원소 3개, `keyConditionType`이 `POSITION/ROLE/PLAY_STYLE`로 각각 매핑 |
| TC-GAME-02 | 권한 | `GET /games` 토큰 없이 | **401** (공개 API가 아님을 고정) |
| TC-GAME-03 | 정상 | `GET /games/PUBG/modes` | **200**, `DUO`(2) · `SQUAD`(4) |
| TC-GAME-04 | 정상 | `GET /games/LOL/modes` | **200**, `SOLO_DUO_RANKED`의 `roleUniqueness == true` |
| TC-GAME-05 | 정상 | `GET /games/LOL/match-schema` | **200**, `keyCondition.values`에 **`ANY` 포함** |
| TC-GAME-06 | 정상 | `GET /games/VALORANT/match-schema` | **200**, `values`에 **`ANY` 미포함** |
| TC-GAME-07 | 검증 | `GET /games/OVERWATCH/modes` | **400** (경로 변수 enum 변환 실패). §11-3 |

> TC-GAME-05/06은 "상관없음 버튼을 세 게임에 똑같이 그리면 안 된다"를 코드로 못 박는다.

---

## 5. Match request (TC-MATCH)

도메인 규칙(같은 포지션 배제, 음성 충돌, 차단)은 `RealtimeMatchingIntegrationTest`에
이미 있다. 여기서는 **HTTP 계약만** 본다.

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-MATCH-01 | 정상 | 알파 | `POST /match-requests` LOL/MID | **201**, `status=="QUEUED"`, `proposalId == null`, `queuedAt` 파싱 가능 |
| TC-MATCH-02 | 검증 | 알파 | `keyCondition.value: "mid"` (소문자) | **201** — 정규화가 동작함을 고정 |
| TC-MATCH-03 | 검증 | 알파 | `voicePreference: "optional"` (소문자) | **400** — enum은 대소문자를 안 봐줌을 고정 |
| TC-MATCH-04 | 검증 | 알파 | LOL + `keyCondition.type: "ROLE"` | **400** `VALIDATION_FAILED` |
| TC-MATCH-05 | 검증 | 알파 | LOL + `value: "JUNGLER"` | **400** `VALIDATION_FAILED` |
| TC-MATCH-06 | 검증 | 알파 | `modeKey: "ARAM"` | **404** `UNKNOWN_GAME_MODE` |
| TC-MATCH-07 | 상태 | 알파 QUEUED | 같은 요청 재전송 | **409** `ACTIVE_MATCH_REQUEST_EXISTS` |
| TC-MATCH-08 | 정상 | 알파 QUEUED | `GET /match-requests/{id}` | **200**, 필드 4개 |
| TC-MATCH-09 | 권한 | 브라보 QUEUED | 알파 토큰으로 브라보 id 조회 | **404** `MATCH_REQUEST_NOT_FOUND` |
| TC-MATCH-10 | 정상 | 알파 QUEUED | `DELETE /match-requests/{id}` | **204**, 이후 재시작이 **201** |
| TC-MATCH-11 | 상태 | 알파 PROPOSED | `DELETE` | **409** `MATCH_REQUEST_NOT_CANCELLABLE` |
| TC-MATCH-12 | 권한 | — | 토큰 없이 `POST /match-requests` | **401**, 본문 없음 |

---

## 6. Proposal (TC-PROP)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-PROP-01 | 정상 | 제안 PENDING | `GET /proposals/{id}` (알파) | **200**, `partyId == null`, `members[].acceptance` 전부 `PENDING` |
| TC-PROP-02 | 권한 | 제안 PENDING (알파·브라보) | **찰리** 토큰으로 조회 | **404** `PROPOSAL_NOT_FOUND` |
| TC-PROP-03 | 정상 | 제안 PENDING | 알파만 accept | **200**, `status=="PENDING"`, 알파만 `ACCEPTED`, `partyId == null` |
| TC-PROP-04 | 정상 | 알파 accept됨 | 브라보 accept | **200**, `status=="CONFIRMED"`, **`partyId != null`** |
| TC-PROP-05 | 상태 | 확정됨 | 알파가 다시 accept | **409** `PROPOSAL_NOT_PENDING` |
| TC-PROP-06 | 상태 | 제안 PENDING | 알파가 accept **2회 연속** | 둘 다 **200**, 확정은 1회 (INV-3 멱등) |
| TC-PROP-07 | 정상 | 확정됨 | `GET /proposals/{id}` 재조회 | **200**, `partyId`가 동일 값으로 계속 실림 |
| TC-PROP-08 | 정상 | 제안 PENDING | 브라보 decline | **204** |
| TC-PROP-09 | 상태 | decline됨 | 알파가 accept | **409** `PROPOSAL_NOT_PENDING` |
| TC-PROP-10 | 상태 | `expiresAt` 경과 | accept | **409** `PROPOSAL_EXPIRED` |
| TC-PROP-11 | 권한 | 제안 PENDING | 찰리가 accept | **404** |

---

## 7. Reservation (TC-RESV)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-RESV-01 | 정상 | 알파 | `POST /reservations` 21:00\~23:00 | **201**, `status=="ACTIVE"`, `scheduledStart/proposalId/partyId` 전부 `null` |
| TC-RESV-02 | 정상 | TC-RESV-01 | 응답 바디 키 집합 검사 | **`createdAt`이 없음을 단언**(§11-1 현행 고정) |
| TC-RESV-03 | 검증 | 알파 | `availableFrom: "...21:15:00Z"` | **400** `VALIDATION_FAILED` |
| TC-RESV-04 | 검증 | 알파 | `availableFrom: "...21:00:30Z"` (초가 0이 아님) | **400** |
| TC-RESV-05 | 검증 | 알파 | `availableTo <= availableFrom` | **400** |
| TC-RESV-06 | 검증 | 알파 | `playAmount` 누락 | **400** |
| TC-RESV-07 | 검증 | 알파 | `modeKey: "ARAM"` | **404** `UNKNOWN_GAME_MODE` |
| TC-RESV-08 | 상태 | 21:00\~23:00 있음 | 22:00\~24:00 생성 | **409** `OVERLAPPING_RESERVATION` |
| TC-RESV-09 | 상태 | 21:00\~23:00 (VALORANT) | 22:00\~24:00 **PUBG**로 생성 | **409** — 게임이 달라도 막힘을 고정 |
| TC-RESV-10 | 정상 | 21:00\~23:00 있음 | 23:00\~24:00 생성 (경계 인접) | **201** — `[from, to)` 반열림 구간 고정 |
| TC-RESV-10b | 정상 | 21:00\~23:00 예약을 **취소함** | 같은 21:00\~23:00 재생성 | **201** — `CANCELLED`는 시간을 점유하지 않음을 고정 |
| TC-RESV-11 | 정상 | 예약 2건 | `GET /reservations` | **200**, 원소 2개 |
| TC-RESV-12 | 권한 | 브라보의 예약 | 알파 토큰으로 `GET /reservations/{id}` | **404** `RESERVATION_NOT_FOUND` |
| TC-RESV-13 | 검증 | 예약 있음 | `PATCH` 바디에서 `playAmount` 생략 | **400** — PATCH가 부분 수정이 아님을 고정 (§11-5) |
| TC-RESV-14 | 정상 | 예약 있음 | `PATCH` 시간 그대로 + 조건만 변경 | **200** — 자기 자신은 겹침 검사에서 제외됨 |
| TC-RESV-15 | 상태 | 예약 PROPOSED | `PATCH` | **409** `RESERVATION_NOT_EDITABLE` |
| TC-RESV-16 | 정상 | 예약 ACTIVE | `DELETE` | **204** |
| TC-RESV-17 | 상태 | 이미 취소됨 | `DELETE` 재호출 | **204** — 취소는 멱등임을 고정 |
| TC-RESV-18 | 정상 | 예약 PROPOSED | `DELETE` | **204**, 제안도 함께 파기됨 |

---

## 8. Party (TC-PARTY)

파티는 생성 API가 없다. 전제는 **제안 확정으로 만든다.**

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-PARTY-01 | 정상 | 파티 OPEN | `GET /parties/{id}` (멤버) | **200**, `members` 2명, `ready` 전부 `false` |
| TC-PARTY-02 | 권한 | 파티 OPEN | **찰리**가 조회 | **404** `PARTY_NOT_FOUND` |
| TC-PARTY-03 | 정상 | 파티 OPEN | 알파 `{"ready":true}` | **200**, `status` 여전히 `OPEN`, 알파만 `ready:true` |
| TC-PARTY-04 | 정상 | 알파 ready | 브라보 `{"ready":true}` | **200**, `status=="READY"` |
| TC-PARTY-05 | 정상 | 알파 ready | 알파 `{"ready":false}` | **200**, 되돌아감 |
| TC-PARTY-06 | 검증 | 파티 OPEN | `{}` (ready 누락) | **400** `VALIDATION_FAILED` |
| TC-PARTY-07 | 상태 | 파티 PLAYING | `{"ready":false}` | **409** `PARTY_PLAYING` |
| TC-PARTY-08 | 상태 | 파티 CLOSED | ready 호출 | **409** `PARTY_CLOSED` |
| TC-PARTY-09 | 정상 | 파티 OPEN (2인) | 알파 `POST /leave` | **204**, 파티가 `CLOSED`가 되고 브라보는 대기열 복귀 |
| TC-PARTY-10 | 상태 | 알파가 나감 | 알파가 다시 `leave` | **409** `ALREADY_LEFT` |
| TC-PARTY-11 | 권한 | 파티 OPEN | 찰리가 `leave` | **404** |
| TC-PARTY-12 | 상태 | — | `POST /parties/{id}/invite/{friendUserId}` | **404** — 미구현임을 고정 (§11-6). 구현되면 이 케이스를 교체한다 |

---

## 9. Friends (TC-FRIEND)

도메인 규칙은 `SocialIntegrationTest`에 두껍게 있다. 여기서는 **HTTP 응답 형태와
상태 코드**만 본다.

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-FRIEND-01 | 정상 | 친구 없음 | `GET /friends` | **200**, `[]` |
| TC-FRIEND-02 | 정상 | 알파-브라보 친구 | `GET /friends` (알파) | **200**, `userId`가 **브라보의 id**임을 단언 |
| TC-FRIEND-03 | 정상 | 알파 | `POST /friend-requests` → 브라보 | **201**, `direction=="SENT"`, `status=="PENDING"` |
| TC-FRIEND-04 | 검증 | 알파 | `targetUserId` 누락 | **400** |
| TC-FRIEND-05 | 검증 | 알파 | `targetUserId: NONE` | **404** `USER_NOT_FOUND` |
| TC-FRIEND-06 | 상태 | 알파 | 자기 자신에게 요청 | **409** `SELF_FRIEND_REQUEST` |
| TC-FRIEND-07 | 상태 | 알파→브라보 PENDING | 알파가 다시 요청 | **409** `REQUEST_ALREADY_PENDING` |
| TC-FRIEND-08 | 상태 | 브라보→알파 PENDING | **알파가** 브라보에게 요청 | **409** `INVERSE_REQUEST_PENDING` (**코드가 07과 다름을 단언**) |
| TC-FRIEND-09 | 상태 | 알파가 브라보 차단 | 알파→브라보 요청 | **409** `BLOCKED_RELATION` |
| TC-FRIEND-10 | 상태 | 브라보가 알파 차단 | 알파→브라보 요청 | **409** `BLOCKED_RELATION` (**같은 코드임을 단언**) |
| TC-FRIEND-11 | 정상 | 브라보→알파 PENDING | `GET /friend-requests` (알파, 기본값) | **200**, `direction=="RECEIVED"` |
| TC-FRIEND-12 | 정상 | 알파→브라보 PENDING | `?direction=SENT` | **200**, 1건 |
| TC-FRIEND-13 | 검증 | — | `?direction=received` (소문자) | **400** |
| TC-FRIEND-14 | 정상 | 브라보→알파 PENDING | 알파가 accept | **200**, 응답이 **`FriendView` 형태**(`friendedAt` 존재, `direction` 없음) |
| TC-FRIEND-15 | 권한 | 알파→브라보 PENDING | **알파(발신자)가** accept | **404** `FRIEND_REQUEST_NOT_FOUND` |
| TC-FRIEND-16 | 상태 | 이미 accept됨 | 다시 accept | **409** `FRIEND_REQUEST_NOT_PENDING` |
| TC-FRIEND-17 | 정상 | 브라보→알파 PENDING | 알파가 decline | **204** |
| TC-FRIEND-18 | 정상 | 알파→브라보 PENDING | 알파가 `DELETE /friend-requests/{id}` | **204** |
| TC-FRIEND-19 | 권한 | 알파→브라보 PENDING | **브라보가** `DELETE` | **404** |
| TC-FRIEND-20 | 정상 | 친구 | `DELETE /friends/{브라보}` | **204**, **양쪽 목록에서 사라짐** |
| TC-FRIEND-21 | 상태 | 친구 아님 | `DELETE /friends/{브라보}` | **404** `FRIENDSHIP_NOT_FOUND` |

---

## 10. Blocks (TC-BLOCK)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-BLOCK-01 | 정상 | 차단 없음 | `GET /blocks` | **200**, `[]` |
| TC-BLOCK-02 | 정상 | 알파 | `POST /blocks` → 찰리 | **201**, 필드 `{userId, nickname, blockedAt}` **3개뿐**(avatarUrl 없음) |
| TC-BLOCK-03 | 검증 | 알파 | `targetUserId` 누락 | **400** |
| TC-BLOCK-04 | 검증 | 알파 | `targetUserId: NONE` | **404** `USER_NOT_FOUND` |
| TC-BLOCK-05 | 상태 | 알파 | 자기 자신 차단 | **409** `SELF_BLOCK` |
| TC-BLOCK-06 | 상태 | 이미 차단 | 재차단 | **409** `ALREADY_BLOCKED` |
| TC-BLOCK-07 | 정상 | 알파-브라보 친구 + 알파→찰리 PENDING | 알파가 브라보 차단 | **201**, 그 뒤 `GET /friends`가 `[]` |
| TC-BLOCK-08 | 정상 | 차단됨 | `DELETE /blocks/{찰리}` | **204** |
| TC-BLOCK-09 | 상태 | 차단 안 함 | `DELETE /blocks/{찰리}` | **404** `BLOCK_NOT_FOUND` |
| TC-BLOCK-10 | 정상 | 차단 해제 후 | 다시 친구 요청 | **201** — 차단 해제가 관계를 다시 열어줌 |

---

## 11. Recent players / Reports (TC-MISC)

| ID | 축 | 전제 | 요청 | 기대 |
|---|---|---|---|---|
| TC-MISC-01 | 정상 | 이력 없음 | `GET /recent-players` | **200**, `[]` |
| TC-MISC-02 | 정상 | 완료 파티 1건 | `GET /recent-players` | **200**, `playCount`·`friend` 필드 포함 |
| TC-MISC-03 | 검증 | — | `?limit=0` | **현재 500.** 400으로 고칠 때 이 케이스를 뒤집는다 (§11-7) |
| TC-MISC-04 | 검증 | — | `?limit=51` | **현재 500.** 위와 동일 |
| TC-MISC-05 | 정상 | — | `?limit=1` / `?limit=50` (경계) | **200** |
| TC-MISC-06 | 정상 | 알파 | `POST /reports` 정상 | **201**, **본문 없음을 단언** |
| TC-MISC-07 | 검증 | 알파 | `reason` 누락 | **400** |
| TC-MISC-08 | 검증 | 알파 | `description` 1001자 | **400** |
| TC-MISC-09 | 검증 | 알파 | `reason: "SPAM"` (enum 밖) | **400** |
| TC-MISC-10 | 상태 | 알파 | 자기 자신 신고 | **409** `SELF_REPORT` |
| TC-MISC-11 | 상태 | 이미 신고함 | 같은 대상 재신고 | **201** — 중복 신고가 허용됨을 고정 |
| TC-MISC-12 | 검증 | 알파 | `targetUserId: NONE` | **404** `USER_NOT_FOUND` |

---

## 12. 횡단 케이스 (TC-X)

리소스별이 아니라 **모든 엔드포인트에 공통**으로 도는 케이스다.
파라미터화 테스트 하나로 전체 엔드포인트 목록을 돌리는 것을 권한다.

| ID | 대상 | 요청 | 기대 |
|---|---|---|---|
| TC-X-01 | 인증 필요한 엔드포인트 **전부** | `Authorization` 없이 호출 | **401**, 본문 비어 있음 |
| TC-X-02 | 인증 필요한 엔드포인트 전부 | 만료된 access token | **401** |
| TC-X-03 | 공개 엔드포인트 3개 | 토큰 없이 | **401이 아님** |
| TC-X-04 | 바디를 받는 엔드포인트 전부 | `Content-Type` 없이 | **415** |
| TC-X-05 | 바디를 받는 엔드포인트 전부 | 깨진 JSON (`{`) | **400** |
| TC-X-06 | 경로에 uuid를 받는 엔드포인트 전부 | `{id}` 자리에 `not-a-uuid` | **400** |
| TC-X-07 | 4xx를 내는 모든 케이스 | — | 바디가 `{code, message}` **2필드이고 `code`가 §0.7 카탈로그 안에 있음** |
| TC-X-08 | 201을 내는 엔드포인트 전부 | — | **`Location` 헤더가 없음** |
| TC-X-09 | 날짜를 내는 모든 응답 | — | `OffsetDateTime.parse()`가 성공 |

> **TC-X-07이 이 문서에서 가장 값이 큰 케이스다.** 새 에러 코드가 카탈로그 없이
> 늘어나거나, 예외 핸들러를 안 타는 경로가 생기면 여기서 잡힌다.
> §11-3(enum 역직렬화)과 §11-7(limit 검증)은 지금 이 케이스를 **깨뜨린다.**

---

## 13. 실제 구현 — 어디에 무엇이 있나

| 테스트 클래스 | 덮는 케이스 | 개수 |
|---|---|---|
| `ApiContractTestSupport` | 공통 바탕 (컨테이너·토큰·픽스처·단언 헬퍼) | — |
| `AuthApiContractTest` | TC-AUTH | 15 |
| `UserApiContractTest` | TC-USER | 16 |
| `GameConfigApiContractTest` | TC-GAME | 7 |
| `MatchRequestApiContractTest` | TC-MATCH | 12 |
| `ReservationApiContractTest` | TC-RESV | 20 |
| `SocialApiContractTest` | TC-FRIEND · TC-BLOCK | 27 |
| `MiscApiContractTest` | TC-MISC | 11 |
| `CrossCuttingApiContractTest` | TC-X (엔드포인트 33개 × 2 파라미터화 + 7) | 80 |
| **합계** | | **188** |

컨테이너는 `@Container`가 아니라 static 블록에서 한 번만 띄운다. 클래스마다 껐다 켜면
Flyway가 8번 다시 돌아 느리다. 8개 클래스 188개가 **약 23초**에 끝난다.

### 아직 코드로 없는 것

**TC-PROP(11개)과 TC-PARTY(12개)는 이 묶음에 넣지 않았다.** 두 리소스는 생성 API가 없어
"제안이 확정된 파티"라는 전제를 만들려면 매칭 엔진을 통과시키거나 `match_proposals` 테이블에
직접 INSERT해야 한다. 그 상태 전이는 이미 `RealtimeMatchingIntegrationTest`,
`ProposalRaceIntegrationTest`, `PartyLifecycleIntegrationTest`,
`PartyDepartureIntegrationTest`가 두껍게 덮고 있어 중복이 크다.

다만 **HTTP 계약(상태 코드·바디 형태·404 vs 403)은 여전히 미검증**이다.
채우려면 `PartyDepartureIntegrationTest`의 `party(...)` 헬퍼를 `ApiContractTestSupport`로
끌어올리면 된다. 넣을지는 팀이 정한다.

한편 **더미 서버(`mock-server/`)는 제안·파티를 전부 구현**했고 스모크 테스트가
그 흐름(매칭 → 제안 → 수락 → 확정 → 파티 → ready → leave)을 끝까지 돌린다.

### v2 계약으로 뒤집은 케이스

docs/14 §11의 13건을 정리하면서 아래 케이스의 기대값이 바뀌었다.
전에는 "버그가 이렇다"를 못 박아 뒀고, 지금은 **정상 동작을 단언한다.**

| 케이스 | v1 (버그) | v2 (지금) |
|---|---|---|
| `TC-USER-07` | `avatarUrl: null` 무시 | **삭제된다** |
| `TC-USER-07b` | — | 키를 빼면 유지된다 |
| `TC-USER-07c` | — | `nickname: null`은 400 |
| `TC-USER-10` | `code` 없는 400 | `VALIDATION_FAILED` |
| `TC-USER-02` | 401 본문 없음 | `{code: "UNAUTHORIZED"}` |
| `TC-RESV-02` | `createdAt` 없음 | **실린다** |
| `TC-RESV-13` | `PATCH` 전체 교체 | `PUT`이 정본 |
| `TC-RESV-13b` | — | `PATCH`는 별칭 |
| `TC-MISC-03·04` | `limit` 위반 → **500** | 400 |
| `TC-FRIEND-14` | `friendedAt`이 `null` | **실린다** |
| `TC-BLOCK-02` | `blockedAt`이 `null` | **실린다** |
| `TC-AUTH-14` | 로그아웃에 인증 필요 | **공개** |
| `TC-GAME-07` | `code` 없는 400 | `VALIDATION_FAILED` |

### 이 작업과 무관하게 발견된 기존 테스트 실패

전체 스위트(483개)를 돌렸을 때 `PartyDepartureIntegrationTest`의 3개가 실패했다.
**이 문서의 작업이 건드리지 않은 파일이다.**

```
party_members_left_at_check 위반 (left_at IS NULL OR left_at >= joined_at)
Failing row: ... 2026-09-05 14:39:14.650418+09, 2026-09-05 14:39:14.65007+09
```

`joined_at`은 Postgres의 `now()`(트랜잭션 시작 시각)이고 `left_at`은 JVM의
`OffsetDateTime.now()`다. **두 시계가 다르다.** 348µs 차이로 `left_at`이 앞서면 제약에
걸린다. 부하나 컨테이너 시계 상태에 따라 붙었다 떨어졌다 하는 경쟁 조건이다.

고치려면 `left_at`도 DB 시각으로 잡거나, 제약을 시각 비교 대신 `left_at IS NULL OR
left_at >= joined_at - interval '1 second'` 같은 여유를 두는 쪽이다. 판단은 팀이 한다.

---

## 14. 원래 계획 — 개수와 우선순위

| 도메인 | 케이스 수 |
|---|---|
| Auth | 13 |
| User / Game account | 16 |
| Game config | 7 |
| Match request | 12 |
| Proposal | 11 |
| Reservation | 19 |
| Party | 12 |
| Friends | 21 |
| Blocks | 10 |
| Misc | 12 |
| 횡단 | 9 |
| **합계** | **142** |

작성 순서에 대한 의견 — 결정은 팀이 한다.

1. **TC-X (횡단 9개)** 먼저. 파라미터화 하나로 전체를 훑어 §11의 버그가 실제로
   존재하는지 코드로 확정한다. 투자 대비 회수가 가장 크다.
2. **TC-USER · TC-FRIEND · TC-BLOCK.** HTTP 계층 검증이 0인데 화면이 바로 붙는 곳이다.
3. **TC-RESV · TC-PARTY.** 상태 전이가 많아 계약이 흔들리기 쉽다.
4. **TC-AUTH · TC-MATCH · TC-PROP.** 도메인 테스트가 이미 두꺼워 겹치는 부분이 있다.
