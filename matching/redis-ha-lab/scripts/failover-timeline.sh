#!/usr/bin/env bash
# Sentinel 로그에서 페일오버 타임라인을 뽑는다. Redis 측 복구 시간이 여기서 나온다.
#
#   ./scripts/kill-master.sh
#   sleep 30
#   ./scripts/failover-timeline.sh
#
# 이 스크립트가 재는 것은 "Redis 가 새 master 를 세우기까지" 다.
# 애플리케이션이 실제로 다시 쓰기 시작한 시각은 여기 안 나온다.
# 그건 k6/failover-probe.js 가 잰다. 둘을 나눠 재는 것이 실험 1 의 핵심이다.
#
# 이벤트가 뜻하는 것
#   +sdown            이 Sentinel 이 혼자 "죽었다" 고 판단했다 (down-after 경과)
#   +odown            정족수만큼 동의했다. 여기서부터가 객관적 다운이다
#   +try-failover     페일오버를 시작한다
#   +elected-leader   페일오버를 집행할 Sentinel 이 뽑혔다
#   +selected-slave   승격시킬 replica 를 골랐다
#   +promoted-slave   고른 replica 에 REPLICAOF NO ONE 을 보냈다
#   +switch-master    감시 대상을 새 master 로 바꿨다. 클라이언트에게 알려 줄 주소가 바뀐 순간
#   +failover-end     남은 replica 재배치까지 끝났다
set -eu
. "$(dirname "$0")/lib.sh"

T0="${1:-}"
if [ -z "$T0" ] && [ -f "${LAB_DIR}/.last-kill-ms" ]; then
  T0=$(cat "${LAB_DIR}/.last-kill-ms")
fi

echo "t0(kill) = ${T0:-없음 - 절대 시각만 찍는다}"
echo
printf "%-14s %-22s %s\n" "t+ms" "sentinel" "event"

for s in $SENTINELS; do
  name="${s%%:*}"
  docker logs -t "$name" 2>&1 \
    | grep -E '\+(sdown|odown|try-failover|vote-for-leader|elected-leader|failover-state-select-slave|selected-slave|failover-state-send-slaveof-noone|promoted-slave|failover-state-reconf-slaves|switch-master|failover-end|slave-reconf-done|convert-to-slave|fix-slave-config)' \
    | while read -r ts rest; do
        # docker 의 -t 는 RFC3339Nano 를 앞에 붙인다. 이걸 epoch ms 로 바꾼다.
        ems=$(date -u -d "$ts" +%s%3N 2>/dev/null || echo "")
        [ -z "$ems" ] && continue
        if [ -n "$T0" ]; then rel=$((ems - T0)); else rel="$ems"; fi
        ev=$(echo "$rest" | sed -E 's/.*(\+[a-z-]+)/\1/' | awk '{print $1}')
        printf "%-14s %-22s %s\n" "$rel" "$name" "$(echo "$rest" | sed -E 's/^[0-9]+:X [^#*]*[#*] //')"
      done
done | sort -n

echo
echo "읽는 법:"
echo "  감지 시간   = +odown 의 t+ms  (down-after-milliseconds 에 지배된다)"
echo "  승격 시간   = +switch-master 의 t+ms - +odown 의 t+ms"
echo "  Redis 복구  = +switch-master 의 t+ms"
echo "  앱 복구     = 이 스크립트로는 안 나온다. k6/failover-probe.js 결과를 봐라"
