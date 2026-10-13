# 방 채팅(chat 서비스 · WebSocket) 검증 체크리스트

기준일 2026-10-13. 방 텍스트 채팅이 WebRTC DataChannel 에서 **`chat` 서비스 소켓**(`ws://{host}/ws/chat/rooms/{roomId}` · Redis Pub/Sub · 저장 없음 — `matching/docs/11_DECISION_LOG.md` D-58)으로 바뀐 날(⑦)의 검증 지도다.
항목은 **이 앱의 규칙**에서 뽑았다 — `src/chat/RoomChatSocket.ts` · `src/state/PartySessionContext.tsx` · `src/pages/PartyRoomPage.tsx` · `src/rooms/RoomConversationChat.tsx` · `chat/CLAUDE.md` §3.1(봉투 · 상한) · §4(C-1~C-5) · §5(인증) · §7(미정 ①) · `chat/backend/src/main/java/com/queuemate/chat/room/*.java` · `application.yaml`.
묶음과 관점은 실시간 채팅 · WebSocket 테스트 자료의 뼈대를 빌렸다(항목은 전부 이 앱의 것이다):

- [OWASP — WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html) — 핸드셰이크의 `Origin` 허용 목록(CSWSH) · 연결 때 인증 · 메시지 크기 · 속도 제한 · 유휴 타임아웃 · 본문 로그 금지.
- [Invicti — WebSocket security best practices and checklist](https://www.invicti.com/blog/web-security/websocket-security-best-practices) — 권한 검사는 핸드셰이크뿐 아니라 **메시지 · 행동마다**(이 앱의 미정 ① 그대로) · `wss` · 입력 검증.
- [Playwright — Mock WebSockets](https://playwright.dev/docs/mock#mock-websockets) · [class WebSocket](https://playwright.dev/docs/api/class-websocket) — `page.on('websocket')` 의 `framesent` · `framereceived` 로 진짜 소켓의 프레임을 **보기만** 하는 법(25 · 26 이 쓴다) · `routeWebSocket` 은 가로채기(mock 모드는 페이지 소켓이 바로 열린다 — 재접속 백오프를 보기엔 맞지 않다, 아래 R2).
- [OneUptime — How to Implement WebSocket Testing](https://oneuptime.com/blog/post/2026-01-25-websocket-testing/view) · [yrkan — WebSocket & Real-time Testing](https://yrkan.com/course/module-05-web-testing/websocket-realtime-testing/) — 실시간 검증의 네 축 **Delivered · Ordered · Complete · Timely** · 재접속의 백오프 · 중복 방지 · 폭주 때 순서 · 부하 · soak.

**표시**(`CHECKLIST.md` 와 같다)

| 표시 | 뜻 |
|---|---|
| `[신규 25]` | `e2e/25-room-chat-server.spec.ts`(화면 · 한 인스턴스 · 2~3명) 가 본다 — **아직 돌려 보지 않았다**(`tsc -p e2e` 만 통과 · 실행은 메인 세션이 Windows 사본에서) |
| `[신규 26]` | `e2e/26-room-chat-multi-instance.spec.ts`(8083 화면 + 8084 날 소켓 · Redis 구독 수) 가 본다 — 같다 |
| `[기존 NN]` | 기존 스펙 `e2e/NN-*.spec.ts` 가 본다. `[기존 chat-check]` 는 메인 세션의 소켓 수준 점검 `chat-check.mjs`(비구성원 403 · ACK · EMPTY · TOO_LONG · BAD_JSON · TOO_FAST · 나간 뒤 재접속 403) |
| `[수동]` | 로컬 스택에서 자동화할 수 없다 — 이유를 적었다(실제 네트워크 단절 · ALB 유휴 · 서버 · Redis 를 멈추는 일(컨테이너 · `bootRun` 조작 금지) · 다른 출처의 브라우저 · 1,000 소켓 부하) |
| `[미커버]` | 자동 · 수동 어느 쪽도 적지 않았다 — 이유를 적었다 |

실행 규칙은 `START_HERE.md` §6 — Windows 사본에서 `npm run e2e` · `workers: 1` · 고정 사용자 `e2e-a/b/c` · 시나리오 앞뒤 `reset`. 25 · 26 은 chat 8083 · **8084(둘째 인스턴스 · 같은 Redis 6380)** · Vite 의 `/ws/chat` 프록시 · `docker.exe exec qm-platform-test-redis redis-cli pubsub numsub …`(읽기만 — `support/chatRedis.ts`) 이 있어야 한다.

**첫 실행(2026-10-13 · 메인 세션)에서 찾은 스펙 쪽 함정 둘** — 제품 문제가 아니다.

- **토큰 통은 단계 사이에도 이어진다** — 25 ⑥ 의 폭주(12개) 직전에 앞 단계가 보낸 글 때문에 통(5개 · 초당 5 보충)이 비어 있으면 거절이 기대(≤ 7)보다 많다(9개). 폭주 전에 1.5초 쉬어 통을 채운다.
- **`route.fulfill` 로 지어낸 빈 페이지(`QmUser#page`)에서 연 날 WebSocket 은 핸드셰이크가 1006 으로 실패한다**(`chat-check.mjs` 에서도 같았다 · `curl` 로는 8084 가 101). 26 의 B 날 소켓은 Vite 가 실제로 내주는 문서(`/robots.txt`)를 연 새 페이지에서 연다 — 출처는 그대로 `http://localhost:5173` 이다.

---

## 1. 연결

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 1.1 | 방 화면에 들어가면 **같은 출처** 소켓 하나 `ws://localhost:5173/ws/chat/rooms/{roomId}` 가 열린다(Vite `/ws/chat` → 8083 · `ws: true`) — 다른 소켓을 더 열지 않는다 | `[신규 25]` ①(소켓 목록 길이 1 — ⑥ 끝에서도 1) | `RoomChatSocket#connect` · `vite.config.ts` |
| 1.2 | 소켓이 열리면 **혼자여도** 전송 버튼이 눌리고 보낼 수 없는 이유(`title`)가 없다 — 옛 "아직 다른 사람이 없어요" 는 없다 | `[신규 25]` ① | `PartyRoomPage` `canChat = chatStatus === 'open'` |
| 1.3 | 연결 중(`connecting`)에는 버튼이 눌리지 않고 `title` "채팅 서버에 연결 중이에요" | `[미커버]` — 로컬은 수십 ms 에 열려 화면에서 잡을 수 없다. `routeWebSocket` 의 mock 모드는 페이지 소켓이 **바로 열려** `connecting` 이 남지 않는다 | `PartyRoomPage` `connectionHint` |
| 1.4 | 방을 **나가면** 소켓이 `close(1000)` 으로 닫히고 다시 열지 않는다(`RoomChatSocket#close` 가 재접속 타이머도 지운다) | `[신규 25]` ⑩(B · A 모두 — 닫힌 뒤 2초 동안 새 소켓 없음) | `PartySessionContext` cleanup |
| 1.5 | 강퇴 · 방 닫힘(`ROOM_MEMBER_KICKED` · `ROOM_CLOSED`) · heartbeat 403 으로 방을 잃어도 같은 길로 소켓이 닫힌다 | `[미커버]` — 02(강퇴) · 03(방장 나가기) 는 소켓을 보지 않는다. 같은 `roomId = null` 경로라 ⑩ 이 대신 본다 — 강퇴 변형은 25 에 한 단계 더하면 된다 | `RoomSessionContext#lose` |
| 1.6 | 새로 고치면 옛 소켓이 닫히고 새 소켓이 열린다 | `[신규 25]` ⑧ | — |
| 1.7 | 서버 ping 20초 → 브라우저 자동 pong · ALB 유휴 300초 안에 끊기지 않는다 · 조용히 죽은 소켓은 ping 실패로 장부에서 빠진다 | `[수동]` — 로컬에 ALB 가 없고 5분을 기다리는 e2e 는 두지 않는다. 운영에서 20초 이상 가만히 둔 뒤 보내 본다 | `RoomSocketPing` · yaml `ping-interval-ms` |
| 1.8 | 같은 사람이 탭을 둘 열면 소켓 둘 · 둘 다 같은 글을 받는다(토큰 통은 **소켓 단위**라 둘이면 초당 10개) | `[미커버]` — 시간. `SendBudget` 주석이 설계로 받아들였다(사고 방지용 · 남용 방지 아님) | `SendBudget` |
| 1.9 | 핸드셰이크 거절(401 · 403)은 브라우저에 `close 1006` 으로 보이고 클라이언트는 같은 간격으로 재시도한다 — 방 세션이 "이 방에 없다" 를 알면 멈춘다 | `[신규 25]` ⑦(소켓 ≤ 2 · 전부 닫힘 · 홈으로 간 뒤 새 소켓 없음) | `RoomChatSocket` 머리 주석 |

## 2. 인증 · 권한

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 2.1 | 핸드셰이크의 "나" 는 **쿠키 `qm_access`** 만 — `Authorization` 헤더 · 쿼리 토큰은 받지 않는다 | `[미커버]` — 프런트는 토큰을 볼 수 없어 헤더 · 쿼리로 보내는 변형을 만들 수 없다 · 쿠키 없는 익명 컨텍스트의 401 은 `chat-check` 에도 없다(새 컨텍스트 하나로 더할 수 있다) | `chat/CLAUDE.md` §5 · `CookieBearerTokenResolver` |
| 2.2 | 구성원이 아니면 **403 — 소켓이 열리지 않는다**(`HEXISTS qm:room:{roomId}:members` 가 TRUE 일 때만 · fail-closed C-3 · C-5) | 소켓 수준 `[기존 chat-check]` · 화면은 `[신규 25]` ⑦(REST 도 403 `NOT_IN_ROOM` · 방 화면이 홈으로 보내고 토스트 "이 방에 들어와 있지 않아요" · 채팅 기록 없음 · 열렸던 소켓은 MSG 0) | `RoomMembershipInterceptor` |
| 2.3 | 나간 뒤 다시 붙으면 403(members HASH 에서 빠졌다) | `[기존 chat-check]` | 같다 |
| 2.4 | 봉투의 **`from` 은 세션의 userId** — 브라우저가 보낸 값은 쓰지 않는다 | `[신규 25]` ①(`from` = A 번호) · `[신규 26]` ②(날 소켓이 가짜 `nick` 을 실어도 `from` = B 번호) | `RoomChatHandler` 4) |
| 2.5 | `Origin` 허용 목록(`ALLOWED_ORIGINS` — 5173 · 3000) 밖의 핸드셰이크는 거절(CSWSH — OWASP) | `[수동]` — Playwright 는 `http://localhost:5173` 출처 한 곳에서만 돈다. `curl -H "Origin: http://evil.test" -H "Upgrade: websocket" …` 로 101 이 아닌 것을 본다 | `WebSocketConfig#setAllowedOrigins` |
| 2.6 | 열린 소켓은 토큰이 만료돼도 끊지 않는다 · 다음 연결이 401 이면 **재발급 뒤** 다시 연다 | `[미커버]` — 15분 만료를 기다릴 수 없다. **의심**: `RoomChatSocket` 은 401 · 403 을 구분하지 못하고(둘 다 1006) `POST /auth/refresh` 를 부르지 않는다 — 다른 요청이 401 로 재발급해 주기 전엔 15초 간격으로 403/401 만 되풀이할 수 있다 | `RoomChatSocket#scheduleReconnect` |
| 2.7 | **미정 ①** — 메시지마다 구성원을 다시 보지 않는다. 강퇴 · 나간 뒤에도 **열린 날 소켓**은 계속 보낼 수 있다(화면 클라이언트는 방을 잃으면 닫으므로 보이지 않는다) | `[미커버]` — 소유자 결정 전(`chat/CLAUDE.md` §7) · 지금 동작을 단언으로 굳히지 않는다. 날 소켓(26 의 B) + `DELETE /rooms/{id}/members/me` 로 재현할 수 있다 | §7 ① |

## 3. 메시지 전달

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 3.1 | 내 글은 **서버가 뿌린 MSG 로 그린다** — 낙관적으로 먼저 그리지 않는다 · SEND 의 `cid` = ACK 의 `cid` = MSG 의 `cid` · 화면에 **한 번** | `[신규 25]` ①(프레임 셋 대조 · 1초 뒤에도 count 1) | `RoomChatSocket` C-2 |
| 3.2 | ACK 는 **보낸 소켓에만** 간다 — 다른 사람의 소켓엔 없다 | `[신규 26]` ①(B 소켓에 A 의 ACK 0) | `RoomChatHandler#reply` |
| 3.3 | 상대에게 닿는다 — A → B · B → A | `[신규 25]` ② ⑪ ③ | — |
| 3.4 | **들어오기 전의 글은 없다**(저장 없음 D-58 결정 3) — 들어온 뒤에도 흘러 들어오지 않는다 | `[신규 25]` ②(기록 비어 있음 · 1초 뒤에도 · 소켓이 받은 MSG 0) | §1 |
| 3.5 | 새로 고치면 기록이 빈다 · 서버 응답 어디에도 글이 없다 | `[신규 25]` ⑧(방 안 사람 응답에 글 없음) | — |
| 3.6 | chat 이 Redis 에 쓰는 것은 `PUBLISH` 뿐(C-1 — `XADD` · `LPUSH` · `SET` 없음) | `[미커버]` — 코드 리뷰 · chat 단위 테스트의 몫(`MONITOR` 는 운영 금지 명령이라 e2e 에 두지 않는다) | §4 C-1 |
| 3.7 | MSG 봉투 `{type, roomId, from, nick, text, cid, sentAt}` — `sentAt` 은 ISO 문자열 · `roomId` 는 그 방 | `[신규 26]` ① · `[신규 25]` ① | `dto/Output` |
| 3.8 | `nick` 은 **브라우저가 실어 보낸 값 그대로**(서버는 길이만 본다) · 화면의 이름은 `from` 으로 찾은 **서버 닉네임이 먼저**(좌석과 같은 출처 §3-35) | `[신규 25]` ④(내 글 · 상대 글 · 양쪽 화면) · `[신규 26]` ②(가짜 `nick` 을 실어도 화면은 서버 닉네임 · 봉투에는 가짜 `nick`) | `PartyRoomPage` `chatName` |
| 3.9 | 앞뒤 공백은 잘라 보낸다 · 공백만이면 전송 버튼이 없다(보내지 않는다) | `[신규 25]` ①②(버튼 없음) · 서버의 EMPTY 는 `[기존 chat-check]` | `RoomConversationChat#send` |
| 3.10 | 줄바꿈이 든 글(Shift+Enter 뒤 전송)이 그대로 전달되고 줄로 보인다 | `[미커버]` — 25 ⑪ 은 Shift+Enter 가 **전송하지 않는 것**만 본다. 줄바꿈 글의 표시(`white-space`)는 보지 않았다 | — |
| 3.11 | 나간 사람에게는 더 가지 않는다 | `[신규 25]` ⑩(B 의 닫힌 소켓에 MSG 0) · `[신규 26]` ④(날 소켓 닫은 뒤 프레임 수 그대로) | — |
| 3.12 | 다른 방의 MSG 는 그리지 않는다(`roomId` 가 다르면 버린다) | `[미커버]` — 채널이 방마다 따로라 서버가 보내지 않는다 · 클라이언트 방어선만 있다 | `RoomChatSocket#onmessage` |

## 4. 순서 · 중복

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 4.1 | A1 B1 A2 B2 A3 를 쉬지 않고 번갈아 보내면 **두 화면의 순서가 같고** 각자의 글은 보낸 차례대로(길이 하나 — Redis 채널 · 방별 전송 줄) | `[신규 25]` ③ | §3.1 · `RoomSendExecutors`(roomId 해시 → 같은 줄) |
| 4.2 | 폭주(초당 5 초과) 때 받아들여진 글만 순서대로 · 거절된 글은 어느 화면에도 없다 | `[신규 25]` ⑥ | `SendBudget` |
| 4.3 | 중복 렌더 없음 — 같은 글이 두 번 그려지지 않는다 | `[신규 25]` ①(1초 뒤 count 1) · ⑧(새 소켓 뒤 count 1) | — |
| 4.4 | 메시지 id = `cid`(React `key`) — **다른 사람이 같은 `cid` 를 다시 쓰면** key 가 겹친다(서버는 `cid` 를 검사하지 않는다) | `[미커버]` — **의심**(`RoomChatSocket.ts:69`) · 날 소켓으로 재현할 수 있지만 "어떻게 해야 맞는가"(서버 id 부여 · `from+cid` 합성 키)는 소유자 결정이다 | — |
| 4.5 | 재접속 사이에 뿌려진 글은 **잃는다**(저장 없음 — 의도) · 재접속 뒤 중복 수신 없음 | `[수동]` — 네트워크 단절을 로컬 e2e 가 만들 수 없다(서버 · 컨테이너를 멈추지 않는다) | D-58 |
| 4.6 | 방마다 늘 같은 전송 줄 · 줄이 가득 차면(1000) 그 메시지를 버린다 | `[미커버]` — 서버 내부 · chat 단위 테스트의 몫 | yaml `send.*` |

## 5. 한도 · 거절

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 5.1 | 501자를 넣으면 **클라이언트가 500자로 잘라 보낸다** — 거절 안내 없이 500자가 양쪽에 보인다 · 소켓으로 나간 본문도 500자 · ERROR 없음 | `[신규 25]` ⑤ — 제품의 실제 동작을 적었다. **검토 항목**: 잘린다는 안내가 없다(사용자는 뒷부분이 사라진 것을 모른다 · `TOO_LONG` 토스트 "메시지는 500자까지…" 는 화면 경로로는 절대 뜨지 않는다) | `RoomChatSocket#send` `slice(0, 500)` |
| 5.2 | 서버 상한 500 — 501자는 `ERROR TOO_LONG` · 발행하지 않는다 | `[신규 26]` ③(날 소켓 · A 화면에 없음) · `[기존 chat-check]` | `RoomChatHandler` 3) |
| 5.3 | 500자 정각은 들어간다 | `[신규 26]` ③ · `[신규 25]` ⑤ | — |
| 5.4 | **초당 5개 토큰 버킷** — 1초 안에 12개를 쏘면 처음 다섯은 들어가고 일부가 `TOO_FAST` · 토스트 "너무 빨리 보내고 있어요…" · 받아들여진 것은 순서대로 양쪽에 · 소켓은 열린 채 · 1.5초 뒤 보통 글은 간다 | `[신규 25]` ⑥(12개 전부에 ACK/ERROR 가 돌아온 뒤 센다 · 거절 이유는 전부 TOO_FAST) · 소켓 수준 `[기존 chat-check]` | `SendBudget` |
| 5.5 | `BAD_JSON`(깨진 프레임 → `cid` 없는 ERROR) | `[기존 chat-check]` — 화면은 늘 JSON 을 보낸다 | `RoomChatHandler` 1) |
| 5.6 | `EMPTY`(공백만) | `[기존 chat-check]` · 화면은 보내지 않는다(3.9) | — |
| 5.7 | `RETRY`(Redis 발행 실패) → 토스트 "채팅 서버가 잠시 바빠요. 다시 보내 주세요" · 그 글만 안 나간다 | `[수동]` — Redis 를 멈춰야 한다(테스트 컨테이너를 e2e 가 조작하지 않는다 — 루트 `CLAUDE.md` §6) | `PartySessionContext` onError |
| 5.8 | `nick` 은 16자로 자른다 · 비어 있으면 뺀다 | `[미커버]` — e2e 닉네임은 5자 · 날 소켓으로 17자 `nick` 을 보내면 바로 볼 수 있다(26 에 한 줄) | `RoomChatHandler` `MAX_NICK_LENGTH` |
| 5.9 | 입력칸 `maxLength 2000`(붙여 넣기 상한) | `[미커버]` — 5.1 이 더 앞에서 자른다 | `RoomConversationChat` |
| 5.10 | 화면 기록은 최근 500개만 든다(`slice(-499)`) | `[미커버]` — 500개를 보내는 데 100초(초당 5) | `PartySessionContext` |
| 5.11 | 거절(ERROR)된 글은 입력칸으로 **되돌아오지 않는다**(보낸 순간 비워진다 — TOO_FAST 로 잃는다) | `[미커버]` — **검토 항목**(UX): 25 ⑥ 에서 거절된 글은 어디에도 남지 않는다. 되살릴지는 소유자 | `RoomConversationChat#send` |

