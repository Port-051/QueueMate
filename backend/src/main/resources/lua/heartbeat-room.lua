-- 접속 확인. 브라우저가 주기적으로 부른다 — "나 아직 이 방에 있다". 키의 수명을 다시 걸어 늘린다.
-- 신호가 끊기면 수명이 다해 저절로 사라진다. 앱이 죽어도, 나가기를 못 눌러도 마찬가지다.
--
-- KEYS[1] = qm:user:active-room:{userId}      신호를 보낸 사람의 입장 표시 키. STRING, 값은 roomId
-- KEYS[2] = qm:room:{roomId}:members          방에 있는 사람들. HASH — 필드는 userId, 값은 참가할 때 고른 포지션(P-44)
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId
-- KEYS[4] = qm:room:{roomId}:confirmed        확정 표시 키. 방장의 신호가 이 키의 수명도 늘린다 — 안 늘리면 수명이 다한 뒤 확정이 풀려 새 사람이 들어온다
-- KEYS[5] = qm:room:{roomId}:needs            찾는 포지션 SET(P-44). 멤버 HASH 의 수명을 늘리는 자리마다 같이 늘린다 — 안 늘리면 방은 살아 있는데
--                                             남은 찾는 포지션만 먼저 사라져 아무도 포지션을 고를 수 없는 방이 된다. 없는 방(포지션이 없는 모드 · 다 골랐다)이면 EXPIRE 가 아무것도 하지 않는다.
--                                             방장의 신호가 유령을 뺄 때 그 사람이 고른 포지션을 여기에 돌려놓는다(2026-10-01 — leave-room.lua 의 returnPosition 과 같다)
--
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = 입장 표시 키 접두사 (qm:user:active-room:). 방장의 신호가 멤버들의 입장 표시를 훑을 때 키를 조립한다
-- ARGV[4] = 수명(초)
--
-- 반환 — 목록이다. 첫 칸이 코드이고 HeartbeatResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다)
--   { 1 }                                        일반 멤버의 신호. 수명을 늘렸다
--   { 1, 뺀 사람 수 N, 뺀 사람 N명..., 남은 사람들... }   방장의 신호. 수명을 늘리고 멤버 HASH 의 유령을 뺐다.
--                                                  서비스가 "남은 사람들"에게 "뺀 사람"마다 퇴장을 알린다.
--                                                  뺀 사람이 없어도 이 모양이다 — { 1, 0, 남은 사람들... }
--   { -1 }   이 방에 없는 사람이다. 이미 수명이 다해 빠졌을 수 있다 — 클라이언트는 방 화면을 닫는다
--   { -2 }   방이 없어졌다(방장이 사라졌다). 이 사람의 입장 표시를 여기서 지웠다

local activeRoomKey = KEYS[1]
local roomMemberKey = KEYS[2]
local roomHostKey = KEYS[3]
local roomConfirmedKey = KEYS[4]
local roomNeedsKey = KEYS[5]

local userId = ARGV[1]
local roomId = ARGV[2]
local activeRoomPrefix = ARGV[3]
local ttl = tonumber(ARGV[4])

-- 뺀 유령의 포지션을 남은 찾는 포지션 SET 에 돌려놓는다 — 입장이 고른 포지션을 SREM 으로 뺐기 때문이다(나가기 · 강퇴와 같다 — 2026-10-01).
--   ① 포지션이 없으면("" · 필드 없음) 돌려놓지 않는다 — "" 는 고를 수 있는 포지션이 아니고, 넣으면 needs 가 없던 방(포지션이 없는 모드)에 쓸모없는 키가 생긴다.
--      필드가 없으면 HGET 이 false 를 주고 SADD 에 넣으면 스크립트 오류다
--   ② 다 차서 비었던 SET 은 Redis 가 지웠으므로 SADD 가 새로 만든다 — 새 키에는 수명이 없어 방이 사라져도 남는다. 그래서 이 신호가 방에 거는 수명을 그대로 건다
--      (방장의 신호 안에서만 부르고, 그때 방은 막 수명이 늘었다 — leave-room.lua 처럼 남은 수명을 물어볼 까닭이 없다)
local function returnPosition(pos)
    if not pos or pos == '' then
        return
    end
    redis.call('SADD', roomNeedsKey, pos)
    redis.call('EXPIRE', roomNeedsKey, ttl)
end

local active = redis.call('GET', activeRoomKey)
if roomId ~= active then
    return { -1 }
end

local host = redis.call('GET', roomHostKey)

