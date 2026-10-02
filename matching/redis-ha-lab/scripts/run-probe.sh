#!/usr/bin/env bash
# k6 프로브를 띄우고, 중간에 장애를 주입하고, 결과를 접는다.
#
#   BASE_URL=http://172.22.149.244:8080 ./scripts/run-probe.sh 120s 50 30
#     |                                    |    |  |
#     |                                    |    |  +-- 시작 몇 초 뒤에 master 를 죽일지
#     |                                    |    +----- 초당 요청 수
#     |                                    +---------- 총 측정 시간
#     +----- 앱 주소. 호스트에서 IDE 로 띄웠으면 WSL eth0 IP 를 쓴다
#            (localhost 를 쓰면 k6 컨테이너가 자기 자신을 가리킨다)
set -eu
. "$(dirname "$0")/lib.sh"

DURATION="${1:-120s}"
RATE="${2:-50}"
KILL_AT="${3:-30}"
: "${BASE_URL:?BASE_URL 이 필요하다 (예: http://172.22.149.244:8080)}"
RUN_ID="fo$(date +%s)"
OUT="${LAB_DIR}/k6/out"
mkdir -p "$OUT"

echo "run_id=${RUN_ID} duration=${DURATION} rate=${RATE} kill_at=${KILL_AT}s"
echo "before master = $(current_master)"

docker run --rm -i \
  -v "${LAB_DIR}/k6:/scripts" \
  -e BASE_URL="$BASE_URL" -e RATE="$RATE" -e DURATION="$DURATION" -e RUN_ID="$RUN_ID" \
  grafana/k6 run --quiet --out "csv=/scripts/out/${RUN_ID}.csv" /scripts/failover-probe.js &
K6_PID=$!

sleep "$KILL_AT"
"${LAB_DIR}/scripts/kill-master.sh"

wait "$K6_PID" || true

echo
echo "=============== 초 단위 집계 ==============="
python3 "${LAB_DIR}/k6/analyze.py" "${OUT}/${RUN_ID}.csv" --t0 "$(cat "${LAB_DIR}/.last-kill-ms")"

echo
echo "=============== Sentinel 타임라인 ==============="
"${LAB_DIR}/scripts/failover-timeline.sh"
