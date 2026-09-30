#!/usr/bin/env bash
# POST 매칭 경로를 도커 vs 네이티브로 비교. 매 arm 전에 Redis 정리 + 대기자 N명 적재.
# 토큰 풀은 load-test/tokens.json 하나를 stock.js([0,N))와 postload.js([N,...))가 나눠 쓴다.
# 도커 arm 은 load-test/ 전체를 /scripts 로 마운트한다 — postload.js 가 ../lt.js 와 ../tokens.json 을 읽기 때문이다.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
LT="$(dirname "$DIR")"
WSL_IP=$(ip -4 addr show eth0 | grep -oP 'inet \K[0-9.]+')
VUS=${VUS:-10}; DURATION=${DURATION:-20s}; N=${N:-200}; ROUND=${ROUND:-1}
TIER=${TIER:-GOLD_2}; TOKENS=${TOKENS:-100000}
K=qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs
z() { docker exec qm-redis redis-cli ZCARD "$K:$1:$TIER"; }   # 티어 모드는 ':{tier}' 칸을 본다

TIER="$TIER" python3 "$LT/mint_tokens.py" --count "$TOKENS" || exit 1

prep() { # $1 = run id
  "$LT/clean.sh" > /dev/null
  BASE_URL="http://$WSL_IP:8080" N="$N" RUN_ID="$1" TIER="$TIER" TOKEN_OFFSET=0 \
    ~/k6 run --quiet "$LT/stock.js" >/dev/null 2>&1
  for i in $(seq 1 60); do c=$(z JUNGLE); [ "$c" -ge "$N" ] && break; sleep 0.5; done
}

arm() { # $1=label $2=mode
  local RID="p${ROUND}$2$(date +%s)"
  prep "$RID"
  local pre=$(z JUNGLE)
  if [ "$2" = "docker" ]; then
    docker run --rm -v "$LT:/scripts" -e BASE_URL="http://$WSL_IP:8080" -e VUS="$VUS" \
      -e DURATION="$DURATION" -e RUN_ID="$RID" -e LABEL="$1" -e OUT_DIR=/scripts/netpath/out \
      -e TIER="$TIER" -e TOKEN_OFFSET="$N" \
      grafana/k6:1.3.0 run --quiet /scripts/netpath/postload.js >/dev/null 2>&1
  else
    BASE_URL="http://$WSL_IP:8080" VUS="$VUS" DURATION="$DURATION" RUN_ID="$RID" \
      LABEL="$1" OUT_DIR="$DIR/out" TIER="$TIER" TOKEN_OFFSET="$N" \
      ~/k6 run --quiet "$DIR/postload.js" >/dev/null 2>&1
  fi
  echo "  $1 완료 (적재후 JUNGLE=$pre, 측정후 JUNGLE=$(z JUNGLE) TOP=$(z TOP))"
}

echo "=== ROUND=$ROUND VUS=$VUS DURATION=$DURATION N=$N TIER=$TIER ==="
arm "r${ROUND}_docker" docker
arm "r${ROUND}_native" native
