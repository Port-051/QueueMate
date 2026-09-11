-- 비어 있지 않은 대기열 bucket과 그 깊이를 읽는다 (docs/07 §3.2).
--
-- 후보를 읽기 전에 "어느 bucket을 볼 것인가"를 정해야 한다. 그 판단에 필요한 것은
-- 각 bucket의 최고참 대기 시각(aging 기준)과 인원(quota 계산)뿐이다.
-- bucket 수는 모드당 유한하고 작으므로(LoL 54) 한 번에 훑는다.
--
-- KEYS[1..N]  모드의 모든 bucket key. 호출자가 만든 순서를 그대로 쓴다
--
-- 반환  비어 있지 않은 bucket마다 3개씩 이어 붙인 평평한 배열
--       { KEYS 인덱스, 최고참 score, 인원, ... }
--       비어 있는 bucket은 아예 나오지 않는다

local out = {}

for i = 1, #KEYS do
  local card = redis.call('ZCARD', KEYS[i])
  if card > 0 then
    local head = redis.call('ZRANGE', KEYS[i], 0, 0, 'WITHSCORES')
    out[#out + 1] = tostring(i)
    out[#out + 1] = head[2]
    out[#out + 1] = tostring(card)
  end
end

return out
