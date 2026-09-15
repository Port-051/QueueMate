-- 예약 제안용 all-or-nothing claim (INV-2).
--
-- 실시간과 같은 active-proposal 키를 쓴다. 실시간 제안을 받고 있는 사람에게
-- 예약 제안이 겹쳐 오면 두 개를 동시에 들고 있게 되기 때문이다.
--
-- 실시간과 달리 대기열 ZSET과 active-request guard가 없다. 예약의 진실은 DB이고
-- Redis는 슬롯 색인과 잠금만 담당한다 (docs/04 §6).
--
-- KEYS[1]        qm:proposal:members:{proposalId}
-- KEYS[2..N+1]   각 사용자의 qm:user:active-proposal:{userId}
--
-- ARGV[1]        proposalId
-- ARGV[2]        ttlSeconds
-- ARGV[3..N+2]   userIds. KEYS[2..]와 같은 순서다
--
-- 반환  1  전원 claim 성공
--       0  한 명이라도 이미 다른 제안에 묶여 있다. 아무것도 바꾸지 않는다

if #KEYS < 3 then
  return redis.error_reply('BAD_KEYS: members key + 최소 2명')
end

local memberCount = #KEYS - 1

if #ARGV ~= 2 + memberCount then
  return redis.error_reply('BAD_ARGV: proposalId, ttl, 사용자당 1개')
end

local membersKey = KEYS[1]
local proposalId = ARGV[1]
local ttl = tonumber(ARGV[2])

if not ttl or ttl <= 0 then
  return redis.error_reply('BAD_TTL: must be a positive number of seconds')
end

-- 1단계: 전원 검증. 쓰지 않는다.
local seen = {}
for i = 1, memberCount do
  local proposalKey = KEYS[i + 1]
  if seen[proposalKey] then
    return redis.error_reply('DUPLICATE_MEMBER: ' .. proposalKey)
  end
  seen[proposalKey] = true

  if redis.call('EXISTS', proposalKey) == 1 then
    return 0
  end
end

-- 2단계: 전원 통과.
for i = 1, memberCount do
  redis.call('SET', KEYS[i + 1], proposalId, 'EX', ttl)
  redis.call('SADD', membersKey, ARGV[i + 2])
end

redis.call('EXPIRE', membersKey, ttl)

return 1
