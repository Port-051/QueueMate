#!/usr/bin/env bash
# 사용법: tp.sh <LABEL>
# 환경변수: BASE_URL VUS WARMUP DURATION TIER(기본 GOLD_2) TOKENS(풀 크기, 기본 200000)
# 45초에 8만 요청을 낸 기록이 있어(docs/PERFORMANCE_EVIDENCE.md §2.4) 풀 기본값이 run.sh 보다 크다.
# 결과 줄의 token_exhausted 가 0 이 아니면 TOKENS 를 늘려라.
# 접속 확인(heartbeat)은 보내지 않는다 — 앱을 ALIVE_GRACE_MS=3600000 으로 띄워야 대기자가 90초 뒤 빠지지 않는다 (README "접속 확인(heartbeat)과 부하 테스트").
set -eu
LABEL=$1
DIR="$(cd "$(dirname "$0")" && pwd)"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
BASE_URL=${BASE_URL:-http://$WSL_IP:8080}
VUS=${VUS:-20}; WARMUP=${WARMUP:-10s}; DURATION=${DURATION:-30s}
TIER=${TIER:-GOLD_2}; TOKENS=${TOKENS:-200000}
RUN_ID="${LABEL}$(date +%s)"

TIER="$TIER" python3 "$DIR/mint_tokens.py" --count "$TOKENS"     # -> $DIR/tokens.json
"$DIR/clean.sh" > /dev/null
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL="$BASE_URL" -e VUS="$VUS" -e WARMUP="$WARMUP" -e DURATION="$DURATION" \
  -e RUN_ID="$RUN_ID" -e LABEL="$LABEL" -e TIER="$TIER" -e TOKEN_OFFSET=0 \
  grafana/k6 run --quiet /scripts/throughput.js 2>&1 | grep '@@RESULT@@'
