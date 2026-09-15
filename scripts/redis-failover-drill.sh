#!/usr/bin/env bash
# Redis Sentinel failover 연습 (docs/07 §11.4, docs/09 Runbooks).
#
# 운영에 나가기 전에 "정말 승격되는가"를 손으로 확인한다. 설정만 써 두고 한 번도
# 돌려 보지 않은 failover는 장애 때 처음 돌아간다.
set -euo pipefail

COMPOSE=(docker compose --profile ha)
MASTER_NAME=queuemate

master_addr() {
  "${COMPOSE[@]}" exec -T redis-sentinel-1 \
    redis-cli -p 26379 sentinel get-master-addr-by-name "$MASTER_NAME" 2>/dev/null | head -1
}

echo "== 토폴로지 확인 =="
"${COMPOSE[@]}" exec -T redis-sentinel-1 redis-cli -p 26379 sentinel master "$MASTER_NAME" \
  | paste - - | grep -E '^(name|ip|num-slaves|num-other-sentinels|quorum)'

before="$(master_addr)"
echo
echo "== 현재 master: ${before} =="

echo
echo "== 복제가 끊기면 write가 거부되는지 (INV-10 fail-closed) =="
"${COMPOSE[@]}" stop redis-replica-1 redis-replica-2 >/dev/null 2>&1
sleep 12
if "${COMPOSE[@]}" exec -T redis-master redis-cli set qm:drill:probe v 2>&1 | grep -q NOREPLICAS; then
  echo "OK: NOREPLICAS로 거부됐다"
else
  echo "FAIL: 복제가 끊겼는데도 write가 통과했다. min-replicas-to-write를 확인하라" >&2
  exit 1
fi
"${COMPOSE[@]}" start redis-replica-1 redis-replica-2 >/dev/null 2>&1
sleep 8

echo
echo "== master를 정지하고 승격을 기다린다 =="
"${COMPOSE[@]}" stop redis-master >/dev/null 2>&1
for i in $(seq 1 20); do
  sleep 3
  now="$(master_addr)"
  if [ -n "$now" ] && [ "$now" != "$before" ]; then
    echo "OK: $((i * 3))초 만에 승격됐다. 새 master = ${now}"
    break
  fi
  if [ "$i" = 20 ]; then
    echo "FAIL: 60초 안에 승격되지 않았다" >&2
    exit 1
  fi
done

echo
echo "== 새 master에 write가 되는지 =="
sleep 10
"${COMPOSE[@]}" exec -T redis-sentinel-1 redis-cli -p 26379 sentinel master "$MASTER_NAME" \
  | paste - - | grep -E '^(ip|num-slaves|flags)'

echo
echo "== 옛 master를 replica로 되돌린다 =="
"${COMPOSE[@]}" start redis-master >/dev/null 2>&1
sleep 12
"${COMPOSE[@]}" exec -T redis-master redis-cli info replication | grep -E '^role|master_host'

echo
echo "드릴 완료. 강제로 master를 되돌리지 않는다 (docs/09 Runbooks)."
