# Claude Code Prompt — Member 1 Frontend

당신은 QueueMate Member 1의 Claude Code 세션이다.

먼저 다음을 읽어라:
- `CLAUDE.md`
- `docs/00_PRODUCT_SPEC.md`
- `docs/01_IA_AND_USER_FLOWS.md`
- `docs/02_MATCH_CONDITION_SCHEMA.md`
- `contracts/openapi.yaml`
- `contracts/events.md`
- `design/README.md`

소유 영역은 `frontend/**`다. backend 파일은 수정하지 마라.

## 목표
React desktop web을 완성한다. 디자인 레퍼런스의 dark/purple 16:9 shell을 유지하되 business logic은 문서만 따른다.

## 구현 순서
1. AppShell/navigation/theme/design tokens
2. Landing/Login/Signup/Onboarding
3. Home
4. Match Condition — LoL/VALORANT/PUBG 전용 축약 조건
5. Waiting
6. Reservation New + Reservation Management
7. Proposal — 같은 팀원만 표시, 상대팀/VS 절대 없음
8. Party Room
9. Friends
10. Recent Players
11. My Info/Settings
12. mock API + WebSocket event simulator
13. real API adapter
14. WebRTC audio + DataChannel client
15. Playwright critical flows

## 중요한 business rules
- 게임은 딱 3개.
- LoL condition: mode/position/voice/purpose.
- VALORANT: mode/role/voice/purpose.
- PUBG: mode/playStyle/voice/purpose.
- Reservation은 위 조건 + 30분 단위 시간 + ONE_GAME/TWO_PLUS.
- 프리미엄 UI 삭제.
- 나이 조건 삭제.
- 캐릭터/챔피언 조건 삭제.
- 친구 추천 알고리즘 추가하지 말 것.

Backend가 없으면 `src/mocks/`를 만들어 contract shape 그대로 mock한다.
각 단계마다 `npm run build`를 통과시켜라.
