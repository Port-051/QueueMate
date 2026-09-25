-- 강퇴. 방장이 방에 들어와 있는 사람을 내보낸다. 방장만 할 수 있다 (docs/11 D-11 8번).
-- "부른 사람이 방장인가"를 여기서 방장 키와 비교한다 — 방장은 계정의 권한이 아니라 방마다 다른 Redis 의 상태다.
--
-- KEYS[1] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId. 이 키가 있다 = 방이 있다
-- KEYS[2] = qm:room:{roomId}:members          방에 있는 사람들. SET
-- KEYS[3] = qm:user:active-room:{대상 userId}  강퇴당하는 사람의 입장 표시 키. STRING, 값은 roomId. 부른 사람의 것이 아니다
--
-- ARGV[1] = 부른 사람의 userId
-- ARGV[2] = 대상(강퇴당하는 사람)의 userId
-- ARGV[3] = roomId
--
-- 반환 — 목록이다. 첫 칸이 코드이고 KickResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다).
-- 강퇴했을 때(1)만 뒤에 "방에 남은 사람들"이 붙는다 — 서비스가 그 사람들과 강퇴된 본인에게 알린다.
-- 뜻이 같은 값은 다른 스크립트와 번호를 맞췄다(-3 은 signal-room.lua, -4 는 enter-room.lua)
--   1 = 강퇴했다                          { 1, 방에 남은 사람들...(방장 포함) }
--  -3 = 대상이 이 방의 멤버가 아니다      이미 나갔거나 들어온 적이 없다
--  -4 = 그런 방이 없다                    방장 키가 없다
--  -5 = 부른 사람이 방장이 아니다         이 방의 멤버여도, 다른 방의 방장이어도 마찬가지다
--  -6 = 방장이 자기 자신을 강퇴하려 한다  방장이 나가려면 나가기를 쓴다 — 그러면 방이 닫힌다
--
-- 거절 갈래(음수)에서는 아무것도 쓰지 않는다. 수명도 건드리지 않는다 — 강퇴는 방의 수명을 늘리지 않는다.

local roomHostKey = KEYS[1]
local roomMemberKey = KEYS[2]
local targetActiveRoomKey = KEYS[3]

local callerId = ARGV[1]
local targetId = ARGV[2]
local roomId = ARGV[3]

-- 키가 없으면 GET 은 false 를 준다
local host = redis.call('GET', roomHostKey)
if not host then
    return { -4 }
end

if host ~= callerId then
    return { -5 }
end

-- 여기까지 왔으면 부른 사람이 방장이다. 방장을 멤버 SET 에서 빼면 방장 키만 남은 방이 된다
if targetId == callerId then
    return { -6 }
end

-- 읽기를 쓰기보다 먼저 끝낸다. Lua 에는 되돌리기가 없어서, SREM 뒤의 GET 이 실패하면 SET 에서만 빠진 어긋난 상태가 남는다.
-- 이 뒤의 DEL 은 키의 자료형을 가리지 않으므로 SREM 이 성공한 뒤에는 실패할 명령이 없다
local targetRoomId = redis.call('GET', targetActiveRoomKey)

-- 늦게 도착한 강퇴일 수 있다(그 사이에 나갔다). 이 방의 멤버가 아니면 입장 표시를 건드리기 전에 끝낸다
if redis.call('SREM', roomMemberKey, targetId) == 0 then
    return { -3 }
end

-- 입장 표시가 이 방을 가리킬 때만 지운다. 다른 방을 가리키고 있다면 그 표시는 남의 것이다
if targetRoomId == roomId then
    redis.call('DEL', targetActiveRoomKey)
end

return { 1, unpack(redis.call('SMEMBERS', roomMemberKey)) }
