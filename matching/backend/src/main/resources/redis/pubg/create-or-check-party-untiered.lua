-- PUBG 의 티어를 보지 않는 배정. 들어갈 후보 파티를 찾고, 없으면 새로 만들어서 넣는다.
--
-- 티어를 보는 모드는 이 스크립트가 맡지 않는다. 별도 스크립트
-- create-or-check-party-tiered.lua 가 담당한다 (합류는 join-party-tiered.lua).
--
-- 기존 파티에 넣는 일은 이 스크립트가 하지 않는다. 후보를 찾으면 멤버 목록만 돌려주고,
-- 자바가 차단 검증을 마친 뒤 join-party.lua 가 넣는다. 찾기와 넣기 사이의 틈은
-- Lua 가 아니라 자바의 후보 풀 락(redisLock/PoolLock.java)이 막는다.
--
-- LoL 과 다른 점: PUBG 의 핵심 조건은 플랫폼(STEAM / KAKAO)이고 포지션이 없다.
-- 한 파티에 같은 플랫폼이 여럿인 것이 정상이라 중복 금지(uniqueness) 개념이 없다.
-- 그래서 needs 색인은 내 플랫폼의 KEYS[3] 한 칸뿐이고, keyValue 목록도 인자로 받지 않는다.
--
-- needs 색인에는 정원이 안 찬 파티만 있다 — 여기서 만들 때 넣고, 정원이 차는 순간
-- join-party.lua 가 같은 실행 안에서 뺀다. 중복 금지가 없으니 색인에 있는 파티는
-- 누구든 받을 수 있다.
--
-- KEYS[1]   = qm:party:{newPartyId}                  HASH. 새로 만들 때만 쓴다
-- KEYS[2]   = qm:user:active-request:{userId}        HASH. partyId를 기록한다
-- KEYS[3]   = qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{플랫폼}  ZSET. 내 플랫폼 색인
--
-- ARGV[1]   = newPartyId (새로 만들 경우에 쓸 id)
-- ARGV[2]   = score (지금 시각 epoch millis)
-- ARGV[3]   = targetPartySize
-- ARGV[4]   = qm:party: 접두사. 후보 파티 키를 만들 때 쓴다
-- ARGV[5]   = userId
-- ARGV[6]   = start. 색인의 몇 번째 후보를 볼지 (0부터)
--
-- 파티 HASH 구조
--   partyId / target / createdAt          메타데이터. 인원 수는 저장하지 않는다 —
--                                         member: 필드를 세는 것이 인원이다.
--                                         카운터를 두면 재시도 때 HINCRBY 가 두 번
--                                         더해져 실제 멤버 수와 어긋난다
--   tierLo / tierHi                       받아들이는 티어 순번 범위. 이 모드는 티어를
--                                         보지 않으므로 항상 1:1 이다 (T=1 인 격자).
--                                         지금 이 값을 읽는 PUBG 스크립트는 없다 —
--                                         PUBG 취소 스크립트(leave-party.lua)가 아직 없다.
--                                         tiered 판과 모양을 맞춰 두는 것이다
--   member:{userId} = 'EXIST'             참가자. 값은 자리 채움이고 읽지 않는다 —
--                                         자바 ScriptSupport#memberIds() 는 필드 이름만 본다.
--                                         join-party.lua 와 같은 리터럴을 쓴다
--
-- 반환
--   { 1, newPartyId, 1 }       = 후보가 없어 새 파티를 만들고 들어감
--   { 2, HKEYS 결과, partyId } = start 번째 후보를 찾았다. 차단 검증을 위해 그 파티 HASH 의
--                                필드 이름 전부를 돌려준다 (member: 거르기는 자바가 한다).
--                                이 경우 아무것도 쓰지 않는다
--   { -2, '', 0 }              = claim 의 TTL 이 먼저 끝나 활성 요청이 사라졌다
--   -1 은 돌려주지 않는다. 검사할 keyValue 목록을 받지 않기 때문이다

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

-- 1. 내 플랫폼 색인의 start 번째 파티가 있나
local found = redis.call('ZRANGE', myIndexKey, start, start)
if #found == 0 then
    -- 2. 없으니 새로 만든다
    -- 티어를 보지 않는 모드는 범위가 1칸이다. tiered 판과 같은 모양이 되도록 자리를 맞춰 둔다.
    redis.call('HSET', KEYS[1],
            'partyId', newPartyId,
            'target', target,
            'createdAt', score,
            'tierLo', 0,
            'tierHi', 0,
            memberField, 'EXIST')
    redis.call('HSET', userKey, 'partyId', newPartyId)
    -- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
    redis.call('PERSIST', userKey)

    -- 자리가 남으면 내 플랫폼 색인에 등록한다.
    -- 중복 금지가 없으므로 내가 들어갔다고 뺄 값은 없다. 1인 모드는 이미 찼으니 넣지 않는다.
    if target > 1 then
        redis.call('ZADD', myIndexKey, score, newPartyId)
    end

    return { 1, newPartyId, 1 }
end

-- 3. 있으니 그 파티의 필드 이름을 반환한다. 합류는 자바의 차단 검증 뒤 join-party.lua 가 한다.
local partyId  = found[1]
local partyKey = prefix .. partyId


local keys = redis.call('HKEYS', partyKey)

return {2, keys, partyId}

