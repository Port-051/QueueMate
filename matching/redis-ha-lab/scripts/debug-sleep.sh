#!/usr/bin/env bash
# master 를 N초 동안 얼린다. tc netem 과 달리 프로세스 자체가 멈춘다.
#
#   ./scripts/debug-sleep.sh 8
#
# DEBUG SLEEP 은 Redis 의 이벤트 루프를 통째로 막는다. 네트워크는 살아 있고
# TCP 연결도 유지되지만 어떤 명령에도 응답하지 않는다.
# 실제로 이런 상태를 만드는 것: 큰 키의 DEL, 무거운 Lua, RDB fork 지연, swap.
#
# down-after-milliseconds 보다 짧게 주면 Sentinel 이 참고 넘어가고,
# 길게 주면 페일오버가 난다. 그 경계를 찾는 것이 실험 3 이다.
#
# 이건 "진짜 장애" 가 아니라 "장애로 오인될 만한 상태" 다.
# 여기서 페일오버가 나면 그게 바로 오탐이다 - 노드는 8초 뒤 멀쩡히 돌아온다.
set -eu
. "$(dirname "$0")/lib.sh"
SEC="${1:?몇 초 얼릴지}"
addr=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
target=$(container_of_addr "$addr")
echo "SLEEP ${target} ${SEC}s t0_ms=$(now_ms)"
# 응답을 기다리면 이 스크립트도 같이 멈춘다. 백그라운드로 던진다.
docker exec -d "$target" redis-cli -p 6379 DEBUG SLEEP "$SEC"
sleep 0.2
echo "던졌다. ./scripts/status.sh 로 s-down-time 이 오르는지 봐라"
