-- PUBG 의 티어를 보지 않는 합류. create-or-check-party-untiered.lua 가 찾아 둔 파티에 넣는다.
--
-- 티어를 보는 모드는 이 스크립트가 맡지 않는다. 별도 스크립트
-- create-or-check-party-tiered.lua 가 담당한다 (합류는 join-party-tiered.lua).
--
-- 찾기와 넣기는 두 스크립트로 나뉘어 있다. 그 사이에 자바의 최근 거절 검증(docs/11 D-45)이 끼기 때문이다.
-- 그 틈에 마지막 자리가 차버리는 것은 Lua 가 아니라 자바의 후보 풀 락(redisLock/PoolLock.java)이 막는다.
-- **차단(INV-6)은 이 스크립트가 본다** (docs/11 D-57, 2026-10-02 — 그 전에는 자바가 DB 의 blocks 를 읽어
-- 걸렀다). 멤버를 넣기 전에 파티원마다 내 차단 집합을 SISMEMBER 로 보고, 하나라도 있으면 아무것도
-- 쓰지 않고 -3 을 돌려준다.
-- 이 스크립트에는 정원 확인 분기가 없다. 정원 초과(INV-3)를 막는 것은 ① 후보가 needs 색인
-- (= 아직 안 찬 파티)에서만 나온다는 것과 ② 찾기부터 합류까지가 그 락 안이라는 것이다.
--
-- LoL 과 다른 점: PUBG 의 핵심 조건은 플랫폼(STEAM / KAKAO)이고 중복 금지(uniqueness)가 없다.
-- 그래서 합류해도 정원이 차기 전에는 색인에서 빼지 않고, 뺄 색인도 KEYS[3] 한 칸뿐이다.
--
-- KEYS[1]   = qm:party:{newPartyId}                  안 읽는다 (create 판과 KEYS 를 같이 쓰려고 둔다)
-- KEYS[2]   = qm:user:active-request:{userId}        HASH. partyId를 기록한다
-- KEYS[3]   = qm:party:open:PUBG:{mode}:{voice}:{purpose}:needs:{플랫폼}  ZSET. 정원이 차면 여기서 뺀다
-- KEYS[4]   = qm:user:block-rel:{userId}   SET. **KEYS 의 마지막이다**(KEYS[#KEYS] 로 읽는다 — 다른 게임과 같은 자리).
--             들어오려는 사람(나)과 어느 방향으로든 차단 관계인 사용자 번호. **app:platform 이 쓰고 이 앱은
--             SISMEMBER 로 읽기만 한다**(D-57 · platform P-52). 키가 없으면 "관계 없음" 이다.
--             create 판은 이 키를 받지 않는다 — 자바가 create 판 KEYS 끝에 하나를 더 붙여 이쪽에만 넘긴다
--
-- ARGV[1]   = partyId. 들어갈 파티 id
-- ARGV[2]   = score (지금 시각 epoch millis). 받기만 하고 쓰지 않는다 — create 판과 배치를 맞춘 자리다
-- ARGV[3]   = targetPartySize
-- ARGV[4]   = qm:party: 접두사. 들어갈 파티 키를 만들 때 쓴다
-- ARGV[5]   = userId
-- ARGV[6]   = expiresAt (epoch millis). 정원이 찰 때 만드는 제안의 시한이다.
--             자바가 now + queuemate.proposal.ttl-seconds 로 계산해 넘긴다.
--             후보를 찾지 않는 스크립트라 create 판의 start 자리에 싣는다
--
-- 파티 HASH 구조
--   partyId / target / createdAt          메타데이터. 인원 수는 저장하지 않는다 —
--                                         member: 필드를 세는 것이 인원이다
--   member:{userId} = 'EXIST'             참가자. 값은 자리 채움이고 읽지 않는다 —
--                                         자바 ScriptSupport#memberIds() 는 필드 이름만 본다.
--                                         create-or-check-party-untiered.lua 와 같은 리터럴을 쓴다
--   status / expiresAt                    정원이 찰 때 이 스크립트가 만든다.
--                                         제안(proposal)은 곧 파티이므로 별도 레코드를
--                                         두지 않는다 (CLAUDE.md §1). 수락 집계는
--                                         qm:proposal:accepts:{partyId} SET 이 맡는다
--
-- 반환 {코드, partyId, 현재인원}
--   { 1, partyId, size } = 기존 파티에 들어감 (아직 자리 남음)
--   { 2, partyId, size } = 기존 파티에 들어갔고 정원이 찼다 (제안을 만들 차례)
--   { -2, '', 0 }        = claim 의 TTL 이 먼저 끝나 활성 요청이 사라졌다
--   { -3, '', 0 }        = 파티원 가운데 나와 차단 관계인 사람이 있다 (INV-6). 아무것도 쓰지 않았다 —
--                          자바가 다음 후보를 본다
--   -1 은 돌려주지 않는다. 검사할 keyValue 목록을 받지 않기 때문이다

local userKey  = KEYS[2]
local blockKey = KEYS[#KEYS]

local partyId    = ARGV[1]
local score      = ARGV[2]
local target     = tonumber(ARGV[3])
local prefix     = ARGV[4]
local userId     = ARGV[5]
local expiresAt  = ARGV[6]

local memberField = 'member:' .. userId

-- 파티 인원은 세어서 구한다. 저장된 카운터를 쓰지 않는다.
--
-- Lua 는 롤백이 없다. 스크립트 중간에 Redis 가 죽으면 그때까지의 쓰기는 남고,
-- 페일오버 뒤 재시도가 같은 명령을 한 번 더 실행한다. 그때
-- HINCRBY 는 두 번 더해져 인원 수가 실제 멤버 수와 어긋나지만
-- (4 명짜리 파티가 "꽉 찼다"고 판정되어 INV-3 이 깨진다),
-- HSET 과 세기는 몇 번 해도 결과가 같다.
--
-- 접두사 'member:' 는 자바의 rule/ScriptSupport#memberIds() 와 같은 값이어야 한다.
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

-- 이 파티에 나와 차단 관계인 사람이 있나 (INV-6, docs/11 D-57).
--
-- 파티 HASH 의 member:{id} 필드마다 내 차단 집합(blockKey)을 SISMEMBER 로 본다. platform 이 차단 때
-- 양쪽 집합에 같이 넣으므로(대칭) 들어오는 사람의 집합 하나만 보면 된다.
-- **후보 풀을 도는 루프가 아니다**(CLAUDE.md §4 "Lua 안에 후보 순회 루프 금지") — 이미 고른 파티 하나의
-- 필드(정원 ≤ 5 명 + 메타 몇 개)만 돈다. 길이가 정원으로 묶여 있어 SCRIPT KILL 이 필요할 만큼 길어지지 않는다.
--
-- 내가 이미 이 파티의 멤버면 보지 않는다 — 페일오버 뒤 재시도가 이 스크립트를 처음부터 다시 돌 때
-- 이미 들어간 나를 "차단" 으로 돌려보내면 자바가 다음 후보에 또 넣어 한 사람이 두 파티에 걸린다.
local function blockedInParty(partyKey)
    if redis.call('HEXISTS', partyKey, memberField) == 1 then
        return false
    end
    local fields = redis.call('HKEYS', partyKey)
    for i = 1, #fields do
        if string.sub(fields[i], 1, 7) == 'member:'
                and redis.call('SISMEMBER', blockKey, string.sub(fields[i], 8)) == 1 then
            return true
        end
    end
    return false
end

-- 자리가 아직 살아 있나. claim 의 TTL 이 먼저 끝났다면 배정하지 않는다.
-- HSET 은 없는 키를 새로 만들기 때문에, 그냥 진행하면 partyId 하나만 든 반쪽짜리
-- 활성 요청이 되살아난다. 그 상태로는 취소가 game 필드를 못 읽어 터진다
if redis.call('EXISTS', userKey) == 0 then
    return { -2, '', 0 }
end

local partyKey = prefix .. partyId

-- 0. 차단 관계면 들어가지 않는다. **쓰기 전이어야 한다** — Lua 는 롤백이 없다
if blockedInParty(partyKey) then
    return { -3, '', 0 }
end

-- 1. 그 파티에 들어간다

-- 참가자를 먼저 기록하고, 그다음 센다. 순서가 반대면 나를 빼고 세게 된다
redis.call('HSET', partyKey, memberField, 'EXIST')
local size = memberCount(partyKey)
redis.call('HSET', userKey, 'partyId', partyId)
-- 배정됐다. 이제 claim 의 만료를 뗀다 (claim-request.lua 참고)
redis.call('PERSIST', userKey)

if size >= target then
    -- 정원이 찼다. 내 플랫폼 색인(PUBG 는 이 한 칸뿐이다)에서 뺀다.
    redis.call('ZREM', KEYS[3], partyId)

    -- 제안이 열린다. status 와 expiresAt 은 여기서 딱 한 번만 생겨야 한다.
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
