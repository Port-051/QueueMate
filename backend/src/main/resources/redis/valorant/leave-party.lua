-- VALORANT 매칭 요청을 취소하고 파티에서 빠진다.
--
-- 빼기 / 인원 감소 / 색인 되돌리기 / 빈 역할군 되돌리기 / 파티 범위 되돌리기 / 빈 파티 삭제 /
-- 활성 요청 삭제가 한 덩어리여야 한다. 자바에서 나눠 하다 중간에 죽으면 이런 게 남는다.
--   · 파티에선 빠졌는데 색인에 안 돌아감 → 그 자리에 아무도 못 들어온다
--   · 활성 요청은 지웠는데 파티에 member: 필드가 남음 → 유령 인원이 자리를 먹는다
--   · needs-roles 에만 돌아가고 색인 칸에는 안 올라감 → 다음 사람이 그 파티를 못 찾는다
--
-- 인원 수는 따로 저장하지 않고 member: 필드를 세어서 구하므로, 필드를 지우는 것과
-- 인원이 줄어드는 것이 같은 일이다 — 둘이 어긋나는 상태가 구조적으로 없다.
-- HINCRBY size -1 로 세던 때는 재시도가 두 번 빼서 어긋났다 (Lua 는 롤백이 없다).
--
-- 삭제는 compare-and-delete다. 저장된 requestId가 넘어온 값과 같을 때만 지운다.
-- 재시도로 취소가 두 번 나가면, 늦게 도착한 쪽이 그 사이 새로 만든 요청을 지울 수 있다.
--
-- 티어 있는 모드(경쟁전)와 없는 모드(일반전)가 이 스크립트 한 벌을 같이 쓴다. LoL 판과 같은
-- 방식이다 — 티어를 보지 않는 모드는 칸 접미사를 **빈 문자열 하나**로 접어서 같은 루프를 탄다.
--
-- ── 발로란트 몫: 파티 범위를 되돌린다 ────────────────────────────────
-- 경쟁전 파티의 범위(tierLo/tierHi)는 합류할 때마다 join-party-tiered.lua 가
-- "지금 범위 ∩ 들어온 사람 줄"로 **좁혀** 둔 것이다. 좁힌 사람이 나가면 그 몫을 되돌려야 한다 —
-- 안 그러면 골드가 만든 파티에 실버가 들어왔다 나간 뒤에도 파티가 실버 언저리에 갇힌다.
--
-- 되돌리는 방법은 "내가 좁힌 만큼을 빼기"가 아니라 **남은 사람들로 다시 구하기**다.
-- 교집합은 뺄 수 없다(A ∩ B 에서 B 를 빼도 A 가 나오지 않는다). 그래서 파티 HASH 에
-- 멤버별 티어(`tier:{userId}`)를 남겨 두고, 남은 사람들의 줄을 처음부터 다시 교집합한다.
--
-- **표를 못 읽으면 되돌리기만 포기하고 취소 자체는 성공시킨다.** 설정이 그 사이 바뀌어
-- 줄이 사라졌다고 취소를 실패시키면 그 사용자가 큐에 갇힌다. 범위가 좁은 채로 남는 것은
-- 매칭이 늦어질 뿐이고, 갇히는 것은 되돌릴 방법이 없다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1]   = qm:user:active-request:{userId}
-- KEYS[2]   = qm:party:{partyId}. 이미 아는 파티 키다.
--             파티가 아직 없을 수 있으므로 자바가 빈 문자열을 넘기면 무시한다
-- KEYS[3]   = qm:gameconfig:VALORANT:tier-range:{modeKey}   HASH. 티어 -> "최저:최고" 또는 SOLO_ONLY.
--             남은 사람들의 줄을 다시 교집합하는 데만 쓴다. 티어를 보지 않는 모드에서는 읽지 않는다
-- KEYS[4]   = qm:gameconfig:VALORANT:tier                   ZSET. 티어 사다리. 순번 <-> 이름.
--             티어를 보지 않는 모드에서는 읽지 않는다
-- KEYS[5]   = qm:party:open:VALORANT:{mode}:{voice}:{purpose}:needs   역할군도 티어도 없는 **밑동**
--
--             되돌릴 칸을 needs-roles SET 의 역할군 **이름**으로 조립할 때 쓴다
--             (join-party-tiered.lua 와 같은 방식)
--
--                 KEYS[5] .. ':' .. 역할군 .. 티어접미사
--
-- KEYS[6..] = 위 밑동에 역할군이 붙은 키. ZSET. **티어 접미사는 없다**
--
--                 KEYS[5 + i] .. 티어접미사   -- i = 역할군 순번
--
--             역할군 순번은 ARGV[6..] 의 순서다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1]   = userId
-- ARGV[2]   = requestId (저장된 값과 비교할 대상)
-- ARGV[3]   = 내 keyValue (역할군)
-- ARGV[4]   = uniqueness ("true" | "false"). 발로란트는 늘 "true" 다
-- ARGV[5]   = 내 티어 이름. 티어를 보지 않는 모드는 'NONE'
-- ARGV[6..] = 역할군 목록. 개수 n = #ARGV - 5
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 취소됨. 파티는 남아 있다   { 1, HKEYS 결과 }
--       HKEYS 는 HDEL **뒤**라 나는 빠져 있다 — 자바가 그 목록을 알림 수신자로 쓴다
--   2 = 취소됐고 파티 없음         { 2 }
--   0 = 활성 요청이 없다           { 0 }
--  -1 = 저장된 requestId 와 다르다 (늦게 도착한 취소)   { -1 }

