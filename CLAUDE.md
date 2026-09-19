# CLAUDE.md — platform 서비스 규칙 (Non-Negotiable)

작업 전에 이 파일과 `README.md`를 읽어라.

이 폴더는 QueueMate의 **API 서버 배포 단위 하나**다. 문서에서 **`app:platform`**이라고 부르는 것이 이것이다
(docs/11 #15). 매칭 엔진 규칙은 옆 폴더 `matching`의 `CLAUDE.md`에, 알림 배달 규칙은 `notification`의
`CLAUDE.md`에 있고, 이 파일은 **계정·파티·소셜·예약 REST에 걸리는 부분만** 담는다.

> **출처 표기.** `docs/…` · `contracts/…` · `HANDOFF.md`처럼 폴더 이름 없이 적은 것은 전부 옆 폴더
> `matching` 기준이다(절대 경로는 §10). **출처가 안 붙은 사실은 정해지지 않은 것이다** — §7로 보낸다.

---

## 1. 제품 경계

QueueMate는 **조건 기반 팀원 자동 랜덤 매칭** 서비스다 — "조건은 사용자가 정하고, 사람 선택은
시스템이 한다"(docs/00 §1). 이 서비스는 매칭의 **앞(계정·예약 등록)과 뒤(파티룸·친구·차단·신고)**를 맡는다.
자동 매칭이 **기본 경로**이고, 이 서비스에 들어오는 **파티 모집 게시판이 두 번째 경로**다 (docs/11 D-11).

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (account · party · social · reservation)
                                 │  ▲
                                 │  └── SQS ProposalConfirmed.fifo ◀── matching   (파티를 만들어라)
                                 │      (차단은 SQS 로 알리지 않는다 — matching 이 social.blocks 를 직접 읽는다)
                                 └──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
```

- 지원 게임은 **LoL, VALORANT, PUBG 셋뿐**이다 (docs/11 #8). **상대팀/VS/대전 상대를 만들거나 보여주지 않는다** (#9).
- 공개 사용자 탐색, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다
  (`matching/CLAUDE.md` §1 · docs/11 #14 · docs/00 §6).
  - **예외 하나 — 파티 모집 게시판은 허용되고 이 서비스의 일이다** (docs/11 D-11이 #14와 docs/00 §6의
    "게시판/LFG 글 작성"을 개정했다). 정해진 것과 정할 것은 §7.1.
  - 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이다. 사람을 검색하고 둘러보는
    공개 사용자 탐색은 여전히 금지다.
  - "공개 채팅방"은 **파티 모집과 무관한 잡담용 공개방**을 뜻한다. 모집 글에 딸린 방은 그 예외다.
- 프리미엄/과금 기능을 구현하지 않는다. 친구 / 차단 / 최근 함께한 사람 / 신고는 필수다 (docs/11 #13·#14).
- **매칭 로직이 하나도 없다.** 매칭·제안·수락·확정은 전부 `matching`의 일이다.

## 2. 하는 일 / 하지 않는 일

| 한다 | 출처 |
|---|---|
| **계정** — 회원가입/로그인/로그아웃, 기본 프로필, 게임 계정 연결/해제. **인증 토큰 발급** — access는 **쿠키로 주고받는 JWT**, refresh는 **Redis에 저장하는 불투명 UUID**(재발급·폐기도 이 앱) | docs/00 §5 · docs/11 #16 · D-14 · docs/AWS_ARCHITECTURE §3 |
| **파티** — `ProposalConfirmed.fifo`를 소비해 **DB에 파티를 만든다.** 파티룸(파티원·게임 ID·Ready·나가기). 파티가 닫히면 `PartyClosed.fifo` 발행 | docs/11 #21 · docs/00 §5 · `matching/CLAUDE.md` §9 |
| **파티 모집 게시판** — 모집 글을 올리면 **그것이 곧 파티방**이다(자동 매칭으로 확정된 뒤 생기는 파티방과 같은 개념의 방). 다른 사용자는 글을 **눌러서 바로 입장**하고(승인 없음, **둘러보러 온 상태**이지 파티원이 아니다) 방 안에서 **음성(WebRTC, 브라우저 직결)**으로 말을 건다. 방장 포함 **최대 5명**, 방장은 **강퇴**할 수 있고, **파티원은 방장이 확정**하며(확정하면 모집이 닫힌다), 방장이 나가면 글은 **만료**된다. **차단 관계가 있는 방은 목록에 보이지 않는다.** 자동 매칭 대기와 방은 **한 번에 하나만**이다. 규칙과 미정은 §7.1 | docs/11 D-11 |
| **소셜** — 친구 요청/수락/거절/삭제, 차단/해제, 신고, 최근 함께한 사람. **`social.blocks`의 소유자**다. 차단은 **DB에 저장하는 것으로 끝낸다** — `BlockChanged.fifo`는 만들지 않는다(§3.4) | docs/00 §5 · D-1 · D-2 · D-12 |
| **예약 REST** — `/api/v1/reservations` 등록·조회·수정·취소 + **INV-9**(시간이 겹치는 활성 예약 금지) 검증. 등록은 **DB에 쓰고 끝난다** — 직후에 후보를 찾지 않는다. 슬롯 시작까지 30분 미만이면 `400` | docs/11 #23·#24 · docs/04 §6 |
| **알림 발행** — `PARTY_*`·`FRIEND_*` 7종과 `WEBRTC_SIGNAL` 1종 | contracts/events.md · docs/11 D-9 |
| **WebRTC 시그널 받기** — REST `POST`로 받아 파티원(게시판 방에서는 같은 방에 들어와 있는 사람, §3.3) 확인 뒤 상대 채널에 발행 | docs/11 D-9 · D-11 |

platform 소관 자원(contracts/openapi.yaml 머리말): `auth` `users` `reservations` `parties` `friends` `blocks` `recent-players` `reports`. **경로와 스키마는 거기 없다**(§3.1).

| 안 한다 | 왜 / 누가 |
|---|---|
| 매칭·제안·수락·확정, 매칭 Redis 키(`qm:party:*` `qm:user:*` `qm:proposal:*` `qm:gameconfig:*` `qm:lock:*`) 접근. **예외는 하나 — 활성 요청 키 `qm:user:active-request:{userId}`를 방 입장 때 쓰고 나갈 때 지운다**(D-11 16번, §7.1). 나머지는 읽지도 쓰지도 않는다 | `matching`의 일이다. 진행 중 매칭 상태의 원본은 Redis이고 그 주인은 `matching`이다 (docs/11 #27) |
| 브라우저 연결 보유 — `SseEmitter` / WebSocket | `notification`의 일이다. 이 앱은 **stateless REST**로 남아야 무중단 교체가 자유롭다 (docs/11 #15 · docs/14 §6). WebSocket은 어느 앱에도 없다 (D-9) |
| 예약 **짝 찾기**, `RESERVATION_*` 발행 | `app:reservation-batch` (docs/11 #23·#24). 여기는 예약 **데이터 CRUD**까지다 |
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

### 3.3 WebRTC 시그널링 (docs/11 D-9 · contracts/events.md "`WEBRTC_SIGNAL` 의 전달")

- WebSocket은 없다. 클라이언트가 시그널을 **보내는** 길은 이 서비스의 REST `POST`, **받는** 길은 SSE다.
  `POST`를 받으면 **보내는 사람과 받는 사람이 같은 파티원인지 확인한 뒤** `qm:pubsub:push:{상대 userId}`에
  `type: "WEBRTC_SIGNAL"` 봉투를 발행한다. 파티를 소유한 앱이 여기라서 확인할 수 있는 곳이 여기다.
  **게시판 방에서는 "같은 방에 들어와 있는 사람인지 확인"이다** — 들어온 사람은 파티원이 아니라 둘러보러 온
  상태이고, 확정 전에도 음성에 붙어 말을 걸 수 있어야 하기 때문이다(D-11이 D-9의 "파티원"을 그렇게 읽도록 개정했다.
  자동 매칭 파티방은 D-9 그대로다). 방장이 확정한 뒤의 방에서는 둘이 같아질 수 있으나 확정의 세부가 미정이다(§7.1).
- **SDP/ICE 내용을 해석하지 않는다.** 저장하지도 않는다. 여러 `POST`의 도착 순서는 보장되지 않고,
  ICE 후보마다 `POST`가 나가므로 **HTTP/2를 전제**한다.
- 놓친 시그널의 **복구는 클라이언트 책임**이다(재-offer, ICE restart). 기준점은 이 서비스가 가진 **파티원 목록**(게시판 방은 방에 있는 사람 목록)이다.
- **경로, 요청/응답 스키마, `payload` 스키마는 미정이다** → §7. `POST /api/v1/parties/{partyId}/signals`는 후보일 뿐이고,
  `PUBLISH` 반환값(그 순간 구독자 수)을 `POST` 응답에 실어 주는 것도 *제안*일 뿐이다.

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
  `social`(friend_requests, friendships, blocks, reports, recent_players, outbox) · `reservation`(reservations —
  롤을 `app:reservation-batch`와 같이 쓴다, #24). **컬럼은 이 컴퓨터의 문서에 없다** → §7.
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
| 저장소 | **PostgreSQL** (docs/11 #4, 근거 docs/WHY_POSTGRESQL.md) + Flyway. Redis는 알림 발행(§3.2) — 그 밖의 용도는 §7 |
| 기본 포트 | **8082 (제안)** — `matching` 8080, `notification` 8081과 로컬에서 같이 띄우기 위해. 문서에 정해진 값은 없다 |

## 5. 설계 규칙

- **stateless다.** 프로세스 로컬 상태(메모리 세션, 로컬 캐시에 의존한 판정)를 두지 않는다. 설정은 환경변수 +
  기본값으로 받고, `/health/live`·`/health/ready`, SIGTERM graceful shutdown, stdout JSON 로그를 지킨다 (docs/11 #15·#20).
- **경계를 넘는 동기 호출을 새로 만들지 않는다.** 다른 앱에 시킬 일은 outbox → SQS, 사용자에게 알릴 일은
  Pub/Sub이다. 둘을 섞지 마라 — 놓치면 데이터가 어긋나는 것이 SQS, 놓쳐도 조회로 복구되는 것이 알림이다 (docs/14 §7).
- **불변식은 DB가 강제한다.** `조회 → 애플리케이션 판단 → 삽입`으로 지키지 마라. INV-9는 exclusion
  constraint(`btree_gist`, `tstzrange … &&`), "같은 사람 두 번 차단 금지"는 UNIQUE다 (docs/WHY_POSTGRESQL §1).
  **H2는 스키마별 롤/GRANT를 재현하지 못하고**(docs/11 D-3) exclusion constraint도 없다 — 이 검증은 PostgreSQL에서만 의미가 있다.
- **인증 — access 토큰은 JWT이고 쿠키로 주고받는다**(`Authorization` 헤더에 싣지 않는다). **refresh 토큰은 JWT가 아니라 불투명 UUID이고
  Redis에 저장한다**(UUID → 사용자. 폐기는 지우면 끝) (docs/11 #16 · D-14). **rotation 필수** — 재발급 때 옛 UUID를 지우고 새 UUID를 준다.
  access TTL은 짧게. #16의 access용 Redis denylist(조회 실패는 fail-closed)를 둘지는 미정이다 — 세부는 §7.
- **Spring Security는 인증 세부(§7)를 정하면서 넣는다** — 먼저 넣으면 기본 설정이 모든 요청을 막는다.

## 6. 배포 기준

현재 배포 기준은 **Stage 1(단일 EC2 + Docker Compose)**다 (docs/11 #20). **k8s / HPA / sticky session을 전제한 구현 금지.**

## 7. 미정 사항

정하기 전에 임의로 구현하지 말고 사용자에게 물어라. 정해지면 이 표에서 빼고 해당 절로 옮긴다.

| 항목 | 상황 |
|---|---|
| **파티 모집 게시판의 세부** | 하는 것과 방의 규칙은 정해졌다(docs/11 D-11, §7.1). **남은 것** — 방장 확정의 세부, 차단을 보는 범위, **활성 요청 키에 무엇을 어떻게 쓰고 언제 지우는가**, 재입장·만석·방장 이탈 판단·만료 글 보존 기간, 도배 대응 등. 개발하면서 정한다 — 해당 지점에 닿으면 그때 묻는다 |
| 시그널 `POST` | 경로, 요청/응답 스키마, 구독자 수 응답 여부. `WEBRTC_SIGNAL` `payload`에 **정해야 할 항목**: 보낸 사람 식별자 / 파티 식별자 / 종류(offer·answer·ICE candidate) / SDP 또는 candidate 본문 / 재협상 시도를 구분할 식별자 (D-9) |
| 모든 엔드포인트의 경로·스키마, `PARTY_*`·`FRIEND_*` 7종의 이름과 `payload` | 원본 계약이 이 컴퓨터에 없다(§3.1). 문서에 이름이 나오는 것은 예시로 든 `PARTY_MEMBER_JOINED` 하나뿐이다 (contracts/events.md) |
| SQS 메시지 본문 3종 | `ProposalConfirmed`에 무엇이 실려 오는지, 파티 id를 `proposalId`와 같게 둘지 — `matching`은 클라이언트에 `MATCH_CONFIRMED {partyId}`(= `proposalId`)를 **이미 내려 주고**, 파티는 비동기로 생기므로 그 직후 조회는 비어 있을 수 있다 (contracts/events.md "`PARTY_CREATED` 이벤트가 없다") |
| **확정된 사용자를 푸는 길** | `matching`은 확정된 사용자의 활성 요청에 `status=PARTY`를 찍어 두고, 푸는 주체가 없다. 후보 셋(`PartyClosed` 소비 / 나가기 API / 긴 TTL)이 **결론 나지 않았다** (HANDOFF.md ①). `PartyClosed.fifo`의 소비자는 platform 하나로 확정했다(§3.4) — 그래서 **`matching`의 `status=PARTY`를 누가 어떻게 푸는지는 여전히 열려 있다.** D-11 16번으로 platform이 활성 요청 키를 쓸 수 있게 됐으므로 **파티가 닫힐 때 platform이 그 키를 지우는 것이 한 가지 해법이 될 수 있다 — 가능성일 뿐이다.** 게시판 방에서 "방장 확정 뒤 언제 지우는가"(§7.1)와 같은 문제다 |
| 테이블 컬럼, DB 롤 | 롤은 이름(`matching`은 `qm_matching`), 한 앱이 네 스키마를 어떤 롤로 붙는지, 롤/GRANT를 누가 만드는지 |
| 인증 세부 | 방식은 정해졌다(§5, docs/11 D-14). **남은 것** — ① 쿠키 속성(`HttpOnly`·`Secure`·`SameSite` Lax/Strict·`Path`·`Domain`·수명, refresh 쿠키의 `Path`를 재발급 경로로 좁힐지) ② **CSRF 대응**(`SameSite`만인가, CSRF 토큰·`Origin` 검사를 더하는가 — 시그널 `POST`·방 입장·차단·매칭 요청이 전부 해당) ③ `matching`·`notification`이 access 토큰을 검증하는 법(HS256 비밀 키 공유 / RS256 공개 키 검증, 키를 나눠 갖는 법, 클레임 구성) ④ access·refresh 수명, access denylist를 둘지 ⑤ refresh의 Redis 키 이름·값·TTL(매칭 키와 겹치지 않는 접두사), 기기별 허용 개수, 옛 값 재사용(탈취 신호) 처리 ⑥ SSE와 토큰 만료 — `EventSource`는 200이 아닌 응답에 재접속을 멈춘다 ⑦ 로컬 개발의 CORS·`credentials`·`withCredentials` ⑧ 인증이 붙기 전 임시 식별(두 서비스는 지금 `userId` 파라미터를 받는다)과 전환 시점 |
| 게임 계정 연동 | 라이엇 등 외부 API 연동 범위. `matching`의 티어는 지금 자기신고다 (`matching/CLAUDE.md` §2) |
| 예약 모듈 공유 | #24는 `module:reservation`을 두 앱이 함께 의존하는 라이브러리로 적지만, 지금은 브랜치마다 단일 모듈이다 |
| Redis의 다른 용도 | 파티 presence/ready(`qm:party:presence:*`·`qm:party:ready:*`), rate limit은 원본 docs/07의 **키 이름만** 있다. 누가 쓰는지, `matching`의 `qm:party:*`와 접두사가 겹치는 것을 어떻게 할지 |
| SQS 배선 시점, 테스트용 PostgreSQL, 포트 | `ProposalConfirmed` 소비와 `PartyClosed`에 AWS SDK를 언제 들일지(`matching` 쪽 발행이 아직 없다). 테스트용 PostgreSQL을 띄우는 법. 기본 포트 8082 확정 |

### 7.1 파티 모집 게시판 — 정해진 것과 정할 것 (docs/11 D-11)

**정해진 것.**
- 모집 글을 올리면 **그것이 곧 파티방**이다. 자동 매칭 뒤에 생기는 파티방과 **같은 개념의 방**을 쓴다(따로 만들지 않는다). 목적은 **모집의 응답성**이다 — 글·메시지로 응답을 기다리는 대신 말을 걸고, 답이 없으면 바로 나가 다른 방을 찾는다.
- 글을 **누르면 바로 들어온다**(방장의 승인/거절 없음). 들어온 것은 **"둘러보러 온 상태"이지 파티원이 아니다.** 방장은 **강퇴**할 수 있다.
- **파티원은 방장이 확정한다.** 방장이 확정하면 그 방에는 **더 이상 새 사람이 들어올 수 없다**(모집이 닫힌다).
- **차단 관계가 있으면 그 방은 목록에 아예 보이지 않는다**(막는 것이 아니라 보이지 않게). **어느 쪽이 차단했든** 같다(INV-6과 같은 방향성). 검사는 `social.blocks`를 가진 **이 앱이 목록을 만들 때** 한다 — 같은 앱 안의 조회라 서비스 경계를 넘지 않는다.
- **한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만** 할 수 있다(INV-2의 취지). **지키는 자리는 `matching`의 활성 요청 키
  `qm:user:active-request:{userId}`(HASH) 하나다** — 입장 때 이 앱이 그 키를 쓰고(**이미 있으면 입장 거절**) 나갈 때 지운다. 방에 있는 사용자의
  매칭 요청은 `matching`의 기존 검사(`redis/shared/claim-request.lua`의 `EXISTS`)가 그대로 거절한다. 서비스 간 호출이 없어 §5와 부딪히지 않는다.
  **이 앱이 만지는 매칭 키는 이것 하나뿐이다.** 접두사 원본은 `matching`의 `SharedKeys.ACTIVE_REQUEST_PREFIX`다 — 알림 채널 접두사(§3.2)와
  같은 위험이다. 오타를 내면 테스트가 통과한 채로 검사가 조용히 무력화된다. **상수 한 곳에만 두고 원본을 주석에 적는다.**
- **최대 인원은 방장 포함 5명**이다(둘러보는 사람도 센다 — 방의 전원이 음성 메시에 붙으므로 이 값이 곧 음성 인원 상한이다). **방장이 나가면 글은 지우지 않고 "만료"로 표시한다.** 목록에 남지만 눌러도 들어갈 수 없다.
- **음성 제어** — 자기 마이크 켜고 끄기, 특정 사람 소리 개별로 안 듣기/다시 듣기. *(구현 메모)* 둘 다 브라우저에서 끝난다 (자기 오디오 트랙 끄기 / 그 사람의 오디오 재생 끄기). 서버가 음성을 중계하지 않으므로 **서버 API가 필요 없다.**
- 시그널 권한 확인은 "같은 방에 들어와 있는 사람"이다(§3.3). `notification`은 바뀌지 않는다.

**정할 것 — 개발하면서 정한다.** 구현하다 해당 지점에 닿으면 **그때 묻고 정한다.** 임의로 정해 구현하지 마라.
- **방장 확정의 세부** — 대상이 그 순간 방에 있는 **전원**인가 방장이 **고른 사람만**인가(고르지 않은 사람은 나가게 되는가). 확정된 글의 목록 표시(만료와 같은가). 확정된 방이 자동 매칭 파티방과 **같은 기능(Ready 등)**을 갖는가, "최근 함께한 사람"을 확정된 파티원 기준으로 기록하는가. 확정 뒤 빈자리가 생기면 다시 모집을 열 수 있는가.
- **차단을 보는 범위** — **방장과의 사이에서만**인가 **방 안의 누구와든**인가. 목록을 본 뒤 차단이 생긴 경우, 같은 방의 두 사람 사이에 차단이 생긴 경우.
- **활성 요청 키의 세부**(사실 확인은 D-11 "아직 미정") — ① **무엇을 써 넣는가**: `matching`의 필드(`requestId`·`game`·…·`partyId`)가 게시판 입장에는 없다.
  ② **"없으면 쓴다"의 원자성**: HASH라 `SET NX`가 안 되고, 대기 중인 요청에는 `status` 필드가 없어 `HSETNX status`도 답이 아니다(`matching`은 Lua 하나로
  `EXISTS`→`HSET`→`EXPIRE 60`을 묶는다). ③ **`matching`이 이 값을 읽을 때**: 지금 코드는 상태 조회가 `QUEUED`로 답하고 취소는 필드가 없으면 예외다 —
  `matching` 쪽 읽는 코드를 손봐야 할 수 있다. ④ **안 지워지는 경우**: 이 앱이 죽으면 그 사용자는 매칭도 입장도 못 한다. TTL·갱신 여부.
  ⑤ **언제 지우는가**: 나가기·강퇴·만료는 분명하다. 방장 확정 뒤는 미정 — §7 "확정된 사용자를 푸는 길"과 같은 문제다.
- 강퇴당한 사람의 **재입장**, **만석**일 때 목록 표시와 눌렀을 때의 동작, 만료된 글의 **보존 기간**, 방장이 나간 것을 **무엇으로 판단하는가**(명시적 나가기만인가, 연결 끊김도인가, 얼마나 기다리는가).
- 글에 담는 것(게임·모드·원하는 조건·한 줄 소개 등), 목록의 정렬·필터. **도배 글 대응**과 신고(docs/11 #13)의 연결.
- **알림 종류** — 기존 `PARTY_*` 재사용인가 새 `type`인가(이름이 이 컴퓨터의 문서에 없다, §3.2). 모든 엔드포인트 경로·스키마·테이블.

## 8. 저장소 구성과 커밋 규칙

`matching`과 **같은 GitHub 저장소의 `platform` 브랜치**다. 이력이 없는 별도 브랜치에서 시작했고, 로컬에서는 `git worktree`로 나란히 둔다.

```
queuemate/
├── matching/       main 브랜치
├── notification/   notification 브랜치
└── platform/       platform 브랜치 (이 폴더)
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
- **옆 폴더 `matching`, `notification`의 파일은 여기서 고치지 않는다.** 읽기만 한다. 고칠 것은 그 폴더에서 따로 작업한다.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 매칭 엔진 규칙 (제품 경계·INV·Contract first의 원형) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/CLAUDE.md` |
| 알림 배달 규칙 (받는 쪽이 메시지를 어떻게 다루나) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/notification/CLAUDE.md` |
| 결정 로그 — #13~#17 · #20~#26 · D-1~D-4 · **D-9** · **D-11**~**D-14** | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/11_DECISION_LOG.md` |
| 알림 계약 (봉투·발행 주체·`WEBRTC_SIGNAL`·SQS FIFO·계약 구멍) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/events.md` |
| 계약 사본의 지위와 앞서간 변경을 적는 법 / platform 소관 자원 목록(머리말) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/README.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/contracts/openapi.yaml` |
| 봉투를 만드는 코드(본보기) / 채널 접두사 원본(`PUSH_CHANNEL_PREFIX`) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/notification/PushPublisher.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/redisKeys/SharedKeys.java` |
| `matching`이 읽는 `social.blocks`의 모양 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/java/com/queuemate/matching/block/Block.java` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/test/resources/schema.sql` |
| 왜 PostgreSQL인가·스키마 배치·outbox·INV-9 제약 / 배포 그림에서 platform의 자리 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/WHY_POSTGRESQL.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/AWS_ARCHITECTURE.md` |
| 제품 정의와 non-goals / 예약 규칙 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/00_PRODUCT_SPEC.md` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/docs/04_RESERVATION_MATCHING_SPEC.md` |
| 버전 맞추기, 설정 본보기 | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/build.gradle` · `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/backend/src/main/resources/application.yaml` |
| `matching`이 platform을 기다리는 일 (① 파티 풀기 ③ outbox) | `/mnt/c/Users/kimye/OneDrive/바탕 화면/queuemate/matching/HANDOFF.md` |

## 11. 이 저장소에서 하지 말 것 (요약)

- 매칭 로직, 매칭 Redis 키 접근(예외는 활성 요청 키 하나 — §7.1. 그 접두사를 `matching`과 따로 바꾸지 마라) — `matching`의 일이다. `SseEmitter` / WebSocket / 연결 보유 — `notification`의 일이다
- 예약 짝 찾기, TURN credential 발급, gameconfig — 각각 다른 배포 단위의 일이다
- 엔드포인트 경로·스키마·payload 필드·테이블 컬럼을 **지어내기** — 묻고, 정한 것은 `contracts/`에 적는다
- 공개 사용자 탐색(사람 검색·둘러보기)·길드·피드·팔로우·좋아요·모집과 무관한 공개 채팅방 — 여전히 금지다(§1 · D-11)
- 게시판 모집의 미정 사항(§7.1)을 **임의로 정해 구현하기** — 구현하다 그 지점에 닿으면 그때 사용자에게 물어라
- 채널 접두사를 `matching`과 따로 바꾸기, 알림 실패로 본 작업을 실패시키기, SDP/ICE 해석·저장, 파티원(게시판 방은 같은 방에 있는 사람) 확인 없는 시그널 발행
- `social.blocks`의 모양을 `matching`과 상의 없이 바꾸기, 크로스 스키마 FK·JOIN, 예외 GRANT 늘리기
- `조회 → 판단 → 삽입`으로 불변식 지키기, H2로 제약·권한을 검증했다고 치기, 경계를 넘는 새 동기 호출
- Kafka/RabbitMQ/Redis Streams, k8s/HPA/sticky session 전제 구현
- 포트 6379·5432 / `queuemate-v2-*` 컨테이너 조작, 띄워 놓고 안 끈 `bootRun`, 옆 폴더 파일 수정
