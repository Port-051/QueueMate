-- 매칭 요청을 취소하고 파티에서 빠진다.
--
-- 빼기 / 인원 감소 / 색인 되돌리기 / 빈 파티 삭제 / 활성 요청 삭제가 한 덩어리여야 한다.
-- 자바에서 나눠 하다 중간에 죽으면 이런 게 남는다.
--   · 파티에선 빠졌는데 색인에 안 돌아감 → 그 자리에 아무도 못 들어온다
--   · 활성 요청은 지웠는데 파티에 member: 필드가 남음 → 유령 인원이 자리를 먹는다
--
-- 인원 수는 따로 저장하지 않고 member: 필드를 세어서 구하므로, 필드를 지우는 것과
-- 인원이 줄어드는 것이 같은 일이다 — 둘이 어긋나는 상태가 구조적으로 없다.
-- HINCRBY size -1 로 세던 때는 재시도가 두 번 빼서 어긋났다 (Lua 는 롤백이 없다).
--
-- 삭제는 compare-and-delete다. 저장된 requestId가 넘어온 값과 같을 때만 지운다.
-- 재시도로 취소가 두 번 나가면, 늦게 도착한 쪽이 그 사이 새로 만든 요청을 지울 수 있다.
--
-- 티어 있는 모드와 없는 모드가 이 스크립트 한 벌을 같이 쓴다. 다른 것은 칸 키에 티어
-- 접미사가 붙느냐뿐이라, 티어를 보지 않는 모드는 **빈 접미사 하나**로 접어서 같은 루프를 탄다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1]   = qm:user:active-request:{userId}
-- KEYS[2]   = qm:party:{partyId}. 이미 아는 파티 키다.
--             파티가 아직 없을 수 있으므로 자바가 빈 문자열을 넘기면 무시한다
-- KEYS[3]   = qm:gameconfig:LOL:tier   ZSET. 티어 사다리.
--             파티 HASH 의 tierLo/tierHi(순번)를 티어 이름으로 되돌리는 데만 쓴다.
--             티어를 보지 않는 모드에서는 읽지 않는다
-- KEYS[4..] = qm:party:open:LOL:{mode}:{voice}:{purpose}:needs:{keyValue}   ZSET
--
--             **티어 접미사가 없는 상태로 넘어온다.** 배정 스크립트와 같은 규칙으로
--             여기에 ':' 와 티어 이름을 붙여 칸 하나를 가리킨다
--
--                 KEYS[3 + i] .. ':' .. 티어이름   -- i = keyValue 순번
--
--             keyValue 순번은 ARGV[6..] 의 순서다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1]   = userId
-- ARGV[2]   = requestId (저장된 값과 비교할 대상)
-- ARGV[3]   = 내 keyValue
-- ARGV[4]   = uniqueness ("true" | "false").
--             create-or-check-party-*.lua 의 ARGV[5] 와 같은 값이어야 한다
-- ARGV[5]   = 내 티어 이름. 티어를 보지 않는 모드는 'NONE'
-- ARGV[6..] = keyValue 목록. 개수 n = #ARGV - 5
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 취소됨. 파티는 남아 있다   { 1, HKEYS 결과 }
--   2 = 취소됐고 파티 없음         { 2 }
--   0 = 활성 요청이 없다           { 0 }
--  -1 = 저장된 requestId 와 다르다 (늦게 도착한 취소)   { -1 }

local userKey  = KEYS[1]
local partyKey = KEYS[2]
local tierKey  = KEYS[3]

local userId    = ARGV[1]
local requestId = ARGV[2]
local myValue   = ARGV[3]
local unique    = ARGV[4] == 'true'
local myTier    = ARGV[5]

local n = #ARGV - 5             -- keyValue 개수
local tiered = myTier ~= 'NONE'

-- 파티 인원은 세어서 구한다. 저장된 카운터를 쓰지 않는다.
--
-- Lua 는 롤백이 없다. 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기는 남고,
-- 페일오버 뒤 재시도가 같은 명령을 한 번 더 실행한다. 그때
-- HINCRBY 는 두 번 빼져 인원 수가 실제 멤버 수와 어긋나지만,
-- HDEL 과 세기는 몇 번 해도 결과가 같다.
--
-- 접두사 'member:' 는 자바의 LolScriptSupport#memberIds() 와 같은 값이어야 한다.
local function memberCount(key)
    local fields = redis.call('HKEYS', key)
    local count = 0
    for i = 1, #fields do
        if string.sub(fields[i], 1, 7) == 'member:' then
            count = count + 1
        end
    end
    return count
