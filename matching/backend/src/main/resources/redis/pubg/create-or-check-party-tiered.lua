-- PUBG 랭크(티어를 보는) 배정의 찾기. 들어갈 후보 파티를 찾거나, 없으면 새로 만들어서 넣는다.
--
-- 티어를 보지 않는 모드(일반전)는 create-or-check-party-untiered.lua 가 맡는다.
-- 찾기와 합류는 두 스크립트로 나뉜다. 후보를 찾으면 멤버 목록만 돌려주고, 자바가 최근 거절 검증(D-45 — 차단은 join 안에서, D-57)을
-- 마친 뒤 join-party-tiered.lua 로 넣는다. 두 호출 사이의 틈은 자바의 후보 풀 락
-- (redisLock/PoolLock.java)이 막는다.
--
-- LoL 과 다른 점: 포지션이 없다. 핵심 조건은 플랫폼(STEAM/KAKAO)이고 한 파티에 같은 플랫폼이
-- 여럿인 것이 정상이라 중복 금지가 없다. 그래서 색인은 (포지션 x 티어) 격자가 아니라
-- **티어 한 줄**이다 — needs 키 하나(KEYS[5])에 티어 접미사만 붙는다.
--
-- 파티가 받아들일 티어 범위는 **파티를 만든 사람 기준으로 여기서 한 번** 정해지고,
-- 그 뒤로 바꾸지 않는다. 합류할 때 다시 계산하지 않는다. 스쿼드 표가 ±5 인 이유가 이것이다 —
-- 만든 사람 위아래로 한 명씩 들어와도 파티 폭(최고-최저)이 10 을 넘지 않는다 (seed/gameconfig.redis).
--
-- 그 범위를 파티 HASH 의 tierLo / tierHi 에 적어 둔다. 정원이 차면 파티가 모든 칸에서 빠지므로,
-- 합류(와 앞으로 생길 PUBG 취소) 스크립트가 어느 칸에서 빼고 되돌릴지는 색인이 아니라 여기서 읽는다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- join-party-tiered.lua 와 같은 배치다. 자바가 한 벌을 만들어 두 스크립트에 넘긴다.
--
-- KEYS[1] = qm:party:{newPartyId}                  HASH. 새로 만들 때만 쓴다
-- KEYS[2] = qm:user:active-request:{userId}        HASH. partyId 를 기록한다
-- KEYS[3] = qm:gameconfig:PUBG:tier-range:{mode}   HASH. 티어별 받아들일 범위 "최저:최고" / SOLO_ONLY
-- KEYS[4] = qm:gameconfig:PUBG:tier                ZSET. 티어 사다리. score = 단계 번호
-- KEYS[5] = qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{플랫폼}   ZSET
--
--           **티어 접미사가 없는 상태로 넘어온다.** 칸 하나를 가리키는 키는 여기서
--           KEYS[5] .. ':' .. 티어이름 으로 만든다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- create-or-check-party-untiered.lua 와 ARGV[1..6] 이 같고, ARGV[7] 에 티어 이름이 더 붙는다.
-- 플랫폼은 ARGV 로 받지 않는다 — 이미 KEYS[5] 이름에 들어 있고 참가자 값으로도 읽는 곳이 없다.
--
-- ARGV[1] = newPartyId. 후보가 없을 때 만들 파티 id
-- ARGV[2] = score (지금 시각 epoch millis). 색인 정렬값이자 createdAt
-- ARGV[3] = targetPartySize
-- ARGV[4] = qm:party: 접두사. 후보 파티 키를 만들 때 쓴다
-- ARGV[5] = userId
-- ARGV[6] = start. 내 칸 색인의 몇 번째 후보를 볼지 (거절 상대이거나 join 이 차단으로 거절하면 자바가 1 씩 올려 다시 부른다)
-- ARGV[7] = 내 티어 이름
--
-- ── 파티 HASH 구조 ───────────────────────────────────────────────────
--   partyId / target / createdAt   메타데이터. 인원 수는 저장하지 않는다 — member: 필드를 센다.
--                                  카운터를 두면 재시도 때 HINCRBY 가 두 번 더해져 어긋난다
--   tierLo / tierHi                받아들이는 티어 **순번** 범위 (ZRANK, 0 부터). 이름이 아니라 숫자다
--   member:{userId} = 'EXIST'      참가자. 값은 자리 채움이고 읽지 않는다 (untiered 판과 같다).
--                                  접두사 'member:' 는 자바 ScriptSupport#memberIds() 와 같아야 한다
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   1 = 새 파티를 만들고 들어감    { 1, newPartyId, 1 }
--   2 = 후보 파티를 찾았다         { 2, HKEYS 결과, partyId }
--       최근 거절 검증(D-45)은 자바가 하므로 멤버를 돌려주고 여기서는 넣지 않는다 (차단은 join 이 본다 — D-57)
--  -1 = 배정할 수 없는 설정        { -1, '', 0 }
--       tier-range 표에 내 티어 줄이 없거나, 그 값이 범위가 아니거나(SOLO_ONLY),
--       사다리에 없는 티어 이름을 가리킨다. 전부 쓰기 전에 걸린다
--  -2 = claim 의 TTL 이 먼저 끝났다 { -2, '', 0 }

local userKey = KEYS[2]

local newPartyId = ARGV[1]
local score      = ARGV[2]
local target     = tonumber(ARGV[3])
local prefix     = ARGV[4]
local userId     = ARGV[5]
local start      = ARGV[6]
local myTier     = ARGV[7]

local memberField = 'member:' .. userId

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다.
-- HSET 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 partyId 하나만 든 반쪽짜리
-- 활성 요청이 되살아난다. 그 상태로는 취소가 game 필드를 못 읽어 터진다
if redis.call('EXISTS', userKey) == 0 then
    return { -2, '', 0 }
end


-- 1. 내 티어 칸에 자리가 남은 파티가 있나
local myIndexKey = KEYS[5] .. ':' .. myTier
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
    -- 앞단(PubgConditionValidator)이 이미 거르지만, 설정이 그 사이 바뀌었을 수 있다
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
            memberField, 'EXIST')
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 내가 받아들일 티어 칸 전부에 등록한다. 포지션이 없어 줄은 하나(KEYS[5])다.
    -- 중복 금지가 없으므로 한 명 들어왔다고 빼지 않는다 — 같은 플랫폼이 더 들어와야 한다.
    if target > 1 then
        for _, member in ipairs(range) do
            redis.call('ZADD', KEYS[5] .. ':' .. member, score, newPartyId)
        end
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티 인원들을 반환한다. 넣는 것은 최근 거절 검증 뒤 자바가 join 으로 한다(차단은 join 안에서 — D-57).
local partyId  = found[1]
local partyKey = prefix .. partyId

return { 2, redis.call('HKEYS', partyKey), partyId }
