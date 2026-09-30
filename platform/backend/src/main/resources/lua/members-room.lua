-- 방 안 사람 목록. 묻는 사람이 그 방에 있는지 확인하고, 방장과 멤버를 같은 순간의 것으로 읽는다. 읽기만 한다.
--
-- KEYS[1] = qm:user:active-room:{userId}      묻는 사람의 입장 표시 키. STRING, 값은 roomId
-- KEYS[2] = qm:room:{roomId}:members          방에 있는 사람들. SET
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId
--
-- ARGV[1] = roomId
--
-- 반환 — 목록이다. 첫 칸이 코드다. RoomMemberService#members 가 이 값을 읽는다(한쪽을 고치면 다른 쪽도 고친다)
--   { 1, 방장, 멤버들... }   읽었다. 멤버들에는 방장도 들어 있다
--   { -1 }                   묻는 사람이 이 방에 없다. 방 밖의 사람에게는 방이 있는지도 알려 주지 않는다
--   { -2 }                   방이 없다. 방이 없어질 때 그 방 사람들의 입장 표시도 같이 지워지므로 -1 에서 먼저 걸린다 —
--                            실제로는 오지 않는 방어용 갈래다

local activeRoomKey = KEYS[1]
local roomMemberKey = KEYS[2]
local roomHostKey = KEYS[3]

local roomId = ARGV[1]

local real = redis.call('GET', activeRoomKey)

if real ~= roomId then
    return {-1}
end

local host = redis.call('GET', roomHostKey)
if not host then
    return {-2}
end
local members = redis.call('SMEMBERS', roomMemberKey)
return {1, host, unpack(members)}