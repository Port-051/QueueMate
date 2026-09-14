#!/usr/bin/env bash
# 한 N에 대해: 정리 -> 대기자 N명 적재 -> 안정화 -> 측정 -> 색인 무결성 확인
set -eu
N=$1
BASE_URL=${BASE_URL:-http://172.22.149.244:8080}
VUS=${VUS:-20}
WARMUP=${WARMUP:-10s}
DURATION=${DURATION:-30s}
DIR="$(cd "$(dirname "$0")" && pwd)"
K=qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs
RUN_ID="n${N}$(date +%s)"

z() { docker exec qm-redis redis-cli ZCARD "$K:$1"; }

echo "=============== N=$N (VUS=$VUS WARMUP=$WARMUP DURATION=$DURATION) ==============="
"$DIR/clean.sh"

echo "--- 적재 중 (N=$N) ---"
docker run --rm -i -v "$DIR:/scripts" -e BASE_URL="$BASE_URL" -e N="$N" -e RUN_ID="$RUN_ID" \
  grafana/k6 run --quiet /scripts/stock.js > /dev/null 2>&1

# 비동기 파티 배정이 끝날 때까지 기다린다
for i in $(seq 1 60); do
  c=$(z JUNGLE); [ "$c" -ge "$N" ] && break; sleep 0.5
done
echo "적재 후 색인: JUNGLE=$(z JUNGLE) TOP=$(z TOP) MID=$(z MID) (기대 JUNGLE=$N, TOP=0)"

echo "--- 측정 중 ---"
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL="$BASE_URL" -e N="$N" -e RUN_ID="$RUN_ID" \
  -e VUS="$VUS" -e WARMUP="$WARMUP" -e DURATION="$DURATION" -e LABEL="${LABEL:-vu$VUS}" \
  grafana/k6 run --quiet /scripts/measure.js 2>&1 | tail -5

sleep 3
echo "측정 후 색인: JUNGLE=$(z JUNGLE) TOP=$(z TOP) MID=$(z MID)"
echo "  (TOP>0 이면 JUNGLE 요청이 색인 대신 새 파티를 만든 것 = 측정 오염)"
