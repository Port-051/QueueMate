-- 방 안 사람 목록. 묻는 사람이 그 방에 있는지 확인하고, 방장과 멤버를 같은 순간의 것으로 읽는다. 읽기만 한다.
--
-- KEYS[1] = qm:user:active-room:{userId}      묻는 사람의 입장 표시 키. STRING, 값은 roomId
-- KEYS[2] = qm:room:{roomId}:members          방에 있는 사람들. HASH — 필드는 userId, 값은 참가할 때 고른 포지션(P-44 — 방장은 방장 포지션, 안 골랐으면 "").
--                                             필드와 값을 같이 내보낸다(2026-10-01 소유자 결정 — 방 안 화면이 사람마다 고른 포지션을 보여 준다)
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId
--
-- ARGV[1] = roomId
--
-- 반환 — 목록이다. 첫 칸이 코드다. RoomMemberService#members 가 이 값을 읽는다(한쪽을 고치면 다른 쪽도 고친다)
--   { 1, 방장, 멤버1, 포지션1, 멤버2, 포지션2, ... }   읽었다. 셋째 칸부터 HGETALL 그대로 — 필드 · 값이 번갈아 온다. 멤버들에는 방장도 들어 있다.
--                                                      포지션은 고르지 않았으면 "" 다(자바가 null 로 바꾼다). 순서는 HASH 의 것이라 뜻이 없다
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
-- HGETALL 은 { 필드1, 값1, 필드2, 값2, ... } 다. unpack 은 맨 끝에 있을 때만 전부 풀린다
local members = redis.call('HGETALL', roomMemberKey)
return {1, host, unpack(members)}
