-- 방을 만든다. 만든 사람이 방장이고, 방장은 만들면서 곧바로 그 방에 들어와 있다.
--
-- KEYS[1] = qm:user:active-request:{userId}   matching 의 활성 요청 키. 있는지만 본다 — 쓰지도 지우지도 않는다
-- KEYS[2] = qm:user:active-room:{userId}      입장 표시 키. STRING, 값은 roomId
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId. 이 키가 있다 = 방이 있다
-- KEYS[4] = qm:room:{roomId}:members          방에 있는 사람들. SET. 방장도 여기에 든다 (정원에 방장이 포함된다 — 정원은 입장이 글에서 받아 온다, enter-room.lua)
--
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = 수명(초). 세 키에 똑같이 건다. 접속 확인(heartbeat-room.lua)이 이 값을 다시 걸어 늘린다 —
--           신호가 끊기면 방과 입장 표시가 저절로 사라진다
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

local userId = ARGV[1]
local roomId = ARGV[2]
local ttl = tonumber(ARGV[3])

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
redis.call('SADD', roomMemberKey, userId)
redis.call('EXPIRE', roomMemberKey, ttl)

return 1
