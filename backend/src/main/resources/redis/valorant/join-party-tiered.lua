-- VALORANT 티어를 보는 배정(경쟁전)의 합류. create-or-check-party-tiered.lua 가 찾아 둔 파티에 넣는다.
--
-- 티어를 보지 않는 모드(일반전)는 join-party.lua 가 맡는다.
-- 찾기와 합류 사이에 자바의 차단 검증이 끼고, 그 틈은 자바의 후보 풀 락
-- (redisLock/PoolLock.java)이 막는다.
--
-- ── 이 스크립트가 파티 범위를 좁힌다 ─────────────────────────────────
-- 발로란트 규칙은 "파티 최고 <= 한계(파티 최저)" 라, 셋이 되면 방장 줄 하나로는 모자라다.
-- 그래서 합류할 때마다 파티 범위를 **지금 범위와 들어온 사람 줄의 교집합**으로 좁힌다.
--
--   · 높은 사람이 들어오면 아래쪽이 올라가고, 낮은 사람이 들어오면 위쪽이 내려간다
--   · 옛 범위 칸 전부에서 파티를 뺀 뒤, 아직 비어 있는 역할군(needs-roles SET) x 새 범위로 다시 등록한다
--   · tierLo / tierHi 를 새 값으로 적는다
--
-- 그래서 색인에 남는 칸이 실제로 들어올 수 있는 티어와 같아진다 — 후보로 잡혔다는 것이
-- 곧 파티 전원과 같이 갈 수 있다는 뜻이다. create 에 따로 거르는 분기가 필요 없는 이유다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- create-or-check-party-tiered.lua 와 같은 배치다. 자바가 한 벌을 두 스크립트에 넘긴다.
--
-- KEYS[1]   = qm:party:{newPartyId}   **이 스크립트는 읽지 않는다** (배치를 맞추려고 둔다)
-- KEYS[2]   = qm:user:active-request:{userId}            HASH. partyId 를 기록한다
-- KEYS[3]   = qm:gameconfig:VALORANT:tier-range:{mode}   HASH. 교집합을 구하려고 내 줄을 읽는다
-- KEYS[4]   = qm:gameconfig:VALORANT:tier                ZSET. 순번 <-> 티어 이름
-- KEYS[5]   = qm:party:open:VALORANT:{mode}:{voice}:{purpose}:needs   역할군도 티어도 없는 밑동.
--             KEYS[5] .. ':' .. 역할군 .. ':' .. 티어이름 으로 칸을 만든다
-- KEYS[6..] = 위 밑동에 역할군이 붙은 키. KEYS[5 + p] .. ':' .. 티어이름  -- p = 역할군 순번
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- create 판과 같은 배치다. ARGV[1] 이 "들어갈 파티 id", ARGV[8] 이 start 대신 제안 시한이다.
--
-- ARGV[1]   = partyId. 들어갈 파티 id
-- ARGV[2]   = score   안 읽는다 — 다시 등록할 때는 파티의 createdAt 을 쓴다
-- ARGV[3]   = 내 keyValue (역할군)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 발로란트는 늘 "true" 다
-- ARGV[6]   = qm:party: 접두사. 들어갈 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = expiresAt (epoch millis). 정원이 찰 때 여는 제안의 시한.
--             자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다
-- ARGV[9]   = 내 티어 이름. 내 줄을 읽어 교집합을 구하는 데 쓴다
-- ARGV[10..]= 역할군 목록. 개수 P = #ARGV - 9
--
-- ── 반환 {코드, partyId, 현재인원} ───────────────────────────────────
--   1 = 들어갔다. 아직 자리가 남았다          { 1, partyId, size }
--   2 = 들어갔고 정원이 찼다 (제안을 열었다)  { 2, partyId, size }
--  -1 = 설정과 맞지 않는다                    { -1, '', 0 }
--       내 역할군이 목록에 없거나, 파티에 tierLo/tierHi/minTier/maxTier 가 없거나,
--       tier-range 표에 내 티어 줄이 없다
--  -2 = claim 의 TTL 이 먼저 끝났다           { -2, '', 0 }

local userKey = KEYS[2]

local partyId = ARGV[1]
local myValue = ARGV[3]
local target  = tonumber(ARGV[4])
local unique  = ARGV[5] == 'true'
local prefix  = ARGV[6]
local userId  = ARGV[7]
local expiresAt = ARGV[8]
local myTier = ARGV[9]
local partyKey = prefix .. partyId

local memberField = 'member:' .. userId

