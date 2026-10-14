# CLAUDE.md — chat 서비스 규칙 (Non-Negotiable)

작업 전에 이 파일과 `README.md`를 읽어라. 전체 그림은 루트 `../START_HERE.md`, 다른 앱의 규칙은 그 폴더의 `CLAUDE.md`다.

이 폴더는 QueueMate의 **네 번째 배포 단위 `chat`**이다(2026-10-09 — `matching` docs/11 **D-58**(초안 · 소유자가 방향을 승인했다)).
방 안의 **실시간 채팅**을 WebSocket 으로 주고받는다. 포트는 **8083**이다(옛 `room` 앱의 8083 은 2026-09-25 에 없어졌고 이 번호를 다시 쓴다).
**친구 DM 은 만들지 않는다** — 2026-10-11 소유자 결정(docs/11 **D-59** — D-58 의 결정 4 · 7 · 8 과 Streams 예외를 거뒀다 · "포폴로써 지금 구현하는 거하고 겹치는 거 같아서"). 이 파일에서 DM · Streams · `qm:dm:*` · `ctl` 을 그날 걷어냈다.

> **이 서비스가 생기며 바뀐 규칙 하나** — 루트 `CLAUDE.md` §6 · `../notification/CLAUDE.md` 의 "notification 밖에 WebSocket 금지"는 **chat(과 앞으로의 `voice` 단위)에는 적용하지 않는다.**
> `SseEmitter` 는 여전히 notification 에만 있다. 알림(`qm:pubsub:push:*` · `qm:pubsub:board`)은 chat 이 받지도 보내지도 않는다.

---

## 1. 제품 경계

```
브라우저 ──WS /ws/chat/rooms/{roomId}──▶ chat ──PUBLISH qm:pubsub:chat:{roomId}──▶ Redis ──SUBSCRIBE──▶ chat(모든 인스턴스) ──WS──▶ 방의 모든 소켓
```

- **매칭 로직 · 방 로직 · 친구 로직이 하나도 없다.** 누가 방에 있는지(`qm:room:{roomId}:members`)는 **platform 이 쓰고 chat 은 읽기만** 한다.
- **방 채팅은 저장하지 않는다.** 방이 살아 있는 동안 Pub/Sub 으로 흘려보내는 것이 전부다. 들어오기 전의 대화는 못 본다.
- **친구 DM 은 없다**(D-59). 친구 사이의 대화는 같은 방 안에서만 한다. Redis Streams 는 이 앱에도 다른 앱에도 없다.
- 공개 채팅방 · 길드 · 피드 · 공개 사용자 탐색은 만들지 않는다(루트 `CLAUDE.md` §6).

## 2. 경계 — Redis 에서 무엇을 만지나

| | 키 · 채널 | 비고 |
|---|---|---|
| **쓴다** | **없다** — 키에 쓰는 것이 하나도 없다 | 방 채팅은 저장하지 않는다(D-58 결정 3). DM 의 Streams `qm:dm:*` · 커서 `qm:dm:read:*` 는 D-59 로 없어졌다 |
| **발행한다** | `qm:pubsub:chat:{roomId}` (방 메시지) — **이것 하나** | `qm:pubsub:push:*` · `qm:pubsub:board` 에는 발행하지 않는다 |
| **구독한다** | `qm:pubsub:chat:{roomId}` — **이 인스턴스에 그 방의 소켓이 있을 때만** | **패턴 구독(`PSUBSCRIBE`) 금지.** 기동 때 구독하는 채널은 없다 |
| **읽기만** | `qm:room:{roomId}:members` (방 구성원 HASH — 핸드셰이크에서 `HEXISTS`) | 쓰거나 지우거나 `EXPIRE` 걸지 않는다. 키 모양이 바뀌면 **platform 과 같이** 바꾼다(루트 `START_HERE.md` §4) |
| **금지** | `qm:party:*` · `qm:proposal:*` · `qm:user:*` · `qm:gameconfig:*` · `qm:auth:*` · `qm:riot:*` · 그 밖의 모든 키 | 매칭 · 계정의 상태다 |
| DB | **없다.** JPA · Flyway · PostgreSQL 어느 것도 들이지 않는다 | 저장하는 것이 없다 — Redis Streams 도 없다(D-59) |
| 토큰 | 공개 키로 **검증만**(§5) | 개인 키 · 서명 · refresh 는 platform 만 |

