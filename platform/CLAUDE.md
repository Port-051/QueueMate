# CLAUDE.md — platform 서비스 규칙 (Non-Negotiable)

> **2026-10-02 멘토 요구사항 반영 브랜치:** 사용자의 최신 요청에 따른 자동 확정·포지션 보존·개인 메시지·방장 설정 수정 계약은 [P-52 · P-53 · P-54](contracts/mentor-room-ux.md)을 우선한다. 아래의 상충하는 과거 결정은 이력이다.

작업 전에 `START_HERE.md`(지금 상태 · 만드는 순서 · 다음에 물을 것) → 이 파일 → `README.md` → **`contracts/platform-api.md`**(이 폴더의 계약 — 경로 · 스키마 · 에러 코드 · 토큰 · 방 · P-항목) 순으로 읽어라.

이 폴더는 QueueMate 의 **API 서버 배포 단위 하나 — `app:platform`**(docs/11 #15)이다. **계정 · 파티 · 소셜 REST · 모집 게시판 · 방 안의 일(입장 · 강퇴 · 시그널 — 2026-09-25 에 옛 `room` 앱을 합쳤다, P-22)** 을 맡는다. 매칭 엔진은 옆 `matching`, 알림 배달은 `notification`, 예약은 `app:reservation`(D-15)이다.

> **이 파일은 지금 유효한 규칙만 담는다(2026-09-30 에 줄였다).** "언제 · 왜 그렇게 정했나", 날짜별 소유자 결정 블록, 걷어낸 옛 규칙은 **`docs/CLAUDE_HISTORY.md`**(줄이기 전의 전문)에 있다. 절 번호는 그 파일과 같다 — 코드와 문서가 `CLAUDE.md §7.1` 처럼 가리키므로 **절 번호와 표의 행 이름을 바꾸지 마라.**

> **출처 표기.** `docs/…` · `contracts/…` · `HANDOFF.md` 처럼 폴더 없이 적은 것은 옆 폴더 **`matching` 기준**이다(절대 경로는 §10). **`contracts/platform-api.md` 만은 이 폴더의 것이다.** **출처가 안 붙은 사실은 정해지지 않은 것이다** — §7 로 보낸다.

> **지위 — 누가 정했나.** 소유자가 "네가 platform 을 만들어 봐라"고 맡겨 Claude 가 정해 구현한 것이 많고, 그 원본은 `contracts/platform-api.md` 의 **P-항목**(맨 아래 "원본에 올려야 할 것")이다.
> - **Claude 가 정했고 소유자가 항목별로 검토하지 않았다** — P-1 ~ P-10(P-3 · P-5 · P-6 · P-10 은 걷어냈다), **P-30**(자동 매칭 파티의 방 — D-42 위의 세부), 그리고 소유자 결정 안의 "Claude 가 정한 세부" — **P-14 · P-15 · P-16 · P-17 · P-19 · P-22 · P-23 · P-28 · P-32 ~ P-36 · P-38 ~ P-44 · P-48 · P-49 · P-50 · P-51 · P-52** 의 세부가 그렇다(계약의 그 P-행에 가려 적었다. 이 파일은 "(Claude 세부)"로 표시한다 — 표시가 빠졌더라도 계약의 행이 원본이다).
> - **소유자가 직접 정했다** — P-11 ~ P-29 · P-31 ~ P-36 · P-38 ~ P-49(**P-44** 참가할 때 고르는 포지션 · **P-45** 글 고치기 없앰 — §3.3 · §7.1 · **P-48** 회원 탈퇴 — §2 · §3.5 · **P-49** LoL 전적에서 커스텀 게임 빼기 — §7 "게임 계정 연동") · P-50(빠른매치 입장 허용 / 금지 — §7 · §7.1) · P-51(JWT 키가 없으면 기동 거부 — §5.1) · P-52(차단 관계를 Redis 집합에도 적는다 — §3.7). docs/11 D-항목 대응: P-2 → D-24 · P-11 → D-25 · P-15 → D-26 · P-12 · P-13 → D-27 · P-14 · P-20 · P-21 → D-28 · P-16 → D-29 · P-17 → D-30 · P-18 → D-31 · P-19 → D-32 · P-22 → D-33 · P-23 → D-34 · P-24 → D-35 · P-25 → D-36 · P-26 → D-37 · P-27 → D-38 · P-29 → D-39 · P-28 → D-40 · P-31 → D-44 · P-32 → D-46 · P-35 → D-47 · P-36 → D-48 · P-38 → D-50 · P-39 → D-51 · P-40 → D-52 · P-41 → D-53 · P-42 → D-54 · P-47 → D-56 · P-52 → D-57. **아직 D-항목이 없다 — P-33 · P-34 · P-43 ~ P-46 · P-48 · P-49 · P-50 · P-51**(docs/11 에 올리는 것은 `matching` 폴더의 일이다).
> - 이 파일에서 출처가 `contracts/platform-api.md` 인 것은 **소유자가 검토하며 뒤집을 수 있다.** 뒤집으면 코드 · 계약 · 이 파일을 같이 고친다.
> - **"미정이니 묻고 정하라"는 남은 미정(§7 · §7.1)에 그대로 유효하다** — 한 번 맡긴 것이 다음에도 임의로 정해도 된다는 뜻이 아니다.

> **지금 상태.** 1단계(계정 · 인증 · 소셜 로그인 · 게임 프로필) · 2단계(차단) · 3단계(모집 글 · 목록 · 게시판 채널 신호) · 5단계(방장 확정의 기록) · 7단계 일부(친구 · 신고 · 최근 함께한 사람 · 알림 둘) · 방 안의 일 · 자동 매칭 파티의 방(P-30) · 게시판 방 먼저 합류(P-28)가 구현됐다(`START_HERE.md` §1). **6단계(SQS)는 없어졌다**(D-42 — §3.4).

---

## 1. 제품 경계

QueueMate 는 **조건 기반 팀원 자동 랜덤 매칭**이다 — "조건은 사용자가 정하고, 사람 선택은 시스템이 한다"(docs/00 §1). 이 서비스는 매칭의 **앞(계정)과 뒤(모집 글 · 확정된 파티 · 친구 · 차단 · 신고)** 와 **방 안의 일**을 맡는다. 자동 매칭이 **기본 경로**, 파티 모집 게시판이 **두 번째 경로**다(D-11). 오래 남는 것은 PostgreSQL, 금방 사라지는 것(방에 누가 있나)은 Redis 다.

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (public 하나)
                                 │  ▲
                                 │  └── 읽기만 ◀── Redis qm:party:{partyId} ◀── matching 이 쓴다 (매칭 파티의 방 · 팀원 카드 — D-42 · P-47)
                                 ├──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
                                 ├──── PUBLISH qm:pubsub:board (BOARD_CHANGED · {}) ──▶ Redis ──▶ notification ──SSE──▶ 모든 연결
                                 ├──── Lua 로 쓰고 읽는다 ──▶ Redis qm:room:* · qm:user:active-room:{userId}   (방 안의 일 — §3.3)
                                 ├──── EXISTS 만 ◀── Redis qm:user:active-request:{userId}   (matching 이 쓴다 — D-19)
                                 ├──── 쓴다 ──▶ Redis qm:user:block-rel:{userId} ──▶ matching 이 읽기만 (차단 관계 사본 — §3.7 · P-52)
                                 ├──── 읽기만 ◀── Redis qm:gameconfig:*                       (운영자가 seed 로 심는 공유 설정 — §3.6)
                                 └──── 인가 코드 흐름 ──▶ 카카오 · 디스코드 · 구글              (소셜 로그인 — 회원 번호와 닉네임만)
```

- 지원 게임은 **LoL · VALORANT · PUBG 셋뿐**(#8). **상대팀/VS 를 만들거나 보여 주지 않는다**(#9).
- 공개 사용자 탐색 · 길드 · 피드 · 팔로우 · 좋아요 · 공개 채팅방을 만들지 않는다(#14 · docs/00 §6).
  - **예외 — 파티 모집 게시판**(D-11). 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이다. 사람 검색 · 둘러보기는 여전히 금지. "공개 채팅방"은 모집과 무관한 잡담방을 뜻하고 모집 글에 딸린 방은 예외다.
- 프리미엄/과금 없음. 친구 / 차단 / 최근 함께한 사람 / 신고는 필수다(#13 · #14).
- **매칭 로직이 하나도 없다.** 매칭 · 제안 · 수락 · 확정은 `matching` 의 일이다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 출처 |
|---|---|
| **계정** — 가입 · 로그인은 **소셜로만**(카카오 · 디스코드 · 구글 — P-24 · P-33. 직접 가입 · 비밀번호 · `loginId` 없음). 처음 온 사람은 **닉네임만** 정한다(`/social/pending` → `POST /social/signup {nickname}`). **소셜 계정 잇기 · 끊기**(로그인한 채 다른 제공자로 오면 같은 사용자에 잇는다 · `DELETE /api/v1/users/me/social/{provider}` · 마지막 하나는 409 `LAST_SOCIAL_IDENTITY` — P-27). 로그아웃 · 기본 프로필. **회원 탈퇴** `DELETE /api/v1/auth/account`(2026-10-02 소유자 결정 · P-48 — 그 사람의 데이터를 지체 없이 전부 지우고 **확정된 파티 기록만 남긴다**(작성자 칸이 빈다 — §3.5 V9) · 방 안이면 평소 나가기로 먼저 나간다 · 매칭 대기 중이면 409 `ALREADY_QUEUED` · Redis 를 못 읽으면 503 · 204 + 쿠키 둘 제거 · **실려 온 refresh 를 Redis 에서 지운다**(그래서 `/api/v1/auth` 아래다 — 처음의 `DELETE /api/v1/users/me` 는 405 · `/api/v1/auth/**` 가운데 이것만 access 쿠키 필수 — §5) · 제공자 쪽 unlink 없음 · 순서와 감수는 Claude 세부 — `account/service/AccountDeletionService`). **토큰 발급** — access 는 쿠키의 RS256 JWT(15분), refresh 는 Redis 의 불투명 UUID(7일). 개인 키는 이 앱만(§5.1). **식별자는 사용자 번호 `userId`(bigint) 하나**, 보여 주는 이름은 닉네임 하나(§3.5). **개발용 로그인**(임시 — §5.1 끝 · P-34) | docs/00 §5 · #16 · D-14 · 계약 "계정" · "소셜 로그인" |
| **게임 프로필** — 게임 계정 + 읽기 전용 `verified` · `stats`(전적 스냅숏). 받는 칸이 게임마다 다르다 — **LoL `{gameNickname}`**(이름#태그 · 티어 · 전적은 Riot 에서 · `tier` · `server` 를 보내면 400 — P-26) · **VALORANT `{gameNickname, tier?}`**(자기신고) · **PUBG `{gameNickname, server}`**(`server` 필수 · `tier` 를 보내면 400 · 티어 · 전적은 PUBG API 에서 — P-36). **티어는 사다리마다 따로 — 응답 `tiers`**(LoL `SOLO` · `FLEX` / VALORANT `COMPETITIVE` / PUBG `RANKED` — P-36). **주 포지션은 어느 게임에도 없다**(`mainPosition` 에 값이 있으면 400 `VALIDATION_FAILED` — `@Null` 이라 `null` 은 통과 · 조용히 버리지 않는다 — P-35). LoL · PUBG 는 저장 전에 **동기로** 긁고 실패하면 저장하지 않는다. **로그인 · 재발급 때 뒤에서 다시 받는다** — 마지막으로 받은 뒤 1시간이 지난 LoL · PUBG 계정만 · 응답은 기다리지 않고 실패시키지도 않는다(P-42 — 사용자가 누르던 **전적 갱신** `POST …/game-accounts/{game}/refresh`(P-17)는 없어졌다 · 그 경로는 404). 세부는 §7 "게임 계정 연동". `tier` 는 gameconfig 사다리에 있는 이름이어야 한다(§3.6) | 계약 "게임 프로필" · "전적을 긁는 것" |
| **파티** — 확정된 파티와 파티원의 기록 · **빠른매치 파티의 팀원 카드** `GET /api/v1/match-parties/{partyId}/members`(P-47 — 파티원끼리만 · 번호 조회 없음). **게시판 파티**(`source='BOARD'`)는 방장 확정이 만든다. **자동 매칭 파티**(`source='MATCH'` · `match_party_id = partyId`)는 **`POST /api/v1/match-parties/{partyId}/room`** 이 `matching` 의 파티 HASH 를 읽어 만든다(D-42 · P-30). **파티가 닫히면 파티원끼리 `recent_players` 에 적는다**(P-25 — §3.3 "파티 닫힘"). 큐 · outbox 없음(§3.4) | #21 · D-13 · **D-42** · 계약 "방장 확정의 기록" · "파티 닫힘" · "자동 매칭 파티의 방" |
| **파티 모집 게시판** — 글 쓰기(**방을 같이 만든다** · 고치기는 없다 — P-45) · 지우기(만료로 바꾸고 방도 닫는다) · 목록 · 단건 · 입장의 글 검사 · 방장 확정의 기록 · **차단 거르기(방 안의 누구와든 — D-20)** · 목록의 한 줄 조립(방 안 인원 · 카드 · 게임별 정보) · **게시판 방 먼저 합류** `POST /api/v1/posts/auto-join`(P-28). 규칙은 §3.3 · §7.1 | D-11 · D-16 · D-20 · D-21 · 계약 "모집 글 · 목록" · P-22 |
| **방 안의 일**(`room` 패키지) — 입장 · 나가기 · 강퇴(10분 재입장 금지 — P-32) · 방장 확정 · 접속 확인 · 방장 이탈 감지 · 방장 승계(확정한 방 — D-23) · 방 안 사람 목록 · 내 방 찾기 · 정원 검사 · **참가할 때 고르는 포지션**(P-44) · 입장 표시 키 · 방 알림(`ROOM_*`) · 시그널(`WEBRTC_SIGNAL`). Redis 에만 있고 **Lua 를 부르는 서비스로만 바꾼다**(§3.3) | D-9 · D-19 ~ D-23(D-33 이 개정) · 계약 "방" |
| **소셜** — 친구(요청 · 수락 · 거절 · 거두기 · 목록 · 끊기) · 차단/해제 · 신고(접수만) · 최근 함께한 사람(읽기 — 파티 닫힘이 채운다). **`blocks` 의 주인.** 차단은 DB 에 저장하고 **같은 트랜잭션에서 Redis 의 차단 관계 사본 `qm:user:block-rel:{userId}` 에도 적는다**(2026-10-02 소유자 결정 · P-52 · D-57 — `matching` 이 그것을 읽고 DB 는 읽지 않는다 · §3.7). `BlockChanged.fifo` 는 없다. 친구 요청은 상대의 사용자 번호를 알아야 한다 — **검색 API 없음** | docs/00 §5 · D-1 · D-2 · D-12 · 계약 "차단" · "친구 · 신고 · 최근 함께한 사람" |
| **알림 발행** — `FRIEND_REQUEST_RECEIVED` · `FRIEND_REQUEST_ACCEPTED`(§3.2) · `ROOM_*` 5종 · `WEBRTC_SIGNAL` · 게시판 채널 `BOARD_CHANGED`. **`PARTY_*` 는 두지 않는다**(P-31 · D-44) | events.md · D-20 ~ D-22 · 계약 "이 앱이 내는 알림" · "방" 의 "알림" |

platform 소관 자원(openapi.yaml 머리말): `auth` `users` `parties` `friends` `blocks` `recent-players` `reports`. 경로와 스키마는 `contracts/platform-api.md` 에 있다(글은 `posts`, 친구 요청은 `friend-requests`. 확정된 파티를 조회하는 `parties` 경로는 두지 않는다 — P-31). 머리말의 `reservations` 는 D-15 로 `app:reservation` 의 것이다.

| 안 한다 | 왜 / 누가 |
|---|---|
| 매칭 · 제안 · 수락 · 확정, 매칭 Redis 키(`qm:party:*` `qm:user:*` `qm:proposal:*` `qm:lock:*`) 접근. **"한 번에 하나만"은 키 둘로 지킨다** — 활성 요청 키 `qm:user:active-request:{userId}` 는 `matching` 만 쓰고 이 앱은 `EXISTS` 만, 입장 표시 키 `qm:user:active-room:{userId}` 는 이 앱만 쓰고 `matching` 은 `EXISTS` 만(D-19). **예외는 둘** — ① `qm:gameconfig:*` 읽기(D-29 — §3.6) ② `qm:party:{partyId}` 읽기(`HGETALL` · `HEXISTS` 만 · 제안 중에도 — P-47 · 쓰지도 지우지도 `EXPIRE` 도 안 한다 — D-42). `qm:user:*` · `qm:proposal:*` · `qm:lock:*` 에는 예외가 없다(**`qm:user:block-rel:*` 는 `matching` 의 키가 아니라 이 앱의 키다** — 이 앱이 쓰고 `matching` 이 읽기만 한다 · §3.7 · P-52) | `matching` 의 일(#27) |
| 브라우저 연결 보유 — `SseEmitter` / WebSocket | `notification` 의 일. 이 앱은 stateless REST(#15). WebSocket 은 어디에도 없다(D-9) |
| **예약 전부** — 예약 REST · 짝 찾기 · `RESERVATION_*` | `app:reservation`(Lambda — D-15) |
| TURN credential 발급, 음성 · 텍스트 중계/저장 | TURN 은 `app:realtime`(#25). 음성 · 텍스트는 브라우저 직결(#6) |
| **gameconfig 모듈** — 모드 설정을 정하고 · 심고 · 해석하는 것 | `app:matching` 의 모듈(#15). **이 앱은 값을 읽기만 한다**(§3.6) |

## 3. 계약

### 3.1 Contract first

- 계약 원본은 **queueMate 본 저장소(`feature/frontend`)의 `contracts/`** 이고 이 컴퓨터에 없다. `matching/contracts/` 는 matching 부분의 발췌라 **platform 엔드포인트가 빠져 있다.**
- **경로 · 스키마 · 에러 코드 · payload 필드를 지어내지 마라.** 묻는다. 원본을 받아 올 수 있으면 그것이 먼저다. 이 폴더에서 정한 것은 **`contracts/platform-api.md`** 에 적고 "원본에 올려야 할 것" 표(P-항목)를 남긴다. 원본에 platform 엔드포인트가 이미 있으면 그쪽과 맞춘다(P-1).
- **코드와 계약은 같이 바뀐다** — 경로 · 스키마 · 에러 코드 · 클레임을 바꾸면 같은 작업에서 계약을 고친다(커밋은 나눈다 — §8). 거기 없는 것을 새로 정할 때는 **먼저 묻는다.**
- **에러 본문은 `matching` 과 같다** — `{"code", "message", "details": [문자열]}`. 방의 요청도 같은 코드 한 벌이다(`VALIDATION_FAILED` · `ROOM_STATE_UNAVAILABLE` 등).

### 3.2 알림 발행 — `matching` 의 `PushPublisher` 와 같은 방식

Redis `PUBLISH qm:pubsub:push:{userId}` 에 **JSON 문자열 하나**. `notification` 은 열어 보지 않고 SSE `data:` 에 싣는다 — 새 종류를 더해도 `notification` 은 재배포하지 않는다.

| 항목 | 값 | 원본(`matching`) |
|---|---|---|
| 채널 | `qm:pubsub:push:{userId}` — `{userId}` 는 사용자 번호(`qm:pubsub:push:42`) | `redisKeys/SharedKeys.PUSH_CHANNEL_PREFIX` |
| 봉투 | `{type, eventId, occurredAt, payload}` 네 칸 고정 | `notification/PushPublisher.Envelope` · events.md |
| `eventId` | 매번 새 UUID(SSE `id:`) | `PushPublisher#publish()` |
| `occurredAt` | ISO-8601 UTC 밀리초 | 같음 |
| `payload` | 객체. 없어도 `{}` | 같음 |

- **채널 접두사는 이 서비스가 정하지 않는다** — 원본은 `matching` 의 `SharedKeys`. 이 앱의 사본은 **`common/push/PushChannels.PUSH_CHANNEL_PREFIX` 한 곳**(봉투 `PushEnvelope` · `type` enum `PushEventType` · 발행 `PushPublisher`). 어긋나면 컴파일 · 테스트가 통과한 채 알림이 전부 끊긴다.
- **발행 실패가 본 작업을 뒤집으면 안 된다** — 발행은 예외를 밖으로 내지 않는다. 대가로 조용하니 **구독해서 확인하는 테스트**를 둔다.
- 이 앱이 정한 알림 — `FRIEND_REQUEST_RECEIVED`(`{requestId, fromUserId}`) · `FRIEND_REQUEST_ACCEPTED`(`{requestId, userId}`). 커밋 뒤 발행 · 닉네임 같은 데이터는 싣지 않는다(P-9 — 원본 events.md 의 `FRIEND_*` 이름과 맞춰야 한다). 거절 · 거두기 · 끊기 · 차단은 알리지 않는다. **`PARTY_*` 는 두지 않는다**(P-31).
- 알림은 "다시 조회하라"는 신호다 — 알림이 가리키는 상태는 **REST 로 조회할 수 있어야 한다.**

**게시판 채널**(D-20 · D-22 · 계약 "모집 글 · 목록" · "방" 의 "게시판 채널 신호").
- 채널 **`qm:pubsub:board`**(게임을 구분하지 않는 하나) · `type` **`BOARD_CHANGED`** · `payload` **`{}`**. 발행기는 `party/board/BoardSignalPublisher` 하나다.
- 글 쪽은 글이 생기거나 · 만료되거나 · 확정되거나 · 그 파티가 닫힐 때(P-46) **커밋 뒤 한 번**(목록 조회가 옮겨 적을 때도), 방 쪽은 **방의 인원이 바뀔 때** 발행한다. 자동 매칭 파티의 방은 내지 않는다.
- `notification` 이 **모든 SSE 연결에** 흘리고(`topics` 없음) **클라이언트가 거른다** — 게시판 페이지를 보고 있으면 목록을 `GET` 으로 다시 받고, 아니면 무시한다. **묶는 법**(2026-09-30 소유자 결정 — 프런트 `rooms/useRoomData.ts`) — 처음 온 신호부터 **1.5초 ± 0.3초**(창마다 무작위로 뽑아 모든 브라우저가 한꺼번에 다시 받지 않게) 창에 한 번 · 뒤에 온 신호로 창을 늘리지 않는다(늘리면 신호가 끊이지 않는 동안 목록이 안 바뀐다).
- **신호에 데이터를 싣지 않는다**(`roomId` · `game` 도) — 방송은 사람별로 거를 수 없어 차단이 뚫린다.
- 채널 이름은 **두 앱이 각자 같은 값을 적었다** — 이 앱 `party/board/BoardChannels.BOARD_CHANNEL`, `notification` `redisKeys/BoardChannels.BOARD_CHANNEL`. **두 값이 같아야 한다.** 구독 테스트 `BoardSignalTest`. 원본 상수를 둘 곳은 미정(§7.1).

### 3.3 방 안의 일(`room`)과 글(`party`)이 맞물리는 법 (P-22 · 계약 "모집 글 · 목록" · "방")

> 합치기 · 입장 경로 안의 글 검사 · 글 쓰기가 방을 만든다는 소유자 결정이고, 창구 이름 · 검사 순서 · 자가 치유 · 에러 코드 통일 · 트랜잭션 안 Lua 예외는 **Claude 가 정했다(검토 항목)**.

**창구.** 두 패키지는 서비스 메서드로 맞물린다. `party` → `room`: `RoomService#create`(글 쓰기) · `#confirm`(방장 확정) · `#states`(방 안 읽기) · `#leave(roomId, userId, whenClosed)` · `#noAutoJoinRooms`. `room` → `party`: **`party/service/PostEntryGate#check`** 하나(입장 검사 · 정원을 돌려준다). `PostEntryGate` 는 `PostService` 를 물지 않는 따로 선 빈이다(빈 순환 방지). **`RoomService` 는 `party` 를 부르지 않는다.** **`party` 는 방 키를 Redis 로 직접 읽거나 쓰지 않는다.**

- **`roomId`** — 게시판 방은 **글의 id**(`recruit_posts.id` bigint — 방 키에 십진 문자열, P-4). **자동 매칭 파티의 방은 `partyId`(UUID) 그대로**(P-30). 방 키 · 방의 요청(접속 확인 · 나가기 · 강퇴 · 목록 · 시그널 · 내 방 찾기)은 둘이 같다.
- **글 쓰기 = 방 만들기.** `POST /api/v1/posts` 가 트랜잭션 안에서 글을 INSERT 하고 **커밋 전에** 방 만들기 Lua 를 부른 뒤 커밋한다(`PostStore#create`). 쓴 사람이 방장이고 곧바로 방에 있다. Lua 거절 → 409 `ALREADY_QUEUED` · `IN_OTHER_ROOM`("이미 방에 있으면 글을 못 쓴다") · `ROOM_ALREADY_EXISTS`, Redis 장애 → 503 `ROOM_STATE_UNAVAILABLE` — **전부 글도 되돌린다.** Lua 성공 뒤 커밋 실패로 남는 고아 방은 감수한다(수명으로 죽고 글이 없어 걸리지 않는다). **방 만들기 HTTP 요청은 없다.**
- **자동 매칭 파티의 방 — `POST /api/v1/match-parties/{partyId}/room`**(D-42 위의 Claude 세부 · P-30 · 계약 "자동 매칭 파티의 방"). 프런트가 `MATCH_CONFIRMED {partyId}` 를 받아 부르면 "없으면 만들고 있으면 들여보낸다"(201/200 `{roomId}`).
  - 순서 — **이미 이 방에 있는 사람은 입장 표시 키로 먼저 판정해 200**(HASH 가 수명으로 사라진 뒤에도). HASH 는 방에 없는 사람의 자격에만 쓴다 — `status == CONFIRMED` · `HEXISTS member:{userId}`. 에러 404 `MATCH_PARTY_NOT_FOUND` · 403 `NOT_PARTY_MEMBER` · 409 `IN_OTHER_ROOM` · 409 `ROOM_FULL` · 503 `ROOM_STATE_UNAVAILABLE`(**HASH 를 못 읽으면 fail-closed**).
  - **처음부터 확정된 방**(`:confirmed` 를 같이 쓴다 — D-23 승계가 걸린다) · **처음 부른 사람이 방장** · 정원은 HASH 의 `target` · 게시판 신호 없음.
  - 전용 Lua **`enter-match-room.lua` 는 활성 요청 키를 보지 않는다**(확정 뒤 60초 `status=PARTY` 로 남는 그 키가 곧 이 파티다). 입장 금지 목록(`no-entry`)도 보지 않는다 — 막을지는 미정(§7.1).
  - **DB 먼저 커밋, Lua 는 트랜잭션 밖** — `parties`(`source='MATCH'` · `match_party_id`) · `party_members`(HASH 의 `member:*` 가운데 `users` 에 있는 번호 · `is_host` 는 처음 부른 사람). `ON CONFLICT (match_party_id) DO NOTHING` — Lua 가 실패해 파티 줄만 남아도 다음 호출이 방을 만든다.
  - 입장 표시 키는 **부른 사람에게만** 찍힌다(D-42 4번은 "전원에게" — 소유자 검토 항목). 감수 — 확정 뒤 60초 안에 입장 표시 키가 찍히지 않은 사람은 그 사이 새 매칭을 걸 수 있다.
  - HASH 의 `playPurpose` 는 **`parties` 에 담지 않는다**(칸을 두지 않는다 — 소유자 결정). `matching` 의 `PlayPurpose` 는 그쪽 것이라 건드리지 않는다.
- **"트랜잭션 안에서 Redis 를 기다리지 않는다"의 예외는 둘** — 글 쓰기와 방장 확정(Lua 한 번). 그 밖(목록 · 단건 · 입장 검사 · 자동 매칭 방)은 트랜잭션 밖이다(`PostService` 와 `PostStore` 를 나눈 이유).
- **입장**(`POST /api/v1/rooms/{roomId}/members`) — ① **글의 검사**(`PostEntryGate#check`): 글이 없거나 차단으로 숨겨지면 **404 `POST_NOT_FOUND`(상태보다 먼저)** → 모집 중이 아니면 409 `POST_NOT_RECRUITING` → 방 안을 못 읽으면 503 → ② **방의 Lua**(`enter-room.lua`): **입장 금지 목록을 맨 먼저**(강퇴 10분 — 403 `KICKED_RECENTLY`) → `ALREADY_QUEUED` → `ROOM_NOT_FOUND` → 이미 들어와 있음 200 · `IN_OTHER_ROOM` → `ROOM_CONFIRMED` → `ROOM_FULL`(글의 정원 — `ARGV[3]`) → **포지션**(P-44 — 아래) → 쓴다.
  **포지션**(2026-09-30 ~ 10-01 **소유자 결정** · P-44 · 계약 "방" 의 "입장") — 참가할 때 **쿼리 `?position=`** 으로 **남은 찾는 포지션(`:needs`) 하나**를 고르고 **들어온 뒤에는 못 바꾼다**(재입장 200 은 포지션을 보지 않는다). **포지션 방**(글의 `wantedPositions` 가 비지 않았다 — `PostEntryGate#check` 가 정원과 함께 `ARGV[7]` 로 · 2026-10-01 소유자 결정)이면 필수 · `:needs` 에 남은 것이어야 한다 — 아니면(남이 고른 것 · 다 골라 `:needs` 가 지워진 것도) -6 → 400 `VALIDATION_FAILED` `"position: …"` · 포지션이 없는 방에 주면 -6 → 400(조용히 버리지 않는다). `SREM` 은 포지션 방에서만. 409 `POSITION_TAKEN`(-8)은 더는 나지 않는다. "남았나 보고 빼기" 가 한 스크립트라 같은 포지션을 동시에 고르면 한 명만 들어온다. `:needs` 의 유무로 가르던 구멍 둘을 막았다(2026-10-01). (-6 의 HTTP · `""` · 글귀 · 순서는 Claude 세부.) 차단 대조는 **방장 + 그 순간 방 안 전원**(확정된 글이면 파티원 — P-40), 판정은 목록과 같은 `PostService#isHidden`. 이미 그 방에 있는 사람은 ① 을 통과한다. **① 과 ② 사이는 원자적이지 않다**(밀리초 경쟁 — §7.1).
- **방장 확정은 한 요청** — `POST /api/v1/rooms/{roomId}/confirm` 이 글의 줄을 `FOR UPDATE` → 확정 Lua(방장만 · 2명 이상 · **되돌릴 수 없다**) → 같은 트랜잭션에서 글 `CONFIRMED` · `parties`(`BOARD` · `post_id`) · `party_members`(Lua 가 돌려준 확정 순간의 전원)를 적는다(`PostStore#confirmRoom`). 멱등은 **`UNIQUE (post_id)` + `ON CONFLICT DO NOTHING`**(`PartyRecordRepository`)과 조건부 UPDATE. `is_host` 는 **글의 `hostId`**(D-23). 만료된 글은 409 `POST_NOT_RECRUITING`. 확정 뒤 입장은 Lua 가 409 `ROOM_CONFIRMED`.
- **자가 치유** — Lua 성공 뒤 커밋이 실패해 "확정된 방인데 글은 모집 중"이 남으면, **목록 · 단건이 확정 표시 키를 보고 그 자리에서 같은 기록을 한다.** 같은 확정을 다시 눌러도 기록한다. 그때의 멤버 HASH 가 확정 순간과 다를 수 있음은 감수한다.
- **방과 글은 같이 산다**(소유자 결정) — 확정 전에는 **방장이 나가면 글도 `EXPIRED`**(`leave` 의 콜백 → `PostLifecycle#expireByRoomClosed`), **글을 지우면 방도 닫힌다**(`ROOM_CLOSED` · 전원의 입장 표시 키 삭제. 만료가 먼저 · 방 닫기가 뒤 · 방 닫기가 실패해도 204). 확정 뒤에는 글 `CONFIRMED` 고정(`DELETE` 는 409 `POST_CONFIRMED`) · 방은 승계.
- **만료 판정 — 모집 중인 글에 방장 키가 없으면 만료다**(말없이 사라진 방장의 받침). 확정 표시 키가 있으면 만료가 아니라 확정으로 기록한다. **확정된 글은 방장 키가 없어도 만료시키지 않는다**(승계 중일 수 있다). **Redis 를 못 읽으면 판정하지 않는다** — 못 읽은 것을 "방이 없다"로 읽지 마라.
- **파티 닫힘**(P-25 · 계약 "방" 의 "파티 닫힘") — 확정된 방이 없어지면 `parties.status='CLOSED'` · `closed_at` 을 적고 **그 순간 `party_members` 끼리 서로를 `recent_players` 에 UPSERT** 한다. 길은 셋 — ① 마지막 사람이 나가 방 키가 지워질 때(나가기 콜백 — 게시판은 `post_id`, 자동 매칭은 `match_party_id` 로 찾는다) ② 목록 · 단건이 방 키를 읽다 발견 ③ **목록 GET(`?game=`)이 그 게임의 `ACTIVE` 자동 매칭 파티를 `match_party_id` 로 골라 방 키를 읽고 닫는다**(한 번에 200개 · 단건은 안 본다 · 못 읽으면 안 닫는다 — `PostService#closeVanishedMatchParties`). **방장 키 · 멤버 HASH · 확정 표시 키가 전부 없을 때만** 닫는다(방장 키만 없으면 승계 중). `ACTIVE` 만 바꾸는 조건부 UPDATE 라 겹쳐도 한 번. 글은 `CONFIRMED` 그대로(`closed` 가 `true` · 게시판 신호 한 번 — P-46) · 개인 알림 없음 · SQS 없음.
- **방 안을 읽는 창구는 `RoomService#states` 하나** — 파이프라인 한 번으로 글마다 `EXISTS host` · `HGETALL members`(사람과 고른 포지션 — `RoomState.positions` → 카드 `position` · P-44) · `EXISTS confirmed`. 쓰지 않고 방장 키의 값은 읽지 않는다. 못 읽으면 `RoomStateUnavailableException` — **fail-open(목록 · 단건 — 방 정보를 비운 채 글만) / fail-closed(입장 — 503)는 부르는 쪽이 정한다.**
- **목록 조회(GET)가 글을 만료 · 확정으로, 파티를 닫힘으로 옮겨 적는다** — 전부 조건부 UPDATE 라 멱등하다(`PostService` 머리 주석의 "허용된 부수 효과"). §5.1 (다)의 "상태를 바꾸는 GET 금지"와의 관계는 **소유자 검토 항목**(`START_HERE.md` §4). 입장 검사는 옮겨 적지 않는다.
- **방을 바꾸려면 Lua 를 부르는 서비스(`RoomService` · `RoomMemberService`)를 거친다** — 정원 · 포지션 · 입장 표시 키 · 활성 요청 키의 `EXISTS` 가 한 스크립트에 있다. 맨손 `HSET` · `SET` 금지. 거절되면 아무것도 쓰이지 않는다. Redis 장애는 부르는 자리에서 503 으로(`room/service/RoomRedis`).
- **정원 — 게시판 방은 그 글의 모드의 인원**(P-41 · 계약 "정원"). 글 쓰기가 gameconfig `targetPartySize` 를 **트랜잭션 밖에서** 읽어(`PostService#capacityOf` ← `GameConfigReader#partySize`) `recruit_posts.capacity` 에 적고, 입장은 `PostEntryGate#check` 가 돌려준 정원을 Lua `ARGV[3]` 로 넘긴다. 글 한 줄의 `capacity` · `full`, 게시판 방 먼저 합류도 같은 값. **모르면 5**(못 읽음 · 안 심김 · V8 전의 글) · 5 초과면 5. 방 키에는 정원을 적지 않는다. 자동 매칭 방은 HASH 의 `target`.

**방 키** — 원본 상수 **`room/redisKeys/RoomKeys.java` 하나.** 계약은 "방" 의 "Redis 키".

| 키 | 자료형 | 값 | 뜻 |
|---|---|---|---|
| `qm:room:{roomId}:host` | STRING | 방장 `userId` | **있다 = 방이 있다.** 확정한 방에서는 값이 바뀔 수 있다(D-23) |
| `qm:room:{roomId}:members` | **HASH**(P-44) | 필드 `userId`(방장 포함) → 값 **참가할 때 고른 포지션**(방장은 `hostPosition` · 없으면 `""`) | `HLEN` 이 인원. 들어온 뒤 값은 안 바뀐다. 나가기 · 강퇴 · 유령 빼기가 필드째 지우고 포지션을 `:needs` 에 돌려놓는다 |
| `qm:room:{roomId}:needs` | SET | 처음은 글의 `wantedPositions` | **남은 찾는 포지션**(P-44). 글 쓰기가 채운다(없으면 안 만든다) · 입장이 `SREM` · 일반 멤버의 나가기 · 강퇴가 돌려놓는다(수명 등은 Claude 세부) · 다 고르면 지워진다 · **남은 목록일 뿐 — 포지션 방인지는 글이 가른다**(위 "입장") · 방이 없어질 때 같이 지운다 |
| `qm:room:{roomId}:confirmed` | STRING | `roomId` | **있다 = 방장이 확정한 방**(D-21) |
| `qm:user:active-room:{userId}` | STRING | `roomId` | **입장 표시 키 — `matching` 과의 약속**(D-19) |
| `qm:room:no-entry:{userId}` | ZSET | 원소 `roomId` · score 풀리는 시각(epoch ms) | **입장 금지 목록**(P-32). 강퇴(`kick-room.lua`)가 쓰고 `enter-room.lua` 가 맨 먼저 읽는다. 스스로 나간 사람은 들지 않는다 |
| `qm:room:no-auto-join:{userId}` | ZSET | 위와 같다 | **자동 합류 건너뛰기**(P-32). 강퇴 · 나가기(일반 멤버의 나가기)가 쓰고 `RoomService#noAutoJoinRooms` 로 읽는다. 사람의 키라 수명 600초(상수)로 사라진다 |

- 두 ZSET 은 **강퇴 · 나가기 스크립트가 방에서 빼는 것과 한 번에 적는다.** 10분은 코드 상수다.
- **`qm:party:{partyId}` 는 이 표에 없다 — `matching` 의 키이고 읽기만 한다**(제안 중에도 · `member:*` 이름도 약속 · 테스트 없음 — P-47 팀원 카드). 필드 계약(`status` · `confirmedAt` · `game` · `modeKey` · `voicePreference` · `playPurpose` · `target` · `member:{userId}` · `tierLo`/`tierHi` · 수명 600초)은 계약 "자동 매칭 파티의 방" 의 "파티 HASH 의 계약". 접두사 사본 `party/match/MatchPartyKeys.PARTY_PREFIX`(원본 `SharedKeys.PARTY_PREFIX` — `SharedPrefixTest` 가 대조한다). **필드 이름은 테스트가 없다.**
- **D-19 약속** — 활성 요청 키는 `matching` 이 쓰고 이 앱은 `EXISTS` 만(글 쓰기 · 입장이 409 `ALREADY_QUEUED`). 입장 표시 키는 이 앱이 쓰고 `matching` 은 `EXISTS` 만(매칭 요청이 409 `IN_ROOM`). 키 이름 원본은 `matching` 의 `SharedKeys`(사본 `room/redisKeys/SharedKeys`). 활성 요청 키는 영구적이지 않다 — 대기 요청은 heartbeat 가 끊기면 90초 안에 취소되고(D-43), `status=PARTY` 는 60초 뒤 만료된다(D-42).
- **수명** — 방 키(`host` · `members` · `needs` · `confirmed` · 입장 표시 키)는 600초(설정 `platform.room.ttl-seconds` · `ROOM_TTL_SECONDS`)이고 브라우저의 1분 접속 확인(`POST …/heartbeat`)이 늘린다. 두 ZSET(`no-entry` · `no-auto-join`)은 설정이 아니라 **상수** 600초다. **확정하지 않은 방의 수명은 방장의 신호만 늘린다** — 방장이 나가든 사라지든 방을 통째로 없앤다(D-21). 대가: 방장이 말없이 사라진 방은 최대 10분 살아 보인다.
- **방장 승계**(D-23) — 확정한 방은 방장이 나가면 남은 멤버가 넘겨받는다(나가기 시 그 자리에서, 사라지면 키 만료 뒤 처음 접속 확인한 멤버가. 확정한 방은 일반 멤버의 접속 확인도 수명을 늘린다). 넘겨받을 사람이 없을 때만 방 키(방장 · 멤버 HASH · 확정 표시 · 찾는 포지션 SET)가 함께 없어진다. 나간 방장 포지션은 `:needs` 에 돌려놓지 않는다(유령 빼기도 — Claude 세부). 새 방장은 알림에 싣지 않는다.
- 멤버 HASH 에 말없이 사라진 사람이 잠깐 남을 수 있다 — 인원 · 카드가 부풀고, 그 사람 때문에 방이 잠깐 숨겨질 수 있다(D-20 "감수하는 것").
- **숫자가 아닌 멤버 필드는 읽는 자리에서 건너뛴다**(WARN · `room/domain/RoomMemberIds`). 숫자이지만 가입하지 않은 번호는 카드에 `nickname: null` · `profile: null` 로 남기고, **파티원으로는 기록하지 않는다**(`INSERT … SELECT … WHERE EXISTS (users)`).
- 방 밖에서 방 안을 보는 공개 창구는 **게시판 목록**이다. `GET /api/v1/rooms/{roomId}/members` 는 방 안 사람만(403 `NOT_IN_ROOM` · `members` 는 `[{userId, position}]`).
- **방이 내는 것** — `ROOM_MEMBER_ENTERED` · `ROOM_MEMBER_LEFT` · `ROOM_CLOSED` · `ROOM_MEMBER_KICKED` · `ROOM_CONFIRMED` · `WEBRTC_SIGNAL`(`room/service/RoomNotifier` → `PushPublisher`). `payload` 는 계약 "방" 의 "알림".
- **어긋날 수 있는 이름** — D-19 의 두 키 이름 · 파티 HASH 의 접두사와 필드 · gameconfig 접두사(fail-open 이라 더 조용하다 — §3.6).

### 3.4 서버 간 이벤트 — 큐가 없다 (D-42)

**이 앱이 내거나 받는 큐는 0개다.** 확정된 파티는 `matching` 이 Redis 파티 HASH 에 자기완결로 적고 이 앱이 읽는다(§3.3).

| 큐 | 지위 | 왜 |
|---|---|---|
| ~~`ProposalConfirmed.fifo`~~ | 만들지 않는다(D-42) | 이 앱이 `qm:party:{partyId}` 를 읽는다. 파티당 한 번 나가는 이벤트라 지킬 순서가 없다 |
| ~~`PartyClosed.fifo`~~ | 만들지 않는다(D-36 · D-42) | 게시판 · 자동 매칭 파티 모두 같은 앱 안에서 닫는다(§3.3) |
| ~~`BlockChanged.fifo`~~ | 만들지 않는다(D-12) | ~~`matching` 이 배정 때 `blocks` 를 직접 읽는다(D-1 · D-2 · D-41)~~ → 2026-10-02 부터 이 앱이 차단 · 해제의 트랜잭션에서 적는 **Redis 차단 관계 사본**을 `matching` 의 합류 스크립트가 읽는다(D-57 · P-52 · §3.7) — 큐가 아니다 |

- **outbox · relay · AWS SDK 를 들이지 않는다.** Kafka/RabbitMQ/Redis Streams 로 바꾸지 마라(#21 · #26). `backend/build.gradle` 의 "SQS 를 넣을 자리" 주석은 낡았다.
- 다른 앱에 시킬 일이 **새로** 생기면 원안은 transactional outbox → SQS FIFO(at-least-once · 멱등 소비 · DLQ)이고, **먼저 묻는다.** 동기 HTTP 로 잇지 않는다.
- 감수 — 확정 뒤 10분 안에 아무도 부르지 않으면 파티가 증발한다. Redis 장애면 파티를 못 만든다(503 — fail-closed).

### 3.5 DB — 스키마 하나(`public`)와 `blocks` (P-23 · D-34)

- **스키마는 `public` 하나, 테이블 13개** — `users` · `game_accounts` · `game_account_stats` · `social_identities` · `blocks` · `friend_requests` · `friendships` · `reports` · `recent_players` · `recruit_posts` · `recruit_post_positions` · `parties` · `party_members`. **테이블 사이의 JOIN · FK 를 허용한다.**
- **FK** — 사용자 번호 칸 전부에 `users(id)` FK `ON DELETE CASCADE`(`blocks.blocker_id/blocked_id` · `friend_requests.requester_id/receiver_id` · `friendships.user_low_id/user_high_id` · `reports.reporter_id/target_user_id` · `recent_players.user_id/other_user_id` · `party_members.user_id`). **`recruit_posts.host_id` 만 `ON DELETE SET NULL`** 이다(**V9** · 2026-10-02 소유자 결정 — 회원 탈퇴에도 **확정된 파티 기록은 남긴다** · P-48. `host_id` 는 NULL 허용이고 **방장이 비는 글은 확정된 글뿐** — CHECK `recruit_posts_host_id_check`(`host_id IS NOT NULL OR status = 'CONFIRMED'`). 모집 중 · 만료된 글은 탈퇴가 `users` 보다 먼저 같은 트랜잭션에서 지운다 — `PostStore#deleteUnconfirmedOf`). 그래서 **글을 읽는 쪽은 `hostId == null`(확정된 글)을 견뎌야 한다**(`RecruitPost#isHost` · 글 한 줄의 `hostId` · `host` 가 `null`). `recent_players.last_party_id → parties(id)` 는 `ON DELETE SET NULL`. `parties.post_id → recruit_posts(id)` 는 CASCADE(Claude 세부 — 앱이 글을 지우는 것은 탈퇴의 비확정 글뿐이고 비확정 글에는 파티가 없다. 운영에서 글을 손으로 지우면 그 글의 파티 기록도 딸려 지워진다). FK 이름은 `<table>_<column>_fkey`.
- **FK 위반의 번역** — 상대 사용자 칸은 404 `USER_NOT_FOUND`, **"나"의 칸**(`blocker_id` · `requester_id` · `reporter_id` · `host_id`)은 **401 `UNAUTHENTICATED`**(토큰은 멀쩡한데 사용자가 없다 — Claude 세부).
- **제약에 전부 이름을 붙였다** — 앱이 그 이름으로 에러 코드를 가른다(`users_nickname_key` → `NICKNAME_TAKEN` · 사용자 번호 FK → `USER_NOT_FOUND` 등 — `common/error/ConstraintViolations`). 이름을 바꾸면 상수도 바꾼다.
- **패키지 나누기는 그대로다**(§4). 창구는 JOIN 이 대신 못 하는 것만 남는다(예 — Redis 에서 온 id 목록으로 묻는 `BlockReader`).
- **롤 · `GRANT` 없음** — 앱 하나가 DB 계정 하나(`DB_USER` · `DB_PASSWORD`). 뷰(`shared_read.blocked_pairs`)도 만들지 않는다. 운영 계정은 미정(§7).
- `reservation` 테이블은 이 앱의 것이 아니다(D-15).
- **`matching` 은 2026-10-02 부터 DB 를 읽지 않는다**(D-57 · P-52 — 그 전에는 `blocks` 하나를 읽었다). 차단 관계는 이 앱이 적는 Redis 사본으로 본다(§3.7). `blocks(id bigint identity PK, blocker_id bigint, blocked_id bigint)` · `(blocker_id, blocked_id)` UNIQUE 는 이 앱이 건다. 방향이 있는 한 줄이고 **사본의 원본이다** — 재구성(§3.7)이 이 모양을 읽는다.
- **모든 PK 는 `bigint GENERATED ALWAYS AS IDENTITY`**(`@GeneratedValue(strategy = IDENTITY)`). **`users.id` 가 `userId`** — JWT `sub`(`"42"`) · 알림 채널 · 방 키 · URL · 본문의 `userId` · 다른 테이블의 `*_id` 가 전부 이것. 닉네임(`users_nickname_key`)은 보여 주는 이름이고 사람을 가리키는 값으로 쓰지 않는다. 제공자의 회원 번호는 `social_identities` 에만 있다(P-11 · P-24). docs/WHY_POSTGRESQL 의 `uuid` 서술은 이 앱의 모양이 아니다(bigint).
- **원본은 마이그레이션** `backend/src/main/resources/db/migration/` — `V1__schema.sql`(스키마 · FK — 옛 V1~V8 을 합친 것) + **V2** `parties.match_party_id varchar(36) UNIQUE` + `CHECK ((source='MATCH') = (match_party_id IS NOT NULL))`(P-30) · **V3** `provider` CHECK 에 `GOOGLE` · `provider_user_id` 255(P-33) · **V4** `game_accounts.main_position` 삭제(P-35) · **V5** `game_accounts.tiers jsonb NOT NULL DEFAULT '{}'` + `jsonb_typeof = 'object'` CHECK, 옛 `tier` 를 옮기고(PUBG 는 버림) 삭제(P-36) · **V6** `recruit_posts.host_position varchar(20)`(P-38) · **V7** LoL 스냅숏 `detail.mostChampions` 를 숙련도만의 모양으로 옮긴다(데이터만 — P-39) · **V8** `recruit_posts.capacity smallint`(NULL 허용 · `2..5` CHECK — P-41) · **V9** `recruit_posts.host_id` NULL 허용 + FK `recruit_posts_host_id_fkey` 를 `ON DELETE SET NULL` 로 다시 건다 + CHECK `recruit_posts_host_id_check`(P-48 — 회원 탈퇴) · **V10** `recruit_posts.allow_auto_join boolean NOT NULL DEFAULT true`(P-50 — 빠른매치 입장 허용 / 금지 · 옛 글은 `true`). ERD(§10)가 어긋나면 마이그레이션이 맞다.
- **"이미 적용된 파일은 고치지 않는다"는 운영 DB 가 생긴 뒤부터 걸린다** — 그 뒤는 새 버전으로. 운영 전에 V1 을 또 갈아 끼울지는 그때 묻는다.
- 담긴 것의 요점(자세한 것은 파일) — `game_accounts`(`UNIQUE (user_id, game)` · `external_id` · `verified` · `server`(PUBG) · `tiers` — 키의 원본은 `account/domain/Game#tierLadders()` · 값이 있는 사다리만 적는다. 응답 `tiers` 는 그 게임의 사다리 키 전부이고 없으면 `null`) · `game_account_stats`(게임 계정과 1:1 · 세 게임이 한 테이블 · 공통 칸 `games` · `(wins IS NULL) = (losses IS NULL)` · 게임별 지표는 `detail` jsonb — P-12) · `social_identities`(PK `(provider, provider_user_id)`) · `friend_requests`(대기 중 요청은 방향마다 하나 — 부분 UNIQUE `WHERE status='PENDING'`) · `friendships`(PK `(user_low_id, user_high_id)`) · `reports`(UNIQUE 없음) · `recent_players`(PK `(user_id, other_user_id)`) · `recruit_posts`(`id` = `roomId` · "모집 중인 글은 한 사람에 하나" 부분 UNIQUE · 인덱스 `(game, id DESC)` · `capacity` · `host_position`) · `parties`(`post_id` UNIQUE · `match_party_id` UNIQUE · `status` · `closed_at`) · `party_members`.
- `refresh_tokens` 테이블 · outbox 테이블은 없다. `recruit_posts.mode` 는 `NULL` 허용 그대로(§3.6 미정).
- 옆 폴더 `matching/db-design/` 은 다른 설계의 흔적 — 근거로 쓰지 마라.

### 3.6 gameconfig 를 읽는다 (D-29 · P-16 · 계약 "gameconfig 를 읽는 것")

값의 원본은 **`matching/seed/gameconfig.redis`**, 이 앱은 **읽기만 한다.**

| 키 | 자료형 | 이 앱이 보는 법 |
|---|---|---|
| `qm:gameconfig:{GAME}:{MODE}` | HASH | `mode` 검증은 **`EXISTS`**(있다 = 그 모드가 있다). **게시판 방 먼저 합류**가 `tierRule` · `tierLadder` 를 `HMGET`(P-28 · P-36 — 모드 이름으로 사다리를 가르지 않는다. `tierLadder` 가 없으면 티어를 보는 글을 건너뛴다 + WARN). **모집 글의 정원**이 `targetPartySize` 를 `HGET`(P-41 · fail-open → 5). **모집 글의 방장 포지션**이 `positionUniqueness` 를 `HGET`(`"true"` 면 포지션이 있는 모드 — P-38 · fail-open). 그 밖의 필드는 읽지 않는다 |
| `qm:gameconfig:{GAME}:tier` | ZSET | `tier` 검증은 **`ZSCORE`**(`null` = 없는 티어). 게시판 방 먼저 합류는 score 도(`ZMSCORE`). 키의 `EXISTS` 가 "심겼는가"를 겸한다 |
| `qm:gameconfig:{GAME}:tier-range:{MODE}` | HASH | **게시판 방 먼저 합류만** — 내 티어 · 방장 티어의 줄(`MIN:MAX` · `SOLO_ONLY`)을 `HMGET` |

- 모드 목록 SET 은 없다(seed 가 일부러 없앴다) — 모드가 있는지는 HASH 의 `EXISTS` 가 답한다.
- **왜 §2 의 금지를 어기는 것이 아닌가** — gameconfig 는 사용자의 행동이 아니라 **운영자의 배포**로 바뀌는 공유 설정이고 쓰는 앱이 없다. (`qm:party:{partyId}` 는 이 근거가 아니라 소유자가 따로 정한 예외다 — D-42 · P-47.)
- **seed 를 심지 않는다 · `matching` 을 HTTP 로 부르지 않는다 · 값 목록을 상수로 베끼지 않는다.**
- **`mode` · `tier` 검증은 fail-open** — Redis 를 못 읽으면 검증만 건너뛰고 성공(WARN). **gameconfig 가 안 심긴 Redis 도 통과**(열쇠는 티어 사다리 키가 있는가). fail-open 은 **`common/gameconfig/GameConfigReader` 한 곳**에 있다.
- **게시판 방 먼저 합류의 읽기는 fail-closed**(`seeded` · `modeConfig` · `tierScores` · `tierRanges` — 못 읽으면 503. 안 심긴 Redis 는 티어 · 정원 검사를 건너뛴다).
- **접두사는 `common/gameconfig/GameConfigKeys` 한 곳**(원본 `matching` `SharedKeys.GAMECONFIG_PREFIX` · seed). 어긋나면 검증이 통째로 꺼진 채 테스트가 통과한다 — **실제로 읽는 테스트** `GameConfigReaderTest`.
- **`mode` 는 필수**, `tier` 는 값이 있을 때만. 거절은 400 `VALIDATION_FAILED` + `"mode: …"` · `"tier: …"`(글귀 · 안 심긴 Redis 통과는 Claude 세부). `tier` 의 `@Pattern(^[A-Z0-9_]{1,20}$)` 은 남긴다.
- **미정** — `recruit_posts.mode` 를 `NOT NULL` 로 조일지 · 옛 글의 빈 `mode`(응답에 `mode: null` 이 나갈 수 있다). **지어내지 마라.**

### 3.7 차단 관계 사본 — Redis `qm:user:block-rel:{userId}` (2026-10-02 소유자 결정 · P-52 · D-57 · 계약 "차단" 의 "차단 관계 사본")

소유자 결정 "차단 관계를 Redis 로 — matching 이 DB 를 안 쓰게". **SET 이고 member 는 그 사용자와 어느 방향으로든 차단 관계인 사용자 번호의 십진 문자열 · 대칭(A 가 B 를 차단하면 둘 다) · 수명 없음.** 쓰는 앱은 이 앱뿐이고 `matching` 은 합류 스크립트에서 `SISMEMBER` 로 읽기만 한다(INV-6). **원본은 `blocks` 표 — 어긋나면 DB 가 맞다.** 키 원본 상수는 `social/redisKeys/BlockKeys`(`matching` 의 `SharedKeys.BLOCK_REL_PREFIX` 가 따라 적었다 — `SharedPrefixTest` 다섯째). 아래는 전부 Claude 세부(P-52 행).
- **쓰는 순서** — 차단 · 해제(`BlockService`)의 `@Transactional` 안에서 **DB 먼저 · Redis 다음 · 커밋.** Redis 를 못 고치면 **503 `BLOCK_STATE_UNAVAILABLE`**(+ `Retry-After: 5`) · 롤백 — "DB 에는 있는데 사본에는 없다"(덜 막기 — 위험한 쪽)가 생기지 않는다. 해제는 **반대 방향 줄이 없을 때만** 빼고, 커밋되지 않으면 사본에 되돌려 넣는다. 두 집합은 한 스크립트로 같이 고친다(`add-block-rel.lua` · `remove-block-rel.lua`).
- **한 줄로 선다** — 차단 · 해제 · 재구성이 트랜잭션 맨 앞에서 advisory lock 을 잡는다(`BlockRepository#lockRelationCopy`). 재구성의 옛 계산 · 반대 방향의 경쟁이 덜 막기를 만들지 않게.
- **회원 탈퇴** — 사용자 줄을 잠근 뒤 지우기 전에 상대 목록을 읽고(`BlockReader#counterpartsOf`) 커밋 뒤 그 사람의 키 `DEL` + 상대마다 `SREM`(`BlockRelationRedis#removeUser`). **실패해도 탈퇴는 끝난다**(남는 것은 없는 사람을 향한 더 막기 — 재구성이 치운다).
- **재구성** — **뜰 때마다 한 번**(`ApplicationRunner` — 못 하면 WARN · 기동을 막지 않는다) + **주기**(`platform.block.redis-sync-interval` ← `BLOCK_REDIS_SYNC_INTERVAL` · 기본 `PT5M` · `PT0S` 면 끈다 — `BlockRelationSync` · `@EnableScheduling` 은 `social/BlockRelationConfig`). 표 전체를 읽어 키마다 `DEL` + `SADD` 한 스크립트(`replace-block-rel.lua`) → `SCAN` 으로 표에 없는 사용자의 키 `DEL`. 멱등 · 수천 줄 전제. 5분은 장애 조치 때 마지막 ms 를 잃는 창(덜 막기)의 상한이다.
- 차단 목록 · 게시판 · 입장의 차단 거르기(`BlockReader`)는 **DB 를 그대로 읽는다** — 사본은 `matching` 을 위한 것이다. **사본을 쓰는 곳은 `social/service/BlockRelationRedis` 하나**(맨손 `SADD` · `SREM` 금지).

## 4. 기술 스택 (결정됨)

| 항목 | 값 |
|---|---|
| 언어 | **Java 21** |
| 프레임워크 | **Spring Boot 4.1.1**(MVC) — `matching` · `notification` 과 같은 버전 |
| 빌드 | Gradle(`io.spring.dependency-management` 1.1.7) · 단일 모듈 · 앱은 `backend/` |
| 저장소 | **PostgreSQL** + Flyway(#4). **Redis 용도** — 알림 발행 · refresh(`qm:auth:refresh:{uuid}`) · 방 안의 일(`qm:room:*` · `qm:user:active-room:*`) · 활성 요청 키 `EXISTS` · 전적 락 `qm:riot:sync:{gameAccountId}`(60초 — 연결 · 로그인 때 다시 받기가 같이 · PUBG 도 같은 키) · PUBG 시즌 캐시 `qm:pubg:season:{shard}`(30일) · gameconfig 읽기 · 파티 HASH 읽기 · **차단 관계 사본 `qm:user:block-rel:{userId}`**(쓴다 — §3.7). 이 앱의 접두사는 `qm:room:*` · `qm:auth:*` · `qm:riot:*` · `qm:pubg:*` · `qm:user:block-rel:*`(2026-10-02 — 접두사가 `qm:user:` 로 시작하지만 이 앱의 키다). (`qm:riot:refresh:*` 는 없어졌다 — P-42.) 그 밖의 용도는 §7 |
| 인증 | **Spring Security `oauth2-resource-server`(Nimbus)** — Boot 4 스타터 `spring-boot-starter-security-oauth2-resource-server` · RS256 · `NimbusJwtEncoder`. jjwt 등 금지. **소셜 로그인에 `oauth2-client` 를 쓰지 않는다**(세션을 쓴다) — `RestClient` 로 직접 짰다 |
| 포트 | **8082**(`SERVER_PORT`). `matching` 8080 · `notification` 8081. Redirect URI 기본 `OAUTH_REDIRECT_BASE_URL=http://localhost:8082` |
| 패키지 | 도메인 먼저 — `common`(`error` · `web` · `security` · `push` · `gameconfig`) · `account` · `social` · `party` · `room`(Redis 만 · Lua 는 `resources/lua/`). 안은 `controller` · `service` · `domain` · `repository` · `dto`. **도메인 사이는 창구로 잇고 남의 리포지토리를 직접 쓰지 않는다**(`package-info.java`). JOIN 은 된다(P-23) |

## 5. 설계 규칙

- **stateless** — 프로세스 로컬 상태 금지. 환경변수 + 기본값, `/health/live` · `/health/ready`(readiness 에 `db` 만 — Redis 는 넣지 않았다), SIGTERM graceful shutdown. JSON 로그는 운영에서 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(형식은 미정 — §7).
- **경계를 넘는 동기 호출을 새로 만들지 않는다.** 놓치면 데이터가 어긋나는 것은 outbox → SQS, 놓쳐도 조회로 복구되는 것은 Pub/Sub(docs/14 §7). 지금 이 앱의 큐는 0개(§3.4).
- **불변식은 DB 가 강제한다** — `조회 → 판단 →삽입` 금지. 중복 가입 · 중복 차단 · 모집 중인 글 하나 · 파티 기록의 멱등 · 있는 사용자인가(FK)가 전부 제약이다(위반을 409/404 로 옮긴다). 그래서 Hibernate 제약 위반 WARN 로거(`org.hibernate.orm.jdbc.error`)를 껐다. **H2 로 PostgreSQL 제약을 검증했다고 치지 마라**(D-3).
- **Spring Security** — `STATELESS` · Spring CSRF 필터 끔(CSRF 는 `Origin` 필터). **인증 없이 되는 요청은 `/api/v1/auth/**` · `/health/**` · `/info` 뿐** — 로그인 안 한 채 모르는 경로는 401 `UNAUTHENTICATED`. **예외 — `DELETE /api/v1/auth/account`(회원 탈퇴)는 access 쿠키가 있어야 한다**(2026-10-02 · P-48 — `SecurityConfig` 의 `authenticated()` 와 `CookieBearerTokenResolver.ACCOUNT_PATH` 가 같은 금이다).

### 5.1 인증 세부 (소유자 확정 · 구현됐다 — D-24 · D-26)

**access 토큰은 쿠키 `qm_access` 로만 주고받는다 — `Authorization` 헤더에 싣지 않는다.** refresh 는 JWT 가 아니라 Redis 의 불투명 UUID 이고 rotation 필수다(#16 · D-14).

**전제 — 브라우저가 보기에 서비스들은 같은 출처다**(CloudFront 한 도메인 → ALB 경로 분기). (나) · (다) · (사)가 이 전제에 선다.

**(가) 서명 — RS256.** 개인 키는 이 앱만, `matching` · `notification` 은 공개 키로 검증만. 공개 키는 **환경변수**(운영은 Secrets Manager) — **JWKS 엔드포인트 없음.** JWT 헤더에 `kid`. 검증 쪽은 `NimbusJwtDecoder.withPublicKey()` + 쿠키에서 꺼내는 `BearerTokenResolver`.
- 클레임 — `sub`(사용자 번호의 십진 문자열) · `iss` · `iat` · `exp` · `jti` · **`token_use`**(`access` · `social_signup` — 원본 `common/security/TokenClaims`). 닉네임처럼 바뀌는 값은 넣지 않는다.
- **검증하는 쪽은 서명 · `iss` · `exp` 에 더해 `token_use` 를 반드시 본다**(같은 키로 서명하므로). JOSE `typ` 을 쓰지 않은 이유는 Spring 기본 디코더가 `typ≠JWT` 를 거절해서다.
- **`sub` 가 `^[0-9]{1,19}$` 인지도 본다**(`TokenClaims.SUBJECT_PATTERN` · `JwtConfig#jwtDecoder`).

**(나) 쿠키.** `HttpOnly` · `Secure` 는 환경변수(`COOKIE_SECURE`, 운영 `true`) · **`SameSite=Lax`** · `Path=/` · **`Domain` 없음**(host-only). access 쿠키 수명 = 토큰 수명. refresh 쿠키 `qm_refresh` 는 **`Path=/api/v1/auth`** · `Max-Age` 7일(2026-10-02 소유자 결정 — 그 전의 `Path=/api/v1/auth/refresh` 는 브라우저가 로그아웃에 싣지 않아 로그아웃이 Redis 의 줄을 못 지웠다 · P-15). **임시(2026-10-02)** — 쿠키를 줄 때 · 지울 때 옛 `Path`(`/api/v1/auth/refresh`)의 `qm_refresh` 를 `Max-Age=0` 으로 같이 지우고, 재발급 · 로그아웃은 실린 `qm_refresh` 를 **전부** 읽는다(옛 쿠키가 남은 브라우저는 재발급에 같은 이름을 둘 싣는다 — `RefreshTokens#valuesIn` · `#consume(List)`). 운영 전 · 옛 쿠키가 다 사라지면(7일) 옛 `Path` 를 지우는 것을 걷어낸다(`RefreshTokens.LEGACY_COOKIE_PATH`).

**(다) CSRF — `SameSite=Lax` + `Origin` 검사.** POST/PUT/PATCH/DELETE 에서 `Origin` 검사(허용 목록 밖이면 403 `ORIGIN_NOT_ALLOWED` · `Origin` 없는 요청은 통과). CSRF 토큰 없음. **전제 — 상태를 바꾸는 GET 을 만들지 않는다**(예외: OAuth 콜백 — `state` 검증이 지킨다. 목록의 옮겨 적기는 §3.3).

**(라) 수명과 denylist.** access `ACCESS_TOKEN_TTL=PT15M` · refresh `REFRESH_TOKEN_TTL=P7D`. **access denylist 는 두지 않는다**(#16 개정) — 로그아웃은 refresh 삭제 + 쿠키 제거, 남는 최대 15분은 감수. **한 사용자의 refresh 를 한꺼번에 끊는 길은 없다**(사용자별 집합 없음 · `KEYS`/`SCAN` 금지 — §7). 프런트가 만료 전에 재발급을 불러야 한다(서버 장치 없음 — §7).

**(마) refresh** (P-15 · 계약 "refresh 토큰").
- 키 **`qm:auth:refresh:{uuid}` → `userId`**. 접두사 상수는 **`common/security/RefreshTokens`**(쿠키 이름 `RefreshTokens.COOKIE` 도 — `TokenClaims` 가 아니라 여기인 것은 옆 서비스와의 약속이 아니라 이 앱만 읽는 값이라서다. `SocialSignupTokens.COOKIE` 와 같은 자리). **JWT 가 아니라 불투명 UUID.**
- rotation 은 **`GETDEL` 한 번**. 기기 수 제한 없음.
- 재발급 **`POST /api/v1/auth/refresh`** — 쿠키로만 받고, 성공하면 `{userId, nickname}` + 새 쿠키 둘. **실패는 전부 같은 401 `INVALID_REFRESH_TOKEN`**(본문이 글자까지 같다) · 실패해도 refresh 쿠키를 지운다(access 는 건드리지 않는다) · 탈취 감지 없음.
- (경로 이름 · 실패를 401 하나로 합친 것 · 실패에도 쿠키를 지우는 것 · 아래 Redis 장애 때의 갈림은 Claude 세부 — P-15.)
- 로그아웃은 Redis 의 줄과 쿠키 둘을 지운다 — 쿠키가 없어도 · Redis 가 죽어도 204. **refresh 쿠키가 실제로 로그아웃에 실려 온다**(2026-10-02 에 `Path` 를 `/api/v1/auth` 로 넓혔다 — 그 전에는 실리지 않아 로그아웃 전의 값으로 재발급이 200 이었다).
- **Redis 가 죽으면** — 소셜 로그인 · 가입은 성공하고 access 만 나간다 · 재발급은 401(fail-closed) · 로그아웃 204.
- "로그인시킨다 = 쿠키 둘"은 **`common/security/SessionCookies`** 한 곳(소셜 로그인 · 가입 · 재발급 · 개발용 로그인). refresh 저장이 실패하면 access 만.
- **로그인시킨 뒤(그 넷 — 성공한 재발급 포함) 낡은 전적을 뒤에서 다시 받게 한다**(P-42 — `account/stats/GameStatsLoginRefresher#refreshStale` · 각 컨트롤러가 쿠키를 만든 뒤 부른다. 곧바로 돌아오고 예외를 내지 않는다 — 응답과 무관하다. 잇기는 로그인이 아니라 부르지 않는다. §7 "게임 계정 연동"). `SessionCookies`(`common`)에 넣지 않은 것은 `common` 이 `account` 를 부르지 않게 하려는 것이다(Claude 세부).

**(바) SSE 와 만료.** `notification` 은 연결할 때만 검증한다. 재접속이 401 로 멈추면 프런트가 재발급 뒤 `EventSource` 를 새로 만든다. 서버 장치 없음.

**(사) 로컬 CORS — 서비스에 CORS 설정을 넣지 않는다.** 프런트 개발 서버의 프록시가 경로별로 나눠 보낸다.

**(아) 옆 서비스의 전환.** `notification` · `matching` 은 `?userId=` 를 버리고 쿠키 `qm_access` 를 이 앱의 공개 키로 검증한다(각 폴더에서 끝났다 — `notification` `CLAUDE.md` §5.1 · `matching` `HANDOFF.md` §0-5). "쿠키가 없으면 `userId`" 개발 스위치는 어느 쪽에도 두지 않았다.

**정해진 값**(P-2 · D-24).

| 항목 | 값 |
|---|---|
| 쿠키 이름 | `qm_access` · `qm_refresh` |
| `iss` | `queuemate-platform` |
| RSA 키 | 2048 · 개인 키 `JWT_PRIVATE_KEY`(PKCS#8 PEM — 이 앱만) · 공개 키 `JWT_PUBLIC_KEY`(X.509 PEM). 하나만 주면 · 둘 다 비고 개발용 키 파일도 없으면 기동하지 않는다(P-51 — 아래 "개발용 키") |
| `kid` | `JWT_KEY_ID`(기본 `dev-1`) |
| `Origin` 허용 목록 | `ALLOWED_ORIGINS`(쉼표 · 기본 `http://localhost:5173,http://localhost:3000`) |
| 수명 | `ACCESS_TOKEN_TTL=PT15M` · `REFRESH_TOKEN_TTL=P7D` |
| `Secure` | `COOKIE_SECURE`(기본 `false`) |

- **개발용 키** — 두 키가 다 비면 `backend/.dev-keys/` 의 파일을 읽는다(경고 로그 · `.gitignore` — **개인 키가 있으니 절대 올리지 않는다**). **파일도 없으면 기동을 거부한다** — 새로 만드는 것은 **`JWT_GENERATE_DEV_KEYS=true`**(기본 `false` · 로컬 처음 한 번 · 운영에서는 두지 않는다)일 때뿐이다(2026-10-02 소유자 결정 · P-51 — 컨테이너가 재시작 · 태스크마다 다른 키로 조용히 뜨지 않게. 파일이 있으면 플래그와 상관없이 읽는 것 등은 Claude 세부). 옆 서비스는 그 `public.pem` 으로 검증한다. 테스트는 임시 폴더(플래그 `true`).
- 로그인 실패 제한은 없다(비밀번호가 없다 — P-24). IP 단위 제한은 앞단(CloudFront/WAF)의 일.
- **개발용 로그인(임시 — `TEMP-DEV-LOGIN`)**(P-34 · 계약 "개발용 로그인") — `POST /api/v1/auth/dev-login {nickname?}`(기본 `dev-tester`)이 그 닉네임의 사용자로(없으면 만들어) **진짜 쿠키 둘**을 준다(`SessionCookies` 경유 · 소셜 연결은 안 만든다). **`DEV_LOGIN_ENABLED`(`platform.auth.dev-login-enabled`) 기본 `false` — 꺼지면 빈이 없어 404.** POST 라 `Origin` 검사를 받는다. 검증하는 쪽을 느슨하게 하는 스위치가 아니라 **발급하는 요청 하나**다. **운영에서 켜지 않는다.** 걷어낼 때는 `grep -rn TEMP-DEV-LOGIN` 이 가리키는 것을 지운다(시점은 소유자가 정한다).

## 6. 배포 기준

**Stage 2(ECS Fargate)**. Stage 1(EC2 + Compose) 적용 안 함(D-18). **k8s / HPA / sticky session 전제 구현 금지.**

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 묻는다. 정해지면 이 표에서 빼고 해당 절로 옮긴다. 닫힌 행의 옛 내용은 `docs/CLAUDE_HISTORY.md` §7. 다음에 물을 것의 추린 목록은 `START_HERE.md` §4.

| 항목 | 상황 |
|---|---|
| **소유자의 검토 — `contracts/platform-api.md` P-1 ~ P-52** | 미정은 아니지만 확정도 아니다. **Claude 가 정한 것** — P-1 ~ P-10(남은 것: 엔드포인트 · 토큰 · `roomId` · 만석 표시 · 소셜 흐름 · 게임 프로필과 글의 칸 · 친구/신고/알림 둘) · **P-30**(자동 매칭 파티의 방 — 경로 · 응답 · 에러 코드 · `roomId = partyId` · V2 · DB 먼저 Lua 뒤 · fail-closed · 입장 표시 키가 부른 사람에게만) · 소유자 결정마다 붙은 "Claude 가 정한 세부"(P-22 의 창구 · 순서 · 자가 치유 · P-28 · P-32 ~ P-34 · P-36 · P-38 ~ P-44 · P-48 ~ P-52 의 세부 — 계약의 각 행). 목록 GET 의 옮겨 적기와 §5.1 (다)의 관계(§3.3)도 검토 항목. 소유자가 뒤집으면 코드 · 계약 · 이 파일을 같이 고친다. 계약 원본에 platform 엔드포인트가 있으면 맞춘다(P-1) |
| **자동 매칭이 게시판 방에 합류하는 길** — 정해졌고 구현됐다(`POST /api/v1/posts/auto-join` · P-28 · D-40 · 계약 그 절) | 두 경로를 다 둔다 — 맞는 열린 방이 있으면 넣고(200 `{postId, roomId}`), 없으면 404 `NO_MATCHING_POST` → **프런트가** `matching` 을 부른다. 조건: `game` · `mode` · `voice` · PUBG `perspective` 가 같고 · 내 티어가 **방장 티어의 줄**로 본 `tier-range` 안이고 · 글의 `wantedPositions` 에 내 포지션이 있어야 한다(빈 배열은 통과 — 옛 글). `purpose` 는 보지 않는다. **찾는 포지션이 있는 글에는 요청의 포지션으로 들어가고 누가 이미 고른 방은 건너뛴다**(방 키 · 스크립트의 -6 — P-44 · Claude 세부). 가장 오래된 방부터 · 만석/확정이면 다음 · 내 글 · 음성 불일치는 SQL 에서 거른다 · `no-auto-join` 목록의 방은 건너뛴다 · 정원은 글의 `capacity` · 활성 요청 키를 만들지 않는다(한 번만 본다) · `IN_OTHER_ROOM` · `ALREADY_QUEUED` 는 409 · Redis 를 못 읽으면 503 · ~~"자동 합류 허용" 칸 없음~~ → **2026-10-02 소유자가 뒤집었다 — 글의 `allowAutoJoin`(빠른매치 입장 허용 / 금지 · 글을 쓸 때 필수)이 `false` 면 후보에서 뺀다**(후보 SQL 에서 · 직접 입장은 된다 · V10 · P-50). 본문은 `matching` 의 `CreateMatchRequestCommand` 모양(`playPurpose` 는 받되 무시) — **티어 · 포지션은 프로필이 아니라 본문의 자기신고**다 · 티어의 400 은 `matching` 과 같은 순서 · 티어가 없는 사람은 `EXIST` 모드면 400 · 포지션이 `NONE` 이거나 없으면 빈 `wantedPositions` 글만 맞는다. **남은 미정 — PUBG `PLATFORM` 값을 방장 `server` 와 대조할지.** 후보 상한 `platform.board.auto-join-scan` 50 등은 Claude 세부 |
| **파티 모집 게시판에 남은 세부** | 확정된 방의 기능(Ready 등) · 게시판 채널 이름의 원본을 둘 곳 · 차단에 남은 경쟁 · 목록의 필터(지금은 필수 `game` 하나 · `status` 필터는 두지 않는다) · 도배 대응 · 자동 매칭 방의 강퇴 재입장. 자세한 것은 §7.1 "정할 것" |
| **운영의 DB 롤** | 테이블 컬럼은 정해졌다(§3.5). 롤 · `GRANT` 는 없다. **남은 것** — 운영에서 붙는 DB 계정의 이름과 권한(로컬은 `postgres`), 마이그레이션 계정과 앱 계정을 나눌지 |
| **옆 서비스의 전환 · refresh 에 남은 것** | 전환은 끝났다(§5.1 (아)). **남은 것** — ① `Origin` 검사를 `matching` · `notification` 에 넣었는지(이 파일에는 끝났다는 기록이 없다 — 각 폴더에서 확인) ② **한 사용자의 refresh 를 한꺼번에 끊는 길**(모든 기기 로그아웃 — 둘지부터 미정) ③ **프런트의 재발급 흐름**(맞춰 본 적 없다) |
| **소셜 로그인에 남은 것** | 흐름은 계약 "소셜 로그인"(P-7). **가짜 제공자로만 테스트했다 — 실제 키로는 붙여 보지 않았다**(구글도). 설정 — 카카오 · 디스코드 키와 **구글 `GOOGLE_CLIENT_ID` · `GOOGLE_CLIENT_SECRET`**(scope `openid profile` · 이메일 없음 · 회원 번호는 userinfo 의 `sub` · 추천 닉네임은 `name` — P-33). 카카오 · 디스코드 · 구글 앱 등록 · 키 · Redirect URI 등록은 **소유자가 해야 하고 필수다**(구글 로컬: `http://localhost:8082/api/v1/auth/oauth/GOOGLE/callback`). 키가 없는 동안은 개발용 로그인(§5.1 끝). 잇기 · 끊기는 됐다(P-27) — **자동 중복 감지는 없다**(로그인 안 한 채 다른 제공자로 오면 새 사용자 · 두 사용자를 합치는 길 없음). 프런트 경로(`/` · `/signup/social` · `/login?error=OAUTH_FAILED` · `/settings?linked=…` · `/settings?error=…`)는 맞춘 적 없다 |
| **게임 계정 연동 — 게임사 API** | **정해져 구현된 것** — 키 **`RIOT_API_KEY`**(없어도 기동은 정상 — LoL 게임 계정 저장만 503) · LoL(`{gameNickname}` · `tier` · `server` 를 보내면 400 · 저장 전에 동기로 긁는다 · 상한 30초 · 이름#태그가 없으면 404 `RIOT_ID_NOT_FOUND` · Riot 장애/시간 초과/키 없음은 503 `GAME_STATS_UNAVAILABLE` — 둘 다 저장하지 않는다 · 솔로 · 자유 두 사다리 · 리그는 `league-v4` `entries/by-puuid` · **최근 10판**(`platform.riot.match-count` 기본 10 — Riot 호출 14번) · 승/패/승률은 솔로랭크 시즌 누적 · 모스트 챔피언은 **통산 숙련도 상위 셋 · `{championId, masteryLevel, masteryPoints}`**(`champion-mastery-v4` `top?count=3` · 동점은 점수 → 레벨 → `championId` 글자 순 · 이름은 `resources/lol/champions.json`(Data Dragon 16.18.1 — 프런트 목록과 같은 버전이라 새 챔피언이 나오면 둘 다 다시 뜬다) → 경기의 `championName` → 번호의 글자 순 · 호출 실패면 빈 배열 · CS 없음 — P-39. 동점 · 이름 찾기는 Claude 세부) · **`detail.recentResults`** — 최근 경기마다의 승 · 패(2026-09-30 **소유자 요청** · P-43 — duo.gg 식 "15승 5패 (20 게임)" + 승/패 칸 줄. **`games` · 평균 · 연승과 같은 경기 목록**(모든 큐 — **커스텀 게임만 뺀다**, 아래) · `"W"` · `"L"` · 새 경기가 먼저 · 길이 = `games` · 경기가 없으면 빈 배열 · **Riot 호출을 늘리지 않는다** · `stats.wins` · `losses`(솔로랭크 시즌 누적)와 다른 숫자다 · 옛 스냅숏에는 칸이 없다(화면이 숨긴다 · 마이그레이션 없음). 칸 이름 · 두 글자 · 순서 · 빈 배열 · 못 읽은 경기는 빠지고 `win` 을 못 읽으면 `"L"` 인 것은 Claude 세부. PUBG 에는 없다) · **커스텀 게임은 뺀다**(2026-10-02 **소유자 지시** · P-49 — Riot 정책이 커스텀 큐의 전적을 동의 없이 공개로 보여 주지 못하게 한다. 큐를 지정하지 않은 경기 id 목록에 토너먼트 코드로 연 커스텀이 섞여 오는 것을 실제 키로 확인했다. 경기 상세의 `info.queueId == 0` 또는 `info.gameType == "CUSTOM_GAME"` 이면 그 경기를 `games` · 평균 · 연승 · `recentResults` 어디에도 넣지 않는다 · **더 받지 않는다** — 최근 10판 가운데 커스텀만큼 짧아진다 · 옛 스냅숏은 다음 받기에서 바뀐다. `queueId` 를 못 읽은 경기도 빼는 것 · 어느 한쪽만 맞아도 빼는 것은 Claude 세부) · Riot 의 429 는 재시도하지 않는다 · 평점/MVP 배지 없음). PUBG(`{gameNickname, server}` · `server` 필수 · `tier` 를 보내면 400 · 동기 · 404 `PUBG_PLAYER_NOT_FOUND` · 호출 2 ~ 4번 · 키 하나에 분당 10회 · 429 는 재시도 없이 503 + `Retry-After` · 사다리 `RANKED` 하나 · 전적은 랭크 모드 합산 · `detail` = `seasonMode` · `avgDamage` · `kd` · `top1Rate` · `PUBG_API_KEY` 없으면 저장 불가 — P-36). **로그인 때 다시 받기**(2026-09-30 **소유자 결정** · P-42 — 전적 갱신(P-17)을 없앴다) — **진짜 로그인(소셜 콜백의 로그인 · 소셜 가입 · 개발용 로그인)과 성공한 재발급 `POST /api/v1/auth/refresh`** 때, 그 사람의 LoL · PUBG 게임 계정 가운데 **마지막으로 받은 뒤(`game_account_stats.synced_at`) 1시간이 지난 것**(전적 줄이 없는 것 포함)을 **뒤에서** 다시 받는다 · **응답은 기다리지 않고 실패시키지도 않는다** · **실패하면 WARN · 옛 전적 그대로 · 재시도 없음**(다음 로그인 · 재발급 때 다시 본다) · 티어도 같이 · VALORANT 는 받지 않는다 · 잇기에는 걸지 않는다. **Claude 세부** — 설정 `platform.riot.stale-after`(`GAME_STATS_STALE_AFTER` · 기본 `PT1H`) · 전용 풀 `gameStatsLoginExecutor`(스레드 하나 · 큐 100 · 넘치면 버리고 WARN — 연결의 풀과 따로) · 자물쇠를 잡은 뒤 다시 읽어 방금 받은 계정은 건너뜀 · 트랜잭션 밖(안이면 커밋 뒤) · 키가 없으면 던지지도 않음 · 소셜 가입에도 건다(`account/stats/GameStatsLoginRefresher` · `GameStatsSyncWorker#syncIfStale`). **연결(`PUT`)이 긁는 동안 · 뒤에서 받는 동안 같은 계정의 `PUT` 은 429 `TOO_MANY_STATS_REFRESHES` + `Retry-After: 60`**(연결이 전부터 쓰던 갈래 — 이름은 그대로 남는다). 연결의 상한은 `platform.riot.refresh-timeout`(30초 · 전용 풀 + `Future.get` · 늦게 끝난 긁기는 버린다 — 이름은 옛 요청의 흔적). 락 `qm:riot:sync:{gameAccountId}`(`SET NX EX 60` · 못 잡으면 건너뜀 · Redis 가 죽으면 락 없이) · 게임별 구현은 `account.stats.GameStatsProvider` 뒤(락 · 인터페이스 · 429 처리는 Claude 세부). `external_id` 에 `puuid` 를 적지만 **`verified` 는 켜지 않는다**. 모집 글을 쓸 때 긁지 않고 신선도 장치도 없다. **남은 것** — **VALORANT 전적**(Riot 별도 승인 — `stats` 는 늘 `null`) · **`verified` 를 켜는 법**(지금은 자기신고를 믿는다 · PUBG 는 길이 없다) · **운영 API 키**(Riot 승인 · `PUBG_API_KEY` — 한도 증액은 developer.pubg.com "I NEED A HIGHER LIMIT"). (타이머로 도는 갱신은 없다 — 두려면 먼저 묻는다) |
| `reservation` 스키마의 마이그레이션 | 이 앱이 대신 갖는지 별도 절차인지 미정. 정해지기 전에 넣지 않는다 |
| Redis 의 다른 용도 | `qm:party:presence:*` · `qm:party:ready:*` · rate limit 은 원본 docs/07 에 **키 이름만** 있다. 누가 쓰는지, `matching` 의 `qm:party:*` 와 접두사가 겹치는 것을 어떻게 할지 |
| 뼈대의 임시값 가운데 남은 것 | 로컬 DB 이름 `queuemate` · 계정 `postgres` · Flyway 기록 테이블 자리(기본 `public`) · JSON 로그 형식(`ecs` 여야 하는지). 테스트용 PostgreSQL 은 **5433**, Redis 는 **6380**(`START_HERE.md` §6 — 5432 · 6379 면 테스트가 건너뛴다) |

**닫힌 행**(참조가 남아 있어 이름만 둔다) — `PARTY_*` 알림 · `parties` 자원: 두지 않는다(P-31 · D-44) / SQS 메시지 본문 3종 · 자동 매칭 파티의 id: SQS 가 없고 `parties.match_party_id` 에 담는다(D-42 · P-30) / 확정된 사용자를 푸는 길: 아무도 지우지 않는다 — `status=PARTY` 60초 수명 + 입장 표시 키(D-42).

### 7.1 파티 모집 게시판과 방 (D-11 · D-16 · D-20 · D-21 · D-23 · 계약 "모집 글 · 목록" · "방")

**정해진 것 — 방의 성격과 차단.**
- 모집 글을 올리면 **그것이 곧 파티방**이다(자동 매칭 파티방과 같은 개념). 글 · 목록 · 상태 · 확정 기록 · 파티원은 `party`, 방 안의 일은 `room` — 둘 다 이 앱(§3.3).
- **차단 관계(어느 방향이든)가 방 안의 누구와든 있으면 그 방은 내 목록에서 아예 보이지 않는다**(D-20). **입장도 같은 규칙**(404). **확정된 글은 확정 순간의 파티원 전원과 본다**(P-40). 대가 — 글마다 전원과 대조하고, 멤버 HASH 의 유령 때문에 방이 잠깐 숨겨질 수 있다.
- **목록의 한 줄** — ① 인원 ② 카드 ④ F5 없이 갱신(③ `filledPositions` 는 없앴다 — P-18). 목록의 단위는 모집 글이고 사람 검색은 금지. 데이터는 전부 이 앱이 조립한다(`RoomService#states` → DB 프로필 → 차단 대조). 갱신은 게시판 채널 신호(§3.2).
- **파티원은 방장이 확정한다**(D-21) — 방장만 · 2명 이상 · **대상은 그 순간 방 안 전원**(원치 않는 사람은 먼저 강퇴) · **되돌릴 수 없다** · 확정 뒤 새 입장 불가 · 확정한 방은 승계(D-23).
- **방과 글은 같이 산다**(§3.3). 방장이 나가면 글은 지우지 않고 **만료**(목록에 남고 못 들어간다). 말없이 사라진 방장은 목록 · 단건이 만료로 옮긴다(최대 10분 늦다).
- 방 알림 이름은 `ROOM_*`(§3.3). 입장 승인 없음 · 둘러보는 상태 · 강퇴 · 음성 제어는 방과 프런트의 규칙. 정원은 모드의 인원(상한 5 — P-41).

**정해진 것 — 글과 목록**(첫 항목은 소유자 지시, 페이지 나누기 · 정렬 · 보존 · `game` 필수 · 고치기 없음 · 방장 포지션 · 확정 카드 · 정원은 소유자 결정, 나머지는 Claude 가 정했다 — P-4 · P-8).
- **목록의 한 줄은 게임마다 다른 정보를 보여 준다**(본보기 OP.GG 듀오 찾기) — LoL: 이름#태그 · 인증 · 티어 · 찾는 포지션 · 모스트 챔피언 · 승/패 · KDA · 메모 / VALORANT: 모스트 요원 · 주 무기 · 헤드샷률 · 음성 / PUBG: 모드 · 티어 · 음성 · 서버/시점 · 평균 데미지 · K/D · 치킨률. 가로 한 줄 · 펼치지 않고 방 안 전원을 보여 준다 → **`members[]` 에 게임 프로필 전체가 실린다.** 글 한 줄의 모양은 세 게임이 같고 내용만 다르다. VALORANT 의 `stats` 는 `null`(화면이 "정보 없음").
- **글에 담는 것** — `game` · **`mode`(필수 · gameconfig 에 있는 모드 · 30자)** · `title`(1~60) · `description`(300까지) · `voice`(`REQUIRED` · `NO_VOICE`) · `conditions`(PUBG `{"perspective": "TPP"|"FPP"}` 필수, 그 밖 `{}`) · `wantedPositions` · **`hostPosition`** · **`allowAutoJoin`**(빠른매치 입장 허용 / 금지 — 2026-10-02 소유자 결정 · P-50 · **필수** — 없거나 `null` 이면 400 `"allowAutoJoin: 필요합니다"` · `false` 면 게시판 방 먼저 합류가 넣지 않는다 · 직접 입장은 된다 · 글 한 줄에 싣는다 — 칸 이름 · 필수 · 글귀는 Claude 세부) · (서버가 적는) `capacity`. `purpose` 는 없다(P-29). **모집 중인 글은 한 사람에 하나**(409 `ALREADY_RECRUITING`).
- **방장 포지션 · 찾는 포지션**(P-38 · 계약 "방장 포지션") — "포지션이 있는 모드" = 게임에 포지션이 있고 gameconfig `positionUniqueness == "true"`. 그런 모드면 **`hostPosition` 필수 · `wantedPositions` 하나 이상 필수 · `hostPosition` 은 `wantedPositions` 에 들 수 없다.** 포지션이 없는 모드(ARAM 등 · PUBG)에는 **둘 다 값이 오면 400**(조용히 버리지 않는다). 거절은 400 `VALIDATION_FAILED`(`"hostPosition: …"`). fail-open. **찾는 포지션 수 ≥ 정원 − 1**(소유자 결정 · 400 · 정원을 모르면 건너뜀(Claude 세부)). 옛 글(빈 배열)은 통과. 응답은 글 한 줄의 `hostPosition`(카드가 아니다). 한계 — "포지션은 있지만 중복 허용" 모드가 생기면 잘못 갈린다.
- **카드** — `{userId, nickname, host, position, profile}`(게임 계정이 없으면 `profile: null`). `position`(2026-10-01 소유자 결정 · P-44) — 모집 중은 멤버 HASH 의 값(안 골랐으면 `null`) · 확정은 늘 `null`. 남은 포지션 = `wantedPositions` − `position`(프런트). `host` 는 끝난 글에도 채운다. **`members` — 모집 중: 방 안 지금 있는 사람 / 확정: 확정 순간의 파티원 전원(`party_members` — 나간 뒤 · 닫힌 뒤에도 그대로 · P-40) / 만료: 빈 배열.** 확정된 글의 `memberCount` = 파티원 수 · `full` = `false` · `closed` = 파티가 `CLOSED` 인가(P-46). 순서는 방장 먼저 · 닉네임순. 파티원은 한 페이지에 쿼리 한 번(`PostStore#findBoardParties`). (순서 · `memberCount` · `full` · 차단을 파티원과 보는 것은 Claude 세부 — P-40.)
- **정렬 — `id` 내림차순 하나.** 상태도 `createdAt` 도 정렬에 쓰지 않는다(변하는 키는 커서 중복을 낸다 — 목록 조회 자신이 상태를 바꾼다). 응답의 `createdAt` 은 화면용으로 그대로.
- **보존 기간 없음 — 모집 중 · 확정 · 만료가 전부 나오고 끝난 글도 남는다**(P-20). 만석은 `full: true` 로 남는다. 앱에 정리 작업을 만들지 않는다(오래된 글은 소유자가 운영에서 지운다). `status` 필터는 두지 않는다. `expired_at` · `confirmed_at` 은 CHECK 제약과 "언제 끝났나"로 계속 쓴다.
- **목록의 `game` 은 필수**(P-21) — 없거나 빈 값이면 400 `"game: 필요합니다"`, 모르는 이름 · 소문자는 400 `"game: 올바른 값이 아닙니다"`(형 변환에서 거절 — 소문자 변환기를 두지 않는다). 목록 하나에만 걸린다.
- **페이지 나누기 — 커서**(P-14 · 계약 "목록의 페이지 나누기"). `limit` 기본 20 · 최대 100 · 벗어나면 400(잘라 주지 않는다). **`cursor` = 마지막으로 읽은 글 번호(숫자 그대로 — base64url 없음)** · 응답 `nextCursor`(끝이면 `null`). 숫자가 아니면 400(`GlobalExceptionHandler#handleTypeMismatch`) · 0 · 음수 · 끝을 넘은 번호는 빈 페이지(옛 커서 호환 없음 · 채우기 · `nextCursor` 의 뜻 · 빈 페이지는 Claude 세부). 차단으로 모자라면 뒤를 더 읽어 채운다(`platform.board.max-refills` 3). `nextCursor` 는 마지막으로 "읽은" 줄. **신호를 받은 프런트는 커서 없이 펼친 만큼을 맨 위부터 다시 받는다.** 대가 — 옮겨 적기가 읽은 글에만 걸린다(입장은 Lua 가 직접 보니 죽은 방에 들어가지 않는다).
- **지우기**(`DELETE`)는 만료로 바꾸고 방을 닫는다. 확정된 글은 409 `POST_CONFIRMED`. 차단으로 숨겨진 글은 단건 · 입장에서 404 `POST_NOT_FOUND`.
- **글 고치기는 없다**(2026-10-01 소유자 결정 · P-45). `PATCH /api/v1/posts/{postId}` · 409 `ROOM_HAS_OTHER_MEMBERS` · `syncPositions` 가 같이 없어졌고 P-19 는 물음째 없어졌다. 프로필 고치기(`PATCH /api/v1/users/me`)는 남는다.
- `filledPositions` 는 없앴다(P-18) — 다시 둘지는 미정이고, **다른 방식을 지어내지 마라.**
**정할 것 — 닿으면 그때 묻는다.**
- 확정된 방이 자동 매칭 파티방과 **같은 기능(Ready 등)**을 갖는가.
- **차단에 남은 것** — 입장의 글 검사와 Lua 사이에 차단 관계인 사람이 먼저 들어오는 경쟁 · 목록을 본 뒤 생긴 차단 · 같은 방의 두 사람 사이에 생긴 차단 · 차단이 친구 관계를 끊는가(지금은 건드리지 않는다).
- **게시판 채널** — 원본 상수를 둘 곳(재요청을 묶는 간격은 2026-09-30 에 정해졌다 — §3.2).
- **자동 매칭 방의 강퇴 재입장** — `enter-match-room.lua` 는 금지 목록을 보지 않는다(강퇴당한 파티원이 다시 부르면 들어온다). **지어내 막지 마라.**
- 목록의 필터를 더 둘지 · 도배 대응과 신고의 연결.
- **방 안 사람에게 "글이 지워졌다"를 알릴지** — `ROOM_*` 에 새 `type` 이 필요하다. 지어내지 마라.
- **포지션의 다음 단계**(P-44) — 들어온 뒤 포지션 바꾸기(나중에 — 소유자) · `POSITION_TAKEN` 을 지울지.
- 계약 원본과 합칠 때 `ROOM_*` 과 원본의 `PARTY_*` 가 같은 뜻인지.

### 7.2 2026-09-21 에 검토한 방향 — 전부 닫혔다

(가) 게시판 방장 확정을 아는 법은 `room` 합치기로 물음째 없어졌다(한 요청 — §3.3). (나) 자동 매칭 확정을 아는 법 · (다) 자동 매칭 파티의 방 · (라) `status=PARTY` 해제는 D-42 · P-30 으로 정해졌다(§3.3 · §3.4). 원문과 "→ 2026-09-27" 기록은 `docs/CLAUDE_HISTORY.md` §7.2.

## 8. 저장소 구성과 커밋 규칙

`matching` 과 같은 GitHub 저장소의 **`platform` 브랜치**(고아 브랜치) — 로컬은 `git worktree` 로 나란히 둔다.

```
queuemate/
├── matching/ · notification/ · frontend/   각 브랜치 (room/ 폴더는 지웠다 — 기록은 origin/room)
└── platform/                                 이 폴더
    ├── START_HERE.md · CLAUDE.md · README.md
    ├── contracts/platform-api.md             이 폴더의 계약 + P-항목 (§3.1)
    ├── docs/LOCAL_ENV_LESSONS.md             로컬 환경 함정
    ├── docs/CLAUDE_HISTORY.md                줄이기 전의 CLAUDE.md 전문(기록)
    └── backend/
        ├── .dev-keys/                        개발용 JWT 키 — git 에 올리지 않는다
        └── src/
            ├── main/java/com/queuemate/platform/   PlatformApplication · package-info(먼저 읽는다)
            │   ├── common/   error · web(Origin) · security(토큰 · TokenClaims · RefreshTokens · SessionCookies) · push · gameconfig(읽기만)
            │   ├── account/  재발급 · 로그아웃 · 프로필 · 게임 계정 · oauth/(소셜 로그인) · stats/(Riot · PUBG 긁기 · 락 · 로그인 때 다시 받기 · 시즌 캐시 · LolChampionNames)
            │   ├── social/   차단 · 친구 · 신고 · 최근 함께한 사람 · 차단 관계 Redis 사본(redisKeys/BlockKeys · BlockRelationRedis · BlockRelationSync — §3.7)
            │   ├── party/    모집 글 · 목록 · PostEntryGate · 확정 기록 · 자동 합류 · board/(채널 신호) · match/(파티 HASH 읽기 · 자동 매칭 파티의 방)
            │   └── room/     방 안의 일 — RoomService · RoomMemberService · RoomRedis · redisKeys/RoomKeys · RoomErrors (Lua 는 resources/lua/)
            ├── main/resources/application.yaml       환경변수 + 기본값 (8082 · PostgreSQL 5433 · Redis 6380 …)
            ├── main/resources/db/migration/          V1__schema.sql + V2 ~ V10 (§3.5)
            ├── test/java/…                           ApiTestSupport · 도메인별 테스트 · account/oauth/FakeOAuthProvider
            └── test/resources/config/application.yaml
```

커밋은 AngularJS convention `type(scope): subject`.
- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: **`platform`**(문서만 `docs`, 계약 `contracts`, 인프라 `infra`)
- subject 한글 50자 이내 · 마침표 없음 / body 한글 3줄 이내(무엇을/왜 — 어떻게는 코드가 말한다) · type/scope 키워드만 영어 / footer `BREAKING CHANGE:` · revert 는 `revert: <원 subject>` + 해시
- **경로를 명시해 커밋한다** — `git add -A` / `git add .` 금지.
- 한 커밋 한 목적 · `build` 와 `feat` 을 섞지 않는다 · **문서 · 계약과 기능 구현을 한 커밋에 섞지 않는다** · 기능과 그 테스트는 한 커밋 · 각 커밋 시점에서 빌드가 통과해야 한다.

## 9. 작업 방식

작업 지시를 받으면 **기본적으로 서브 에이전트를 띄워 처리한다.**
- 조사 · 탐색 · 문서 정리 · 여러 파일 변경은 서브 에이전트에게. **필요한 맥락(읽을 파일 · 건드리면 안 되는 파일 · 정해진 결정)을 프롬프트에 다 적는다.** 겹치지 않는 일은 한 번에 여럿. 결과는 **직접 확인한 뒤** 요약한다.
- 예외 — 사용자가 **묻기만 한 것**(설명 · 확인 · 의견)은 바로 답한다.
- 무엇부터 만들지는 `README.md` "만드는 순서"(상태는 `START_HERE.md` §1 · §3, 물을 것은 §4). 순서를 바꾸려면 먼저 묻는다.
- 완료 조건(`matching/CLAUDE.md` §6): happy path / 실패 · 중복 · timeout / 테스트 / 로그 · metric / 계약 불일치 없음(또는 기록) / 불변식 검증.

운영 규칙:
- **포트 6379 · 5432 와 `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다 — 절대 건드리지 마라.** 테스트용 Redis/PostgreSQL 은 다른 포트(6380 · 5433)로 띄우고 끝나면 끈다.
- WSL(mirrored 네트워크) + Windows Docker Desktop — WSL 에서는 **`docker.exe`**. 출력에 `\r` 이 섞이니 비교 전 `tr -d '\r'`.
- **WSL 에서 Chrome · Chromium 을 띄우지 않는다**(루트 `CLAUDE.md` §0). 브라우저 일은 Windows 터미널의 Claude 가 한다.
- `bootRun` 은 **반드시 끈다** · 끝나면 `./gradlew --stop` · 빌드 · 테스트는 `backend/` 안에서. `pkill -f` 금지.
- **옆 폴더(`matching` · `notification` · `frontend`)의 파일은 여기서 고치지 않는다.** 읽기만.
- Claude 가 파일을 고친 뒤 IntelliJ 에서 **`Ctrl+Alt+Y`**(디스크에서 다시 읽기)를 눌러야 한다 — 안 누르면 IntelliJ 가 옛 버전으로 덮어쓴다. 소유자가 저장하지 않은 코드는 Claude 가 볼 수 없다.
- **셸은 zsh** — `qm:room:$1:host` 는 `$1:h` 로 깨진다. `${1}` 처럼 감싼다.
- worktree 의 `.git` 파일에 `/mnt/c/…` 경로가 있어 Windows git · IntelliJ 는 저장소로 못 읽는다 — **커밋은 WSL 에서.**
- `notification` 을 띄운 직후 첫 SSE 구독까지 **7초쯤** 걸렸다 — `PUBSUB NUMSUB` 로 구독자 1 을 본 뒤 움직인다.
- 명령과 그 밖의 함정은 `docs/LOCAL_ENV_LESSONS.md`.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 시작 안내 — 상태 · 순서 · 물을 것 · 로컬 띄우기 | `START_HERE.md` |
| 이 폴더의 계약 + P-1 ~ P-52 | `contracts/platform-api.md` |
| **줄이기 전의 이 파일(날짜별 결정 · 옛 규칙 · 이유)** | `docs/CLAUDE_HISTORY.md` |
| ERD(어긋나면 마이그레이션이 맞다) | <https://claude.ai/artifact/LBngVYThyCjipLUkatC6Bq> |
| 로컬 환경 함정 | `docs/LOCAL_ENV_LESSONS.md` |
| 매칭 엔진 규칙 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 배달 규칙 | `…/queuemate/notification/CLAUDE.md` |
| 옛 `room` 앱의 규칙 · 계약 · 결정(참고만 — 근거로 쓰지 마라) | `git show origin/room:CLAUDE.md` · `origin/room:contracts/room-api.md` · `origin/room:docs/DECISIONS.md` |
| 결정 로그 — #13 ~ #27 · D-1 ~ D-57(D-16 · D-19 ~ D-23 은 두 앱 전제 — D-33 이 개정. D-42 가 #18 · #21 · D-13 을 개정) | `…/queuemate/matching/docs/11_DECISION_LOG.md` |
| 알림 계약(SQS 절은 기록) | `…/queuemate/matching/contracts/events.md` |
| 계약 사본의 지위 · 자원 목록 | `…/queuemate/matching/contracts/README.md` · `openapi.yaml` |
| 봉투 · 채널 접두사 원본 | `…/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `…/redisKeys/SharedKeys.java` |
| `matching` 이 읽는 `blocks` | `…/matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `…/src/test/resources/schema.sql` |
| 왜 PostgreSQL(§3 의 스키마 배치는 개정됐다) · 배포 그림 | `…/matching/docs/WHY_POSTGRESQL.md` · `AWS_ARCHITECTURE.md` |
| 제품 정의 · 예약 규칙 | `…/matching/docs/00_PRODUCT_SPEC.md` · `04_RESERVATION_MATCHING_SPEC.md` |
| 버전 · 설정 본보기 | `…/matching/backend/build.gradle` · `application.yaml` |
| `matching` 의 상태 | `…/matching/HANDOFF.md` §0-6 |

## 11. 이 저장소에서 하지 말 것 (요약)

- **매칭 로직 · 매칭 Redis 키 접근**(예외 둘 — `qm:gameconfig:*` 읽기 · `qm:party:{partyId}` 의 `HGETALL` · `HEXISTS` — 제안 중 포함. `qm:user:block-rel:*` 는 이 앱의 키다 — §3.7). **`qm:party:*` 에 쓰기 · 지우기 · `EXPIRE`.** 활성 요청 키에 `EXISTS` 말고 무엇이든. `SseEmitter` / WebSocket / 연결 보유.
- **방을 Lua 서비스 밖에서 바꾸기**(맨손 `HSET` · `SET`) · **`party` 가 방 키를 직접 읽고 쓰기** · 글 쓰기 · 방장 확정 밖에서 트랜잭션 안에 Redis 호출 · 게시판 방(글 쓰기) · 자동 매칭 방(`POST /api/v1/match-parties/{partyId}/room`) 밖의 방 만들기 요청 · 자동 매칭 방에 `POST /rooms/{roomId}/members` 나 방장 확정을 쓰기 · **자동 매칭 방의 Lua 가 활성 요청 키를 보게 하기** · 자동 매칭 방을 만들 때 DB 와 Lua 를 한 트랜잭션에 묶기 · **확정된 글을 방장 키가 없다고 만료시키기** · **방 키를 못 읽은 것을 "방이 없다"로 읽기**.
- **입장 금지 · 자동 합류 건너뛰기 목록을 Lua 밖에서 쓰기** · `party` 가 그 목록을 직접 읽기(`RoomService#noAutoJoinRooms`) · 스스로 나간 사람의 직접 입장 막기 · 자동 매칭 방이 금지 목록을 보게 만들기(미정).
- 예약 · TURN credential · **gameconfig 모듈**(정하고 · 심고 · 해석하기 — 이 앱은 §3.6 의 필드를 읽기만).
- 경로 · 스키마 · payload · 컬럼을 **지어내기** · 코드만 바꾸고 계약을 안 고치기 · 운영 DB 가 생긴 뒤 적용된 마이그레이션 고치기 · 남은 미정(§7 · §7.1)을 임의로 정해 구현하기.
- 공개 사용자 탐색 · 길드 · 피드 · 팔로우 · 좋아요 · 모집과 무관한 공개 채팅방.
- 채널 접두사 · 게시판 채널 이름 · D-19 키 이름 · gameconfig 접두사 · 파티 HASH 접두사를 한쪽만 바꾸기 · 방 키 상수를 `RoomKeys` 밖에 또 적기 · **`mode` · `tier` · 모드 인원의 값 목록을 상수로 베끼기** · seed 를 이 앱이 심기 · gameconfig 를 읽으려고 `matching` 을 HTTP 로 부르기 · 알림 실패로 본 작업을 실패시키기.
- 모집 중이 아니거나 차단 관계인 글의 방에 들여보내기(입장의 글 검사 건너뛰기 — **차단은 방 안 전원과 본다**) · 게시판 신호에 데이터(`roomId` · `game` · 프로필) 싣기 · 목록을 그리려고 `GET …/members` 부르기.
- **포지션**(P-44) — `:needs` 를 Lua 밖에서 바꾸기 · 포지션을 돌려놓을 때 수명 없이 `SADD` · `""` 를 `:needs` 에 넣기 · `:needs` 의 유무로 포지션 방을 가르기 · 입장의 포지션 판정을 자바에서 하기 · 들어온 사람의 포지션을 바꾸게 하기 · 멤버 키를 다시 SET 으로 쓰기(`opsForSet` · `SADD`).
- **전적 갱신 요청 · 그 쿨타임 되살리기**(P-42) · 로그인 때 다시 받기를 **요청 스레드에서 기다리거나 · 그것 때문에 로그인 · 재발급을 실패시키거나 · 1시간 기준 없이 긁거나 · GET 에서 긁기** · 로그인 때 다시 받기를 연결의 풀에 던지기 · 타이머로 도는 갱신을 지어내기.
- **LoL · PUBG 의 `tier` 를 요청으로 받기** · 티어를 사다리 없이 한 칸으로 되돌리기 · 모드 이름으로 사다리를 가르기 · PUBG 시즌 캐시를 프로세스 로컬에 두기 · PUBG 429 재시도 · 긁기에 실패했는데 저장하기 · **`mainPosition` 되살리기 · 오는 값을 조용히 버리기** · Riot 최근 경기에서 포지션 뽑기 · **모스트 챔피언에 판 수 · 승률 · CS 되살리기** · **`recentResults`(P-43)를 채우려고 Riot 을 더 부르거나 `games` 와 다른 경기 목록으로 만들기** · **커스텀 게임(`queueId` 0 · `CUSTOM_GAME`)을 전적에 넣기 · 커스텀을 뺀 만큼 Riot 을 더 불러 채우기**(P-49).
- **`hostPosition` 을 카드로 옮기기** · 포지션이 없는 모드에서 오는 `hostPosition` · `wantedPositions` 를 조용히 버리기 · 포지션이 있는 모드에서 빈 `wantedPositions` 받기 · 모드 이름으로 "포지션이 있는 모드" 가르기(`positionUniqueness` 만 본다).
- **확정된 글의 `members` 를 방 안 사람으로 되돌리기 · 파티원 카드를 차단 대조 없이 내보내기 · 파티원을 글마다 따로 읽기.**
- **게시판 방의 정원을 상수 5 로 되돌리기 · 입장 · 목록마다 gameconfig 를 다시 읽기** · 자동 매칭 방의 정원(HASH 의 `target`)을 글의 규칙으로 바꾸기.
- 글 고치기(`PATCH /api/v1/posts/{postId}`) 되살리기(P-45) · 글의 `purpose` 되살리기(`matching` 의 `PlayPurpose` 는 그쪽 것이라 건드리지 않는다) · `parties` 에 `playPurpose` 칸 두기 · 게시판 방 먼저 합류에 활성 요청 키를 만들거나 `matching` 을 부르기 · **빠른매치 입장을 금지한 글(`allowAutoJoin = false` — P-50)을 자동 합류에 넣기 · 그것을 자바에서 거르기(후보 SQL 에서 거른다) · 글 쓰기에서 `allowAutoJoin` 에 기본값을 주기(필수다)** · 그 요청의 gameconfig 읽기를 fail-open 으로 바꾸기 · PUBG `PLATFORM` ↔ 방장 `server` 대조를 지어내기 · 소셜 계정을 이메일 등으로 자동으로 합치기.
- `filledPositions` 나 그 대안을 지어내기.
- 파티 닫힘을 SQS 로 돌리기 · 방장 키만 없다고 파티를 닫기 · `PARTY_*` 알림 지어내기 · **outbox · SQS · AWS SDK · Kafka/RabbitMQ/Redis Streams 들이기.**
- `blocks` 모양을 한쪽만 바꾸기 · 스키마를 다시 나누거나 DB 롤 · `GRANT` 두기 · `matching` 이 DB 를 다시 읽게 만들기(D-57).
- **차단 관계 사본**(§3.7 · P-52) — `BlockRelationRedis` 밖에서 쓰기(맨손 `SADD` · `SREM`) · Redis 를 못 고쳤는데 차단 · 해제를 커밋하기 · 해제에서 반대 방향 줄을 안 보고 빼기 · 한쪽 집합만 고치기 · 사본을 원본으로 읽기(차단 목록 · 게시판 거르기는 DB) · 재구성 실패로 기동을 막기 · `BLOCK_REL_PREFIX` 를 한쪽만 바꾸기 · 차단 · 해제 · 재구성에서 줄 서기(`lockRelationCopy`)를 빼기.
- **회원 탈퇴**(P-48 · `DELETE /api/v1/auth/account`) — 확정된 글 · 파티 · 남의 파티원 줄을 지우기 · 방장이 빈 비확정 글을 남기기(비확정 글을 `users` 와 다른 트랜잭션에서 지우기) · 탈퇴에서 `matching` 의 활성 요청 키를 지우기 · Redis 를 못 읽었는데 지우기 · 방에 있는 사람을 평소 나가기 없이 지우기 · refresh 를 `KEYS`/`SCAN` 으로 찾아 지우기 · refresh 를 못 지웠다고 탈퇴를 실패시키기 · 탈퇴를 access 토큰 없이 받기(`/api/v1/auth/**` 의 `permitAll` 에 묻히게 두기) · refresh 쿠키의 `Path` 를 `/api/v1/auth` 보다 넓히기 · 방장이 빈 글의 `host` 에 빈 카드를 지어내기 · 제공자 unlink 를 지어내기.
- 사용자 번호 말고 다른 것을 식별자로 쓰기 · **직접 가입 · 비밀번호 · `loginId` · 로그인 실패 제한 되살리기** · 새 테이블 PK 를 `bigint identity` 말고 두기 · 숫자가 아닌 id 를 400 으로 갈라 주기(없는 사용자와 같은 404).
- `조회 → 판단 → 삽입` · H2 로 제약 검증했다고 치기 · 경계를 넘는 새 동기 호출.
- 인증 — HS256 · JWKS · access denylist · CSRF 토큰 · 서비스에 CORS · **상태를 바꾸는 GET** · 토큰에 바뀌는 값 · jjwt 등 · `oauth2-client` · **`token_use` 를 안 보고 받기** · `TokenClaims` 값을 한쪽만 바꾸기 · `.dev-keys/` 커밋 · refresh 를 JWT 로 · `KEYS`/`SCAN` 으로 refresh 훑기 · 재발급 실패를 이유별로 갈라 알려 주기.
- **개발용 로그인을 운영에서 켜기** · `TEMP-DEV-LOGIN` 표식 없이 늘리기 · 검증하는 쪽을 느슨하게 하는 스위치로 바꾸기.
- k8s/HPA/sticky session 전제 구현 · 포트 6379 · 5432 / `queuemate-v2-*` 조작 · 안 끈 `bootRun` · 옆 폴더 파일 수정.
