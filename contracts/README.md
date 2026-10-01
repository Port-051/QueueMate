# contracts/ — app:matching 이 노출하는 계약

## 이게 뭔가

`contracts/openapi.yaml` 과 `contracts/events.md` 는 팀 간 계약이다.
**원본은 queueMate 본 저장소의 `contracts/`** 이고, 여기 있는 두 파일은
**`app:matching` 이 노출하는 부분만 발췌한 사본**이다.

| 파일 | 원본 | 발췌 기준 |
|---|---|---|
| `openapi.yaml` | queueMate `feature/frontend:contracts/openapi.yaml` (662줄) | `/games`, `/match-requests*`, `/proposals/{id}/accept|decline` + 그에 필요한 스키마 |
| `events.md` | queueMate `feature/frontend:contracts/events.md` (110줄) | `MATCH_*` 5종. SQS FIFO 는 **0개** — 원본은 `ProposalConfirmed` 생산 + `BlockChanged` 소비 2개였다. `BlockChanged` 는 docs/11 D-12 로 폐기(아래 A-5), `ProposalConfirmed` 는 docs/11 D-42 로 만들지 않는다(아래 A-15) |

**계약을 바꿔야 하면 여기서 바꾸지 마라.** queueMate 본 저장소에서 contract 변경 커밋을
먼저 만들고, 그 뒤 이 사본을 다시 뜬다. 소유 영역 밖 계약을 임의로 바꾸지 않는다.

### 이 사본이 원본보다 앞서간 변경

위 규칙의 예외다. **원본 저장소가 이 컴퓨터에 없어** 원본을 먼저 바꿀 수 없었고, 결정이 난
내용을 사본에 먼저 적었다. 사본의 각 자리에는 `> 개정 이력:` 으로 무엇을 언제 왜 바꿨는지
남겨 두었다. **원본(queueMate `feature/frontend`)에 contract 변경 커밋으로 반영해야 하고,
반영되면 이 표에서 지운다.**