`platform` 쪽과의 약속은 방 멤버 HASH `qm:room:{roomId}:members` 의 모양 하나다(원본 `../platform/backend/src/main/java/com/queuemate/platform/room/redisKeys/RoomKeys.java` · 사본 `redisKeys/SharedRedisKeys`). `../platform/contracts/platform-api.md` **P-54**(친구 키 · `DM_DROP`)는 D-59 로 **취소**됐다.

## 3. 방 채팅의 모양 (기능은 이것 하나)

### 3.1 방 채팅 — Pub/Sub · 저장 없음

- 소켓 `GET /ws/chat/rooms/{roomId}`. 핸드셰이크에서 쿠키의 access 토큰을 검증하고(§5) `qm:room:{roomId}:members` 를 `HEXISTS` 로 읽어 **구성원인지** 본다(`room/RoomMembershipInterceptor`). 아니면 403 — 소켓을 열지 않는다.
- 메시지는 `PUBLISH qm:pubsub:chat:{roomId}` 로 나간다. **보낸 서버도 로컬 소켓에 직접 쓰지 않고 구독으로 받아서 쏜다** — 길이 하나라 모든 인스턴스의 모든 소켓이 같은 순서로 받는다.
- 인스턴스는 **자기한테 그 방의 소켓이 생길 때 `SUBSCRIBE`, 마지막 소켓이 끊기면 `UNSUBSCRIBE`** 한다(notification 의 사용자별 구독과 같은 규율).
- 상한은 `application.yaml` — 한 메시지 500자 · 소켓마다 5개/초 · ping 20초(ALB 유휴 300초보다 짧게). 넘으면 **그 메시지만** `ERROR` 로 돌려주고 발행하지 않는다.
- **봉투(2026-10-11 소유자 결정).** 들어오는 것 `{type:"SEND", cid, nick, text}` → 나가는 것 `{type:"MSG", roomId, from, nick, text, cid, sentAt}`.
  **`from` 은 세션의 userId 다**(핸드셰이크에서 토큰으로 확인한 값 — 바디의 `userId` 를 받지 않는 공통 규칙 그대로). **`nick` 은 브라우저가 실어 보낸 것을 그대로 싣는다** — 소유자 "그냥 브라우저로 해 · 보낸 사람 바꿔봤자 중요한 것도 아니고". chat 은 닉네임을 모르고(platform DB 에만 있다) 이름은 표시용이라서다. 서버는 `nick` 의 길이(platform 의 닉네임 규칙 2~16자)만 보고 비어 있으면 뺀다. 프런트는 이름을 `nick` 으로, 색 · 프로필 같은 "누구" 는 `from` 으로 그린다.
  `cid` 는 브라우저가 만든 UUID — 보낸 사람에게 돌아가는 `{type:"ACK", cid}` · `{type:"ERROR", cid, code}` 와 자기 `MSG` 를 알아보는 열쇠다(ACK · ERROR 는 그 소켓에만 핸들러가 직접 쓴다 — 방에 뿌리는 것이 아니라 C-2 와 어긋나지 않는다). 리스너는 봉투를 열어 보지 않고 그대로 쏜다.

친구 DM(옛 §3.2 — Streams `qm:dm:*` · 커서 · `/ws/chat/dm` · `/api/v1/chat/dm/*` · `DM_DROP`)은 **2026-10-11 D-59 로 만들지 않는다.** 설계는 docs/11 D-58 결정 4 에 기록으로 남아 있다 — 되살리려면 새 D-항목이 먼저다.

## 4. 불변식

| | 불변식 | 지키는 자리 |
|---|---|---|
| C-1 | **방 채팅은 저장하지 않는다 — Redis 에 쓰는 것은 `PUBLISH` 뿐이다.** `XADD` · `LPUSH` · `SET` · `HSET` 어느 것도 없다(옛 C-1 "모든 `XADD` 는 한 메서드 · `MAXLEN`" 은 DM 의 것이라 D-59 로 없어졌다 — 번호는 남긴다) | 방 메시지 핸들러(`room/RoomChatHandler`). 코드 리뷰에서 `PUBLISH` 밖의 Redis 쓰기 호출부가 **없음**을 센다 |
| C-2 | **보낸 서버도 구독으로 받아 쏜다** — 로컬 소켓에 직접 쓰는 길을 두지 않는다 | 방 메시지 핸들러는 `PUBLISH` 만 한다 |
| C-3 | **키 없음 = 구성원 아님(fail-closed).** Redis 장애 · `nil` · 예외 어느 것도 "구성원"으로 읽지 않는다 — `HEXISTS` 가 `TRUE` 일 때만 연다 | 핸드셰이크 인터셉터(`room/RoomMembershipInterceptor`). INV-10(`../matching/CLAUDE.md`)과 같은 태도 |
| C-4 | **패턴 구독 금지.** 방 채널은 로컬 소켓이 있을 때만 이름으로 구독한다 | 구독 관리 클래스 하나(`room/RoomChannelSubscriber`) |
| C-5 | 방 소켓은 **구성원만** 연다. `qm:room:{roomId}:members` 가 없거나 내가 없으면 거절 | 핸드셰이크 인터셉터 |