## 6. 재연결 · 장애

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 6.1 | 끊기면 `reconnecting` — 버튼이 눌리지 않고 `title` "채팅 연결이 끊겨 다시 잇는 중이에요" · 초안은 남는다 | `[미커버]` — 진짜 서버를 끊을 수 없다. `page.routeWebSocket` 으로 닫아 볼 수는 있다(다음 후보) | `PartyRoomPage` `connectionHint` |
| 6.2 | 백오프 1 · 2 · 4 · 8 · 15초 · 열리면 0 으로 되돌아간다 | `[미커버]` — mock 모드(`routeWebSocket`)는 페이지 소켓이 **바로 열려** `retries` 가 0 으로 되돌아가 백오프를 볼 수 없다 · 진짜 서버 정지는 금지 | `RoomChatSocket` `BACKOFF_MS` |
| 6.3 | 서버 재배포(graceful · 1001 GOING_AWAY) → 재접속 → 다시 보낼 수 있다 | `[수동]` — `bootRun` 을 내렸다 올리는 일은 e2e 가 하지 않는다. 손으로: chat 을 재시작하고 15초 안에 글이 가는지 | `RoomSocketShutdown` |
| 6.4 | Redis 장애 중 핸드셰이크는 **거절**(fail-closed C-3) · 발행 실패는 RETRY | `[수동]` — 5.7 과 같은 이유 | C-3 |
| 6.5 | 느린 브라우저 — 한 번 쓰기 10초 · 버퍼 64KB 를 넘으면 그 소켓만 4500 으로 끊는다 · 나머지는 받는다 | `[수동]`/부하 — 느린 수신자를 흉내 내려면 소켓을 잡아 두는 클라이언트가 필요하다 | `RoomSockets` |
| 6.6 | 방 heartbeat(platform · 1분) 403 → 방을 잃음 → 소켓 닫힘 | `[미커버]` — 1분 + TTL 을 기다리는 e2e 를 두지 않았다(1.5 와 같은 경로) | `RoomSessionContext` |
| 6.7 | SSE(notification) 가 끊겨도 채팅 소켓은 따로 산다 · 반대도 | `[미커버]` — 22 는 SSE 만 본다 | — |
| 6.8 | 전송 실패(`ws.send` 예외 · 닫힘 직후) → 토스트 "전송하지 못했습니다…" · 초안 보존 | `[미커버]` — 닫히는 순간을 겨누기 어렵다 | `PartyRoomPage#send` |

