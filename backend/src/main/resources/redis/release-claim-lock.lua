-- 짧은 mutex 해제 원시 연산. 만료 뒤 새 소유자가 얻은 락을 지우지 않는다.
-- 참가자 검증과 선점 업무 로직은 Java에서 처리한다.
local released = 0
for i = 1, #KEYS do
  if redis.call('GET', KEYS[i]) == ARGV[1] then
    released = released + redis.call('DEL', KEYS[i])
  end
end
return released
