-- 자동 매칭 파티의 방에 들어온다 — 없으면 만들고 있으면 들어간다. 확인과 쓰기를 한 번에 한다 (2026-09-27 소유자 결정 — docs/11 D-42).
-- 게시판의 방은 글 쓰기가 만들고(create-room.lua) 입장이 따로다(enter-room.lua). 자동 매칭 파티에는 글이 없고 파티원 전원이
-- MATCH_CONFIRMED 를 받고 동시에 이 요청을 부르므로, "누가 먼저 오든 첫 사람이 방을 만들고 나머지는 들어간다"를 한 스크립트로 묶었다 —
-- 둘로 나누면 먼저 온 둘이 각자 방을 만들거나 만들기 전에 들어가려다 거절당한다.
--
-- KEYS[1] = qm:party:{partyId}                matching 의 파티 HASH. **읽기만 한다 — HGET · HEXISTS 뿐이고 쓰지도 지우지도 수명을 걸지도 않는다**
-- KEYS[2] = qm:user:active-room:{userId}      입장 표시 키. STRING, 값은 roomId
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId. 이 키가 있다 = 방이 있다
-- KEYS[4] = qm:room:{roomId}:members          방에 있는 사람들. SET
-- KEYS[5] = qm:room:{roomId}:confirmed        확정 표시 키. STRING, 값은 roomId — 자동 매칭 파티의 방은 태어날 때부터 확정된 방이다
--
-- ARGV[1] = userId
-- ARGV[2] = roomId (= partyId — matching 의 UUID 문자열 그대로)
-- ARGV[3] = 수명(초). 만들 때 세 키(방장 키 · 멤버 SET · 확정 표시 키)와 입장 표시 키에 건다. 접속 확인(heartbeat-room.lua)이 늘린다
--
-- 반환 — 목록이다. 첫 칸이 코드이고 MatchRoomResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다).
-- 들어왔을 때(3)만 뒤에 "내가 들어오기 전부터 방에 있던 사람들"이 붙는다 — 서비스가 그 사람들에게 입장을 알린다.
-- 뜻이 같은 값은 enter-room.lua 와 번호를 맞췄다(2 · -2 · -3 · -4)
--   1 = 방을 만들고 들어왔다. 내가 방장이다                     { 1 }
--   2 = 이미 이 방에 들어와 있다 (아무것도 쓰지 않는다 · 파티 HASH 를 보지 않는다)  { 2 }
--   3 = 있던 방에 들어왔다                                      { 3, "u1", "u2", ... }
--  -2 = 방이 가득 찼다 — 정원은 파티 HASH 의 target 이다
--  -3 = 다른 방에 들어가 있다 (파티 HASH 를 보지 않는다 — 그 파티가 있든 없든 이 값이다)
--  -4 = 확정된 파티가 없다 (아직 제안 중이다 · 수명이 다해 사라졌다 · 그런 파티가 없다 · HASH 에 target 이 없다)
--  -6 = 이 파티의 파티원이 아니다
--
-- **활성 요청 키(qm:user:active-request:{userId})를 보지 않는다** — create-room.lua · enter-room.lua 와 다른 점이다. 확정된 파티원의 활성 요청은
-- status = 'PARTY' 로 60초쯤 더 남아 있고, 그 활성 요청이 곧 이 파티다 — 그것을 보고 거절하면 아무도 방에 못 들어온다. "매칭 대기와 방은 한 번에 하나"
-- (D-19)는 그대로 지켜진다: 그 키를 쓰지도 지우지도 않고(EXISTS 로도 보지 않는다), 입장 표시 키를 세우면 matching 의 claim-request.lua 가 그것을
-- 보고 새 매칭을 거절한다. 게시판 방에 있는 사람이 자동 매칭 파티의 파티원일 수는 없다 — 매칭을 걸 때 입장 표시 키가 있으면 거절됐다.
--
-- 검사 순서 — ① 입장 표시 키가 이 방이면 { 2 } → ② 입장 표시 키가 다른 방이면 { -3 } → ③ 파티 HASH 의 status 가 CONFIRMED 이고 target 이 있는가(아니면 { -4 })
-- → ④ HASH 에 member:{userId} 가 있는가(아니면 { -6 }) → ⑤ 방이 없으면 만든다({ 1 }) · 있으면 정원을 보고 들어간다({ 3 } / { -2 }).
--
-- **왜 입장 표시 키를 파티 HASH 보다 먼저 보는가**(2026-09-28 — 소유자 지적. "방에 있는 사람의 판정은 방 키로, 방에 없는 사람의 판정만 HASH 로").
-- 파티 HASH 는 수명이 600초이고 방 키는 접속 확인으로 그보다 오래 산다. HASH 를 먼저 보면 확정 10분 뒤 방에 멀쩡히 앉아 있는 사람이 새로고침(같은 요청)을
-- 했을 때 { -4 } 로 404 가 나 튕긴 것처럼 보인다. 이미 방에 들어온 사람에게는 방 키(입장 표시 키)가 원본이다 — 그 사람의 자격은 들어올 때 HASH 로 이미 봤다.
-- 다른 방에 들어가 있는 사람({ -3 })도 HASH 를 볼 필요가 없어 같이 앞에 두었다. 방에 없는 사람은 HASH 로만 자격을 보므로, HASH 가 사라지면 못 들어온다
-- (게시판 확정 방의 "확정 뒤 새 사람은 못 들어온다" 와 같다).
--
-- 확인을 전부 끝낸 뒤에 쓴다. Lua 에는 되돌리기가 없어서, 쓰다가 거절하면 쓴 것이 그대로 남는다.

