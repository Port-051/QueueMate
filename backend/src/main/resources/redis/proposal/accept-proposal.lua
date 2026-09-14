-- 제안 수락. 한 참가자의 수락을 기록하고, 그 수락으로 전원이 차면 확정까지 한다 (INV-4).
--
-- **제안은 곧 파티다.** proposal 하나는 언제나 하나의 party 를 뜻하므로(CLAUDE.md §1)
-- proposalId = partyId 이고, 제안 상태를 파티 HASH 에 얹는다. 별도 qm:proposal:{id}
-- 레코드를 두지 않는다. 수락자 집합만 따로 SET 으로 둔다.
--
-- 확인(전원 찼나)과 쓰기(확정)가 한 덩어리여야 한다. 나뉘면 마지막 두 명이 동시에
-- 눌렀을 때 둘 다 "내가 마지막이다" 라고 판단한다 (CLAUDE.md §4 원자성 규칙).
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1] = qm:party:{partyId}              HASH. status / target / member:{userId}
-- KEYS[2] = qm:proposal:accepts:{partyId}   SET.  수락한 userId 만 담는다
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1] = userId. 수락한 사용자
--
-- ── 반환 (문자열) ────────────────────────────────────────────────────
--   NOT_FOUND     제안이 없다. 아직 정원이 안 찼거나, 파티 자체가 사라졌거나,
--                 **다른 참가자가 거절해 제안 흔적이 지워졌다** (decline-proposal.lua)
--   NOT_A_MEMBER  이 파티의 참가자가 아니다
--   ACCEPTED      수락이 기록됐다. 아직 전원이 수락하지는 않았다
--   CONFIRMED     전원이 수락해 확정됐다 (내 수락으로 찼든, 이미 확정돼 있었든)
--   DECLINED      다른 참가자가 이미 거절해 제안이 깨졌다.
--                 **지금 이 값이 나가는 경로는 없다** — 아래 3번 참고
--
-- ── 멱등성 ───────────────────────────────────────────────────────────
-- 같은 수락이 두 번 들어와도 **답이 같아야 한다.** 응답을 못 읽은 클라이언트는 재시도하고,
-- Redis 페일오버 뒤에도 같은 호출이 다시 올 수 있다.
--
-- 쓰기는 SADD 와 HSET 뿐이고 둘 다 몇 번 해도 결과가 같다. 더하기(HINCRBY/INCR)는
-- 쓰지 않는다 — 재시도가 한 번 더 더하면 수락자 수가 실제와 어긋나 INV-4 가 깨진다.
--
-- **SADD 의 반환값을 보고 early return 하지 않는 것이 이 설계의 핵심이다.**
-- "이미 수락했으면 ALREADY_RESPONDED" 로 끊으면 1차 응답은 ACCEPTED, 재시도 응답은
-- ALREADY_RESPONDED 가 되어 답이 달라진다. 특히 **마지막 수락자**가 확정을 유발해 놓고
-- 응답을 잃으면, 재시도에서 자기가 확정시켰다는 사실을 영영 못 듣게 된다.
-- 매번 다시 세고 다시 판정하므로 몇 번을 불러도 같은 답이 나온다.
--
-- **중간에 죽어도 자가 치유된다.** SADD 는 성공했는데 status 를 CONFIRMED 로 올리기 전에
-- 죽어도, 다음 수락(또는 같은 사용자의 재시도)이 SCARD 로 다시 세어 확정을 올린다.
-- 수락자 수를 카운터로 들고 있었다면 이 복구가 불가능하다 — 카운터는 어디까지 반영됐는지
-- 알 수 없지만, 집합은 지금 들어 있는 것이 곧 사실이기 때문이다.

local partyKey   = KEYS[1]
local acceptsKey = KEYS[2]
local userId     = ARGV[1]

-- 1. 제안이 있나. status 는 정원이 찰 때 join-party*.lua 가 만든다.
--    없으면(Lua false) 아직 제안이 아니거나 파티가 사라진 것이다.
local status = redis.call('HGET', partyKey, 'status')
if status == false then
    return 'NOT_FOUND'
end

-- 2. 남의 제안에 응답하려는 것은 아닌가.
if redis.call('HEXISTS', partyKey, 'member:' .. userId) == 0 then
    return 'NOT_A_MEMBER'
end

-- 3. 이미 끝난 제안이면 그 상태를 그대로 돌려준다.
--    확정도 거절도 되돌릴 수 없다 (INV-5). 재시도해도 같은 답이다.
--
--    **status == 'DECLINED' 분기는 현재 도달하지 않는다.** decline-proposal.lua 가
--    거절을 status='DECLINED' 로 기록하지 않고 status/expiresAt 을 지우기 때문이다
--    (그래야 파티가 다시 찼을 때 HSETNX 가 새 제안을 열 수 있다 — 그 파일의
--    "왜 지우는가" 참고). 그래서 거절된 제안에 들어온 수락은 1번에서 NOT_FOUND 다.
--
--    그래도 **지우지 않고 방어로 남겨 둔다.** 거절 처리 방식은 한 번 바뀐 자리이고
--    (예전에는 status 를 남겼다) 다시 바뀔 수 있는데, 그때 이 분기가 없으면 깨진
--    제안에 수락이 쌓여 조용히 확정된다. 사라져도 해가 없는 세 줄이 그 위험보다 싸다.
if status == 'CONFIRMED' then
    return 'CONFIRMED'
end
if status == 'DECLINED' then
    return 'DECLINED'
end

-- 4. 수락을 기록한다. 반환값을 보지 않는다 (위 "멱등성" 참고).
redis.call('SADD', acceptsKey, userId)

-- 5. 지금 몇 명이 수락했나. 저장된 카운터가 아니라 집합을 센다.
local count  = redis.call('SCARD', acceptsKey)
local target = tonumber(redis.call('HGET', partyKey, 'target'))

-- 6. 전원이 찼으면 확정한다 (INV-4).
--    target 이 없는 파티는 있을 수 없지만, 있다면 확정 판정을 할 근거가 없으므로
--    확정하지 않고 수락만 기록한 채로 둔다 (fail-closed).
if target ~= nil and count >= target then
    redis.call('HSET', partyKey, 'status', 'CONFIRMED')
    return 'CONFIRMED'
end

-- 7. 아직 기다리는 사람이 있다.
return 'ACCEPTED'
