# 스크립트들이 함께 쓰는 조각. 직접 실행하지 않는다.
#
# redis-cli 는 호스트에 없을 수 있다 (START_HERE.md 도 "이 문서를 쓴 환경에는
# docker 도 redis-cli 도 없어 실행을 확인하지 못했다" 고 적었다).
# 그래서 전부 컨테이너 안의 redis-cli 를 docker exec 로 부른다.
set -eu

LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
# --env-file 을 명시한다. compose 는 프로젝트 디렉터리 기준으로 .env 를 찾는데,
# 스크립트를 어디서 부르든 같게 동작해야 한다.
# 배열로 둔다. 문자열로 두고 $COMPOSE_FILES 로 펼치면 경로에 공백이 있을 때
# (윈도우 "바탕 화면" 처럼) 단어가 쪼개져 docker 가 엉뚱한 인자를 받는다.
COMPOSE_ARGS=(--env-file "${LAB_DIR}/.env" -f "${LAB_DIR}/docker-compose.yml")
if [ "${NETPART:-0}" = "1" ]; then
  COMPOSE_ARGS+=(-f "${LAB_DIR}/docker-compose.netpart.yml")
fi

MASTER_NAME=qm-ha-master
REPLICA1_NAME=qm-ha-replica1
REPLICA2_NAME=qm-ha-replica2
SENTINELS="qm-ha-sentinel1:26379 qm-ha-sentinel2:26380 qm-ha-sentinel3:26381"
NODES="${MASTER_NAME} ${REPLICA1_NAME} ${REPLICA2_NAME}"

dc() { docker compose "${COMPOSE_ARGS[@]}" "$@"; }

# 노드에 명령을 보낸다. 노드가 죽어 있으면 실패한다 (그게 정상이다).
node() { local n="$1"; shift; docker exec "$n" redis-cli -p 6379 "$@"; }

# Sentinel 에 명령을 보낸다. 첫 번째로 응답하는 Sentinel 을 쓴다.
sent() {
  local s
  for s in $SENTINELS; do
    if docker exec "${s%%:*}" redis-cli -p "${s##*:}" "$@" 2>/dev/null; then
      return 0
    fi
  done
  echo "살아 있는 Sentinel 이 없다" >&2
  return 1
}

# Sentinel 이 지금 master 라고 보는 주소. "ip port" 두 토큰으로 나온다.
current_master() { sent SENTINEL get-master-addr-by-name mymaster | tr '\n' ' '; }

# announce 주소(ip:port)를 컨테이너 이름으로 되돌린다. kill 대상을 고를 때 쓴다.
container_of_addr() {
  local addr="$1" n port ip env
  for n in $NODES; do
    env=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$n" 2>/dev/null || true)
    [ -z "$env" ] && continue
    port=$(echo "$env" | grep '^ANNOUNCE_PORT=' | cut -d= -f2)
    ip=$(echo "$env" | grep '^ANNOUNCE_IP=' | cut -d= -f2)
    if [ "${ip}:${port}" = "$addr" ]; then echo "$n"; return 0; fi
  done
  return 1
}

now_ms() { date +%s%3N; }
