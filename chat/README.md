# chat — QueueMate 채팅 서비스

방 안의 **실시간 채팅**을 WebSocket 으로 주고받는 네 번째 배포 단위다(2026-10-09 · `matching` docs/11 D-58). 포트 **8083**.
**친구 DM 은 만들지 않는다**(2026-10-11 · D-59 — 소유자 "포폴로써 지금 구현하는 거하고 겹치는 거 같아서"). 규칙은 `CLAUDE.md`, 전체 그림은 루트 `../START_HERE.md`.

```
브라우저 ──WS /ws/chat/rooms/{roomId}──▶ chat ──PUBLISH qm:pubsub:chat:{roomId}──▶ Redis ──SUBSCRIBE──▶ chat(모든 인스턴스) ──WS──▶ 방의 소켓들
```

## 이 서비스가 하는 일 — 하나뿐

1. **방 채팅** — 방 구성원(`qm:room:{roomId}:members` 에 있는 사람 — platform 이 쓰고 여기서는 `HEXISTS` 로 **읽기만**)의 소켓을 들고, 메시지를 Redis Pub/Sub(`qm:pubsub:chat:{roomId}`)으로 뿌려 그 방의 모든 소켓에 쏜다. **저장하지 않는다.** 보낸 서버도 구독으로 받아 쏜다(순서가 하나).

## 이 서비스가 하지 않는 일

- **친구 DM 이 없다**(D-59). Redis Streams · `qm:dm:*` · `/ws/chat/dm` · `/api/v1/chat/dm/*` 어느 것도 없다 — 설계는 docs/11 D-58 결정 4 에 기록으로만 남아 있다.
- 매칭 · 방 · 친구 · 차단의 상태를 **바꾸지 않는다.** Redis 에 쓰는 것은 `qm:pubsub:chat:{roomId}` 발행뿐이다 — 키에 쓰는 것은 없다.
- DB 가 없다. 알림(`qm:pubsub:push:*` · `qm:pubsub:board`)을 받지도 보내지도 않는다 — 그것은 notification 의 일이다. `SseEmitter` 도 없다.
- 패턴 구독(`PSUBSCRIBE`)을 하지 않는다 — 이 인스턴스에 소켓이 있는 방의 채널만 이름으로 구독한다.
- 토큰을 **서명하지 않는다** — platform 이 서명한 access 토큰을 공개 키로 검증만 한다.

## 저장소 구성

`matching` 과 **같은 GitHub 저장소의 `chat` 브랜치**(고아 브랜치)다. 로컬에서는 `git worktree` 로 나란히 둔다. 커밋은 이 폴더에서 WSL 의 git 으로만.

```
queuemate/
├── matching/       matching 브랜치
├── platform/       platform 브랜치 (계정 · 친구 · 차단 · 게시판 · 방)
├── notification/   notification 브랜치 (SSE 알림)
├── frontend/       frontend 브랜치
└── chat/           chat 브랜치 (이 폴더)
    └── backend/    스프링 앱. notification/backend/ 와 같은 모양이다
```

## 로컬에서 띄우기

1. **Redis** — 6379 는 다른 프로젝트 것이다. 공용 테스트 Redis `qm-platform-test-redis`(6380)를 쓴다: `docker.exe run -d --rm --name qm-platform-test-redis -p 6380:6379 redis:7-alpine`(이미 떠 있으면 생략).
2. **platform 의 개발용 공개 키** — `../../platform/backend/.dev-keys/public.pem` 이 있어야 뜬다. 없으면 platform 을 한 번 띄워 만든다(개인 키는 platform 만 갖는다 — 여기서 만들지 않는다).
3. 띄운다 — `cd backend && REDIS_PORT=6380 SERVER_PORT=8083 ./gradlew bootRun`. **끝나면 반드시 내린다**(`./gradlew --stop`).
4. 확인 — `curl -s localhost:8083/health/live` 가 `{"status":"UP"}` 이면 떠 있다. `/api/v1/…` · `/ws/…` 는 쿠키 없이 401 이다.

## 엔드포인트

| 경로 | 인증 | 무엇 |
|---|---|---|
| `GET /health/live` | 없음 | 살아 있나 — ALB 헬스 체크는 여기에 |
| `GET /health/ready` | 없음 | readinessState + Redis |
| `GET /ws/chat/rooms/{roomId}` | 쿠키 `qm_access` + 방 구성원(`qm:room:{roomId}:members` `HEXISTS` — 아니면 403) | 방 채팅 소켓 — 소유자 구현 중(2026-10-11 — 전파까지 들어갔다) |

