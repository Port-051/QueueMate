-- VALORANT 티어를 보는 배정(경쟁전). 들어갈 파티를 찾거나, 없으면 만들어서 넣는다.
--
-- 티어를 보지 않는 모드(일반전)는 create-or-check-party-untiered.lua 가 맡는다.
--
-- 색인은 (역할군 x 티어) 격자다. 역할군은 4개(DUELIST/INITIATOR/CONTROLLER/SENTINEL)이고
-- 한 파티 안에서 **겹치지 않는다**(LoL 포지션과 같은 규칙). 티어는 UNRANKED 를 뺀 25칸이다.
--
-- ── 발로란트 티어 규칙 ───────────────────────────────────────────────
-- 게임 규칙은 **파티 최고 티어 <= 한계(파티 최저 티어)** 하나다. tier-range 표의 한 줄은
-- "그 티어와 둘이 같이 갈 수 있는 구간"이라, 셋이 되면 줄 하나만으로는 모자라다.
-- 그래서 파티 HASH 에 지금까지 들어온 사람의 최저/최고 순번(minTier / maxTier)을 같이 적고,
--
-- 합류할 때마다 join 이 파티 범위를 **지금 범위와 들어온 사람 줄의 교집합**으로 좁힌다
-- (join-party-tiered.lua). 그래서 색인의 칸은 늘 "지금 이 파티에 들어올 수 있는 티어"와 같고,
-- 여기서는 내 칸에서 나온 후보를 그대로 돌려주면 된다.
--
-- 듀오(정원 2)는 합류 즉시 정원이 차서 좁히는 자리가 없고, 방장 줄 그대로면 맞다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- join-party-tiered.lua 와 같은 배치다. 자바가 한 벌을 만들어 두 스크립트에 넘긴다.
--
-- KEYS[1]   = qm:party:{newPartyId}              HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}    HASH. partyId 를 기록한다
-- KEYS[3]   = qm:gameconfig:VALORANT:tier-range:{mode}   HASH. 티어 -> "최저:최고" 또는 SOLO_ONLY
-- KEYS[4]   = qm:gameconfig:VALORANT:tier                ZSET. 티어 사다리. score = 단계 번호
-- KEYS[5]   = qm:party:open:VALORANT:{mode}:{voice}:{purpose}:needs   역할군도 티어도 없는 **밑동**
--
--             칸 키를 이름으로 조립할 때 쓴다 (join 이 needs-roles SET 에서 받은 역할군
--             이름으로 칸을 다시 만들 때 필요하다)
--
--                 KEYS[5] .. ':' .. 역할군 .. ':' .. 티어이름
--
-- KEYS[6..] = 위 밑동에 역할군이 붙은 키. ZSET. **티어 접미사는 없다**
--
--                 KEYS[5 + p] .. ':' .. 티어이름   -- p = 역할군 순번
--
--             역할군 순번은 ARGV[10..] 의 순서다.
--             격자를 통째로 KEYS 로 받지 않는 이유는 칸이 (역할군 4 x 티어 25) = 100 개라
--             호출마다 그만큼을 넘겨야 하기 때문이다. 이렇게 조립하면 KEYS 는
--             5 + 역할군 개수로 고정된다.
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- join-party-tiered.lua 와 같은 배치다.
--
-- ARGV[1]   = newPartyId. 후보가 없을 때 만들 파티 id
-- ARGV[2]   = score (지금 시각 epoch millis). 색인 정렬값이자 createdAt
-- ARGV[3]   = 내 keyValue (역할군)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 발로란트는 늘 "true" 다
-- ARGV[6]   = qm:party: 접두사. 후보 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = start. 색인의 몇 번째 후보부터 볼지 (안 맞으면 자바가 1 씩 올려 다시 부른다)
-- ARGV[9]   = 내 티어 이름
-- ARGV[10..]= 역할군 목록. 개수 P = #ARGV - 9
--
-- ── 파티 HASH 구조 ───────────────────────────────────────────────────
--   partyId / target / createdAt   메타데이터. 인원 수는 저장하지 않는다 — member: 필드를 센다.
--                                  카운터를 두면 재시도 때 HINCRBY 가 두 번 더해져 어긋난다
--   tierLo / tierHi                지금 이 파티가 받아들이는 티어 **순번**(ZRANK, 0 부터) 범위.
--                                  join 이 좁힐 수 있다
--   minTier / maxTier              지금까지 들어온 사람의 최저/최고 티어 순번(ZRANK, 0 부터).
--                                  join 이 갱신하고, leave 는 남은 사람들의 tier: 필드로
--                                  다시 계산해 덮어쓴다
--   member:{userId} = 역할군       참가자. 접두사 'member:' 는 자바 ScriptSupport#memberIds() 와 같아야 한다
--   tier:{userId} = 티어 이름      그 참가자가 들고 온 티어. leave 가 취소된 사람을 뺀 **남은 사람들**의
--                                  줄로 범위를 다시 구하려면 각자의 티어를 알아야 하는데, minTier/maxTier
--                                  두 값만으로는 누가 나갔는지 구분할 수 없다.
--                                  접두사가 'member:' 로 시작하면 안 된다 — memberCount 와 자바
--                                  ScriptSupport#memberIds() 가 인원을 잘못 센다
--
-- ── 곁딸린 키 ────────────────────────────────────────────────────────
--   qm:party:needs-roles:{partyId}  SET. 아직 비어 있는 역할군. join 이 범위를 좁혀
--                                   칸을 다시 만들 때 이 목록만 등록한다.
--                                   합류 때 SREM, 정원이 차면 DEL (join-party-tiered.lua)
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 새 파티를 만들고 들어감    { 1, newPartyId, 1 }
--   2 = 후보 파티를 찾았다         { 2, HKEYS 결과, partyId }
--       최근 거절 검증(D-45)은 자바가 하므로 멤버를 돌려주고 여기서는 넣지 않는다 (차단은 join 이 본다 — D-57)
--  -1 = 배정할 수 없는 설정        { -1, '', 0 }
--       내 역할군이 목록에 없거나, tier-range 표에 내 티어 줄이 없거나, 그 값이 범위가
--       아니거나(SOLO_ONLY), 사다리에 없는 티어이거나, 후보 파티에 minTier/maxTier 가 없다
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
local myTier     = ARGV[9]