-- 파티 인원은 세어서 구한다. 저장된 카운터를 쓰지 않는다.
--
-- Lua 는 롤백이 없다. 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기는 남고,
-- 페일오버 뒤 재시도가 같은 명령을 한 번 더 실행한다. 그때
-- HINCRBY 는 두 번 더해져 인원 수가 실제 멤버 수와 어긋나지만
-- (4 명짜리 파티가 "꽉 찼다"고 판정되어 INV-3 이 깨진다),
-- HSET 과 세기는 몇 번 해도 결과가 같다.
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

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다.
-- HSET 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 partyId 하나만 든 반쪽짜리
-- 활성 요청이 되살아난다. 그 상태로는 취소가 game 필드를 못 읽어 터진다
if redis.call('EXISTS', userKey) == 0 then
    return { -2, '', 0 }
end

local P = #ARGV - 9    -- 역할군 개수. ARGV[9 + p] <-> 격자의 p 번째 줄

-- 내 포지션이 격자의 몇 번째 줄인지 찾는다
local myPos
for p = 1, P do
    if ARGV[9 + p] == myValue then
        myPos = p
        break
    end
end
if myPos == nil then
    return { -1, '', 0 }
end


-- 1. 이 파티가 올라가 있는 칸이 어디인지 **먼저** 읽는다.
--
--    **파티에 적힌 지금 범위로 빼야 한다.** 내 줄로 빼면 파티가 실제로 올라가 있는 칸과
--    어긋나 일부 칸에 그대로 남는다 — 정원이 찼는데도 남의 눈에 후보로 보이는 유령이 되고
--    INV-3 이 깨진다. 새 범위는 아래 2번에서 따로 구한다.
--
--    create 가 tierLo/tierHi 를 ZRANK + 1 로 적어 두었으므로 되돌릴 때 1 을 뺀다.
--    쓰기보다 먼저 하는 이유는 Lua 에 롤백이 없기 때문이다 — 멤버만 넣고 색인 정리에서
--    터지면 정원이 찬 파티가 색인에 남는다.
local bound = redis.call('HMGET', partyKey, 'tierLo', 'tierHi')
local lo = tonumber(bound[1])
local hi = tonumber(bound[2])
if lo == nil or hi == nil then
    return { -1, '', 0 }
end
local range = redis.call('ZRANGE', KEYS[4], lo, hi)

-- 2. 이번 합류로 파티 범위가 어디로 좁아지는지 **쓰기 전에 정해 둔다.**
--
--    Lua 는 롤백이 없다. 멤버를 먼저 넣고 나서 표를 읽다 실패하면(줄이 없거나 SOLO_ONLY 이거나
--    페일오버로 끊기거나) 멤버는 들어갔는데 색인은 정리되지 않은 반쪽 상태가 남는다.
--    자바는 음수를 "합류 실패"로 보므로 아무도 그 사실을 모른다.
--
--    새 범위는 **지금 범위와 내 줄의 교집합**이다. 양쪽 다 좁아질 수 있다.
--      · 나보다 높은 사람이 들어오면 아래쪽이 올라간다 (그 사람을 못 받는 낮은 티어를 뺀다)
--      · 나보다 낮은 사람이 들어오면 위쪽이 내려간다 (그 사람을 못 받는 높은 티어를 뺀다)
--    이렇게 하면 색인에 남는 칸이 "실제로 들어올 수 있는 티어"와 정확히 같아진다 —
--    후보로 잡혔다는 것이 곧 파티 전원과 같이 갈 수 있다는 뜻이 된다
local myTierRank = redis.call('ZRANK', KEYS[4], myTier)
local partyMin = tonumber(redis.call('HGET', partyKey, 'minTier'))
local partyMax = tonumber(redis.call('HGET', partyKey, 'maxTier'))
if myTierRank == false or partyMin == nil or partyMax == nil then
    return { -1, '', 0 }
end

local tierRange = redis.call('HGET', KEYS[3], myTier)
if tierRange == false then
    return { -1, '', 0 }
end
local minMaxTier = {}
for tier in string.gmatch(tierRange, "([^:]+)") do
    table.insert(minMaxTier, tier)
end
-- 'SOLO_ONLY' 처럼 범위가 아닌 값이 여기서 걸린다
if #minMaxTier ~= 2 then
    return { -1, '', 0 }
end
local myLo = redis.call('ZRANK', KEYS[4], minMaxTier[1])
local myHi = redis.call('ZRANK', KEYS[4], minMaxTier[2])
if myLo == false or myHi == false then
    return { -1, '', 0 }
end

local newLo = lo
local newHi = hi
if myLo > newLo then newLo = myLo end
if myHi < newHi then newHi = myHi end
-- 후보 색인에서 나온 파티라 교집합이 빌 수 없다. 설정이 그 사이 바뀌었을 때의 방어다
if newLo > newHi then
    return { -1, '', 0 }
end

