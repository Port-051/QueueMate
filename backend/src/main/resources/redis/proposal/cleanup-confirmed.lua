-- 확정된 제안 뒷정리. 수락으로 확정된 직후 자바가 한 번 부른다.
--
-- **확정을 찍는 accept-proposal.lua 와 합치지 않는다.** 그 스크립트는 수락자 집합을 다시 세어
-- 판단하는 것으로 멱등성을 얻는다. 거기서 같이 지워 버리면 재시도가 셀 근거를 잃는다.
-- 확정과 정리는 실패했을 때 할 일도 다르다 — 정리가 실패해도 확정을 되돌리면 안 된다.
--
-- 대신 이 스크립트가 스스로 status 를 확인한다. 몇 번 불려도, 페일오버로 다시 실행돼도
-- 결과가 같다.
--
-- ── 활성 요청을 지우지 않는 이유 ─────────────────────────────────────
-- 확정된 사람은 **이미 파티에 속해 있다.** 활성 요청을 지우면 그 순간 새 매칭을 걸 수 있게 되어
-- 한 사람이 두 파티에 속한다 (INV-2). 그래서 지우는 대신 status 필드를 찍어 "파티 중"으로
-- 표시하고, INV-1 의 선점은 그대로 유지한다.
--
-- <b>그래서 이 상태를 푸는 것은 이 앱이 아니다.</b> 게임이 끝났는지를 여기서는 알 수 없다 —
-- 파티를 닫는 것은 app:platform 이고, 그쪽이 PartyClosed 를 발행하면 그때 활성 요청과 파티를
-- 지운다(미구현). 그전까지는 확정된 사용자가 큐에 다시 들어오지 못하는 것이 맞는 상태다.
--
-- ── 무엇을 남기고 무엇을 정리하나 ────────────────────────────────────
--   활성 요청   남긴다. status = 'PARTY' 를 찍어 조회가 "파티 중"을 답할 수 있게 한다
--   파티 HASH   남긴다. 상태 조회와 수락 재전송이 여기를 읽는다
--   수락자 집합 TTL 을 건다. 확정 판정에 다 쓰였고, 재전송한 수락은 파티의 status 만 보면 된다
--
--   needs 색인은 정원이 찰 때 이미 빠졌고, 만료 대기 목록은 확정 시 accept-proposal.lua 가 뺀다
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1] = qm:party:{partyId}              HASH. status / member:{userId}
-- KEYS[2] = qm:proposal:accepts:{partyId}   SET.  수락한 userId
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1] = 활성 요청 키 접두사 (qm:user:active-request:)
-- ARGV[2] = 수락자 집합을 남겨 둘 시간(초)
--
-- ── 반환 ─────────────────────────────────────────────────────────────
--   파티원 userId 목록. 확정된 제안이 아니면 빈 목록이다
--   (이미 정리됐거나, 확정되지 않은 파티다)

local partyKey   = KEYS[1]
local acceptsKey = KEYS[2]

local activeRequestPrefix = ARGV[1]
local ttlSeconds          = tonumber(ARGV[2])

-- 확정된 제안만 정리한다. 재실행·동시 호출에도 답이 같은 이유가 이 한 줄이다
if redis.call('HGET', partyKey, 'status') ~= 'CONFIRMED' then
    return {}
end

-- 파티원은 파티 HASH 의 member: 필드가 원본이다. 수락자 집합을 쓰지 않는 이유는
-- 그 집합이 "누가 눌렀나"이지 "누가 파티원인가"가 아니기 때문이다
local fields = redis.call('HKEYS', partyKey)
local members = {}
for i = 1, #fields do
    if string.sub(fields[i], 1, 7) == 'member:' then
        local userId = string.sub(fields[i], 8)
        table.insert(members, userId)
        -- 활성 요청은 남기고 상태만 바꾼다. 지우면 INV-2 가 깨진다(머리말 참고)
        redis.call('HSET', activeRequestPrefix .. userId, 'status', 'PARTY')
    end
end

redis.call('EXPIRE', acceptsKey, ttlSeconds)

return members