DM 의 소켓 · REST(`/ws/chat/dm` · `/api/v1/chat/dm/*`)는 D-59 로 없다. `GET /api/v1/chat/ping` 은 인증 테스트용 발판(`web/PingController`)이지 기능이 아니다.

"나"는 쿠키의 `sub` 다. `?userId=` 도 바디의 `userId` 도 없다. 인증 실패는 전부 401 `{"code":"UNAUTHENTICATED","message":"로그인이 필요합니다","details":[]}`.

## 환경변수 (`backend/src/main/resources/application.yaml`)

| 변수 | 기본값 | 뜻 |
|---|---|---|
| `SERVER_PORT` | `8083` | 포트 |
| `REDIS_HOST` · `REDIS_PORT` | `localhost` · `6379` | Redis — 로컬은 `6380` 으로 넘긴다 |
| `JWT_PUBLIC_KEY` | (빈 값) | 검증용 공개 키 X.509 PEM — 운영은 이것(Secrets Manager). 줄바꿈이 글자 `\n` 이어도 읽는다 |
| `JWT_PUBLIC_KEY_FILE` | `../../platform/backend/.dev-keys/public.pem` | `JWT_PUBLIC_KEY` 가 비면 읽는 파일. **둘 다 없으면 기동하지 않는다** |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | POST/PUT/PATCH/DELETE 와 WebSocket 핸드셰이크의 `Origin` 허용 목록 |
| `CHAT_MAX_TEXT_LENGTH` | `500` | 방 메시지 한 개의 최대 글자 수 |
| `CHAT_MAX_MESSAGES_PER_SECOND` | `5` | 소켓마다 초당 메시지 수 |
| `CHAT_PING_INTERVAL_MS` | `20000` | 서버 → 브라우저 ping 간격(ALB 유휴 300초보다 짧게) |
| `CHAT_SEND_STRIPES` | `4` | 전송 줄(스레드) 수 — 방은 `roomId` 해시로 늘 같은 줄에 간다(방 안의 순서) |
| `CHAT_SEND_QUEUE_CAPACITY` | `1000` | 줄마다 쌓아 둘 수 있는 메시지 수 — 넘치면 그 메시지는 버린다 |

`CHAT_DM_MAX_MESSAGES` · `CHAT_DM_TTL` · `CHAT_DM_PAGE_SIZE` 는 D-59(친구 DM 취소)로 없어졌다.

## 테스트

- `wsl.exe -e bash -lc '~/bin/qm-test chat'`(WSL 에서는 `~/bin/qm-test chat`) — `backend/` 를 `/tmp/qm-build/chat/backend` 에 rsync 한 뒤 거기서 `gradlew test` 를 돌리고 `tests N failures 0 errors 0 skipped N` 한 줄을 찍는다. 골라 돌리기: `~/bin/qm-test chat '*AuthenticationTest'`.
- 지금 있는 테스트(인증 · 공개 키 · Origin · `/health/live`)는 **Redis 없이 돈다** — Lettuce 는 연결을 늦게 열고 테스트가 Redis 를 건드리지 않는다(2026-10-09 Docker 를 내린 채 확인). 소켓 · Pub/Sub 테스트가 생기면 `REDIS_PORT=6380` 으로 넘긴다.
- 새 `@SpringBootTest` 에는 `TestTokens.register` 를 부르는 `@DynamicPropertySource` 가 필수다 — 없으면 공개 키가 없어 컨텍스트가 뜨지 않는다.

## 이미지

- `cd backend && docker build -t qm-chat:local .`(`backend/Dockerfile` — JDK 로 빌드 · JRE 로 실행 · Spring Boot 계층 추출 · uid 1000 · 테스트는 `-x test`).
- `docker run --rm -p 8083:8083 -e REDIS_HOST=host.docker.internal -e REDIS_PORT=6380 -e JWT_PUBLIC_KEY_FILE=/keys/public.pem -v <platform/backend/.dev-keys/public.pem>:/keys/public.pem:ro qm-chat:local`(운영은 `JWT_PUBLIC_KEY`. 개인 키가 든 `.dev-keys` 폴더째로 마운트하지 않는다).
