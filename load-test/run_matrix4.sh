#!/bin/bash
cd "/mnt/c/Users/kimye/OneDrive/바탕 화면/matching/load-test"
OUT=results_match4.txt
: > $OUT
run() { local L=$1 C=$2 P=$3 R=$4 D=$5 S=$6
  bash clean_match.sh >/dev/null; sleep 4
  LABEL=$L CONC=$C PROCS=$P ROUNDS=$R DISCARD=$D POLL_SLEEP=$S RAW=raw_$L.tsv \
    timeout 900 python3 match_latency.py >> $OUT 2>&1; echo "" >> $OUT
}
for i in 1 2; do run c40b_r$i   40 20 300 60 0.001; done
for i in 1 2; do run c100b_r$i 100 20 150 30 0.001; done
for i in 1 2; do run c200b_r$i 200 20 100 20 0.002; done
bash clean_match.sh >> $OUT
echo "=== DONE ===" >> $OUT