local userKey     = KEYS[1]
local partyKey    = KEYS[2]
local tierTableKey = KEYS[3]
local ladderKey   = KEYS[4]
local baseKey     = KEYS[5]

local userId    = ARGV[1]
local requestId = ARGV[2]
local myValue   = ARGV[3]
local unique    = ARGV[4] == 'true'
local myTier    = ARGV[5]

local n = #ARGV - 5             -- 역할군 개수
local tiered = myTier ~= 'NONE'

local needsRolesKey = nil       -- partyId 를 읽은 뒤에 정해진다

-- 파티 인원은 세어서 구한다. 저장된 카운터를 쓰지 않는다.
--
-- Lua 는 롤백이 없다. 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기는 남고,
-- 페일오버 뒤 재시도가 같은 명령을 한 번 더 실행한다. 그때
-- HINCRBY 는 두 번 빼져 인원 수가 실제 멤버 수와 어긋나지만,
-- HDEL 과 세기는 몇 번 해도 결과가 같다.
--
-- 접두사 'member:' 는 자바의 ScriptSupport#memberIds() 와 같은 값이어야 한다.
-- 'tier:{userId}' 필드가 여기 걸리지 않는 이유도 같다 — 접두사가 다르다
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

-- 사다리 순번 구간 -> 칸 접미사 목록. 티어를 보지 않는 모드는 접미사가 빈 문자열 하나다
local function suffixesOf(tierNames)
    local out = {}
    for i = 1, #tierNames do
        out[i] = ':' .. tierNames[i]
    end
    return out
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
--    아직 아무 파티에도 안 들어갔으니 되돌릴 색인도, 되돌릴 범위도 없다
local partyId = redis.call('HGET', userKey, 'partyId')
if partyId == false or partyId == '' then
    redis.call('DEL', userKey)
    return { 2 }
end
local needsRolesKey = 'qm:party:needs-roles:' .. partyId

-- 3. **쓰기보다 먼저 필요한 것을 전부 읽는다.** Lua 는 롤백이 없다 —
--    쓰기를 시작한 뒤에 읽기가 실패하면 멤버만 빠지고 색인은 그대로인 반쪽 상태가 남는다.
--    아래 4번에서 파티를 통째로 지울 수도 있어서 그 뒤에는 읽을 수도 없다.
--
--    HGETALL 한 번으로 메타데이터(tierLo/tierHi/createdAt)와 멤버별 티어를 같이 가져온다.
--    필드마다 HGET 을 부르면 왕복은 같지만(스크립트 안이라) 파티 구조를 아는 자리가 늘어난다.
local raw = redis.call('HGETALL', partyKey)
local lo, hi
local createdAt = '0'
local otherTiers = {}           -- 나를 뺀 남은 멤버들의 티어 이름
for i = 1, #raw, 2 do
    local field = raw[i]
    local value = raw[i + 1]
    if field == 'createdAt' then
        createdAt = value
    elseif field == 'tierLo' then
        lo = tonumber(value)
    elseif field == 'tierHi' then
        hi = tonumber(value)
    elseif string.sub(field, 1, 5) == 'tier:' then
        -- 'tierLo' / 'tierHi' 는 여섯 번째 글자가 ':' 가 아니라 여기 걸리지 않는다
        if string.sub(field, 6) ~= userId then
            table.insert(otherTiers, value)
        end
    end
