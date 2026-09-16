










-- KEYS[1]   = qm:party:{partyId}              HASH. 파티 아이디 키 값
-- KEYS[2] = qm:proposal:accepts:{partyId}   SET.  수락한 userId 만 담는다
-- KEYS[3] = qm:proposal:pending ZSET. 아직 남아있는 것들

local partyKey   = KEYS[1]
local acceptsKey = KEYS[2]
local pendingKey = KEYS[3]
local partyId    = ARGV[1]

-- 진행 중인 제안인가. **읽고 판단하는 자리가 스크립트 안이어야 한다.**
-- 스위퍼는 Redis 를 두 번 부른다 — ZSET 에서 꺼낼 때와 이 스크립트를 실행할 때다.
-- 그 사이에 마지막 수락이 들어와 확정될 수 있다(수락 스크립트는 expiresAt 을 보지 않는다).
-- 그때 그냥 지우면 방금 찍힌 CONFIRMED 가 사라져 수락 재시도가 NOT_FOUND 를 받는다.
--
-- status 가 아예 없는 경우(거절·취소로 이미 깨진 제안)는 더 나쁘다. 수락자 집합도 같이
-- 지워져 있어서 **멤버 전원이 무응답자로 나오고**, 기다리던 사람들이 통째로 큐에서 빠진다.
local status = redis.call('HGET', partyKey, 'status')
if status ~= 'PENDING' then
    redis.call('ZREM', pendingKey, partyId)
    return {}
end

local fields = redis.call('HKEYS', partyKey)
local notAccepted = {}
local acceptedUserIds = {}
for i = 1, #fields do
    if string.sub(fields[i], 1, 7) == 'member:' then
        local userId = string.sub(fields[i], 8)
        if redis.call('SISMEMBER', acceptsKey, userId) == 0 then
            -- Lua 의 문자열 잇기는 '..' 다. '+' 는 산술이라 실행하는 순간 에러가 난다
            local requestId = redis.call('HGET', 'qm:user:active-request:' .. userId, 'requestId')
            local value = {userId, requestId}
            table.insert(notAccepted, value)
        else
            table.insert(acceptedUserIds, userId)
        end
    end
end

redis.call('HDEL', partyKey, 'status', 'expiresAt')
redis.call('DEL', acceptsKey)
-- 만료 대기 목록에서 뺀다. 안 빼면 스위퍼가 같은 파티를 주기마다 영원히 다시 꺼낸다
redis.call('ZREM', pendingKey, partyId)
return {notAccepted, acceptedUserIds}
