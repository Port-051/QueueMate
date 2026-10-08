#!/usr/bin/env bash
# 후보 탐색 원시연산만 격리 측정한다. HTTP/JVM/도커 네트워크 노이즈가 없다.
#   index : ZRANGE key 0 0   (역색인 = 현재 구현, 맨 앞 1개만)
#   naive : ZRANGE key 0 -1  (색인 없이 대기자 전체를 훑는 방식)
set -eu
R="docker exec qm-redis redis-cli"
REQ=${REQ:-50000}
CONC=${CONC:-10}

for M in 10 100 500 1000 10000 100000; do
  $R DEL "bench:z:$M" > /dev/null
  # ZSET을 M개로 채운다
  $R EVAL "for i=1,tonumber(ARGV[1]) do redis.call('ZADD', KEYS[1], i, 'party-'..i) end return redis.call('ZCARD', KEYS[1])" 1 "bench:z:$M" "$M" > /dev/null
done
echo "적재 완료:"; for M in 10 100 500 1000 10000 100000; do echo "  M=$M ZCARD=$($R ZCARD bench:z:$M)"; done

echo ""
printf "%-8s %-8s %8s %12s %10s %10s %10s\n" "mode" "M" "n" "req/s" "avg_ms" "p50_ms" "p99.2_ms"
for MODE in index naive; do
  [ "$MODE" = index ] && STOP=0 || STOP=-1
  for M in 10 100 500 1000 10000 100000; do
    # naive는 응답 크기가 M에 비례해 커진다. 시간 폭발을 막으려 요청 수를 줄인다.
    NREQ=$REQ
    if [ "$MODE" = naive ]; then
      if [ "$M" -ge 100000 ]; then NREQ=300
      elif [ "$M" -ge 10000 ]; then NREQ=3000
      elif [ "$M" -ge 1000 ]; then NREQ=20000
      fi
    fi
    OUT=$(timeout 120 docker exec qm-redis redis-benchmark -n "$NREQ" -c "$CONC" -P 1 \
          ZRANGE "bench:z:$M" 0 "$STOP" 2>/dev/null) || OUT=""
    RPS=$(echo "$OUT" | grep -oP 'throughput summary: \K[0-9.]+' | head -1)
    LAT=$(echo "$OUT" | grep -A2 'latency summary' | tail -1)
    AVG=$(echo "$LAT" | awk '{print $1}')
    P50=$(echo "$OUT" | grep -oP '^50\.000% <= \K[0-9.]+' | head -1)
    # redis-benchmark은 이진 분할 퍼센타일이라 99.000%가 없다. 99.219%를 쓴다.
    P99=$(echo "$OUT" | grep -oP '^99\.219% <= \K[0-9.]+' | head -1)
    printf "%-8s %-8s %8s %12s %10s %10s %10s\n" "$MODE" "$M" "$NREQ" "${RPS:-TIMEOUT}" "${AVG:-?}" "${P50:-?}" "${P99:-?}"
  done
done
for M in 10 100 500 1000 10000 100000; do $R DEL "bench:z:$M" > /dev/null; done
echo ""; echo "bench 키 정리 완료"
