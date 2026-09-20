# CLAUDE.md — platform 서비스 규칙 (Non-Negotiable)

작업 전에 이 파일과 `README.md`를 읽어라.

이 폴더는 QueueMate의 **API 서버 배포 단위 하나**다. 문서에서 **`app:platform`**이라고 부르는 것이 이것이다
(docs/11 #15). 매칭 엔진 규칙은 옆 폴더 `matching`의 `CLAUDE.md`에, 알림 배달 규칙은 `notification`의, **방 안의 일**(입장·강퇴·시그널)의
규칙은 `room`의 `CLAUDE.md`에 있고(docs/11 D-16), 이 파일은 **계정·파티·소셜 REST에 걸리는 부분만** 담는다(예약은 `app:reservation`으로 빠졌다 — docs/11 D-15).

> **출처 표기.** `docs/…` · `contracts/…` · `HANDOFF.md`처럼 폴더 이름 없이 적은 것은 전부 옆 폴더
> `matching` 기준이다(절대 경로는 §10). **출처가 안 붙은 사실은 정해지지 않은 것이다** — §7로 보낸다.

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다 — "조건은 사용자가 정하고, 사람 선택은
시스템이 한다"(docs/00 §1). 이 서비스는 매칭의 **앞(계정)과 뒤(모집 글·확정된 파티·친구·차단·신고)**를 맡는다.
자동 매칭이 **기본 경로**이고 **파티 모집 게시판이 두 번째 경로**다 (docs/11 D-11). **이 서비스는 오래 남는 것(PostgreSQL)을
다루고, 지금 방에 누가 있는지처럼 금방 사라지는 것은 `room`(Redis)이 다룬다** (docs/11 D-16).

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (account · party · social)
                                 │  ▲
                                 │  └── SQS ProposalConfirmed.fifo ◀── matching   (파티를 만들어라)
                                 │      (차단은 SQS 로 알리지 않는다 — matching 이 social.blocks 를 직접 읽는다)
                                 ├──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
                                 └──── 입장권(서명) ──▶ 브라우저 ──▶ room      (room 은 서명만 검증한다. 서로 호출하지 않는다)
```

- 지원 게임은 **LoL, VALORANT, PUBG 셋뿐**이다 (docs/11 #8). **상대팀/VS/대전 상대를 만들거나 보여주지 않는다** (#9).
- 공개 사용자 탐색, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다
  (`matching/CLAUDE.md` §1 · docs/11 #14 · docs/00 §6).
  - **예외 하나 — 파티 모집 게시판은 허용된다** (docs/11 D-11이 #14와 docs/00 §6의 "게시판/LFG 글 작성"을 개정했다).
    **글·목록·방장 확정은 이 서비스, 방 안의 일은 `room`의 일이다** (D-16). 정해진 것과 정할 것은 §7.1.
  - 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이다. 사람을 검색하고 둘러보는
    공개 사용자 탐색은 여전히 금지다.
  - "공개 채팅방"은 **파티 모집과 무관한 잡담용 공개방**을 뜻한다. 모집 글에 딸린 방은 그 예외다.
- 프리미엄/과금 기능을 구현하지 않는다. 친구 / 차단 / 최근 함께한 사람 / 신고는 필수다 (docs/11 #13·#14).
- **매칭 로직이 하나도 없다.** 매칭·제안·수락·확정은 전부 `matching`의 일이다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 출처 |
|---|---|
| **계정** — 회원가입/로그인/로그아웃, 기본 프로필, 게임 계정 연결/해제. **인증 토큰 발급** — access는 **쿠키로 주고받는 JWT**, refresh는 **Redis에 저장하는 불투명 UUID**(재발급·폐기도 이 앱) | docs/00 §5 · docs/11 #16 · D-14 · docs/AWS_ARCHITECTURE §3 |
| **파티** — `ProposalConfirmed.fifo`를 소비해 **DB에 파티를 만든다.** 확정된 파티와 파티원의 기록. 파티가 닫히면 `PartyClosed.fifo` 발행(소비도 이 앱). 파티룸의 **나가기·지금 누가 있나**는 `room`의 일이다(D-16) | docs/11 #21 · D-13 · D-16 · docs/00 §5 · `matching/CLAUDE.md` §9 |
| **파티 모집 게시판(오래 남는 쪽)** — 모집 글 쓰기·수정, 게시판 목록, **차단 관계 거르기**(`social.blocks`가 같은 앱에 있다. **방 안의 누구와든** 본다 — D-20), 글의 상태(모집 중/확정/만료), **방장 확정의 기록 — 글의 상태를 "확정"으로 바꾸고 파티원을 기록한다**(확정 요청 자체는 `room`이 받아 확정 표시 키를 쓴다. 이 앱은 그 키와 멤버 SET을 **읽는다** — D-21 · §3.3), **입장권 발급**(글이 모집 중이고 **방 안의 누구와도** 차단 관계가 아닐 때만 서명해 준다). **목록의 한 줄은 이 앱이 전부 조립한다**(D-20) — 방 안에 몇 명인가, **방 안 사람들의 카드**(닉네임·티어·포지션 등 — 프로필은 이 앱의 DB에 있다), **글의 "찾는 포지션" 가운데 이미 방 안에 있는 포지션의 강조**(포지션의 출처는 **프로필의 주 포지션**이다. 입장할 때 고르지 않는다). 목록·입장권·확정 때 `room`의 방 키를 **읽는다**(§3.3) | docs/11 D-11 · D-16 · **D-20** · **D-21** |
| **소셜** — 친구 요청/수락/거절/삭제, 차단/해제, 신고, 최근 함께한 사람. **`social.blocks`의 소유자**다. 차단은 **DB에 저장하는 것으로 끝낸다** — `BlockChanged.fifo`는 만들지 않는다(§3.4) | docs/00 §5 · D-1 · D-2 · D-12 |
| **알림 발행** — `PARTY_*`·`FRIEND_*` 7종(이름과 `payload`는 미정 — §7). **방 입장·퇴장·방 닫힘·강퇴·방장 확정 알림(`ROOM_CONFIRMED`)과 `WEBRTC_SIGNAL`은 `room`이 발행한다** — `PARTY_*`를 다시 쓰지 않고 새 이름(`ROOM_MEMBER_ENTERED` 등)을 지었다(§3.3). **게시판 채널에 "바뀌었다" 신호 발행** — 글이 생기거나 사라지거나 상태가 바뀔 때(§3.2 "게시판 채널" — 채널 `qm:pubsub:board:{game}`, `type`은 `BOARD_CHANGED`, `payload`는 `{}`. **정해졌고 구현 전**, D-20) | contracts/events.md · docs/11 D-16 · D-20 · D-21 · `../room/contracts/room-api.md` "알림" |

platform 소관 자원(contracts/openapi.yaml 머리말): `auth` `users` `parties` `friends` `blocks` `recent-players` `reports`. **경로와 스키마는 거기 없다**(§3.1). 머리말은 `reservations`도 platform 소관으로 적지만 D-15로 `app:reservation`의 것이 됐다.

| 안 한다 | 왜 / 누가 |
|---|---|
| 매칭·제안·수락·확정, 매칭 Redis 키(`qm:party:*` `qm:user:*` `qm:proposal:*` `qm:gameconfig:*` `qm:lock:*`) 접근. **예외가 없다** — "한 번에 하나만"은 키 둘로 지키고 **이 앱은 둘 다 만지지 않는다**: 활성 요청 키(`qm:user:active-request:{userId}`)는 `matching`만, 입장 표시 키(`qm:user:active-room:{userId}`)는 `room`만 쓰고 지우며 서로 상대 키를 `EXISTS`로만 본다(D-19가 D-11 16번과 D-16의 해당 대목을 개정) | `matching`의 일이다. 진행 중 매칭 상태의 원본은 Redis이고 그 주인은 `matching`이다 (docs/11 #27) |
| **방 안의 일** — 방 만들기·입장·나가기·강퇴, 정원 5명 검사, 접속 확인과 방장 이탈 감지, 입장 표시 키 쓰고 지우기(활성 요청 키는 `EXISTS`로 보기만 한다 — D-19), 방 알림(`ROOM_*`) 발행, 시그널 `POST` 받기와 `WEBRTC_SIGNAL` 발행 | **`room`(`app:room`)** 의 일이다 (docs/11 D-16 · D-19). 금방 사라지는 상태라 Redis에만 둔다. 이 앱과 `room`은 **서로 호출하지 않는다** — 입장권과 방 키로 잇는다(§3.3) |
| 브라우저 연결 보유 — `SseEmitter` / WebSocket | `notification`의 일이다. 이 앱은 **stateless REST**로 남아야 무중단 교체가 자유롭다 (docs/11 #15 · docs/14 §6). WebSocket은 어느 앱에도 없다 (D-9) |
| **예약 전부** — 예약 REST(`/api/v1/reservations` 등록·조회·수정·취소, INV-9 검증), 짝 찾기 배치, `RESERVATION_*` 발행 | **`app:reservation`(AWS Lambda)** 의 일이다 (docs/11 D-15 — #24의 "예약 REST는 `app:platform`"과 `app:reservation-batch`를 대체한다). 예약은 이 앱의 다른 모듈과 같은 트랜잭션으로 묶일 일이 없다 |
| TURN 단기 credential 발급, 음성·텍스트 채팅 중계/저장 | TURN은 Cloudflare 관리형이고 발급 주체는 `app:realtime`이다 (docs/11 #25). 음성·텍스트는 브라우저 직결(WebRTC audio + DataChannel)이라 서버를 거치지 않는다 (#6). (참고: 지금은 공개 STUN만으로 개발을 시작한다) |
| gameconfig(게임 모드 설정) | `app:matching`의 모듈이다 (docs/11 #15) |

## 3. 계약

### 3.1 Contract first — 그런데 platform 계약은 이 컴퓨터에 없다

- 계약 원본은 **queueMate 본 저장소(`feature/frontend` 브랜치)의 `contracts/`**이고 **이 컴퓨터에 없다.** `matching/contracts/`는
  `matching`이 노출하는 부분만의 발췌 사본이라 **platform 엔드포인트가 통째로 빠져 있다** (contracts/openapi.yaml 머리말).
- 그러므로 **경로·요청/응답 스키마·에러 코드·payload 필드를 지어내지 마라.** 사용자에게 묻는다.
  원본을 받아 올 수 있으면 그것이 먼저다.
- 여기서 정한 것은 **이 폴더의 `contracts/`**(없으면 그때 만든다)에 적고 "원본에 올려야 할 것" 표를 같이
  남긴다. 본보기는 contracts/README.md의 "이 사본이 원본보다 앞서간 변경"(A-1~A-4) 표다.

### 3.2 알림 발행 — `matching`의 `PushPublisher`와 같은 방식

Redis `PUBLISH qm:pubsub:push:{userId}`에 **JSON 문자열 하나**를 보낸다. `notification`은 열어 보지
않고 SSE `data:`에 그대로 싣는다 — **새 종류를 추가해도 `notification`은 재배포하지 않는다.**

| 항목 | 값 | 원본 (`matching` 기준) |
|---|---|---|
| 채널 | `qm:pubsub:push:{userId}` | `redisKeys/SharedKeys.java`의 `PUSH_CHANNEL_PREFIX` + `pushChannel(userId)` |
| 봉투 | `{type, eventId, occurredAt, payload}` 네 칸 고정 | `notification/PushPublisher.java`의 `record Envelope` · contracts/events.md "Envelope" |
| `eventId` | 매번 새 UUID 문자열 (SSE `id:`가 된다. 클라이언트 중복 제거용) | `PushPublisher#publish()` |
| `occurredAt` | ISO-8601 UTC, 밀리초 (`Instant.truncatedTo(MILLIS)`) | 같음 |
| `payload` | 객체. 담을 것이 없어도 `null`이 아니라 `{}` | 같음 |

> **채널 접두사는 이 서비스가 정하지 않는다.** 원본은 `matching`의 `SharedKeys.PUSH_CHANNEL_PREFIX`다.
> 따로 바꾸거나 오타를 내면 **컴파일도 테스트도 통과한 채로 알림이 전부 끊긴다** — 발행 쪽은 구독자
> 0명을 실패로 보지 않기 때문이다. 이 서비스에서도 접두사는 **상수 한 곳에만** 둔다.

- **발행 실패가 본 작업을 뒤집으면 안 된다.** 발행은 어떤 예외도 밖으로 내보내지 않는다 — 알림은 휘발성이고
  놓치면 클라이언트가 REST 재조회로 복구한다(`PushPublisher#publish()` 주석). 대가로 발행이 틀려도 조용하니
  **구독해서 확인하는 테스트**를 둔다. `type`은 오타를 컴파일에서 막도록 enum으로 다룬다(`PushEventType.java` 주석).
- **7종의 이름과 15종 전부의 `payload` 스키마는 이 컴퓨터의 문서에 없다** (contracts/events.md "미해결 계약 구멍") → §7.
- 클라이언트는 알림을 "다시 조회하라"는 신호로 다룬다(contracts/events.md "순서 보장 범위"). 그러므로
  알림이 가리키는 상태는 **REST로 조회할 수 있어야 한다.**

**게시판 채널 — 목록을 F5 없이 갱신하는 신호 (docs/11 D-20. 정해졌고 구현 전이다).**

- 위 알림은 사용자 한 명의 채널로 간다. 게시판 목록을 보는 사람은 **"누구인지 모르는 다수"**라서 사용자 채널로는 보낼 수 없다. 그래서 **주제 채널**을 새로 둔다.
  **채널은 `qm:pubsub:board:{game}`**(`{game}`은 `LOL`·`VALORANT`·`PUBG`), **`type`은 `BOARD_CHANGED`**, 봉투 네 칸은 위와 같고 **`payload`는 빈 객체 `{}`**다.
  이 앱은 **글이 생기거나 사라지거나 상태가 바뀔 때**, `room`은 **방의 인원이 바뀔 때** 그 채널에 발행한다. `notification`이 그 채널을 구독해
  "게시판을 보고 있는 연결"에 SSE로 흘려보낸다(주제 구독도 구현 전이다. 클라이언트는 SSE를 열 때 `topics=board:LOL`로 구독을 알린다 — `GET /api/v1/events?topics=board:LOL`).
  **이 신호는 "다시 받아라"일 뿐이다** — 받은 프런트가 **이 앱의 목록을 `GET`으로 다시 요청**하고(몇 초에 한 번으로 묶어서), 데이터와 차단 거르기는 그 응답에서 온다.
- **신호에는 데이터를 싣지 않는다.** 방송에 데이터를 실으면 사람별로 거를 수 없다 — 차단(§7.1)이 성립하지 않는다. 데이터는 거르는 곳인 **이 앱의 목록 조회**에서만 나간다.
  **`roomId`도 싣지 않는다** — "뭔가 바뀌었다"만 보낸다. 나에게 숨겨진 방이 바뀌었다는 사실이 새어 나가지 않게 하려는 것이다.
- **채널 이름은 위 채널 접두사와 같은 위험이다** — 발행하는 앱(이 앱·`room`)과 구독하는 앱(`notification`)이 어긋나도 컴파일·테스트가 통과한 채로 목록이 조용히 갱신되지 않는다.
  상수 한 곳에만 두고 원본이 어디인지 주석에 적는다(**원본 상수를 어느 서비스에 둘지는 미정**이다). **발행 실패가 본 작업(글 쓰기 등)을 뒤집으면 안 된다**는 규칙도 위와 같다.
- **미정** — 채널 접두사의 **원본 상수를 어느 서비스에 둘지**(알림 채널 접두사는 `matching`의 `SharedKeys`가 원본이다 — 같은 방식으로 갈지 정해지지 않았다), 프런트가 재요청을
  묶는 간격, 한 연결이 여러 주제를 구독할 때의 `topics` 표기, `room`이 어느 게임의 채널에 발행할지를 어떻게 아는가 → §7.1. **지어내지 마라.**
- 급하면 **프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다** — 나중에 신호를 얹어도 프런트 코드는 거의 그대로다. 그동안 이 앱에는 발행할 것이 없다.

### 3.3 `room`과 잇는 법 — 입장권과 방 키 (docs/11 D-16 · D-19 · D-20 · D-21). 시그널링은 이 앱의 일이 아니다

- **WebRTC 시그널 `POST`와 `WEBRTC_SIGNAL` 발행은 `room`의 일이다** (D-16이 D-9의 `app:platform`을 개정). 규칙은 `../room/CLAUDE.md` §3.3.
  방 알림(`ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` — 전부 구현돼 있다, D-21)도 `room`이 발행한다 — `payload`는 `docs/ROOM_CONTRACT.md` "알림".
- **입장권.** 글을 누르면 브라우저가 먼저 이 앱에 입장권을 요청한다. 글이 **모집 중인지·차단 관계가 아닌지** 확인하고 **서명된 입장권**(방
  식별자, 방장이 누구인지, 만료 시각 등)을 준다. **차단은 방장만이 아니라 그 순간 방 안에 있는 전원과 대조한다**(D-20 — 멤버 SET을 `SMEMBERS`로 읽는다.
  목록에서만 숨기고 입장은 되면 의미가 없다. 내가 들어간 **뒤에** 나와 차단 관계인 사람이 따라 들어오는 것은 **그 사람의 입장권 발급 때** 방 안의 나와 대조해야 막힌다). `room`은 서명만 검증하고 이 앱에 묻지 않는다. **확정되거나 만료된 글에는 입장권을 내주지
  않는다** — 그래서 "확정 뒤에는 새 사람이 못 들어온다"와 "만료된 글은 못 들어간다"가 `room`이 글의 상태를 몰라도 지켜진다.
  확정 쪽은 `room`도 직접 지킨다 — 확정된 방에는 입장이 409 `ROOM_CONFIRMED`로 거절된다(D-21. 아래 "방장 확정").
- **방 키를 읽는다 — 이 앱이 남의 Redis 키를 만지는 유일한 예외다.** 목록을 만들 때 글마다 "방이 살아 있는가·몇 명인가·**누가 있는가**"를 조회하고(가득 찬 방
  표시, **방 안 사람들의 카드, 차단 대조** — 읽는 범위가 D-20으로 `SCARD`에서 **`SMEMBERS`까지** 늘었다), 방이 사라져 있으면 그 자리에서 글을 만료로 바꾼다.
  입장권을 내줄 때도 멤버 SET을 읽는다(위). 방장 확정 때도 같은 키에서 현재 인원을 읽어 파티원으로 기록한다. **쓰지는 않는다 — 예외가 없다.**
- **방장 확정 — 확정 표시는 `room`이 쓰고 이 앱은 읽는다**(2026-09-20, D-21). 확정 요청(`POST /api/v1/rooms/{roomId}/confirm` — 방장만 · 2명 이상 · **되돌릴 수 없다**)은 **`room`이 받는다. 이 앱은 그 요청을
  받지 않는다.** `room`이 확정 표시 키 `qm:room:{roomId}:confirmed`를 쓰고 그 뒤로 새 사람의 입장을 409 `ROOM_CONFIRMED`로 거절한다. **이 앱은 확정 표시 키와 멤버 SET을 읽어 글의 상태를 "확정"으로 바꾸고
  파티원을 기록한다.** 확정한 그 순간 방에 있던 **전원**(방장 포함)이 파티원이다. 확정 순간의 경쟁(미리 받은 입장권으로 누가 들어오는 것)은 확정과 입장이 둘 다 `room`의 스크립트라서 거기서 막힌다 —
  D-16이 가능성으로 적었던 **"이 앱이 확정 직전에 '닫힘' 표시를 쓴다"는 받지 않았다**(`room`의 키는 `room`만 쓴다 — D-19와 같은 이유). **브라우저가 `room`과 이 앱을 어떤 순서로 부르는지는 미정**이다(§7.1).
- **방 키는 정해졌다.** 원본 상수는 `room`의 `redisKeys/RoomKeys.java`, 계약은 `../room/contracts/room-api.md` "Redis 키" 절이다(이 폴더의 사본은 `docs/ROOM_CONTRACT.md`).

  | 키 | 자료형 | 값 | 뜻 |
  |---|---|---|---|
  | `qm:room:{roomId}:host` | STRING | 방장의 `userId` | **이 키가 있다 = 방이 있다.** "방이 살아 있는가"는 이 키 하나를 `EXISTS` 하면 된다 |
  | `qm:room:{roomId}:members` | SET | 방에 있는 사람의 `userId`(방장 포함) | `SCARD`가 현재 인원이다. 정원은 5. **`SMEMBERS`로 `userId` 목록을 받아 이 앱의 DB에서 프로필을 붙이고 차단을 대조한다**(D-20). `room`은 `userId`만 안다 — 닉네임·티어·포지션은 이 SET에 없다 |
  | `qm:room:{roomId}:confirmed` | STRING | 그 방의 `roomId` | **확정 표시 키. 이 키가 있다 = 방장이 확정한 방이다**(D-21). 방장 확정을 기록할 때 이 키와 멤버 SET을 읽는다. **이 앱은 쓰지 않는다** |
  | `qm:user:active-room:{userId}` | STRING | 들어가 있는 방의 `roomId` | 입장 표시 키(D-19). `matching`도 `EXISTS`로 본다. **이 앱은 이 키를 만지지 않는다**(§2) |

  - 모든 키가 수명 600초(`room`의 `ROOM_TTL_SECONDS`)이고 브라우저가 1분마다 `room`에 보내는 접속 확인(`POST …/heartbeat`)이 늘린다. **방의 수명(방장 키 · 멤버 SET · 확정 표시 키)은 방장의
    신호만 늘린다** — 방장이 명시적으로 나가든 말없이 사라지든(연결 끊김) 방장 키가 없어진다. 방장이 나가면 방에 다른 사람이 있어도, **확정 뒤에도** 방을 통째로 없앤다(확정 표시 키도 같이 없어진다 — D-21).
    그래서 **방장 키가 없으면 방장이 나갔거나 사라진 것**이다. 대가 — 방장이 말없이 사라진 방은 최대 10분 살아 있는 것처럼 보인다.
  - 멤버 SET에는 말없이 사라진 사람의 이름이 잠깐 남을 수 있다(방장의 접속 확인이 뺀다) — **이 앱이 읽는 인원수는 길어야 수명만큼 부풀 수 있다.**
    카드에도 그 사람이 남고, **그 사람과 차단 관계인 사용자에게는 이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(D-20 "감수하는 것").
  - **`room`은 방이 없어져도 이 앱에 알리지 않는다**(서비스 간 호출도 큐도 없다 — D-16 그대로). 이 앱이 목록을 그릴 때와 입장권을 내줄 때 방장 키를 보고 스스로
    글을 만료시킨다. `room` 쪽에도 안전망이 있다 — 없는 방에는 입장이 404 `ROOM_NOT_FOUND`로 거절된다.
  - **방은 `room`의 "방 만들기" 요청(`POST /api/v1/rooms/{roomId}`)이 만든다.** 부른 사람이 방장이고 곧바로 들어와 있다. **입장은 방을 만들지 않는다.** 그래서
    **글을 쓴 직후, 방 만들기를 부르기 전에는 방장 키가 없다** — "방장 키가 없다 → 글을 만료로"를 그대로 적용하면 방금 쓴 글을 만료시킨다. 가리는 법은 **미정**이다 → §7.1.
- **이 읽기는 교과서대로면 "DB 공유로 통합하기"다 — 알고 감수한다**(D-20). **읽기 전용·키 넷(D-20 때는 셋이었고 확정 표시 키가 더해졌다 — D-21)·쓰는 주인 하나(`room`)**로 좁혀 두었다. 검토하고 버린 대안 —
  ① 이 앱이 `room`의 API를 호출한다(동기 호출이 생겨 한쪽이 죽으면 목록도 죽고 docs/11 #15가 깨진다. 글 N개에 호출 N번) ② `room`이 이벤트를 보내고 이 앱이 사본을
  유지한다(가장 정석이지만 놓치면 사본이 영원히 틀려 내구성 있는 큐(SQS)와 재처리·주기적 맞추기가 필요하다 — 금방 사라지는 정보에는 과하다) ③ 프런트가 이 앱과 `room`을
  따로 불러 조합한다(차단 관계를 서버에서 거를 수 없다). **나중에 ②로 옮길 길은 열려 있다** — `room`이 이미 인원이 바뀔 때마다 알림을 발행한다.
  **`room`의 방 안 사람 목록 API(`GET …/members`)는 방 안의 사람만 볼 수 있다**(403 `NOT_IN_ROOM`) — 방 밖에서 방 안을 보는 공개 창구는 **이 앱의 목록**이다. 차단을 거르는 곳이 여기라서다.
  **그 API를 이 앱이 부르지 마라.**
- **방 키의 이름·구조는 두 앱의 약속이다.** 알림 채널 접두사(§3.2)와 같은 위험이다 — 어긋나면 테스트가 통과한 채로 목록이 조용히 틀린다.
  이 앱을 구현할 때 접두사는 **상수 한 곳에만** 두고 **원본이 `room`의 `RoomKeys`임을 주석에 적는다.** 바꿀 때는 `room`과 같이 바꾼다.
  **입장권**의 형식·서명 방식은 여전히 **미정**이다 → §7.1.
- **`room`은 지금 입장권 없이 돈다.** 이 앱이 없어 인증·입장권·방장 확인을 임시 처리(`TEMP-NO-PLATFORM`)로 비워 뒀다 — 누구나 아무 `roomId`로 방을 만들고 방장이 된다.
  **이 앱이 생기면 채워 줘야 하는 자리의 목록**은 `../room/START_HERE.md` §2다(access 토큰의 사용자, 입장권 서명 검증, 글의 상태·차단 확인).

### 3.4 서버 간 이벤트 — transactional outbox + SQS FIFO (docs/11 #21)

| 큐 | 이 앱의 역할 | MessageGroupId |
|---|---|---|
| `ProposalConfirmed.fifo` | **소비** — 확정된 제안으로 파티를 DB에 만든다 | `proposalId` |
| `PartyClosed.fifo` | **발행**(`party.outbox`) **+ 소비** — 닫힌 파티의 멤버로 `social.recent_players`를 만든다. **소비자는 이 앱 하나다**(docs/11 D-13). `matching`은 이 큐를 읽지 않는다 — FIFO 큐 하나를 두 앱이 읽으면 메시지를 나눠 갖게 된다 | `partyId` |
| ~~`BlockChanged.fifo`~~ | **만들지 않는다**(docs/11 D-12). 원안(docs/11 #21)은 이 앱이 발행하고 `matching`이 받아 Redis 선필터를 만드는 것이었으나, `matching`은 확정 직전에 `social.blocks`를 직접 조회하는 한 겹으로 INV-6을 지킨다(D-1·D-2). 차단/해제는 DB 트랜잭션으로 끝난다 | — |

- 상태 변경과 outbox 행을 **같은 트랜잭션**에 쓴다. relay가 SQS로 민다. `MessageDeduplicationId`는 outbox id다.
- 전달은 at-least-once다. **소비자는 반드시 멱등**해야 한다 — 같은 `ProposalConfirmed`가 두 번 와도 파티는 하나다.
  각 큐에 DLQ + `maxReceiveCount`. Kafka/RabbitMQ/Redis Streams로 바꾸지 마라 (docs/11 #21·#26).
- **메시지 본문 스키마는 문서에 없다.** `matching` 쪽 발행도 아직 미구현이다(HANDOFF.md ③) → §7.

### 3.5 DB — 스키마 소유와 `social.blocks`

- PostgreSQL 인스턴스 1개에 **schema-per-service**다. **크로스 스키마 FK·JOIN 금지.** Flyway 마이그레이션은
  `db/migration/<schema>/`로 나눈다 (docs/11 #17). 이 앱이 소유하는 스키마(docs/WHY_POSTGRESQL §3이 인용한 원본 docs/06 배치):
  `account`(users, credentials/refresh_tokens, game_accounts) · `party`(parties, party_members, outbox) ·
  `social`(friend_requests, friendships, blocks, reports, recent_players, outbox). **컬럼은 이 컴퓨터의 문서에 없다** → §7.
  `reservation` 스키마는 **이 앱이 소유하지 않는다**(D-15 — `app:reservation`의 것). 그 마이그레이션을 누가 실행하는지는 미정이다(§7).
- **유일한 예외를 이 앱이 열어 준다** — `matching` 롤에 `social.blocks`의 SELECT (docs/11 D-1):
  `GRANT USAGE ON SCHEMA social TO qm_matching; GRANT SELECT ON social.blocks TO qm_matching;`
  뷰(`shared_read.blocked_pairs`)는 만들지 않는다. 예외를 늘리지 않는다.
- **`matching`이 이미 이 모양으로 읽고 있다** (`block/Block.java` · docs/11 D-4) — 바꾸면 `matching`이 런타임에 깨진다:
  `social.blocks(id 일련번호 PK — 채번은 이 앱, blocker_id 문자열, blocked_id 문자열)`. 방향이 있는 한 줄이고
  `matching`은 양방향으로 조회한다. **`(blocker_id, blocked_id)` UNIQUE는 이 앱이 건다.**
- **사용자 id는 사용자가 가입할 때 정한 로그인 아이디(문자열)다**(2026-09-19 확정). uuid나 일련번호를 따로
  두지 않는다. `matching`이 이미 `String`으로 다루고(docs/11 D-4) `social.blocks.blocker_id/blocked_id`도
  문자열로 읽으므로 변환 지점이 없다. docs/WHY_POSTGRESQL의 `uuid` 서술은 이 결정으로 낡았다.
  이 값은 Redis 채널 이름(`qm:pubsub:push:{userId}`)과 URL에 그대로 나간다 — **바꿀 수 없는 값으로 다룬다**
  (아이디 변경 기능을 만들지 않는다). 허용 문자·길이는 가입 검증에서 정한다 → §7.
- 옆 폴더 `matching/db-design/`은 **다른 설계의 흔적이다**(LoL 전용·Discord 로그인·차단 제외). 근거로 쓰지 마라.

## 4. 기술 스택 (결정됨)

| 항목 | 값 |
|---|---|
| 언어 | **Java 21** (`matching/backend/build.gradle`의 `JavaLanguageVersion.of(21)`) |
| 프레임워크 | **Spring Boot 4.1.1** (MVC, 서블릿) — `matching`·`notification`과 같은 버전. 한 사람이 세 서비스를 같이 다루므로 의존성·설정 감각을 한 벌로 유지한다 |
| 빌드 | Gradle (`io.spring.dependency-management` 1.1.7), **단일 모듈**, 앱은 `backend/` 아래 |
| 저장소 | **PostgreSQL** (docs/11 #4, 근거 docs/WHY_POSTGRESQL.md) + Flyway. Redis는 알림 발행(§3.2)·refresh 토큰(§5)·`room`의 방 키 읽기(§3.3) — 그 밖의 용도는 §7 |
| 기본 포트 | **8082 (제안)** — `matching` 8080, `notification` 8081과 로컬에서 같이 띄우기 위해. 문서에 정해진 값은 없다 |

## 5. 설계 규칙

- **stateless다.** 프로세스 로컬 상태(메모리 세션, 로컬 캐시에 의존한 판정)를 두지 않는다. 설정은 환경변수 +
  기본값으로 받고, `/health/live`·`/health/ready`, SIGTERM graceful shutdown, stdout JSON 로그를 지킨다 (docs/11 #15·#20).
- **경계를 넘는 동기 호출을 새로 만들지 않는다.** 다른 앱에 시킬 일은 outbox → SQS, 사용자에게 알릴 일은
  Pub/Sub이다. 둘을 섞지 마라 — 놓치면 데이터가 어긋나는 것이 SQS, 놓쳐도 조회로 복구되는 것이 알림이다 (docs/14 §7).
- **불변식은 DB가 강제한다.** `조회 → 애플리케이션 판단 → 삽입`으로 지키지 마라. "같은 사람 두 번 차단
  금지"는 UNIQUE다 (docs/WHY_POSTGRESQL §1. 같은 절의 INV-9 exclusion constraint는 이제 `app:reservation`의 일이다 — D-15).
  **H2는 스키마별 롤/GRANT를 재현하지 못하고**(docs/11 D-3) exclusion constraint도 없다 — 이 검증은 PostgreSQL에서만 의미가 있다.
- **인증 — access 토큰은 JWT이고 쿠키로 주고받는다**(`Authorization` 헤더에 싣지 않는다). **refresh 토큰은 JWT가 아니라 불투명 UUID이고
  Redis에 저장한다**(UUID → 사용자. 폐기는 지우면 끝) (docs/11 #16 · D-14). **rotation 필수** — 재발급 때 옛 UUID를 지우고 새 UUID를 준다.
  access TTL은 짧게. #16의 access용 Redis denylist(조회 실패는 fail-closed)를 둘지는 미정이다 — 세부는 §7.
- **Spring Security는 인증 세부(§7)를 정하면서 넣는다** — 먼저 넣으면 기본 설정이 모든 요청을 막는다.

## 6. 배포 기준

배포 기준은 **Stage 2(ECS Fargate)**다. Stage 1(단일 EC2 + Docker Compose)은 적용하지 않는다 (docs/11 D-18이 #20을 개정). **k8s / HPA / sticky session을 전제한 구현 금지**는 그대로다.

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라. 정해지면 이 표에서 빼고 해당 절로 옮긴다.

| 항목 | 상황 |
|---|---|
| **파티 모집 게시판의 세부** | 하는 것·방의 규칙·두 앱의 분담은 정해졌다(docs/11 D-11 · D-16, §7.1). 방 키도 정해졌다(§3.3). 목록이 보여 주는 것·목록을 누가 조립하는가·갱신 방식·**차단을 보는 범위(방 안의 누구와든)**도 정해졌다(D-20). **방장 확정의 규칙도 정해졌다**(전원 · 되돌릴 수 없다 · 확정 표시는 `room`이 쓰고 이 앱은 읽는다 — D-21). **남은 것** — 방장 확정에 남은 것(브라우저가 두 앱을 부르는 순서, 확정된 글의 목록 표시, 확정된 방의 기능), **게시판 채널 접두사의 원본을 둘 곳·재요청을 묶는 간격·`room`이 게임을 아는 법**, 입장권의 형식, **"아직 안 만들어진 방"과 "사라진 방"을 가리는 법**, 만료 글 보존 기간, 글의 내용·정렬·필터, 도배 대응 등. 개발하면서 정한다 — 해당 지점에 닿으면 그때 묻는다 |
| 모든 엔드포인트의 경로·스키마, `PARTY_*`·`FRIEND_*` 7종의 이름과 `payload` | 원본 계약이 이 컴퓨터에 없다(§3.1). 문서에 이름이 나오는 것은 예시로 든 `PARTY_MEMBER_JOINED` 하나뿐이다 (contracts/events.md) |
| SQS 메시지 본문 3종 | `ProposalConfirmed`에 무엇이 실려 오는지, 파티 id를 `proposalId`와 같게 둘지 — `matching`은 클라이언트에 `MATCH_CONFIRMED {partyId}`(= `proposalId`)를 **이미 내려 주고**, 파티는 비동기로 생기므로 그 직후 조회는 비어 있을 수 있다 (contracts/events.md "`PARTY_CREATED` 이벤트가 없다") |
| **확정된 사용자를 푸는 길** | `matching`은 확정된 사용자의 활성 요청에 `status=PARTY`를 찍어 두고, 푸는 주체가 없다. 후보 셋(`PartyClosed` 소비 / 나가기 API / 긴 TTL)이 **결론 나지 않았다** (HANDOFF.md ①). `PartyClosed.fifo`의 소비자는 platform 하나로 확정했다(§3.4) — 그래서 **`matching`의 `status=PARTY`를 누가 어떻게 푸는지는 여전히 열려 있다.** D-13 · D-16이 가능성으로 적은 "방이 닫힐 때 `room`이 활성 요청 키를 지운다"는 **D-19로 없어졌다** — `room`은 그 키에 쓰지도 지우지도 않는다. **푸는 주체는 `matching`이나 이 앱 쪽에서 찾아야 한다**(이 앱은 지금 그 키를 만지지 않는다 — 만지게 된다면 그것부터가 결정이다). 확정된 사용자는 활성 요청 키가 남아 있어 **그대로는 `room` 입장도 거절된다**(409 `ALREADY_QUEUED`) — 자동 매칭 파티의 방 입장(§7.1)을 정할 때 같이 풀어야 한다 |
| 테이블 컬럼, DB 롤 | 롤은 이름(`matching`은 `qm_matching`), 한 앱이 네 스키마를 어떤 롤로 붙는지, 롤/GRANT를 누가 만드는지 |
| 인증 세부 | 방식은 정해졌다(§5, docs/11 D-14). **남은 것** — ① 쿠키 속성(`HttpOnly`·`Secure`·`SameSite` Lax/Strict·`Path`·`Domain`·수명, refresh 쿠키의 `Path`를 재발급 경로로 좁힐지) ② **CSRF 대응**(`SameSite`만인가, CSRF 토큰·`Origin` 검사를 더하는가 — 시그널 `POST`·방 입장·차단·매칭 요청이 전부 해당) ③ `matching`·`notification`·`room`이 access 토큰을 검증하는 법(HS256 비밀 키 공유 / RS256 공개 키 검증, 키를 나눠 갖는 법, 클레임 구성) ④ access·refresh 수명, access denylist를 둘지 ⑤ refresh의 Redis 키 이름·값·TTL(매칭 키와 겹치지 않는 접두사), 기기별 허용 개수, 옛 값 재사용(탈취 신호) 처리 ⑥ SSE와 토큰 만료 — `EventSource`는 200이 아닌 응답에 재접속을 멈춘다 ⑦ 로컬 개발의 CORS·`credentials`·`withCredentials` ⑧ 인증이 붙기 전 임시 식별(`matching`·`notification`·`room` 세 서비스가 지금 `userId` 파라미터를 받는다. `room`은 그 자리를 `TEMP-NO-PLATFORM`으로 표시해 뒀다)과 전환 시점 |
| 게임 계정 연동 | 라이엇 등 외부 API 연동 범위. `matching`의 티어는 지금 자기신고다 (`matching/CLAUDE.md` §2) |
| `reservation` 스키마의 마이그레이션 | 예약은 `app:reservation`(Lambda)으로 빠졌다(D-15). Spring/Flyway가 없는 Lambda가 스스로 마이그레이션하기 어렵다 — **이 앱이 대신 갖는지 별도 절차인지 미정이다.** 정해지기 전에 이 앱에 `reservation` 마이그레이션을 넣지 않는다 |
| Redis의 다른 용도 | 파티 presence/ready(`qm:party:presence:*`·`qm:party:ready:*`), rate limit은 원본 docs/07의 **키 이름만** 있다. 누가 쓰는지(presence는 D-16으로 `room` 쪽 성질이 됐다), `matching`의 `qm:party:*`와 접두사가 겹치는 것을 어떻게 할지 |
| SQS 배선 시점, 테스트용 PostgreSQL, 포트 | `ProposalConfirmed` 소비와 `PartyClosed`에 AWS SDK를 언제 들일지(`matching` 쪽 발행이 아직 없다). 테스트용 PostgreSQL을 띄우는 법. 기본 포트 8082 확정 |

### 7.1 파티 모집 게시판 — 이 앱에 남는 것 (docs/11 D-11 · D-16 · D-20 · D-21). 방 안의 규칙은 `../room/CLAUDE.md`

**정해진 것.**
- 모집 글을 올리면 **그것이 곧 파티방**이다. 자동 매칭 뒤에 생기는 파티방과 **같은 개념의 방**을 쓴다. 목적은 **모집의 응답성**이다.
- **글·목록·글의 상태·방장 확정·파티원 기록·입장권 발급은 이 앱**, 방 만들기·입장·나가기·강퇴·정원·접속 확인·입장 표시 키·방 알림·시그널은 **`room`** 이다(D-16 · D-19).
- **차단 관계가 있으면 그 방은 목록에 아예 보이지 않는다**(막는 것이 아니라 보이지 않게). **어느 쪽이 차단했든** 같다. 목록을 만들 때 이 앱이 거른다.
- **차단을 보는 범위는 "방 안의 누구와든"이다**(2026-09-20, D-20). 방 안에 나와 차단 관계(어느 방향이든)인 사람이 **한 명이라도** 있으면 **그 방 자체를 내 목록에서 뺀다** —
  들어가면 음성으로 바로 마주치기 때문이다. 검토하고 버린 것 — 방장과의 사이에서만 본다 / 방은 보이되 그 사람의 카드만 가린다. **입장권을 내줄 때도 같은 규칙이다**(§3.3).
  대가 — 목록을 그릴 때마다 글마다 방 안 전원과 요청자의 차단 관계를 대조해야 하고, 멤버 SET의 유령 때문에 **이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(§3.3).
- **목록의 한 줄이 보여 주는 것**(D-20) — ① 방 안에 몇 명인가 ② **방 안 사람들의 카드**(닉네임·티어·포지션 등) ③ 글의 **"찾는 포지션" 가운데 이미 방 안에 있는 포지션의 강조**
  ④ **F5 없이 갱신된다.** 목록의 단위는 여전히 **모집 글(방)**이다 — 카드는 방에 딸려 나오고, 사람을 검색하거나 방과 무관하게 둘러보는 기능은 여전히 금지다(§1).
- **포지션의 출처는 프로필의 주 포지션이다**(이 앱 DB의 계정·게임 계정). 입장할 때 고르는 방식은 **하지 않는다** — 그러면 `room`이 포지션을 들어야 하고 멤버 SET을
  HASH로 바꿔야 해서 방 키 약속이 바뀐다. ③의 강조는 "찾는 포지션 ∩ 방 안 사람들의 주 포지션"이다.
- **목록의 데이터는 전부 이 앱이 조립한다** — 멤버 SET의 `SCARD`·`SMEMBERS` → 이 앱 DB에서 프로필 붙이기 → 차단 대조. 서비스 간 호출을 만들지 않고 `room`은 `userId`만 안다(§3.3).
- **갱신은 게시판 채널의 "바뀌었다" 신호로 한다**(§3.2 "게시판 채널") — 채널 `qm:pubsub:board:{game}`, `type` `BOARD_CHANGED`, `payload` `{}`(**`roomId`도 싣지 않는다**),
  클라이언트는 SSE를 열 때 `topics=board:LOL`로 구독을 알린다. 신호는 "다시 받아라"일 뿐이고 받은 프런트가 이 앱의 목록을 `GET`으로 다시 요청한다. **정해졌고 구현 전이다.**
- **파티원은 방장이 확정한다.** 확정하면 **더 이상 새 사람이 들어올 수 없다** — 확정된 글에 입장권을 내주지 않는 것으로 지키고, `room`도 확정된 방의 입장을 409 `ROOM_CONFIRMED`로 거절한다.
- **방장 확정의 규칙이 정해졌다**(2026-09-20, D-21 · `docs/ROOM_CONTRACT.md` "방장 확정"). 확정 요청은 **`room`이 받는다**(방장만 · 2명 이상). 대상은 **그 순간 방에 있는 전원**(방장 포함)이다 — 방장이 고르지
  않고, 원치 않는 사람은 그 전에 강퇴한다. **되돌릴 수 없다** — 확정 뒤 빈자리가 생겨도 다시 모집을 열 수 없다. 확정 뒤에도 방장이 나가면 방은 통째로 없어진다. **이 앱의 몫은 확정 표시 키
  `qm:room:{roomId}:confirmed`와 멤버 SET을 읽어 글의 상태를 "확정"으로 바꾸고 파티원을 기록하는 것**이고, **`room`의 키에 쓰지 않는다** — "닫힘" 표시를 이 앱이 쓰는 방법은 받지 않았다(§3.3).
- **방장이 나가면 글은 지우지 않고 "만료"로 표시한다.** 목록에 남지만 들어갈 수 없다. 방장 이탈은 `room`이 판단하고(명시적 나가기와 **연결 끊김 둘 다** — 방의 수명은
  방장의 접속 확인만 늘린다), **이 앱은 목록을 그리거나 입장권을 요청받을 때 방장 키(`qm:room:{roomId}:host`)가 사라진 것을 보고 글을 만료로 바꾼다** — `room`은 알려 주지
  않는다. 만료가 즉시는 아니지만 목록을 그리는 순간 걸러지므로 보이는 차이는 없다(방장이 말없이 사라진 경우는 수명이 다할 때까지 최대 10분 늦는다 — §3.3).
- **방 키의 이름·구조·수명**이 정해졌다(§3.3 · `docs/ROOM_CONTRACT.md`). **방은 `room`의 "방 만들기" 요청이 만든다** — 입장은 방을 만들지 않는다(없는 방은 404).
- **`room`이 내는 알림이 정해졌다** — `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · `WEBRTC_SIGNAL`(전부 구현돼 있다 — D-21).
  `PARTY_*`를 다시 쓰지 않고 새 이름을 지었다 — 그 7종의 이름과 `payload`가 이 컴퓨터의 문서에 없어서다(`../room/contracts/room-api.md` "알림").
- 입장 승인 없음·둘러보는 상태·강퇴·최대 5명·음성 제어(브라우저에서 끝난다)는 `room`과 프런트 쪽 규칙이다. "자동 매칭 대기와 방은 한 번에 하나만"은 `room`과 `matching`이
  키 둘로 지킨다(D-19) — 이 앱은 관여하지 않는다.

**정할 것 — 개발하면서 정한다.** 구현하다 해당 지점에 닿으면 **그때 묻고 정한다.** 임의로 정해 구현하지 마라.
- **방장 확정에 남은 것**(대상 · 되돌릴 수 없음 · 확정 표시를 누가 쓰는가는 정해졌다 — 위) — **브라우저가 `room`과 이 앱을 어떤 순서로 부르는가**(D-21 "아직 미정". `docs/ROOM_CONTRACT.md` 머리 절에 그때 같이 볼 물음 둘이 있다). 확정된 글의 목록 표시(만료와 같은가). 확정된 방이 자동 매칭 파티방과 **같은 기능(Ready 등)**을 갖는가, "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가.
- **차단에 남은 것**(범위는 정해졌다 — 위) — 입장권을 **받은 뒤 들어오기 전** 사이에 나와 차단 관계인 사람이 먼저 그 방에 들어온 경우의 경쟁(D-20 "아직 미정"). 목록을 본 뒤 차단이 생긴 경우, 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우(D-11의 미정 그대로).
- **게시판 채널에 남은 것**(§3.2. 이름·`type`·`payload`·`topics`는 정해졌다 — 위) — 채널 접두사의 **원본 상수를 어느 서비스에 둘지**, 프런트가 재요청을 묶는 간격, 한 연결이 여러 주제를
  구독할 때의 `topics` 표기(쉼표로 나열하는지 등). **`room`이 어느 게임의 채널에 발행할지를 어떻게 아는가** — `room`은 지금 방이 어느 게임의 것인지 모른다. 방 만들기 때 받아 방 키에
  두는지(그러면 방 키 약속이 바뀐다), **입장권이 말해 주는지**(그러면 이 앱이 입장권에 게임을 담는다) 정해지지 않았다(D-20 "아직 미정").
- **목록의 세부**(D-20) — ③의 강조를 이 앱이 계산하는가 프런트가 계산하는가. 카드에 담는 것(닉네임·티어·포지션 "등"의 나머지). 글의 "찾는 포지션"을 어떤 모양으로 담는가.
- **입장권**의 형식·서명 방식·수명·담는 정보, 서명 키를 두 앱이 나눠 갖는 법. 입장권이 정해지면 `room`의 "방 만들기"가 **방장임을 입장권으로 확인**한다 — 그 요청이
  그대로 남는지 방장의 첫 입장에 합쳐지는지도 그때 다시 정한다(`../room/CLAUDE.md` §7).
- **"아직 안 만들어진 방"과 "사라진 방"을 가리는 법** — 방장이 글을 쓴 직후, `room`의 방 만들기를 부르기 **전**에는 방장 키가 없다. 목록을 그리면서 "방장 키가 없다 →
  방이 사라졌다 → 글을 만료로"를 그대로 적용하면 **방금 쓴 글을 만료시킨다.** 방법의 예 — 글 작성 직후 일정 시간은 만료 검사를 건너뛴다 / 방이 한 번 생긴 적이 있는
  글만 검사한다. **정해지지 않았다.**
- **자동 매칭으로 확정된 파티의 방은 어떻게 생기는가** — 이 앱이 파티를 DB에 만든 뒤 파티원이 입장권으로 들어오는 것이 자연스럽지만 미정이다. **확정된 사용자는 활성 요청 키(`status=PARTY`)가 남아 있어 그대로는 `room` 입장이 거절된다** — §7 "확정된 사용자를 푸는 길"과 같이 풀어야 한다(D-19 "아직 미정"). 강퇴당한 사람의 재입장을 입장권으로 막을지.
- 만료된 글을 **언제까지** 목록에 두는가, 만석일 때의 목록 표시. 글에 담는 것(게임·모드·원하는 조건·한 줄 소개 등), 목록의 정렬·필터. **도배 글 대응**과 신고(docs/11 #13)의 연결.
- **알림 종류** — 방 입장·퇴장·방 닫힘·강퇴·**방장 확정**(`ROOM_CONFIRMED` — `payload`의 `members`가 파티원이다)은 `room`이 새 이름으로 내는 것으로 정해졌다(위). **이 앱이 낼 알림**(`PARTY_*` · `FRIEND_*`)의 이름과 `payload`는 여전히 미정이다(§3.2). 계약 원본과 합칠 때 `ROOM_*`과 `PARTY_*`가 같은 뜻인지 맞춰야 한다. 모든 엔드포인트 경로·스키마·테이블.

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `platform` 브랜치**다. 이력이 없는 별도 브랜치에서 시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       matching 브랜치
├── notification/   notification 브랜치
├── room/           room 브랜치. 방 안의 일 (D-16)
└── platform/       platform 브랜치 (이 폴더)
    ├── docs/       옆 폴더 문서의 사본 (ROOM_CONTRACT.md — §10)
    └── backend/    스프링 앱을 둘 자리 (아직 없다). matching/backend/ 와 같은 모양으로 만든다
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

**무엇부터 만들지는 `README.md` "만드는 순서"를 따른다.** 순서를 바꾸려면 먼저 묻는다.

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
- 위 함정은 전부 `room`을 만들며 실제로 겪은 것이다. 명령과 그 밖의 환경 함정(`pkill -f` 금지, 느린 빌드)은 `../room/docs/NOTIFICATION_LESSONS.md`에 있다.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 매칭 엔진 규칙 (제품 경계·INV·Contract first의 원형) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 배달 규칙 (받는 쪽이 메시지를 어떻게 다루나) / **방 안의 일의 규칙**(입장권을 받는 쪽, 방 키의 원본) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/notification/CLAUDE.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/CLAUDE.md` |
| **`room` 계약의 사본** — 이 앱이 읽는 Redis 키, 방 만들기·입장·접속 확인, `room`이 내는 알림, 이 앱에서 그것이 뜻하는 것. 옆 폴더가 없어도 읽을 수 있다. **낡는다** — 머리의 확인 명령을 돌린다 | `docs/ROOM_CONTRACT.md` (이 폴더) |
| `room` 계약의 원본 (요청 전부 · 알림 · "Redis 키" 절) / 방 키 상수의 원본 / 이 앱이 생기면 채워 줘야 하는 자리(`TEMP-NO-PLATFORM`, §2) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/contracts/room-api.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/backend/src/main/java/com/queuemate/room/redisKeys/RoomKeys.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/START_HERE.md` |
| 로컬 환경 함정 (띄우고 죽이기 · IntelliJ · worktree · 테스트) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/docs/NOTIFICATION_LESSONS.md` |
| 결정 로그 — #13~#17 · #20~#26 · D-1~D-4 · **D-9** · **D-11**~**D-16** · **D-18** · **D-19**(D-11 16번과 D-16의 활성 요청 키 대목을 개정 — 파일 머리의 "낡은 항목 주의"로 걸러 읽는다) · **D-20**(게시판 목록 — 방 안 사람 카드·이 앱이 조립·게시판 채널 신호·차단은 방 안의 누구와든. D-11 14번의 범위를 정하고 D-16의 방 키 읽기 범위를 늘렸다) · **D-21**(`room`의 방 안의 규칙과 계약 — 방 키 · 수명 · 방장 확정. **확정 표시는 `room`이 쓰고 이 앱은 읽는다.** D-16의 "닫힘 표시" 가능성은 받지 않았다) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/11_DECISION_LOG.md` |
| 알림 계약 (봉투·발행 주체·`WEBRTC_SIGNAL`·SQS FIFO·계약 구멍) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/events.md` |
| 계약 사본의 지위와 앞서간 변경을 적는 법 / platform 소관 자원 목록(머리말) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/README.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/openapi.yaml` |
| 봉투를 만드는 코드(본보기) / 채널 접두사 원본(`PUSH_CHANNEL_PREFIX`) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` |
| `matching`이 읽는 `social.blocks`의 모양 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/test/resources/schema.sql` |
| 왜 PostgreSQL인가·스키마 배치·outbox·INV-9 제약 / 배포 그림에서 platform의 자리 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/WHY_POSTGRESQL.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/AWS_ARCHITECTURE.md` |
| 제품 정의와 non-goals / 예약 규칙 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/00_PRODUCT_SPEC.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/04_RESERVATION_MATCHING_SPEC.md` |
| 버전 맞추기, 설정 본보기 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/build.gradle` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/resources/application.yaml` |
| `matching`이 platform을 기다리는 일 (① 파티 풀기 ③ outbox) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/HANDOFF.md` |

## 11. 이 저장소에서 하지 말 것 (요약)

- 매칭 로직, 매칭 Redis 키 접근(**예외 없음** — 활성 요청 키는 `matching`만, 입장 표시 키는 `room`만 쓴다. 이 앱은 둘 다 만지지 않는다, D-19) — `matching`의 일이다. `SseEmitter` / WebSocket / 연결 보유 — `notification`의 일이다
- **방 안의 일**(방 만들기·입장·나가기·강퇴·정원·접속 확인·입장 표시 키·방 알림·시그널 `POST`·`WEBRTC_SIGNAL`) — `room`의 일이다(D-16 · D-19). `room`을 호출하지도 마라 — 입장권과 방 키 읽기로만 잇는다. 방 키에 **쓰지 마라** — 확정 표시도 `room`이 쓴다("닫힘" 표시를 이 앱이 쓰는 방법은 받지 않았다 — D-21). **방장 키가 없다고 방금 쓴 글을 만료시키지 마라** — 아직 안 만들어진 방일 수 있다(§7.1 미정)
- 예약(REST·짝 찾기·`RESERVATION_*` — `app:reservation`, D-15), TURN credential 발급, gameconfig — 각각 다른 배포 단위의 일이다
- 엔드포인트 경로·스키마·payload 필드·테이블 컬럼을 **지어내기** — 묻고, 정한 것은 `contracts/`에 적는다
- 공개 사용자 탐색(사람 검색·둘러보기)·길드·피드·팔로우·좋아요·모집과 무관한 공개 채팅방 — 여전히 금지다(§1 · D-11)
- 게시판 모집의 미정 사항(§7.1)을 **임의로 정해 구현하기** — 구현하다 그 지점에 닿으면 그때 사용자에게 물어라
- 채널 접두사를 `matching`과 따로 바꾸기, 방 키 형식을 `room`과 따로 적기, 알림 실패로 본 작업을 실패시키기, 모집 중이 아니거나 차단 관계인 글에 입장권 내주기(**차단은 방장만이 아니라 방 안의 전원과 본다** — D-20)
- 게시판 채널 신호에 **데이터(프로필·방 안 사람 등) 싣기** — 방송은 사람별로 거를 수 없어 차단이 뚫린다. 포지션을 `room`에 들게 하기, 목록을 그리려고 `room`의 API 부르기(D-20)
- `social.blocks`의 모양을 `matching`과 상의 없이 바꾸기, 크로스 스키마 FK·JOIN, 예외 GRANT 늘리기
- `조회 → 판단 → 삽입`으로 불변식 지키기, H2로 제약·권한을 검증했다고 치기, 경계를 넘는 새 동기 호출
- Kafka/RabbitMQ/Redis Streams, k8s/HPA/sticky session 전제 구현
- 포트 6379·5432 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`, 옆 폴더 파일 수정
