-- 후보 N명을 전원 잠그거나, 한 명도 잠그지 않는다 (docs/03 §7, docs/07 §5).
-- GET -> 애플리케이션 판단 -> SET 으로 나누지 않는다. 검증과 쓰기가 한 스크립트 안에서 끝난다.
--
-- KEYS[1]            qm:queue:{game}:{mode}             ZSET
-- KEYS[2]            qm:proposal:members:{proposalId}   SET
-- KEYS[3], KEYS[4]   사용자 1의 active-proposal, active-request
-- KEYS[5], KEYS[6]   사용자 2의 active-proposal, active-request
-- ...
--
-- ARGV[1]            proposalId
-- ARGV[2]            ttlSeconds
-- ARGV[3], ARGV[4]   사용자 1의 expectedRequestId, userId
-- ARGV[5], ARGV[6]   사용자 2의 expectedRequestId, userId
-- ...
--
-- 반환  1  전원 claim 성공
--       0  한 명이라도 충돌. 아무것도 바꾸지 않는다 (INV-2)
--
-- proposal 본문(qm:proposal:{id} HASH)과 DB 저장은 이 스크립트의 책임이 아니다.
-- 여기는 "동시에 두 proposal에 들어가지 않는다"만 보장한다.
--
-- Redis Cluster로 가면 KEYS가 같은 슬롯에 있어야 한다. 현재는 단일 인스턴스 전제이고,
-- 필요해지면 hash tag를 도입한다.

if #KEYS < 4 or (#KEYS - 2) % 2 ~= 0 then
  return redis.error_reply('BAD_KEYS: queue, members, then 2 keys per user')
end

local memberCount = (#KEYS - 2) / 2

if #ARGV ~= 2 + memberCount * 2 then
  return redis.error_reply('BAD_ARGV: proposalId, ttl, then 2 args per user')
end

local queueKey = KEYS[1]
local membersKey = KEYS[2]
local proposalId = ARGV[1]
local ttl = tonumber(ARGV[2])

if not ttl or ttl <= 0 then
  return redis.error_reply('BAD_TTL: must be a positive number of seconds')
end

-- 1단계: 전원 검증. 여기서는 한 글자도 쓰지 않는다.
local seenProposalKey = {}
for i = 1, memberCount do
  local proposalKey = KEYS[2 + i * 2 - 1]
  local requestKey = KEYS[2 + i * 2]
  local expectedRequestId = ARGV[2 + i * 2 - 1]

  -- 같은 사용자를 두 번 담으면 정원이 부풀려진다 (INV-7). 호출자 버그이므로 숨기지 않는다.
  if seenProposalKey[proposalKey] then
    return redis.error_reply('DUPLICATE_MEMBER: ' .. proposalKey)
  end
  seenProposalKey[proposalKey] = true

  -- 이미 다른 proposal에 묶여 있다.
  if redis.call('EXISTS', proposalKey) == 1 then
    return 0
  end

  -- 활성 요청이 사라졌거나(취소/만료) 우리가 본 것과 다른 요청으로 교체됐다.
  if redis.call('GET', requestKey) ~= expectedRequestId then
    return 0
  end
end

-- 2단계: 전원 통과. 이제 쓴다.
for i = 1, memberCount do
  local proposalKey = KEYS[2 + i * 2 - 1]
  local expectedRequestId = ARGV[2 + i * 2 - 1]
  local userId = ARGV[2 + i * 2]

  redis.call('SET', proposalKey, proposalId, 'EX', ttl)
  redis.call('ZREM', queueKey, expectedRequestId)
  redis.call('SADD', membersKey, userId)
end

redis.call('EXPIRE', membersKey, ttl)

return 1
