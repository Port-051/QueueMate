#!/bin/bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching/load-test"
OUT=results_match.txt
: > $OUT
run() { # label conc procs rounds sleep prefillN
  local L=$1 C=$2 P=$3 R=$4 S=$5 N=$6
  bash clean_match.sh >/dev/null
  if [ "$N" != "0" ]; then python3 prefill.py $N >> $OUT; fi
  echo "-- ops before: $(docker exec qm-redis redis-cli info stats | grep -oP 'instantaneous_ops_per_sec:\K\d+')" >> $OUT
  LABEL=$L CONC=$C PROCS=$P ROUNDS=$R POLL_SLEEP=$S RAW=raw_$L.tsv timeout 600 python3 match_latency.py >> $OUT 2>&1
  echo "" >> $OUT
}
for i in 1 2; do
  run idle_r$i        1  1  250 0     0
done
for i in 1 2; do
  run busy20_r$i     20 10   15 0.001 0
done
for i in 1 2; do
  run idle_deep100_r$i  1  1  250 0     100
done
for i in 1 2; do
  run busy20_deep100_r$i 20 10  15 0.001 100
done
bash clean_match.sh >> $OUT
cat $OUT