local narrows = newLo ~= lo or newHi ~= hi
local newRange, openRoles, createdAt
if narrows then
    newRange = redis.call('ZRANGE', KEYS[4], newLo, newHi)
    -- 다시 올릴 대상은 **아직 빈** 역할군이다. 아래에서 SREM 하기 전에 읽으므로 내 역할군이
    -- 아직 들어 있다 — 올릴 때 건너뛴다. 이미 찬 역할군을 올리면 같은 역할군 두 명이 된다
    openRoles = redis.call('SMEMBERS', 'qm:party:needs-roles:' .. partyId)
    -- 정렬값은 지금 시각이 아니라 파티의 createdAt 이다. now 를 쓰면 이 파티만 색인에서
    -- 가장 새 것으로 밀려 먼저 기다린 파티보다 늦게 잡힌다
    createdAt = redis.call('HGET', partyKey, 'createdAt')
end

-- 3. 여기서부터 쓰기다. 위에서 읽기·검증이 전부 끝났다
-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, myValue)
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

-- 내 역할군 자리는 이제 찼다. 빈 역할군 목록에서 뺀다.
-- **범위를 좁히는 분기 안이 아니라 여기여야 한다.** 나보다 티어가 높은 사람이 들어오면
-- 그 분기를 타지 않으므로, 거기서만 빼면 이미 찬 역할군이 목록에 남는다. 그러면 나중에
-- 범위를 좁히는 사람이 그 역할군 칸에 파티를 다시 올려 같은 역할군 두 명이 될 수 있다
redis.call('SREM', 'qm:party:needs-roles:' .. partyId, myValue)

-- 4. 내 역할군 줄은 이제 찼다. 그 줄의 모든 티어 칸에서 뺀다
if unique then
    for _, member in ipairs(range) do
        redis.call('ZREM', KEYS[5 + myPos] .. ':' .. member, partyId)
    end
end

if size >= target then
    -- 5. 정원이 찼다. 이 파티가 올라가 있던 칸 전부에서 뺀다
    for p = 1, P do
        for _, member in ipairs(range) do
            redis.call('ZREM', KEYS[5 + p] .. ':' .. member, partyId)
        end
    end

    -- 6. 제안이 열린다. status 와 expiresAt 은 여기서 딱 한 번만 생겨야 한다.
    --
    -- HSET 이 아니라 HSETNX 인 이유: Lua 는 롤백이 없어서, 페일오버 뒤 재시도가
    -- 이 스크립트를 처음부터 한 번 더 실행할 수 있다. 그때 HSET 이면 이미
    -- CONFIRMED 된 제안이 PENDING 으로 되돌아가 INV-5 가 깨진다.
    -- expiresAt 을 같은 분기 안에 넣는 것도 같은 이유다 — 재시도가 시한을 연장하면
    -- 만료된 제안이 되살아난다. 처음 성공한 한 번의 값이 끝까지 그대로여야 한다.
    if redis.call('HSETNX', partyKey, 'status', 'PENDING') == 1 then
        redis.call('HSET', partyKey, 'expiresAt', expiresAt)
    end

    -- 정원이 찼으니 빈 역할군 목록은 쓸 일이 없다. TTL 이 없는 키라 여기서 안 지우면 영원히 남는다.
    -- (마지막 한 명이 나가 파티가 사라질 때 지우는 것은 leave 스크립트의 몫이다)
    redis.call('DEL', 'qm:party:needs-roles:' .. partyId)

    return { 2, partyId, size }
end

-- 7. 아직 자리가 남았다. 범위가 좁아졌으면 옛 범위 칸에서 전부 빼고
--    아직 빈 역할군만 새 범위 칸에 다시 올린다
if narrows then
    for p = 1, P do
        for _, member in ipairs(range) do
            redis.call('ZREM', KEYS[5 + p] .. ':' .. member, partyId)
        end
    end
    for _, role in ipairs(openRoles) do
        if role ~= myValue then
            for _, tierName in ipairs(newRange) do
                redis.call('ZADD', KEYS[5] .. ':' .. role .. ':' .. tierName, createdAt, partyId)
            end
        end
    end
    redis.call('HSET', partyKey, 'tierLo', newLo, 'tierHi', newHi)
end

-- 파티의 최저·최고 티어를 갱신한다. 지금은 범위를 좁히는 데 쓰지 않지만,
-- 앞으로 leave 가 남은 사람 기준으로 범위를 되돌릴 때 필요하다
if myTierRank < partyMin then
    redis.call('HSET', partyKey, 'minTier', myTierRank)
elseif partyMax < myTierRank then
    redis.call('HSET', partyKey, 'maxTier', myTierRank)
end

return { 1, partyId, size }
