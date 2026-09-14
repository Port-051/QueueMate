-- 제안 거절. 한 명이라도 거절하면 제안이 깨진다.
--
-- **거절은 따로 담지 않는다.** 수락자 집합만 있고, 거절은 파티 HASH 에서 제안 흔적
-- (status / expiresAt)을 **지우는 것**으로 끝난다. status 를 'DECLINED' 로 설정하지
-- 않는다. 한 명만 거절해도 제안 전체가 깨지므로 누가 거절했는지를 집계할 필요가 없다.
-- 흔적이 지워지고 나면 그 뒤 들어오는 수락/거절은 전부 1번(제안이 있나)에서 걸린다.
--
-- **왜 'DECLINED' 로 남기지 않고 지우는가 — 좀비 파티 때문이다.**
-- status 를 남겨 두면 그 파티를 다시 채울 수 없다. 거절한 사람이 빠진 자리에 새 사람이
-- 들어와 정원이 다시 차면 join-party*.lua 가 HSETNX status 'PENDING' 을 부르는데,
-- status 가 이미 있으면 HSETNX 는 0 을 돌려주고 값은 'DECLINED' 인 채로 **굳는다.**
-- 그러면 새로 모인 사람들이 전원 수락해도 accept-proposal.lua 는 계속 'DECLINED' 를
-- 돌려준다 — 아무도 확정시킬 수 없는 좀비 파티가 된다.
-- 지워 두면 HSETNX 가 다시 성공해 **새 제안이 정상적으로 열린다.**
--
-- **파티와 참가자(member:*)는 그대로 두고 제안 흔적만 지우는 것이 이 설계의 핵심이다.**
-- "상태를 남기는 편이 낫지 않나" 로 되돌리지 마라. 되돌리는 순간 위 좀비 파티가 돌아온다.
--
-- **DEL acceptsKey 도 같은 이유다.** 옛 수락 기록을 남겨 두면, 파티가 다시 차서 새 제안이
-- 열렸을 때 새로 들어온 사람 혼자 눌러도 SCARD 가 target 을 채워 곧바로 확정된다.
-- 실제로는 아무도 수락하지 않은 제안이 확정되는 것이므로 INV-4 가 깨진다.
--
-- **거절해도 나머지 사람의 활성 요청은 건드리지 않는다.** 잘못은 거절한 사람이 했는데
-- 기다리던 사람들까지 큐에서 빠지면 안 된다 — 취소(leave-party.lua)와 다른 점이다.
-- 거절한 사람 본인을 큐에서 빼는 것은 이 스크립트가 아니라 ProposalService.decline() 이
-- matchCancelService.cancel() 로 한다. 파티/색인 정리는 이번 범위가 아니다.
--
-- ── KEYS ─────────────────────────────────────────────────────────────
-- KEYS[1] = qm:party:{partyId}              HASH. status / member:{userId}
-- KEYS[2] = qm:proposal:accepts:{partyId}   SET.  수락한 userId
--
-- ── ARGV ─────────────────────────────────────────────────────────────
-- ARGV[1] = userId. 거절한 사용자
--
-- ── 반환 (문자열) ────────────────────────────────────────────────────
--   NOT_FOUND         제안이 없다. 아직 정원이 안 찼거나, 파티가 사라졌거나,
--                     **이미 누가 거절해 제안 흔적이 지워졌다** (같은 거절의 재시도 포함)
--   NOT_A_MEMBER      이 파티의 참가자가 아니다
--   ALREADY_RESPONDED 내가 이미 수락해 놓고 거절을 눌렀다
--   DECLINED          내 거절로 제안이 깨졌다. 이 값은 **제안을 실제로 깬 한 번만** 나온다
--   CONFIRMED         그사이 전원 수락으로 확정됐다. 확정은 되돌릴 수 없다 (INV-5)
--
-- ── 멱등성 ───────────────────────────────────────────────────────────
-- **거절은 멱등이 아니다.** 첫 호출은 'DECLINED' 이지만, 같은 거절이 한 번 더 들어오면
-- status 가 이미 지워졌으므로 1번에서 'NOT_FOUND' 가 나간다. 답이 달라진다.
-- 매번 다시 세어 답을 맞추는 수락(accept-proposal.lua)과 일부러 다르게 두었다.
--
-- **그래도 괜찮다고 보았다.** 근거는 두 가지다.
--   · 두 답이 클라이언트를 같은 곳으로 보낸다. 거절한 사람은 ProposalService.decline() 이
--     matchCancelService.cancel() 로 큐에서도 빼므로, 'DECLINED' 를 받든 'NOT_FOUND' 를
--     받든 갈 화면은 대기 화면 복귀 하나뿐이다.
--   · 수락과 달리 재시도에서 놓치면 안 되는 정보가 없다. 수락은 마지막 수락자가 자기가
--     확정시켰다는 사실을 못 들으면 복구할 방법이 없지만, 거절에는 그런 값이 없다.
-- 판단이 들어간 자리다. 답을 맞추려고 'DECLINED' 를 남기는 쪽으로 되돌리면 위의 좀비
-- 파티를 다시 불러오므로, 바꿀 때는 그 대가를 먼저 보라.
--
-- 4번의 ALREADY_RESPONDED 는 여전히 의미가 있다. 거기까지 오는 경우는 **수락해 놓고
-- 거절을 누른 것** 하나뿐이고, 그건 재시도가 아니라 진짜 충돌이다 — 어느 쪽이 본심인지
-- 서버가 정할 수 없다. 거절 재시도는 1번에서 NOT_FOUND 로 걸러지므로 여기 오지 않는다.

local partyKey   = KEYS[1]
local acceptsKey = KEYS[2]
local userId     = ARGV[1]

-- 1. 제안이 있나. status 는 정원이 찰 때 join-party*.lua 가 만든다.
--    거절이 흔적을 지우므로, 이미 깨진 제안도 거절 재시도도 여기서 끝난다.
local status = redis.call('HGET', partyKey, 'status')
if status == false then
    return 'NOT_FOUND'
end

-- 2. 남의 제안에 응답하려는 것은 아닌가.
if redis.call('HEXISTS', partyKey, 'member:' .. userId) == 0 then
    return 'NOT_A_MEMBER'
end

-- 3. 확정된 제안은 깰 수 없다 (INV-5).
if status == 'CONFIRMED' then
    return 'CONFIRMED'
end

-- 4. 수락해 놓고 거절을 눌렀다. 재시도가 아니라 서로 다른 두 명령이다.
if redis.call('SISMEMBER', acceptsKey, userId) == 1 then
    return 'ALREADY_RESPONDED'
end

-- 5. 제안을 깬다. status 를 바꾸는 것이 아니라 제안 흔적만 지운다 (머리말 참고).
--    파티와 member:* 는 남으므로, 자리가 다시 차면 새 제안이 열린다.
redis.call('DEL', acceptsKey)
redis.call('HDEL', partyKey, 'status', 'expiresAt')
return 'DECLINED'
