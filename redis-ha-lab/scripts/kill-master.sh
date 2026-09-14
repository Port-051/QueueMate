#!/usr/bin/env bash
# 진짜 장애 주입. 지금 master 인 컨테이너를 SIGKILL 로 죽인다.
#
#   ./scripts/kill-master.sh
#
# docker kill 은 SIGKILL 이라 Redis 가 종료 인사를 못 한다.
# docker stop(SIGTERM) 을 쓰면 Redis 가 연결을 정상 종료하며 나가서
# 클라이언트가 즉시 끊김을 알아차린다 - 실제 장애보다 훨씬 빠르게 복구된다.
# 그 차이 자체가 실험 1 의 관측 대상이므로 여기서는 kill 만 쓴다.
#
# 죽인 시각을 epoch millis 로 찍는다. 이 값이 다운타임 계산의 t0 다.
set -eu
. "$(dirname "$0")/lib.sh"

addr=$(sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ':' | sed 's/:$//')
target=$(container_of_addr "$addr" || echo "")
if [ -z "$target" ]; then
  echo "announce 주소 ${addr} 에 해당하는 컨테이너를 못 찾았다." >&2
  echo "T-A/T-B 를 섞어 띄웠거나 .env 의 HOST_IP 가 바뀐 것이다." >&2
  exit 1
fi

t0=$(now_ms)
docker kill "$target" > /dev/null
echo "KILLED container=${target} addr=${addr} t0_ms=${t0}"
echo "$t0" > "${LAB_DIR}/.last-kill-ms"
echo "(t0 를 ${LAB_DIR}/.last-kill-ms 에 남겼다. failover-timeline.sh 가 읽는다)"
