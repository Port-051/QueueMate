#!/usr/bin/env bash
# 수동 페일오버. SENTINEL FAILOVER 는 "장애"가 아니라 "계획된 전환"이다.
#
# 진짜 장애(kill-master.sh)와의 차이:
#   - 감지 단계가 통째로 없다. down-after-milliseconds 를 기다리지 않는다.
#   - 다른 Sentinel 의 동의(quorum)도 구하지 않는다. 받은 Sentinel 이 바로 한다.
#   - 구 master 가 살아 있으므로 데이터 유실이 없다. 승격 직후 강등되어 복제를 받는다.
# 그래서 이 명령으로 잰 시간은 다운타임의 하한이지 장애 시 다운타임이 아니다.
# 이 둘을 같은 표에 올리면 안 된다 (README.md "수동 페일오버 vs 진짜 장애").
set -eu
. "$(dirname "$0")/lib.sh"
echo "before: $(current_master)"
t0=$(now_ms)
sent SENTINEL FAILOVER mymaster
for i in $(seq 1 120); do
  cur=$(current_master)
  echo "$(now_ms) $cur"
  sleep 0.25
done | awk -v t0="$t0" 'NR==1{first=$2" "$3} {if($2" "$3!=first){print "전환 감지 t+"($1-t0)"ms -> "$2" "$3; exit}}'
echo "after : $(current_master)"