end

-- 3-1. 이 파티가 지금 올라가 있는(또는 올라가야 할) 칸의 티어 접미사 목록.
--      파티에 적힌 **지금 범위**로 빼야 실제로 올라가 있는 칸과 어긋나지 않는다
local oldSuffixes
if tiered then
    local tiers
    if lo == nil or hi == nil then
        -- 있을 수 없는 상태다 — create 가 파티 HASH 와 함께 적는다. 그래도 비어 있다면
        -- 사다리 전체를 훑는다. 덜 빼면 색인에 유령이 영구히 남지만, 더 빼는 것은 무해하다
        tiers = redis.call('ZRANGE', ladderKey, 0, -1)
    else
        tiers = redis.call('ZRANGE', ladderKey, lo, hi)
    end
    oldSuffixes = suffixesOf(tiers)
else
    -- 티어를 보지 않는 모드는 역할군 하나당 칸이 하나다
    oldSuffixes = { '' }
end

-- 3-2. 남은 사람들로 파티 범위를 다시 구한다 (머리말 참고).
--      남은 사람이 없으면(내가 마지막) 아래 4번에서 파티가 통째로 사라지므로 계산하지 않는다.
--      멤버 루프는 최대 target - 1 회(발로란트는 2)라 후보를 훑는 루프와 다르다 —
--      CLAUDE.md 가 금지하는 것은 색인 후보 순회다
local newLo, newHi            -- 사다리 순번. 전부 교집합한 결과
local memberLo, memberHi      -- 남은 사람들의 최저/최고 순번. minTier/maxTier 로 다시 적는다
local rangeOk = tiered and #otherTiers > 0
local ranksOk = rangeOk
if rangeOk then
    for _, tierName in ipairs(otherTiers) do
        local rank = redis.call('ZRANK', ladderKey, tierName)
        if rank == false then
            -- 사다리에 없는 티어다. 사다리가 바뀌었다는 뜻이라 되돌리기를 포기한다
            rangeOk = false
            ranksOk = false
        else
            if memberLo == nil or rank < memberLo then memberLo = rank end
            if memberHi == nil or rank > memberHi then memberHi = rank end

            local row = redis.call('HGET', tierTableKey, tierName)
            if row == false then
                rangeOk = false     -- 표에 그 티어 줄이 없다
            else
                local bounds = {}
                for part in string.gmatch(row, "([^:]+)") do
                    table.insert(bounds, part)
                end
                if #bounds ~= 2 then
                    rangeOk = false -- 'SOLO_ONLY' 처럼 범위가 아닌 값과 깨진 값이 같이 걸린다
                else
                    local rowLo = redis.call('ZRANK', ladderKey, bounds[1])
                    local rowHi = redis.call('ZRANK', ladderKey, bounds[2])
                    if rowLo == false or rowHi == false then
                        rangeOk = false
                    else
                        -- 교집합: 아래쪽은 가장 높은 하한, 위쪽은 가장 낮은 상한
                        if newLo == nil or rowLo > newLo then newLo = rowLo end
                        if newHi == nil or rowHi < newHi then newHi = rowHi end
                    end
                end
            end
        end
    end
    if newLo == nil or newHi == nil or newLo > newHi then
        -- 교집합이 비었다. 남은 사람들끼리 이미 같이 못 가는 상태라 되돌릴 범위가 없다
        rangeOk = false
    end
end

-- 3-3. 범위가 달라졌으면 새 칸 목록도 미리 읽어 둔다.
--      멤버를 빼면 교집합은 넓어지거나 그대로다(제약이 하나 줄었으므로).
--      그래도 `~=` 로 보는 것은 설정이 그 사이 바뀌어 좁아진 경우에도 옛 칸을 남기지 않기 위해서다
local rangeChanged = rangeOk and (lo == nil or hi == nil or newLo ~= lo or newHi ~= hi)
local newSuffixes = oldSuffixes
if rangeChanged then
    newSuffixes = suffixesOf(redis.call('ZRANGE', ladderKey, newLo, newHi))
end

