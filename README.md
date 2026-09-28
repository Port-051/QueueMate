# QueueMate 프런트 (`frontend/` · 브랜치 `frontend`)

QueueMate 의 **브라우저 앱**이다. React 18 · react-router 6 · TypeScript · Vite 5 · npm. 개발 서버 **5173**.

- **원본** — `Port-051/QueueMate` 브랜치 **`codex/room-card-board`**(커밋 `7ee7177`, 2026-09-28)의 `frontend/` 를 2026-09-28 에 그대로 가져왔다(`main` f860ada 보다 90커밋 앞 — **방 카드 보드**가 더해진 갈래다). 원본의 README 는 `docs/UPSTREAM_README.md` 에 있다 — 그 파일은 **원본 백엔드**(8080 하나 · bearer 토큰 · WebSocket)를 전제로 쓰였고 **이 작업 공간의 백엔드와 다르다.**
- **이 폴더의 규칙은 `CLAUDE.md`, 지금 상태와 계획은 `START_HERE.md`.** 백엔드 계약은 옆 폴더 — `platform/contracts/platform-api.md` · `matching/contracts/openapi.yaml` · `events.md`.
- 이 폴더는 git **고아 브랜치 `frontend`** 의 worktree 다(`matching/.git` 이 본 저장소). 커밋은 WSL 에서, 경로를 명시해서 한다(루트 `CLAUDE.md` §4).

## 명령

```bash
npm ci
npm run dev          # VITE_API_MODE=real, 5173 — 프록시 → 8080 (지금은 원본 백엔드 전제. 프록시 셋으로 바꾸는 것이 2단계다)
npm run dev:mock     # 5174 — 브라우저 안의 가짜 서버(localStorage). 기본 화면이 방 카드 보드다
npm run dev:rooms    # 5174 — real 모드 + VITE_HOME_LAYOUT=rooms + 데모 로그인 버튼(원본 파일럿 전용)
npm run build        # tsc -b && vite build
npm run typecheck    # tsc --noEmit
npm run e2e          # Playwright — mock 모드 전제(35 spec + room-decks). 통합 뒤에는 지운다(소유자 결정)
npx playwright test --config tests/playwright.config.ts   # 브라우저 없는 단위 테스트 5개(rooms 의 순수 함수)
```

`npm ci` · `build` · `typecheck` 는 2026-09-28 에 이 자리(OneDrive 아래 WSL)에서 통과했다. **dev 서버는 띄웠으면 반드시 내린다**(`ss -ltnp | grep :5173` → `kill <PID>`. `pkill -f` 금지).

## 지금 어디까지

**1단계(가져오기 · 빌드) 끝.** 코드는 아직 원본 그대로라 **우리 백엔드와 통하지 않는다** — 토큰을 `localStorage` 에 두고 `Authorization: Bearer` 로 보내며, 이메일 · 비밀번호 가입 · 로그인과 `/ws` WebSocket 을 전제한다. 우리 쪽은 쿠키 `qm_access` · 소셜 로그인만 · SSE 다. 무엇을 어떤 순서로 바꾸는지는 `START_HERE.md` §2, 경로 하나하나의 대조는 `START_HERE.md` §3 이다.
