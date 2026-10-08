-- 차단 관계 사본에서 한 쌍을 뺀다 — 대칭으로 둘 다(2026-10-02 소유자 결정 · docs/11 D-57 · contracts/platform-api.md P-52).
-- 부르는 곳은 BlockService#unblock 하나다 — blocks 의 줄을 지운 같은 트랜잭션 안, 커밋 전이고
-- 반대 방향의 줄이 DB 에 없을 때만 부른다(있으면 관계가 남아 있다 — 그 판단은 자바가 같은 트랜잭션에서 한다).
-- 두 SREM 을 한 스크립트에 둔 것은 한쪽만 빠지는 일을 없애려는 것이다 — 한쪽만 빠지면 그 사람이 들어오려 할 때 차단이 안 보인다.
-- 빈 SET 은 Redis 가 키째 지운다.
--
-- KEYS[1] = qm:user:block-rel:{a}     a 의 차단 관계 SET
-- KEYS[2] = qm:user:block-rel:{b}     b 의 차단 관계 SET
-- ARGV[1] = a (사용자 번호의 십진 문자열)
-- ARGV[2] = b
--
-- 반환 — 0 (늘 같다. 실패는 Redis 의 예외로 온다)
--
redis.call('SREM', KEYS[1], ARGV[2])
redis.call('SREM', KEYS[2], ARGV[1])
return 0
