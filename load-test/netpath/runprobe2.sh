#!/usr/bin/env bash
# 다른 에이전트가 앱을 재시작하면 측정이 무효가 되므로 arm 전후 8080 리스너 PID를 확인한다.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
VUS=${VUS:-5}; DURATION=${DURATION:-15s}; ROUND=${ROUND:-1}
appid() { ss -lptn 'sport = :8080' 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1; }

arm() { # $1=label $2=docker|native $3=base
  local a=$(appid)
  if [ "$2" = docker ]; then
    docker run --rm -v "$DIR:/scripts" -e BASE_URL="$3" -e VUS="$VUS" -e DURATION="$DURATION" \
      -e LABEL="$1" -e OUT_DIR=/scripts/out grafana/k6:1.3.0 run --quiet /scripts/netprobe.js >/dev/null 2>&1
  else
    BASE_URL="$3" VUS="$VUS" DURATION="$DURATION" LABEL="$1" OUT_DIR="$DIR/out" \
      ~/k6 run --quiet "$DIR/netprobe.js" >/dev/null 2>&1
  fi
  local b=$(appid)
  if [ "$a" != "$b" ]; then echo "  $1 무효(앱 재시작 $a->$b)"; rm -f "$DIR/out/probe_$1.json"; else echo "  $1 ok (pid=$a)"; fi
}

echo "=== ROUND=$ROUND VUS=$VUS DURATION=$DURATION ==="
arm "g${ROUND}_dockerIP" docker "http://$WSL_IP:8080"
arm "g${ROUND}_nativeIP" native "http://$WSL_IP:8080"