end

-- 1. 활성 요청이 있나 / 내가 아는 그 요청이 맞나
if redis.call('EXISTS', userKey) == 0 then
    return { 0 }
end

-- HGET 은 없는 필드에 Lua false 를 돌려준다. nil 이 아니다.
if redis.call('HGET', userKey, 'requestId') ~= requestId then
    return { -1 }
end

-- 2. 파티에 배정되기 전이면 활성 요청만 지우고 끝.
--    아직 아무 파티에도 안 들어갔으니 되돌릴 색인이 없다
local partyId = redis.call('HGET', userKey, 'partyId')
if partyId == false or partyId == '' then
    redis.call('DEL', userKey)
    return { 2 }
end

-- 3. 이 파티가 올라가 있는(또는 올라가야 할) 칸의 티어 접미사 목록.
--
--    **쓰기보다 먼저 읽는다.** 아래 4번에서 파티를 지울 수 있으므로 그 뒤에는 못 읽는다.
--    그리고 Lua 는 롤백이 없어서, 쓰기를 시작한 뒤에 읽기가 실패하면 멤버만 빠지고
--    색인은 그대로인 상태가 남는다.
--
--    범위는 파티를 만들 때 정해져 tierLo/tierHi 에 **순번**으로 적혀 있다
--    (create-or-check-party-tiered.lua 가 ZRANK + 1 로 적는다). 되돌릴 때 1 을 뺀다.
local suffixes
if tiered then
    local bound = redis.call('HMGET', partyKey, 'tierLo', 'tierHi')
    local lo = tonumber(bound[1])
    local hi = tonumber(bound[2])

    local tiers
    if lo == nil or hi == nil then
        -- 있을 수 없는 상태다 — create 가 파티 HASH 와 함께 적는다. 그래도 비어 있다면
        -- 사다리 전체를 훑는다. 덜 빼면 색인에 유령이 영구히 남지만, 더 빼는 것은 무해하다
        tiers = redis.call('ZRANGE', tierKey, 0, -1)
    else
        tiers = redis.call('ZRANGE', tierKey, lo - 1, hi - 1)
    end

    suffixes = {}
    for k = 1, #tiers do
        suffixes[k] = ':' .. tiers[k]
    end
else
    -- 티어를 보지 않는 모드는 keyValue 하나당 칸이 하나다
    suffixes = { '' }
end

-- 파티를 지우고 나면 못 읽으므로 여기서 같이 읽어 둔다
local score = redis.call('HGET', partyKey, 'createdAt')
if score == false then
    score = '0'
end

-- 4. 파티에서 뺀다.
--    먼저 지우고, 그다음 센다. 순서가 반대면 나를 넣고 세게 된다.
--    세기는 몇 번 해도 같은 값이라 재시도에 안전하다 (머리말 참고)
redis.call('HDEL', partyKey, 'member:' .. userId)
local size = memberCount(partyKey)

if size <= 0 then
    -- 5. 마지막 한 명이었다. 파티를 지우고 이 파티가 올라가 있던 칸에서 전부 뺀다.
    --    빈 파티를 남기면 색인의 유령이 그 자리를 영구히 점거한다.
    --    ZREM 은 없는 멤버에 걸어도 무해하므로 어느 칸에 있었는지 가릴 필요가 없다
    redis.call('DEL', partyKey)
    for i = 1, n do
        for _, suffix in ipairs(suffixes) do
            redis.call('ZREM', KEYS[3 + i] .. suffix, partyId)
        end
    end
    redis.call('DEL', userKey)
    return { 2 }
end

-- 6. 아직 남은 사람이 있다. 내가 비운 자리를 색인에 되돌린다.
--    판단식이 create-or-check-party-*.lua 의 등록 규칙과 짝을 이룬다.
--      중복 금지 모드 — 내가 비운 값 하나만 다시 필요해졌다
--      중복 허용 모드 — 정원이 찼다가 풀렸으므로 전부 다시 받을 수 있다
for i = 1, n do
    if (not unique) or (ARGV[5 + i] == myValue) then
        for _, suffix in ipairs(suffixes) do
            redis.call('ZADD', KEYS[3 + i] .. suffix, score, partyId)
        end
    end
end

-- 7. 활성 요청을 지운다
redis.call('DEL', userKey)
return { 1, redis.call('HKEYS', partyKey) }
