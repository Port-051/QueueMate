# Frontend

Owner: Member 1. 소유 영역은 `frontend/**`다.

## 실행
```bash
npm install
npm run dev      # http://localhost:5173 실제 backend에 붙는다 (기본)
npm run dev:mock # http://localhost:5174 backend 없이 돈다
npm run build    # tsc -b && vite build
npm run e2e      # Playwright critical flows (mock 서버를 직접 띄운다)
```

`npm run e2e`는 처음 한 번 `npx playwright install chromium`이 필요하다.

## API 모드
backend가 없어도 전체 흐름이 돌아간다.

| 모드 | 실행 | 포트 | 동작 |
| --- | --- | --- | --- |
| real (기본) | `npm run dev` | 5173 | `/api/v1` REST와 `/ws` WebSocket에 그대로 붙는다 (vite proxy → `localhost:8080`) |
| mock | `npm run dev:mock` | 5174 | `src/mocks/`의 in-memory 서버가 REST와 WebSocket 이벤트를 대신한다 |

`develop`은 실제로 되는지 확인하는 자리라 기본이 real이다. mock은 지우지 않는다.
CLAUDE.md §5가 요구하는 것은 backend 없이 개발할 수 있어야 한다는 것이고,
`npm run dev:mock`이 그 길을 유지한다.

포트를 나눠 둔 이유는 두 모드를 헷갈리지 않기 위해서다. 화면만 보고는 구별이 어렵다.
mock은 봇이 인사하고 매칭이 곧바로 잡히므로, real인 줄 알고 보면 동작하는 것처럼 착각한다.
주소가 5173인지 5174인지가 지금 어느 모드인지를 말해 준다.

real은 아래가 모두 떠 있어야 한다.

```bash
docker compose up -d postgres redis
cd backend && gradle bootRun            # 8080. Java 21을 못 찾으면 JAVA_HOME을 준다
```

real에서 매칭을 검증할 때는 대기열에 남은 계정을 먼저 비운다. 남아 있으면 혼자 매칭을
걸어도 즉시 상대가 잡혀서 mock처럼 보인다.

mock 데모 계정: `demo@queuemate.gg` / `queuemate1`

mock은 계약을 흉내 내는 데서 그치지 않고 아래 invariant를 강제한다.
INV-1(활성 요청 1개), INV-2(활성 proposal 1개), INV-4(전원 수락 후 파티 확정),
INV-5(만료/거절 proposal 재확정 불가), INV-6(차단 사용자 후보 제외), INV-9(예약 시간 중복 금지).

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
