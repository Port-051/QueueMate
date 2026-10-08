#!/usr/bin/env bash
# 같은 스크립트/같은 VU/같은 시간으로 3가지 경로를 번갈아 측정한다.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
VUS=${VUS:-5}; DURATION=${DURATION:-20s}; ROUND=${ROUND:-1}
K6=~/k6

run_docker() { # $1=label $2=base
  docker run --rm -i -v "$DIR:/scripts" \
    -e BASE_URL="$2" -e VUS="$VUS" -e DURATION="$DURATION" \
    -e LABEL="$1" -e OUT_DIR=/scripts/out \
    grafana/k6:1.3.0 run --quiet /scripts/netprobe.js >/dev/null 2>&1
}
run_native() { # $1=label $2=base
  BASE_URL="$2" VUS="$VUS" DURATION="$DURATION" LABEL="$1" OUT_DIR="$DIR/out" \
    $K6 run --quiet "$DIR/netprobe.js" >/dev/null 2>&1
}

echo "WSL_IP=$WSL_IP VUS=$VUS DURATION=$DURATION ROUND=$ROUND"
run_docker "r${ROUND}_dockerIP" "http://$WSL_IP:8080";  echo "  dockerIP 완료"
run_native "r${ROUND}_nativeIP" "http://$WSL_IP:8080";  echo "  nativeIP 완료"
run_native "r${ROUND}_nativeLO" "http://127.0.0.1:8080"; echo "  nativeLO 완료"