## 5. 인증 — 쿠키 `qm_access` 의 access 토큰 (notification 과 같은 틀)

규칙의 원본은 `../platform/CLAUDE.md` §5.1 · `../platform/contracts/platform-api.md` "공통" · "access 토큰"(P-2 · P-15)이고, 구현은 `../notification/backend/…/security/*` 를 그대로 옮긴 것이다(2026-10-09).

- **"나"는 쿠키 `qm_access` 의 access 토큰이다.** `Authorization` 헤더 · 쿼리 파라미터 · 바디의 토큰은 받지 않는다(`security/CookieBearerTokenResolver`). 브라우저의 WebSocket 핸드셰이크도 같은 출처의 쿠키를 자동으로 싣는다 — 그래서 소켓에도 같은 방식이 맞는다. **`?userId=` 는 없다.**
- **검증만 한다 — 서명은 platform 만 한다(RS256).** 보는 것 — 서명 · `exp` · `iss` = `queuemate-platform` · **`token_use` = `access`**(같은 키로 서명되는 `social_signup` 을 막는다) · **`sub` 가 숫자 문자열**(`^[0-9]{1,19}$`)(`security/JwtConfig`). `sub` 가 곧 방 멤버 HASH 의 field · 봉투의 `from` 이 되는 사용자 번호다.
- **고정값은 `security/TokenClaims` 에 베껴 두었다 — 원본은 platform 의 `common/security/TokenClaims`다.** 여기서만 바꾸면 모든 요청이 401 이 된다.
- **공개 키** — `JWT_PUBLIC_KEY`(X.509 PEM · 운영) → 비면 `JWT_PUBLIC_KEY_FILE`(기본값 `../../platform/backend/.dev-keys/public.pem`). **둘 다 없으면 기동하지 않는다**(`security/JwtPublicKey`). 로컬은 platform 을 한 번 띄워 키를 만든 뒤 띄운다. JWKS 는 없다.
- **실패는 전부 같은 401** `{"code":"UNAUTHENTICATED","message":"로그인이 필요합니다","details":[]}`(`security/ApiAuthenticationEntryPoint`). 거절되면 소켓도 구독도 생기지 않는다.
- **연결할 때만 검증한다** — 열린 소켓은 토큰이 만료돼도 끊지 않는다. 다음 연결이 401 이면 프런트가 `POST /api/v1/auth/refresh` 뒤 다시 연다.
- **stateless** — 세션 · CSRF 필터 · 폼 로그인 끔(`security/SecurityConfig`). `/health/**` · `/health` · `/info` 만 인증 없이 열리고 **`/ws/**` 와 `/api/v1/**` 를 포함한 나머지는 전부 인증**이다. `Origin` 검사는 `web/OriginCheckFilter`(POST/PUT/PATCH/DELETE · 설정 `ALLOWED_ORIGINS`) — WebSocket 핸드셰이크의 `Origin` 은 소켓 설정에서 같은 목록으로 본다(소유자 구현). **CORS 설정은 넣지 않는다.**
- **테스트** — `test/…/security/TestTokens` 가 테스트 동안만 있는 키 쌍을 만든다. **새 `@SpringBootTest` 에는 `TestTokens.register` 를 부르는 `@DynamicPropertySource` 를 꼭 붙인다.**

## 6. 기술 스택 · 배포 기준

| 항목 | 값 |
|---|---|
| 언어 · 프레임워크 | **Java 21 · Spring Boot 4.1.1**(MVC · 서블릿) — 다른 셋과 같다 |
| WebSocket | `spring-boot-starter-websocket` — **STOMP · simple broker 는 쓰지 않는다.** 날 `WebSocketHandler` 와 JSON 한 줄 |
| Redis | Spring Data Redis — `RedisMessageListenerContainer`(Pub/Sub). **Streams 는 쓰지 않는다**(D-59) |
| 인증 | `oauth2-resource-server`(Nimbus) — 검증만 |
| 헬스 체크 | actuator 를 루트에 — `/health/live`(ALB) · `/health/ready`(readinessState + Redis) |

