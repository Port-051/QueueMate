-- 한 사용자의 차단 관계 사본을 통째로 갈아 끼운다 — DEL 과 SADD 를 한 번에(2026-10-02 · docs/11 D-57 · contracts/platform-api.md P-52).
-- 부르는 곳은 재구성(BlockRelationRedis#rebuild) 하나다 — blocks 표 전체에서 계산한 그 사용자의 집합을 넘긴다.
-- 한 스크립트라 matching 의 합류 스크립트가 "지웠는데 아직 안 채운" 빈 집합을 보는 순간이 없다.
-- 차단 관계가 하나도 없는 사용자는 이 스크립트를 부르지 않는다 — 재구성이 SCAN 으로 그 키를 찾아 지운다.
--
-- KEYS[1] = qm:user:block-rel:{userId}   그 사용자의 차단 관계 SET
-- ARGV    = 그 사용자와 어느 방향으로든 차단 관계인 사용자 번호들(십진 문자열 · 하나 이상)
--
-- 반환 — 넣은 member 의 수(#ARGV)
--
redis.call('DEL', KEYS[1])
local n = #ARGV
-- unpack 은 Lua 스택 한도(수천 개)가 있다 — 나눠서 넣는다. 한 사람의 차단 관계가 그만큼 많을 일은 없지만 스크립트가 터지지 않게 한다
local step = 500
for i = 1, n, step do
    redis.call('SADD', KEYS[1], unpack(ARGV, i, math.min(i + step - 1, n)))
end
return n