## 7. 다중 인스턴스

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 7.1 | 8083(화면)의 글이 Redis 를 건너 **8084 의 소켓**에 MSG 로 온다 — 한 번 | `[신규 26]` ①(B 의 날 소켓은 Vite 가 내주는 실제 문서를 연 페이지에서 — `route.fulfill` 빈 페이지에서는 핸드셰이크가 1006 으로 실패한다, 머리의 함정 둘) | §1 그림 |
| 7.2 | 8084 의 SEND 가 8083 의 화면에 그려진다(서버 닉네임) | `[신규 26]` ② | — |
| 7.3 | `PUBSUB NUMSUB qm:pubsub:chat:{roomId}` = **소켓을 가진 인스턴스 수** — 둘이 붙으면 2 · 8084 소켓이 닫히면 1 · 마지막이 나가면 0(UNSUBSCRIBE · C-4) | `[신규 26]` 준비 · ④ | `RoomChannelSubscriber` |
| 7.4 | 한 인스턴스에 소켓이 둘이면 구독은 하나 · **마지막 소켓**이 끊길 때만 해제 | `[신규 25]` ⑩(A · B 모두 8083 → 1 · B 나가도 1 · A 나가면 0) | `RoomSockets#add/remove` |
| 7.5 | 패턴 구독 없음(`PSUBSCRIBE` 금지 — `PUBSUB NUMPAT`) | `[미커버]` — `NUMPAT` 은 Redis 전체 값이라 다른 앱의 것과 섞인다(지금은 어느 앱도 패턴 구독을 두지 않지만 e2e 가 그것까지 단언하지 않는다) · chat 코드 리뷰의 몫 | C-4 |
| 7.6 | sticky session 없이 어느 인스턴스에 붙어도 같은 방을 본다(D-18) | `[신규 26]` ①②(두 인스턴스가 한 방) | D-18 |
| 7.7 | 한쪽 인스턴스에만 소켓이 있을 때 다른 쪽은 그 채널을 구독하지 않는다(소켓 없는 방을 구독한 채 두지 않기) | `[신규 26]` ④(8084 소켓이 닫히자 1 로 준다) | §3.1 |

