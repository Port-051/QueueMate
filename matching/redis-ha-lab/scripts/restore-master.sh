#!/usr/bin/env bash
# 죽였던 노드를 되살린다. 실험 5 의 "구 master 복귀" 단계다.
#
#   ./scripts/restore-master.sh qm-ha-master
#
# 되살아난 노드는 자기 설정 파일(/data/redis.conf)에 master 로 적혀 있으므로
# 처음 몇 초 동안은 스스로를 master 라고 믿는다. Sentinel 이 그것을 보고
# REPLICAOF 를 보내 강등시킨다. 그 사이의 창이 실험 5 의 관측 대상이다.
set -eu
. "$(dirname "$0")/lib.sh"
target="${1:-qm-ha-master}"
docker start "$target" > /dev/null
echo "STARTED ${target} t_ms=$(now_ms)"
echo "--- 5초간 role 을 0.5초 간격으로 본다 (강등 순간을 잡는다) ---"
for i in $(seq 1 10); do
  r=$(docker exec "$target" redis-cli -p 6379 INFO replication 2>/dev/null | grep -E '^role:' | tr -d '\r' || echo "role:DOWN")
  echo "$(now_ms) ${target} ${r}"
  sleep 0.5
done
