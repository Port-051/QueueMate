-- game_mode_configs 초기 행.
--
-- 매칭이 INV-3(파티 정원)과 INV-8(게임별 hard rule)을 판단하는 근거다. 이 표가 비어 있으면
-- 위반을 막는 것이 아니라 위반인지 물어볼 대상이 없다. 코드가 옳아도 검증이 성립하지 않는다.
--
-- 값은 docs/02 §3~§5의 모드 목록과 같아야 한다. 모드를 늘리는 것은 코드 변경이 아니라
-- 이 표에 행을 넣는 일이다 (docs/02).
--
-- 이미 손으로 넣어 둔 개발 DB가 있어 충돌은 무시한다. 시드는 값을 덮어쓰지 않는다.
INSERT INTO game_mode_configs (game_key, mode_key, target_party_size, role_uniqueness, active) VALUES
    -- LoL 랭크는 자리가 하나뿐이라 포지션 중복을 hard reject 한다.
    ('LOL',      'SOLO_DUO_RANKED', 2, TRUE,  TRUE),
    ('VALORANT', 'COMPETITIVE',     5, FALSE, TRUE),
    ('VALORANT', 'UNRATED',         5, FALSE, TRUE),
    ('PUBG',     'DUO',             2, FALSE, TRUE),
    ('PUBG',     'SQUAD',           4, FALSE, TRUE)
ON CONFLICT (game_key, mode_key) DO NOTHING;