## 8. UI · 접근성

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 8.1 | 기록은 `role="log"` "방 메시지"(`aria-live="polite"` · additions) · 입력은 `textbox` "방에 메시지 보내기" · 버튼 "메시지 보내기" | `[신규 25]`(모든 단계가 이 역할 · 이름으로 찾는다 — 없으면 실패) | `RoomConversationChat` |
| 8.2 | 빈 기록 "인사를 건네보세요!" · 글자가 없으면 전송 버튼이 없다(공백만도) | `[신규 25]` ①②⑧ | mentor-room-ux "추가 UI 정리" |
| 8.3 | 보낸 뒤 입력칸이 비고 초점이 남는다 | `[신규 25]` ①⑪ | — |
| 8.4 | Enter 전송 · Shift+Enter 줄바꿈(보내지 않는다) | `[신규 25]` ⑪ | — |
| 8.5 | 한글 IME 조합 중 Enter(`isComposing` · `keyCode 229`)는 보내지 않는다 | `[수동]` — Playwright 로 IME 조합 이벤트를 만들 수 없다. 손으로: 한글을 치는 중 Enter | `RoomConversationChat` onKeyDown |
| 8.6 | 보낸 사람 이름(`.room-profile-name-button`) · 내 글은 내 닉네임 · 이름을 누르면 상세 창 | 이름 `[신규 25]` ④ · 상세 창 `[기존 19]`(19 는 메인 세션이 다시 쓰는 중) | §3-35 |
| 8.7 | 거절 안내는 토스트(`role="status"` · 3.8초) — TOO_FAST · TOO_LONG · RETRY · 그 밖 `(코드)` · EMPTY 는 조용히 | TOO_FAST `[신규 25]` ⑥ · TOO_LONG 은 화면 경로로 뜰 수 없다(5.1) · RETRY `[수동]`(5.7) | `PartySessionContext` onError |
| 8.8 | 위로 스크롤해 둔 채 새 글이 오면 "새 메시지 보기 ↓" · 누르면 맨 아래 | `[미커버]` — 기록이 넘치게 채우고 스크롤을 올리는 단계가 필요하다(다음 후보) | `RoomConversationChat` `unread` |
| 8.9 | 같은 사람의 5분 안 연속 글은 묶인다(`is-grouped`) · 날짜가 바뀌면 구분선 | `[미커버]` — 25 ⑥ 의 연속 글이 묶이는지는 보지 않았다 · 날짜 경계는 만들 수 없다 | — |
| 8.10 | 폰 폭에서 채팅이 방 패널 안에서 쓰인다 | `[미커버]` — 23(반응형)은 채팅을 보내지 않는다 | — |
| 8.11 | 얼굴 색 — 채팅의 아바타가 음성 칸 좌석과 같은 방 색(모두 다른 색) | `[미커버]` — 색 비교는 18 · 좌석 쪽 스펙의 몫 | §3-30 |

