# platform — QueueMate API 서버

계정·파티·소셜(친구/차단/신고)·예약을 다루는 **REST API 서버**다. 문서에서 `app:platform` 이라고
부르는 배포 단위가 이것이다. 매칭의 앞(계정, 예약 등록)과 뒤(파티룸, 친구, 차단, 신고)를 맡는다.

```
브라우저 ──REST /api/v1/**──▶ platform ──▶ PostgreSQL (account · party · social · reservation)
                                 │  ▲
                                 │  └── SQS ProposalConfirmed.fifo ◀── matching   (파티를 만들어라)
                                 │      (차단은 SQS 로 알리지 않는다 — matching 이 social.blocks 를 직접 읽는다)
                                 └──── PUBLISH qm:pubsub:push:{userId} ──▶ Redis ──▶ notification ──SSE──▶ 브라우저
```

**아직 코드가 없다.** 지금 이 폴더에는 작업 규칙(`CLAUDE.md`)과 이 파일뿐이다.

## 이 서비스가 하는 일

1. **계정** — 회원가입/로그인, 프로필, 게임 계정 연결. 인증 토큰을 발급한다 — access 는 쿠키로 주고받는 JWT,
   refresh 는 Redis 에 저장하는 불투명 UUID 다(`matching` docs/11 D-14)
2. **파티** — `matching` 이 확정한 제안(`ProposalConfirmed.fifo`)을 받아 DB 에 파티를 만들고, 파티룸
   (파티원·Ready·나가기)을 서빙한다. 파티가 닫히면 `PartyClosed.fifo` 로 알려 "최근 함께한 사람"을 만든다
   (보내는 쪽도 받는 쪽도 이 앱이다 — `matching` docs/11 D-13)
3. **파티 모집 게시판** — 모집 글을 올리면 그것이 곧 파티방이다(자동 매칭 뒤에 생기는 파티방과 같은 개념의 방).
   다른 사용자는 글을 누르면 승인 없이 바로 그 방에 들어오고(둘러보러 온 상태이지 파티원이 아니다), 방 안에서
   음성(WebRTC)으로 바로 말을 건다. 방은 방장 포함 최대 5명이고, 방장은 강퇴할 수 있다. 파티원은 방장이
   확정하고, 확정하면 모집이 닫힌다. 방장이 나가면 글은 "만료"로 남는다. 차단 관계가 있는 방은 어느 쪽이
   차단했든 목록에 보이지 않는다. 한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 —
   방에 들어올 때 `matching` 의 활성 요청 키(`qm:user:active-request:{userId}`)를 쓰고 나갈 때 지워서 지킨다.
   자동 매칭이 기본 경로이고 이것은 두 번째 경로다. 이유는 **모집의 응답성**이다 — 같이 할 사람을 직접 확인하고 싶은 사용자가
   있고, 글·메시지로 응답을 기다리는 것보다 말을 거는 쪽이 빠르다. 답이 없으면 바로 나가 다른 방을 찾는다
   (`matching` docs/11 D-11)
4. **소셜** — 친구, 차단, 신고, 최근 함께한 사람. 차단 테이블(`social.blocks`)의 주인이다 — `matching` 이
   "차단 관계는 같은 파티가 될 수 없다"(INV-6)를 지키려고 이 테이블을 직접 읽는다. 차단은 DB 에 저장하면
   끝이고 SQS 로 따로 알리지 않는다(`BlockChanged.fifo` 폐기 — docs/11 D-12)
5. **예약 REST** — 예약 등록·조회·수정·취소와 "시간이 겹치는 활성 예약 금지"(INV-9)
6. **알림 발행** — `PARTY_*`·`FRIEND_*` 7종과 `WEBRTC_SIGNAL` 을 Redis Pub/Sub 에 발행한다. 봉투
   (`{type, eventId, occurredAt, payload}`)와 채널(`qm:pubsub:push:{userId}`)은 `matching` 의
   `PushPublisher.java` / `SharedKeys.java` 와 같다. 배달은 `notification` 이 한다
7. **WebRTC 시그널 받기** — WebSocket 은 없다. 클라이언트가 REST `POST` 로 보낸 시그널을, 보낸 사람과 받는
   사람이 같은 파티원인지(게시판 방에서는 같은 방에 들어와 있는 사람인지) 확인한 뒤 상대 채널에 발행한다.
   내용(SDP/ICE)은 해석하지 않는다

## 이 서비스가 하지 않는 일

- **매칭을 하지 않는다.** 매칭·제안·수락·확정은 `matching` 의 일이고, 매칭 Redis 키를 만지지 않는다.
  예외는 활성 요청 키 하나다(게시판 방 입장/퇴장 때 쓰고 지운다 — D-11 16번).
- **브라우저 연결을 들고 있지 않는다.** SSE 는 `notification` 의 일이다. 이 앱은 stateless REST 로 둔다.
- 예약 **짝 찾기**는 `app:reservation-batch` 의 일이다. 여기는 예약 데이터 CRUD 까지다.
- 음성·텍스트 채팅을 중계하지 않는다(브라우저 직결). TURN credential 발급도 이 앱의 일이 아니다.
- 공개 사용자 탐색, 길드, 피드, 팔로우, 좋아요, 공개 채팅방을 만들지 않는다. 파티 모집 게시판은 그 예외다
  (D-11) — 게시판이 보여 주는 것은 **사람 목록이 아니라 모집 글(방) 목록**이고, 사람을 검색하고 둘러보는
  기능은 여전히 만들지 않는다. "공개 채팅방"은 파티 모집과 무관한 잡담용 공개방을 뜻한다

## 저장소 구성

