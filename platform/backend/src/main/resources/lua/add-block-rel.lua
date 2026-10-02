-- 차단 관계 사본에 한 쌍을 더한다 — 대칭으로 둘 다(2026-10-02 소유자 결정 · docs/11 D-57 · contracts/platform-api.md P-52).
-- 부르는 곳은 BlockService#block 하나다 — blocks 에 줄을 넣은 같은 트랜잭션 안, 커밋 전이다(DB 먼저 · Redis 다음).
-- 두 SADD 를 한 스크립트에 둔 것은 한쪽만 들어가는 일을 없애려는 것이다(matching 은 들어오려는 사람의 집합 하나만 본다).
-- 이미 있어도 그대로다(SADD) — 반대 방향의 차단이 먼저 있었으면 이미 들어 있다.
--
-- KEYS[1] = qm:user:block-rel:{a}     a 의 차단 관계 SET
-- KEYS[2] = qm:user:block-rel:{b}     b 의 차단 관계 SET
-- ARGV[1] = a (사용자 번호의 십진 문자열)
-- ARGV[2] = b
--
-- 반환 — 0 (늘 같다. 실패는 Redis 의 예외로 온다)
--
redis.call('SADD', KEYS[1], ARGV[2])
redis.call('SADD', KEYS[2], ARGV[1])
return 0