## 9. 보안(XSS · 개인정보)

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 9.1 | `<img src=x onerror=alert(1)>` 같은 글은 **글자 그대로** — 요소가 생기지 않고(`img` · `script` 0) alert 가 뜨지 않는다(양쪽 화면) | `[신규 25]` ⑨ | React 텍스트 노드 |
| 9.2 | `nick` 사칭 — 가짜 이름을 실어도 화면의 이름 · 색 · 프로필은 `from`(세션 번호) 기준 | `[신규 26]` ② · 봉투에 가짜 `nick` 이 그대로 실리는 것은 **소유자 결정**(§3.1 "브라우저로 해") — 옛 클라이언트 · 날 소켓 수신자는 그 이름을 볼 수 있다 | §3.1 |
| 9.3 | 방 밖의 사람은 글을 받을 수 없다(403 · 소켓 없음) · 방 번호를 바꿔 넣어도 같다 | `[신규 25]` ⑦ · `[기존 chat-check]` | C-5 |
| 9.4 | `Origin` 허용 목록(CSWSH) | `[수동]`(2.5) | OWASP |
| 9.5 | 운영은 `wss`(CloudFront `/ws/*` → ALB) | `[수동]` — 운영 배포 뒤 | D-58 |
| 9.6 | 서버 로그에 **본문**이 남지 않는다(OWASP — 메시지 내용 · 토큰 로그 금지) | `[미커버]` — **의심**: `RoomChatHandler.java:46` 이 받은 프레임 전부(`payload`)를 `INFO` 로 남기고 `:40` 이 세션 attributes 를 남긴다 — 방 채팅은 "저장하지 않는다"(D-58) 는 약속인데 로그에는 남는다. 운영 전에 `DEBUG` 로 내리거나 본문을 빼야 한다(소유자 판단) | `RoomChatHandler` |
| 9.7 | 토큰이 JS 에 노출되지 않는다(`HttpOnly` 쿠키 · 소켓 URL 에 토큰 없음) | `[신규 25]`(소켓 URL 에 쿼리 없음 — 1.1 의 URL 검사가 간접 확인) | §5 |
| 9.8 | 사용자 번호(`from`)가 봉투에 실린다 — 방 안 사람에게만 · 이미 `GET /rooms/{id}/members` 로 보이는 값 | `[신규 26]` ①(값 확인) — 공개 범위 자체는 계약(P-44 ⑩) 그대로 | — |