`matching` 과 **같은 GitHub 저장소의 다른 브랜치**(`platform`)다. 이력이 없는 빈 브랜치에서 시작했으므로
매칭·알림 코드는 이 브랜치에 없다. 로컬에서는 `git worktree` 로 나란히 둔다.

```
queuemate/
├── matching/       main 브랜치. 매칭 엔진. 결정 로그와 계약 사본이 여기 있다
├── notification/   notification 브랜치. 알림(SSE) 서비스
└── platform/       platform 브랜치 (이 폴더)
    └── backend/    스프링 앱을 둘 자리 (아직 없다). matching/backend/ · notification/backend/ 와 같은 모양
```

기술 스택은 두 서비스와 같은 버전으로 맞춘다 — Java 21, Spring Boot 4.1.1(MVC), Gradle, PostgreSQL.
로컬 기본 포트는 `matching` 8080, `notification` 8081 이고 이 앱은 **8082 를 제안**한다.

## 만드는 순서

자동 매칭으로 확정된 파티를 받으려면 SQS(`ProposalConfirmed.fifo`)가 필요하고, 그러려면 AWS SDK · 메시지 형식
결정 · `matching` 쪽 발행 코드까지 있어야 한다. 반면 **게시판 방은 SQS 없이 platform 과 notification 만으로
끝까지 돈다**(방 만들기 → 입장 → 시그널 → 음성). 그래서 방을 먼저 만들고, 자동 매칭 파티를 나중에 같은 방에 합친다.

| 단계 | 만드는 것 | 끝나면 되는 것 |
|---|---|---|
| 1 | 기반(PostgreSQL · 스키마 · Flyway · 테스트용 DB)과 최소한의 계정(가입 · 로그인 · JWT 발급) | 로그인한 사용자가 생긴다. `notification` 의 남은 인증 미정(토큰 검증 · 만료 시 응답)도 여기서 같이 풀 수 있다 |
| 2 | 차단(`social.blocks`) | `matching` 의 INV-6 검증이 실제 데이터로 동작한다 |
| 3 | 방과 게시판 — 글 쓰기(= 방 만들기), 목록(차단 관계 거르기), 입장, 나가기, 활성 요청 키 쓰고 지우기 | 글을 올리고 눌러서 들어간다. 입장 알림이 SSE 로 간다 |
| 4 | 시그널 `POST` 와 프런트의 WebRTC 음성 | **브라우저 두 개로 음성 통화가 된다.** 눈에 보이는 첫 완성 |
| 5 | 방장 확정 · 강퇴 · 만료 · 5명 정원 · 개별 음소거(프런트) | D-11 의 방 규칙 완성 |
| 6 | 자동 매칭 파티 — `ProposalConfirmed.fifo` 소비(SQS · outbox) | 두 경로가 같은 방으로 합쳐진다. `matching` 쪽 발행도 이때 필요하다 |
| 7 | 친구 · 신고 · 최근 함께한 사람(`PartyClosed`) | |
| 8 | 예약 REST | 짝을 찾는 `app:reservation-batch` 가 있어야 의미가 생긴다 |

미뤄도 되는 것:

- refresh 토큰 교체와 denylist — 처음에는 access 토큰만으로 시작한다
- 게임 계정 연결 — 외부 API 가 걸려 범위가 불어나기 쉽다
- 신고는 접수만 받는 최소 형태로 둔다
- 예약은 맨 나중이거나 뺀다

**Spring Security 는 1단계에서 인증 세부를 정할 때 넣는다** — 그 전에 넣으면 기본 설정이 모든 요청을 막는다.

순서를 바꾸려면 먼저 묻는다(`CLAUDE.md` §9).

## 아직 정할 것

- **엔드포인트의 경로와 스키마 전부.** 계약 원본(queueMate 본 저장소 `feature/frontend`)이 이 컴퓨터에 없고,
  `matching/contracts/` 는 `matching` 몫만 발췌한 사본이라 platform 엔드포인트가 없다. 지어내지 않는다 —
  정한 것은 이 폴더의 `contracts/` 에 적고 나중에 원본에 올린다
- **시그널 `POST`** 의 경로·요청/응답, `WEBRTC_SIGNAL` 의 payload
- `PARTY_*`·`FRIEND_*` 7종의 이름과 payload, SQS 메시지 본문
- **확정된 사용자를 매칭에서 풀어 주는 길** — `matching` 이 기다리고 있는 결정이다 (`matching/HANDOFF.md` ①)
- 테이블 컬럼, DB 롤, 사용자 아이디의 허용 문자·길이
- 인증 세부 — 방식은 정해졌다(쿠키 JWT access + Redis UUID refresh, D-14). 남은 것은 쿠키 속성, CSRF 대응, 다른 두
  서비스가 access 토큰을 검증하는 법(키 공유), 수명과 denylist, refresh 의 Redis 키, SSE 와 토큰 만료, 로컬 개발의 CORS 다
- **파티 모집 게시판의 남은 세부** — 방의 규칙은 정해졌다(D-11). 남은 것은 개발하면서 정한다 — 방장 확정의 세부
  (방에 있는 전원인가 고른 사람만인가, 확정된 방의 기능과 목록 표시), 차단을 방장과의 사이에서만 보는가 방 안의
  누구와든 보는가, **활성 요청 키에 무엇을 어떻게 써 넣고 언제 지우는가**(원자성, 안 지워졌을 때의 수명, 방장 확정 뒤.
  `matching` 의 상태 조회·취소가 이 값을 읽는 쪽도 손봐야 할 수 있다), 강퇴 뒤 재입장, 만석일 때의 동작, 방장 이탈의
  판단 기준, 만료 글 보존 기간, 글의 내용·정렬·필터, 알림 종류, 도배 글 대응이다 (`CLAUDE.md` §7.1)

전체 목록과 각 항목의 출처는 `CLAUDE.md` §7 에 있다.
