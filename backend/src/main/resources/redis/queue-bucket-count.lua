-- 모드 전체의 대기 인원 (docs/07 §3.1).
--
-- bucket으로 나뉘었으므로 ZCARD 하나로는 알 수 없다. 대기 화면과 metric이 쓴다.
--
-- KEYS[1..N]  모드의 모든 bucket key
--
-- 반환  합계

local total = 0

for i = 1, #KEYS do
  total = total + redis.call('ZCARD', KEYS[i])
end

return total
