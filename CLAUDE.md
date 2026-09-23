# CLAUDE.md — platform 서비스 규칙 (Non-Negotiable)

작업 전에 `START_HERE.md`(지금 어디까지 됐나 · 만드는 순서 · 다음에 닿기 전에 물어야 하는 것) → 이 파일 → `README.md` → **`contracts/platform-api.md`**(이 폴더에서 정한 계약 — 경로 · 스키마 · 에러 코드 · 토큰 · 입장권) 순으로 읽어라.

이 폴더는 QueueMate의 **API 서버 배포 단위 하나**다. 문서에서 **`app:platform`**이라고 부르는 것이 이것이다
(docs/11 #15). 매칭 엔진 규칙은 옆 폴더 `matching`의 `CLAUDE.md`에, 알림 배달 규칙은 `notification`의, **방 안의 일**(입장·강퇴·시그널)의
규칙은 `room`의 `CLAUDE.md`에 있고(docs/11 D-16), 이 파일은 **계정·파티·소셜 REST에 걸리는 부분만** 담는다(예약은 `app:reservation`으로 빠졌다 — docs/11 D-15).

> **출처 표기.** `docs/…` · `contracts/…` · `HANDOFF.md`처럼 폴더 이름 없이 적은 것은 전부 옆 폴더
> `matching` 기준이다(절대 경로는 §10). **출처가 안 붙은 사실은 정해지지 않은 것이다** — §7로 보낸다.
> **`contracts/platform-api.md`만은 이 폴더의 것이다**(옆 폴더의 계약은 `contracts/events.md` · `contracts/openapi.yaml` · `contracts/README.md`처럼 다른 파일 이름이다).

> **지금 상태와 그 지위(2026-09-21).** 빈 뼈대에서 **1단계(계정 · 인증 · 소셜 로그인 · 게임 프로필) · 2단계(차단) · 3단계(모집 글 · 목록 · 입장권 · 게시판 채널 신호) · 5단계(방장 확정의 기록) · 7단계의 일부(친구 · 신고 · 최근 함께한 사람의 읽기 · 알림 둘)가 구현됐다**(`START_HERE.md` §1). **6단계(SQS)는 막혀 있고 refresh 토큰과 게임사 API 연동은 없다.**
> 소유자가 "네가 platform을 만들어 봐라"고 맡겼고, 이 파일이 "미정 — 임의로 정해 구현하지 마라"로 묶어 두었던 것들을 **Claude가 정해 구현했다. 소유자는 아직 항목별로 검토하지 않았다.**
> 그렇게 정한 것의 원본은 **`contracts/platform-api.md`**다(머리의 "지위" 문단 · 맨 아래 "원본에 올려야 할 것" **P-1~P-10.** P-11은 2026-09-22 소유자 결정이다 — 바로 아래). **docs/11에 D-항목이 하나도 없다** — `matching` 폴더에서 올려야 한다.
> 이 파일에서 출처가 `contracts/platform-api.md`인 것은 **전부 그 지위다** — 소유자가 검토하며 뒤집을 수 있다. 소유자가 직접 정한 것은 "소유자 확정" · "소유자 지시"라고 따로 적었다.
> **"미정이니 묻고 정하라"는 규칙은 남은 미정(§7 · §7.1 · §7.2)에 대해 그대로 유효하다** — 이번에 맡긴 것이 다음에도 임의로 정해도 된다는 뜻은 아니다.

> **2026-09-22 소유자 결정 둘 — 이것은 소유자가 직접 정했다.**
> ① **모든 테이블의 PK를 `bigint GENERATED ALWAYS AS IDENTITY`로 하고, 사용자의 식별자를 둘로 가른다** — **`users.id`(사용자 번호)가 `userId`**이고 **로그인 아이디는 `login_id` · `loginId`로 따로** 둔다(§3.5).
> **2026-09-19의 "사용자 id는 가입할 때 정한 로그인 아이디(문자열)"를 개정하는 것이고 docs/11 D-4와 얽힌다.** 코드와 마이그레이션은 전부 이 모양이다 — 계약은 `contracts/platform-api.md`(P-11).
> **`matching`의 `block/Block.java`를 `Long`으로 바꿔야 한다(아직 안 바꿨다 — 그 폴더의 일이다).**
> ② **스키마별 DB 롤을 두지 않는다**(§3.5 — docs/11 #17 · D-1을 개정한다). **둘 다 docs/11에 D-항목이 없다.**

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다 — "조건은 사용자가 정하고, 사람 선택은
시스템이 한다"(docs/00 §1). 이 서비스는 매칭의 **앞(계정)과 뒤(모집 글·확정된 파티·친구·차단·신고)**를 맡는다.
자동 매칭이 **기본 경로**이고 **파티 모집 게시판이 두 번째 경로**다 (docs/11 D-11). **이 서비스는 오래 남는 것(PostgreSQL)을
다루고, 지금 방에 누가 있는지처럼 금방 사라지는 것은 `room`(Redis)이 다룬다** (docs/11 D-16).

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (account · party · social)
                                 │  ▲
                                 │  └── SQS ProposalConfirmed.fifo ◀── matching   (파티를 만들어라 — 아직 배선되지 않았다, §7)
                                 │      (차단은 SQS 로 알리지 않는다 — matching 이 social.blocks 를 직접 읽는다)
                                 ├──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
                                 ├──── PUBLISH qm:pubsub:board (BOARD_CHANGED · {}) ──▶ Redis ──▶ notification ──SSE──▶ 모든 연결
                                 ├──── 읽기만 ◀── Redis qm:room:{roomId}:host · :members · :confirmed   (room 이 쓴다. 이 앱은 쓰지 않는다 — §3.3)
                                 ├──── 인가 코드 흐름 ──▶ 카카오 · 디스코드   (소셜 로그인 — 회원 번호와 닉네임만 받는다)
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
| **계정** — 회원가입/로그인/로그아웃, 기본 프로필, 게임 계정 연결/해제. **인증 토큰 발급** — access는 **쿠키로 주고받는 JWT**, refresh는 **Redis에 저장하는 불투명 UUID**(재발급·폐기도 이 앱). **서명은 RS256이고 개인 키는 이 앱만 갖는다** — 다른 세 서비스는 공개 키로 검증만 한다. **access 만으로 시작한다**(refresh는 배포 전까지 붙인다 — §5.1). **식별자는 둘이다(2026-09-22 소유자 결정 — §3.5)** — `userId`는 DB가 매기는 **사용자 번호**(bigint identity)이고 가입·로그인에 쓰는 **로그인 아이디는 `loginId`**로 따로 있다(중복은 409 `LOGIN_ID_TAKEN`). **소셜 로그인(카카오 · 디스코드 — 2026-09-21 소유자 지시)** — 로그인 아이디는 여전히 가입할 때 정하는 것이라 소셜로 **처음** 온 사람은 로그인 아이디 · 닉네임을 정하는 한 단계를 거친다(이 방식은 Claude가 정했다). **게임 프로필** — 게임 계정(자기신고: 게임 닉네임 · 티어 · 주 포지션 · PUBG의 서버)에 읽기 전용 `verified` · `stats`(전적 스냅숏)를 붙여 밖에 보여 주는 모양. `users/me`와 목록의 카드가 같이 쓴다. **구현됐다(2026-09-21)** — 단, refresh는 없고(`TEMP-NO-REFRESH`), 소셜 로그인은 **가짜 제공자로만 테스트했으며**, **전적을 채우는 기능이 없어 `stats`는 늘 `null`이고 `verified`를 켜는 길도 없다**(§7 "게임 계정 연동") | docs/00 §5 · docs/11 #16 · D-14 · docs/AWS_ARCHITECTURE §3 · §5.1 · `contracts/platform-api.md` "계정" · "게임 프로필" · "소셜 로그인" |
| **파티** — `ProposalConfirmed.fifo`를 소비해 **DB에 파티를 만든다.** 확정된 파티와 파티원의 기록. 파티가 닫히면 `PartyClosed.fifo` 발행(소비도 이 앱). 파티룸의 **나가기·지금 누가 있나**는 `room`의 일이다(D-16). **구현된 것은 게시판 쪽 기록뿐이다** — 방장 확정으로 생기는 파티(`party.parties`의 `source` = `BOARD`)와 파티원. **`ProposalConfirmed.fifo` 소비 · `PartyClosed.fifo` · `party.outbox`는 없다**(SQS 배선이 미정이다 — §7) | docs/11 #21 · D-13 · D-16 · docs/00 §5 · `matching/CLAUDE.md` §9 · `contracts/platform-api.md` "방장 확정의 기록" |
| **파티 모집 게시판(오래 남는 쪽)** — 모집 글 쓰기·수정, 게시판 목록, **차단 관계 거르기**(`social.blocks`가 같은 앱에 있다. **방 안의 누구와든** 본다 — D-20), 글의 상태(모집 중/확정/만료), **방장 확정의 기록 — 글의 상태를 "확정"으로 바꾸고 파티원을 기록한다**(확정 요청 자체는 `room`이 받아 확정 표시 키를 쓴다. 이 앱은 그 키와 멤버 SET을 **읽는다** — D-21 · §3.3), **입장권 발급**(글이 모집 중이고 **방 안의 누구와도** 차단 관계가 아닐 때만 서명해 준다). **목록의 한 줄은 이 앱이 전부 조립한다**(D-20) — 방 안에 몇 명인가, **방 안 사람들의 카드**(닉네임·티어·포지션 등 — 프로필은 이 앱의 DB에 있다), **글의 "찾는 포지션" 가운데 이미 방 안에 있는 포지션의 강조**(포지션의 출처는 **프로필의 주 포지션**이다. 입장할 때 고르지 않는다). 목록·입장권·확정 때 `room`의 방 키를 **읽는다**(§3.3). **목록의 한 줄은 게임마다 다른 정보를 보여 준다**(2026-09-21 소유자 지시 — 본보기는 OP.GG의 듀오 찾기. §7.1). **구현됐다(2026-09-21)** — 글 쓰기 · 고치기 · 지우기(만료로 바꾼다) · 목록 · 단건 · 입장권 · 방장 확정의 기록 | docs/11 D-11 · D-16 · **D-20** · **D-21** · `contracts/platform-api.md` "모집 글 · 목록 · 입장권" |
| **소셜** — 친구 요청/수락/거절/삭제, 차단/해제, 신고, 최근 함께한 사람. **`social.blocks`의 소유자**다. 차단은 **DB에 저장하는 것으로 끝낸다** — `BlockChanged.fifo`는 만들지 않는다(§3.4). **차단은 구현됐다(2026-09-21)** — `social.blocks` · API 셋(**스키마별 DB 롤은 두지 않는다** — 2026-09-22 소유자 결정, §3.5). **친구(요청 · 수락 · 거절 · 거두기 · 목록 · 끊기) · 신고(접수만) · 최근 함께한 사람(읽기)도 같은 날 들어왔다**(`social/V6__friends_reports_recent_players.sql` — 통과 여부는 `START_HERE.md` §1). **최근 함께한 사람은 읽는 쪽만 있고 채우는 주체가 없다 — 늘 빈 목록이다**(`PartyClosed.fifo` — §7). **친구 목록은 사람을 찾아보는 기능이 아니다** — 아이디를 정확히 알아야 요청을 보낼 수 있고 검색 API는 만들지 않는다(§1) | docs/00 §5 · D-1 · D-2 · D-12 · `contracts/platform-api.md` "차단" · "친구 · 신고 · 최근 함께한 사람" |
| **알림 발행** — `PARTY_*`·`FRIEND_*` 7종. **`FRIEND_*` 둘의 이름과 `payload`가 정해졌다** — `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`(`contracts/platform-api.md` "이 앱이 내는 알림"). **이 둘의 발행은 구현됐다**(2026-09-21 — `common/push/`). **나머지(`PARTY_*` 등)는 여전히 미정이다** — §7. `PARTY_*`는 아직 내지 않는다(자동 매칭 파티가 없다). **방 입장·퇴장·방 닫힘·강퇴·방장 확정 알림(`ROOM_CONFIRMED`)과 `WEBRTC_SIGNAL`은 `room`이 발행한다** — `PARTY_*`를 다시 쓰지 않고 새 이름(`ROOM_MEMBER_ENTERED` 등)을 지었다(§3.3). **게시판 채널에 "바뀌었다" 신호 발행** — 글이 생기거나 사라지거나 상태가 바뀔 때(§3.2 "게시판 채널" — 채널 `qm:pubsub:board`(게임을 구분하지 않는 하나), `type`은 `BOARD_CHANGED`, `payload`는 `{}`. **구현됐다(2026-09-21)** — 글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정되면 커밋 뒤에 발행한다, D-20 · D-22) | contracts/events.md · docs/11 D-16 · D-20 · D-21 · D-22 · `../room/contracts/room-api.md` "알림" · `contracts/platform-api.md` |

platform 소관 자원(contracts/openapi.yaml 머리말): `auth` `users` `parties` `friends` `blocks` `recent-players` `reports`. **경로와 스키마는 거기 없다**(§3.1) — **이 폴더에서 정한 것이 `contracts/platform-api.md`에 있다**(`parties` 자원은 아직 없다 — 글은 `posts`, 친구 요청은 `friend-requests`라는 이름을 새로 지었다). 머리말은 `reservations`도 platform 소관으로 적지만 D-15로 `app:reservation`의 것이 됐다.

| 안 한다 | 왜 / 누가 |
|---|---|
| 매칭·제안·수락·확정, 매칭 Redis 키(`qm:party:*` `qm:user:*` `qm:proposal:*` `qm:gameconfig:*` `qm:lock:*`) 접근. **예외가 없다** — "한 번에 하나만"은 키 둘로 지키고 **이 앱은 둘 다 만지지 않는다**: 활성 요청 키(`qm:user:active-request:{userId}`)는 `matching`만, 입장 표시 키(`qm:user:active-room:{userId}`)는 `room`만 쓰고 지우며 서로 상대 키를 `EXISTS`로만 본다(D-19가 D-11 16번과 D-16의 해당 대목을 개정) | `matching`의 일이다. 진행 중 매칭 상태의 원본은 Redis이고 그 주인은 `matching`이다 (docs/11 #27) |
| **방 안의 일** — 방 만들기·입장·나가기·강퇴, 정원 5명 검사, 접속 확인과 방장 이탈 감지, 입장 표시 키 쓰고 지우기(활성 요청 키는 `EXISTS`로 보기만 한다 — D-19), 방 알림(`ROOM_*`) 발행, 시그널 `POST` 받기와 `WEBRTC_SIGNAL` 발행 | **`room`(`app:room`)** 의 일이다 (docs/11 D-16 · D-19). 금방 사라지는 상태라 Redis에만 둔다. 이 앱과 `room`은 **서로 호출하지 않는다** — 입장권과 방 키로 잇는다(§3.3) |
| 브라우저 연결 보유 — `SseEmitter` / WebSocket | `notification`의 일이다. 이 앱은 **stateless REST**로 남아야 무중단 교체가 자유롭다 (docs/11 #15 · docs/14 §6). WebSocket은 어느 앱에도 없다 (D-9) |
| **예약 전부** — 예약 REST(`/api/v1/reservations` 등록·조회·수정·취소, INV-9 검증), 짝 찾기 배치, `RESERVATION_*` 발행 | **`app:reservation`(AWS Lambda)** 의 일이다 (docs/11 D-15 — #24의 "예약 REST는 `app:platform`"과 `app:reservation-batch`를 대체한다). 예약은 이 앱의 다른 모듈과 같은 트랜잭션으로 묶일 일이 없다 |
| TURN 단기 credential 발급, 음성·텍스트 채팅 중계/저장 | TURN은 Cloudflare 관리형이고 발급 주체는 `app:realtime`이다 (docs/11 #25). 음성·텍스트는 브라우저 직결(WebRTC audio + DataChannel)이라 서버를 거치지 않는다 (#6). (참고: 지금은 공개 STUN만으로 개발을 시작한다) |
| gameconfig(게임 모드 설정) | `app:matching`의 모듈이다 (docs/11 #15) |

## 3. 계약

### 3.1 Contract first — platform 계약의 원본은 이 컴퓨터에 없고, 여기서 정한 것은 `contracts/platform-api.md`에 있다

- 계약 원본은 **queueMate 본 저장소(`feature/frontend` 브랜치)의 `contracts/`**이고 **이 컴퓨터에 없다.** `matching/contracts/`는
  `matching`이 노출하는 부분만의 발췌 사본이라 **platform 엔드포인트가 통째로 빠져 있다** (contracts/openapi.yaml 머리말).
- 그러므로 **경로·요청/응답 스키마·에러 코드·payload 필드를 지어내지 마라.** 사용자에게 묻는다.
  원본을 받아 올 수 있으면 그것이 먼저다.
- 여기서 정한 것은 **이 폴더의 `contracts/`**에 적고 "원본에 올려야 할 것" 표를 같이
  남긴다. 본보기는 contracts/README.md의 "이 사본이 원본보다 앞서간 변경"(A-1~A-4) 표다.
- **이제 `contracts/platform-api.md`가 있다(2026-09-21).** 공통(에러 본문 · 인증 · `Origin` 검사 · access 토큰) · 계정 · 게임 프로필 · 소셜 로그인 · 차단 · 모집 글/목록/입장권 · 방장 확정의 기록 ·
  친구/신고/최근 함께한 사람 · 이 앱이 내는 알림, 그리고 맨 아래 **"원본에 올려야 할 것" 표(P-1~P-12)**다.
  **지위** — 소유자가 맡겨 Claude가 정해 구현한 것이고 **소유자가 아직 항목별로 검토하지 않았다**(그 파일 머리의 "지위"). **원본에 platform 엔드포인트가 이미 있으면 그쪽과 맞춰야 한다**(P-1 — 이 컴퓨터에서는 볼 수 없었다).
- **코드와 그 파일은 같이 바뀐다** — 경로 · 스키마 · 에러 코드 · 클레임을 바꾸면 같은 작업에서 `contracts/platform-api.md`를 고친다(커밋은 나눈다 — §8). 거기 없는 것을 새로 정할 때는 **여전히 먼저 묻는다.**
- **에러 본문은 `matching` · `room`과 같다** — `{"code", "message", "details": [문자열]}`(`contracts/platform-api.md` "공통").

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
  **거절 · 거두기 · 친구 끊기 · 차단은 알리지 않는다. 나머지 다섯(`PARTY_*` 등)의 이름과 `payload`는 여전히 미정이다** → §7.
- 클라이언트는 알림을 "다시 조회하라"는 신호로 다룬다(contracts/events.md "순서 보장 범위"). 그러므로
  알림이 가리키는 상태는 **REST로 조회할 수 있어야 한다.**

**게시판 채널 — 목록을 F5 없이 갱신하는 신호 (docs/11 D-20 · D-22. **이 앱의 발행도 구현됐다 — 2026-09-21**, `contracts/platform-api.md` "모집 글 · 목록 · 입장권". `room`의 발행은 같은 날 먼저 구현됐다 — D-23).**

- 위 알림은 사용자 한 명의 채널로 간다. 게시판 목록을 보는 사람은 **"누구인지 모르는 다수"**라서 사용자 채널로는 보낼 수 없다. 그래서 **게시판 채널**을 새로 둔다.
  **채널은 `qm:pubsub:board`** — **게임을 구분하지 않는 하나다**(D-22가 D-20의 게임별 세 채널 `qm:pubsub:board:{game}`을 고쳤다). **`type`은 `BOARD_CHANGED`**, 봉투 네 칸은 위와 같고 **`payload`는 빈 객체 `{}`**다.
  **이 앱과 `room` 둘 다 같은 채널 하나에 `{}`를 발행한다 — 발행하는 쪽이 게임을 알 필요가 없다.**
  이 앱은 **글이 생기거나 사라지거나 상태가 바뀔 때**(구현은 글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정될 때다 — **트랜잭션이 커밋된 뒤에** 발행한다. 목록 조회가 글을 만료 · 확정으로 옮겨 적을 때도 나간다), `room`은 **방의 인원이 바뀔 때** 그 채널에 발행한다. `notification`이 그 채널을 구독해
  **살아 있는 모든 SSE 연결에** 그대로 흘려보낸다(`notification` 쪽은 구현됐다. **`topics` 파라미터는 없앴다 — D-22.** 서버에서 거르지 않는다).
  **거르는 것은 클라이언트다** — 게시판 페이지(어느 게임이든)를 보고 있으면 목록을 다시 요청하고, 게시판 페이지가 아니면 무시한다. 다른 게임의 방이 바뀐 신호에도 재요청이 나가므로 프런트가 재요청을 묶는다(간격은 미정).
  **이 신호는 "다시 받아라"일 뿐이다** — 받은 프런트가 **이 앱의 목록을 `GET`으로 다시 요청**하고(몇 초에 한 번으로 묶어서), 데이터와 차단 거르기는 그 응답에서 온다.
- **신호에는 데이터를 싣지 않는다.** 방송에 데이터를 실으면 사람별로 거를 수 없다 — 차단(§7.1)이 성립하지 않는다. 데이터는 거르는 곳인 **이 앱의 목록 조회**에서만 나간다.
  **`roomId`도 `game`도 싣지 않는다** — "뭔가 바뀌었다"만 보낸다. `roomId`는 나에게 숨겨진 방이 바뀌었다는 사실이 새어 나가지 않게 하려는 것이다.
- **왜 서버에서 거르지 않나(D-22).** 거르려면 `notification`이 연결마다 주제를 기억하고 `topics` 파라미터·주제→연결 맵·표기 규칙을 가져야 한다. 클라이언트가 무시하면 전부 필요 없다.
  대가는 둘이다 — 다른 게임의 변화에도 재요청이 나가고(묶기로 상한을 둔다), 게시판을 안 보는 연결(방 안에서 음성 중인 사람 등)에도 작은 이벤트가 간다. MVP 규모에서 받아들인다.
  **다시 볼 조건** — 부담이 되면 `payload`에 `game`을 싣거나(그러면 `room`이 방의 게임을 알아야 한다) `topics`를 되살린다. `payload`에 `game`을 싣는 안은 같은 날 먼저 정했다가 물렸다.
- **채널 이름은 위 채널 접두사와 같은 위험이다** — 발행하는 앱(이 앱·`room`)과 구독하는 앱(`notification`)이 어긋나도 컴파일·테스트가 통과한 채로 목록이 조용히 갱신되지 않는다.
  상수 한 곳에만 두고 원본이 어디인지 주석에 적는다(**원본 상수를 어느 서비스에 둘지는 미정**이다 — 지금은 **세 앱이 각자 같은 값을 적어 두었다**: 이 앱의 `party/board/BoardChannels.BOARD_CHANNEL`, `room`의 `redisKeys/SharedKeys.BOARD_CHANNEL`, `notification`의 `redisKeys/BoardChannels.BOARD_CHANNEL`. **세 값이 같아야 한다**). **발행 실패가 본 작업(글 쓰기 등)을 뒤집으면 안 된다**는 규칙도 위와 같다 — **구독해서 확인하는 테스트**가 있다(`BoardSignalTest`).
- **미정** — 채널 이름의 **원본 상수를 어느 서비스에 둘지**(알림 채널 접두사는 `matching`의 `SharedKeys`가 원본이다 — 같은 방식으로 갈지 정해지지 않았다), 프런트가 재요청을
  묶는 간격 → §7.1(`topics` 표기와 "`room`이 어느 게임의 채널에 발행할지를 어떻게 아는가"의 미정은 D-22로 물음째 없어졌다). **지어내지 마라.**
- **게시판은 실시간으로 바뀌어야 한다**(인원 · 새 글 — 2026-09-21 소유자 지시) — 이 신호로 한다. (신호 없이 프런트가 몇 초마다 목록을 다시 받는 방식으로 먼저 시작해도 된다고 적어 두었던 것은, 두 앱의 발행과 `notification`의 구독이 다 된 지금은 필요 없다.)

### 3.3 `room`과 잇는 법 — 입장권과 방 키 (docs/11 D-16 · D-19 · D-20 · D-21). 시그널링은 이 앱의 일이 아니다

> **이 절에서 이 앱의 몫은 구현됐다(2026-09-21)** — 방 키 읽기 · 입장권 발급 · 방장 확정의 기록. 입장권의 형식, `roomId`, "아직 안 만들어진 방"을 가르는 법, 확정된 글에서 방장 키가 없을 때, 브라우저가 두 앱을 부르는 순서는
> **`contracts/platform-api.md`에서 정했다 — Claude가 정했고 소유자가 항목별로 검토하지 않았다**(P-3 ~ P-6). **`room` 쪽의 입장권 검증은 아직 없다**(`TEMP-NO-PLATFORM` 그대로 — `room` 폴더의 일이다).

- **`roomId`는 글의 id다**(`party.recruit_posts.id` — **bigint identity. DB가 매긴다**. 2026-09-22 소유자 결정 전에는 UUID였다. `room`은 `roomId`를 문자열로 다루므로 방 키에는 **숫자가 십진 문자열로** 들어간다 — `qm:room:123:host`). 브라우저는 글을 쓴 뒤 그 id로 `room`의 방 만들기를 부른다(`contracts/platform-api.md` "모집 글 · 목록 · 입장권" · P-4 — 그 전에는 어느 문서에도 없던 것이다).
  같은 절은 "자동 매칭 파티는 `roomId = partyId`다"라고도 적었다 — **그쪽은 §7.2 (나) · (다)의 미정에 걸려 있다. 구현이 없다.**
  **2026-09-22로 미정이 하나 늘었다** — `matching`은 `partyId`를 UUID 문자열로 내려 주는데 `party.parties.id`는 bigint다. **그 값을 어디에 둘지는 6단계(SQS)에 닿을 때 묻는다**(§7).

- **WebRTC 시그널 `POST`와 `WEBRTC_SIGNAL` 발행은 `room`의 일이다** (D-16이 D-9의 `app:platform`을 개정). 규칙은 `../room/CLAUDE.md` §3.3.
  방 알림(`ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` — 전부 구현돼 있다, D-21)도 `room`이 발행한다 — `payload`는 `docs/ROOM_CONTRACT.md` "알림".
- **입장권.** 글을 누르면 브라우저가 먼저 이 앱에 입장권을 요청한다. 글이 **모집 중인지·차단 관계가 아닌지** 확인하고 **서명된 입장권**(방
  식별자, 방장이 누구인지, 만료 시각 등)을 준다. **차단은 방장만이 아니라 그 순간 방 안에 있는 전원과 대조한다**(D-20 — 멤버 SET을 `SMEMBERS`로 읽는다.
  목록에서만 숨기고 입장은 되면 의미가 없다. 내가 들어간 **뒤에** 나와 차단 관계인 사람이 따라 들어오는 것은 **그 사람의 입장권 발급 때** 방 안의 나와 대조해야 막힌다). `room`은 서명만 검증하고 이 앱에 묻지 않는다. **확정되거나 만료된 글에는 입장권을 내주지
  않는다** — 그래서 "확정 뒤에는 새 사람이 못 들어온다"와 "만료된 글은 못 들어간다"가 `room`이 글의 상태를 몰라도 지켜진다.
  확정 쪽은 `room`도 직접 지킨다 — 확정된 방에는 입장이 409 `ROOM_CONFIRMED`로 거절된다(D-21. 아래 "방장 확정").
- **입장권의 형식이 정해졌다**(`contracts/platform-api.md` "입장권" · P-3 — `room`의 계약 `room-api.md`에도 걸린다). **access 토큰과 같은 키로 서명한 JWT(RS256)**이고 `room`은 공개 키만 갖는다(§5.1 (가)).
  클레임은 `iss` · `iat` · `jti` · `sub`(입장하려는 사람) · **`token_use` = `room_ticket`** · `room_id`(글의 id) · `host_id`(글을 쓴 사람 — `room`은 방 만들기를 부른 사람이 이 값과 같은지 본다) · `exp`(발급 후 **60초** — `ROOM_TICKET_TTL`, 기본값 `PT60S`).
  요청은 `POST /api/v1/posts/{postId}/ticket`이고 방장도 같은 요청으로 받는다(`sub` = `host_id`). **차단 관계로 숨겨진 글은 없는 글과 똑같이 404다**(숨김을 상태보다 먼저 본다 — "모집이 끝났다"도 알려 주지 않는다). 모집 중이 아니면 409 `POST_NOT_RECRUITING`.
  **방 키를 못 읽으면 내주지 않는다**(503 `ROOM_STATE_UNAVAILABLE` — 차단 대조를 못 했다). **검증하는 쪽은 `token_use`를 반드시 본다** — 같은 키라서 안 보면 입장권을 access 토큰으로(또는 그 반대로) 쓸 수 있다(§5.1).
  수명이 짧아서 "입장권을 받은 뒤 들어오기 전"의 차단 경쟁(§7.1 미정)의 창도 60초로 줄어든다 — **없어진 것은 아니다.** 강퇴당한 사람의 재입장은 입장권으로 막지 않는다(`room` 쪽 미정 그대로).
- **방 키를 읽는다 — 이 앱이 남의 Redis 키를 만지는 유일한 예외다.** 목록을 만들 때 글마다 "방이 살아 있는가·몇 명인가·**누가 있는가**"를 조회하고(가득 찬 방
  표시, **방 안 사람들의 카드, 차단 대조** — 읽는 범위가 D-20으로 `SCARD`에서 **`SMEMBERS`까지** 늘었다), 방이 사라져 있으면 그 자리에서 글을 만료로 바꾼다.
  입장권을 내줄 때도 멤버 SET을 읽는다(위). 방장 확정 때도 같은 키에서 현재 인원을 읽어 파티원으로 기록한다. **쓰지는 않는다 — 예외가 없다.**
  **구현** — 글이 몇 개든 **파이프라인 한 번**으로 글마다 `EXISTS host` · `SMEMBERS members` · `EXISTS confirmed`를 읽는다(`party/room/RedisRoomStateReader`). 방장 키의 **값은 읽지 않는다**(확정한 방에서는 바뀔 수 있다 — 아래).
  **Redis를 못 읽으면** 목록 · 단건은 방 정보를 비운 채 글만 내려 주고 **만료 판정을 하지 않는다** — 못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다(`contracts/platform-api.md`).
  **목록 조회(GET)가 글을 만료 · 확정으로 바꾸고 `room_seen_at`을 적는다** — `room`이 알려 주지 않으니 이 앱이 방 키를 볼 때 스스로 옮겨 적는다. 전부 조건부 UPDATE라 멱등하다(`party/service/PostService`의 머리 주석이 "허용된 부수 효과"라고 적었다). **§5.1 (다)의 "상태를 바꾸는 GET을 만들지 않는다"와 어떻게 같이 서는지는 어느 문서도 말하지 않는다** — 사용자의 뜻으로 바뀌는 것이 아니라 `room`에서 읽은 사실을 옮겨 적는 것이고 누가 불러도 결과가 같다는 것이 구현한 쪽의 판단이다. **소유자가 검토할 것이다**(`START_HERE.md` §4).
- **방장 확정 — 확정 표시는 `room`이 쓰고 이 앱은 읽는다**(2026-09-20, D-21). 확정 요청(`POST /api/v1/rooms/{roomId}/confirm` — 방장만 · 2명 이상 · **되돌릴 수 없다**)은 **`room`이 받는다. 이 앱은 그 요청을
  받지 않는다.** `room`이 확정 표시 키 `qm:room:{roomId}:confirmed`를 쓰고 그 뒤로 새 사람의 입장을 409 `ROOM_CONFIRMED`로 거절한다. **이 앱은 확정 표시 키와 멤버 SET을 읽어 글의 상태를 "확정"으로 바꾸고
  파티원을 기록한다.** 확정한 그 순간 방에 있던 **전원**(방장 포함)이 파티원이다. 확정 순간의 경쟁(미리 받은 입장권으로 누가 들어오는 것)은 확정과 입장이 둘 다 `room`의 스크립트라서 거기서 막힌다 —
  D-16이 가능성으로 적었던 **"이 앱이 확정 직전에 '닫힘' 표시를 쓴다"는 받지 않았다**(`room`의 키는 `room`만 쓴다 — D-19와 같은 이유).
  **브라우저가 부르는 순서는 §7.2 (가)의 방향을 받았다**(`contracts/platform-api.md` "방장 확정의 기록" · P-6 — 검토만 했던 방향을 Claude가 받은 것이다). **길 ①** — 브라우저가 `room`의 확정이 성공한 뒤 이 앱의 `POST /api/v1/posts/{postId}/confirm`을 부른다.
  이 앱은 그 말을 믿지 않고 확정 표시 키와 멤버 SET을 읽어 검증한 뒤 기록한다(키가 없으면 409 `ROOM_NOT_CONFIRMED`). 읽은 것만 기록하므로 **부르는 사람은 로그인한 누구든 된다.** **길 ②** — 목록 · 단건 · 입장권에서 방 키를 읽다가 "DB에는 모집 중인데 확정 표시 키가 있는 글"을 보면 그 자리에서 같은 기록을 한다.
  **기록** — 글을 `CONFIRMED`로, `party.parties`(`source` = `BOARD` · **`post_id`가 글의 id(= `roomId`)**. `id`는 DB가 매기는 파티 자신의 번호다)와 `party.party_members`를 만든다.
  **멱등을 지키는 것은 `UNIQUE (post_id)`다**(2026-09-22 — PK가 `roomId`였던 때는 PK가 지켰다. 넣는 문장이 `INSERT … ON CONFLICT (post_id) DO NOTHING`이다 — `party/repository/PartyRecordRepository`) — 두 길이 동시에 와도 파티는 하나다. **읽는 시점의 멤버 SET은 확정한 그 순간과 다를 수 있다**(확정 뒤에 나간 사람이 빠진다) — **감수한다**(확정 직후에 ①이 오므로 창이 짧다).
  둘 다 빠지고 방이 사라지는 드문 경우의 구멍은 그대로 남아 있다(§7.2 (가) "남는 구멍").
- **방 키는 정해졌다.** 원본 상수는 `room`의 `redisKeys/RoomKeys.java`, 계약은 `../room/contracts/room-api.md` "Redis 키" 절이다(이 폴더의 사본은 `docs/ROOM_CONTRACT.md`).

  | 키 | 자료형 | 값 | 뜻 |
  |---|---|---|---|
  | `qm:room:{roomId}:host` | STRING | 방장의 `userId`(사용자 번호의 십진 문자열) | **이 키가 있다 = 방이 있다.** "방이 살아 있는가"는 이 키 하나를 `EXISTS` 하면 된다 |
  | `qm:room:{roomId}:members` | SET | 방에 있는 사람의 `userId`(방장 포함. 역시 숫자의 문자열이다 — **숫자로 팔 수 없는 값은 이 앱이 건너뛴다**, 아래) | `SCARD`가 현재 인원이다. 정원은 5. **`SMEMBERS`로 `userId` 목록을 받아 이 앱의 DB에서 프로필을 붙이고 차단을 대조한다**(D-20). `room`은 `userId`만 안다 — 닉네임·티어·포지션은 이 SET에 없다 |
  | `qm:room:{roomId}:confirmed` | STRING | 그 방의 `roomId` | **확정 표시 키. 이 키가 있다 = 방장이 확정한 방이다**(D-21). 방장 확정을 기록할 때 이 키와 멤버 SET을 읽는다. **이 앱은 쓰지 않는다** |
  | `qm:user:active-room:{userId}` | STRING | 들어가 있는 방의 `roomId` | 입장 표시 키(D-19). `matching`도 `EXISTS`로 본다. **이 앱은 이 키를 만지지 않는다**(§2) |

  - 모든 키가 수명 600초(`room`의 `ROOM_TTL_SECONDS`)이고 브라우저가 1분마다 `room`에 보내는 접속 확인(`POST …/heartbeat`)이 늘린다. **확정하지 않은 방의 수명(방장 키 · 멤버 SET)은 방장의
    신호만 늘린다** — 방장이 명시적으로 나가든 말없이 사라지든(연결 끊김) 방장 키가 없어진다. 방장이 나가면 방에 다른 사람이 있어도 방을 통째로 없앤다(D-21).
    그래서 **방장 키가 없으면 방이 없어진 것**이다. 대가 — 방장이 말없이 사라진 방은 최대 10분 살아 있는 것처럼 보인다.
  - **확정한 방은 방장이 나가도 방이 이어진다(2026-09-21, D-23 — D-21의 "확정 뒤에도 방을 통째로 없앤다"를 고쳤다).** 남은 멤버 가운데 한 명이 방장을 넘겨받는다 — 방장이 나가기를 부르면 그 자리에서,
    말없이 사라지면 방장 키가 만료된 뒤 처음 접속 확인을 보낸 멤버가(확정한 방에 한해 일반 멤버의 접속 확인도 멤버 SET · 확정 표시 키의 수명을 늘린다). 넘겨받을 사람이 없을 때만 방장 키 · 멤버 SET · 확정 표시 키가 함께 없어진다.
    **방 키 약속의 형식(이름 · 자료형 · 값의 뜻)과 "방장 키가 있다 = 방이 있다"는 그대로다. 이 앱이 알아야 하는 것은 하나다 — 확정한 방에서는 방장 키의 값이 바뀔 수 있다.**
    그래서 **확정된 글을 "방장이 나갔으니 만료"로 다루면 안 된다** — 방장 키가 **없어졌을 때**가 방이 없어진 것이다(`docs/ROOM_CONTRACT.md`).
    **단, 확정한 방은 방장 키만 잠깐 없을 수 있다**(D-23 "감수하는 것" — 방장이 말없이 사라지면 방장 키가 만료된 때부터 다음 멤버의 접속 확인까지, 늦어도 1분. 멤버 SET · 확정 표시 키는 있다).
    **확정된 글을 "방장 키가 없다" 하나로 곧바로 만료시키면 안 된다** — **확정된 글은 방장 키가 없어도 만료시키지 않는다. 끝까지 `CONFIRMED`다**(`contracts/platform-api.md` "모집 글 · 목록 · 입장권" · P-5 — 가를 필요가 없게 정했다. 만료 검사는 모집 중인 글에만 한다).
    모집 중인 글도 **확정 표시 키가 있으면 방장 키가 없어도** 만료가 아니라 확정으로 기록한다(길 ②).
  - 멤버 SET에는 말없이 사라진 사람의 이름이 잠깐 남을 수 있다(방장의 접속 확인이 뺀다) — **이 앱이 읽는 인원수는 길어야 수명만큼 부풀 수 있다.**
    카드에도 그 사람이 남고, **그 사람과 차단 관계인 사용자에게는 이미 없는 사람 때문에 방이 잠깐 숨겨질 수 있다**(D-20 "감수하는 것").
  - **`room`은 방이 없어져도 이 앱에 알리지 않는다**(서비스 간 호출도 큐도 없다 — D-16 그대로). 이 앱이 목록을 그릴 때와 입장권을 내줄 때 방장 키를 보고 스스로
    글을 만료시킨다. `room` 쪽에도 안전망이 있다 — 없는 방에는 입장이 404 `ROOM_NOT_FOUND`로 거절된다.
  - **방은 `room`의 "방 만들기" 요청(`POST /api/v1/rooms/{roomId}`)이 만든다.** 부른 사람이 방장이고 곧바로 들어와 있다. **입장은 방을 만들지 않는다.** 그래서
    **글을 쓴 직후, 방 만들기를 부르기 전에는 방장 키가 없다** — "방장 키가 없다 → 글을 만료로"를 그대로 적용하면 방금 쓴 글을 만료시킨다.
    **가르는 법 — `room_seen_at`**(`contracts/platform-api.md` · P-5). 방장 키를 **처음 본 순간** 글에 `room_seen_at`을 적는다. **`room_seen_at`이 있는데 방장 키가 없으면** 방이 사라진 것이다 → 만료.
    `room_seen_at`이 없는 글(방 만들기를 아직 안 부른 글)은 **쓴 지 10분(`room`의 방 수명과 같다 — `platform.board.room-grace`)이 지나도록 방이 안 생겼을 때만** 만료시킨다. 방이 아직 없는 글의 차단은 방장과의 사이를 본다.
- **이 읽기는 교과서대로면 "DB 공유로 통합하기"다 — 알고 감수한다**(D-20). **읽기 전용·키 넷(D-20 때는 셋이었고 확정 표시 키가 더해졌다 — D-21)·쓰는 주인 하나(`room`)**로 좁혀 두었다(이 앱의 코드가 실제로 읽는 것은 위 표의 앞 **셋**이다 — 입장 표시 키는 읽지도 않아 `party/room/RoomKeys`에 그 이름이 없다). 검토하고 버린 대안 —
  ① 이 앱이 `room`의 API를 호출한다(동기 호출이 생겨 한쪽이 죽으면 목록도 죽고 docs/11 #15가 깨진다. 글 N개에 호출 N번) ② `room`이 이벤트를 보내고 이 앱이 사본을
  유지한다(가장 정석이지만 놓치면 사본이 영원히 틀려 내구성 있는 큐(SQS)와 재처리·주기적 맞추기가 필요하다 — 금방 사라지는 정보에는 과하다) ③ 프런트가 이 앱과 `room`을
  따로 불러 조합한다(차단 관계를 서버에서 거를 수 없다). **나중에 ②로 옮길 길은 열려 있다** — `room`이 이미 인원이 바뀔 때마다 알림을 발행한다.
  **`room`의 방 안 사람 목록 API(`GET …/members`)는 방 안의 사람만 볼 수 있다**(403 `NOT_IN_ROOM`) — 방 밖에서 방 안을 보는 공개 창구는 **이 앱의 목록**이다. 차단을 거르는 곳이 여기라서다.
  **그 API를 이 앱이 부르지 마라.**
- **방 키의 이름·구조는 두 앱의 약속이다.** 알림 채널 접두사(§3.2)와 같은 위험이다 — 어긋나면 테스트가 통과한 채로 목록이 조용히 틀린다.
  접두사는 **상수 한 곳에만** 두고 **원본이 `room`의 `RoomKeys`임을 주석에 적는다**(그렇게 돼 있다 — `party/room/RoomKeys.java`). 바꿀 때는 `room`과 같이 바꾼다.
- **`room`은 지금도 입장권 없이 돈다.** 이 앱이 없던 때에 인증·입장권·방장 확인을 임시 처리(`TEMP-NO-PLATFORM`)로 비워 뒀고 **그대로다** — 누구나 아무 `roomId`로 방을 만들고 방장이 된다.
  **이제 이 앱이 줄 것은 다 있다**(access 토큰 · 입장권 · 공개 키 — `START_HERE.md` §2). **채우는 것은 `room` 폴더의 일이다** — 자리의 목록은 `../room/START_HERE.md` §2다(access 토큰의 사용자, 입장권 서명 검증, 글의 상태·차단 확인).
  그동안 멤버 SET에 아무 문자열이나 들어올 수 있다. 가르는 기준은 **사용자 번호로 팔 수 있는가**다(2026-09-22 — `contracts/platform-api.md`) — **숫자가 아닌 값은 방 키를 읽는 자리에서 건너뛴다**(WARN 한 줄. 카드에도 파티원에도 들지 않고 `memberCount`에서도 빠진다 — `party/room/RedisRoomStateReader`).
  **숫자이지만 가입하지 않은 번호는 빼지 않는다** — 카드를 `nickname: null` · `profile: null`로 내보내고(`memberCount`와 어긋나지 않게) 파티원으로도 기록한다.

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
- **이 절은 이 앱에 하나도 구현되지 않았다(2026-09-21)** — AWS SDK가 없고(`backend/build.gradle`에 넣을 자리만 주석으로 남겼다), `party.outbox` · `social.outbox` 테이블도 만들지 않았다(`party/V5__party_posts.sql`의 머리 주석).
  `party.parties`의 `source`에 `MATCH`의 자리만 있다. 그래서 **`social.recent_players`를 채우는 주체가 없다.**

### 3.5 DB — 스키마 소유와 `social.blocks`

- PostgreSQL 인스턴스 1개에 **schema-per-service**다. **크로스 스키마 FK·JOIN 금지.** Flyway 마이그레이션은
  `db/migration/<schema>/`로 나눈다 (docs/11 #17). 이 앱이 소유하는 스키마(docs/WHY_POSTGRESQL §3이 인용한 원본 docs/06 배치):
  `account`(users, credentials/refresh_tokens, game_accounts) · `party`(parties, party_members, outbox) ·
  `social`(friend_requests, friendships, blocks, reports, recent_players, outbox). **컬럼은 `matching` 쪽 문서에 없다 — 이 폴더에서 정했고, 원본은 마이그레이션이다**(아래).
  `reservation` 스키마는 **이 앱이 소유하지 않는다**(D-15 — `app:reservation`의 것). 그 마이그레이션을 누가 실행하는지는 미정이다(§7).
- **스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정).** 앱 하나가 롤 하나로 붙는다. `matching`은 별도 롤 없이 `social.blocks`를 읽는다 — `qm_matching` 롤도 `GRANT`도 없다.
  스키마 분리 · 크로스 스키마 FK/JOIN 금지는 그대로다. **docs/11 #17의 "스키마별 DB 롤" 대목과 D-1의 GRANT(`matching` 롤에 `social.blocks`의 SELECT)를 개정하는 것이다 — `matching` 폴더에서 D-항목으로 남겨야 한다(아직 안 남겼다).**
  뷰(`shared_read.blocked_pairs`)는 만들지 않는다. **`matching`이 읽는 이 앱의 테이블은 `social.blocks` 하나다 — 늘리지 않는다**(권한이 아니라 약속으로 지킨다).
- **`matching`이 이미 이 모양으로 읽고 있다** (`block/Block.java` · docs/11 D-4) — 바꾸면 `matching`이 런타임에 깨진다:
  `social.blocks(id bigint identity PK — 채번은 DB, blocker_id bigint, blocked_id bigint)`. 방향이 있는 한 줄이고
  `matching`은 양방향으로 조회한다. **`(blocker_id, blocked_id)` UNIQUE는 이 앱이 건다.**
  **두 칸이 `varchar(20)`에서 `bigint`가 됐다(2026-09-22 소유자 결정 — 아래).** 그래서 지금은 반대다 — **`matching`의 `Block.java`가 아직 `String`이라 그쪽을 `Long`으로 바꿔야 한다**(아직 안 바꿨다. `matching` 폴더의 일이다).
- **모든 테이블의 PK는 `bigint GENERATED ALWAYS AS IDENTITY`이고, 사용자의 식별자는 둘로 갈린다**(2026-09-22 **소유자 결정**. Spring에서는 `@GeneratedValue(strategy = GenerationType.IDENTITY)`다).
  **2026-09-19의 "사용자 id는 가입할 때 정한 로그인 아이디(문자열)다"를 개정하는 것이다 — docs/11 D-4와 얽힌다.** 원본은 마이그레이션과 `contracts/platform-api.md`(P-11)다.
  - **`account.users.id`(bigint)가 `userId`다** — JWT의 `sub`(숫자를 문자열로 — `"42"`), 알림 채널 `qm:pubsub:push:{userId}`, 입장권의 `sub` · `host_id`, URL의 `{userId}`, 요청·응답 본문의 `userId`, 다른 스키마의 `*_id` 컬럼이 전부 이것이다.
  - **`account.users.login_id`(`varchar(20) NOT NULL UNIQUE` · CHECK `^[a-z0-9_]{4,20}$`)는 로그인할 때만 쓴다.** 가입·로그인 본문의 필드 이름은 **`loginId`**이고 응답은 `{userId, loginId, nickname}`이다. 중복은 **409 `LOGIN_ID_TAKEN`**(옛 `USER_ID_TAKEN`이 이 이름이 됐다).
    **바꾸는 API는 아직 없다**(엔티티도 `updatable = false`다). 다만 **이제는 이론상 바꿀 수 있다** — 채널 이름·URL·토큰에 로그인 아이디가 박혀 있지 않다. 옛 결정의 "바꿀 수 없는 값으로 다룬다"가 풀린 것이 **이 결정의 이득이다.**
  - **로그인 실패 제한의 Redis 키만 로그인 아이디 기준이다**(`qm:auth:login-fail:{loginId}` · `qm:auth:login-lock:{loginId}`) — 사용자를 찾기 **전에** 세야 하고 없는 아이디도 세기 때문이다.
  - **옛 결정의 근거였던 "`matching`이 이미 `String`으로 다룬다"(docs/11 D-4)는 이제 고쳐야 할 것이 됐다** — `social.blocks.blocker_id/blocked_id`가 bigint가 됐으므로 **`matching`의 `block/Block.java`를 `Long`으로 바꿔야 한다. 아직 안 바꿨고, 그 폴더의 일이다**(바꾸기 전까지 `matching`은 그 테이블을 읽다가 런타임에 깨진다). 여기서 고치지 마라(§9).
    **`matching` · `notification` · `room`이 받는 `userId` 파라미터와 Redis 채널·방 키·멤버 SET은 문자열을 그대로 다루므로 `"42"`가 들어가도 그쪽 코드 변경이 없다.**
  - docs/WHY_POSTGRESQL의 `uuid` 서술은 여전히 이 앱의 모양이 아니다 — 이제는 bigint다.
  - **소셜 로그인도 그대로다**(2026-09-21) — 소셜로 처음 온 사람이 **로그인 아이디**를 정하고, 제공자의 회원 번호는 `account.social_identities`에만 있어 사용자 번호가 되지 않는다.
- **테이블과 컬럼의 원본은 `backend/src/main/resources/db/migration/`이다**(2026-09-21 — `contracts/platform-api.md` 머리. Claude가 정했고 소유자가 검토하지 않았다). 그림은 ERD(§10)에 있다.

  | 파일 | 만드는 것 |
  |---|---|
  | `V1__baseline.sql` | 주석뿐이다 |
  | `account/V2__account_schema.sql` | `account.users`(`id` = **사용자 번호**(bigint identity) · `login_id` UNIQUE + 형식 CHECK · `nickname` UNIQUE) · `account.credentials`(PK `user_id` · 비밀번호 해시 — `{bcrypt}` 접두사) · `account.game_accounts`(`id` identity · `UNIQUE (user_id, game)`) |
  | `account/V3__game_profile_and_social_login.sql` | `game_accounts`에 `external_id` · `verified` · `server`(PUBG만) · `account.game_account_stats`(전적 스냅숏 — 게임 계정과 1:1. **세 게임이 한 테이블을 쓰고 판 수 `games`만 공통 컬럼이다** — `wins` · `losses` · `win_streak` · `avg_assists`는 PUBG에 없어 비는 칸이고 `(wins IS NULL) = (losses IS NULL)`을 CHECK가 지킨다. 소유자 결정 2026-09-22 · P-12. 게임마다 다른 지표는 `detail` jsonb) · `account.social_identities`(PK `(provider, provider_user_id)`) |
  | `social/V4__social_blocks.sql` | `social.blocks`(롤 · GRANT는 없다 — 위) |
  | `party/V5__party_posts.sql` | `party.recruit_posts`(`id`(bigint identity) = `roomId` · `room_seen_at` · "모집 중인 글은 한 사람에 하나"의 부분 UNIQUE 인덱스) · `party.recruit_post_positions` · `party.parties`(`id`는 파티 자신의 번호이고 **`post_id`에 `UNIQUE`** — 그것이 방장 확정 기록의 멱등을 지킨다) · `party.party_members` |
  | `social/V6__friends_reports_recent_players.sql` | `social.friend_requests`(같은 방향의 대기 중 요청은 하나 — 부분 UNIQUE 인덱스 `WHERE status = 'PENDING'`) · `social.friendships`(PK `(user_low_id, user_high_id)`로 정규화한 한 줄 — **숫자라 `COLLATE "C"`가 필요 없어졌다**) · `social.reports`(UNIQUE 없음 — 여러 번 신고할 수 있다) · `social.recent_players`(PK `(user_id, other_user_id)` — **채우는 코드가 없다**) |

  - **버전 번호는 스키마 폴더 사이에서 하나의 순서다** — Flyway가 `classpath:db/migration`을 하위 폴더까지 한 번에 훑기 때문이다(`application.yaml`). **이미 적용된 파일은 고치지 않는다** — 체크섬이 달라져 기동이 막힌다. 바꿀 것은 새 버전으로 쓴다.
  - **제약에 전부 이름을 붙였다** — 앱이 제약 위반을 그 이름으로 갈라 에러 코드로 옮긴다(`users_login_id_key` → `LOGIN_ID_TAKEN` · `users_nickname_key` → `NICKNAME_TAKEN` 등 — `common/error/ConstraintViolations`). 이름을 바꾸면 앱의 상수도 같이 바꾼다. §5 "불변식은 DB가 강제한다"가 이렇게 지켜진다.
  - **`refresh_tokens` 테이블은 만들지 않았다** — refresh는 Redis에 둔다(D-14 · §5.1 (마)). `account.users` 밖의 스키마는 사용자 id에 FK를 걸지 않는다(크로스 스키마 FK 금지 — 있는 사용자인지는 앱이 `account`의 창구 `UserReader`로 확인한다).
  - **롤을 만들거나 `GRANT`하는 마이그레이션은 없다** — 스키마별 DB 롤을 두지 않기 때문이다(위). 이 앱이 붙는 DB 계정은 환경변수(`DB_USER` · `DB_PASSWORD`)로 받는다 — 운영의 계정은 미정이다(§7 "운영의 DB 롤").
- 옆 폴더 `matching/db-design/`은 **다른 설계의 흔적이다**(LoL 전용·Discord 로그인·차단 제외). 근거로 쓰지 마라.

## 4. 기술 스택 (결정됨)

| 항목 | 값 |
|---|---|
| 언어 | **Java 21** (`matching/backend/build.gradle`의 `JavaLanguageVersion.of(21)`) |
| 프레임워크 | **Spring Boot 4.1.1** (MVC, 서블릿) — `matching`·`notification`·`room`과 같은 버전. 한 사람이 네 서비스를 같이 다루므로 의존성·설정 감각을 한 벌로 유지한다 |
| 빌드 | Gradle (`io.spring.dependency-management` 1.1.7), **단일 모듈**, 앱은 `backend/` 아래 |
| 저장소 | **PostgreSQL** (docs/11 #4, 근거 docs/WHY_POSTGRESQL.md) + Flyway. Redis는 알림 발행(§3.2)·refresh 토큰(키 `qm:auth:refresh:{uuid}` — 접두사 `qm:auth:*`, §5.1 (마). refresh를 도입할 때부터 쓴다)·`room`의 방 키 읽기(§3.3)·**로그인 실패 제한**(`qm:auth:login-fail:{loginId}` · `qm:auth:login-lock:{loginId}` — **여기만 로그인 아이디로 센다**, §3.5 · `contracts/platform-api.md` "계정") — 그 밖의 용도는 §7 |
| 인증 | **Spring Security `oauth2-resource-server`(Nimbus)** — RS256. `NimbusJwtEncoder`로 서명한다. jjwt 등을 따로 들이지 않는다(§5.1 (가)). **들어 있다(2026-09-21)** — Boot 4의 스타터 이름은 `spring-boot-starter-security-oauth2-resource-server`다. **소셜 로그인에 Spring의 `oauth2-client`는 쓰지 않는다** — 기본값이 인가 요청을 HTTP 세션에 넣는다(§5 "stateless"). 인가 코드 흐름을 `RestClient`로 직접 짰다(`contracts/platform-api.md` "소셜 로그인") |
| 기본 포트 | **8082 (확정 — 2026-09-21)** — `matching` 8080, `notification` 8081(`room`은 8083)과 로컬에서 같이 띄우기 위해. `backend/`의 `application.yaml`이 이 값을 기본값으로 쓴다(`SERVER_PORT`). 소셜 로그인의 Redirect URI 기본값(`OAUTH_REDIRECT_BASE_URL` = `http://localhost:8082`)도 이 값에 묶여 있다 |
| 패키지 | **도메인(= DB 스키마)을 먼저 나눈다** — `common` · `account` · `social` · `party`. 안에서 `controller` · `service` · `domain` · `repository` · `dto`로 나눈다. **도메인 사이는 "읽는 창구"로만 잇는다**(`account`의 `UserReader` · `GameProfileReader`, `social`의 `BlockReader`) — 남의 리포지토리를 직접 쓰거나 남의 스키마를 JOIN하지 않는다(`backend/…/platform/package-info.java`. Claude가 정했다) |

## 5. 설계 규칙

- **stateless다.** 프로세스 로컬 상태(메모리 세션, 로컬 캐시에 의존한 판정)를 두지 않는다. 설정은 환경변수 +
  기본값으로 받고, `/health/live`·`/health/ready`, SIGTERM graceful shutdown, stdout JSON 로그를 지킨다 (docs/11 #15·#20).
  **readiness에 `db`를 넣었다 — Redis는 넣지 않았다**(Redis가 죽어도 로그인은 된다. `application.yaml`의 주석). **JSON 로그는 기본값으로 켜지 않았다** — 운영에서 환경변수 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`로 켠다(형식이 `ecs`여야 하는지는 정해진 적이 없다 — §7).
- **경계를 넘는 동기 호출을 새로 만들지 않는다.** 다른 앱에 시킬 일은 outbox → SQS, 사용자에게 알릴 일은
  Pub/Sub이다. 둘을 섞지 마라 — 놓치면 데이터가 어긋나는 것이 SQS, 놓쳐도 조회로 복구되는 것이 알림이다 (docs/14 §7).
- **불변식은 DB가 강제한다.** `조회 → 애플리케이션 판단 → 삽입`으로 지키지 마라. "같은 사람 두 번 차단
  금지"는 UNIQUE다 — **중복 가입 · 중복 차단 · "모집 중인 글은 한 사람에 하나" · 파티 기록의 멱등이 전부 이렇게 구현됐다**(INSERT의 제약 위반을 409로 옮긴다 — §3.5). 그것이 정상 경로라서 Hibernate의 제약 위반 WARN 로거(`org.hibernate.orm.jdbc.error`)를 껐다(`application.yaml`의 주석) (docs/WHY_POSTGRESQL §1. 같은 절의 INV-9 exclusion constraint는 이제 `app:reservation`의 일이다 — D-15).
  **H2는 PostgreSQL의 제약(부분 UNIQUE 인덱스 등)을 그대로 재현하지 못하고**(docs/11 D-3) exclusion constraint도 없다 — 이 검증은 PostgreSQL에서만 의미가 있다.
- **인증 — access 토큰은 JWT이고 쿠키로 주고받는다**(`Authorization` 헤더에 싣지 않는다). **refresh 토큰은 JWT가 아니라 불투명 UUID이고
  Redis에 저장한다**(UUID → 사용자. 폐기는 지우면 끝) (docs/11 #16 · D-14). **rotation 필수** — 재발급 때 옛 UUID를 지우고 새 UUID를 준다.
  **세부는 정해졌다(2026-09-21) — §5.1.** 서명은 RS256, CSRF는 `SameSite=Lax` + `Origin` 검사, **access 만으로 시작**하고 **access denylist는 두지 않는다.**
- **Spring Security가 들어 있다(2026-09-21)** — `oauth2-resource-server`(Nimbus)다(§5.1 (가)). 세션을 만들지 않고(`STATELESS`) Spring의 CSRF 필터는 끈다 — CSRF는 `Origin` 검사 필터가 맡는다(§5.1 (다)).
  **인증이 필요 없는 요청은 `/api/v1/auth/**` · `/health/**` · `/info`뿐이다** — 로그인하지 않은 채 모르는 경로를 부르면 404가 아니라 401 `UNAUTHENTICATED`다(`contracts/platform-api.md` "공통").

### 5.1 인증 세부 (2026-09-21 소유자 확정 · **구현됐다.** **docs/11에는 아직 없다**)

> **결정 로그에 올려야 한다.** 아래는 시스템 전체에 걸리는 결정이다 — 특히 (가) RS256, (다) CSRF, (라)의 **denylist를 두지 않는 것(= docs/11 #16 개정)**.
> `matching` 폴더에서 docs/11에 D-항목으로 남겨야 한다 — **아직 안 남겼다(2026-09-21).** 그 파일은 여기서 고치지 않는다(§9). D-번호가 없으므로 출처는 "§5.1"로 적는다.
> **같은 처지의 결정이 셋 더 있다**(전부 소유자 결정이고 D-항목이 없다. ③은 2026-09-23, 나머지는 2026-09-22다) — ① **스키마별 DB 롤을 두지 않는 것**(§3.5. docs/11 #17의 "스키마별 DB 롤" 대목과 D-1의 GRANT를 개정한다)
> ② **모든 PK를 `bigint identity`로 하고 `userId`(사용자 번호)와 `loginId`를 가른 것**(§3.5. **docs/11 D-4와 얽히고 2026-09-19의 결정을 개정한다.** `sub`가 숫자 문자열이 되므로 검증하는 세 서비스에도 걸린다 — 아래 (가)).
> ③ **자동 매칭이 조건 맞는 게시판 방에 먼저 합류하는 길을 둔다**(§7 그 행 — 두 경로를 다 두고 대기열 매칭은 살린다. `matching` · `room` 과 D-19 에 걸린다). **방향만 정해졌고 세부는 미정이다.**
> `matching` · `notification` · `room`에 걸리는 것(검증 · `Origin` 검사 · 전환)의 **적용은 각 폴더의 일이다.**

**전제 — 브라우저가 보기에 네 서비스는 같은 출처다.** 운영은 CloudFront 한 도메인 아래에서 `/api/**` · `/events`를 ALB가 경로로 나눠 각 서비스로 보낸다(docs/AWS_ARCHITECTURE의 연결 표).
(나) · (다) · (사)가 전부 이 전제에 서 있다.

**(가) 서명 방식 — RS256.**
- **서명(개인 키)은 이 앱만 한다.** `matching` · `notification` · `room`은 **공개 키로 검증만** 한다. HS256이면 비밀 키를 네 서비스가 다 가져, 어느 하나만 뚫려도 아무 사용자의 토큰을 만들 수 있다.
- **키 전달** — 공개 키 PEM을 각 서비스의 **환경변수**로 넣는다(운영은 Secrets Manager). **JWKS 엔드포인트는 두지 않는다** — 서비스 간 동기 호출이 생긴다(위 "경계를 넘는 동기 호출을 새로 만들지 않는다").
  나중의 키 교체를 위해 JWT 헤더에 **`kid`**를 넣는다.
- **라이브러리** — Spring Security의 `oauth2-resource-server`(Nimbus). 이 앱은 `NimbusJwtEncoder`로 서명하고, 검증하는 서비스는 `NimbusJwtDecoder.withPublicKey()` +
  **쿠키에서 토큰을 꺼내는 `BearerTokenResolver`**를 쓴다. jjwt 등을 따로 들이지 않는다.
- **클레임** — `sub`(**사용자 번호를 십진 문자열로** — `"42"`. 로그인 아이디가 아니다, 2026-09-22 소유자 결정 · §3.5) · `iss` · `iat` · `exp` · `jti` **만**(여기에 쓰임새를 가르는 `token_use`가 더해졌다 — 아래). 닉네임처럼 바뀌는 값은 넣지 않는다. `jti`는 나중에 denylist를 두게 될 때 쓸 자리다((라) — 지금은 두지 않는다).
- **클레임이 하나 늘었다 — `token_use`**(2026-09-21 소유자 확정). 값은 access 토큰이 `access`, 입장권이 `room_ticket`, 소셜 가입 대기 토큰이 `social_signup`이다(원본 상수는 `common/security/TokenClaims`). **같은 키로 서명하기 때문에 검증하는 쪽은 서명 · `iss` · `exp`에 더해 `token_use`가 기대한 값인지 반드시 본다** —
  안 보면 입장권을 access 토큰으로(또는 그 반대로) 쓸 수 있다. JOSE 헤더의 `typ`을 쓰지 않은 이유 — Spring Security의 기본 디코더가 `typ`이 `JWT`가 아니면 거절해서 검증하는 세 서비스가 전부 설정을 바꿔야 한다(`contracts/platform-api.md` "access 토큰").
- **검증하는 쪽은 `sub`가 사용자 번호(숫자 문자열)인지도 본다** — `^[0-9]{1,19}$`(원본 상수 `common/security/TokenClaims.SUBJECT_PATTERN`. 이 앱은 `JwtConfig#jwtDecoder`에서 한다). 아니면 컨트롤러에 닿기 전에 401이다. 옆 서비스가 붙일 검증도 같은 모양이다(이 검사 자체는 Claude가 정했다).
- **입장권도 같은 키 · 같은 비대칭 서명이다** — `room`은 공개 키만 갖는다. 형식은 §3.3 · `contracts/platform-api.md` "입장권"(그 형식은 Claude가 정했다 — P-3). **`sub` · `room_id` · `host_id`는 전부 숫자를 문자열로 찍는다.**

**(나) 쿠키 속성.**
- `HttpOnly` 켠다. `Secure`는 **환경변수로 켜고 끈다**(운영은 켠다). **`SameSite=Lax`** — `Strict`면 외부 링크로 들어온 첫 화면이 로그아웃 상태로 보인다. `Path=/`.
- **`Domain`은 지정하지 않는다**(host-only). 한 도메인이라 충분하고, 로컬에서는 쿠키가 포트를 가리지 않아 8080~8083에 전부 간다.
- access 쿠키의 수명은 **토큰 수명과 같다.** refresh 쿠키는 도입할 때 `Path`를 **재발급 경로로 좁힌다.**

**(다) CSRF — `SameSite=Lax` + `Origin` 헤더 검사.**
- **POST/PUT/PATCH/DELETE에서 `Origin` 헤더를 검사한다.** `SameSite`만으로는 모자라다 — 출처가 아니라 **사이트 단위**라 서브도메인을 못 막는다.
- **CSRF 토큰은 쓰지 않는다** — stateless인 네 서비스가 각자 발급 · 검증해야 하고 프런트도 매번 실어야 한다. `Origin` 검사는 필터 하나라 네 서비스에 똑같이 들어간다.
- **전제 — 상태를 바꾸는 GET을 만들지 않는다.** 이 전제가 깨지면 `SameSite=Lax`도 `Origin` 검사도 그 요청을 막지 못한다.
- 다른 세 서비스에도 걸리는 결정이다(시그널 `POST` · 방 입장 · 매칭 요청) — 그쪽 적용은 그 폴더의 일이다.

**(라) 수명과 denylist — access 만으로 시작한다.**
- **access 만으로 시작한다**(`README.md` "미뤄도 되는 것" 그대로). access 수명은 **환경변수**로 받고, refresh가 없는 동안의 개발 기본값은 **길게(24시간쯤)** 둔다 —
  **임시 처리다. 임시 처리로 표시한다**(`START_HERE.md` §2 끝 — 표식 문구를 담은 주석). refresh를 붙이면 **access 15분 · refresh 14일**로 줄인다. **배포 전에는 refresh가 있어야 한다.**
- **access denylist는 두지 않는다 — docs/11 #16("Redis denylist, 조회 실패 시 fail-closed")을 개정하는 것이다.** 두면 네 서비스의 모든 요청이 Redis를 조회하고, fail-closed라
  Redis가 죽으면 전부 401이 되며, JWT를 스스로 검증하는 이점이 사라진다. **로그아웃은 refresh 삭제 + 쿠키 제거**이고 **남는 최대 15분은 감수한다.**

**(마) refresh의 Redis 키 (도입할 때).**
- 키 **`qm:auth:refresh:{uuid}`** → 값 `userId`. 접두사 `qm:auth:*`는 매칭의 `qm:user:*`와 겹치지 않는다. 접두사는 **상수 한 곳에만** 둔다.
- rotation은 **`GETDEL` 한 번**으로 원자적으로 한다(`조회 → 판단 → 삭제`가 아니다). 기기 수는 제한하지 않는다(토큰마다 키 하나).
- 옛 값을 다시 쓰면 **그냥 401이다** — 탈취 감지(토큰 계보 추적)는 넣지 않는다.

**(바) SSE와 토큰 만료.** `notification`은 **연결할 때만** 검증한다. 이미 열린 연결은 토큰이 만료돼도 끊지 않는다. 재접속이 401로 멈추면(`EventSource`는 200이 아닌 응답에 재접속을 멈춘다)
프런트가 `onerror`에서 `readyState === CLOSED`를 보고 **재발급한 뒤 `EventSource`를 새로 만든다.** 서버 쪽 장치는 두지 않는다.

**(사) 로컬 CORS — 서비스에 CORS 설정을 넣지 않는다.** **프런트 개발 서버의 프록시**가 경로별로 8080~8083에 나눠 보낸다 — 운영이 같은 출처라서다. (다)의 `Origin` 검사도 로컬과 운영이 같은 모양이 된다.
프런트가 이 컴퓨터에 없어 개발 서버가 무엇인지는 모른다.

**(아) 임시 식별(`?userId=`)에서의 전환.** 1단계가 끝나 **로그인이 도는 것을 본 뒤**, 서비스별로 따로 옮긴다. 순서는 **`room` → `notification` → `matching`.**
각 서비스에 "쿠키가 없으면 `userId` 파라미터를 받는" 개발용 스위치를 잠깐 남겨도 된다 — **임시 처리로 표시하고 운영에서는 끈다.** 바꾸는 작업은 각 폴더에서 한다(§9).

**이 절의 "남은 것"은 정해졌다(2026-09-21 소유자 확정. 원본은 `contracts/platform-api.md` "공통" · "access 토큰" · P-2 — docs/11에도 같이 올려야 한다).**

| 남아 있던 것 | 정해진 값 |
|---|---|
| 쿠키 이름 | **`qm_access`** |
| `iss` 값 | **`queuemate-platform`** |
| RSA 키 길이 | **2048** |
| 키를 담는 환경변수 | 개인 키 **`JWT_PRIVATE_KEY`**(PKCS#8 PEM — 이 앱만) · 공개 키 **`JWT_PUBLIC_KEY`**(X.509 PEM — 옆 서비스도 받는다). 하나만 주면 기동하지 않는다 |
| `kid` 값을 매기는 법 | 환경변수 **`JWT_KEY_ID`**(기본값 `dev-1`). 키를 바꿀 때 값을 올린다 |
| `Origin` 허용 목록을 받는 설정 | **`ALLOWED_ORIGINS`**(쉼표로 구분. 기본값 `http://localhost:5173,http://localhost:3000`). 허용 목록에 없으면 403 `ORIGIN_NOT_ALLOWED`. **`Origin`이 없는 요청(curl · 서버 사이)은 통과한다** |
| access 수명 개발 기본값 | **`ACCESS_TOKEN_TTL`** 기본값 **`PT24H`** — 코드에 **`TEMP-NO-REFRESH`** 표식을 달았다(`grep -rn "TEMP-NO-REFRESH" backend/src`). refresh를 붙이면 `PT15M` |
| 입장권과 access 토큰을 가르는 법 | **`token_use` 클레임**((가)) |
| (같이 정해진 것) `Secure`를 켜고 끄는 환경변수 | **`COOKIE_SECURE`**(기본값 `false`, 운영은 `true`) |

- **개발용 키.** `JWT_PRIVATE_KEY` · `JWT_PUBLIC_KEY`가 **둘 다 비어 있으면** 개발용 키를 **`backend/.dev-keys/`**에 만들어 다시 쓴다(경고 로그를 남긴다. `.gitignore`에 있다 — **개인 키가 들어 있으니 절대 올리지 않는다**).
  **옆 서비스는 그 폴더의 `public.pem`으로 검증한다.** 운영에서는 반드시 둘 다 넣는다(Secrets Manager). 테스트는 임시 폴더를 쓴다.
- **"상태를 바꾸는 GET을 만들지 않는다"의 예외가 하나 생겼다** — 소셜 로그인의 콜백이다. OAuth가 GET을 강제한다. `state` 검증이 그 자리를 지킨다(`contracts/platform-api.md` "소셜 로그인"). 목록 조회의 옮겨 적기는 §3.3.
- **로그인 실패 제한**(소유자가 사례를 찾아 정하라고 맡겼다 — OWASP Authentication Cheat Sheet · NIST SP 800-63B). 세는 단위는 **계정**이고 **세는 열쇠는 로그인 아이디다**(사용자를 찾기 전에 세야 한다 — §3.5). **15분 안에 5번 틀리면 잠그고, 그 뒤로 틀릴 때마다 잠금이 두 배**(1분 → … → 최대 15분). 잠긴 동안은 429 `TOO_MANY_LOGIN_ATTEMPTS` + `Retry-After`.
  **영구 잠금은 없다.** 없는 아이디에도 똑같이 센다. **Redis가 죽으면 제한 없이 통과시킨다.** IP 단위의 제한은 앞단(CloudFront/WAF)의 일로 둔다(`contracts/platform-api.md` "계정" · P-10).
- **아직 없는 것 — refresh 토큰.** (라) · (마)는 도입할 때의 설계 그대로이고 **코드가 없다.** access 24시간이 임시로 돈다. **배포 전에 있어야 한다.** 도입할 때 물을 것은 `START_HERE.md` §4.
- **옆 서비스의 전환((아))은 아직 하나도 안 됐다** — `room`의 `TEMP-NO-PLATFORM`은 그대로이고 `notification` · `matching`도 `?userId=`를 받는다. 각 폴더의 일이다.

## 6. 배포 기준

배포 기준은 **Stage 2(ECS Fargate)**다. Stage 1(단일 EC2 + Docker Compose)은 적용하지 않는다 (docs/11 D-18이 #20을 개정). **k8s / HPA / sticky session을 전제한 구현 금지**는 그대로다.

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라. 정해지면 이 표에서 빼고 해당 절로 옮긴다.

> **2026-09-21에 이 표에서 많은 것이 빠졌다** — 소유자가 "네가 만들어 봐라"고 맡겨 Claude가 정해 구현했기 때문이다(머리의 "지금 상태와 그 지위"). 빠진 것의 원본은 `contracts/platform-api.md`이고 **소유자가 항목별로 검토하지 않았다 · docs/11에 D-항목이 없다.**
> **아래 남은 것에는 위 규칙이 그대로 걸린다 — 묻고 정한다.** 다음에 닿기 전에 물을 것을 추린 목록은 `START_HERE.md` §4다.

| 항목 | 상황 |
|---|---|
| **소유자의 검토 — `contracts/platform-api.md` P-1~P-12** | 미정은 아니지만 **확정도 아니다.** P-1~P-10은 Claude가 정해 구현한 것 전부다(엔드포인트 · 토큰 · 입장권 · `roomId` · `room_seen_at`과 10분 보존 · 방장 확정의 길 둘 · 소셜 로그인의 "처음 오면 로그인 아이디를 정한다" · 게임 프로필과 글의 `voice`/`purpose`/`conditions` · 친구/신고/알림 둘 · 로그인 실패 제한). **P-11(PK는 bigint identity · `userId`는 사용자 번호 · `loginId`는 따로)만은 소유자가 직접 정한 것이고, 검토가 아니라 docs/11에 올리는 것이 남았다**(2026-09-19의 결정 · D-4를 개정한다. `matching`의 `Block.java`도 그 폴더에서 `Long`으로 바꿔야 한다). 소유자가 뒤집으면 코드 · 계약 · 이 파일을 같이 고친다. **계약 원본(본 저장소 `feature/frontend`)에 platform 엔드포인트가 이미 있으면 그쪽과 맞춰야 한다**(P-1). **docs/11에 올리는 것은 `matching` 폴더의 일이고 아직 안 했다** |
| **자동 매칭이 게시판 방에 합류하는 길**(2026-09-23 소유자 결정 — **방향만 정해졌다. docs/11에 D-항목이 없다**) | **두 경로를 다 둔다** — "매칭 시작"을 누르면 ① **조건이 맞는 열린 게시판 방이 있으면 거기에 넣고** ② 없으면 기존 대기열 매칭으로 간다(`matching`의 대기열·제안·수락·확정은 **그대로 살린다** — 갈아엎지 않는다). 이유는 **콜드 스타트**다 — 사람이 적으면 대기열은 영영 안 모인다. **①을 `platform`이 맡는 쪽으로 기운다** — 방 키·차단·프로필을 이미 다 읽는 앱이 여기뿐이다(`matching`이 하려면 `room`의 Redis와 `social.blocks`를 알아야 해서 경계가 무너진다). **정할 것** — ①의 요청이 어느 앱의 어느 경로인가, "조건이 맞는다"를 무엇으로 보는가(글의 `game`·`mode`·`voice`·`purpose`·`conditions`와 매칭 요청의 조건을 어떻게 맞추는가), 맞는 방이 여럿이면 어느 것을 고르는가, ①에서 방에 들어간 사람의 **활성 요청 키**를 어떻게 다루는가(D-19의 "대기와 방은 한 번에 하나만"에 걸린다 — §7 "확정된 사용자를 푸는 길"과 같이 본다), ①이 실패했을 때 ②로 넘기는 것을 누가 하는가(프런트인가 서버인가). **`matching` 폴더에서 D-항목으로 남겨야 한다** |
| **파티 모집 게시판에 남은 세부** | 하는 것·방의 규칙·두 앱의 분담(docs/11 D-11 · D-16), 방 키(§3.3), 목록과 차단의 범위(D-20), 방장 확정의 규칙(D-21)에 이어 **입장권 · `roomId` · "아직 안 만들어진 방"을 가르는 법 · 확정된 글에서 방장 키가 없을 때 · 브라우저가 두 앱을 부르는 순서 · 만료/확정 글의 보존(10분) · 만석 표시 · 글의 내용과 정렬 · 카드에 담는 것 · 강조를 누가 계산하는가**도 정해졌다(`contracts/platform-api.md` — §7.1 "정해진 것"). **남은 것** — 확정된 방의 기능(Ready 등)과 "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가, **게시판 채널 이름의 원본을 둘 곳·재요청을 묶는 간격**(D-20 · D-22), 차단에 남은 경쟁, 목록의 필터(지금은 `game` 하나) · 페이지 나누기, 도배 대응, 자동 매칭 파티의 방(§7.2). 해당 지점에 닿으면 그때 묻는다 |
| `PARTY_*` 등 알림 다섯의 이름과 `payload`, `parties` 자원 | `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED` 둘은 정했다(§3.2). **나머지는 미정이다** — 문서에 이름이 나오는 것은 예시로 든 `PARTY_MEMBER_JOINED` 하나뿐이다 (contracts/events.md). 확정된 파티를 조회하는 경로(`parties`)도 아직 없다 — 자동 매칭 파티(아래)와 같이 정한다 |
| SQS 메시지 본문 3종 · SQS 배선 시점 · **자동 매칭 파티의 id** | **`matching`은 `partyId`를 UUID 문자열로 내려 주는데 `party.parties.id`는 bigint다(2026-09-22) — 그 값을 어디에 둘지 정해지지 않았다**(새 칸에 담을지 · 다른 값을 쓸지). `source = 'MATCH'`의 자리만 있고 만드는 코드가 없다. `ProposalConfirmed`에 무엇이 실려 오는지, 파티 id를 `proposalId`와 같게 둘지 — `matching`은 클라이언트에 `MATCH_CONFIRMED {partyId}`(= `proposalId`)를 **이미 내려 주고**, 파티는 비동기로 생기므로 그 직후 조회는 비어 있을 수 있다 (contracts/events.md "`PARTY_CREATED` 이벤트가 없다"). AWS SDK를 언제 들일지 · 로컬에서 SQS를 무엇으로 흉내 낼지(`matching` 쪽 발행이 아직 없다 — `backend/build.gradle`에 넣을 자리만 주석으로 남겼다). `party.outbox` · `social.outbox`도 그때 만든다. **파티가 "닫혔다"를 무엇으로 판단하는가**도 이 폴더의 문서와 docs/11에서 찾지 못했다 — 이것이 정해져야 `PartyClosed.fifo`와 최근 함께한 사람이 채워진다 |
| **확정된 사용자를 푸는 길** | `matching`은 확정된 사용자의 활성 요청에 `status=PARTY`를 찍어 두고, 푸는 주체가 없다. 후보 셋(`PartyClosed` 소비 / 나가기 API / 긴 TTL)이 **결론 나지 않았다** (HANDOFF.md ①). `PartyClosed.fifo`의 소비자는 platform 하나로 확정했다(§3.4) — 그래서 **`matching`의 `status=PARTY`를 누가 어떻게 푸는지는 여전히 열려 있다.** D-13 · D-16이 가능성으로 적은 "방이 닫힐 때 `room`이 활성 요청 키를 지운다"는 **D-19로 없어졌다** — `room`은 그 키에 쓰지도 지우지도 않는다. **푸는 주체는 `matching`이나 이 앱 쪽에서 찾아야 한다**(이 앱은 지금 그 키를 만지지 않는다 — 만지게 된다면 그것부터가 결정이다). 확정된 사용자는 활성 요청 키가 남아 있어 **그대로는 `room` 입장도 거절된다**(409 `ALREADY_QUEUED`) — 자동 매칭 파티의 방 입장(§7.1)을 정할 때 같이 풀어야 한다. **2026-09-21에 검토한 방향이 있다 — 정하지 않았다**(§7.2 (라)) |
| **운영의 DB 롤**(예전 이름 "테이블 컬럼, DB 롤" — 코드의 주석이 그 이름으로 가리킨다) | 테이블 컬럼은 정해졌다(§3.5 — 원본은 마이그레이션). **스키마별 DB 롤은 두지 않는다**(2026-09-22 소유자 결정 — §3.5. 롤을 만드는 마이그레이션도 `CREATEROLE` 문제도 없어졌다). **남은 것** — 이 앱이 운영에서 붙는 DB 계정의 이름과 권한(로컬은 `postgres` 슈퍼유저다. `matching`도 별도 롤 없이 같은 방식으로 붙는다 — 그 계정은 `matching` 폴더의 일이다), 마이그레이션 계정과 앱 계정을 나눌지 |
| **refresh 토큰의 도입** · 옆 서비스의 전환 | 인증 세부와 그 "남은 것"은 전부 정해졌고 구현됐다(§5.1). **남은 것은 구현이다** — refresh(설계는 §5.1 (라) · (마). **코드가 없다.** 재발급 경로 · refresh 쿠키의 이름 · 로그아웃에서 지우는 것 — 닿으면 묻는다. **배포 전에 있어야 한다**), `room` → `notification` → `matching`의 전환(각 폴더의 일 — 하나도 안 됐다), `Origin` 검사를 세 서비스에 언제 넣는가. **docs/11에 올려야 한다** — §5.1은 시스템 전체에 걸리고 #16(access denylist)을 개정한다. `matching` 폴더에서 D-항목으로 남겨야 한다 — **아직 안 남겼다(2026-09-21)** |
| **소셜 로그인에 남은 것** | 넣는다는 것은 소유자 지시다(2026-09-21). 흐름은 `contracts/platform-api.md` "소셜 로그인"(Claude가 정했다 — P-7. docs/00의 계정 정의에 걸린다). **가짜 제공자로만 테스트했다 — 실제 키로는 붙여 보지 않았다.** 카카오 · 디스코드의 앱 등록 · 키 · Redirect URI 등록은 **소유자가 해야 한다.** **하지 않은 것** — 이미 가입한 계정에 소셜 계정을 나중에 잇기 · 끊기, 소셜 가입자가 비밀번호를 만드는 것(할지부터가 미정이다). 프런트의 경로(`/` · `/signup/social` · `/login?error=OAUTH_FAILED`)는 프런트와 맞춘 적이 없다 |
| **게임 계정 연동 — 게임사 API** | **자기신고와 그것을 담을 자리까지 됐다**(2026-09-21) — 게임 계정(게임 닉네임 · 티어 · 주 포지션 · PUBG의 서버)은 사용자가 적는다. 전적 스냅숏 테이블 `account.game_account_stats`와 `verified` · `external_id` 칸이 있다. **읽는 쪽만 있다 — 채우는 기능이 없어 `stats`는 늘 `null`이고 `verified`를 켜는 길도 없다.** **남은 것** — 전적을 가져오는 주기와 방법(목록을 그릴 때는 부르지 않는다 — 스냅숏만 읽는다), Riot(RSO) 인증으로 `verified`를 켜는 법, API 키(Riot의 승인이 필요하고 **VALORANT의 전적 API는 별도 승인**이다), PUBG API. OP.GG의 "MVP · Ace" 배지와 평점은 Riot API에 없어 넣지 않았다(`contracts/platform-api.md` "게임 프로필"). `matching`의 티어도 지금 자기신고다 (`matching/CLAUDE.md` §2). `tier` · `mode`의 값 목록은 `matching`의 gameconfig가 원본이라 이 앱이 검증하지 않는다 — 그대로 둘지 |
| `reservation` 스키마의 마이그레이션 | 예약은 `app:reservation`(Lambda)으로 빠졌다(D-15). Spring/Flyway가 없는 Lambda가 스스로 마이그레이션하기 어렵다 — **이 앱이 대신 갖는지 별도 절차인지 미정이다.** 정해지기 전에 이 앱에 `reservation` 마이그레이션을 넣지 않는다 |
| Redis의 다른 용도 | 파티 presence/ready(`qm:party:presence:*`·`qm:party:ready:*`), rate limit은 원본 docs/07의 **키 이름만** 있다. 누가 쓰는지(presence는 D-16으로 `room` 쪽 성질이 됐다), `matching`의 `qm:party:*`와 접두사가 겹치는 것을 어떻게 할지. (로그인 실패 제한은 그 rate limit과 별개로 이 앱의 접두사 `qm:auth:*`에 두었다 — §4) |
| 뼈대의 임시값 가운데 남은 것 | **정해진 것** — 포트 8082, 패키지를 나누는 법, readiness에 `db`를 넣고 Redis는 넣지 않는 것(§4 · §5). **남은 것** — 로컬 DB 이름 `queuemate` · 계정 `postgres`(위 "운영의 DB 롤"), Flyway 기록 테이블의 자리(기본값 `public`), **JSON 로그 형식**(운영에서 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`로 켜는 것으로 해 두었다 — `ecs`여야 하는지는 정해진 적이 없다). (테스트용 PostgreSQL은 `docker.exe`로 **5433**에, Redis는 **6380**에 따로 띄운다 — `START_HERE.md` §6. 5432 · 6379면 테스트가 건너뛴다) |

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
- **갱신은 게시판 채널의 "바뀌었다" 신호로 한다**(§3.2 "게시판 채널") — 채널 `qm:pubsub:board`(게임을 구분하지 않는 하나 — D-22), `type` `BOARD_CHANGED`, `payload` `{}`(**`roomId`도 `game`도 싣지 않는다**),
  **`topics` 파라미터는 없앴다(D-22)** — `notification`이 모든 연결에 그대로 보내고 클라이언트가 거른다(게시판 페이지를 보고 있을 때만 묶어서 다시 받는다). 신호는 "다시 받아라"일 뿐이고 받은 프런트가 이 앱의 목록을 `GET`으로 다시 요청한다. **이 앱의 발행도 구현됐다**(2026-09-21. `room`의 발행은 D-23). **게시판은 실시간으로 바뀌어야 한다**(인원 · 새 글 — 2026-09-21 소유자 지시)는 이것으로 지킨다.
- **파티원은 방장이 확정한다.** 확정하면 **더 이상 새 사람이 들어올 수 없다** — 확정된 글에 입장권을 내주지 않는 것으로 지키고, `room`도 확정된 방의 입장을 409 `ROOM_CONFIRMED`로 거절한다.
- **방장 확정의 규칙이 정해졌다**(2026-09-20, D-21 · `docs/ROOM_CONTRACT.md` "방장 확정"). 확정 요청은 **`room`이 받는다**(방장만 · 2명 이상). 대상은 **그 순간 방에 있는 전원**(방장 포함)이다 — 방장이 고르지
  않고, 원치 않는 사람은 그 전에 강퇴한다. **되돌릴 수 없다** — 확정 뒤 빈자리가 생겨도 다시 모집을 열 수 없다. **확정한 방은 방장이 나가도 없어지지 않는다 — 남은 멤버가 방장을 넘겨받는다(D-23. 방장 키의 값이 바뀔 수 있다 — §3.3).** **이 앱의 몫은 확정 표시 키
  `qm:room:{roomId}:confirmed`와 멤버 SET을 읽어 글의 상태를 "확정"으로 바꾸고 파티원을 기록하는 것**이고, **`room`의 키에 쓰지 않는다** — "닫힘" 표시를 이 앱이 쓰는 방법은 받지 않았다(§3.3).
- **방장이 나가면 글은 지우지 않고 "만료"로 표시한다.** 목록에 남지만 들어갈 수 없다. 방장 이탈은 `room`이 판단하고(명시적 나가기와 **연결 끊김 둘 다** — 확정하지 않은 방의 수명은
  방장의 접속 확인만 늘린다. **확정한 방은 방장이 나가도 방이 이어지므로 이 규칙에 걸리지 않는다** — D-23), **이 앱은 목록을 그리거나 입장권을 요청받을 때 방장 키(`qm:room:{roomId}:host`)가 사라진 것을 보고 글을 만료로 바꾼다** — `room`은 알려 주지
  않는다. 만료가 즉시는 아니지만 목록을 그리는 순간 걸러지므로 보이는 차이는 없다(방장이 말없이 사라진 경우는 수명이 다할 때까지 최대 10분 늦는다 — §3.3).
- **방 키의 이름·구조·수명**이 정해졌다(§3.3 · `docs/ROOM_CONTRACT.md`). **방은 `room`의 "방 만들기" 요청이 만든다** — 입장은 방을 만들지 않는다(없는 방은 404).
- **`room`이 내는 알림이 정해졌다** — `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · `WEBRTC_SIGNAL`(전부 구현돼 있다 — D-21).
  `PARTY_*`를 다시 쓰지 않고 새 이름을 지었다 — 그 7종의 이름과 `payload`가 이 컴퓨터의 문서에 없어서다(`../room/contracts/room-api.md` "알림").
- 입장 승인 없음·둘러보는 상태·강퇴·최대 5명·음성 제어(브라우저에서 끝난다)는 `room`과 프런트 쪽 규칙이다. "자동 매칭 대기와 방은 한 번에 하나만"은 `room`과 `matching`이
  키 둘로 지킨다(D-19) — 이 앱은 관여하지 않는다.

**2026-09-21에 정해진 것 — 출처는 `contracts/platform-api.md` "모집 글 · 목록 · 입장권" · "입장권" · "방장 확정의 기록".** 첫 항목만 소유자 지시이고, **나머지는 Claude가 정해 구현했으며 소유자가 항목별로 검토하지 않았다**(P-3 ~ P-6 · P-8). docs/11에 D-항목이 없다.
- **목록의 한 줄은 게임마다 다른 정보를 보여 준다**(2026-09-21 소유자 지시. 본보기는 OP.GG의 듀오 찾기) — LoL: 이름#태그 · 인증 · 주 포지션 · 티어 · 찾는 포지션 · 모스트 챔피언 · 승/패 · KDA · 메모,
  VALORANT: 모스트 요원 · 주 무기 · 헤드샷률 · 음성, PUBG: 모드 · 티어 · 음성/목적 태그 · 서버/시점 · 평균 데미지 · K/D · 치킨률. **세 게임 모두 가로 한 줄이고, 글을 눌러 펼치지 않고 방 안 전원을 그 자리에서 다 보여 준다** —
  그래서 **목록 응답의 `members[]`에 게임 프로필 전체가 실린다**. **백엔드는 같은 모양에 게임별 내용을 채운다** — 글 한 줄의 모양은 세 게임이 같고 `host.profile` · `members[].profile`(그 글의 게임에 연결한 게임 계정)과 글의 `voice` · `purpose` · `conditions` · `wantedPositions` · `description`이 게임마다 다르게 채워진다.
  **전적(`stats`)은 아직 늘 `null`이다**(§7 "게임 계정 연동") — 모스트 챔피언 · KDA · 평균 데미지 같은 칸은 화면이 "정보 없음"으로 그린다.
- **`roomId` = 글의 id**(2026-09-22 소유자 결정으로 **bigint identity**가 됐다 — 정할 때는 UUID였다) — §3.3. **입장권의 형식**(같은 키의 JWT · `token_use` = `room_ticket` · `room_id` · `host_id` · 60초. 셋 다 숫자를 문자열로 찍는다) — §3.3. 방 만들기가 방장의 첫 입장에 합쳐지는지는 `room` 쪽에서 정한다(`../room/CLAUDE.md` §7) — 이 앱은 방장에게도 같은 요청으로 입장권을 준다.
- **"아직 안 만들어진 방"과 "사라진 방"은 `room_seen_at`으로 가른다** — §3.3. **확정된 글은 방장 키가 없어도 만료시키지 않는다 — 끝까지 `CONFIRMED`다**(§3.3).
- **브라우저가 두 앱을 부르는 순서 — `room`의 확정 → 이 앱의 `POST …/confirm`(길 ①), 받침은 목록이 발견하는 길 ②** — §3.3 · §7.2 (가). 읽는 시점의 멤버 SET이 확정 순간과 다를 수 있는 것은 **감수한다.**
- **글에 담는 것** — `game` · `mode`(30자까지의 자유 문자열 — 목록의 원본은 `matching`의 gameconfig) · `title`(1~60자) · `description`(300자까지) · `voice`(`REQUIRED` · `NO_VOICE`) · `purpose`(`RANK_UP` · `NORMAL` · `FUN` — 둘 다 `matching`의 `VoicePreference` · `PlayPurpose`와 같은 이름) ·
  `conditions`(게임별 조건 — PUBG는 `{"perspective": "TPP" | "FPP"}` 필수, LoL · VALORANT는 `{}`) · **`wantedPositions`(그 게임의 포지션 이름의 배열. PUBG는 빈 배열)**. **모집 중인 글은 한 사람에 하나**다(DB의 부분 UNIQUE 인덱스 — 409 `ALREADY_RECRUITING`).
- **③의 강조는 이 앱이 계산한다** — 응답의 `filledPositions` = `wantedPositions` ∩ 방 안 사람들의 주 포지션. **카드에 담는 것** — `{userId, nickname, host, profile}`이고 `profile`은 게임 프로필 전체다(게임 계정이 없으면 `null`).
  `host`는 방이 아직 없거나 글이 만료 · 확정된 뒤에도 채워진다. `members`는 방 안에 지금 있는 사람이다.
- **정렬** — 모집 중인 글이 먼저, 그 안에서는 새 글이 먼저. 필터는 `?game=` 하나다(없으면 세 게임 전부). **만료 · 확정된 글은 그렇게 된 뒤 10분 동안만 목록에 남는다**(`status`로 구분해 보여 준다. 멤버는 비운다 — `platform.board.closed-retention`). **만석인 방은 `full: true`로 목록에 남는다.**
- **글을 지우면(`DELETE`) 지우지 않고 "만료"로 바꾼다.** 확정된 글은 지울 수 없다(409 `POST_CONFIRMED`). 차단 관계로 숨겨진 글은 단건 · 입장권에서도 **없는 글과 같은 404**다.

**정할 것 — 개발하면서 정한다.** 구현하다 해당 지점에 닿으면 **그때 묻고 정한다.** 임의로 정해 구현하지 마라.
- **방장 확정에 남은 것** — 확정된 방이 자동 매칭 파티방과 **같은 기능(Ready 등)**을 갖는가, "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가(기록할 자리 `party.party_members`는 있다 — 채우는 계기인 "파티가 닫혔다"가 미정이다, §7). 둘 다 빠지고 방이 사라지는 구멍(§7.2 (가))을 막을지.
- **차단에 남은 것**(범위는 정해졌다 — 위) — 입장권을 **받은 뒤 들어오기 전** 사이에 나와 차단 관계인 사람이 먼저 그 방에 들어온 경우의 경쟁(D-20 "아직 미정" — 입장권의 수명이 60초라 창이 그만큼으로 줄었을 뿐이다). 목록을 본 뒤 차단이 생긴 경우, 이미 같은 방에 있는 두 사람 사이에 차단이 생긴 경우(D-11의 미정 그대로). 차단이 이미 맺은 친구 관계를 끊는가(지금은 건드리지 않는다 — `contracts/platform-api.md` "차단").
- **게시판 채널에 남은 것**(§3.2. 이름·`type`·`payload`는 정해졌고 `topics`는 없앴다 — 위) — 채널 이름의 **원본 상수를 어느 서비스에 둘지**(지금은 세 앱이 각자 적어 두었다), 프런트가 재요청을 묶는 간격(D-20 · D-22 "아직 미정").
  **`topics` 표기와 "`room`이 어느 게임의 채널에 발행할지를 어떻게 아는가"의 미정은 D-22로 물음째 없어졌다** — 채널이 하나라 발행하는 쪽이 게임을 알 필요가 없다. 방 키 약속도 입장권도 이 때문에 바뀌지 않는다.
- **자동 매칭으로 확정된 파티의 방은 어떻게 생기는가** — 이 앱이 파티를 DB에 만든 뒤 파티원이 입장권으로 들어오는 것이 자연스럽지만 미정이다. **확정된 사용자는 활성 요청 키(`status=PARTY`)가 남아 있어 그대로는 `room` 입장이 거절된다** — §7 "확정된 사용자를 푸는 길"과 같이 풀어야 한다(D-19 "아직 미정"). **2026-09-21에 검토한 방향이 있다 — 정하지 않았다**(§7.2 (다)). 강퇴당한 사람의 재입장을 입장권으로 막을지(지금은 막지 않는다 — `room` 쪽 미정 그대로).
- 목록의 **필터를 더 둘지 · 페이지를 나눌지**(지금은 보존 기간 안의 글을 전부 내려 준다). **도배 글 대응**과 신고(docs/11 #13)의 연결 — 지금 있는 것은 "모집 중인 글은 한 사람에 하나"뿐이다.
- **알림 종류** — 방 입장·퇴장·방 닫힘·강퇴·**방장 확정**(`ROOM_CONFIRMED` — `payload`의 `members`가 파티원이다)은 `room`이 새 이름으로 내는 것으로 정해졌다(위). **이 앱이 낼 알림** 가운데 `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`는 정했고(§3.2) **`PARTY_*`의 이름과 `payload`는 여전히 미정이다.** 계약 원본과 합칠 때 `ROOM_*`과 `PARTY_*`가 같은 뜻인지 맞춰야 한다.

### 7.2 2026-09-21에 검토한 방향 — (가)는 받았다 · (나)(다)(라)는 정하지 않았다 (묻어 두었다)

> **(가)는 같은 날 구현하며 받았다** — `contracts/platform-api.md` "방장 확정의 기록" · P-6. **Claude가 받은 것이고 소유자가 항목별로 검토하지 않았다.** 결정 로그(docs/11)에 D-항목이 없다.
> **(나) · (다) · (라)는 결정이 아니다.** 소유자와 논의만 하고 묻어 둔 것이다. 결정 로그(docs/11)에 D-항목이 없다. **그 셋을 근거로 구현하지 마라** — 해당 지점에 닿으면 이것을 들고 **다시 묻는다.**
> 원문은 `../room/CLAUDE.md` §7 "`status=PARTY` 해제" 행이다(`room`의 눈으로 적혀 있다). 여기는 그것을 이 앱의 눈으로 옮긴 것이다. 이미 정해진 것은 "(정해진 것)"이라고 따로 표시했다.

**(가) 게시판 방의 방장 확정을 이 앱이 아는 법 — 받았다 · 구현됐다(2026-09-21. §3.3 "방장 확정").** "브라우저가 `room`과 이 앱을 어떤 순서로 부르는가"에 대한 방향이었다. 아래는 검토한 그대로이고, 구현이 어떻게 받았는지를 괄호로 붙였다.

- **`room`은 보내지 않는다 — 이 앱이 읽어 간다.** `room`은 DB가 없어 outbox를 못 하므로 "절대 안 잃는" 발행을 할 수 없다. 이 앱이 방 키를 읽는 것(§3.3)은 이미 정해진 길이다.
- **길 둘로 받친다.** ① 프런트가 `room`의 확정이 성공한 직후 이 앱에 "이 글을 확정 처리해 달라"고 부른다. **이 앱은 그 말을 믿지 않는다** — `room`의 키(확정 표시 키 · 방장 키 · 멤버 SET)를 읽어 검증한 뒤 DB에 쓴다(구현 — `POST /api/v1/posts/{postId}/confirm`. **확정 표시 키와 멤버 SET**을 본다. 방장 키는 검증에 쓰지 않는다 — 확정한 방은 방장 키만 잠깐 없을 수 있다, D-23).
  ② 이 앱이 목록을 조립하며 방 키를 읽을 때 **"DB에는 모집 중인데 확정 표시 키가 있는 글"**을 보면 그 자리에서 확정으로 기록한다(①이 빠졌을 때의 받침. 구현 — 목록뿐 아니라 단건 조회 · 입장권 발급에서도 한다).
- **기록은 `roomId` 기준으로 멱등이어야 한다** — 두 길이 같은 글을 두 번 기록하려 들 수 있다(구현 — `party.parties`의 PK가 `roomId`다. 두 길이 동시에 와도 파티는 하나다).
- **남는 구멍** — 둘 다 빠지고 방이 사라지는 드문 경우. 막으려면 `room`이 확정 순간의 멤버를 방보다 오래 남는 키에 남기고 이 앱이 주기적으로 훑어야 한다. **넣지 않았다**(지금도 없다 — 그런 글은 "사라진 방"으로 읽혀 만료가 된다).
- `docs/ROOM_CONTRACT.md` 머리 절의 물음 둘은 계약에서 답했다 — 이 앱이 읽는 시점의 멤버 SET이 확정한 그 순간의 것과 다를 수 있는 것은 **감수한다**, 확정된 글은 방장 키가 없어도 **만료시키지 않는다**(`contracts/platform-api.md` "방장 확정의 기록" · "모집 글 · 목록 · 입장권").

**(나) 자동 매칭 확정을 이 앱이 아는 법.**

- (정해진 것 — docs/11 #21 · §3.4) `matching` → outbox → SQS `ProposalConfirmed.fifo` → 이 앱이 파티를 DB에 만든다. SQS는 같은 메시지를 두 번 줄 수 있으므로 **소비는 멱등**이어야 한다.
- (검토한 방향) 멱등의 기준을 **`partyId`**로 둔다(`matching`이 클라이언트에 내려 주는 `partyId`는 `proposalId`와 같다 — §7 "SQS 메시지 본문 3종". 파티 id를 그것과 같게 둘지부터가 미정이다).
  (`party.parties`는 이 방향을 받을 수 있는 모양으로 만들어 두었다 — `id`가 PK이고 `source`에 `MATCH`의 자리가 있다. **만드는 코드는 없다.** 계약이 "자동 매칭 파티는 `roomId = partyId`다"라고 적은 한 줄도 이 미정 위에 서 있다.)
- (검토한 방향) **`matching` → `room` → 이 앱으로 잇는 안은 받지 않았다** — (가)와 같은 이유다. `room`은 "절대 안 잃는" 발행을 할 수 없다. 서버 간 이벤트는 `matching` → 이 앱의 SQS 하나로 둔다.

**(다) 자동 매칭 파티의 방과 입장권** — §7.1 "자동 매칭으로 확정된 파티의 방은 어떻게 생기는가"에 대한 방향이다.

- **방은 서버끼리 연락해서 만들지 않는다.** 클라이언트가 `MATCH_CONFIRMED`의 `partyId`를 들고 `room`에 "없으면 만들고 있으면 입장"을 한 번에 부른다. 이 앱이 방을 만들어 주거나 `room`을 부르지 않는다.
  그 방을 처음부터 확정된 방으로 둘지 · 정원 · 강퇴 · 게시판 신호를 안 내는 것은 `room`을 구현할 때 묻는다(`room` 쪽 일이다).
- **파티 입장권은 이 앱이 아니라 `matching`이 발급하는 쪽이 낫다** — 이 앱은 SQS 지연 때문에 **확정 직후에는 그 파티를 모른다**(§7 "SQS 메시지 본문 3종"의 "그 직후 조회는 비어 있을 수 있다"와 같은 사정이다).
  그러면 입장권을 발급하는 앱이 둘이 된다(게시판 방은 이 앱, 파티 방은 `matching`) — 입장권의 형식 · 서명 키를 나눠 갖는 법을 정할 때 같이 봐야 한다. **미정이다.**
  (게시판 방의 입장권은 그 뒤에 정해졌다 — §3.3. **이 앱의 개인 키로 서명한다.** `matching`이 파티 입장권을 발급하게 되면 §5.1 (가)의 "서명은 이 앱만 한다"와 부딪힌다 — 그때 같이 묻는다.)
- 입장권 형식이 정해지기 전의 임시안으로는 `room`이 활성 요청 키의 `status` · `partyId`를 **읽어** 통과시키는 안이 있었다(D-19의 "`EXISTS`만"을 완화해야 한다 — `room` · `matching` 쪽 결정이고 이 앱은 걸리지 않는다).

**(라) `status=PARTY` 해제** — §7 "확정된 사용자를 푸는 길"에 대한 방향이다.

- **아무도 지우지 않는다.** 확정 때 `matching`이 자기 활성 요청 키에 **짧은 수명**을 걸고, 그 안에 사용자가 파티방에 들어오면 `room`의 입장 표시 키가 자물쇠를 이어받는다(`matching`의 `claim-request.lua`가 이미 입장 표시 키를 본다 — D-19).
  활성 요청 키는 수명이 다해 저절로 사라진다. D-19를 어기지 않는다 — 각자 자기 키만 쓴다.
- **이 방향이면 이 앱이 푸는 주체가 되는 안(파티가 닫힐 때 `PartyClosed.fifo`를 계기로 푼다)은 필요 없어진다.** 이 앱은 여전히 활성 요청 키도 입장 표시 키도 만지지 않는다(§2).
  `PartyClosed.fifo` 자체(닫힌 파티의 멤버로 `social.recent_players`를 만든다 — §3.4 · D-13)는 이 검토와 무관하게 그대로다.
- **미정이다.** 정해지면 `matching` 폴더에서 docs/11에 D-항목으로 남기고 §7의 그 행을 고친다.

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `platform` 브랜치**다. 이력이 없는 별도 브랜치에서 시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       matching 브랜치
├── notification/   notification 브랜치
├── room/           room 브랜치. 방 안의 일 (D-16)
└── platform/       platform 브랜치 (이 폴더)
    ├── START_HERE.md            시작 안내 — 지금 어디까지 됐나 · 만드는 순서 · 다음에 닿기 전에 물어야 하는 것
    ├── CLAUDE.md · README.md    규칙 / 짧은 소개
    ├── docs/                    옆 폴더 문서의 사본 (ROOM_CONTRACT.md — §10)
    ├── contracts/
    │   └── platform-api.md      **이 폴더에서 정한 계약** — 경로 · 스키마 · 에러 코드 · 토큰 · 입장권 · 알림 + "원본에 올려야 할 것"(P-1~P-12) (§3.1)
    └── backend/                 스프링 앱. matching/backend/ · room/backend/ 와 같은 모양이다
        ├── build.gradle · settings.gradle · gradlew · gradle/wrapper/
        ├── .dev-keys/           개발용 JWT 키(private.pem · public.pem). 앱이 만든다. **git 에 올리지 않는다** — 옆 서비스는 public.pem 으로 검증한다 (§5.1)
        └── src/
            ├── main/java/com/queuemate/platform/    PlatformApplication · package-info(패키지를 나누는 법 — 먼저 읽는다)
            │   ├── common/      error(에러 본문 · 제약 위반을 에러 코드로) · web(Origin 검사) · security(토큰 서명과 검증 · 보안 설정 · TokenClaims) · push(알림 봉투 · 채널 접두사 · 발행)
            │   ├── account/     가입 · 로그인 · 프로필 · 게임 계정(게임 프로필) · 로그인 실패 제한 · oauth/(소셜 로그인 — 제공자와 주고받는 것)
            │   ├── social/      차단 · 친구 요청과 친구 · 신고 · 최근 함께한 사람(읽기)
            │   └── party/       모집 글 · 목록 · 입장권 · 방장 확정의 기록. room/(room 의 방 키를 읽는 곳 — 쓰지 않는다) · board/(게시판 채널 신호 발행)
            ├── main/resources/application.yaml      환경변수 + 기본값 (포트 8082 · PostgreSQL 5433 · Redis 6380 · JWT · Origin · 로그인 실패 제한 · 게시판 · 소셜 로그인)
            ├── main/resources/db/migration/         Flyway. V1__baseline.sql(주석뿐) + account/ V2 · V3 · social/ V4 · V6 · party/ V5 — 번호는 폴더 사이에서 하나의 순서다 (§3.5)
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
- 위 함정은 전부 `room`을 만들며 실제로 겪은 것이다. 명령과 그 밖의 환경 함정(`pkill -f` 금지, 느린 빌드)은 `../room/docs/NOTIFICATION_LESSONS.md`에 있다.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| **시작 안내** — 지금 어디까지 됐나, 옆 서비스의 임시 처리(`TEMP-NO-PLATFORM`)와 이 앱이 이제 줄 수 있는 것, 만드는 순서와 단계별 확인, 다음에 닿기 전에 물어야 하는 것 · 소유자가 검토해야 하는 것, 로컬에서 띄우는 법 | `START_HERE.md` (이 폴더) |
| **이 폴더에서 정한 계약** — 공통(에러 · 인증 · `Origin`) · access 토큰 · 계정 · 게임 프로필 · 소셜 로그인 · 차단 · 모집 글/목록/입장권 · 방장 확정의 기록 · 친구/신고/최근 함께한 사람 · 알림 · **"원본에 올려야 할 것" P-1~P-12.** Claude가 정했고 소유자가 항목별로 검토하지 않았다 | `contracts/platform-api.md` (이 폴더) |
| **ERD**(테이블의 원본은 `backend/src/main/resources/db/migration/`이다 — 그림이 어긋나면 마이그레이션이 맞다) | <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq> |
| 매칭 엔진 규칙 (제품 경계·INV·Contract first의 원형) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 배달 규칙 (받는 쪽이 메시지를 어떻게 다루나) / **방 안의 일의 규칙**(입장권을 받는 쪽, 방 키의 원본) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/notification/CLAUDE.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/CLAUDE.md` |
| **`room` 계약의 사본** — 이 앱이 읽는 Redis 키, 방 만들기·입장·접속 확인, `room`이 내는 알림, 이 앱에서 그것이 뜻하는 것. 옆 폴더가 없어도 읽을 수 있다. **낡는다** — 머리의 확인 명령을 돌린다 | `docs/ROOM_CONTRACT.md` (이 폴더) |
| `room` 계약의 원본 (요청 전부 · 알림 · "Redis 키" 절) / 방 키 상수의 원본 / 이 앱이 생기면 채워 줘야 하는 자리(`TEMP-NO-PLATFORM`, §2) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/contracts/room-api.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/backend/src/main/java/com/queuemate/room/redisKeys/RoomKeys.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/START_HERE.md` |
| 로컬 환경 함정 (띄우고 죽이기 · IntelliJ · worktree · 테스트) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/room/docs/NOTIFICATION_LESSONS.md` |
| 결정 로그 — #13~#17 · #20~#26 · D-1~D-4 · **D-9** · **D-11**~**D-16** · **D-18** · **D-19**(D-11 16번과 D-16의 활성 요청 키 대목을 개정 — 파일 머리의 "낡은 항목 주의"로 걸러 읽는다) · **D-20**(게시판 목록 — 방 안 사람 카드·이 앱이 조립·게시판 채널 신호·차단은 방 안의 누구와든. D-11 14번의 범위를 정하고 D-16의 방 키 읽기 범위를 늘렸다) · **D-21**(`room`의 방 안의 규칙과 계약 — 방 키 · 수명 · 방장 확정. **확정 표시는 `room`이 쓰고 이 앱은 읽는다.** D-16의 "닫힘 표시" 가능성은 받지 않았다) · **D-22**(게시판 채널은 게임을 구분하지 않는 `qm:pubsub:board` 하나 · `topics`를 없애고 거르기는 클라이언트가 한다 — D-20 (다)를 개정) · **D-23**(확정한 방은 방장이 나가도 없어지지 않고 방장 자리를 넘긴다 — **확정한 방에서는 방장 키의 값이 바뀔 수 있다.** `room`의 게시판 채널 신호 발행이 구현됐다 — D-21 · D-20을 개정) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/11_DECISION_LOG.md` |
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
- **방 안의 일**(방 만들기·입장·나가기·강퇴·정원·접속 확인·입장 표시 키·방 알림·시그널 `POST`·`WEBRTC_SIGNAL`) — `room`의 일이다(D-16 · D-19). `room`을 호출하지도 마라 — 입장권과 방 키 읽기로만 잇는다. 방 키에 **쓰지 마라** — 확정 표시도 `room`이 쓴다("닫힘" 표시를 이 앱이 쓰는 방법은 받지 않았다 — D-21). **방장 키가 없다고 방금 쓴 글을 만료시키지 마라** — 아직 안 만들어진 방일 수 있다(`room_seen_at`으로 가른다 — §3.3). **확정된 글을 방장 키가 없다고 만료시키지 마라**(§3.3 · D-23). **방 키를 못 읽은 것을 "방이 없다"로 읽지 마라**
- 예약(REST·짝 찾기·`RESERVATION_*` — `app:reservation`, D-15), TURN credential 발급, gameconfig — 각각 다른 배포 단위의 일이다
- 엔드포인트 경로·스키마·payload 필드·테이블 컬럼을 **지어내기** — 묻고, 정한 것은 `contracts/platform-api.md`에 적는다. 2026-09-21에 Claude가 정해 구현한 것은 **소유자가 맡겨서** 한 것이다 — 남은 미정(§7)에 같은 방식을 되풀이하지 마라. **코드만 바꾸고 `contracts/platform-api.md`를 안 고치기**, 이미 적용된 마이그레이션 파일 고치기(§3.5)
- 공개 사용자 탐색(사람 검색·둘러보기)·길드·피드·팔로우·좋아요·모집과 무관한 공개 채팅방 — 여전히 금지다(§1 · D-11)
- 게시판 모집의 **남은** 미정 사항(§7.1 "정할 것")을 **임의로 정해 구현하기** — 구현하다 그 지점에 닿으면 그때 사용자에게 물어라
- 채널 접두사를 `matching`과 따로 바꾸기, 방 키 형식을 `room`과 따로 적기, 알림 실패로 본 작업을 실패시키기, 모집 중이 아니거나 차단 관계인 글에 입장권 내주기(**차단은 방장만이 아니라 방 안의 전원과 본다** — D-20)
- 게시판 채널 신호에 **데이터(`roomId`·프로필·방 안 사람 등) 싣기** — 방송은 사람별로 거를 수 없어 차단이 뚫린다(D-20 · D-22). 포지션을 `room`에 들게 하기, 목록을 그리려고 `room`의 API 부르기(D-20)
- `social.blocks`의 모양을 `matching`과 상의 없이 바꾸기, 크로스 스키마 FK·JOIN, 스키마별 DB 롤이나 `GRANT`를 다시 두기(§3.5 — 두지 않는다), `matching`이 읽는 테이블을 `social.blocks` 밖으로 늘리기
- **로그인 아이디를 사용자의 식별자로 쓰기**(§3.5 — 2026-09-22 소유자 결정) — JWT의 `sub`·알림 채널·방 키·URL·요청과 응답 본문의 `userId`·다른 스키마의 `*_id`는 전부 **사용자 번호**다. `loginId`는 가입·로그인 본문과 `users/me`의 응답에만 나온다(로그인 실패 제한의 Redis 키만 예외다). 새 테이블에 PK를 `bigint identity` 말고 다른 것으로 두기, 본문의 id가 숫자가 아닌 것을 400으로 갈라 주기(**없는 사용자와 같은 404다**)
- `조회 → 판단 → 삽입`으로 불변식 지키기, H2로 제약·권한을 검증했다고 치기, 경계를 넘는 새 동기 호출
- 인증(§5.1)에서 — HS256으로 비밀 키 나눠 갖기, JWKS 엔드포인트, access denylist, CSRF 토큰, 서비스에 CORS 설정 넣기, **상태를 바꾸는 GET**, access 토큰에 닉네임처럼 바뀌는 값 싣기, jjwt 등 다른 JWT 라이브러리 들이기, Spring의 `oauth2-client` 들이기(세션을 쓴다), **`token_use`를 안 보고 토큰 받기**, `TokenClaims`의 값(`iss` · 쿠키 이름 · `token_use`)을 옆 서비스와 따로 바꾸기, `backend/.dev-keys/`를 git에 올리기, `TEMP-NO-REFRESH`를 단 채 배포하기
- Kafka/RabbitMQ/Redis Streams, k8s/HPA/sticky session 전제 구현
- 포트 6379·5432 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`, 옆 폴더 파일 수정
