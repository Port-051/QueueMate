-- 방을 만든다. 만든 사람이 방장이고, 방장은 만들면서 곧바로 그 방에 들어와 있다.
--
-- KEYS[1] = qm:user:active-request:{userId}   matching 의 활성 요청 키. 있는지만 본다 — 쓰지도 지우지도 않는다
-- KEYS[2] = qm:user:active-room:{userId}      입장 표시 키. STRING, 값은 roomId
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId. 이 키가 있다 = 방이 있다
-- KEYS[4] = qm:room:{roomId}:members          방에 있는 사람들. HASH — 필드는 userId, 값은 참가할 때 고른 포지션(2026-09-30 소유자 결정 — P-44).
--                                             방장도 여기에 든다(정원에 방장이 포함된다 — 정원은 입장이 글에서 받아 온다, enter-room.lua).
--                                             방장의 값은 방장 포지션이다(글의 hostPosition — 사람마다의 포지션을 HASH 한 곳에 둔다).
--                                             포지션이 없는 모드면 "" 다. 글은 고칠 수 없어(2026-10-01 소유자 결정) 방이 있는 동안 이 값은 그대로다
-- KEYS[5] = qm:room:{roomId}:needs            남은 찾는 포지션. SET — 여기서는 글의 wantedPositions 그대로 넣는다. 들어오는 사람이 고른 것을 빼고(enter-room.lua),
--                                             나가기 · 강퇴가 돌려놓는다(leave-room.lua · kick-room.lua). 포지션이 없는 모드면 만들지 않는다
--
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = 수명(초). 네 키(방장 키 · 멤버 HASH · 찾는 포지션 SET · 입장 표시 키)에 똑같이 건다. 접속 확인(heartbeat-room.lua)이 이 값을 다시 걸어 늘린다 —
--           신호가 끊기면 방과 입장 표시가 저절로 사라진다
-- ARGV[4] = 방장 포지션. 없으면 "" (자바는 null 을 보낼 수 없어 "" 로 넘긴다)
-- ARGV[5] = 찾는 포지션 수. 0 이면 KEYS[5] 를 만들지 않는다(포지션이 없는 모드 — 원소 없는 SADD 는 오류다)
-- ARGV[6..] = 찾는 포지션 이름들(글의 wantedPositions). KEYS[5] 에 넣는다
--
-- 반환 — CreateResult 의 code 와 짝이다. 한쪽을 고치면 다른 쪽도 고친다.
-- 뜻이 같은 값은 enter-room.lua 와 번호를 맞췄다
--   1 = 만들었다
--   2 = 이미 내가 만든 방이다 (아무것도 쓰지 않는다)
--  -1 = 자동 매칭을 돌리는 중이다
--  -3 = 다른 방에 들어가 있다
--  -4 = 이미 다른 사람이 방장인 방이다
--
-- 확인을 전부 끝낸 뒤에 쓴다. Lua 에는 되돌리기가 없어서, 쓰다가 거절하면 쓴 것이 그대로 남는다.

local activeRequestKey = KEYS[1]
local activeRoomKey = KEYS[2]
local roomHostKey = KEYS[3]
local roomMemberKey = KEYS[4]
local roomNeedsKey = KEYS[5]

local userId = ARGV[1]
local roomId = ARGV[2]
local ttl = tonumber(ARGV[3])
local position = ARGV[4]
local needCount = tonumber(ARGV[5])

if redis.call('EXISTS', activeRequestKey) == 1 then
    return -1
end

-- 키가 없으면 GET 은 false 를 준다
local host = redis.call('GET', roomHostKey)
if host == userId then
    return 2
end
if host then
    return -4
end

-- 방은 없다. 그런데 입장 표시가 있으면 다른 방에 들어가 있는 것이다
if redis.call('EXISTS', activeRoomKey) == 1 then
    return -3
end

-- SETEX 는 인자 순서가 (키, 초, 값)이라 헷갈린다. SET 키 값 EX 초 로 쓴다
redis.call('SET', roomHostKey, userId, 'EX', ttl)
redis.call('SET', activeRoomKey, roomId, 'EX', ttl)
-- 방장의 값은 방장 포지션이다(포지션이 없는 모드면 "")
redis.call('HSET', roomMemberKey, userId, position)
redis.call('EXPIRE', roomMemberKey, ttl)
-- 찾는 포지션은 이 글의 목록 그대로다 — 같은 번호의 방이 남긴 것이 있어도(정상이면 없다) 섞이지 않게 먼저 지운다
redis.call('DEL', roomNeedsKey)
if needCount > 0 then
    -- Redis 의 Lua 는 5.1 이라 table.unpack 이 없다 — 전역 unpack 을 쓴다
    redis.call('SADD', roomNeedsKey, unpack(ARGV, 6, 5 + needCount))
    redis.call('EXPIRE', roomNeedsKey, ttl)
end

return 1