## 10. 성능

| # | 항목 | 확인 | 근거 |
|---|---|---|---|
| 10.1 | 로컬 전달 지연 — 보낸 뒤 상대 화면에 1초 안 | `[미커버]` — 25 · 26 은 10초 상한의 `toBeVisible` 로 **도달만** 본다 · 지연 수치는 재지 않았다(재려면 `sentAt` 과 화면 시각의 차) | yrkan "Timely" |
| 10.2 | 1,000 소켓 · 방 200개 동시 — 전송 줄 4개 · 큐 1000 · 끊김 없이 | `[수동]` — 부하 도구(k6 · `matching/load-test` 같은) 의 몫 · e2e 는 2~3명 | yaml `send.*` |
| 10.3 | 장시간(soak) 메모리 — 장부(`RoomSockets`)가 끊긴 소켓을 남기지 않는다 | `[수동]` — 몇 시간을 돈다 · 25 ⑩ 이 구독 0 으로 "그 방의 장부가 비었다" 는 것만 간접 확인 | `RoomSockets#remove` |
| 10.4 | 폭주(초당 12) 때 다른 사람의 화면이 멈추지 않는다 | `[신규 25]` ⑥(B 화면이 같은 수 · 같은 순서로 받는다) | — |
| 10.5 | `BOARD_CHANGED` · `ROOM_*` 재조회와 채팅이 서로 막지 않는다 | `[미커버]` — 25 에서 둘이 같이 일어나지만(입장 · 나가기) 단언은 채팅만 | — |

