#!/usr/bin/env bash
# POST /api/v1/match-requests 를 도커/네이티브 두 경로에서 동일 조건으로.
# 다른 에이전트가 앱을 재시작하거나 Redis를 비우면 그 arm은 무효 처리한다.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"; LT="$(dirname "$DIR")"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
VUS=${VUS:-10}; DURATION=${DURATION:-15s}; N=${N:-300}; ROUND=${ROUND:-1}
K=qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs
z() { docker exec qm-redis redis-cli ZCARD "$K:JUNGLE" 2>/dev/null; }
appid() { ss -lptn 'sport = :8080' 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1; }

arm() { # $1=label $2=docker|native
  local RID="q${ROUND}$2$(date +%s)"
  "$DIR/clean-fast.sh" >/dev/null 2>&1
  BASE_URL="http://$WSL_IP:8080" N="$N" RUN_ID="$RID" ~/k6 run --quiet "$LT/stock.js" >/dev/null 2>&1
  for i in $(seq 1 40); do [ "$(z)" -ge "$N" ] 2>/dev/null && break; sleep 0.5; done
  local pre=$(z) a=$(appid)
  if [ "$2" = docker ]; then
    docker run --rm -v "$DIR:/scripts" -e BASE_URL="http://$WSL_IP:8080" -e VUS="$VUS" \
      -e DURATION="$DURATION" -e RUN_ID="$RID" -e LABEL="$1" -e OUT_DIR=/scripts/out \
      grafana/k6:1.3.0 run --quiet /scripts/postload.js >/dev/null 2>&1
  else
    BASE_URL="http://$WSL_IP:8080" VUS="$VUS" DURATION="$DURATION" RUN_ID="$RID" \
      LABEL="$1" OUT_DIR="$DIR/out" ~/k6 run --quiet "$DIR/postload.js" >/dev/null 2>&1
  fi
  local b=$(appid) post=$(z)
  if [ "$a" != "$b" ]; then echo "  $1 무효(앱 재시작 $a->$b)"; rm -f "$DIR/out/post_$1.json"
  else echo "  $1 ok pid=$a 색인 $pre->$post"; fi
}
echo "=== ROUND=$ROUND VUS=$VUS DURATION=$DURATION N=$N ==="
if [ "${ORDER:-dn}" = dn ]; then arm "q${ROUND}_docker" docker; arm "q${ROUND}_native" native; else arm "q${ROUND}_native" native; arm "q${ROUND}_docker" docker; fi
