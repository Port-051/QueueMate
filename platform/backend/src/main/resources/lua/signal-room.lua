-- WebRTC 시그널을 전달해도 되는지 확인한다. 보낸 사람과 받는 사람이 둘 다 이 방에 들어와 있어야 한다.
-- 읽기만 한다. 시그널의 내용은 이 스크립트에 오지도 않는다 — 서버는 그것을 해석하지도 저장하지도 않는다.
--
-- KEYS[1] = qm:user:active-room:{보낸 사람 userId}     입장 표시 키. STRING, 값은 roomId
-- KEYS[2] = qm:user:active-room:{받는 사람 userId}
--
-- ARGV[1] = roomId
--
-- 반환 — SignalResult 의 code 와 짝이다. 한쪽을 고치면 다른 쪽도 고친다
--   1 = 둘 다 이 방에 있다. 전달해도 된다
--  -1 = 보낸 사람이 이 방에 없다
--  -3 = 받는 사람이 이 방에 없다
--
-- 확정 전에도 둘러보는 사람이 음성에 붙어 말을 걸 수 있어야 하므로 기준은 "파티원"이 아니라
-- "방에 들어와 있는 사람"이다 (docs/11 D-9 · D-11). 두 값을 같은 순간에 읽으려고 스크립트로 묶었다

local roomId = ARGV[1]

if redis.call('GET', KEYS[1]) ~= roomId then
    return -1
end

if redis.call('GET', KEYS[2]) ~= roomId then
    return -3
end

return 1
