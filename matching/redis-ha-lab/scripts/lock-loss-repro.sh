#!/usr/bin/env bash
# 실험 4 - 비동기 복제로 인한 락 유실 재현.
#
#   ./scripts/lock-loss-repro.sh
#   MIN_REPLICAS_TO_WRITE=1 ./scripts/lock-loss-repro.sh     # 방어를 켜고 다시
#
# 무엇을 재현하나:
#   PoolLock.call() 이 잡는 락(qm:lock:pool:...)이 master 에만 기록된 채
#   replica 로 복제되기 전에 master 가 죽으면, 승격된 replica 에는 그 키가 없다.
#   두 번째 요청이 같은 락을 다시 잡는다. 상호 배제가 깨진 것이다.
#
# 왜 replica 를 pause 하나:
#   그냥 죽이면 로컬 복제가 너무 빨라(수백 us) 재현이 운에 달린다.
#   docker pause 로 replica 의 프로세스를 멈춰 두면 복제 스트림이 replica 쪽에서
#   소비되지 않는다. master 는 그래도 쓰기를 받는다 - 비동기 복제이기 때문이다.
#   이 "복제가 안 따라오는데 master 는 OK 를 돌려준다" 가 바로 재현하려는 성질이다.
#
# 락 키 모양은 PoolLock.java:43 (LOCK_PREFIX) + LolPartyKeys.java:24 (poolKey) 를 따랐다.
# Redisson RLock 의 내부 표현은 HASH(field = "<clientUuid>:<threadId>", value = 재진입 횟수)
# + PEXPIRE 다. 여기서는 그 모양을 손으로 흉내 낸다.
# 앱의 PoolLock 을 그대로 쓰는 판은 docs/app-config-snippets.md 의 테스트 스니펫을 봐라.
set -eu
. "$(dirname "$0")/lib.sh"

POOL_KEY="qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP"
LOCK_KEY="qm:lock:pool:${POOL_KEY}"
LEASE_MS=3000        # PoolLock.java:54 LEASE_MILLIS
HOLDER_A="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1"
HOLDER_B="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb:1"

echo "=== 0. 시작 상태 ==="
before=$(current_master)
echo "master = ${before}"
addr=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
m=$(container_of_addr "$addr")
echo "master container = ${m}"
node "$m" DEL "$LOCK_KEY" > /dev/null

echo
echo "=== 1. replica 두 대를 얼린다 (복제가 따라오지 못하게) ==="
docker pause "$REPLICA1_NAME" "$REPLICA2_NAME" > /dev/null
echo "paused t_ms=$(now_ms)"

echo
echo "=== 2. master 에 락을 잡는다 (A) ==="
node "$m" EVAL "
  if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('HSET', KEYS[1], ARGV[2], 1);
    redis.call('PEXPIRE', KEYS[1], ARGV[1]);
    return 1;
  end
  return 0;" 1 "$LOCK_KEY" "$LEASE_MS" "$HOLDER_A"
echo "A 가 락을 잡았다. master 의 상태:"
node "$m" HGETALL "$LOCK_KEY"
echo "master_repl_offset = $(node "$m" INFO replication | grep '^master_repl_offset' | tr -d '\r')"

echo
echo "=== 3. 복제가 안 따라온 것을 확인한다 (WAIT 는 0 을 돌려줘야 한다) ==="
echo "WAIT 1 100 -> $(node "$m" WAIT 1 100)"
echo "  (0 이면 어떤 replica 도 이 쓰기를 받지 못했다는 뜻이다)"

echo
echo "=== 4. master 를 죽인다 ==="
t0=$(now_ms)
docker kill "$m" > /dev/null
echo "$t0" > "${LAB_DIR}/.last-kill-ms"
echo "KILLED ${m} t0_ms=${t0}"

echo
echo "=== 5. replica 를 깨운다. Sentinel 이 승격시킨다 ==="
docker unpause "$REPLICA1_NAME" "$REPLICA2_NAME" > /dev/null
for i in $(seq 1 120); do
  cur=$(current_master)
  if [ "$cur" != "$before" ]; then
    echo "승격 완료 t+$(( $(now_ms) - t0 ))ms -> ${cur}"
    break
  fi
  sleep 0.5
done

new_addr=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
nm=$(container_of_addr "$new_addr")
echo "새 master container = ${nm}"

echo
echo "=== 6. 새 master 에 그 락이 있나 ==="
echo "EXISTS ${LOCK_KEY} -> $(node "$nm" EXISTS "$LOCK_KEY")"
echo "HGETALL -> $(node "$nm" HGETALL "$LOCK_KEY")"

echo
echo "=== 7. B 가 같은 락을 잡아 본다 ==="
r=$(node "$nm" EVAL "
  if redis.call('EXISTS', KEYS[1]) == 0 then
    redis.call('HSET', KEYS[1], ARGV[2], 1);
    redis.call('PEXPIRE', KEYS[1], ARGV[1]);
    return 1;
  end
  return 0;" 1 "$LOCK_KEY" "$LEASE_MS" "$HOLDER_B")

echo "결과 = ${r}"
if [ "$r" = "1" ]; then
  echo
  echo "  >>> 상호 배제가 깨졌다. A 의 유지 시간(${LEASE_MS}ms)이 아직 안 지났는데"
  echo "  >>> B 가 같은 풀 락을 잡았다. A 와 B 가 동시에 같은 needs 색인을 훑는다."
  echo "  >>> 이때 무슨 일이 나는지는 docs/lock-safety-analysis.md 를 봐라."
else
  echo "  락이 살아남았다. 복제가 따라잡았거나 승격 대상이 그 쓰기를 받은 replica 였다."
  echo "  MIN_REPLICAS_TO_WRITE 를 켜고 돌렸다면 그게 막은 것이다."
fi

echo
echo "=== 정리 ==="
echo "./scripts/reset.sh 로 판을 새로 깔고 다음 조건으로 넘어가라"
