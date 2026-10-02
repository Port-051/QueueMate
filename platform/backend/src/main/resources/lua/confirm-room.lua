-- 방장이 파티를 확정한다. 확정되면 새 사람이 못 들어온다(enter-room.lua 가 이 표시를 본다). 되돌릴 수 없다.
--
-- KEYS[1] = qm:room:{roomId}:confirmed        확정 표시 키. STRING, 값은 roomId. 이 키가 있다 = 확정된 방이다
-- KEYS[2] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId
-- KEYS[3] = qm:room:{roomId}:members          방에 있는 사람들. HASH — 필드는 userId(값은 고른 포지션 — 여기서는 읽지 않는다, P-44)
--
-- ARGV[1] = userId (부른 사람)
-- ARGV[2] = roomId (확정 표시 키의 값으로 쓴다)
-- ARGV[3] = 수명(초). 확정 표시 키에 건다. 방장의 접속 확인(heartbeat-room.lua)이 다시 걸어 늘린다 —
--           안 늘리면 수명이 다한 뒤 확정이 풀려 새 사람이 들어온다
--
-- 반환 — 목록이다. 첫 칸이 코드이고 ConfirmResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다)
--   { 1, 멤버들... }   확정했다. 멤버들은 확정한 그 순간 방에 있던 전원(방장 포함)이고 이 사람들이 파티원이다
--   { 2 }              이미 확정된 방이다 (아무것도 쓰지 않는다)
--   { -4 }             방이 없다 (방장 키가 없다)
--   { -5 }             부른 사람이 방장이 아니다 — 방 밖의 사람도, 다른 방의 방장도 여기로 온다
--   { -7 }             혼자서는 확정할 수 없다. 2명 이상이어야 한다
--
-- 확인을 전부 끝낸 뒤에 쓴다. Lua 에는 되돌리기가 없어서, 쓰다가 거절하면 쓴 것이 그대로 남는다.

local roomConfirmedKey = KEYS[1]
local roomHostKey = KEYS[2]
local roomMemberKey = KEYS[3]

local userId = ARGV[1]
local roomId = ARGV[2]
local ttl = tonumber(ARGV[3])

-- 부른 사람의 입장 표시는 보지 않는다. 방장 키가 이 사람을 가리키면 이 사람은 이 방에 있다 —
-- 방 만들기가 방장 키와 입장 표시를 같이 쓰고, 방장이 나가면 둘을 같이 지운다.
-- roomId 를 바꿔 남의 방을 확정하려 해도 그 방의 방장 키가 그 사람이 아니라 아래에서 걸린다
local host = redis.call('GET', roomHostKey)

if not host then
    return {-4}
end

if host ~= userId then
    return {-5}
end

-- 이미 확정된 방이다. 버튼을 두 번 눌렀거나 재시도다 — 아무것도 바꾸지 않는다
if redis.call('EXISTS', roomConfirmedKey) == 1 then
    return {2}
end

local members = redis.call('HKEYS', roomMemberKey)

if #members < 2 then
    return {-7}
end

-- 확인이 전부 끝났다. 쓰기는 여기 하나뿐이다 — 값과 수명을 한 명령으로 쓴다.
-- (SETNX 로 "이미 있나"와 쓰기를 한 번에 하면, 그 뒤의 인원 확인에서 거절해도 확정 표시가 남는다.
--  스크립트 안은 어차피 원자적이라 EXISTS 로 보고 나중에 써도 그 사이에 아무도 끼어들지 못한다)
redis.call('SET', roomConfirmedKey, roomId, 'EX', ttl)

return {1, unpack(members)}