-- 방장 키가 없다 — 방장이 말없이 사라져 방장 키의 수명이 다했다.
--   확정하지 않은 방: 방이 없어진 것이다. 남아 있던 사람은 여기서 자기 입장 표시를 지우고 나간다.
--   확정한 방: 이어 간다. 이 신호를 보낸 사람이 방장 자리를 넘겨받고, 아래의 방장 분기로 그대로 들어간다 —
--             거기서 방의 수명을 늘리고, 입장 표시가 만료된 옛 방장을 유령으로 빼서 남은 사람들에게 알린다.
-- EXISTS 는 0 / 1 을 준다. Lua 에서는 0 도 참이라 반드시 == 1 로 비교한다
if not host then
    if redis.call('EXISTS', roomConfirmedKey) == 0 then
        redis.call('DEL', activeRoomKey)
        return { -2 }
    end

    redis.call('SET', roomHostKey, userId, 'EX', ttl)
    host = userId
end

if host == userId then
    redis.call('EXPIRE', activeRoomKey, ttl)
    redis.call('EXPIRE', roomMemberKey, ttl)
    redis.call('EXPIRE', roomHostKey, ttl)
    -- 확정하지 않은 방이면 키가 없고 EXPIRE 는 아무것도 하지 않는다 (없는 키를 만들지 않는다)
    redis.call('EXPIRE', roomConfirmedKey, ttl)
    -- 포지션이 없는 방이면 키가 없고 EXPIRE 는 아무것도 하지 않는다
    redis.call('EXPIRE', roomNeedsKey, ttl)
    -- 유령을 뺀다. HASH 의 필드에는 수명을 걸 수 없어서, 말없이 사라진 사람은 입장 표시가 만료된 뒤에도 이름이 남는다.
    -- 입장 표시가 이 방을 가리키지 않으면(만료돼 없거나, 다른 방에 가 있으면) 이 방 사람이 아니다.
    -- 빼는 것은 HASH 의 필드뿐이다 — 그 사람의 입장 표시 키는 건드리지 않는다. 다른 방을 가리키고 있다면 남의 것이다.
    -- 그 사람이 고른 포지션은 찾는 포지션(KEYS[5])에 돌려놓는다 — 나가기 · 강퇴처럼 그 자리가 다시 열린다(2026-10-01).
    -- 확정한 방은 돌려놓지 않는다 — 새 사람이 못 들어와(enter-room.lua 의 -7) 돌려놓을 까닭이 없고, 방장 키가 만료돼 넘겨받은 직후라면
    -- 옛 방장이 유령으로 빠지는데 그 값은 방장 포지션이다(찾는 포지션에 없던 것 — leave-room.lua 의 승계도 돌려놓지 않는다)
    local returnPositions = redis.call('EXISTS', roomConfirmedKey) == 0
    local removed = {}
    local remaining = {}
    for i, member in ipairs(redis.call('HKEYS', roomMemberKey)) do
        if redis.call('GET', activeRoomPrefix .. member) ~= roomId then
            -- 필드를 지우기 전에 읽는다 — 지운 뒤에는 값이 없다
            local position = redis.call('HGET', roomMemberKey, member)
            redis.call('HDEL', roomMemberKey, member)
            if returnPositions then
                returnPosition(position)
            end
            table.insert(removed, member)
        else
            table.insert(remaining, member)
        end
    end

    -- 뺀 사람과 남은 사람을 한 목록에 담는다. 몇 명이 빠졌는지를 둘째 칸에 적어 경계를 알린다.
    -- unpack 은 맨 끝에 있을 때만 전부 풀리므로 뺀 사람은 한 칸씩 넣는다
    local reply = { 1, #removed }
    for i, member in ipairs(removed) do
        table.insert(reply, member)
    end
    for i, member in ipairs(remaining) do
        table.insert(reply, member)
    end
    return reply
end

-- 일반 멤버는 자기 입장 표시의 수명만 늘린다. 방의 수명은 방장의 신호만 늘린다 —
-- 그래야 방장이 사라졌을 때 방이 저절로 없어진다
redis.call('EXPIRE', activeRoomKey, ttl)

-- 확정한 방은 예외다. 방장이 사라져도 이어 가야 하므로 멤버의 신호가 멤버 HASH 와 확정 표시 키(와 찾는 포지션)의 수명도 늘린다.
-- 방장 키는 늘리지 않는다 — 방장이 사라졌으면 방장 키가 만료돼야 위에서 다음 사람이 넘겨받는다
if redis.call('EXISTS', roomConfirmedKey) == 1 then
    redis.call('EXPIRE', roomMemberKey, ttl)
    redis.call('EXPIRE', roomConfirmedKey, ttl)
    redis.call('EXPIRE', roomNeedsKey, ttl)
end

return { 1 }