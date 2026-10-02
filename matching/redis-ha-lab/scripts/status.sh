#!/usr/bin/env bash
# 지금 상태를 한 화면에 모은다. 실험 전후로 이걸 찍어 두면 나중에 해석이 된다.
set -eu
. "$(dirname "$0")/lib.sh"

echo "=============== 컨테이너 ==============="
dc ps --format 'table {{.Name}}\t{{.Status}}'

echo
echo "=============== Sentinel 이 보는 master ==============="
addr=$(current_master || echo "?")
echo "get-master-addr-by-name mymaster -> ${addr}"

echo
echo "=============== SENTINEL master mymaster (요약) ==============="
sent SENTINEL master mymaster 2>/dev/null | paste - - | \
  grep -E '^(name|ip|port|flags|num-slaves|num-other-sentinels|quorum|failover-state|down-after-milliseconds|failover-timeout|role-reported|s-down-time|o-down-time)' || true

echo
echo "=============== replicas ==============="
sent SENTINEL replicas mymaster 2>/dev/null | paste - - | \
  grep -E '^(name|flags|master-link-status|slave-repl-offset|slave-priority)' || true

echo
echo "=============== 다른 Sentinel ==============="
sent SENTINEL sentinels mymaster 2>/dev/null | paste - - | \
  grep -E '^(name|ip|port|flags)' || true

echo
echo "=============== 각 노드의 role ==============="
for n in $NODES; do
  role=$(node "$n" INFO replication 2>/dev/null | grep -E '^role:' | tr -d '\r' || echo "role:DOWN")
  off=$(node "$n" INFO replication 2>/dev/null | grep -E '^master_repl_offset:' | tr -d '\r' || echo "")
  link=$(node "$n" INFO replication 2>/dev/null | grep -E '^master_link_status:' | tr -d '\r' || echo "")
  printf "%-18s %-14s %-28s %s\n" "$n" "$role" "$off" "$link"
done
