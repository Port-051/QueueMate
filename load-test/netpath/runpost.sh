#!/usr/bin/env bash
# POST 매칭 경로를 도커 vs 네이티브로 비교. 매 arm 전에 Redis 정리 + 대기자 N명 적재.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
LT="$(dirname "$DIR")"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
VUS=${VUS:-10}; DURATION=${DURATION:-20s}; N=${N:-200}; ROUND=${ROUND:-1}
K=qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs
z() { docker exec qm-redis redis-cli ZCARD "$K:$1"; }

prep() { # $1 = run id
  "$LT/clean.sh" > /dev/null
  BASE_URL="http://$WSL_IP:8080" N="$N" RUN_ID="$1" ~/k6 run --quiet "$LT/stock.js" >/dev/null 2>&1
  for i in $(seq 1 60); do c=$(z JUNGLE); [ "$c" -ge "$N" ] && break; sleep 0.5; done
}

arm() { # $1=label $2=mode
  local RID="p${ROUND}$2$(date +%s)"
  prep "$RID"
  local pre=$(z JUNGLE)
  if [ "$2" = "docker" ]; then
    docker run --rm -v "$DIR:/scripts" -e BASE_URL="http://$WSL_IP:8080" -e VUS="$VUS" \
      -e DURATION="$DURATION" -e RUN_ID="$RID" -e LABEL="$1" -e OUT_DIR=/scripts/out \
      grafana/k6:1.3.0 run --quiet /scripts/postload.js >/dev/null 2>&1
  else
    BASE_URL="http://$WSL_IP:8080" VUS="$VUS" DURATION="$DURATION" RUN_ID="$RID" \
      LABEL="$1" OUT_DIR="$DIR/out" ~/k6 run --quiet "$DIR/postload.js" >/dev/null 2>&1
  fi
  echo "  $1 완료 (적재후 JUNGLE=$pre, 측정후 JUNGLE=$(z JUNGLE) TOP=$(z TOP))"
}

echo "=== ROUND=$ROUND VUS=$VUS DURATION=$DURATION N=$N ==="
arm "r${ROUND}_docker" docker
arm "r${ROUND}_native" native
