#!/usr/bin/env bash
# 노드의 네트워크에 지연을 넣는다. 오탐(false positive) 을 일부러 만드는 데 쓴다.
#
#   ./scripts/inject-latency.sh qm-ha-master 300ms 50ms   # 300ms +- 50ms
#   ./scripts/inject-latency.sh qm-ha-master clear        # 걷어낸다
#
# redis:7-alpine 에는 tc 도 NET_ADMIN 도 없다. 그래서 같은 네트워크 네임스페이스를
# 공유하는 별도 컨테이너를 띄워서 그 안에서 tc 를 건다.
# --net=container:<이름> 이 그 뜻이다. 대상 컨테이너의 eth0 에 그대로 적용된다.
#
# 주의: tc 는 나가는(egress) 트래픽에만 걸린다. master 의 PING 응답이 늦어지는
# 효과는 나지만, Sentinel -> master 방향은 그대로다. 양방향을 원하면 Sentinel 쪽에도
# 같은 명령을 걸어라.
set -eu
. "$(dirname "$0")/lib.sh"

TARGET="${1:?대상 컨테이너 이름이 필요하다}"
DELAY="${2:?지연 (예: 300ms) 또는 clear}"
JITTER="${3:-}"
NETEM_IMAGE="${NETEM_IMAGE:-nicolaka/netshoot}"

run_tc() {
  docker run --rm \
    --net="container:${TARGET}" \
    --cap-add=NET_ADMIN \
    --entrypoint tc \
    "$NETEM_IMAGE" "$@"
}

if [ "$DELAY" = "clear" ]; then
  run_tc qdisc del dev eth0 root 2>/dev/null || echo "걸린 qdisc 가 없다"
  echo "CLEARED ${TARGET} t_ms=$(now_ms)"
  exit 0
fi

run_tc qdisc del dev eth0 root 2>/dev/null || true
run_tc qdisc add dev eth0 root netem delay "$DELAY" $JITTER
echo "INJECTED ${TARGET} delay=${DELAY} ${JITTER} t_ms=$(now_ms)"
echo "걷어내려면: $0 ${TARGET} clear"
