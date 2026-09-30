-- 방에서 나간다. 확정하지 않은 방은 방장이 나가면 통째로 없앤다 — 방에 다른 사람이 있어도 마찬가지다.
-- 확정한 방은 방장이 나가도 이어 간다 — 방장 자리를 남은 사람 가운데 한 명에게 넘긴다. 넘길 사람이 없으면 방을 없앤다.
--
-- KEYS[1] = qm:user:active-room:{userId}      나가는 사람의 입장 표시 키. STRING, 값은 roomId
-- KEYS[2] = qm:room:{roomId}:members          방에 있는 사람들. SET
-- KEYS[3] = qm:room:{roomId}:host             방장 키. STRING, 값은 방장의 userId
-- KEYS[4] = qm:room:{roomId}:confirmed        확정 표시 키. 방을 없앨 때(그리고 확정한 방의 마지막 사람이 나갈 때) 같이 지운다 —
--                                             남으면 같은 roomId 로 다시 만든 방이 처음부터 확정돼 있다
-- KEYS[5] = qm:room:no-auto-join:{userId}     자동 합류 건너뛰기 목록. ZSET, 원소는 roomId · score 는 풀리는 시각(epoch ms). AutoJoinService 가 읽는다.
--                                             스스로 나간 사람은 10분 동안 이 방에 자동 합류로는 안 들어간다 — 직접 입장은 막지 않는다 (2026-09-29 소유자 결정).
--                                             일반 멤버가 나가는 길(1)에서만 적는다. 방이 없어지는 길(2)은 적을 방이 없고,
--                                             확정한 방의 방장 승계(1)는 확정된 방이라 애초에 자동 합류 후보(모집 중인 글)가 아니다
--
-- ARGV[1] = userId
-- ARGV[2] = roomId
-- ARGV[3] = 입장 표시 키 접두사 (qm:user:active-room:). 방을 없앨 때 남은 사람들의 키를 여기서 조립한다.
--           그 키들은 KEYS 로 받을 수 없다 — 누가 남아 있는지는 스크립트 안에서 SMEMBERS 를 해 봐야 안다.
--           Redis 한 대(클러스터 아님)를 전제한 방식이고 matching 의 leave-party.lua 도 같은 식으로 조립한다
-- ARGV[4] = 금지가 풀리는 시각(epoch ms). 자바가 지금 + 10분으로 넘긴다 — KEYS[5] 의 score 가 된다
--
-- 반환 — 목록이다. 첫 칸이 코드이고 LeaveResult 의 code 와 짝이다(한쪽을 고치면 다른 쪽도 고친다).
-- 뒤에는 서비스가 알림을 보낼 사람들이 붙는다. 방이 없어지면 멤버 SET 도 지워지므로 스크립트가 지우기 전에 읽어 돌려준다
--   1 = 나갔다                       { 1, 방에 남은 사람들... }   확정한 방의 방장이 나가 방장이 바뀐 것도 이것이다.
--                                    새 방장이 누구인지는 돌려주지 않는다 — 클라이언트가 방 안 사람 목록을 다시 조회한다
--   2 = 방장이 나가서 방이 없어졌다   { 2, 방에 있던 사람들...(방장 포함) }   확정하지 않은 방, 또는 확정한 방인데 넘길 사람이 없을 때
--  -1 = 이 방에 없는 사람이다 (아무것도 지우지 않는다)
--
-- 방을 없애는 일을 자바와 나눠 하지 않는다. "방장 키를 지웠다 → 앱이 죽었다"가 되면 남은 사람들의
-- 입장 표시 키가 영원히 남아, 그 사람들은 방 입장도 매칭도 못 하게 된다.

local activeRoomKey = KEYS[1]
local roomMemberKey = KEYS[2]
local roomHostKey = KEYS[3]
local roomConfirmedKey = KEYS[4]
local roomNoAutoJoin = KEYS[5]

local userId = ARGV[1]
local roomId = ARGV[2]
local activeRoomPrefix = ARGV[3]
local expireTime = tonumber(ARGV[4])

-- 입장 표시가 이 방을 가리킬 때만 지운다. 그 사이 다른 방에 들어갔다면 그 표시는 남의 것이다
local function clearMarker(key)
    if redis.call('GET', key) == roomId then
        redis.call('DEL', key)
    end
end

if redis.call('GET', roomHostKey) == userId then
    if redis.call('EXISTS', roomConfirmedKey) == 1 then
        -- 확정한 방이다. 방장 자리를 넘긴다. 받는 사람은 입장 표시가 이 방을 가리키는(살아 있는) 멤버여야 한다 —
        -- 멤버 SET 에 이름만 남은 유령에게 넘기면 방장 없는 방이 된다
        redis.call('SREM', roomMemberKey, userId)
        clearMarker(activeRoomKey)

        local remaining = redis.call('SMEMBERS', roomMemberKey)
        for _, memberId in ipairs(remaining) do
            if redis.call('GET', activeRoomPrefix .. memberId) == roomId then
                -- KEEPTTL: 남은 수명을 그대로 둔다. 새 방장의 접속 확인이 곧 늘린다
                redis.call('SET', roomHostKey, memberId, 'KEEPTTL')
                return { 1, unpack(remaining) }
            end
        end

        -- 넘길 사람이 없다. 방을 없앤다
        redis.call('DEL', roomMemberKey)
        redis.call('DEL', roomHostKey)
        redis.call('DEL', roomConfirmedKey)
        return { 2, userId }
    end

    -- 확정하지 않은 방이다. 방장도 멤버 SET 에 있으므로 방장의 입장 표시도 여기서 지워진다
    local members = redis.call('SMEMBERS', roomMemberKey)
    for _, memberId in ipairs(members) do
        clearMarker(activeRoomPrefix .. memberId)
    end
    redis.call('DEL', roomMemberKey)
    redis.call('DEL', roomHostKey)
    return { 2, unpack(members) }
end

-- 늦게 도착한 나가기일 수 있다. 이 방의 멤버가 아니면 입장 표시를 건드리기 전에 끝낸다
if redis.call('SREM', roomMemberKey, userId) == 0 then
    return { -1 }
end

clearMarker(activeRoomKey)
redis.call('ZADD', roomNoAutoJoin, expireTime, roomId)
redis.call('EXPIRE', roomNoAutoJoin, 600)
return { 1, unpack(redis.call('SMEMBERS', roomMemberKey)) }
