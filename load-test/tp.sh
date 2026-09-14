#!/usr/bin/env bash
# 사용법: tp.sh <LABEL>
set -eu
LABEL=$1
DIR="$(cd "$(dirname "$0")" && pwd)"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
BASE_URL=${BASE_URL:-http://$WSL_IP:8080}
VUS=${VUS:-20}; WARMUP=${WARMUP:-10s}; DURATION=${DURATION:-30s}
RUN_ID="${LABEL}$(date +%s)"

"$DIR/clean.sh" > /dev/null
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL="$BASE_URL" -e VUS="$VUS" -e WARMUP="$WARMUP" -e DURATION="$DURATION" \
  -e RUN_ID="$RUN_ID" -e LABEL="$LABEL" \
  grafana/k6 run --quiet /scripts/throughput.js 2>&1 | grep '@@RESULT@@'