---

**집계**(항목 84) — 신규 스펙이 보는 항목 41(`[신규 25]` 32 · `[신규 26]` 14 · 둘이 겹치는 5) · `[기존 …]` 만으로 보는 항목 3(`chat-check` — 2.3 · 5.5 · 5.6 · `[기존 chat-check]` 표시는 모두 8 · `[기존 19]` 1) · `[수동]` 만 12(표시는 13 — 8.7 은 신규와 함께) · `[미커버]` 28. 41 + 3 + 12 + 28 = 84.

**스펙에서 못 단언한 것(이유)** — 연결 중 상태(1.3 — 너무 빨리 열린다) · 재접속 안내 · 백오프(6.1 · 6.2 — 서버를 끊을 수 없고 mock 은 바로 열린다) · RETRY(5.7 — Redis 를 멈추지 않는다) · Origin(2.5 — 출처 하나) · 미정 ①(2.7 — 결정 전).

**코드를 읽고 의심한 것(확인 안 함 — 소유자 판단)** — 5.1 잘림 안내 없음(`RoomChatSocket.ts:91`) · 5.11 거절된 글이 사라짐(`RoomConversationChat.tsx:37-40`) · 4.4 `cid` 가 React key(`RoomChatSocket.ts:69`) · 2.6 401 뒤 재발급 없이 재시도만(`RoomChatSocket.ts:80-84 · 115-121`) · 9.6 본문 INFO 로그(`RoomChatHandler.java:40 · 46`) · `RoomChatHandler.java:66-69` 토큰 검사가 본문 검사보다 앞이라 빈 글 · 긴 글도 토큰을 쓴다(사소).
