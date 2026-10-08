#!/usr/bin/env bash
# 네트워크 파티션. master 를 qm-ha 네트워크에서 떼어낸다.
#
#   NETPART=1 ./scripts/partition-master.sh cut
#   NETPART=1 ./scripts/partition-master.sh heal
#
# 반드시 T-B 토폴로지에서만 쓴다 (docker-compose.netpart.yml).
# T-A 는 노드끼리도 호스트 IP + 포트 포워딩으로 붙기 때문에, 네트워크에서 떼면
# 격리된 master 에 아무도 - 실험자 본인도 - 못 붙는다. 그러면 "구 master 에
# 계속 쓴다" 는 스플릿 브레인의 전제 자체가 성립하지 않는다.
#
# cut 뒤에도 `docker exec qm-ha-master redis-cli` 는 된다. 네트워크가 아니라
# 프로세스에 직접 붙는 통로이기 때문이다. 격리된 master 에 쓰는 것은 이걸로 한다.
set -eu
. "$(dirname "$0")/lib.sh"
ACTION="${1:?cut | heal}"
TARGET="${2:-qm-ha-master}"

case "$ACTION" in
  cut)
    docker network disconnect qm-ha "$TARGET"
    echo "CUT ${TARGET} t0_ms=$(now_ms)"
    echo "이제 ${TARGET} 은 자기 혼자다. Sentinel 3대는 서로 보이므로 정족수가 산다."
    ;;
  heal)
    ip=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$TARGET" | grep '^ANNOUNCE_IP=' | cut -d= -f2)
    docker network connect --ip "$ip" qm-ha "$TARGET"
    echo "HEALED ${TARGET} ip=${ip} t_ms=$(now_ms)"
    echo "복귀 직후 role 을 본다 - 강등되기 전까지의 창이 관측 대상이다"
    for i in $(seq 1 10); do
      echo "$(now_ms) $(docker exec "$TARGET" redis-cli -p 6379 INFO replication | grep -E '^role:' | tr -d '\r')"
      sleep 0.5
    done
    ;;
  *) echo "cut 또는 heal" >&2; exit 1 ;;
esac
