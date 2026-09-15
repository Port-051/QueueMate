-- 티어를 보지 않는 합류. create-or-check-party-untiered.lua 가 찾아 둔 파티에 넣는다.
--
-- 티어를 보는 모드는 이 스크립트가 맡지 않는다. 별도 스크립트
-- join-party-tiered.lua 가 담당한다 (후보 찾기는 create-or-check-party-tiered.lua).
--
-- 찾기와 넣기는 두 스크립트로 나뉘어 있다. 그 사이에 자바의 차단 검증이 끼기 때문이다.
-- 그 틈에 마지막 자리가 차버리는 것은 Lua 가 아니라 자바의 후보 풀 락(redis/PoolLock.java)이 막는다.
--
-- needs 색인은 이 스크립트가 같은 실행 안에서 갱신하므로 항상 정확하다.
-- 즉 needs:{내값}에 있는 파티는 진짜로 내 값을 받을 수 있다. 그래서 재시도 루프가 없다.
--
-- KEYS[1]   = qm:party:{newPartyId}                  안 읽는다 (create 판과 자리를 맞추려고 둔다)
-- KEYS[2]   = qm:user:active-request:{userId}        HASH. partyId를 기록한다
-- KEYS[3..] = qm:party:open:{조건}:needs:{keyValue}  ZSET. ARGV[9..]와 순서가 같다
--
-- ARGV[1]   = partyId. 들어갈 파티 id
-- ARGV[2]   = score (지금 시각 epoch millis)
-- ARGV[3]   = 내 keyValue (게임별 핵심 조건값: LoL 포지션 / VALORANT 역할 / PUBG 플레이 스타일)
-- ARGV[4]   = targetPartySize
-- ARGV[5]   = uniqueness ("true" | "false"). 한 파티 안에서 같은 keyValue 중복 금지 여부.
--             무엇이 이 값을 정하는지는 게임별 CandidateRule이 안다
-- ARGV[6]   = qm:party: 접두사. 기존 파티 키를 만들 때 쓴다
-- ARGV[7]   = userId
-- ARGV[8]   = expiresAt (epoch millis). 정원이 찰 때 만드는 제안의 시한이다.
--             자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다.
--             후보를 찾지 않는 스크립트라 원래 start 자리로 비어 있던 칸을 쓴다 —
--             #ARGV - 8 로 keyValue 개수를 세므로 자리 수는 그대로여야 한다
-- ARGV[9..] = keyValue 목록. KEYS[3..]와 순서가 같다
--
-- 파티 HASH 구조
--   partyId / target / createdAt          메타데이터. 인원 수는 저장하지 않는다 —
--                                         member: 필드를 세는 것이 인원이다
--   member:{userId} = keyValue            참가자. userId를 필드로 쓰는 이유는
--                                         칼바람이나 PUBG처럼 같은 keyValue를
--                                         여럿이 가질 수 있기 때문이다
--   status / expiresAt                    정원이 찰 때 이 스크립트가 만든다.
--                                         제안(proposal)은 곧 파티이므로 별도 레코드를
--                                         두지 않는다 (CLAUDE.md §1). 수락 집계는
--                                         qm:proposal:accepts:{partyId} SET 이 맡는다
--
-- 반환 {코드, partyId, 현재인원}
--   1 = 기존 파티에 들어감 (아직 자리 남음)
--   2 = 기존 파티에 들어갔고 정원이 찼다 (제안을 만들 차례)
--  -1 = 설정과 맞지 않는 keyValue

local userKey = KEYS[2]

local partyId    = ARGV[1]
local score      = ARGV[2]
local myValue    = ARGV[3]
local target     = tonumber(ARGV[4])
local unique     = ARGV[5] == 'true'
local prefix     = ARGV[6]
local userId     = ARGV[7]
local expiresAt  = ARGV[8]

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

local n = #ARGV - 8    -- keyValue 개수. KEYS[2+i] <-> ARGV[8+i]

-- 내 keyValue의 색인이 KEYS 중 몇 번째인지 찾는다
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

-- 1. 그 파티에 들어간다
local partyKey = prefix .. partyId

-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, myValue)
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

-- 중복을 금지하는 모드면 내 값은 이제 찼으므로 그 색인에서 뺀다.
-- 중복을 허용하는 모드(칼바람, PUBG)는 아직 같은 값을 더 받을 수 있으므로 그대로 둔다.
if unique then
    redis.call('ZREM', myIndexKey, partyId)
end

if size >= target then
    -- 정원이 찼다. 아직 남아 있는 색인에서도 전부 뺀다.
    for i = 1, n do
        redis.call('ZREM', KEYS[2 + i], partyId)
    end

    -- 제안이 열린다. status 와 expiresAt 은 여기서 딱 한 번만 생겨야 한다.
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
