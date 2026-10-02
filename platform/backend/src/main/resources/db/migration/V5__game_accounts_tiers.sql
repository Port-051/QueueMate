-- 게임 계정의 티어를 사다리(랭크 큐)마다 따로 담는다 — 2026-09-29 소유자 결정(contracts/platform-api.md P-36 · docs/11 D-48).
-- "모드별 티어를 무조건 저장한다" · "db 에 jsonb 로 만들어 안에서 모드에 맞는 티어를 뽑게".
-- 까닭 — LoL 솔로랭크와 자유랭크는 서로 다른 사다리인데 계정에 티어를 하나만 적어, 자유랭크 매칭이 솔로랭크 티어로 돌았다.
--
-- tiers : {사다리: 티어 이름} 의 JSON 객체 하나. 사다리 키는 게임마다 정해져 있다(앱의 account.domain.Game#tierLadders —
--         LOL SOLO · FLEX / VALORANT COMPETITIVE / PUBG RANKED). 값은 gameconfig 티어 사다리(qm:gameconfig:{GAME}:tier)의 이름이다.
--         값을 모르는 사다리는 키를 적지 않는다(앱이 읽을 때 null 로 채운다). 어느 모드가 어느 사다리를 보는지는 gameconfig 모드 HASH 의 tierLadder 다.
--         DB 는 "객체다" 만 건다 — 키 · 값의 목록은 앱과 gameconfig 가 원본이라 DB 로는 강제하지 않는다(값의 목록이 Redis 에 있다).
--
-- 옛 tier 칸의 값은 이렇게 옮긴다 —
--   LOL      → {"SOLO": tier}         (Riot 의 솔로랭크에서 채운 값이었다. 자유랭크는 다음 연결 · 전적 갱신 때 채워진다)
--   VALORANT → {"COMPETITIVE": tier}  (자기신고였다. 경쟁전 사다리 하나뿐이다)
--   PUBG     → 버린다                  (자기신고였다 — 이제 PUBG API 로 다시 채운다)
-- 그 뒤 tier 칸을 지운다.
--
-- V1 ~ V4 는 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
ALTER TABLE game_accounts
    ADD COLUMN tiers jsonb NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT game_accounts_tiers_check CHECK (jsonb_typeof(tiers) = 'object');

UPDATE game_accounts SET tiers = jsonb_build_object('SOLO', tier) WHERE game = 'LOL' AND tier IS NOT NULL;

UPDATE game_accounts SET tiers = jsonb_build_object('COMPETITIVE', tier) WHERE game = 'VALORANT' AND tier IS NOT NULL;

ALTER TABLE game_accounts DROP COLUMN tier;
