#!/bin/bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching/load-test"
SP="/tmp/claude-1000/-mnt-c-Users-kimye-OneDrive-------queueMate/0ce3c982-127a-4b84-9354-51289fc1cee5/scratchpad"
OUT=results_final.txt
: > $OUT
run() { # label conc procs warmup dur sleep prefillN
  local L=$1 C=$2 P=$3 W=$4 D=$5 S=$6 N=$7
  bash clean_match.sh >/dev/null; sleep 4
  [ "$N" != "0" ] && python3 prefill.py $N >> $OUT
  LABEL=$L CONC=$C PROCS=$P ROUNDS=999999 WARMUP=$W DURATION=$D POLL_SLEEP=$S \
    RAW=raw_$L.tsv timeout 900 python3 match_latency.py >> $OUT 2>&1
  echo "   exec: queued=$(curl -s -m3 localhost:8080/actuator/metrics/executor.queued | grep -oP '"VALUE","value":\K[0-9.]+')" >> $OUT
  echo "" >> $OUT
}
runbg() { # 배경부하 100VU 로 앱을 천장까지 밀어놓고 프로브 1쌍 측정
  local L=$1
  bash clean_match.sh >/dev/null; sleep 4
  VUS=100 PROCS=8 WARMUP=8 DURATION=45 LABEL=bg RUN_ID=bg$RANDOM \
    python3 "$SP/sweep_load.py" > /tmp/bgf_$L.txt 2>&1 &
  local BG=$!
  sleep 14
  echo "-- $L bg_ops=$(docker exec qm-redis redis-cli info stats | grep -oP 'instantaneous_ops_per_sec:\K\d+')" >> $OUT
  LABEL=$L CONC=1 PROCS=1 ROUNDS=999999 WARMUP=1 DURATION=20 POLL_SLEEP=0 PREFILL=1 \
    RAW=raw_$L.tsv timeout 300 python3 match_latency.py >> $OUT 2>&1
  wait $BG
  grep -o '@@RESULT@@.*' /tmp/bgf_$L.txt >> $OUT; echo "" >> $OUT
}
for i in 1 2; do run F_idle_r$i        1  1 3 20 0     0;   done
for i in 1 2; do run F_c5_r$i          5  5 3 15 0.001 0;   done
for i in 1 2; do run F_c10_r$i        10 10 3 15 0.001 0;   done
for i in 1 2; do run F_c20_r$i        20 20 3 15 0.001 0;   done
for i in 1 2; do run F_c100_r$i      100 20 5 15 0.001 0;   done
for i in 1 2; do run F_c200_r$i      200 20 6 20 0.002 0;   done
for i in 1 2; do run F_idle_deep_r$i   1  1 3 20 0     100; done
for i in 1 2; do run F_c20_deep_r$i   20 20 3 15 0.001 100; done
for i in 1 2; do runbg F_sat_r$i; done
bash clean_match.sh >> $OUT
echo "=== DONE ===" >> $OUT