-- 4. 여기서부터 쓰기다. 위에서 읽기가 전부 끝났다.
--    먼저 지우고, 그다음 센다. 순서가 반대면 나를 넣고 세게 된다.
--    세기는 몇 번 해도 같은 값이라 재시도에 안전하다 (머리말 참고)
redis.call('HDEL', partyKey, 'member:' .. userId, 'tier:' .. userId)
local size = memberCount(partyKey)

if size <= 0 then
    -- 5. 마지막 한 명이었다. 파티를 지우고 이 파티가 올라가 있던 칸에서 전부 뺀다.
    --    빈 파티를 남기면 색인의 유령이 그 자리를 영구히 점거한다.
    --    ZREM 은 없는 멤버에 걸어도 무해하므로 어느 칸에 있었는지 가릴 필요가 없다
    redis.call('DEL', partyKey)
    for i = 1, n do
        for _, suffix in ipairs(oldSuffixes) do
            redis.call('ZREM', KEYS[5 + i] .. suffix, partyId)
        end
    end
    -- TTL 이 없는 키다. 파티와 같이 지우지 않으면 영원히 남는다
    redis.call('DEL', needsRolesKey)
    redis.call('DEL', userKey)
    return { 2 }
end

-- 6. 아직 남은 사람이 있다. 내 역할군 자리가 다시 비었다.
--    **색인보다 needs-roles 를 먼저 채운다** — 아래에서 다시 올릴 칸의 목록이 이 SET 이다
redis.call('SADD', needsRolesKey, myValue)

-- 되살릴 역할군 줄. 중복 금지 모드(발로란트는 늘 이쪽)에서는 needs-roles SET 이
-- "아직 빈 역할군"을 정확히 알고 있으므로 그대로 쓴다. 이미 찬 역할군을 올리면
-- 같은 역할군 두 명이 되는데, 그 판단을 여기서 다시 하지 않아도 된다는 뜻이다.
-- 중복 허용 모드는 빈 역할군이라는 개념이 없어 모든 줄이 다시 열린다 (LoL 판과 같다)
local restoreRoles
if unique then
    restoreRoles = redis.call('SMEMBERS', needsRolesKey)
else
    restoreRoles = {}
    for i = 1, n do
        restoreRoles[i] = ARGV[5 + i]
    end
end

if rangeChanged then
    -- 6-1. 범위가 달라졌다. **옛 범위 칸에서 먼저 전부 뺀 뒤** 새 범위에 올린다.
    --      순서가 반대면 옛 범위에만 있던 칸에 파티가 그대로 남아,
    --      이제는 같이 갈 수 없는 티어의 눈에 후보로 보인다
    for i = 1, n do
        for _, suffix in ipairs(oldSuffixes) do
            redis.call('ZREM', KEYS[5 + i] .. suffix, partyId)
        end
    end
    for _, role in ipairs(restoreRoles) do
        for _, suffix in ipairs(newSuffixes) do
            -- 정렬값은 지금 시각이 아니라 파티의 createdAt 이다. now 를 쓰면 이 파티만
            -- 색인에서 가장 새 것으로 밀려 먼저 기다린 파티보다 늦게 잡힌다
            redis.call('ZADD', baseKey .. ':' .. role .. suffix, createdAt, partyId)
        end
    end
    redis.call('HSET', partyKey, 'tierLo', newLo, 'tierHi', newHi)
else
    -- 6-2. 범위는 그대로고 내가 비운 줄만 다시 필요해졌다.
    --      이미 올라가 있는 칸에 ZADD 를 한 번 더 해도 같은 score 로 덮어쓸 뿐이라 무해하다
    for _, role in ipairs(restoreRoles) do
        for _, suffix in ipairs(oldSuffixes) do
            redis.call('ZADD', baseKey .. ':' .. role .. suffix, createdAt, partyId)
        end
    end
end

-- 6-3. 파티의 최저·최고 티어를 남은 사람 기준으로 다시 적는다.
--      join-party-tiered.lua 가 이 값으로 다음 합류를 판단하므로, 나간 사람의 티어가
--      남아 있으면 그 사람이 없는데도 폭이 그만큼 벌어진 것으로 보인다.
--      티어를 보지 않는 모드는 애초에 없는 필드라 건드리지 않는다
if ranksOk and memberLo ~= nil then
    redis.call('HSET', partyKey, 'minTier', memberLo, 'maxTier', memberHi)
end

-- 7. 활성 요청을 지운다
redis.call('DEL', userKey)
return { 1, redis.call('HKEYS', partyKey) }
