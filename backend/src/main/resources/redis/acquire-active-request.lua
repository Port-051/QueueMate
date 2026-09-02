-- 활성 요청 guard를 잡고 대기열에 등록한다 (INV-1, docs/07 §4).
--
-- 둘을 한 스크립트로 묶는 이유: guard만 서고 대기열에 못 들어가면 사용자는
-- "매칭 중"인데 아무도 후보로 보지 못하는 유령이 된다.
--
-- KEYS[1]  qm:user:active-request:{userId}
-- KEYS[2]  qm:queue:{game}:{mode}
--
-- ARGV[1]  requestId
-- ARGV[2]  queuedAt score. 재시도해도 최초 대기 시각을 보존해 aging을 잃지 않는다.
--
-- 반환  1  등록 성공
--       0  이미 활성 요청이 있다. 호출자는 409로 응답한다.

if redis.call('SET', KEYS[1], ARGV[1], 'NX') then
  redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
  return 1
end

return 0
