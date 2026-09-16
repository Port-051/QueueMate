-- VALORANT 티어를 보지 않는 배정(일반전). 들어갈 파티를 찾거나, 없으면 만들어서 넣는다.
--
-- 티어를 보는 모드(경쟁전)는 create-or-check-party-tiered.lua 가 맡는다.
--
-- 색인이 역할군 한 줄뿐이라는 것만 다르다. 티어 접미사가 없으므로 칸을 조립할 일도,
-- 파티 범위를 좁힐 일도 없다. 그래서 티어 판이 쓰는 밑동 키(KEYS[5])도 받지 않는다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- join-party.lua 와 같은 배치다. 자바가 한 벌을 만들어 두 스크립트에 넘긴다.
--
-- KEYS[1]   = qm:party:{newPartyId}              HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}    HASH. partyId 를 기록한다
-- KEYS[3..] = qm:party:open:VALORANT:{mode}:{voice}:{purpose}:needs:{역할군}   ZSET
--             역할군 순번은 ARGV[9..] 의 순서다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1]   = newPartyId. 후보가 없을 때 만들 파티 id
-- ARGV[2]   = score (지금 시각 epoch millis). 색인 정렬값이자 createdAt
-- ARGV[3]   = 내 keyValue (역할군)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 발로란트는 늘 "true" 다
-- ARGV[6]   = qm:party: 접두사. 후보 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = start. 색인의 몇 번째 후보부터 볼지
-- ARGV[9..] = 역할군 목록. 개수 n = #ARGV - 8
--
-- ── 곁딸린 키 ────────────────────────────────────────────────────────
--   qm:party:needs-roles:{partyId}  SET. 아직 비어 있는 역할군.
--   티어 판에서는 범위를 좁힐 때 "어느 칸을 다시 만들지"가 이 목록이라 꼭 필요하고,
--   여기서는 쓸 일이 없다. 그래도 **같이 만든다** — leave-party.lua 를 티어 유/무 한 벌로
--   쓰기 때문이다. 없으면 취소할 때의 SADD 가 아무도 안 지우는 키를 새로 만든다
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 새 파티를 만들고 들어감    { 1, newPartyId, 1 }
--   2 = 후보 파티를 찾았다         { 2, HKEYS 결과, partyId }
--  -1 = 내 역할군이 목록에 없다    { -1, '', 0 }
--  -2 = claim 의 TTL 이 먼저 끝났다 { -2, '', 0 }

local userKey = KEYS[2]

local newPartyId = ARGV[1]
local score      = ARGV[2]
local myValue    = ARGV[3]
local target     = tonumber(ARGV[4])
local unique     = ARGV[5] == 'true'
local prefix     = ARGV[6]
local userId     = ARGV[7]
local start      = ARGV[8]

local memberField = 'member:' .. userId

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다.
-- HSET 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 partyId 하나만 든 반쪽짜리
-- 활성 요청이 되살아난다. 그 상태로는 취소가 game 필드를 못 읽어 터진다
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

-- 1. 내 역할군 자리가 비어 있는 파티가 있나
local found = redis.call('ZRANGE', myIndexKey, start, start)

if #found == 0 then
    -- 2. 없으니 새로 만든다.
    --    tierLo/tierHi 는 0 으로 채워 둔다. 티어를 보지 않는 모드는 범위가 한 칸이고,
    --    leave-party.lua 가 티어 유무를 값이 아니라 넘어온 티어 이름('NONE')으로 가른다
    redis.call('HSET', KEYS[1],
            'partyId', newPartyId,
            'target', target,
            'createdAt', score,
            'tierLo', 0,
            'tierHi', 0,
            memberField, myValue)
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 아직 필요한 역할군의 색인에 등록하고, 같은 목록을 needs-roles SET 에도 남긴다
    if target > 1 then
        for i = 1, n do
            local role = ARGV[8 + i]
            if (not unique) or (role ~= myValue) then
                redis.call('ZADD', KEYS[2 + i], score, newPartyId)
                redis.call('SADD', 'qm:party:needs-roles:' .. newPartyId, role)
            end
        end
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티 인원들을 반환한다. 넣는 것은 차단 검증 뒤 자바가 join 으로 한다
local partyId  = found[1]
local partyKey = prefix .. partyId

return { 2, redis.call('HKEYS', partyKey), partyId }
