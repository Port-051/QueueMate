#!/usr/bin/env bash
# 한 N에 대해: 토큰 풀 준비 -> 정리 -> 대기자 N명 적재 -> 안정화 -> 측정 -> 색인 무결성 확인
#
# 환경변수: BASE_URL(기본 WSL eth0 IP:8080 — 도커 k6 가 호스트 앱을 보는 주소) VUS WARMUP DURATION LABEL
#           TIER(기본 GOLD_2)  TOKENS(풀 크기, 기본 100000 — N + 측정 요청 수 이상이어야 한다. token_exhausted 참고)
# 사용자 번호: stock.js 가 풀의 [0, N), measure.js 가 [N, ...) 을 쓴다 (lt.js TOKEN_OFFSET).
# 접속 확인(heartbeat)은 보내지 않는다 — 앱을 ALIVE_GRACE_MS=3600000 으로 띄워야 적재한 대기자가 90초 뒤 빠지지 않는다 (README "접속 확인(heartbeat)과 부하 테스트").
set -eu
N=$1
DIR="$(cd "$(dirname "$0")" && pwd)"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
BASE_URL=${BASE_URL:-http://$WSL_IP:8080}
VUS=${VUS:-20}
WARMUP=${WARMUP:-10s}
DURATION=${DURATION:-30s}
TIER=${TIER:-GOLD_2}
TOKENS=${TOKENS:-100000}
# 티어 모드의 needs 색인은 (포지션 x 티어) 격자다 — Lua 가 ':{tier}' 를 붙이므로 그 칸을 봐야 한다 (ltconfig.py 머리말)
K=qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs
RUN_ID="n${N}$(date +%s)"

z() { docker exec qm-redis redis-cli ZCARD "$K:$1:$TIER"; }

echo "=============== N=$N (VUS=$VUS WARMUP=$WARMUP DURATION=$DURATION TIER=$TIER) ==============="
[ "$TOKENS" -gt "$N" ] || { echo "TOKENS($TOKENS) 는 N($N) 보다 커야 한다"; exit 1; }
TIER="$TIER" python3 "$DIR/mint_tokens.py" --count "$TOKENS"     # -> $DIR/tokens.json
"$DIR/clean.sh"

echo "--- 적재 중 (N=$N) ---"
docker run --rm -i -v "$DIR:/scripts" -e BASE_URL="$BASE_URL" -e N="$N" -e RUN_ID="$RUN_ID" \
  -e TIER="$TIER" -e TOKEN_OFFSET=0 \
  grafana/k6 run --quiet /scripts/stock.js > /dev/null 2>&1

# 비동기 파티 배정이 끝날 때까지 기다린다
for i in $(seq 1 60); do
  c=$(z JUNGLE); [ "$c" -ge "$N" ] && break; sleep 0.5
done
echo "적재 후 색인($TIER 칸): JUNGLE=$(z JUNGLE) TOP=$(z TOP) MID=$(z MID) (기대 JUNGLE=$N, TOP=0)"

echo "--- 측정 중 ---"
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL="$BASE_URL" -e N="$N" -e RUN_ID="$RUN_ID" \
  -e VUS="$VUS" -e WARMUP="$WARMUP" -e DURATION="$DURATION" -e LABEL="${LABEL:-vu$VUS}" \
  -e TIER="$TIER" -e TOKEN_OFFSET="$N" \
  grafana/k6 run --quiet /scripts/measure.js 2>&1 | tail -6

sleep 3
echo "측정 후 색인($TIER 칸): JUNGLE=$(z JUNGLE) TOP=$(z TOP) MID=$(z MID)"
echo "  (TOP>0 이면 JUNGLE 요청이 색인 대신 새 파티를 만든 것 = 측정 오염. token_exhausted>0 이면 TOKENS 를 늘려라)"
