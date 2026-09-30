-- 게임 계정의 주 포지션을 없앤다 — 2026-09-29 소유자 결정(contracts/platform-api.md P-35).
-- "주 포지션 · 주 역할군은 게시판에 글 쓸 때 하는 것이라 계정 연동에서 할 이유가 없다." 앱은 이 칸을 더 받지도 내보내지도 않는다
-- (PUT …/game-accounts/{game} 의 mainPosition 은 400 · 게임 프로필에 mainPosition 칸이 없다).
--
-- 이 칸에는 CHECK 도 인덱스도 없었다(V1 — 포지션 이름의 검증은 앱이 했다). 그래서 칸 하나만 지운다.
-- 적혀 있던 값은 버린다 — 옮겨 담을 곳이 없다(글의 wanted_positions 는 "찾는 포지션" 이라 뜻이 다르다).
--
-- V1 ~ V3 은 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
-- V1 의 game_accounts 머리 주석이 main_position 을 적은 것은 그 때의 기록으로 남는다.
ALTER TABLE game_accounts DROP COLUMN main_position;
