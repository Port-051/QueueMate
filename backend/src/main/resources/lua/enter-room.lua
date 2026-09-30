-- 방에 들어온다. 확인과 쓰기를 한 번에 한다.
--
-- KEYS[1] = qm:user:active-request:{userId}   matching 의 활성 요청 키. 있는지만 본다 — 쓰지도 지우지도 않는다
-- KEYS[2] = qm:user:active-room:{userId}      입장 표시 키. STRING, 값은 roomId
-- KEYS[3] = qm:room:{roomId}:members          방에 있는 사람들. SET
-- KEYS[4] = qm:room:{roomId}:host             방장 키. 있는지만 본다 — 이 키가 있다 = 방이 있다 (create-room.lua 가 쓴다)
-- KEYS[5] = qm:room:{roomId}:confirmed        확정 표시 키. 있는지만 본다 — 이 키가 있다 = 방장이 확정한 방이다 (confirm-room.lua 가 쓴다)
-- KEYS[6] = qm:room:no-entry:{userId}         입장 금지 목록. ZSET, 원소는 roomId · score 는 풀리는 시각(epoch ms). kick-room.lua 가 쓴다 —
--                                             이 방의 score 가 아직 안 지났으면 강퇴당한 지 10분이 안 된 것이다 (2026-09-29 소유자 결정)
--
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = 정원. 방장을 포함해 방에 있을 수 있는 최대 인원 — 그 글의 모드의 인원이다(2026-09-30 소유자 결정 — P-41. 솔로 랭크 2 · 5인 모드 5 ·
--           옛 글은 5). 자바가 글에서 읽어 넘긴다(PostEntryGate#check) — 이 스크립트는 값의 뜻을 모른다
-- ARGV[4] = 수명(초). 입장 표시 키에 건다. 방장 키와 멤버 SET 의 수명은 건드리지 않는다 — 그것은 방장의 접속 확인만 늘린다
-- ARGV[5] = 지금 시각(epoch ms). 자바가 넘긴다 — 입장 금지의 score 와 비교한다
--
-- 반환 — 목록이다. 첫 칸이 코드이고 EnterResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다).
-- 들어왔을 때(1)만 뒤에 "내가 들어오기 전부터 방에 있던 사람들"이 붙는다 — 서비스가 그 사람들에게 입장을 알린다.
--   { 1, "host", "u2", ... }   /   나머지는 { 코드 } 하나뿐이다
--
--   1 = 들어왔다
--   2 = 이미 이 방에 들어와 있다 (아무것도 쓰지 않는다)
--  -1 = 자동 매칭을 돌리는 중이다
--  -2 = 방이 가득 찼다
--  -3 = 다른 방에 들어가 있다
--  -4 = 그런 방이 없다 (만들어진 적이 없거나, 방장이 나가서 사라졌다)
--  -5 = 이 방에서 강퇴당한 지 10분이 안 됐다 (스스로 나간 사람은 막지 않는다)
--  -7 = 방장이 확정한 방이다. 새 사람은 못 들어온다 (이미 들어와 있는 사람의 재입장 2 는 그대로다)
--
-- 확인을 전부 끝낸 뒤에 쓴다. Lua 에는 되돌리기가 없어서, 쓰다가 거절하면 쓴 것이 그대로 남는다.

local activeRequestKey = KEYS[1]
local activeRoomKey = KEYS[2]
local roomMemberKey = KEYS[3]
local roomHostKey = KEYS[4]
local roomConfirmedKey = KEYS[5]
local noEntryKey = KEYS[6]

local userId = ARGV[1]
local roomId = ARGV[2]
local capacity = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])
local now = tonumber(ARGV[5])

local score = tonumber(redis.call('ZSCORE', noEntryKey, roomId))

if score and score > now then
    return {-5}
end

if redis.call('EXISTS', activeRequestKey) == 1 then
    return { -1 }
end

-- 입장이 방을 만들면 안 된다. 방장이 나가 사라진 방에 늦게 도착한 입장이 방장 없는 방을 되살린다
if redis.call('EXISTS', roomHostKey) == 0 then
    return { -4 }
end

-- 키가 없으면 GET 은 false 를 준다
local currentRoomId = redis.call('GET', activeRoomKey)
if currentRoomId == roomId then
    return { 2 }
end
if currentRoomId then
    return { -3 }
end

-- 확정된 방에는 새 사람이 못 들어온다. 자리가 비어 있어도 마찬가지다.
-- 위의 재입장 확인(2)보다 뒤에 둔다 — 앞에 두면 확정된 방의 멤버가 새로고침했을 때 거절당한다
if redis.call('EXISTS', roomConfirmedKey) == 1 then
    return { -7 }
end

-- 들어가기 전의 사람들이다. 이미 정원만큼 있으면 자리가 없다
local members = redis.call('SMEMBERS', roomMemberKey)
if #members >= capacity then
    return { -2 }
end

redis.call('SET', activeRoomKey, roomId, 'EX', ttl)
redis.call('SADD', roomMemberKey, userId)

-- unpack 은 테이블을 낱개 값으로 푼다 — { 1, unpack({"host","u2"}) } 는 { 1, "host", "u2" } 다
return { 1, unpack(members) }