local partyKey = KEYS[1]
local activeRoomKey = KEYS[2]
local roomHostKey = KEYS[3]
local roomMemberKey = KEYS[4]
local roomConfirmedKey = KEYS[5]

local userId = ARGV[1]
local roomId = ARGV[2]
local ttl = tonumber(ARGV[3])

-- 방에 있는 사람은 방 키로 판정한다 — 파티 HASH 가 수명으로 사라진 뒤에도 새로고침이 { 2 } 다 (머리 주석)
local currentRoomId = redis.call('GET', activeRoomKey)
if currentRoomId == roomId then
    return { 2 }
end
if currentRoomId then
    return { -3 }
end

-- 여기부터는 방에 없는 사람이다 — 자격은 파티 HASH 로만 본다
-- 키가 없으면 HGET 은 false 를 준다 — 사라진 파티도 여기서 걸린다
if redis.call('HGET', partyKey, 'status') ~= 'CONFIRMED' then
    return { -4 }
end
-- 정원은 이 HASH 의 target 이다. 없으면 혼자 읽을 수 없는 HASH 라 없는 파티로 다룬다 (MatchPartyReader 와 같은 판정)
local capacity = tonumber(redis.call('HGET', partyKey, 'target'))
if not capacity then
    return { -4 }
end

-- EXISTS · HEXISTS 는 0 / 1 을 준다. Lua 에서는 0 도 참이라 반드시 == 0 으로 비교한다
if redis.call('HEXISTS', partyKey, 'member:' .. userId) == 0 then
    return { -6 }
end

local host = redis.call('GET', roomHostKey)
if not host then
    -- 방이 없다. 내가 만들고 방장이 된다. 자동 매칭 파티는 이미 확정된 사실이라 확정 표시 키도 같이 세운다 —
    -- 그래야 방장이 나가도 승계되고(D-23) enter-room.lua 로 들어오려는 새 사람은 거절된다
    -- SETEX 는 인자 순서가 (키, 초, 값)이라 헷갈린다. SET 키 값 EX 초 로 쓴다
    redis.call('SET', roomHostKey, userId, 'EX', ttl)
    redis.call('SET', roomConfirmedKey, roomId, 'EX', ttl)
    redis.call('SADD', roomMemberKey, userId)
    redis.call('EXPIRE', roomMemberKey, ttl)
    redis.call('SET', activeRoomKey, roomId, 'EX', ttl)
    return { 1 }
end

-- 들어가기 전의 사람들이다. 이미 정원만큼 있으면 자리가 없다 — 파티원이 아닌 사람은 위에서 걸렸으니 정상이면 나지 않는다
local members = redis.call('SMEMBERS', roomMemberKey)
if #members >= capacity then
    return { -2 }
end

-- 방의 수명은 건드리지 않는다 — 그것은 접속 확인이 늘린다 (enter-room.lua 와 같다)
redis.call('SET', activeRoomKey, roomId, 'EX', ttl)
redis.call('SADD', roomMemberKey, userId)

-- unpack 은 테이블을 낱개 값으로 푼다 — { 3, unpack({"u1","u2"}) } 는 { 3, "u1", "u2" } 다
return { 3, unpack(members) }