local memberField = 'member:' .. userId

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

-- 1. 내 포지션 x 내 티어 자리가 비어 있는 파티가 있나
local myIndexKey = KEYS[5 + myPos] .. ':' .. myTier
local found = redis.call('ZRANGE', myIndexKey, start, start)
local myTierRank = redis.call('ZRANK', KEYS[4], myTier)

local tierRange = redis.call('HGET', KEYS[3], myTier)
if tierRange == false then
    return { -1, '', 0 }
end
local minMaxTier = {}
for tier in string.gmatch(tierRange, "([^:]+)") do
    table.insert(minMaxTier, tier)
end
-- 'SOLO_ONLY' 처럼 범위가 아닌 값과 깨진 값이 여기서 같이 걸린다.
-- 앞단(LolConditionValidator)이 이미 거르지만, 설정이 그 사이 바뀌었을 수 있다
if #minMaxTier ~= 2 then
    return { -1, '', 0 }
end

-- 티어 이름 -> 사다리 순번. ZRANK 를 그대로(0부터) 적는다 —
-- join/leave 가 tierLo/tierHi 를 ZRANGE 에 그대로 넘겨 칸 이름을 되돌리기 때문이다
local loRank = redis.call('ZRANK', KEYS[4], minMaxTier[1])
local hiRank = redis.call('ZRANK', KEYS[4], minMaxTier[2])

if #found == 0 then
    -- 2. 없으니 새로 만든다. 이 파티가 받아들일 티어 범위는 지금 정해지고 끝이다.
    --
    --    **읽기와 환산을 먼저 끝내고 쓰기는 그 뒤에 몰아서 한다.** Lua 는 롤백이 없다 —
    --    쓰기를 시작한 뒤에 실패하면 파티 HASH 만 만들어지고 색인에는 한 칸도 안 올라간
    --    상태가 남는다. 그러면 그 파티는 아무도 못 찾고 그 사용자는 영영 매칭되지 않는다.
    if loRank == false or hiRank == false or myTierRank == false then
        return { -1, '', 0 }
    end
    local range = redis.call('ZRANGE', KEYS[4], loRank, hiRank)

    redis.call('HSET', KEYS[1],
            'partyId', newPartyId,
            'target', target,
            'createdAt', score,
            'tierLo', loRank,
            'tierHi', hiRank,
            'minTier', myTierRank,
            'maxTier', myTierRank,
            memberField, myValue,
            -- 멤버별 티어. leave 가 남은 사람들의 줄로 범위를 되돌릴 때 읽는다 (머리말 참고)
            'tier:' .. userId, myTier)
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 아직 필요한 역할군 x 내가 받아들일 티어 전부에 등록한다.
    -- 내가 찬 역할군 줄은 빼고, 그 목록을 needs-roles SET 에도 남긴다 —
    -- 나중에 join 이 범위를 좁혀 칸을 다시 만들 때 이 목록만 등록한다.
    if target > 1 then
        for p = 1, P do
            if (not unique) or (ARGV[9 + p] ~= myValue) then
                redis.call('SADD', 'qm:party:needs-roles:' .. newPartyId, ARGV[9 + p])
                for _, member in ipairs(range) do
                    redis.call('ZADD', KEYS[5 + p] .. ':' .. member, score, newPartyId)
                end
            end
        end
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티 인원들을 반환한다. 넣는 것은 최근 거절 검증 뒤 자바가 join 으로 한다(차단은 join 안에서 — D-57).
local partyId  = found[1]
local partyKey = prefix .. partyId

-- 이 후보가 나와 맞는지 따로 보지 않는다. 색인의 칸은 join 이 합류마다 파티 범위를
-- 교집합으로 좁혀 유지하므로, 내 칸에서 나왔다는 것이 곧 파티 전원과 같이 갈 수 있다는 뜻이다
-- (join-party-tiered.lua 머리말)

return { 2, redis.call('HKEYS', partyKey), partyId }
