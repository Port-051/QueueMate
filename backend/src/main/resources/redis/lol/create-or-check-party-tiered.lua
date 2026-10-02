-- 티어를 보는 배정. 들어갈 파티를 찾거나, 없으면 만들어서 넣는다.
--
-- 티어를 보지 않는 모드는 create-or-check-party-untiered.lua 가 맡는다.
--
-- untiered 와 다른 점은 색인이 (포지션 x 티어) 2차원이라는 것뿐이다.
--
-- 파티가 받아들일 티어 범위는 **파티를 만든 사람 기준으로 여기서 한 번** 정해지고,
-- 그 뒤로 바꾸지 않는다. 합류할 때 범위를 다시 계산하지 않는다. 대가로 서로 직접은
-- 안 받을 두 사람(실버와 플래티넘)이 같은 파티가 될 수 있다 — 의도한 것이다.
--
-- 그 범위를 파티 HASH 의 tierLo / tierHi 에 적어 둔다. 정원이 차면 파티가 격자 전체에서
-- 빠지므로, 그 뒤 한 명이 나갈 때 "어느 칸으로 돌아가야 하는지"가 색인에는 남아 있지 않다.
-- leave-party.lua 가 읽을 곳이 여기뿐이다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1]   = qm:party:{newPartyId}              HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}    HASH. partyId 를 기록한다
-- KEYS[3]   = qm:gameconfig:LOL:tier-range:{mode}    HASH. 티어별 매칭 가능한 티어 범위 저장소
-- KEYS[4]   = qm:gameconfig:LOL:tier    ZSET. 롤 티어 정보. 롤에 무슨무슨 티어가 있는 지.
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
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- join-party-tiered.lua 와 같은 배치다. 두 스크립트가 같은 KEYS 격자를 쓰기 때문이다.
--
-- ARGV[1]   = partyId. 새로 만들 경우에 쓸 id
-- ARGV[2]   = score (지금 시각 epoch millis)
-- ARGV[3]   = 내 keyValue (LoL 포지션)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 한 파티 안에서 같은 keyValue 중복 금지 여부
-- ARGV[6]   = qm:party: 접두사. 기존 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = start. 색인의 몇 번째 후보부터 볼지
-- ARGV[9]   = 내 티어 이름
-- ARGV[10..]= 포지션 목록. 개수 P = #ARGV - 9
--
-- ── 파티 HASH 구조 ───────────────────────────────────────────────────
--   partyId / target / createdAt          메타데이터. 인원 수는 저장하지 않는다 —
--                                         member: 필드를 세는 것이 인원이다.
--                                         카운터를 두면 재시도 때 HINCRBY 가 두 번
--                                         더해져 실제 멤버 수와 어긋난다
--   tierLo / tierHi                       이 파티가 받아들이는 티어 **순번**(ZRANK, 0부터) 범위.
--                                         이름이 아니라 숫자다 — leave 가 이름↔순번을
--                                         환산하지 않아도 되게 한다
--   member:{userId} = keyValue            참가자. userId 를 필드로 쓰는 이유는
--                                         칼바람처럼 같은 keyValue 를 여럿이
--                                         가질 수 있기 때문이다
--   untiered 판도 tierLo=tierHi=0 로 같은 모양을 갖는다
--
-- ── 색인 등록 규칙 ───────────────────────────────────────────────────
--   새로 만들 때 : (아직 필요한 포지션) x (tier-range 표가 준 티어 범위) 전부 ZADD
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 새 파티를 만들고 들어감    { 1, newPartyId, 1 }
--   2 = 후보 파티를 찾았다         { 2, HKEYS 결과, partyId }
--       최근 거절 검증(D-45)은 자바가 하므로 멤버를 돌려주고 여기서는 넣지 않는다 (차단은 join 이 본다 — D-57)
--  -1 = 배정할 수 없는 설정        { -1, '', 0 }
--       내 keyValue 가 포지션 목록에 없거나, tier-range 표에 내 티어 줄이 없거나,
--       그 값이 범위가 아니거나(SOLO_ONLY), 사다리에 없는 티어 이름을 가리킨다.
--       전부 "설정이 불완전하면 매칭하지 않는다" 하나로 묶는다 — 쓰기 전에 걸린다
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

-- 1. 내 포지션 x 내 티어 자리가 비어 있는 파티가 있나
local myIndexKey = KEYS[4 + myPos] .. ':' .. myTier
local found = redis.call('ZRANGE', myIndexKey, start, start)

if #found == 0 then
    -- 2. 없으니 새로 만든다. 이 파티가 받아들일 티어 범위는 지금 정해지고 끝이다.
    --
    --    **읽기와 환산을 먼저 끝내고 쓰기는 그 뒤에 몰아서 한다.** Lua 는 롤백이 없다 —
    --    쓰기를 시작한 뒤에 실패하면 파티 HASH 만 만들어지고 색인에는 한 칸도 안 올라간
    --    상태가 남는다. 그러면 그 파티는 아무도 못 찾고 그 사용자는 영영 매칭되지 않는다.
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

    -- 티어 이름 -> 사다리 순번. ZRANK 그대로 적는다(0 부터).
    -- 읽는 쪽(join / leave)이 ZRANGE 에 그대로 넘기므로 더하고 빼는 자리가 없다
    local loRank = redis.call('ZRANK', KEYS[4], minMaxTier[1])
    local hiRank = redis.call('ZRANK', KEYS[4], minMaxTier[2])
    if loRank == false or hiRank == false then
        return { -1, '', 0 }
    end
    local range = redis.call('ZRANGE', KEYS[4], loRank, hiRank)

    redis.call('HSET', KEYS[1],
            'partyId', newPartyId,
            'target', target,
            'createdAt', score,
            'tierLo', loRank,
            'tierHi', hiRank,
            memberField, myValue)
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 아직 필요한 포지션 x 내가 받아들일 티어 전부에 등록한다.
    -- 중복을 금지하는 모드면 내가 찬 포지션 줄은 통째로 뺀다.
    if target > 1 then
        for p = 1, P do
            if (not unique) or (ARGV[9 + p] ~= myValue) then
                for _, member in ipairs(range) do
                    redis.call('ZADD', KEYS[4 + p] .. ':' .. member, score, newPartyId)
                end
            end
        end
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티 인원들을 반환한다. 넣는 것은 최근 거절 검증 뒤 자바가 join 으로 한다(차단은 join 안에서 — D-57).
local partyId  = found[1]
local partyKey = prefix .. partyId

return { 2, redis.call('HKEYS', partyKey), partyId }
