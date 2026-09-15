# Frontend

Owner: Member 1. 소유 영역은 `frontend/**`다.

## 실행
```bash
npm install
npm run dev              # 5173. real 모드. 8080에 떠 있는 것에 붙는다
npm run dev:mock         # 5174. in-memory mock. 아무것도 안 띄워도 된다
npm run dev:mock-server  # 5173. 더미 서버를 같이 띄우고 거기에 붙는다
npm run build            # tsc -b && vite build
npm run typecheck        # tsc --noEmit
npm run e2e              # Playwright critical flows (dev:mock을 직접 띄운다)
```

`npm run e2e`는 처음 한 번 `npx playwright install chromium`이 필요하다.

## API 모드

**모드는 두 개뿐이다.** `VITE_API_MODE`가 정한다.

| 모드 | 의미 |
| --- | --- |
| `mock` | `src/mocks/`의 in-memory 어댑터가 REST와 이벤트를 대신한다. 네트워크가 없다 |
| `real` | 진짜 HTTP/WebSocket을 쓴다. **8080에 무엇이 떠 있는지는 프론트가 알 바 아니다** |

"더미 서버(`mock-server/`)에 붙는다"는 세 번째 모드가 아니라 **`real` 모드의 상대**다.
프론트 코드에는 분기가 없고 8080에 무엇을 띄웠는지만 다르다.

| 실행 | 포트 | `VITE_API_MODE` | 8080에 떠 있는 것 |
| --- | --- | --- | --- |
| `npm run dev:mock` | 5174 | `mock` | (없음) |
| `npm run dev:mock-server` | 5173 | `real` | `mock-server/server.js` (스크립트가 같이 띄운다) |
| `npm run dev` | 5173 | `real` | 직접 띄운 것. 실 backend든 `npm run mock-server`든 |

포트를 나눠 둔 이유는 두 모드를 헷갈리지 않기 위해서다. 화면만 보고는 구별이 어렵다.
주소가 5173인지 5174인지가 지금 어느 모드인지를 말해 준다.
개발 빌드는 부팅할 때 콘솔에도 `QueueMate API mode: ...` 한 줄을 남긴다.

`vite.config.ts`가 `/api`와 `/ws`를 `localhost:8080`으로 프록시한다.
다른 호스트에 붙이려면 `VITE_API_BASE`(REST)와 `VITE_WS_URL`(WebSocket 절대 URL)을 준다.

### 더미 서버 (`mock-server/`)

의존성이 0이고 `docs/14`의 계약을 그대로 흉내 낸다. Node 18 이상이면 바로 뜬다.

```bash
npm run mock-server                              # 8080
MOCK_LATENCY_MS=800 npm run mock-server          # 로딩 UI 확인
MOCK_AUTO_MATCH_MS=0 npm run mock-server         # 자동 매칭 끄기
```

계약 검사는 `mock-server/contract-smoke.sh`가 돈다. 다만 지금 그 스크립트에는 두 가지
함정이 있어서 그냥 돌리면 실패한다 (더미 서버 소유자에게 보고된 상태다).

```bash
# 스크립트 기본 포트가 8099라 8080에 띄웠으면 알려 줘야 한다.
# 자동 매칭 기본값(4000ms)이 스크립트의 sleep 2보다 길어서 제안 단계부터 줄줄이 실패한다.
PORT=8099 MOCK_AUTO_MATCH_MS=1000 node ../mock-server/server.js &
npm run mock-server:smoke     # → 통과 78 · 실패 0
```

**WebSocket `/ws`는 더미 서버에 없다.** 이 조합에서는 서버 이벤트가 오지 않으므로
매칭 진행은 `GET /match-requests/{id}` 폴링으로 확인한다. 이벤트까지 보려면
`npm run dev:mock`(in-memory)이나 실 backend를 쓴다.

### 실 backend

```bash
docker compose up -d postgres redis
cd backend && gradle bootRun            # 8080. Java 21을 못 찾으면 JAVA_HOME을 준다
```

real에서 매칭을 검증할 때는 대기열에 남은 계정을 먼저 비운다. 남아 있으면 혼자 매칭을
걸어도 즉시 상대가 잡혀서 mock처럼 보인다.

mock(in-memory) 데모 계정: `demo@queuemate.gg` / `queuemate1`

mock은 계약을 흉내 내는 데서 그치지 않고 아래 invariant를 강제한다.
INV-1(활성 요청 1개), INV-2(활성 proposal 1개), INV-4(전원 수락 후 파티 확정),
INV-5(만료/거절 proposal 재확정 불가), INV-6(차단 사용자 후보 제외), INV-9(예약 시간 중복 금지).

## 계약

`contracts/openapi.yaml`(v2.0.0)과 `contracts/events.md`가 정본이다. 구현이 어긋나면
계약이 아니라 구현을 고친다. `src/api/types.ts`는 그 두 파일과 1:1로 맞춰 둔 것이고,
`src/mocks/contract.ts`는 서버가 아는 gameconfig seed다(화면용 한글 카탈로그인
`src/domain/gameConfig.ts`와 다른 물건이다).

- REST 에러는 예외 없이 `{code, message}`다. `src/api/error.ts`의 `ErrorCode`가 전량이다
- 서버 이벤트는 **WebSocket** `/ws` 17종이다. SSE가 아니다
- handshake는 subprotocol `['queuemate.v1', 'bearer.<accessToken>']`로 인증한다.
  query string에 token을 싣지 않는다
- 401을 만나면 `http.ts`가 한 번 재발급하고 같은 요청을 다시 보낸다.
  재발급도 실패하면 세션을 버리고 익명 상태로 돌아간다

## 구조
```text
src/
  api/        contracts/openapi.yaml·events.md 그대로의 타입과 REST/WS 클라이언트
  mocks/      backend 없이 도는 in-memory 서버 + 이벤트 시뮬레이터
  domain/     게임별 조건 카탈로그(docs/02), 한글 라벨, 30분 슬롯 시간 유틸
  state/      인증 / 매칭·예약 / 소셜 컨텍스트
  components/ 앱 셸, 조건 폼, UI 프리미티브
  pages/      docs/01의 route별 화면
  webrtc/     파티 음성 + DataChannel 클라이언트 (mock 대체 구현 포함)
e2e/          Playwright critical flow
```

## 지켜야 하는 것
- 지원 게임은 LoL / VALORANT / PUBG 셋뿐이다.
- 조건은 게임 모드 + 게임별 핵심 조건 1개 + 음성 + 플레이 목적. 예약은 여기에 30분 단위 시간과 플레이할 양만 더한다.
- 상대팀/VS, 공개 탐색·게시판·피드, 프리미엄, 나이·챔피언 조건, 친구 추천은 만들지 않는다.
- `design/` 이미지는 레이아웃 참고일 뿐이고 business data는 `CLAUDE.md`와 `docs/`가 우선한다.

## 공유 UI 데모

https://port-051.github.io/QueueMate/#/login

`codex/ui-ux`의 프론트 변경을 푸시하면 GitHub Pages에 자동 배포됩니다.
브라우저 Mock 모드이며 실제 사용자 간 데이터는 공유되지 않습니다.
데모 계정: `demo@queuemate.gg` / `queuemate1`.
Pages 배포만 hash 라우팅을 사용하며 로컬 개발 URL은 그대로 유지합니다.
