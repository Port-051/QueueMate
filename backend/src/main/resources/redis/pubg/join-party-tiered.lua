-- PUBG 랭크(티어를 보는) 배정의 합류. create-or-check-party-tiered.lua 가 찾아 둔 파티에 넣는다.
--
-- 티어를 보지 않는 모드(일반전)는 join-party.lua 가 맡는다.
-- 찾기와 합류는 두 스크립트로 나뉜다. 사이에 자바의 차단 검증이 끼고, 그 틈은
-- 자바의 후보 풀 락(redisLock/PoolLock.java)이 막는다.
--
-- **티어 범위는 다시 계산하지 않는다.** 파티가 받아들일 범위는 만든 사람 기준으로 생성 시
-- 정해져 파티 HASH 의 tierLo/tierHi 에 있다. 이 스크립트는 그 범위의 칸에서 빼기만 한다.
--
-- LoL 과 다른 점: 포지션도 중복 금지도 없다. 그래서 들어가도 **정원이 차기 전에는 색인에서
-- 빼지 않는다** — 같은 플랫폼 사람이 더 들어와야 하기 때문이다. 정원이 차면 그때 모든 칸에서 뺀다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- create-or-check-party-tiered.lua 와 같은 배치다. 자바가 한 벌을 두 스크립트에 넘긴다.
--
-- KEYS[1] = qm:party:{newPartyId}                  **이 스크립트는 읽지 않는다** (배치를 맞추려고 둔다)
-- KEYS[2] = qm:user:active-request:{userId}        HASH. partyId 를 기록한다
-- KEYS[3] = qm:gameconfig:PUBG:tier-range:{mode}   **이 스크립트는 읽지 않는다** — 범위는 파티 HASH 에 있다
-- KEYS[4] = qm:gameconfig:PUBG:tier                ZSET. tierLo/tierHi(순번)를 티어 이름으로 되돌린다
-- KEYS[5] = qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{플랫폼}   ZSET
--           티어 접미사 없이 넘어오며 KEYS[5] .. ':' .. 티어이름 으로 칸을 만든다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- create 판과 자리를 맞춘다 (자바 joinArgs 와 짝).
--
-- ARGV[1] = partyId. 들어갈 파티 id
-- ARGV[2] = score               안 읽는다 (새로 만들지 않아 등록할 일이 없다)
-- ARGV[3] = targetPartySize
-- ARGV[4] = qm:party: 접두사. 들어갈 파티 키를 만들 때 쓴다
-- ARGV[5] = userId
-- ARGV[6] = expiresAt (epoch millis). 정원이 찰 때 여는 제안의 시한.
--           자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다
-- ARGV[7] = 내 티어 이름        안 읽는다 (뺄 칸은 파티의 tierLo/tierHi 가 정한다)
--
-- ── 반환 {코드, partyId, 현재인원} ───────────────────────────────────
--   1 = 들어갔다. 아직 자리가 남았다          { 1, partyId, size }
--   2 = 들어갔고 정원이 찼다 (제안을 열었다)  { 2, partyId, size }
--  -1 = 파티에 tierLo/tierHi 가 없다         { -1, '', 0 }
--       찾은 뒤 합류 전에 파티가 사라졌거나 망가진 경우다. 쓰기 전에 걸려 파티를 되살리지 않는다
--  -2 = claim 의 TTL 이 먼저 끝났다          { -2, '', 0 }

local userKey = KEYS[2]

local partyId   = ARGV[1]
local target    = tonumber(ARGV[3])
local prefix    = ARGV[4]
local userId    = ARGV[5]
local expiresAt = ARGV[6]

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

local partyKey = prefix .. partyId

-- 1. 이 파티가 올라가 있는 칸이 어디인지 **먼저** 읽는다.
--
--    **내 tier-range 를 다시 읽으면 안 된다.** 받아들일 범위는 파티를 만든 사람 기준으로
--    한 번 정해지고 끝이다 (create-or-check-party-tiered.lua 머리말). 내 범위로 빼면
--    파티가 실제로 올라가 있는 칸과 어긋나 일부 칸에 그대로 남는다 — 정원이 찼는데도
--    남의 눈에 후보로 보이는 유령이 되고 INV-3 이 깨진다.
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
local range = redis.call('ZRANGE', KEYS[4], lo - 1, hi - 1)

-- 2. 그 파티에 들어간다
-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, 'EXIST')
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

if size >= target then
    -- 3. 정원이 찼다. 이 파티가 올라가 있던 티어 칸 전부에서 뺀다.
    for _, member in ipairs(range) do
        redis.call('ZREM', KEYS[5] .. ':' .. member, partyId)
    end

    -- 4. 제안이 열린다. status 와 expiresAt 은 여기서 딱 한 번만 생겨야 한다.
    --
    -- HSET 이 아니라 HSETNX 인 이유: Lua 는 롤백이 없어서, 페일오버 뒤 재시도가
    -- 이 스크립트를 처음부터 한 번 더 실행할 수 있다. 그때 HSET 이면 이미
    -- CONFIRMED 된 제안이 PENDING 으로 되돌아가 INV-5 가 깨진다.
    -- expiresAt 을 같은 분기 안에 넣는 것도 같은 이유다 — 재시도가 시한을 연장하면
    -- 만료된 제안이 되살아난다. 처음 성공한 한 번의 값이 끝까지 그대로여야 한다.
    if redis.call('HSETNX', partyKey, 'status', 'PENDING') == 1 then
        redis.call('HSET', partyKey, 'expiresAt', expiresAt)
    end

    return { 2, partyId, size }
end

return { 1, partyId, size }
