-- LoL 전적 스냅숏(game_account_stats.detail)의 모스트 챔피언을 새 모양으로 옮긴다 — 2026-09-30 소유자 결정(contracts/platform-api.md P-39).
-- 모스트 챔피언이 "최근 경기에서 많이 한 셋 + 그 판 수 · 승률 · 통산 숙련도" 에서 "통산 숙련도 점수 상위 셋 · 숙련도만" 이 됐다.
-- 새 줄은 {"championId", "masteryLevel", "masteryPoints"} 셋이고 숙련도 둘은 늘 값이 있다.
-- 앱은 detail 을 들여다보지 않고 글자 그대로 싣는다(GameStatsResponse) — 옮기지 않으면 다음 연결 · 전적 갱신 전까지 옛 칸이 그대로 밖에 나간다.
--
-- 옛 줄은 이렇게 옮긴다 —
--   games · winRate 를 버린다(최근 경기의 수라 새 모양에 없다).
--   숙련도(masteryLevel · masteryPoints)가 숫자가 아닌 줄(그 챔피언이 숙련도 목록에 없었거나 그때 숙련도를 못 받았다)은 버린다.
--   남은 줄을 masteryPoints 내림차순 → masteryLevel 내림차순 → championId 오름차순(COLLATE "C" — 앱의 String 비교와 같다)으로 줄 세운다.
-- 옛 셋은 "최근에 많이 한 챔피언" 이었으므로 옮긴 결과가 진짜 숙련도 상위 셋은 아닐 수 있다 — 다음 연결 · 전적 갱신 때 Riot 에서 다시 채워진다.
--
-- 이미 새 모양인 줄은 같은 값으로 다시 적힌다. mostChampions 가 배열이 아닌 줄 · LoL 이 아닌 줄은 건드리지 않는다.
-- V1 ~ V6 은 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
UPDATE game_account_stats s
SET detail = jsonb_set(s.detail, '{mostChampions}', COALESCE((
        SELECT jsonb_agg(jsonb_build_object('championId', e -> 'championId',
                                            'masteryLevel', e -> 'masteryLevel',
                                            'masteryPoints', e -> 'masteryPoints')
                         ORDER BY (e ->> 'masteryPoints')::numeric DESC,
                                  (e ->> 'masteryLevel')::numeric DESC,
                                  (e ->> 'championId') COLLATE "C")
        FROM jsonb_array_elements(s.detail -> 'mostChampions') AS e
        WHERE jsonb_typeof(e -> 'championId') IN ('string', 'number')
          AND jsonb_typeof(e -> 'masteryLevel') = 'number'
          AND jsonb_typeof(e -> 'masteryPoints') = 'number'
    ), '[]'::jsonb))
FROM game_accounts a
WHERE a.id = s.game_account_id
  AND a.game = 'LOL'
  AND jsonb_typeof(s.detail -> 'mostChampions') = 'array';
