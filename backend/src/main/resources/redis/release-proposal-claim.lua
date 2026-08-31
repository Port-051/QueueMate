-- 제안이 끝나 참가자 잠금을 푼다.
--
-- 반드시 compare-and-delete로 지운다. 그냥 DEL 하면 이런 일이 생긴다.
--   1. P1의 Redis TTL이 먼저 끝나 잠금이 사라진다
--   2. 그 사이 사용자가 새 제안 P2에 claim된다
--   3. 뒤늦게 도착한 P1 정리가 P2의 잠금을 지운다
--   4. 그 사용자는 P2에 묶인 채로 P3에도 claim된다 -> INV-2가 깨진다
--
-- KEYS[1..N]   각 사용자의 qm:user:active-proposal:{userId}
-- KEYS[N+1]    qm:proposal:members:{proposalId}
--
-- ARGV[1]      정리하려는 proposalId
--
-- 반환  실제로 푼 잠금 수

if #KEYS < 2 then
  return redis.error_reply('BAD_KEYS: 사용자 키 최소 1개 + members 키')
end

local proposalId = ARGV[1]
local released = 0

for i = 1, #KEYS - 1 do
  -- 내 것일 때만 지운다. 남이 이미 가져간 잠금은 건드리지 않는다.
  if redis.call('GET', KEYS[i]) == proposalId then
    redis.call('DEL', KEYS[i])
    released = released + 1
  end
end

-- members 키 이름에 proposalId가 들어 있어 남의 것과 섞이지 않는다.
redis.call('DEL', KEYS[#KEYS])

return released
