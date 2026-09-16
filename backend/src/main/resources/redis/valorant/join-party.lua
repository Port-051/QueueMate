-- VALORANT 티어를 보지 않는 배정(일반전)의 합류. create-or-check-party-untiered.lua 가
-- 찾아 둔 파티에 넣는다.
--
-- 티어를 보는 모드(경쟁전)는 join-party-tiered.lua 가 맡는다. 그쪽과 달리 파티 범위를
-- 좁힐 일이 없어 tier-range 표도 사다리도 읽지 않는다.
--
-- 찾기와 합류 사이에 자바의 차단 검증이 끼고, 그 틈은 자바의 후보 풀 락
-- (redisLock/PoolLock.java)이 막는다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- create-or-check-party-untiered.lua 와 같은 배치다.
--
-- KEYS[1]   = qm:party:{newPartyId}   **이 스크립트는 읽지 않는다** (배치를 맞추려고 둔다)
-- KEYS[2]   = qm:user:active-request:{userId}    HASH. partyId 를 기록한다
-- KEYS[3..] = qm:party:open:VALORANT:{mode}:{voice}:{purpose}:needs:{역할군}   ZSET
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1]   = partyId. 들어갈 파티 id
-- ARGV[2]   = score               안 읽는다 (새로 만들지 않아 등록할 일이 없다)
-- ARGV[3]   = 내 keyValue (역할군)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 발로란트는 늘 "true" 다
-- ARGV[6]   = qm:party: 접두사. 들어갈 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = expiresAt (epoch millis). 정원이 찰 때 여는 제안의 시한.
--             자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다
-- ARGV[9..] = 역할군 목록. 개수 n = #ARGV - 8
--
-- ── 반환 {코드, partyId, 현재인원} ───────────────────────────────────
--   1 = 들어갔다. 아직 자리가 남았다          { 1, partyId, size }
--   2 = 들어갔고 정원이 찼다 (제안을 열었다)  { 2, partyId, size }
--  -1 = 내 역할군이 목록에 없다               { -1, '', 0 }
--  -2 = claim 의 TTL 이 먼저 끝났다           { -2, '', 0 }

local userKey = KEYS[2]

local partyId    = ARGV[1]
local myValue    = ARGV[3]
local target     = tonumber(ARGV[4])
local unique     = ARGV[5] == 'true'
local prefix     = ARGV[6]
local userId     = ARGV[7]
local expiresAt  = ARGV[8]

local memberField = 'member:' .. userId

-- 파티 인원은 세어서 구한다. 저장된 카운터를 쓰지 않는다.
--
-- Lua 는 롤백이 없다. 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기는 남고,
-- 페일오버 뒤 재시도가 같은 명령을 한 번 더 실행한다. 그때 HINCRBY 는 두 번 더해져
-- 인원 수가 실제 멤버 수와 어긋나지만, HSET 과 세기는 몇 번 해도 결과가 같다.
--
-- 접두사 'member:' 는 자바의 ScriptSupport#memberIds() 와 같은 값이어야 한다.
local function memberCount(partyKey)
    local fields = redis.call('HKEYS', partyKey)
    local n = 0
    for i = 1, #fields do
        if string.sub(fields[i], 1, 7) == 'member:' then
            n = n + 1
        end
    end
    return n
end

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다
if redis.call('EXISTS', userKey) == 0 then
    return { -2, '', 0 }
end

local n = #ARGV - 8    -- 역할군 개수. KEYS[2+i] <-> ARGV[8+i]

-- 내 역할군의 색인이 KEYS 중 몇 번째인지 찾는다
local myIndexKey
for i = 1, n do
    if ARGV[8 + i] == myValue then
        myIndexKey = KEYS[2 + i]
        break
    end
end
if myIndexKey == nil then
    return { -1, '', 0 }
end

local partyKey = prefix .. partyId

-- 1. 그 파티에 들어간다
-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, myValue)
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

-- 내 역할군 자리는 이제 찼다. 색인과 빈 역할군 목록 둘 다에서 뺀다
if unique then
    redis.call('ZREM', myIndexKey, partyId)
end
redis.call('SREM', 'qm:party:needs-roles:' .. partyId, myValue)

if size >= target then
    -- 2. 정원이 찼다. 아직 남아 있는 색인에서도 전부 뺀다
    for i = 1, n do
        redis.call('ZREM', KEYS[2 + i], partyId)
    end

    -- 3. 제안이 열린다. status 와 expiresAt 은 여기서 딱 한 번만 생겨야 한다.
    --
    -- HSET 이 아니라 HSETNX 인 이유: Lua 는 롤백이 없어서, 페일오버 뒤 재시도가
    -- 이 스크립트를 처음부터 한 번 더 실행할 수 있다. 그때 HSET 이면 이미
    -- CONFIRMED 된 제안이 PENDING 으로 되돌아가 INV-5 가 깨진다.
    -- expiresAt 을 같은 분기 안에 넣는 것도 같은 이유다 — 재시도가 시한을 연장하면
    -- 만료된 제안이 되살아난다. 처음 성공한 한 번의 값이 끝까지 그대로여야 한다.
    if redis.call('HSETNX', partyKey, 'status', 'PENDING') == 1 then
        redis.call('HSET', partyKey, 'expiresAt', expiresAt)
    end

    -- 정원이 찼으니 빈 역할군 목록은 쓸 일이 없다. TTL 이 없는 키라 여기서 안 지우면 영원히 남는다
    redis.call('DEL', 'qm:party:needs-roles:' .. partyId)

    return { 2, partyId, size }
end

return { 1, partyId, size }
