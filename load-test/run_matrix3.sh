#!/bin/bash
# 2026-09-27: 바디의 tier · 쿠키 qm_access · 숫자 사용자 번호는 match_latency.py / prefill.py 가 처리한다 (TIER 환경변수, 기본 GOLD_2).
# 배경부하 sweep_load.py($SP)는 스크래치패드에 있던 파일이라 없다 — runbg 계열은 재현 불가 (docs/PERFORMANCE_EVIDENCE.md §4).
cd "$(dirname "$0")" || exit 1
SP="/tmp/claude-1000/-mnt-c-Users-kimye-OneDrive-------queueMate/0ce3c982-127a-4b84-9354-51289fc1cee5/scratchpad"
OUT=results_match3.txt
: > $OUT
run() { local L=$1 C=$2 P=$3 R=$4 D=$5 S=$6
  bash clean_match.sh >/dev/null; sleep 4
  echo "-- $L idle_ops=$(docker exec qm-redis redis-cli info stats | grep -oP 'instantaneous_ops_per_sec:\K\d+')" >> $OUT
  LABEL=$L CONC=$C PROCS=$P ROUNDS=$R DISCARD=$D POLL_SLEEP=$S RAW=raw_$L.tsv \
    timeout 900 python3 match_latency.py >> $OUT 2>&1; echo "" >> $OUT
}
runbg() { local L=$1 R=$2
  bash clean_match.sh >/dev/null; sleep 4
  VUS=100 PROCS=8 WARMUP=8 DURATION=50 LABEL=bg RUN_ID=bg$RANDOM \
    python3 "$SP/sweep_load.py" > /tmp/bg_$L.txt 2>&1 &
  local BG=$!
  sleep 12
  echo "-- $L bg_ops=$(docker exec qm-redis redis-cli info stats | grep -oP 'instantaneous_ops_per_sec:\K\d+')" >> $OUT
  LABEL=$L CONC=1 PROCS=1 ROUNDS=$R DISCARD=10 POLL_SLEEP=0 PREFILL=1 RAW=raw_$L.tsv \
    timeout 300 python3 match_latency.py >> $OUT 2>&1
  echo "-- $L bg_ops_after=$(docker exec qm-redis redis-cli info stats | grep -oP 'instantaneous_ops_per_sec:\K\d+')" >> $OUT
  wait $BG
  grep -o '@@RESULT@@.*' /tmp/bg_$L.txt >> $OUT; echo "" >> $OUT
}
for i in 1 2; do runbg saturated_r$i 250; done
for i in 1 2; do run c5_r$i    5  5 300 10 0.001; done
for i in 1 2; do run c10_r$i  10 10 250 10 0.001; done
for i in 1 2; do run c40_r$i  40 20 100 10 0.001; done
for i in 1 2; do run c200_r$i 200 20 25 5 0.002; done
bash clean_match.sh >> $OUT
echo "=== DONE ===" >> $OUT
