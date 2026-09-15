-- 티어를 보지 않는 배정. 들어갈 파티를 찾아 넣거나, 없으면 만들어서 넣는다.
--
-- 티어를 보는 모드는 이 스크립트가 맡지 않는다. 별도 스크립트
-- join-or-create-party-tiered.lua 가 담당한다 (아직 없다).
--
-- 찾기와 넣기를 나누면 그 사이에 마지막 자리가 차버릴 수 있다.
-- 한 덩어리로 실행해서 그 틈을 없앤다.
--
-- needs 색인은 이 스크립트가 같은 실행 안에서 갱신하므로 항상 정확하다.
-- 즉 needs:{내값}에 있는 파티는 진짜로 내 값을 받을 수 있다. 그래서 재시도 루프가 없다.
--
-- KEYS[1]   = qm:party:{newPartyId}                  HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}        HASH. partyId를 기록한다
-- KEYS[3] = qm:party:open:{조건}:needs  ZSET.
--
-- ARGV[1]   = newPartyId (새로 만들 경우에 쓸 id)
-- ARGV[2]   = score (지금 시각 epoch millis)
-- ARGV[3]   = targetPartySize
-- ARGV[4]   = qm:party: 접두사. 기존 파티 키를 만들 때 쓴다
-- ARGV[5]   = userId
-- ARGV[6]   = start
--
-- 파티 HASH 구조
--   partyId / target / createdAt          메타데이터. 인원 수는 저장하지 않는다 —
--                                         member: 필드를 세는 것이 인원이다.
--                                         카운터를 두면 재시도 때 HINCRBY 가 두 번
--                                         더해져 실제 멤버 수와 어긋난다
--   tierLo / tierHi                       받아들이는 티어 순번 범위. 이 모드는 티어를
--                                         보지 않으므로 항상 1:1 이다 (T=1 인 격자)
--   member:{userId} = keyValue            참가자. userId를 필드로 쓰는 이유는
--                                         칼바람이나 PUBG처럼 같은 keyValue를
--                                         여럿이 가질 수 있기 때문이다
--
-- 반환 {코드, partyId, 현재인원}
--   1 = 새 파티를 만들고 들어감
--   2 = 참여 후보 파티 인원 멤버들을 반환. 차단 검증을 위해 멤버 반환
--  -1 = 설정과 맞지 않는 keyValue

local userKey = KEYS[2]
local myIndexKey = KEYS[3]


local newPartyId = ARGV[1]
local score      = ARGV[2]
local target     = tonumber(ARGV[3])
local prefix     = ARGV[4]
local userId     = ARGV[5]
local start      = ARGV[6]

local memberField = 'member:' .. userId

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다.
-- HSET 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 partyId 하나만 든 반쪽짜리
-- 활성 요청이 되살아난다. 그 상태로는 취소가 game 필드를 못 읽어 터진다
if redis.call('EXISTS', userKey) == 0 then
    return { -2, '', 0 }
end

-- 1. 내 자리가 비어 있는 파티가 있나
local found = redis.call('ZRANGE', myIndexKey, start, start)
if #found == 0 then
    -- 2. 없으니 새로 만든다
    -- 티어를 보지 않는 모드는 범위가 1칸이다. leave-party.lua 가 티어 유무를 구분하지
    -- 않도록(T=1 인 격자로 다루도록) 자리를 맞춰 둔다.
    redis.call('HSET', KEYS[1],
            'partyId', newPartyId,
            'target', target,
            'createdAt', score,
            'tierLo', 1,
            'tierHi', 1,
            memberField, myValue)
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 아직 필요한 값의 색인에 등록한다.
    -- 중복을 금지하는 모드면 내가 찬 값은 뺀다.
    if target > 1 then
        redis.call('ZADD', myIndexKey, score, newPartyId)
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티 인원들을 반환한다.
local partyId  = found[1]
local partyKey = prefix .. partyId


local keys = redis.call('HKEYS', partyKey)

return {2, keys, partyId}

