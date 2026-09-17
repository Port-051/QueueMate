-- 연결된 게임 계정의 티어를 외부 API에서 주기적으로 채운다.

-- rank_code는 V1부터 있었지만 아무도 채우지 않았다. 이제 채우기 시작하므로
-- "언제 채운 값인가"가 필요하다. updated_at으로는 대신할 수 없다. 그쪽은 지역 수정 같은
-- 다른 이유로도 움직여서, 갱신이 필요한지 판단하는 근거가 되지 못한다.
ALTER TABLE game_accounts ADD COLUMN rank_updated_at TIMESTAMPTZ;

-- 조회 시도 자체의 시각이다. rank_updated_at과 나누는 이유는 랭크가 없는 계정
-- (언랭·신규) 때문이다. 둘이 같으면 rank_code가 NULL인 계정을 매번 다시 조회하게 된다.
-- 외부 API에는 호출 한도가 있고, 언랭은 가장 흔한 상태다.
ALTER TABLE game_accounts ADD COLUMN rank_synced_at TIMESTAMPTZ;
