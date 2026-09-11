-- 고른 bucket들에서 오래 기다린 순으로 quota만큼 꺼낸다 (docs/07 §3.2).
--
-- bucket마다 따로 왕복하면 모드 하나에 수십 번이 된다. 한 번에 끝낸다.
--
-- 어느 requestId를 어느 bucket에서 읽었는지 호출자가 알아야 한다. DB에서 이미 끝난 요청은
-- 조건을 다시 읽을 수 없어, 읽어 온 bucket을 기억해 두지 않으면 어디서 지울지 알 수 없다
-- (docs/07 §3.3). 그래서 평평한 목록이 아니라 bucket별로 개수를 앞세워 돌려준다.
--
-- KEYS[1..N]  꺼낼 bucket key
-- ARGV[1..N]  KEYS와 같은 순서의 quota. 0 이하면 그 bucket은 건너뛴다
--
-- 반환  bucket마다 { 개수, requestId * 개수 } 를 이어 붙인 평평한 배열

if #ARGV ~= #KEYS then
  return redis.error_reply('BAD_ARGV: bucket마다 quota 하나')
end

local out = {}

for i = 1, #KEYS do
  local quota = tonumber(ARGV[i])
  local ids = {}
  if quota and quota > 0 then
    ids = redis.call('ZRANGE', KEYS[i], 0, quota - 1)
  end
  out[#out + 1] = tostring(#ids)
  for j = 1, #ids do
    out[#out + 1] = ids[j]
  end
end

return out
