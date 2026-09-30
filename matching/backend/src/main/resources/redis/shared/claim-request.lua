-- 사용자의 활성 매칭 요청 자리를 원자적으로 선점한다.
--
-- "이미 대기 중인가?"를 확인하고 등록하는 두 동작 사이에 다른 요청이 끼어들면
-- 한 사용자가 활성 요청을 둘 가질 수 있다 (INV-1 위반).
-- Lua 스크립트는 통째로 하나의 원자 단위로 실행되므로 그 틈이 없다.
--
-- KEYS[1]   = qm:user:active-request:{userId}
--             HASH. 활성 요청 표시이자, 나중에 키를 되조립할 재료를 담는다.
-- KEYS[2]   = qm:user:active-room:{userId}
--             app:room 의 입장 표시 키. 있는지만 본다 — 쓰지도 지우지도 값을 읽지도 않는다.
-- KEYS[3]   = qm:request:alive
--             접속 확인(heartbeat) 목록. ZSET, member = userId, score = 이 시각까지 신호가 없으면 빠진다(epoch ms).
--             접수가 첫 신호다 — HSET 과 같은 원자 실행 안에 넣어야 "선점됐는데 목록에 없어 스위퍼가 못 보는" 사람이 없다 (docs/11 D-43)
-- ARGV[1]   = userId (ZSET 의 member)
-- ARGV[2]   = 첫 신호의 시한 (epoch ms) — 자바가 now + 유예 로 계산해 넘긴다
-- ARGV[3..] = HASH 필드 쌍 (field, value, field, value, ...)
--
-- 반환
--   1 = 선점 성공
--   0 = 이미 활성 요청이 있음 (409)
--  -1 = 게시판 방에 들어가 있음 (409)

if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- 한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 15번).
-- 반대 방향(대기 중이면 방에 못 들어간다)은 app:room 의 입장 스크립트가 KEYS[1] 을 보고 지킨다.
-- 두 스크립트가 각자 "두 키를 확인하고 자기 키만 쓴다"를 원자적으로 하므로 먼저 돈 쪽이 이긴다.
if redis.call('EXISTS', KEYS[2]) == 1 then
    return -1
end

redis.call('HSET', KEYS[1], unpack(ARGV, 3))
redis.call('ZADD', KEYS[3], tonumber(ARGV[2]), ARGV[1])
-- 자리만 잡고 파티 배정 전에 앱이 죽으면, 이 키가 영원히 남아 그 사용자는
-- 매칭도 못 되고 INV-1 때문에 새 요청도 못 건다. 응답을 못 받았으면 requestId 를
-- 모르니 취소도 못 한다. 그래서 만료를 걸어 두고, 파티 배정에 성공한 스크립트가
-- PERSIST 로 뗀다. 배정이 어떤 이유로든 실패하면 이 시간 뒤에 저절로 풀린다.
--
-- 60 초인 이유: claim 부터 배정까지는 설정상 상한이 7초다
-- (차단 조회 300ms + 락 대기 3s + 락 유지 3s). 8배 여유다
redis.call('EXPIRE', KEYS[1], 60)

return 1