배포 기준은 **Stage 2(ECS Fargate)**다(D-18). **k8s · HPA · sticky session 전제 금지** — 어느 인스턴스에 붙든 Redis 가 뿌린다.

## 7. 정해진 것과 미정 (2026-10-09 소유자 답 · 2026-10-11 D-59)

D-58 초안의 여덟 물음 가운데 **셋(⑤ · ⑥ · ⑧)은 소유자가 2026-10-09 에 닫았고**, **넷(② · ③ · ④ · ⑦)은 DM 의 물음이라 2026-10-11 D-59(DM 취소)로 물음 자체가 없어졌다.** 남은 미정은 **① 하나**다.
미정은 권고가 있어도 **결정이 아니다 — 정해서 구현하지 않는다.** 방 채팅이 끝난 뒤 소유자가 정한다.

**정해진 것(⑤ · ⑥ · ⑧) — 이대로 구현한다.**

| # | 물음 | 결정 |
|---|---|---|
| ⑤ | 시스템 메시지(입장 · 퇴장) | **없다** — `ROOM_*` 알림이 이미 간다 |
| ⑥ | 숫자들 | **`application.yaml` 의 값 그대로** — 500자 · 초당 5개 · ping 20초(DM 의 200개 · 30일 · 페이지 50 은 D-59 로 함께 없어졌다) |
| ⑧ | 메시지 삭제 | **없다** — 방 채팅은 저장하지 않으니 지울 것도 없다 |

**미정(①) — 방 채팅 뒤에 소유자가 정한다. 묻기 전에 구현하지 않는다.**

| # | 물음 | 권고(아직 결정이 아니다) |
|---|---|---|
| ① | 방 구성원 재확인 — 메시지마다 `qm:room:{roomId}:members` 를 보나, 주기적으로 보나 | 메시지마다(`HEXISTS` 한 번 — 강퇴된 사람의 다음 메시지가 바로 막힌다) |

**구현 순서 — 방 채팅(Pub/Sub)을 소유자가 직접.** 이 폴더의 Claude 는 소켓 핸들러 · Pub/Sub 코드를 먼저 쓰지 않는다(소유자가 시킨 것만).

## 8. 저장소 구성과 커밋 규칙

`matching` 과 **같은 GitHub 저장소의 `chat` 브랜치**(고아 브랜치 · 2026-10-09)다. 로컬에서는 `git worktree` 로 나란히 둔다. **커밋은 이 폴더 안에서 WSL 의 git 으로만** 한다(Windows git · IntelliJ 는 worktree 를 못 읽는다 — 루트 `CLAUDE.md` §0 · §1).

```
chat/
└── backend/    스프링 앱. notification/backend/ 와 같은 모양이다
    └── src/main/java/com/queuemate/chat/
        ├── security/   access 토큰 검증 — 공개 키 · JwtDecoder · 쿠키 리졸버 · 401 · 보안 설정 (notification 에서 옮김)
        ├── web/        Origin 검사 · 에러 본문
        ├── room/       (소유자) 방 채팅 — 핸드셰이크 · 핸들러 · 구독 · 전송 줄
        ├── config/     Redis 리스너 컨테이너 · WebSocket 등록 · 전송 줄(stripe) 설정
        └── redisKeys/  (소유자) 키 · 채널 이름 상수 — 읽기만 하는 키의 원본은 platform
```

커밋은 AngularJS convention `type(scope): subject`.

- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: **`chat`**(문서만이면 `docs`, Dockerfile · 인프라는 `infra`)
- subject: **한글** · 50자 이내 · 마침표 없음. body: 한글 3줄 이내. footer: `BREAKING CHANGE:` · revert 는 `revert: <원 subject>` + 원 해시. 시스템 안내의 `Co-Authored-By` 줄을 끝에
- **`git add` 는 경로를 명시한다.** `git add -A` · `git add .` 금지
- 문서(`CLAUDE.md` · `README.md`)와 코드를 한 커밋에 섞지 않는다. type 이 다르면 나눈다. 기능과 그 테스트는 한 커밋. 각 커밋 시점에서 빌드 통과
- **테스트를 돌려 통과한 뒤 커밋한다** — `wsl.exe -e bash -lc '~/bin/qm-test chat'`(WSL 에서는 `~/bin/qm-test chat`)이 `failures 0 errors 0` 을 찍어야 한다. `/mnt/c` 에서 직접 `./gradlew test` 는 느리다

## 9. 작업 방식

