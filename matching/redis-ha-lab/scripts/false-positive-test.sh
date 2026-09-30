#!/usr/bin/env bash
# 오탐 실험. master 프로세스를 N초 얼리고 Sentinel 이 페일오버를 했는지 본다.
#
#   ./scripts/false-positive-test.sh <얼릴초> <라벨>
#
# 얼리는 방법은 docker pause(SIGSTOP) 다. DEBUG SLEEP 은 Redis 7 부터
# enable-debug-command 가 기본 no 라 컨테이너를 다시 만들지 않으면 못 쓴다.
# pause 는 프로세스만 멈추고 TCP 연결과 포트는 그대로라 의미가 같다 —
# 실제로 이런 상태를 만드는 것: RDB fork 지연, 큰 키 DEL, 무거운 Lua, swap.
#
# 얼린 노드는 N초 뒤 멀쩡히 돌아온다. 그런데도 페일오버가 났다면 그것이 오탐이다.
set -eu
. "$(dirname "$0")/lib.sh"
SEC="${1:?초}"; LABEL="${2:-test}"

da=$(sent SENTINEL master mymaster | paste - - | grep '^down-after-milliseconds' | awk '{print $2}')
before=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
target=$(container_of_addr "$before")
t0=$(now_ms)

echo "### ${LABEL}"
echo "    down-after=${da}ms  얼림=${SEC}s  master=${before} (${target})"
docker pause "$target" > /dev/null
sleep "$SEC"
docker unpause "$target" > /dev/null
echo "    (${SEC}초 뒤 깨어남. 노드는 멀쩡하다)"
sleep 8

after=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
echo "    얼리기 전 master : ${before}"
echo "    8초 뒤    master : ${after}"
if [ "$before" = "$after" ]; then
  echo "    >>> 오탐 없음 — Sentinel 이 참고 넘어갔다 <<<"
else
  echo "    >>> 오탐 발생 — 멀쩡한 master 가 교체됐다 <<<"
fi

echo "    --- Sentinel 이벤트 ---"
found=0
for s in $SENTINELS; do
  docker logs -t --since 40s "${s%%:*}" 2>&1 \
    | grep -E '\+(sdown|odown|switch-master|failover-end)' || true
done | while read -r ts rest; do
    ems=$(date -u -d "$ts" +%s%3N 2>/dev/null || echo "")
    [ -z "$ems" ] && continue
    [ "$ems" -lt "$t0" ] && continue
    printf "    t0%+7d ms  %s\n" "$((ems - t0))" "$(echo "$rest" | sed -E 's/^[0-9]+:X [^#*]*[#*] //' | cut -c1-64)"
  done | sort -k2 -n | head -12
echo
