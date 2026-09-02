# Frontend

Owner: Member 1. 소유 영역은 `frontend/**`다.

## 실행
```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # tsc -b && vite build
npm run e2e      # Playwright critical flows
```

`npm run e2e`는 처음 한 번 `npx playwright install chromium`이 필요하다.

## API 모드
backend가 없어도 전체 흐름이 돌아간다.

| 모드 | 설정 | 동작 |
| --- | --- | --- |
| mock (기본) | 없음 | `src/mocks/`의 in-memory 서버가 REST와 WebSocket 이벤트를 대신한다 |
| real | `VITE_API_MODE=real` | `/api/v1` REST와 `/ws` WebSocket에 그대로 붙는다 (vite proxy → `localhost:8080`) |

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