- 조사 · 탐색 · 여러 파일 변경은 **서브 에이전트**에 맡기고 필요한 맥락(읽을 파일 · 건드리면 안 되는 파일 · 정해진 결정)을 프롬프트에 다 적는다. 묻기만 한 것은 바로 답한다.
- **서브 에이전트 프롬프트에도 "WSL 에서 Chrome · Chromium 금지" 를 넣는다**(루트 `CLAUDE.md` §0).
- **다른 폴더(`matching` · `platform` · `notification` · `frontend`)의 파일을 여기서 고치지 않는다.** 읽기만 한다. 고칠 것이 있으면 그 폴더에서 따로 작업을 연다.
- 포트 **6379 · 5432 · `queuemate-v2-*` 컨테이너는 다른 프로젝트 것이다** — 건드리지 않는다. 테스트 · 로컬은 6380(`qm-platform-test-redis`)을 `REDIS_PORT` 로 넘긴다.
- `bootRun` 으로 띄웠으면 **반드시 내린다.** 끝나면 `./gradlew --stop`.
- 코드를 읽고 한 추측은 실험으로 확인한 뒤에 말하고, 확인한 결과는 그 자리 주석에 날짜와 함께 남긴다.

## 10. 함께 봐야 할 곳

| 무엇 | 경로 |
|---|---|
| 결정 D-58(이 단위의 근거) · **D-59(친구 DM 취소 — 2026-10-11)** | `../matching/docs/11_DECISION_LOG.md`(`### D-58.` · `### D-59.`) |
| platform 쪽 — 방 키 · 인증(P-54 친구 키 · `DM_DROP` 는 D-59 로 취소) | `../platform/contracts/platform-api.md` · `../platform/CLAUDE.md` §3.3 · §5.1 |
| 방 키 `qm:room:*` 의 자바 원본 | `../platform/backend/src/main/java/com/queuemate/platform/room/redisKeys/RoomKeys.java` |
| 토큰 고정값의 원본 | `../platform/backend/src/main/java/com/queuemate/platform/common/security/TokenClaims.java` |
| 이 틀을 가져온 곳 | `../notification/CLAUDE.md` §5.1 · `../notification/backend/src/main/java/com/queuemate/notification/security/` |

## 11. 이 저장소에서 하지 말 것 (요약)

- DB · JPA · Flyway 들이기 · Kafka · SQS · RabbitMQ · outbox · **Redis Streams** — 큐는 없다(DM 의 Streams 예외는 D-59 로 없어졌다)
- **친구 DM 을 되살리기**(D-59 — 되살리려면 새 D-항목이 먼저다) · `qm:dm:*` · `qm:dm:read:*` · `qm:pubsub:chat:dm:*` · `qm:pubsub:chat:ctl` 어느 것이든 다시 만들기
- **방 채팅을 저장하기**(List · Stream · DB 어디에도)
- `SseEmitter` · STOMP · simple broker · `@MessageMapping`
- **패턴 구독**(`PSUBSCRIBE qm:pubsub:chat:*`) · 로컬 소켓이 없는 방의 채널을 구독한 채 두기 · 보낸 서버가 로컬 소켓에 직접 쓰기
- `qm:room:*` · `qm:user:*` · `qm:party:*` · `qm:pubsub:push:*` · `qm:pubsub:board` 에 쓰거나 발행하기 · `PUBLISH qm:pubsub:chat:{roomId}` 밖의 Redis 쓰기
- 방 멤버 키가 없을 때 "구성원" 으로 읽기(fail-open) · Redis 장애 시 검사 건너뛰기
- `?userId=` · 바디 `userId` · "쿠키가 없으면 `userId`" 개발 스위치 · `Authorization` 헤더 · 쿼리 파라미터 토큰
- 개인 키 들이기 · 서명 · jjwt · `oauth2-client` · JWKS · `token_use` 안 보고 받기 · `TokenClaims` 를 platform 과 따로 바꾸기 · 공개 키 없이 뜨게 하기 · 서비스에 CORS 설정
- k8s · HPA · sticky session 전제 구현 · Stage 1 적용
- §7 의 미정(①)을 정해서 구현하기 · 정해진 것(⑤⑥⑧)을 묻지 않고 바꾸기 · 소유자가 시키지 않은 소켓 · Pub/Sub 코드를 먼저 쓰기 · 다른 폴더의 파일 고치기 · WSL 에서 Chrome 띄우기
- 포트 6379 · 5432 · `queuemate-v2-*` 조작 · `pkill -f` · `git add -A` · 띄워 놓고 안 끈 `bootRun`
