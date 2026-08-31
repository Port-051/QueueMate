-- 활성 요청 guard를 해제한다 (docs/07 §4).
--
-- 반드시 compare-and-delete로 지운다. 그냥 DEL 하면, 사용자가 취소한 직후 새로
-- 등록한 요청의 guard를 늦게 도착한 취소 요청이 지워 버린다. 그 순간 INV-1이 깨진다.
--
-- KEYS[1]  qm:user:active-request:{userId}
-- KEYS[2]  qm:queue:{game}:{mode}
--
-- ARGV[1]  해제하려는 requestId
--
-- 반환  1  해제됨
--       0  이미 없거나 다른 요청이 자리를 차지하고 있다. 아무것도 건드리지 않았다.

if redis.call('GET', KEYS[1]) == ARGV[1] then
  redis.call('DEL', KEYS[1])
  redis.call('ZREM', KEYS[2], ARGV[1])
  return 1
end

return 0