| # | 날짜 | 파일 · 자리 | 원본 | 이 사본 | 원본에 반영할 것 |
|---|---|---|---|---|---|
| A-1 | 2026-09-18 | `events.md` "재연결" | `Last-Event-ID` 로 유한 버퍼에서 재개한다 | **재전송하지 않는다.** 클라이언트가 재연결 직후 상태를 조회한다 | `events.md` "재연결" 절 |
| A-2 | 2026-09-19 | `events.md` 맨 위 전송 표 · `app:realtime` 설명 · "이 저장소가 전송하지 않는 것" · 새 절 "`WEBRTC_SIGNAL` 의 전달" | 전송 2종 — SSE 14종 + WebSocket(`/ws`) `WEBRTC_SIGNAL` 1종 | **전송은 SSE 하나, 15종.** `/ws` 는 없다. `WEBRTC_SIGNAL` 은 SSE 로 받고, 보내는 쪽은 `app:platform` 의 REST `POST` 다 (docs/11 D-9) | ① `events.md` 전송 표(SSE 15종)와 `WEBRTC_SIGNAL` 전달 규약 ② `/ws` 제거 ③ `openapi.yaml` 에 `app:platform` 의 시그널 `POST` 엔드포인트 추가 — **경로와 요청/응답 스키마, `WEBRTC_SIGNAL` 의 `payload` 스키마는 미정이다.** 이 저장소의 `openapi.yaml` 발췌본은 `app:matching` 엔드포인트만 담으므로 고치지 않았다 |
| A-3 | 2026-09-19 | `events.md` "heartbeat" | 서버는 15~30초마다 heartbeat(**코멘트 라인**)를 보낸다. 클라이언트가 할 일은 적혀 있지 않다 | **이름 있는 이벤트**(`event: heartbeat` / `data: heartbeat`)로 보낸다. 간격(15~30초)과 기존 역할(idle timeout, 서버의 죽은 연결 정리)은 그대로다. 클라이언트는 `addEventListener("heartbeat", ...)` 로 받고(`onmessage` 로는 오지 않는다), 일정 시간(권장 60초 정도) 아무것도 오지 않으면 연결을 닫고 새로 연 뒤 상태를 조회한다 (docs/11 D-10) | `events.md` "heartbeat" 절 — 형식, 클라이언트 감시 규칙, 이유. 감시 기준 시간은 권장값이지 확정 수치가 아니다 |
| A-4 | 2026-09-19 | `events.md` "재연결" 의 `retry:` 항목 | 발췌본에 `retry:` 서술이 없다 (원본 전문은 이 컴퓨터에 없어 확인하지 못했다) | 서버가 **연결할 때 한 번** SSE `retry:` 로 재접속 대기 시간을 내려 준다. 값은 연결마다 무작위다(현재 구현 기본 1000~2000ms, 설정 가능). 재배포 직후 재접속 몰림을 흩는다. 클라이언트가 할 일은 없다 (docs/11 D-10) | `events.md` "재연결" 절에 `retry:` 항목 추가. 범위 수치는 구현 기본값이지 계약 값이 아니다 |
| A-5 | 2026-09-19 | `events.md` "서버 간 이벤트 — SQS FIFO" 의 큐 표 | `app:matching` 이 걸린 큐는 2개 — `ProposalConfirmed.fifo` 생산 / `BlockChanged.fifo` 소비(`qm:block:{userId}` read model 갱신) | **`BlockChanged.fifo` 는 폐기됐다 — 만들지 않는다.** 이 앱이 걸린 큐는 `ProposalConfirmed.fifo` 생산 1개다(→ 2026-09-27 A-15 로 그것도 없어져 **0개**). 차단은 `app:platform` 이 `social.blocks`(2026-09-26 부터 `public.blocks` — docs/11 D-34)에 저장하면 끝이고, 이 앱은 확정 직전에 그 테이블을 직접 조회한다 (docs/11 D-12 · D-1) | `events.md` 의 큐 목록에서 `BlockChanged.fifo` 와 `qm:block:{userId}` read model 서술 제거. 원본의 큐는 3개 → 2개가 된다 |
| A-6 | 2026-09-19 | `events.md` 같은 절의 `PartyClosed.fifo` 한 줄 | 발췌본에 `PartyClosed.fifo` 서술이 없다 (원본 전문은 이 컴퓨터에 없어 확인하지 못했다) | **`PartyClosed.fifo` 의 소비자는 `app:platform` 하나다.** `app:matching` 은 이 큐를 읽지 않는다 (docs/11 D-13) | 원본 `events.md` 의 `PartyClosed.fifo` 소비자 표기를 `app:platform` 하나로 분명히 한다 |
| A-7 | 2026-09-19 | `events.md` "`app:matching` 이 발행하는 5종" 머리의 발행 주체 목록 | `RESERVATION_*` 2종의 발행 주체는 `app:reservation-batch` 다. 예약 REST 는 `app:platform` 이 서빙한다 | **`app:reservation`(AWS Lambda)이 `app:reservation-batch` 를 대체한다.** 예약 등록 REST 도 `app:platform` 이 아니라 `app:reservation` 의 일이다 (docs/11 D-15). 발행 방식과 종류 수는 그대로다 | `events.md` 의 발행 주체 표기. `openapi.yaml` 의 `/reservations` 소관 표기(`app:platform` → `app:reservation`) — HTTP 진입점과 경로 라우팅은 미정이다 |
| A-8 | 2026-09-19 | `events.md` 맨 위 개정 이력 · 발행 주체 목록 · "`WEBRTC_SIGNAL` 의 전달" · "이 저장소가 전송하지 않는 것" | 원본에는 `app:room` 이 없다. A-2 는 시그널 `POST` 를 받고 `WEBRTC_SIGNAL` 을 발행하는 앱을 `app:platform` 으로 적었다 | **`app:room` 이 시그널 `POST` 를 받고 `WEBRTC_SIGNAL` 을 발행한다** (docs/11 D-16 — 방을 별도 서비스로 분리). 확인 대상은 "같은 방에 들어와 있는 사람"(자동 매칭 파티방은 같은 파티원)이다. `PARTY_*` 가운데 방 입장 · 퇴장 · 강퇴 알림의 발행 주체와 `type` 은 **미정**이라 옮기지 않았다 | A-2 의 ③ 을 고쳐 읽는다 — 시그널 `POST` 엔드포인트는 `app:platform` 이 아니라 `app:room` 의 것이다. 발행 주체 표에 `app:room` 추가. 경로 · 스키마 · `payload` 는 여전히 미정이다 |
| A-9 | 2026-09-19 | (이 사본에 해당 자리 없음 — `events.md` · `openapi.yaml` 발췌본은 예약 배치의 주기를 적지 않는다) | 원본 `docs/04` §6 · #23 — 예약 매칭은 **1분 주기** 배치 단일 경로, 최소 리드타임 30분, 시간 기반 tier 완화 | **예약 짝 찾기 배치는 요일 구분에 따라 하루 중 정해진 시각에만 돈다**(시각은 설정값). "배치 단일 경로"는 그대로다. 리드타임 · tier 완화 · 제안 수락 방식은 **미정**이 됐다 (docs/11 D-17) | 원본 `docs/04` §6 · §6-1 · §6-2 와 결정 로그 #23. `openapi.yaml` 의 `/reservations` 가 "슬롯 시작까지 30분 미만이면 `400`"을 적고 있다면 그 규칙도 D-17 의 미정에 걸린다(원본 전문은 이 컴퓨터에 없어 확인하지 못했다) |
| A-10 | 2026-09-19 | `openapi.yaml` `POST /match-requests` 의 `409` · `ErrorResponse.code` | `409` 는 "User already has active request" 하나다. 에러 코드는 `ALREADY_QUEUED` 뿐이다 | **`409` 의 에러 코드가 둘이 된다 — `ALREADY_QUEUED`(이미 활성 요청이 있다) / `IN_ROOM`(게시판 방에 들어가 있다).** `claim-request.lua` 가 `app:room`(2026-09-25 부터 `app:platform` — D-33)의 입장 표시 키 `qm:user:active-room:{userId}` 를 `EXISTS` 로 보고 있으면 거절한다. 한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 15번). **D-11 16번("활성 요청 키 하나로 지킨다")을 키 둘로 개정한 결정에 따른 것이다 (docs/11 D-19)** | `openapi.yaml` 의 `409` 설명과 `ErrorResponse.code` enum 에 `IN_ROOM` 추가 |
| A-11 | 2026-09-26 | `events.md` 맨 위 개정 이력 · 발행 주체 목록 · "`WEBRTC_SIGNAL` 의 전달" · "이 저장소가 전송하지 않는 것" | 원본에는 `app:room` 이 없다(A-8 이 사본에만 `app:room` 을 넣었다) | **A-8 을 되돌린다 — 2026-09-25 에 `app:room` 을 `app:platform` 에 합쳤다**(docs/11 D-33). 시그널 `POST`(`POST /api/v1/rooms/{roomId}/signals` — docs/11 D-21)를 받고 `WEBRTC_SIGNAL` 과 방 알림 `ROOM_*` 다섯을 발행하는 것은 `app:platform`(의 `room` 패키지)이다. 알림 채널의 `{userId}` 는 사용자 번호의 십진 문자열이다(docs/11 D-25) | A-2 의 ③(시그널 `POST` 는 `app:platform` 의 것)이 **다시 맞다.** 원본에 올릴 것 — 시그널 경로와 스키마(`../platform/contracts/platform-api.md` "방"), `ROOM_*` 다섯의 이름과 `payload`(원본의 `PARTY_*` 와 같은 뜻인지 맞춰야 한다) |
| A-12 | 2026-09-27 | `openapi.yaml` 의 `components.securitySchemes` · 전역 `security` · `/match-requests*` · `/proposals/*` 의 설명과 `401` · `403` · `CreateMatchRequest` 머리 주석 · `ErrorResponse.code` | 발췌본에 `securitySchemes` 가 없다(원본 전문은 이 컴퓨터에 없어 확인하지 못했다 — 아래 표 #10 은 "JWT bearer" 로 적었다) | **인증은 쿠키 `qm_access` 의 access 토큰(RS256 JWT)이다** — `cookieAuth`(`type: apiKey` · `in: cookie` · `name: qm_access`). `app:platform` 이 발급하고 이 앱은 공개 키로 검증만 한다(서명 · `exp` · `iss` · `token_use == access` · `sub` 숫자). **"나"는 `sub`(사용자 번호의 십진 문자열)다 — 요청 바디의 `userId` 와 쿼리 파라미터 `?userId=` 는 없어졌다.** 실패는 `401 UNAUTHENTICATED` 하나다. 상태를 바꾸는 요청은 `Origin` 을 허용 목록과 대조한다(`403 ORIGIN_NOT_ALLOWED` — `Origin` 이 없으면 통과) (docs/11 D-14 · D-24 · D-25) | `openapi.yaml` 에 `cookieAuth` 보안 스킴과 전역 `security`, 모든 엔드포인트의 `401` · 상태 변경 엔드포인트의 `403 ORIGIN_NOT_ALLOWED`, `ErrorResponse.code` 에 둘 추가. 원본이 bearer 로 적혀 있다면 cookie 로 고친다 |
| A-13 | 2026-09-27 | `openapi.yaml` 의 `VoicePreference` · `KeyCondition.type` · `CreateMatchRequest`(`tier`) · `MatchRequestView` · `GET /match-requests`(경로) · `POST /match-requests` 의 `201` 설명 · `info.description`(공통 응답 규칙) · 모든 엔드포인트의 `503` · `ErrorResponse.code` · 머리 ★ 주석. `events.md` "재연결"의 상태 조회 경로 | `VoicePreference` 는 `[REQUIRED, OPTIONAL, NO_VOICE]`, `KeyCondition.type` 의 PUBG 값은 `PLAY_STYLE`, `CreateMatchRequest` 에 `tier` 없음, `MatchRequestView` 는 `{ id, status, queuedAt(date-time), proposalId }` 4개 · status 5값, 상태 조회는 `GET /match-requests/{requestId}`(경로 변수), `503` 없음 | **아래 불일치 표에서 "코드가 맞다"로 판정된 것을 사본에 적었다.** `VoicePreference` = `[REQUIRED, NO_VOICE]`(#1). `KeyCondition.type` = `[POSITION, ROLE, PLATFORM]`, PUBG 값은 `STEAM` / `KAKAO`(#14). `CreateMatchRequest` 에 선택 필드 `tier`(String, `tierRule != NONE` 인 모드에서 사실상 필수, 값은 `GOLD_2` 처럼 단까지, 원본은 Redis ZSET `qm:gameconfig:{GAME}:tier`, 계정 연동이 붙으면 사라진다 — #13)(**→ A-18** — 바디에 남는다). 상태 조회는 경로 변수 없는 `GET /match-requests`("나" = 쿠키의 `sub`, 항상 200, 없으면 `{"status":"IDLE"}` — #5). `POST /match-requests` 의 201 본문은 `MatchRequestView` 의 QUEUED 갈래(#5-1 — 구현을 고쳤다). **`MatchRequestView` 는 구현이 지금 돌려주는 8개 필드** `{status, requestId, queuedAt(epoch millis), partyId, target, memberCount, expiresAt(epoch millis), isAccepted}` 와 status `[IDLE, QUEUED, PROPOSED, MATCHED]` 로 적었다 — 뜻이 없는 필드는 응답에 나타나지 않는다(`@JsonInclude(NON_NULL)`). **이름 · 타입(`id` vs `requestId`, `proposalId` vs `partyId`, `queuedAt` 의 date-time vs epoch millis)과 `CANCELLED`/`EXPIRED` 를 남길지는 정하지 않았다**(#4 · #4-1). `503 MATCHING_UNAVAILABLE` + `Retry-After: 5` 를 모든 엔드포인트에 적고 규칙은 `info.description` 에 한 번 적었다(#9). `ErrorResponse.code` 에 `PROPOSAL_NOT_FOUND` · `PROPOSAL_CONFLICT` · `NOT_PROPOSAL_MEMBER` 추가. `events.md` "재연결"의 `?userId=` 제거 | `openapi.yaml` 의 위 자리 전부. **정해야 할 것 둘** — ① `MatchRequestView` 의 필드 이름 · `queuedAt`/`expiresAt` 의 타입 · `CANCELLED`/`EXPIRED` 존치 ② 상태 조회를 `GET /match-requests` 로 둘지 `/me` 로 옮길지. 이 둘이 정해지기 전에는 코드도 사본도 이름을 바꾸지 않는다 |
| A-14 | 2026-09-27 | `openapi.yaml` `/proposals/{id}/accept` · `/proposals/{id}/decline` · `ProposalView` · `ProposalMember` | `accept` 는 `200` + `ProposalView`(members · acceptance · expiresAt …), `decline` 은 `200`(본문 없음). 쿼리 파라미터 없음. 401/403/404/409 갈래 없음 | **둘 다 `204`, 본문 없음**(#6 · #6-1). `ProposalView` · `ProposalMember` 스키마는 **지웠다** — 참조하는 곳이 없어졌고 실을 값도 없다(수락 진행상황을 싣지 않는 이유는 그 숫자가 바뀔 때 알려 주는 이벤트가 계약에 없어서다 — `events.md` "미해결 계약 구멍"). `decline` 은 **쿼리 파라미터를 받지 않는다** — 본인을 큐에서 빼는 데 쓰는 요청 id 는 서버가 활성 요청 HASH 에서 읽는다(구현이 한때 `?requestId=` 를 필수로 받았으나 2026-09-27 에 없앴다. 스크립트가 멤버 여부를 먼저 보므로 취소와 달리 옛 요청이 끼어들 틈이 없다). 응답 갈래 — `401 UNAUTHENTICATED` / `403 ORIGIN_NOT_ALLOWED` · `NOT_PROPOSAL_MEMBER` / `404 PROPOSAL_NOT_FOUND`(만료 · 누가 거절함 · 거절 재시도) / `409 PROPOSAL_CONFLICT`(이미 확정 · 수락해 놓고 거절 · 다른 참가자가 거절) / `503`. **수락은 멱등**(이미 확정된 제안에 재전송해도 `204` — #6-2), **거절은 멱등이 아니다**(재시도는 `404`). `{id}` 는 proposalId = partyId 다 | `openapi.yaml` 의 두 엔드포인트와 스키마 둘 삭제. 원본이 `ProposalView` 를 다른 곳(예: `/parties`)에서도 쓰고 있다면 거기는 그대로 둔다 — 이 발췌본에서만 없앴다 |
| A-15 | 2026-09-27 | `events.md` "서버 간 이벤트 — SQS FIFO" 절 · `MATCH_CONFIRMED` 행 · 머리 ★ 주석 · "미해결 계약 구멍" 의 `PARTY_CREATED` 항목 | 이 앱이 `ProposalConfirmed.fifo` 를 **생산**하고 `app:platform` 이 소비해 파티를 DB 에 만든다(transactional outbox → SQS FIFO, #18 · #21) | **큐 0개 — `ProposalConfirmed.fifo` 를 만들지 않는다** (docs/11 D-42). 확정되면 `cleanup-confirmed.lua` 가 파티 HASH `qm:party:{partyId}` 에 `status=CONFIRMED` · `confirmedAt` · `game` · `modeKey` · `voicePreference` · `playPurpose` 를 채우고(기존 `target` · `member:{userId}=keyValue` · 티어 모드의 `tierLo`/`tierHi` 그대로) TTL 600초를 건다. `MATCH_CONFIRMED {partyId}` 를 받은 클라이언트가 `app:platform` 의 "이 매칭으로 파티 만들기" 를 부르고, platform 이 그 HASH 를 읽어 파티와 방을 만들며 파티원 전원에게 입장 표시 키를 찍는다(`partyId` 로 한 번만). **HASH 필드 이름이 platform 과의 계약이다.** 활성 요청(`status=PARTY`) · 수락자 SET 은 60초 뒤 만료 — 그 뒤 새 매칭이 가능하다 | 원본 `events.md` 의 큐 목록에서 `ProposalConfirmed.fifo` 삭제(원본의 큐는 A-5 뒤 2개 → 1개, `PartyClosed.fifo` 만 남고 그것도 platform 안의 일이다). `../platform/contracts/platform-api.md` 에 "이 매칭으로 파티 만들기" 진입점 추가 — **경로 · 요청 본문 · 에러 코드 · 입장 키를 지우는 때(D-36)는 미정**이다. 파티 HASH 필드 표를 platform 쪽 계약에도 같이 적는다 |
| A-17 | 2026-09-28 | `events.md` 머리의 발행 주체 목록("`PARTY_*`·`FRIEND_*` 7종 = `app:platform`") · "미해결 계약 구멍" 의 `PARTY_CREATED` 항목 | 원본은 `PARTY_*` 5종을 `app:platform` 의 알림으로 둔다 | **`PARTY_*` 는 두지 않는다**(docs/11 D-44 · platform P-31, 2026-09-28 소유자 결정). 파티가 생기는 계기는 프런트가 `MATCH_CONFIRMED` 를 받아 platform 의 방 만들기를 부르는 것이라 알릴 것이 없고, 방 안의 일은 `ROOM_*` 5종이 맡는다. 확정된 파티를 조회하는 경로도 두지 않는다 | 원본 `events.md` 에서 `PARTY_*` 5종 삭제 — 남는 것은 `MATCH_*` 5 · `FRIEND_*` 2 · `ROOM_*` 5 · `WEBRTC_SIGNAL` · `BOARD_CHANGED` · `RESERVATION_*` 2 |
| A-16 | 2026-09-28 | `openapi.yaml` 새 경로 `POST /match-requests/heartbeat` · `ErrorResponse.code` 의 `MATCH_REQUEST_NOT_FOUND` 주석 | 원본에 없다 | **대기 중인 요청의 접속 확인(heartbeat)** (docs/11 D-43). 대기 화면이 열려 있는 동안 클라이언트가 **30초마다** 본문 없이 보내고, 서버는 마지막 신호로부터 **90초**(`queuemate.alive.grace-ms`) 안에 다음 신호가 없으면 그 요청을 취소한다(신호 세 번 놓치면 빠짐 — 남은 파티원에게 `MATCH_CANCELLED`). 응답은 `204`(활성 요청이 있어 시한을 밀었다) / `404 MATCH_REQUEST_NOT_FOUND`(없다 — 클라이언트는 `GET /match-requests` 로 다시 조회) / `401` · `403 ORIGIN_NOT_ALLOWED` · `503`. 새 에러 코드는 없다. 닫기 신호(`pagehide` 의 `keepalive` DELETE)는 두지 않는다. 확정된 요청(`MATCHED`)은 서버가 취소하지 않으므로 보내지 않아도 된다 | `openapi.yaml` 에 엔드포인트 추가 + **클라이언트 규약**(대기 화면에서 30초 주기, 유예 90초, 404 면 상태 재조회)을 원본 계약의 클라이언트 지침 자리에 적는다 |
| A-18 | 2026-09-29 | `openapi.yaml` `CreateMatchRequest.tier` 의 설명 · (사본 밖) `seed/gameconfig.redis` 의 모드 HASH | 원본에 `tier` 가 없다(A-13) — 그러니 "어느 사다리의 티어인가" 도 없다 | **`tier` 는 그 모드의 랭크 사다리의 티어다** (docs/11 D-48 · `../platform/contracts/platform-api.md` P-36). gameconfig 의 `tierRule EXIST` 모드 HASH 10개에 사다리 키 `tierLadder`(LoL `SOLO` · `FLEX` / VALORANT `COMPETITIVE` / PUBG `RANKED` — 시즌 36 통합이라 하나)를 더했고, 클라이언트는 `app:platform` 에 사다리마다 저장된 티어 가운데 그 모드의 `tierLadder` 것을 골라 `tier` 에 싣는다. **`tier` 는 바디에 남는다** — A-13 · #13 의 "계정 연동이 붙으면 사라진다" 를 고친다. 이 앱은 `tierLadder` 를 읽지 않는다(엔진 코드 변경 없음 — 받은 `tier` 를 그 모드의 티어로 쓴다) | A-13 의 `tier` 를 원본에 올릴 때 "그 모드의 사다리(`tierLadder`)의 티어 · 바디에 남는다" 까지 같이 적는다. gameconfig 의 키 · 필드 모양은 원본 계약에 없다 — `seed/gameconfig.redis` 가 원본이다 |
| A-19 | 2026-09-29 | `openapi.yaml` `PlayPurpose` 의 enum · `events.md` "서버 간 이벤트" 절의 파티 HASH 필드 표(`playPurpose`) | `PlayPurpose` 는 `[RANK_UP, NORMAL, FUN]` 이다 | **`NORMAL`(일반 플레이)이 `TRYHARD`(빡겜)가 됐다 — `[RANK_UP, TRYHARD, FUN]`** (docs/11 D-49 — 소유자 결정. 값 이름 `TRYHARD` 는 Claude 가 정했다). 뜻도 바뀌었다 — "평범하게 한다" 가 아니라 "진지하게(빡세게) 한다". 요청 본문 `CreateMatchRequest.playPurpose`(`MatchCondition` 이 같은 스키마를 쓴다)에 옛 이름 `NORMAL` 을 보내면 **400** 이다(호환 이름을 두지 않았다). 파티 HASH 의 `playPurpose` **필드 이름은 그대로**이고 값만 바뀐다 | 원본 `openapi.yaml` 의 `PlayPurpose` enum 을 `[RANK_UP, TRYHARD, FUN]` 으로. 원본 `events.md` 에 파티 HASH 필드 표가 없으면(A-15) A-15 를 올릴 때 새 값으로 적는다 |
| A-20 | 2026-10-01 | `events.md` "서버 간 이벤트" 절의 파티 HASH 필드 표(`status` · `member:{userId}` 행)와 그 아래 "제안 중에도 읽는다" 단락 | 원본에 파티 HASH 의 계약이 없다(A-15 가 사본에 먼저 적었다) | **`app:platform` 이 제안 중(`status=PENDING`)인 파티 HASH 도 `HGETALL` 로 읽는다** (docs/11 D-56 — 2026-10-01 소유자 결정 · `../platform/contracts/platform-api.md` P-47). 빠른매치 파티의 팀원 카드 `GET /api/v1/match-parties/{partyId}/members?game=` 의 자격 확인 — 부른 사람의 `member:{나}` 와 `status` 를 본다. 그래서 **배정 스크립트가 쓰는 `status` · `member:{userId}` 의 이름과 값 모양이 확정 전에도 앱 사이의 계약**이 된다(대조 테스트는 없다). 쓰기 · 지우기 · `EXPIRE` 금지는 그대로. 제안 중에는 `game` 이 없어 프런트가 `?game=` 을 넘긴다. 같은 자리에서 `member:` 값의 서술을 바로잡았다 — **PUBG 는 keyValue(플랫폼)가 아니라 `'EXIST'`** 다(배정 스크립트가 그렇게 쓴다) | A-15 를 원본에 올릴 때 "platform 이 읽는 때" 에 제안 중(팀원 카드)을 더하고, `member:` 의 값을 게임별로(LoL 포지션 · VALORANT 역할군 · PUBG `'EXIST'`) 적는다 |

---

## 코드 ↔ 계약 불일치 (2026-09-11 확인, 2026-09-17 · 2026-09-27 갱신)

> 2026-09-27 — "코드가 맞다"로 판정된 줄은 사본(`openapi.yaml` · `events.md`)에 반영하고 ~~취소선~~ 으로 표시했다(위 A-13 · A-14).
> **해소는 사본 기준이다 — 원본(queueMate `feature/frontend`)에는 아직 그대로 있다.** 어느 쪽이 맞는지 정하지 않은 줄(#4 · #4-1 · #5)과
> 계약이 맞는 줄(#7)은 남겨 두었다. #12(SQS `ProposalConfirmed`)는 2026-09-27 에 큐를 두지 않기로 해(docs/11 D-42, A-15) 해소로 표시했다.

계약과 구현이 어긋난 지점이다. **한쪽에 맞추기 전에 어느 쪽이 맞는지부터 판단해라.**
아래 "판정" 열이 그 판단이다.

| # | 지점 | 계약 | 구현 | 판정 |
|---|---|---|---|---|
| ~~1~~ | `VoicePreference` enum | `[REQUIRED, OPTIONAL, NO_VOICE]` | `[REQUIRED, NO_VOICE]` (`domain/condition/VoicePreference.java`) | **코드가 맞다.** `OPTIONAL` 제거는 의도된 결정이고 근거가 enum 주석에 있다. **해소됐다(사본) — 2026-09-27 에 사본의 enum 을 `[REQUIRED, NO_VOICE]` 로 고쳤다(A-13). 원본은 아직 세 값이다** |
| ~~2~~ | `CreateMatchRequest` 바디 | `userId` 없음 (JWT 로 식별) | **2026-09-27 에 맞췄다** — 바디의 `userId` 를 받지 않는다(보내도 무시 — `@JsonIgnore`). "나"는 쿠키 `qm_access` 의 `sub` 다. (그 전에는 `userId` **필수**였다 — JWT 도입 전 임시) | **해소됐다** (docs/11 D-24 · D-25, 위 A-12) |
| ~~3~~ | `DELETE /match-requests/{id}` | 쿼리 파라미터 없음 | **2026-09-27 에 맞췄다** — `?userId=` 가 없어졌다(그 전에는 **필수**였다). 수락 · 거절의 `?userId=` 도 같이 없어졌다(거절의 `?requestId=` 도 같은 날 늦게 없어졌다 — #6) | **해소됐다** |
| 4 | `MatchRequestView` **필드** | `{ id, status, queuedAt, proposalId }` **4개** | `MatchRequestResponse` **8개** — `{ status, requestId, queuedAt, partyId, target, memberCount, expiresAt, isAccepted }` (`dto/MatchRequestResponse.java`, **record** + `@JsonInclude(NON_NULL)`) | **정하지 않은 것이 남아 있다 — 이름과 타입.** 2026-09-27 에 **사본은 구현이 지금 돌려주는 8개 필드를 그대로 적었다**(A-13) — `target`/`memberCount`(대기 화면의 "3/5명"), `expiresAt`/`isAccepted`(제안 화면의 남은 시간과 내가 눌렀는지)는 클라이언트가 화면을 그리는 데 필요해 필드 수는 구현이 앞서 있는 것이 맞다. `@JsonInclude(NON_NULL)` 이라 그 갈래에서 뜻이 없는 칸은 **응답에 아예 나타나지 않는다**(예: `IDLE` 은 `{"status":"IDLE"}` 하나뿐이다) — 이것도 사본에 적었다. **아직 열린 것**: ① `requestId` 냐 `id` 냐 ② `partyId` 냐 `proposalId` 냐(확정되면 둘이 같은 값이다 — proposal = party) ③ `queuedAt`/`expiresAt` 이 `date-time` 문자열이냐 **epoch millis `Long`** 이냐(구현은 후자). **원본 계약과 같이 정한다. 그때까지 코드도 사본도 이름을 바꾸지 않는다** |
| 4-1 | `MatchRequestView.status` enum | `[QUEUED, PROPOSED, MATCHED, CANCELLED, EXPIRED]` | `domain/MatchRequestStatus.java` 에 **`IDLE` 이 추가**돼 6개다. 반대로 `CANCELLED`/`EXPIRED` 는 **enum 에만 있고 조회가 절대 돌려주지 않는다** | **구현이 맞다.** 취소·만료는 활성 요청 키를 지우므로 서버에 근거가 남지 않아 "원래 큐에 없었다"와 구분되지 않는다 — 둘 다 `IDLE` 로 나간다. 2026-09-27 에 **사본은 조회가 실제로 돌려주는 `[IDLE, QUEUED, PROPOSED, MATCHED]` 로 적고, `CANCELLED`/`EXPIRED` 는 enum 에 자리만 있다고 설명에 남겼다**(A-13). **아직 열린 것**: `CANCELLED`/`EXPIRED` 를 계약에 남길지 — #4 와 같이 원본과 정한다 |
| 5 | **상태 조회의 경로** | `GET /match-requests/{requestId}` — **경로 변수**로 찾는다 | **구현됐다. 그러나 경로가 다르다** — 경로 변수 없는 `GET /match-requests` 다. "나"는 쿠키 `qm_access` 의 `sub` 다 (`MatchingController#getMatchRequest` → `service/MatchQueryService`. 2026-09-17 ~ 09-27 사이에는 `?userId=` 였다) | **구현이 맞다고 보고 그렇게 뒀고, 2026-09-27 에 사본도 `GET /match-requests` 로 적었다(A-13).** 이유 둘: ① 활성 요청은 `qm:user:active-request:{userId}` 로 **사용자 단위** 저장이라(INV-1) `requestId` 는 그 HASH 안에 든 값이지 찾는 열쇠가 아니다. ② 이 조회가 가장 필요한 순간이 **페이지를 새로 열었을 때**인데 그때 클라이언트는 `requestId` 를 잃은 상태다 — 그 값을 요구하면 정작 필요할 때 못 쓰는 API 가 된다. 취소(`DELETE`)가 `requestId` 를 받는 것은 **쓰기**라서다(늦게 도착한 취소가 그 사이 새로 만든 요청을 지우면 안 된다). 조회에는 그 위험이 없다. 근거는 그 메서드 주석. **아직 열린 것**: `GET /match-requests` 로 둘지 **`GET /match-requests/me`** 로 옮길지 — 원본 계약과 같이 정한다. 그래서 취소선을 긋지 않았다 |
| ~~5-1~~ | `POST /match-requests` 의 **201 본문** | `MatchRequestView` | (옛 구현) JSON 이 아니라 문자열 `"CREATED"` 였다 | **계약이 맞았고 구현을 고쳤다.** 지금 `MatchingController#createMatchRequest` 는 `MatchRequestResponse.queued(requestId, queuedAt)` 를 돌려준다 — 접수 응답이 `requestId` 를 줘야 클라이언트가 취소(`DELETE /{requestId}`)를 부를 수 있다. **해소됐다** — 사본의 `201` 설명도 그렇게 적었다(A-13) |
| ~~6~~ | `POST /proposals/{id}/accept`·`decline` | 있다 (`200`) | **구현됨.** `controller/ProposalController.java` → `service/ProposalService.java` → `redis/proposal/accept-proposal.lua`·`decline-proposal.lua`. 응답 갈래는 `domain/ProposalResult.java` 한 enum 이다(`AcceptResult`/`DeclineResult` 는 없어졌다). `decline` 은 쿼리 파라미터를 받지 않는다(한때 받던 `?requestId=` 는 2026-09-27 에 없앴다 — 서버가 활성 요청 HASH 에서 읽는다) | **구현이 앞서 있다. 해소됐다(사본) — 2026-09-27 에 사본에 401/403/404/409/503 갈래를 적었다(A-14). 원본은 아직 없다** |
| ~~6-1~~ | 위 두 엔드포인트의 성공 응답 | `200` (본문 스키마 없음) | `204 No Content` | **구현 쪽이 낫다고 보고 그렇게 뒀다.** 본문 스키마가 계약에 없어 `200` 이 돌려줄 것이 없고, 취소(`DELETE`)와 모양이 맞는다. 근거는 `ProposalController` 의 `decline` 주석. **해소됐다(사본) — 2026-09-27 에 사본을 `204` 로 고치고 `ProposalView`·`ProposalMember` 스키마를 지웠다(A-14). 원본은 아직 `200`** |
| ~~6-2~~ | **수락 재전송**의 응답 | 계약에 없다 | **`204`**. 이미 확정된 제안에 수락이 또 오면 `accept-proposal.lua` 가 `ALREADY_RESPONDED` 를 돌려주고, 컨트롤러가 `ACCEPTED` / `CONFIRMED` 와 **같이 묶어 204** 로 내보낸다 (`ProposalController#accept`) | **구현이 맞다.** 같은 명령을 두 번 보내 결과가 같으면 성공이다 — 응답이 유실돼 자동 재시도한 클라이언트에게 오류를 보이지 않는다. 스크립트가 `CONFIRMED` 와 값을 가른 것은 확정 알림(`MATCH_CONFIRMED`)이 두 번 나가지 않게 하려는 것이지 실패라는 뜻이 아니다. **거절 쪽 `ALREADY_RESPONDED` 는 409 그대로다** — 그쪽은 재시도가 아니라 "수락해 놓고 거절을 눌렀다"는 진짜 충돌이다. **해소됐다(사본) — 2026-09-27 에 사본의 `accept` 설명에 "멱등이다 — 이미 확정된 제안에 재전송해도 204"를, `decline` 에 "멱등이 아니다 — 재시도는 404"를 적었다(A-14)** |
| 7 | `GET /games` | 있다 | **없다** | 계약이 맞다. gameconfig 는 `seed/gameconfig.redis` 로만 다뤄지고 조회 API 가 없다 |
| 8 | `ErrorResponse` 스키마 | **없다** (원본이 스스로 구멍이라고 지적) | 있다 (`common/error/ErrorResponse.java`: `{code, message, details}`) | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다 |
| ~~9~~ | `503 MATCHING_UNAVAILABLE` 응답 | 계약에 없다 | 있다 (`GlobalExceptionHandler#handleRedisFailure`, INV-10). 후보 풀 락 획득 실패(`redisLock/PoolLock.java`)도 같은 자리로 나간다 | 구현이 맞다. **해소됐다(사본) — 2026-09-27 에 사본의 모든 엔드포인트에 `503` 을 적고 `Retry-After: 5` 규칙은 `info.description` 에 한 번 적었다(A-13). 원본은 아직 없다** |
| 10 | `securitySchemes` (JWT bearer — docs/11 D-14 로 bearer 가 아니라 **cookie** 방식이 됐다) | **없다** | **2026-09-27 에 붙었다** — 쿠키 `qm_access` 의 RS256 JWT 를 검증한다(`common/security/`). 사본의 `openapi.yaml` 에 `cookieAuth` 를 먼저 적었다(위 A-12) | **구현과 사본이 앞서 있다.** 원본에 올려야 한다 |
| 11 | SSE `MATCH_*` 5종 | 있다 | **5종 모두 발행됨** — 앞의 3종에 더해 `MATCH_PROPOSAL_EXPIRED`(`service/ProposalExpiryService.java`) · `MATCH_CONFIRMED`(`service/ProposalService.java#accept()`)가 붙었다. 전부 `notification/PushPublisher.java` 가 `qm:pubsub:push:{userId}` 로 publish 한다 | **구현됨.** SSE 배달 자체는 `app:realtime` 몫이므로 이 저장소가 할 일은 publish 까지다. 상세는 `events.md` 의 "구현 상태" 표 |
| 11-1 | SSE payload 스키마 | **없다** (15종 전부 미정의 — `events.md` 의 "미해결 계약 구멍". 원본은 14종이고 `WEBRTC_SIGNAL` 이 더해졌다 — 위 A-2) | 구현이 먼저 정했다. `MATCH_QUEUE_UPDATED`·`MATCH_CANCELLED` = `{memberNumber}`, `MATCH_PROPOSAL_CREATED` = `{memberNumber, target, partyId}`, `MATCH_PROPOSAL_EXPIRED`·`MATCH_CONFIRMED` = `{partyId}` | **구현이 앞서 있다.** 계약으로 승격하려면 본 저장소에 contract 커밋이 필요하다. `PushPublisher` 의 `payload` 가 `Map` 인 것도 그 때문이다 |
| ~~12~~ | SQS `ProposalConfirmed` (`BlockChanged` 는 docs/11 D-12 로 폐기 — 위 A-5) | 있다 | **없다, 그리고 두지 않는다** (docs/11 D-42). 확정은 되고 Redis 쪽 뒷정리(`cleanup-confirmed.lua`)와 `MATCH_CONFIRMED` 알림까지 붙어 있다. 2026-09-27 에 Flyway + `matching_outbox` 를 넣었다가 같은 날 되돌렸다 | **해소됐다 — 큐 자체를 두지 않기로 했다(D-42, 위 A-15).** `app:platform` 이 확정된 파티 HASH `qm:party:{partyId}` 를 직접 읽어 파티를 만든다. 옛 서술의 "`status=PARTY` 를 푸는 주체 미정" 도 D-42 로 닫혔다 — 활성 요청은 `confirmed-retention-seconds`(60초) 뒤 만료되고, 그 뒤 "한 번에 하나만" 은 platform 의 입장 표시 키가 맡는다. 원본에는 아직 큐가 있다 — 지워야 한다 |
| ~~13~~ | `CreateMatchRequest` 의 `tier` | **없다** | `tier` (선택 필드, String). `tierRule` 이 `NONE` 이 아닌(= `EXIST` 인) 모드에서는 사실상 필수이고 빠지면 400 이다 (`validation/lol/LolConditionValidator.java`). **값은 단(division)까지 적는다** — `GOLD` 가 아니라 `GOLD_2` 다. 허용되는 이름의 원본은 자바 enum 이 아니라 Redis ZSET `qm:gameconfig:LOL:tier` 다 (32개) | **구현이 앞서 있다.** 조건 5번째가 아니라 derived/자격 조건이다 (docs/02 §6, CLAUDE.md §2). 계정 연동이 붙으면 요청 필드에서 사라진다(**→ A-18 · docs/11 D-48** — 바디에 남고 클라이언트가 그 모드의 사다리 티어를 싣는다). **해소됐다(사본) — 2026-09-27 에 사본의 `CreateMatchRequest` 에 선택 필드 `tier` 를 그 뜻 그대로 적었다(A-13). 원본은 아직 없다** |
| ~~14~~ | `KeyCondition.type` 의 PUBG 값 | `PLAY_STYLE` | `PLATFORM` (`domain/condition/KeyConditionType.java`), 값은 `STEAM` / `KAKAO` | **코드가 맞다.** 스팀·카카오는 서로 파티를 맺을 수 없어 플레이 스타일(취향) 대신 플랫폼(hard)을 핵심 조건으로 교체했다. 근거는 enum 클래스 주석. **해소됐다(사본) — 2026-09-27 에 사본의 enum 을 `[POSITION, ROLE, PLATFORM]` 으로 고쳤다(A-13). 원본은 아직 `PLAY_STYLE`** |

### 계약에는 없지만 구현이 실제로 내는 에러 코드

`GlobalExceptionHandler` · `MatchingController` · `ProposalController` 에서 추출했다.

| HTTP | code | 언제 |
|---|---|---|
| 400 | `INVALID_REQUEST` | `@Valid` 실패 (필수 필드 누락, enum 값 오류). `details` 에 필드별 메시지 |
| 400 | `BAD_REQUEST` | `IllegalArgumentException` — 예: 지원하는 규칙이 없는 게임 |
| 400 | `INVALID_MATCH_CONDITION` | 게임별 조건 검증 실패 (없는 modeKey, 모드에 안 맞는 포지션 값, `tierRule` 에 안 맞는 티어, `tierRule=EXIST` 인데 `tier-range` 표에 줄이 없거나 `SOLO_ONLY` 인 티어) |
| 409 | `ALREADY_QUEUED` | INV-1 — 이미 활성 요청이 있다 |
| 409 | `IN_ROOM` | 게시판 방에 들어가 있다 — `app:platform`(옛 `app:room` — docs/11 D-33)의 입장 표시 키(`qm:user:active-room:{userId}`)가 있다. 방에서 나와야 매칭을 시작할 수 있다 (docs/11 D-11 15번 · D-19, 위 A-10) |
| 404 | `MATCH_REQUEST_NOT_FOUND` | 취소할 활성 요청이 없다. (2026-09-28) `POST /match-requests/heartbeat` 도 활성 요청이 없으면 같은 코드다 — 새 코드를 만들지 않았다 (A-16) |
| 404 | `MATCH_REQUEST_MISMATCH` | 저장된 requestId 와 다르다 (늦게 도착한 취소) |
| 404 | `PROPOSAL_NOT_FOUND` | 진행 중인 제안이 없다 — 만료됐거나, 누가 거절해 깨졌거나, 같은 거절의 재시도다. 클라이언트가 할 일은 셋 다 같다(대기 화면 복귀) (`ProposalController`) |
| 409 | `PROPOSAL_CONFLICT` | 지금 상태와 맞지 않는다 — 이미 확정된 제안을 거절했다(INV-5) / 수락해 놓고 거절을 눌렀다 / (수락 경로) 다른 참가자가 거절했다 (`ProposalController`) |
| 403 | `NOT_PROPOSAL_MEMBER` | 남의 제안에 응답했다. 404 가 아닌 것은 제안이 존재한다는 사실 자체는 숨길 값이 없어서다 (`ProposalController`) |
| 503 | `MATCHING_UNAVAILABLE` | Redis 장애 또는 후보 풀 락 획득 실패. `Retry-After: 5` 헤더 동반 (INV-10 fail-closed) |
| 401 | `UNAUTHENTICATED` | (2026-09-27) 쿠키 `qm_access` 가 없거나, 만료 · 서명 · `iss` · `token_use` · `sub` 가 어긋난다. 이유를 가르지 않는다 (`common/security/ApiAuthenticationEntryPoint`) |
| 403 | `ORIGIN_NOT_ALLOWED` | (2026-09-27) POST/PUT/PATCH/DELETE 의 `Origin` 이 허용 목록(`ALLOWED_ORIGINS`)에 없다. 인증보다 먼저 본다 (`common/web/OriginCheckFilter`) |

`501 NOT_IMPLEMENTED` 는 **없어졌다** — 상태 조회가 구현되면서 그 스텁이 사라졌다(위 #5).
**상태 조회는 에러를 내지 않는다.** 활성 요청이 없어도 404 가 아니라 `200 {"status":"IDLE"}` 이다 —
"큐에 없음"은 오류가 아니라 답의 한 갈래이고, 클라이언트는 어느 경우든 `status` 하나로 화면을
고르면 된다. 나갈 수 있는 실패는 Redis 장애의 503 뿐이다.

`ProposalController` 가 실제로 내는 것은 위 표의 셋 — `PROPOSAL_CONFLICT` · `PROPOSAL_NOT_FOUND` ·
`NOT_PROPOSAL_MEMBER` — 뿐이다(2026-09-27 에 사본의 `ErrorResponse.code` 에도 넣었다 — A-13). 예전에 여기 적혀 있던
`PROPOSAL_EXPIRED`(410) · `PROPOSAL_DECLINED` · `PROPOSAL_ALREADY_RESPONDED` 는
`ProposalService` 가 껍데기이던 시절의 목록이고, 지금 코드에는 없다.

---

## 메워진 구멍 — "지금 상태"를 물어볼 곳이 생겼다 (2026-09-17)

계약은 매칭 결과를 알리는 경로를 **두 가지**로 정의한다.

1. SSE `MATCH_PROPOSAL_CREATED` / `MATCH_CONFIRMED` (`events.md`)
2. 폴링 `GET /api/v1/match-requests/{id}` (원본 `openapi.yaml`. 이 사본은 2026-09-27 부터 `GET /api/v1/match-requests` 로 적는다 — A-13)

**둘 다 메워졌다.** 다만 2번은 **원본 계약과 다른 경로**로 메워졌다 — 위 표 #5 참고.
아래 본문은 그 구멍이 왜 있었는지의 기록이라 남겨 둔다.

- SSE — 이 앱의 몫인 Redis Pub/Sub publish 는 **된다**
  (`notification/PushPublisher.java`, 채널 `qm:pubsub:push:{userId}`). 정원이 차면
  `MATCH_PROPOSAL_CREATED` 가, 전원 수락으로 확정되면 `MATCH_CONFIRMED` 가, 시한이 지나면
  `MATCH_PROPOSAL_EXPIRED` 가 파티 전원에게 나간다. SSE 배달은 `app:realtime` 몫이고
  이 저장소에 `SseEmitter` 를 넣지 않는 것이 맞다 (CLAUDE.md §3).
- 폴링 — **구현됐다.** `GET /match-requests`(2026-09-27 까지는 `?userId=` — 지금은 쿠키의 `sub`) → `service/MatchQueryService` 가
  활성 요청 HASH 와 파티 HASH, 수락자 SET 을 읽어 `IDLE` / `QUEUED` / `PROPOSED` / `MATCHED`
  네 갈래로 답한다. 경로가 계약과 다른 이유는 위 표 #5 에 있다.

**왜 필요했나 (기록).**

1. **알림은 사건만 전한다.** "방금 이렇게 됐다"는 말하지만 "지금 이렇다"는 못 한다.
   앱을 껐다 켜거나 새로고침하거나 다른 기기로 접속한 클라이언트는 아무것도 모른 채
   "매칭 시작" 버튼을 그리고, 누르면 409 `ALREADY_QUEUED` 를 받는다. 확정된 사용자는
   활성 요청에 `status=PARTY` 가 찍힌 채 남아 있어 더욱 그렇다. 게다가 Pub/Sub 은
   at-most-once 라 놓친 알림의 복구도 이 REST 몫이다 — 계약이 "놓친 상태는 REST 로
   복구한다"고 정한 그 REST 가 이것이다.

(옛 2번 "수락 집계·확정이 없다"는 해소됐다 — `POST /proposals/{id}/accept|decline` 이
동작하고 만료도 스위퍼가 처리한다.)

**이 조회가 답하지 못하는 것 두 가지.**

- **취소·만료의 구분.** 둘 다 활성 요청 키를 지우므로 `IDLE` 과 같아진다(위 #4-1).
  "왜 큐에서 빠졌는지"를 클라이언트가 알려면 알림(`MATCH_CANCELLED` /
  `MATCH_PROPOSAL_EXPIRED`)을 받았어야 하는데, 그 알림은 at-most-once 라 놓칠 수 있다.
  그때 사용자는 이유를 모른 채 대기 화면에서 시작 화면으로 돌아간다.
- **파티 상세(누가 같이 있는지).** 여기서 답하지 않는다. 진행 중인 매칭 상태만 이 앱의
  소유이고 확정된 파티는 `app:platform` 의 것이다 (CLAUDE.md §9). 그래서 `MATCHED` 응답은
  `partyId` 까지만 준다.

남은 작업과 우선순위는 `HANDOFF.md` 의 2026-09-17 블록에 있다.

`load-test/match_latency.py` 가 성사를 감지하려고 HTTP 가 아니라 **Redis 를 직접 폴링**하는
것(`HGET qm:user:active-request:{uid} partyId` → `HGET qm:party:{pid} size`)은 그 스크립트가
알림 도입 전에 쓰였기 때문이다. 알림 경로가 생겼으니 `PSUBSCRIBE qm:pubsub:push:*` 로
바꿀 수 있다.
