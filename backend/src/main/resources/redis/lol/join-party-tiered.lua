-- 티어를 보는 배정의 합류. 이미 찾아둔 파티에 넣는다.
--
-- 티어를 보지 않는 모드는 join-party.lua 가 맡는다.
--
-- create-or-check-party-tiered.lua 가 후보 파티를 돌려주고 자바가 차단 검증을 마친 뒤
-- 이 스크립트를 부른다. 인원 증가 / 참가자 기록 / 색인 정리가 한 덩어리여야
-- 정원 초과와 유령 색인이 생기지 않는다.
--
-- **티어 범위는 다시 계산하지 않는다.** 파티가 받아들일 범위는 만든 사람 기준으로
-- 생성 시 정해졌고 끝까지 그대로다. 그래서 이 스크립트는 색인에서 빼기만 한다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1]   = qm:party:{newPartyId}              HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}    HASH. partyId 를 기록한다
-- KEYS[3]   = qm:gameconfig:LOL:tier-range:{mode}    HASH. **이 스크립트는 읽지 않는다.**
--             create 와 KEYS 배치를 맞추려고 자리만 차지한다 — 이 파티가 받아들일 범위는
--             만들 때 정해져 파티 HASH 의 tierLo/tierHi 에 들어 있다
-- KEYS[4]   = qm:gameconfig:LOL:tier    ZSET. 티어 사다리. tierLo/tierHi(순번) 를
--             티어 이름으로 되돌려 칸 키를 만드는 데 쓴다
-- KEYS[5..] = qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{포지션}   ZSET
--
--             **티어 접미사가 없는 상태로 넘어온다.** 칸 하나를 가리키는 키는 여기에
--             ':' 와 티어 이름을 붙여 만든다
--
--                 KEYS[4 + p] .. ':' .. 티어이름   -- p = 포지션 순번
--
--             포지션 순번은 ARGV[10..] 의 순서다.
--             격자를 통째로 KEYS 로 받지 않는 이유는 티어에 단(division)이 들어가면
--             칸이 (포지션 6 x 티어 32) = 192 개가 되어 호출마다 그만큼을 넘겨야 하기
--             때문이다. 이렇게 조립하면 KEYS 는 4 + 포지션 개수로 고정된다.
-- ── ARGV ─────────────────────────────────────────────────────────────
-- create-or-check-party-tiered.lua 와 같은 배치다. 두 스크립트가 같은 KEYS 격자를 쓰기 때문이다.
--
-- ARGV[1]   = partyId. 들어갈 파티 id
-- ARGV[2]   = score               안 읽는다 (새로 만들지 않으므로 등록할 일이 없다)
-- ARGV[3]   = 내 keyValue (LoL 포지션)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false")
-- ARGV[6]   = qm:party: 접두사. 들어갈 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = expiresAt (epoch millis). 정원이 찰 때 만드는 제안의 시한이다.
--             자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다.
--             후보를 찾는 단계가 아니라 원래 start 자리로 비어 있던 칸을 쓴다 —
--             #ARGV - 9 로 포지션 개수를 세므로 자리 수는 그대로여야 한다
-- ARGV[9]   = 내 티어 이름        안 읽는다 (빼야 할 칸은 파티의 tierLo/tierHi 가 정한다)
-- ARGV[10..]= 포지션 목록. 개수 P = #ARGV - 9
--
-- ── 반환 {코드, partyId, 현재인원} ───────────────────────────────────
--   1 = 들어갔다. 아직 자리가 남았다
--   2 = 들어갔고 정원이 찼다 (제안을 만들 차례)
--  -1 = 설정과 맞지 않는 keyValue  { -1, '', 0 }

local userKey = KEYS[2]

local partyId = ARGV[1]
local myValue = ARGV[3]
local target  = tonumber(ARGV[4])
local unique  = ARGV[5] == 'true'
local prefix  = ARGV[6]
local userId  = ARGV[7]
local expiresAt = ARGV[8]

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

local P = #ARGV - 9    -- 포지션 개수. ARGV[9 + p] <-> 격자의 p 번째 줄

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

local partyKey = prefix .. partyId

-- 1. 이 파티가 올라가 있는 칸이 어디인지 **먼저** 읽는다.
--
--    **내 tier-range 를 다시 읽으면 안 된다.** 받아들일 범위는 파티를 만든 사람 기준으로
--    한 번 정해지고 끝이다 (create-or-check-party-tiered.lua 머리말). 내 범위로 빼면
--    파티가 실제로 올라가 있는 칸과 어긋나 일부 칸에 그대로 남는다 — 정원이 찼는데도
--    남의 눈에 후보로 보이는 유령이 되고 INV-3 이 깨진다.
--
--    create 가 tierLo/tierHi 를 ZRANK 그대로(0 부터) 적어 두었으므로 ZRANGE 에 그대로 넘긴다.
--    쓰기보다 먼저 하는 이유는 Lua 에 롤백이 없기 때문이다 — 멤버만 넣고 색인 정리에서
--    터지면 정원이 찬 파티가 색인에 남는다.
local bound = redis.call('HMGET', partyKey, 'tierLo', 'tierHi')
local lo = tonumber(bound[1])
local hi = tonumber(bound[2])
if lo == nil or hi == nil then
    return { -1, '', 0 }
end
local range = redis.call('ZRANGE', KEYS[4], lo, hi)

-- 2. 그 파티에 들어간다
-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, myValue)
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

-- 3. 중복을 금지하는 모드면 내 포지션은 이제 찼다. 그 줄의 모든 티어에서 뺀다.
--    중복을 허용하는 모드(칼바람)는 아직 같은 포지션을 더 받을 수 있으므로 그대로 둔다.
if unique then
    for _, member in ipairs(range) do
        redis.call('ZREM', KEYS[4 + myPos] .. ':' .. member, partyId)
    end
end

if size >= target then
    -- 4. 정원이 찼다. 격자 전체에서 뺀다.
    for p = 1, P do
        for _, member in ipairs(range) do
            redis.call('ZREM', KEYS[4 + p] .. ':' .. member, partyId)
        end
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
        -- 만료 대기 목록. 스위퍼가 이 ZSET 에서 시한이 지난 제안만 꺼낸다.
        -- **HSETNX 가 성공한 이 자리여야 한다.** 분기 밖에 두면 재시도가 돌 때마다 점수를
        -- 새 시각으로 덮어써, 파티 HASH 의 expiresAt 은 그대로인데 목록만 미래로 밀린다
        redis.call('ZADD', 'qm:proposal:pending', expiresAt, partyId)
    end
    return { 2, partyId, size }
end

return { 1, partyId, size }
